package project.lms_rikkei_edu.modules.course.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.course.dto.response.CourseSnapshotDto;
import project.lms_rikkei_edu.modules.course.entity.CourseVersion;
import project.lms_rikkei_edu.modules.course.repository.CourseVersionRepository;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Kiểm tra 1 s3Key (tài liệu) có còn được tham chiếu bởi snapshot của bất kỳ {@link CourseVersion}
 * nào (mọi trạng thái: DRAFT/PENDING/APPROVED/REJECTED) thuộc 1 khóa học hay không, trước khi
 * xóa thật file trên S3. CourseVersion.snapshot lưu tài liệu bằng s3Key (không lưu ID) — nếu xóa
 * file trong khi còn version nào đó cần lại (rollback), nội dung mất vĩnh viễn không thể phục hồi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseVersionReferenceChecker {

    private final CourseVersionRepository courseVersionRepository;
    private final ObjectMapper objectMapper;

    /** True nếu s3Key an toàn để xóa (không còn version nào tham chiếu). External URL luôn coi là an toàn (không có file S3 thật để xóa). */
    public boolean isSafeToDelete(UUID courseId, String s3Key) {
        if (s3Key == null || s3Key.isBlank() || s3Key.startsWith("ext://")) return true;
        return courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(courseId).stream()
                .map(CourseVersion::getSnapshot)
                .filter(Objects::nonNull)
                .noneMatch(snapshotJson -> snapshotReferencesKey(courseId, snapshotJson, s3Key));
    }

    private boolean snapshotReferencesKey(UUID courseId, String snapshotJson, String s3Key) {
        try {
            CourseSnapshotDto snap = objectMapper.readValue(snapshotJson, CourseSnapshotDto.class);
            if (snap.getChapters() == null) return false;
            return snap.getChapters().stream()
                    .filter(Objects::nonNull)
                    .flatMap(c -> c.getLessons() == null ? Stream.<CourseSnapshotDto.LessonSnap>empty() : c.getLessons().stream())
                    .filter(Objects::nonNull)
                    .flatMap(l -> l.getResources() == null ? Stream.<CourseSnapshotDto.ResourceSnap>empty() : l.getResources().stream())
                    .filter(Objects::nonNull)
                    .anyMatch(r -> s3Key.equals(r.getS3Key()));
        } catch (Exception e) {
            // Không đọc được snapshot — an toàn hơn là coi như CÓ tham chiếu, tránh xóa nhầm file
            // mà một version cũ có thể vẫn cần.
            log.warn("Không thể đọc snapshot khi kiểm tra tham chiếu s3Key cho courseId={}: {}", courseId, e.getMessage());
            return true;
        }
    }
}
