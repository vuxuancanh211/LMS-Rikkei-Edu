package project.lms_rikkei_edu.modules.course.service.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import project.lms_rikkei_edu.modules.course.dto.response.CourseResponse;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Vùng cache "course-list" (Redis) chỉ nên giữ dữ liệu đơn giản (List/long),
 * KHÔNG cache trực tiếp {@link Page}/{@code PageImpl} vì kiểu này không có
 * constructor phù hợp để Jackson deserialize lại từ Redis. Tách gateway
 * riêng (bean khác) — không phải method private trong CourseServiceImpl —
 * vì @Cacheable chỉ chặn được lời gọi đi qua Spring proxy; gọi nội bộ cùng
 * class (self-invocation) sẽ bỏ qua cache hoàn toàn.
 */
@Component
@RequiredArgsConstructor
public class CourseListCacheGateway {

    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;

    /* Class thường (không phải record) — record ngầm định "final", mà
       DefaultTyping.NON_FINAL (RedisConfig) bỏ qua việc ghi kèm type info cho
       class final khi serialize, trong khi lúc đọc (Object.class) lại luôn
       kỳ vọng có type info → lệch định dạng, gây lỗi khi đọc cache. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private List<CourseResponse> content;
        private long totalElements;
    }

    @Cacheable(value = "course-list", key = "#instructorId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + (#keyword ?: '')")
    public Entry find(UUID instructorId, Pageable pageable, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            Page<Course> page = courseRepository.findAllByInstructorId(instructorId, pageable);
            List<CourseResponse> content = page.getContent().stream().map(courseMapper::toResponse).toList();
            return new Entry(content, page.getTotalElements());
        }
        return searchRanked(instructorId, pageable, keyword);
    }

    private Entry searchRanked(UUID instructorId, Pageable pageable, String keyword) {
        List<String> tokens = Arrays.stream(keyword.trim().toLowerCase().split("\\s+"))
                .filter(t -> !t.isBlank())
                .distinct()
                .toList();

        if (tokens.isEmpty()) {
            Page<Course> page = courseRepository.findAllByInstructorId(instructorId, pageable);
            List<CourseResponse> content = page.getContent().stream().map(courseMapper::toResponse).toList();
            return new Entry(content, page.getTotalElements());
        }

        Specification<Course> spec = (root, query, cb) -> {
            var ownerPredicate = cb.equal(root.get("instructorId"), instructorId);
            List<jakarta.persistence.criteria.Predicate> tokenPredicates = new ArrayList<>();
            for (String token : tokens) {
                String pattern = "%" + token + "%";
                tokenPredicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)
                ));
            }
            var anyTokenMatch = cb.or(tokenPredicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            return cb.and(ownerPredicate, anyTokenMatch);
        };

        // Danh sách khóa học của 1 giảng viên vốn nhỏ (chục/trăm bản ghi) nên xếp
        // hạng theo số từ khớp được làm trong bộ nhớ, không cần full-text search.
        List<Course> matched = courseRepository.findAll(spec);
        List<Course> ranked = matched.stream()
                .sorted(Comparator
                        .comparingInt((Course c) -> -matchScore(c, tokens))
                        .thenComparing(Course::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long total = ranked.size();
        int fromIndex = (int) pageable.getOffset();
        if (fromIndex >= ranked.size()) {
            return new Entry(List.of(), total);
        }
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), ranked.size());
        List<CourseResponse> content = ranked.subList(fromIndex, toIndex).stream()
                .map(courseMapper::toResponse)
                .toList();
        return new Entry(content, total);
    }

    private int matchScore(Course course, List<String> tokens) {
        String haystack = (safe(course.getTitle()) + " " + safe(course.getDescription())).toLowerCase();
        int score = 0;
        for (String token : tokens) {
            if (haystack.contains(token)) score++;
        }
        return score;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
