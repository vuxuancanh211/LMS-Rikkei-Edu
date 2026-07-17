package project.lms_rikkei_edu.modules.course.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.Chapter;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.CourseCategory;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.exception.*;
import project.lms_rikkei_edu.modules.course.mapper.ChapterMapper;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.mapper.LessonMapper;
import project.lms_rikkei_edu.modules.course.entity.CourseApprovalLog;
import project.lms_rikkei_edu.modules.course.entity.CourseVersion;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.CourseService;
import project.lms_rikkei_edu.modules.course.service.StudentCourseService;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.quiz.dto.request.QuizMetadataRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.QuizSummaryResponse;
import project.lms_rikkei_edu.modules.quiz.entity.QuizEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;
import project.lms_rikkei_edu.modules.quiz.repository.QuizRepository;
import project.lms_rikkei_edu.modules.quiz.repository.BankQuestionRepository;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CourseServiceImpl implements CourseService {

    private static final String VERSION_STATUS_DRAFT   = "DRAFT";
    private static final String ERR_VERSION_NOT_FOUND  = "Version không tồn tại";

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final LessonResourceRepository lessonResourceRepository;
    private final CourseCategoryRepository categoryRepository;
    private final CourseApprovalLogRepository approvalLogRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final CourseMapper courseMapper;
    private final ObjectMapper objectMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final EntityManager entityManager;
    private final S3Service s3Service;
    private final QuizService quizService;
    private final QuizRepository quizRepository;
    private final BankQuestionRepository bankQuestionRepository;
    private final StudentCourseService studentCourseService;
    private final CourseListCacheGateway courseListCacheGateway;
    private final CourseVersionReferenceChecker courseVersionReferenceChecker;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final project.lms_rikkei_edu.modules.user.repository.UserRepository userRepository;

    /* Self-proxy để getCourseDetailBySlug() gọi lại getCourseDetail() QUA proxy Spring AOP —
       gọi trực tiếp (this.getCourseDetail(...)) sẽ bỏ qua @Cacheable vì Spring cache dựa trên
       proxy, không chặn được self-invocation. Cần proxy để 2 đường vào (theo id / theo slug)
       dùng chung đúng 1 cache entry (key theo courseId) — trước đây cache riêng theo slug nên
       @CacheEvict(key = courseId) ở các thao tác sửa (thêm chương/bài/tài liệu...) không xóa
       được entry theo slug, khiến trang chi tiết khóa học hiện dữ liệu cũ sau khi F5. */
    @Autowired
    @Lazy
    private CourseService self;

    @Override
    public CourseResponse createCourse(UUID instructorId, CreateCourseRequest request) {
        CourseCategory category = resolveCategory(request.getCategoryId());
        String slug = generateUniqueSlug(request.getTitle(), null);

        Course course = Course.builder()
                .instructorId(instructorId)
                .title(request.getTitle())
                .slug(slug)
                .description(project.lms_rikkei_edu.modules.course.util.CourseDescriptionSanitizer.sanitize(request.getDescription()))
                .level(request.getLevel())
                .thumbnailUrl(request.getThumbnailUrl())
                .chatEnabled(request.getChatEnabled() != null ? request.getChatEnabled() : false)
                .category(category)
                .status(CourseStatus.DRAFT)
                .learningOutcomes(request.getLearningOutcomes() != null ? cleanStringList(request.getLearningOutcomes()) : new ArrayList<>())
                .build();

        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public CourseDetailResponse getCourseDetail(UUID instructorId, UUID courseId) {
        Course course = courseRepository.findByIdWithFullContent(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        assertOwner(course, instructorId);
        hydrateChaptersLessonsResources(course);
        CourseDetailResponse response = courseMapper.toDetailResponse(course);
        attachCourseStats(response, courseId);
        attachInstructorInfo(response, instructorId);
        return response;
    }

    /** Số học viên — dùng cho panel xem trước ở tab "Thông tin" (không cần theo học viên cụ thể). */
    private void attachCourseStats(CourseDetailResponse response, UUID courseId) {
        response.setStudentCount((int) courseEnrollmentRepository.countByCourseId(courseId));
    }

    private void attachInstructorInfo(CourseDetailResponse response, UUID instructorId) {
        userRepository.findById(instructorId).ifPresent(u -> {
            response.setInstructorName(u.getFullName());
            response.setInstructorBio(u.getBio());
        });
        response.setInstructorCourseCount((int) courseRepository.countByInstructorIdAndStatus(instructorId, CourseStatus.PUBLISHED));
    }

    private List<String> cleanStringList(List<String> raw) {
        return raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetailBySlug(UUID instructorId, String slug) {
        Course course = courseRepository.findBySlug(slug)
                .orElseThrow(() -> new CourseNotFoundException(slug));
        assertOwner(course, instructorId);
        // Gọi qua self-proxy để dùng chung cache "course-detail" key theo courseId — xem
        // ghi chú ở field `self` phía trên.
        return self.getCourseDetail(instructorId, course.getId());
    }

    /**
     * Nạp lessons + resources bằng 2 query riêng (thay vì JOIN FETCH cả 2 tầng trong 1 query)
     * vì Hibernate không cho JOIN FETCH nhiều hơn 1 collection List (bag) cùng lúc —
     * {@code chapters}/{@code lessons}/{@code resources} đều là List nên chỉ 1 trong 3 được
     * fetch cùng query gốc (xem CourseRepository.findByIdWithFullContent). Cùng persistence
     * context (@Transactional) nên Hibernate tự gắn kết quả vào đúng entity Chapter/Lesson đã
     * có trong {@code course.getChapters()} — không tạo thêm bản sao, không lazy-load per-row.
     */
    private void hydrateChaptersLessonsResources(Course course) {
        chapterRepository.findAllWithLessonsByCourseId(course.getId());
        lessonRepository.findAllWithResourcesByCourseId(course.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseResponse> listCourses(UUID instructorId, Pageable pageable, String keyword) {
        CourseListCacheGateway.Entry entry = courseListCacheGateway.find(instructorId, pageable, keyword);
        return new PageImpl<>(entry.getContent(), pageable, entry.getTotalElements());
    }

    @Override
    public List<CourseCompactResponse> listCoursesCompact(UUID instructorId) {
        // Tái sử dụng lại cache gateway với size 1000 để lấy gọn
        CourseListCacheGateway.Entry entry = courseListCacheGateway.find(instructorId, org.springframework.data.domain.PageRequest.of(0, 1000), null);
        return entry.getContent().stream()
                .map(c -> new CourseCompactResponse(c.getId(), c.getTitle()))
                .collect(Collectors.toList());
    }

    @Override
    public AssessStatsResponse getAssessStats(UUID instructorId, UUID courseId) {
        // Có thể check quyền sở hữu
        loadOwnedCourse(instructorId, courseId);
        long quizCount = quizRepository.countByCourseId(courseId);
        long bankCount = bankQuestionRepository.countByCourseId(courseId);
        return new AssessStatsResponse(quizCount, bankCount);
    }


    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public CourseResponse updateCourse(UUID instructorId, UUID courseId, UpdateCourseRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new CourseStateException("Cannot modify an archived course");
        }

        // Thông tin cơ bản (tên, mô tả, cấp độ, danh mục) luôn cập nhật trực tiếp — không cần duyệt
        if (request.getTitle() != null) {
            course.setTitle(request.getTitle());
            course.setSlug(generateUniqueSlug(request.getTitle(), courseId));
        }
        if (request.getDescription() != null)
            course.setDescription(project.lms_rikkei_edu.modules.course.util.CourseDescriptionSanitizer.sanitize(request.getDescription()));
        if (request.getLevel() != null) course.setLevel(request.getLevel());
        if (request.getCategoryId() != null) course.setCategory(resolveCategory(request.getCategoryId()));
        if (request.getThumbnailUrl() != null) course.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getChatEnabled() != null) course.setChatEnabled(request.getChatEnabled());
        if (request.getLearningOutcomes() != null) course.setLearningOutcomes(cleanStringList(request.getLearningOutcomes()));
        if (request.getRequirements() != null) course.setRequirements(cleanStringList(request.getRequirements()));

        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public void deleteCourse(UUID instructorId, UUID courseId) {
        Course course = loadOwnedCourse(instructorId, courseId);
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            throw new CourseStateException("Cannot delete a published course");
        }
        course.setDeletedAt(Instant.now());
        courseRepository.save(course);
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public CourseDetailResponse submitForApproval(UUID instructorId, UUID courseId, String changeSummary) {
        Course course = loadOwnedCourse(instructorId, courseId);

        // Force-load toàn bộ nội dung để build snapshot
        course.getChapters().forEach(ch -> ch.getLessons().forEach(l -> l.getResources().size()));

        // Bài học loại quiz phải trỏ tới 1 đề đã Hoạt động (PUBLISHED) — nếu không, học viên sẽ vào
        // được khóa học đã duyệt nhưng gặp bài học quiz bị khóa (xem LecturePlayer: quizLocked khi
        // quizStatus !== PUBLISHED). Áp dụng cho cả 3 nhánh gửi duyệt bên dưới.
        validateQuizzesActive(course);

        if (course.getStatus() == CourseStatus.DRAFT || course.getStatus() == CourseStatus.REJECTED) {
            long lessonCount = lessonRepository.countByCourseId(courseId);
            if (lessonCount == 0) {
                throw new CourseStateException("Course must have at least one lesson before submitting");
            }
            course.setStatus(CourseStatus.PENDING);
            course.setSubmittedAt(Instant.now());
            course.setRejectionReason(null);
            createCourseVersion(instructorId, courseId, changeSummary, course);
            saveLogWithSnapshot(instructorId, courseId, "SUBMITTED_FIRST", course);

        } else if (course.getStatus() == CourseStatus.PUBLISHED) {
            boolean hasDraftChanges = course.isHasPendingDraft();
            boolean hasResourceChanges = lessonResourceRepository.existsByCourseIdAndIsNewInUpdateTrue(courseId)
                    || lessonResourceRepository.existsByCourseIdAndPendingDeleteTrue(courseId);
            if (!hasDraftChanges && !hasResourceChanges) {
                throw new CourseStateException("Không có thay đổi nào để gửi duyệt");
            }
            revertPendingVersionsToDraft(courseId);
            course.setChangeSummary(changeSummary);
            course.setSubmittedAt(Instant.now());
            course.setDraftRejectionReason(null);
            course.setStatus(CourseStatus.PENDING_UPDATE);
            course.setPendingUpdateAt(Instant.now());
            createCourseVersion(instructorId, courseId, changeSummary, course);
            saveLogWithSnapshot(instructorId, courseId, "SUBMITTED_UPDATE", course);

        } else if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
            throw new CourseStateException("Cập nhật đang chờ admin duyệt — không thể gửi lại. Hãy đợi admin xử lý hoặc hủy cập nhật trước.");
        } else {
            throw new CourseStateException("Course cannot be submitted in current status: " + course.getStatus());
        }

        return courseMapper.toDetailResponse(courseRepository.save(course));
    }

    // Chặn gửi duyệt nếu còn bài học loại quiz trỏ tới đề chưa Hoạt động (DRAFT) hoặc đã Lưu trữ
    // (ARCHIVED) — cả 2 trạng thái đều khiến học viên gặp bài học bị khóa dù khóa học đã duyệt.
    private void validateQuizzesActive(Course course) {
        List<UUID> quizIds = course.getChapters().stream()
                .filter(ch -> !Boolean.TRUE.equals(ch.getPendingDelete()))
                .flatMap(ch -> ch.getLessons().stream())
                .filter(l -> !Boolean.TRUE.equals(l.getPendingDelete())
                        && l.getType() == LessonType.QUIZ && l.getQuizId() != null)
                .map(Lesson::getQuizId)
                .distinct()
                .toList();
        if (quizIds.isEmpty()) return;

        Map<UUID, QuizEntity> quizMap = quizRepository.findAllById(quizIds).stream()
                .collect(Collectors.toMap(QuizEntity::getId, q -> q));
        List<String> notActive = quizIds.stream()
                .map(quizMap::get)
                .filter(quiz -> quiz == null || quiz.getStatus() != QuizStatus.PUBLISHED)
                .map(quiz -> quiz != null ? quiz.getTitle() : "(không tìm thấy đề)")
                .toList();

        if (!notActive.isEmpty()) {
            throw new CourseStateException("Còn đề trắc nghiệm chưa Hoạt động: " + String.join(", ", notActive)
                    + ". Vui lòng chuyển các đề này sang Hoạt động trước khi gửi duyệt khóa học.");
        }
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public CourseDetailResponse withdrawFromReview(UUID instructorId, UUID courseId) {
        Course course = loadOwnedCourse(instructorId, courseId);

        if (course.getStatus() == CourseStatus.PENDING) {
            // Lần đầu pending → về DRAFT, revert CourseVersion PENDING → DRAFT
            course.setStatus(CourseStatus.DRAFT);
            course.setSubmittedAt(null);
            courseVersionRepository.findFirstByCourseIdAndStatus(courseId, "PENDING").ifPresent(v -> {
                v.setStatus(VERSION_STATUS_DRAFT);
                courseVersionRepository.save(v);
            });

        } else if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
            // Rút lại khỏi hàng chờ → về PUBLISHED, xóa tất cả draft content
            initChapters(course);
            clearAllDrafts(course);
            // clearAllDrafts() chỉ xóa hẳn resources thuộc chapter/lesson DRAFT (chưa từng live).
            // Resource được thêm/xóa NGAY trên chapter/lesson ĐANG LIVE (cùng lớp bug đã sửa ở
            // AdminCourseServiceImpl.rejectUpdate và nhánh PUBLISHED bên dưới) phải xử lý riêng —
            // thiếu đoạn này khiến "Hủy cập nhật" (rút một update đã nộp) để lại tài liệu chưa
            // từng publish vẫn hiện ra, và tài liệu bị đánh dấu chờ xóa không được khôi phục.
            lessonResourceRepository.findAllByCourseIdAndIsNewInUpdateTrue(courseId).forEach(r -> {
                r.setDeletedAt(Instant.now());
                r.setStatus("DELETED");
                lessonResourceRepository.save(r);
                String s3Key = r.getS3Key();
                if (s3Key != null && !s3Key.startsWith("ext://")
                        && courseVersionReferenceChecker.isSafeToDelete(courseId, s3Key)) {
                    s3Service.deleteObjectAsync(s3Key);
                }
            });
            lessonResourceRepository.findAllByCourseIdAndPendingDeleteTrue(courseId)
                    .forEach(r -> { r.setPendingDelete(false); r.setStatus("ACTIVE"); lessonResourceRepository.save(r); });
            course.setStatus(CourseStatus.PUBLISHED);
            course.setPendingUpdateAt(null);
            course.setSubmittedAt(null);
            // Revert CourseVersion PENDING → DRAFT để instructor có thể chỉnh sửa lại
            courseVersionRepository.findFirstByCourseIdAndStatus(courseId, "PENDING").ifPresent(v -> {
                v.setStatus(VERSION_STATUS_DRAFT);
                courseVersionRepository.save(v);
            });

        } else if (course.getStatus() == CourseStatus.PUBLISHED) {
            boolean hasDraft = course.isHasPendingDraft();
            boolean hasResourceChanges = lessonResourceRepository.existsByCourseIdAndIsNewInUpdateTrue(courseId)
                    || lessonResourceRepository.existsByCourseIdAndPendingDeleteTrue(courseId);
            if (!hasDraft && !hasResourceChanges) {
                throw new CourseStateException("No pending draft to withdraw");
            }
            // Hủy toàn bộ draft (kể cả resource changes). clearAllDrafts() đã xóa hẳn resources
            // thuộc các chapter/lesson DRAFT (chưa từng live) — vòng lặp dưới đây xử lý phần còn
            // lại: resource được thêm/xóa ngay trên chapter/lesson ĐANG LIVE.
            initChapters(course);
            clearAllDrafts(course);
            // Resource vừa thêm (isNewInUpdate) CHƯA TỪNG lên live — "hủy thay đổi" phải xóa hẳn,
            // không chỉ gỡ cờ (gỡ cờ mà giữ lại sẽ biến 1 file chưa từng được duyệt thành "đang live"
            // — cùng lớp bug đã sửa ở AdminCourseServiceImpl.rejectUpdate).
            lessonResourceRepository.findAllByCourseIdAndIsNewInUpdateTrue(courseId).forEach(r -> {
                r.setDeletedAt(Instant.now());
                r.setStatus("DELETED");
                lessonResourceRepository.save(r);
                String s3Key = r.getS3Key();
                if (s3Key != null && !s3Key.startsWith("ext://")
                        && courseVersionReferenceChecker.isSafeToDelete(courseId, s3Key)) {
                    s3Service.deleteObjectAsync(s3Key);
                }
            });
            // Resource này VẪN đang live, chỉ bị đánh dấu chờ xóa — hủy thay đổi = bỏ đánh dấu, giữ lại.
            lessonResourceRepository.findAllByCourseIdAndPendingDeleteTrue(courseId)
                    .forEach(r -> { r.setPendingDelete(false); r.setStatus("ACTIVE"); lessonResourceRepository.save(r); });
            course.setPendingUpdateAt(null);
            course.setSubmittedAt(null);

        } else {
            throw new CourseStateException("No pending draft to withdraw");
        }

        return courseMapper.toDetailResponse(courseRepository.save(course));
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public ChapterResponse addChapter(UUID instructorId, UUID courseId, CreateChapterRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        int nextOrder = chapterRepository.findMaxOrderIndexByCourseId(courseId) + 1;
        boolean draft = isLive(course);

        Chapter chapter = Chapter.builder()
                .course(course)
                .title(request.getTitle())
                .description(request.getDescription())
                .orderIndex(nextOrder)
                .isDraft(draft)
                .build();

        return chapterMapper.toResponse(chapterRepository.save(chapter));
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public ChapterResponse updateChapter(UUID instructorId, UUID courseId, UUID chapterId, UpdateChapterRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        Chapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        // Chapter title update: sửa trực tiếp ngay cả khi published (ít tác động, không cần draft)
        if (request.getTitle() != null) chapter.setTitle(request.getTitle());
        if (request.getDescription() != null) chapter.setDescription(request.getDescription());

        return chapterMapper.toResponse(chapterRepository.save(chapter));
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public void deleteChapter(UUID instructorId, UUID courseId, UUID chapterId) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        Chapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        if (isLive(course)) {
            if (Boolean.TRUE.equals(chapter.getIsDraft())) {
                // Chương draft chưa duyệt → xóa ngay không cần pending
                chapterRepository.delete(chapter);
            } else {
                // Chương live → đánh dấu chờ xóa (sẽ xóa thật khi admin duyệt)
                chapter.setPendingDelete(true);
                chapterRepository.save(chapter);
            }
        } else {
            chapterRepository.delete(chapter);
        }
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public List<ChapterResponse> reorderChapters(UUID instructorId, UUID courseId, List<UUID> chapterIds) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        List<Chapter> chapters = chapterRepository.findAllByCourseIdOrderByOrderIndexAsc(courseId);
        validateReorderSet(chapters.stream().map(Chapter::getId).toList(), chapterIds, "chương");

        Map<UUID, Chapter> byId = chapters.stream().collect(Collectors.toMap(Chapter::getId, c -> c));
        for (int i = 0; i < chapterIds.size(); i++) {
            byId.get(chapterIds.get(i)).setOrderIndex(i + 1);
        }
        chapterRepository.saveAll(chapters);

        return chapterRepository.findAllByCourseIdOrderByOrderIndexAsc(courseId).stream()
                .map(chapterMapper::toResponse).toList();
    }

    /** So khớp tập id gửi lên với tập id hiện có (không quan tâm thứ tự) — không cho thêm/bớt/nhầm phần tử qua API sắp xếp lại. */
    private void validateReorderSet(List<UUID> existingIds, List<UUID> requestedIds, String label) {
        if (requestedIds.size() != existingIds.size() || !new HashSet<>(requestedIds).equals(new HashSet<>(existingIds))) {
            throw new BusinessException("Danh sách sắp xếp lại " + label + " không khớp với danh sách hiện có");
        }
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public LessonResponse addLesson(UUID instructorId, UUID courseId, UUID chapterId, CreateLessonRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        Chapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        int nextOrder = lessonRepository.findMaxOrderIndexByChapterId(chapterId) + 1;
        boolean draft = isLive(course);

        UUID quizId = request.getType() == LessonType.QUIZ
                ? resolveQuizForLesson(courseId, instructorId, request.getQuizId(), request.getNewQuiz(), null)
                : null;

        Lesson lesson = Lesson.builder()
                .chapter(chapter)
                .courseId(courseId)
                .title(request.getTitle())
                .type(request.getType())
                .contentText(request.getContentText())
                .isPreview(request.getIsPreview() != null ? request.getIsPreview() : false)
                .orderIndex(nextOrder)
                .isDraft(draft)
                .quizId(quizId)
                .build();

        return lessonMapper.toResponse(lessonRepository.save(lesson));
    }

    /**
     * Gắn quiz cho 1 lesson loại QUIZ — hoặc gắn quiz đã có sẵn ({@code quizId}), hoặc tạo mới
     * (shell, chưa có câu hỏi) từ {@code newQuiz}. Bắt buộc đúng 1 trong 2. {@code excludeLessonId}
     * dùng khi đổi quiz của 1 lesson đã tồn tại (bỏ qua chính lesson đó khi kiểm tra unique).
     */
    private UUID resolveQuizForLesson(UUID courseId, UUID instructorId, UUID quizId,
                                       QuizMetadataRequest newQuiz, UUID excludeLessonId) {
        if ((quizId == null) == (newQuiz == null)) {
            throw new BusinessException("Bài học loại đề trắc nghiệm phải chọn đúng 1 trong 2: gắn đề có sẵn hoặc tạo đề mới");
        }
        if (newQuiz != null) {
            QuizSummaryResponse created = quizService.create(courseId, instructorId, newQuiz);
            return created.getId();
        }
        quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException("Đề trắc nghiệm không tồn tại trong khóa học này"));
        lessonRepository.findByQuizId(quizId)
                .filter(l -> !l.getId().equals(excludeLessonId))
                .ifPresent(l -> {
                    throw new BusinessException("Đề trắc nghiệm này đã được gắn với 1 bài học khác");
                });
        return quizId;
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public LessonResponse updateLesson(UUID instructorId, UUID courseId, UUID chapterId, UUID lessonId, UpdateLessonRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        Lesson lesson = lessonRepository.findByIdAndCourseId(lessonId, courseId)
                .orElseThrow(() -> new LessonNotFoundException(lessonId));

        if (isLive(course) && !Boolean.TRUE.equals(lesson.getIsDraft())) {
            // Lesson live trong published course → ghi vào draft fields
            if (request.getTitle() != null) lesson.setDraftTitle(request.getTitle());
            if (request.getContentText() != null) lesson.setDraftContentText(request.getContentText());
            if (request.getIsPreview() != null) lesson.setIsPreview(request.getIsPreview());
        } else {
            // DRAFT/REJECTED course, hoặc lesson chính nó đang là draft → sửa trực tiếp
            if (request.getTitle() != null) lesson.setTitle(request.getTitle());
            if (request.getType() != null) lesson.setType(request.getType());
            if (request.getContentText() != null) lesson.setContentText(request.getContentText());
            if (request.getIsPreview() != null) lesson.setIsPreview(request.getIsPreview());
        }

        // Đổi quiz gắn với lesson QUIZ — áp dụng ngay bất kể course live/draft (giống reorder ở
        // trên), không qua cơ chế draft-approval vì đây là đổi assessment, không phải content-diff dạng text.
        if (lesson.getType() == LessonType.QUIZ && (request.getQuizId() != null || request.getNewQuiz() != null)) {
            UUID newQuizId = resolveQuizForLesson(courseId, instructorId, request.getQuizId(), request.getNewQuiz(), lessonId);
            lesson.setQuizId(newQuizId);
            lessonRepository.save(lesson);

            if (Boolean.TRUE.equals(request.getResetProgressForInProgressStudents())) {
                studentCourseService.resetLessonProgressForInProgressStudents(courseId, lessonId);
            }
        }

        return lessonMapper.toResponse(lessonRepository.save(lesson));
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public List<LessonResponse> reorderLessons(UUID instructorId, UUID courseId, UUID chapterId, List<UUID> lessonIds) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        List<Lesson> lessons = lessonRepository.findAllByChapterIdOrderByOrderIndexAsc(chapterId);
        validateReorderSet(lessons.stream().map(Lesson::getId).toList(), lessonIds, "bài học");

        Map<UUID, Lesson> byId = lessons.stream().collect(Collectors.toMap(Lesson::getId, l -> l));
        for (int i = 0; i < lessonIds.size(); i++) {
            byId.get(lessonIds.get(i)).setOrderIndex(i + 1);
        }
        lessonRepository.saveAll(lessons);

        return lessonRepository.findAllByChapterIdOrderByOrderIndexAsc(chapterId).stream()
                .map(lessonMapper::toResponse).toList();
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public void deleteLesson(UUID instructorId, UUID courseId, UUID chapterId, UUID lessonId) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        Lesson lesson = lessonRepository.findByIdAndCourseId(lessonId, courseId)
                .orElseThrow(() -> new LessonNotFoundException(lessonId));

        if (isLive(course)) {
            if (Boolean.TRUE.equals(lesson.getIsDraft())) {
                // Lesson draft chưa duyệt → xóa ngay
                lessonRepository.delete(lesson);
            } else {
                // Lesson live → đánh dấu chờ xóa (sẽ xóa thật khi admin duyệt)
                lesson.setPendingDelete(true);
                lessonRepository.save(lesson);
            }
        } else {
            lessonRepository.delete(lesson);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseApprovalLogResponse> getCourseHistory(UUID instructorId, UUID courseId) {
        loadOwnedCourse(instructorId, courseId); // verify ownership
        return approvalLogRepository.findByCourseIdOrderByCreatedAtAsc(courseId).stream()
                .map(log -> CourseApprovalLogResponse.builder()
                        .id(log.getId())
                        .action(log.getAction())
                        .reason(log.getReason())
                        .createdAt(log.getCreatedAt())
                        .actorType(isInstructorAction(log.getAction()) ? "INSTRUCTOR" : "ADMIN")
                        .snapshot(log.getSnapshot())
                        .build())
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True nếu khóa học đang live (PUBLISHED hoặc PENDING_UPDATE) */
    private boolean isLive(Course course) {
        return course.getStatus() == CourseStatus.PUBLISHED
                || course.getStatus() == CourseStatus.PENDING_UPDATE;
    }



    /** Force-load chapters + lessons (cần gọi trong transaction) */
    private void initChapters(Course course) {
        course.getChapters().forEach(ch ->
            ch.getLessons().forEach(l -> l.getResources().size())
        );
    }

    /**
     * Xóa toàn bộ nội dung draft khi instructor withdraw hoặc admin reject.
     * Gọi sau initChapters().
     */
    private void clearAllDrafts(Course course) {
        // Xóa draft metadata
        course.setDraftTitle(null);
        course.setDraftDescription(null);
        course.setDraftLevel(null);
        course.setDraftThumbnailUrl(null);
        course.setChangeSummary(null);
        course.setDraftRejectionReason(null);

        List<Chapter> draftChapters = new ArrayList<>();
        for (Chapter ch : course.getChapters()) {
            if (Boolean.TRUE.equals(ch.getIsDraft())) {
                draftChapters.add(ch);
            } else {
                ch.setPendingDelete(false);
                // Xử lý lessons trong chương live
                List<Lesson> draftLessons = new ArrayList<>();
                for (Lesson l : ch.getLessons()) {
                    if (Boolean.TRUE.equals(l.getIsDraft())) {
                        draftLessons.add(l);
                    } else {
                        l.setPendingDelete(false);
                        l.setDraftTitle(null);
                        l.setDraftContentText(null);
                    }
                }
                // Thu thập S3 keys để cleanup sau
                List<String> keysToDelete = draftLessons.stream()
                    .filter(l -> l.getResources() != null)
                    .flatMap(l -> l.getResources().stream())
                    .map(LessonResource::getS3Key)
                    .filter(k -> k != null && !k.startsWith("ext://"))
                    .distinct().toList();
                // Clear resources collection — đánh dấu orphan, sau đó flush ngay để xóa resource trước lesson
                draftLessons.forEach(l -> l.getResources().clear());
                entityManager.flush(); // đảm bảo DELETE lesson_resources chạy trước DELETE lessons
                // Xóa S3 sau khi DB đã xóa — bỏ qua key nào còn được version khác tham chiếu
                keysToDelete.stream()
                        .filter(key -> courseVersionReferenceChecker.isSafeToDelete(course.getId(), key))
                        .forEach(s3Service::deleteObjectAsync);
                // Xóa draft lessons qua orphanRemoval
                ch.getLessons().removeAll(draftLessons);
            }
        }
        // Thu thập S3 keys của lessons thuộc draft chapters
        List<String> chapterKeysToDelete = draftChapters.stream()
            .filter(ch -> ch.getLessons() != null)
            .flatMap(ch -> ch.getLessons().stream())
            .filter(l -> l.getResources() != null)
            .flatMap(l -> l.getResources().stream())
            .map(LessonResource::getS3Key)
            .filter(k -> k != null && !k.startsWith("ext://"))
            .distinct().toList();
        // Clear resources của lessons thuộc draft chapters, flush trước khi xóa chapters
        draftChapters.forEach(ch ->
            ch.getLessons().forEach(l -> l.getResources().clear())
        );
        entityManager.flush(); // đảm bảo DELETE lesson_resources chạy trước DELETE lessons/chapters
        chapterKeysToDelete.stream()
                .filter(key -> courseVersionReferenceChecker.isSafeToDelete(course.getId(), key))
                .forEach(s3Service::deleteObjectAsync);
        // Xóa draft chapters qua orphanRemoval (cascade → lessons)
        course.getChapters().removeAll(draftChapters);
    }

    // private void saveLog(UUID actorId, UUID courseId, String action) {
    //     approvalLogRepository.save(
    //             CourseApprovalLog.builder()
    //                     .courseId(courseId)
    //                     .adminId(actorId)
    //                     .action(action)
    //                     .createdAt(Instant.now())
    //                     .build()
    //     );
    // }

    private void saveLogWithSnapshot(UUID actorId, UUID courseId, String action, Course course) {
        String snapshotJson = null;
        try {
            snapshotJson = objectMapper.writeValueAsString(buildSnapshot(course));
        } catch (Exception e) {
            log.warn("Failed to serialize course snapshot for courseId={}: {}", courseId, e.getMessage());
        }
        approvalLogRepository.save(
                CourseApprovalLog.builder()
                        .courseId(courseId)
                        .adminId(actorId)
                        .action(action)
                        .createdAt(Instant.now())
                        .snapshot(snapshotJson)
                        .build()
        );
    }

    /**
     * Snapshot phản ánh trạng thái course SAU KHI duyệt (preview of approved state):
     * - SUBMITTED_FIRST: toàn bộ chapter/lesson hiện tại
     * - SUBMITTED_UPDATE: apply draft — bao gồm chapter/lesson mới (isDraft=true),
     *   loại bỏ pendingDelete, apply draftTitle/draftContentText
     */
    private CourseSnapshotDto buildSnapshot(Course course) {
        boolean isUpdate = course.getStatus() == CourseStatus.PENDING_UPDATE
                || course.getStatus() == CourseStatus.PUBLISHED;

        String title       = isUpdate && course.getDraftTitle()       != null ? course.getDraftTitle()       : course.getTitle();
        String description = isUpdate && course.getDraftDescription() != null ? course.getDraftDescription() : course.getDescription();
        String level       = isUpdate && course.getDraftLevel()       != null ? course.getDraftLevel().name() : (course.getLevel() != null ? course.getLevel().name() : null);
        String thumbnail   = isUpdate && course.getDraftThumbnailUrl()!= null ? course.getDraftThumbnailUrl(): course.getThumbnailUrl();
        String catName     = course.getCategory() != null ? course.getCategory().getName() : null;

        List<CourseSnapshotDto.ChapterSnap> chapterSnaps = new ArrayList<>();
        for (Chapter ch : course.getChapters()) {
            if (isUpdate && Boolean.TRUE.equals(ch.getPendingDelete())) continue;

            List<CourseSnapshotDto.LessonSnap> lessonSnaps = new ArrayList<>();
            for (Lesson l : ch.getLessons()) {
                if (isUpdate && Boolean.TRUE.equals(l.getPendingDelete())) continue;

                String lTitle   = (isUpdate && l.getDraftTitle()       != null) ? l.getDraftTitle()       : l.getTitle();
                String lContent = (isUpdate && l.getDraftContentText() != null) ? l.getDraftContentText() : l.getContentText();

                List<CourseSnapshotDto.ResourceSnap> resSnaps = (l.getResources() == null) ? List.of()
                    : l.getResources().stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getPendingDelete()))
                        .map(r -> CourseSnapshotDto.ResourceSnap.builder()
                                .displayName(r.getDisplayName() != null ? r.getDisplayName() : r.getOriginalFilename())
                                .resourceType(r.getResourceType() != null ? r.getResourceType().name() : null)
                                .mimeType(r.getMimeType())
                                .fileSizeBytes(r.getFileSizeBytes())
                                .s3Key(r.getS3Key() != null && !r.getS3Key().startsWith("ext://") ? r.getS3Key() : null)
                                .build())
                        .toList();

                lessonSnaps.add(CourseSnapshotDto.LessonSnap.builder()
                        .title(lTitle)
                        .lessonType(l.getType() != null ? l.getType().name() : null)
                        .contentText(lContent)
                        .durationSeconds(l.getDurationSeconds())
                        .orderIndex(l.getOrderIndex())
                        .resources(resSnaps)
                        .build());
            }

            chapterSnaps.add(CourseSnapshotDto.ChapterSnap.builder()
                    .title(ch.getTitle())
                    .orderIndex(ch.getOrderIndex())
                    .lessons(lessonSnaps)
                    .build());
        }

        return CourseSnapshotDto.builder()
                .title(title)
                .description(description)
                .level(level)
                .thumbnailUrl(thumbnail)
                .categoryName(catName)
                .chapters(chapterSnaps)
                .build();
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public CourseDetailResponse rollbackToVersion(UUID instructorId, UUID courseId, UUID versionId) {
        Course course = loadOwnedCourse(instructorId, courseId);

        CourseVersion version = courseVersionRepository.findById(versionId)
                .filter(v -> v.getCourseId().equals(courseId))
                .orElseThrow(() -> new IllegalArgumentException("Version không tồn tại hoặc không thuộc khóa học này"));

        boolean isDraftRestore = VERSION_STATUS_DRAFT.equals(version.getStatus());

        if (!isDraftRestore && course.getStatus() != CourseStatus.PUBLISHED) {
            throw new CourseStateException("Chỉ có thể rollback về APPROVED khi khóa học đang PUBLISHED");
        }
        if (!isDraftRestore && !"APPROVED".equals(version.getStatus())) {
            throw new CourseStateException("Chỉ có thể khôi phục version APPROVED hoặc bản nháp DRAFT đã lưu");
        }
        if (course.getStatus() == CourseStatus.PENDING || course.getStatus() == CourseStatus.PENDING_UPDATE) {
            throw new CourseStateException("Không thể khôi phục khi khóa học đang chờ duyệt");
        }

        CourseSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(version.getSnapshot(), CourseSnapshotDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể đọc snapshot của version này: " + e.getMessage());
        }

        // Force-load toàn bộ nội dung hiện tại
        initChapters(course);

        // 1. Xóa draft cũ nếu có — resource thêm trong draft hiện tại (isNewInUpdate) chưa từng lên
        // live nên phải xóa hẳn (không chỉ gỡ cờ, xem ghi chú ở withdrawFromReview/rejectUpdate);
        // resource đang live chỉ bị đánh dấu chờ xóa (pendingDelete) thì gỡ đánh dấu, giữ lại.
        clearAllDrafts(course);
        lessonResourceRepository.findAllByCourseIdAndIsNewInUpdateTrue(courseId).forEach(r -> {
            r.setDeletedAt(Instant.now());
            r.setStatus("DELETED");
            lessonResourceRepository.save(r);
            String s3Key = r.getS3Key();
            if (s3Key != null && !s3Key.startsWith("ext://")
                    && courseVersionReferenceChecker.isSafeToDelete(courseId, s3Key)) {
                s3Service.deleteObjectAsync(s3Key);
            }
        });
        lessonResourceRepository.findAllByCourseIdAndPendingDeleteTrue(courseId)
                .forEach(r -> { r.setPendingDelete(false); r.setStatus("ACTIVE"); lessonResourceRepository.save(r); });

        // 2. Apply diff metadata
        if (!Objects.equals(snapshot.getTitle(), course.getTitle()))
            course.setDraftTitle(snapshot.getTitle());
        if (!Objects.equals(snapshot.getDescription(), course.getDescription()))
            course.setDraftDescription(snapshot.getDescription());
        if (snapshot.getLevel() != null) {
            String currentLevel = course.getLevel() != null ? course.getLevel().name() : null;
            if (!snapshot.getLevel().equals(currentLevel))
                course.setDraftLevel(CourseLevel.valueOf(snapshot.getLevel()));
        }
        if (!Objects.equals(snapshot.getThumbnailUrl(), course.getThumbnailUrl()))
            course.setDraftThumbnailUrl(snapshot.getThumbnailUrl());

        // 3. Apply diff chapters + lessons + resources
        List<CourseSnapshotDto.ChapterSnap> snapChapters =
                snapshot.getChapters() != null ? snapshot.getChapters() : List.of();
        Set<Integer> snapChapterOrders = snapChapters.stream()
                .map(CourseSnapshotDto.ChapterSnap::getOrderIndex)
                .collect(Collectors.toSet());

        // Đánh dấu chapters trong live nhưng không có trong snapshot → pendingDelete
        Map<Integer, Chapter> liveChaptersByOrder = new HashMap<>();
        for (Chapter ch : course.getChapters()) {
            liveChaptersByOrder.put(ch.getOrderIndex(), ch);
            if (!snapChapterOrders.contains(ch.getOrderIndex())) {
                ch.setPendingDelete(true);
                chapterRepository.save(ch);
            }
        }

        // Chapters trong snapshot — thêm mới hoặc diff lessons
        for (CourseSnapshotDto.ChapterSnap snapCh : snapChapters) {
            Chapter liveCh = liveChaptersByOrder.get(snapCh.getOrderIndex());
            if (liveCh == null) {
                // Chapter không còn sống ở vị trí này — thử "hồi sinh" chapter đã soft-delete cùng
                // vị trí trước khi tạo mới trắng tay: nếu tìm thấy, lessons/resources gốc của nó
                // vẫn còn nguyên trong DB (chỉ ẩn qua @SQLRestriction do admin duyệt xóa trước đó),
                // hồi sinh xong sẽ tự hiện lại đầy đủ — khác hẳn tạo chapter mới (chỉ có title,
                // không có lesson/tài liệu nào, xem phản hồi người dùng: rollback không thấy lại
                // tài liệu cũ).
                Chapter resurrected = chapterRepository
                        .findSoftDeletedByCourseIdAndOrderIndex(courseId, snapCh.getOrderIndex())
                        .orElse(null);
                if (resurrected != null) {
                    resurrected.setDeletedAt(null);
                    // isDraft=true (không phải false) — chapter này chưa nằm trong bản đã duyệt/
                    // publish hiện tại, cần được gửi duyệt lại giống hệt như tạo chapter mới, nếu
                    // không hasPendingDraft() sẽ không nhận ra có gì cần gửi, ẩn mất nút "Gửi duyệt"
                    // (xem phản hồi người dùng) và học viên sẽ thấy lại nội dung chưa qua duyệt.
                    resurrected.setIsDraft(true);
                    resurrected.setPendingDelete(false);
                    resurrected.setTitle(snapCh.getTitle());
                    liveCh = chapterRepository.save(resurrected);
                    // Đồng bộ lại collection trong bộ nhớ NGAY trong transaction này — nếu không,
                    // response trả về ngay lúc rollback (course.isHasPendingDraft() tính trên chính
                    // course.getChapters() đã load từ đầu hàm) vẫn thấy danh sách chapters CŨ (thiếu
                    // chapter vừa hồi sinh), khiến nút "Gửi duyệt" không hiện ngay mà phải load lại
                    // trang mới đúng.
                    course.getChapters().add(liveCh);
                    // Lessons của chapter này cũng đã bị soft-delete cùng lúc (xem
                    // AdminCourseServiceImpl.approveUpdate) — applyLessonDiffForRollback tự hồi
                    // sinh từng lesson tương ứng bên dưới.
                    applyLessonDiffForRollback(liveCh, snapCh, courseId);
                } else {
                    // Chưa từng tồn tại → tạo mới dạng draft
                    Chapter newCh = Chapter.builder()
                            .course(course)
                            .title(snapCh.getTitle())
                            .orderIndex(snapCh.getOrderIndex())
                            .isDraft(true)
                            .build();
                    liveCh = chapterRepository.save(newCh);
                    // Tạo luôn lessons trong chapter mới
                    if (snapCh.getLessons() != null) {
                        for (CourseSnapshotDto.LessonSnap snapL : snapCh.getLessons()) {
                            Lesson newL = buildDraftLesson(liveCh, courseId, snapL);
                            lessonRepository.save(newL);
                        }
                    }
                }
            } else {
                // Chapter tồn tại → diff lessons
                applyLessonDiffForRollback(liveCh, snapCh, courseId);
            }
        }

        courseRepository.save(course);
        return courseMapper.toDetailResponse(course);
    }

    private void applyLessonDiffForRollback(Chapter chapter, CourseSnapshotDto.ChapterSnap snapCh, UUID courseId) {
        List<CourseSnapshotDto.LessonSnap> snapLessons =
                snapCh.getLessons() != null ? snapCh.getLessons() : List.of();
        Set<Integer> snapLessonOrders = snapLessons.stream()
                .map(CourseSnapshotDto.LessonSnap::getOrderIndex)
                .collect(Collectors.toSet());

        Map<Integer, Lesson> liveLessonsByOrder = new HashMap<>();
        for (Lesson l : chapter.getLessons()) {
            liveLessonsByOrder.put(l.getOrderIndex(), l);
            if (!snapLessonOrders.contains(l.getOrderIndex())) {
                l.setPendingDelete(true);
                lessonRepository.save(l);
            }
        }

        for (CourseSnapshotDto.LessonSnap snapL : snapLessons) {
            Lesson liveL = liveLessonsByOrder.get(snapL.getOrderIndex());
            if (liveL == null) {
                // Không còn sống ở vị trí này — thử hồi sinh lesson đã soft-delete cùng vị trí
                // trước khi tạo mới trắng tay: resources của nó chưa từng bị đụng tới (chỉ ẩn theo
                // lesson cha), nên hồi sinh xong sẽ tự động hiện lại đầy đủ tài liệu gốc.
                Lesson resurrected = lessonRepository
                        .findSoftDeletedByChapterIdAndOrderIndex(chapter.getId(), snapL.getOrderIndex())
                        .orElse(null);
                if (resurrected != null) {
                    resurrected.setDeletedAt(null);
                    // isDraft=true — xem ghi chú tương tự ở nhánh resurrect chapter phía trên.
                    resurrected.setIsDraft(true);
                    resurrected.setPendingDelete(false);
                    if (!Objects.equals(snapL.getTitle(), resurrected.getTitle()))
                        resurrected.setDraftTitle(snapL.getTitle());
                    if (!Objects.equals(snapL.getContentText(), resurrected.getContentText()))
                        resurrected.setDraftContentText(snapL.getContentText());
                    applyResourceDiffForRollback(resurrected, snapL);
                    lessonRepository.save(resurrected);
                    // Đồng bộ lại collection trong bộ nhớ NGAY trong transaction này — xem ghi chú
                    // tương tự ở nhánh resurrect chapter phía trên (course.getChapters().add).
                    chapter.getLessons().add(resurrected);
                } else {
                    lessonRepository.save(buildDraftLesson(chapter, courseId, snapL));
                }
            } else {
                // Apply title/content diff
                if (!Objects.equals(snapL.getTitle(), liveL.getTitle()))
                    liveL.setDraftTitle(snapL.getTitle());
                if (!Objects.equals(snapL.getContentText(), liveL.getContentText()))
                    liveL.setDraftContentText(snapL.getContentText());
                // Resource diff — chỉ đánh dấu xóa những gì không có trong snapshot
                applyResourceDiffForRollback(liveL, snapL);
                lessonRepository.save(liveL);
            }
        }
    }

    private void applyResourceDiffForRollback(Lesson lesson, CourseSnapshotDto.LessonSnap snapL) {
        List<CourseSnapshotDto.ResourceSnap> snapResources =
                snapL.getResources() != null ? snapL.getResources() : List.of();
        Set<String> snapNames = snapResources.stream()
                .map(CourseSnapshotDto.ResourceSnap::getDisplayName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> liveNames = new HashSet<>();
        for (LessonResource r : lesson.getResources()) {
            String name = r.getDisplayName() != null ? r.getDisplayName() : r.getOriginalFilename();
            liveNames.add(name);
            if (!snapNames.contains(name)) {
                r.setPendingDelete(true);
                r.setStatus("PENDING_DELETE");
                lessonResourceRepository.save(r);
            }
        }

        // Tài liệu có trong snapshot nhưng không còn sống ở lesson này — thử hồi sinh tài liệu đã
        // soft-delete cùng tên trước khi bỏ cuộc: từ khi admin duyệt xóa chuyển sang soft-delete
        // (giữ nguyên row + file S3 nếu còn version nào tham chiếu, xem CourseVersionReferenceChecker),
        // rollback về đúng version đó CÓ THỂ phục hồi được — khác với trước đây (ghi chú cũ:
        // "không thể tạo lại resource đã xóa" chỉ còn đúng khi row đã bị xóa cứng thật sự).
        for (CourseSnapshotDto.ResourceSnap snapR : snapResources) {
            String name = snapR.getDisplayName();
            if (name == null || liveNames.contains(name)) continue;
            lessonResourceRepository.findSoftDeletedByLessonIdAndDisplayName(lesson.getId(), name)
                    .ifPresent(r -> {
                        r.setDeletedAt(null);
                        r.setStatus("ACTIVE");
                        r.setPendingDelete(false);
                        // true (không phải false) — tài liệu này chưa nằm trong bản đã duyệt/publish
                        // hiện tại, cần gửi duyệt lại. Frontend tính nút "Gửi cập nhật" ngoài
                        // course.hasPendingDraft còn xét riêng allResources.some(r => r.isNewInUpdate
                        // || r.pendingDelete) — set false ở đây tái diễn đúng lỗi đã sửa ở cấp
                        // chapter/lesson (isDraft phải true), khiến nút không hiện sau khi rollback.
                        r.setIsNewInUpdate(true);
                        lessonResourceRepository.save(r);
                    });
        }
    }

    private Lesson buildDraftLesson(Chapter chapter, UUID courseId, CourseSnapshotDto.LessonSnap snapL) {
        LessonType type = null;
        if (snapL.getLessonType() != null) {
            try { type = LessonType.valueOf(snapL.getLessonType()); } catch (IllegalArgumentException e) { log.warn("Unknown lesson type in snapshot: {}", snapL.getLessonType()); }
        }
        return Lesson.builder()
                .chapter(chapter)
                .courseId(courseId)
                .title(snapL.getTitle())
                .type(type)
                .contentText(snapL.getContentText())
                .orderIndex(snapL.getOrderIndex())
                .isDraft(true)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseVersionResponse> getCourseVersions(UUID instructorId, UUID courseId) {
        loadOwnedCourse(instructorId, courseId);
        return courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(courseId).stream()
                .map(v -> CourseVersionResponse.builder()
                        .id(v.getId())
                        .versionNumber(v.getVersionNumber())
                        .status(v.getStatus())
                        .changeSummary(v.getChangeSummary())
                        .rejectionReason(v.getRejectionReason())
                        .label(v.getLabel())
                        .submittedAt(v.getSubmittedAt())
                        .reviewedAt(v.getReviewedAt())
                        .snapshot(v.getSnapshot())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public CourseVersionResponse saveDraft(UUID instructorId, UUID courseId, String label) {
        Course course = loadOwnedCourse(instructorId, courseId);

        if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
            throw new CourseStateException("Không thể lưu bản nháp khi đang chờ admin duyệt");
        }
        if (course.getStatus() == CourseStatus.PENDING) {
            throw new CourseStateException("Không thể lưu bản nháp khi đang chờ duyệt lần đầu");
        }

        long draftCount = courseVersionRepository.countByCourseIdAndStatus(courseId, VERSION_STATUS_DRAFT);
        if (draftCount >= 3) {
            throw new CourseStateException("Đã đạt giới hạn 3 bản nháp. Vui lòng xóa bớt bản nháp cũ trước khi lưu mới.");
        }

        String snapshotJson = null;
        try {
            // Load resources để có trong snapshot
            course.getChapters().forEach(ch -> ch.getLessons().forEach(l -> l.getResources().size()));
            snapshotJson = objectMapper.writeValueAsString(buildSnapshot(course));
        } catch (Exception e) {
            log.warn("Failed to serialize snapshot for draft courseId={}: {}", courseId, e.getMessage());
        }

        CourseVersion draft = courseVersionRepository.save(CourseVersion.builder()
                .courseId(courseId)
                .versionNumber(null)        // DRAFT không có version number chính thức
                .status(VERSION_STATUS_DRAFT)
                .snapshot(snapshotJson)
                .label(label != null && !label.isBlank() ? label.trim() : null)
                .submittedBy(instructorId)
                .submittedAt(Instant.now())
                .build());

        return CourseVersionResponse.builder()
                .id(draft.getId())
                .versionNumber(null)
                .status(VERSION_STATUS_DRAFT)
                .label(draft.getLabel())
                .submittedAt(draft.getSubmittedAt())
                .snapshot(draft.getSnapshot())
                .build();
    }

    @Override
    @Transactional
    public void deleteDraftVersion(UUID instructorId, UUID courseId, UUID versionId) {
        loadOwnedCourse(instructorId, courseId);
        CourseVersion version = courseVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_VERSION_NOT_FOUND));
        if (!version.getCourseId().equals(courseId)) {
            throw new CourseStateException("Version không thuộc khóa học này");
        }
        if (!VERSION_STATUS_DRAFT.equals(version.getStatus()) && !"REJECTED".equals(version.getStatus())) {
            throw new CourseStateException("Chỉ có thể xóa bản nháp DRAFT hoặc REJECTED");
        }
        String snapshotJson = version.getSnapshot();
        // Xóa version TRƯỚC rồi flush — cleanupSnapshotS3Keys kiểm tra tham chiếu qua các
        // CourseVersion còn lại; nếu chưa xóa/flush, chính version này vẫn còn trong DB và sẽ luôn
        // "tự tham chiếu" key của chính nó, khiến không bao giờ xóa được file nào.
        courseVersionRepository.delete(version);
        courseVersionRepository.flush();
        // Cleanup S3: xóa các file trong snapshot không còn được tham chiếu bởi lesson_resources
        // hoặc bất kỳ CourseVersion nào khác của khóa học.
        cleanupSnapshotS3Keys(courseId, snapshotJson);
    }

    @Override
    @Transactional
    public void renameDraftVersion(UUID instructorId, UUID courseId, UUID versionId, String label) {
        loadOwnedCourse(instructorId, courseId);
        CourseVersion version = courseVersionRepository.findById(versionId)
                .filter(v -> v.getCourseId().equals(courseId))
                .orElseThrow(() -> new IllegalArgumentException(ERR_VERSION_NOT_FOUND));
        if (!VERSION_STATUS_DRAFT.equals(version.getStatus())) {
            throw new CourseStateException("Chỉ có thể đổi tên bản nháp DRAFT");
        }
        version.setLabel(label != null ? label.trim() : null);
        courseVersionRepository.save(version);
    }

    private void revertPendingVersionsToDraft(UUID courseId) {
        courseVersionRepository.findByCourseIdAndStatusOrderBySubmittedAtDesc(courseId, "PENDING")
                .forEach(v -> {
                    v.setStatus(VERSION_STATUS_DRAFT);
                    courseVersionRepository.save(v);
                });
    }

    private void cleanupSnapshotS3Keys(UUID courseId, String snapshotJson) {
        if (snapshotJson == null) return;
        try {
            CourseSnapshotDto snap = objectMapper.readValue(snapshotJson, CourseSnapshotDto.class);
            if (snap.getChapters() == null) return;
            snap.getChapters().stream()
                .filter(ch -> ch.getLessons() != null)
                .flatMap(ch -> ch.getLessons().stream())
                .filter(l -> l.getResources() != null)
                .flatMap(l -> l.getResources().stream())
                .map(CourseSnapshotDto.ResourceSnap::getS3Key)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .forEach(key -> {
                    // Chỉ xóa S3 nếu không còn lesson_resource nào dùng key này, VÀ không còn
                    // CourseVersion nào khác của khóa học còn tham chiếu key này trong snapshot.
                    if (!lessonResourceRepository.existsByS3KeyAndDeletedAtIsNull(key)
                            && courseVersionReferenceChecker.isSafeToDelete(courseId, key)) {
                        s3Service.deleteObjectAsync(key);
                    }
                });
        } catch (Exception e) {
            log.warn("Không thể parse snapshot để cleanup S3: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public CourseVersionResponse cloneVersionAsDraft(UUID instructorId, UUID courseId, UUID sourceVersionId, String label) {
        loadOwnedCourse(instructorId, courseId);

        CourseVersion source = courseVersionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_VERSION_NOT_FOUND));
        if (!source.getCourseId().equals(courseId)) {
            throw new CourseStateException("Version không thuộc khóa học này");
        }

        long draftCount = courseVersionRepository.countByCourseIdAndStatus(courseId, VERSION_STATUS_DRAFT);
        if (draftCount >= 3) {
            throw new CourseStateException("Đã đạt giới hạn 3 bản nháp. Vui lòng xóa bớt bản nháp cũ trước khi lưu mới.");
        }

        String draftLabel = (label != null && !label.isBlank()) ? label.trim()
                : (source.getStatus().equals(VERSION_STATUS_DRAFT) ? source.getLabel()
                : "Clone từ v" + source.getVersionNumber());

        CourseVersion draft = courseVersionRepository.save(CourseVersion.builder()
                .courseId(courseId)
                .versionNumber(null)
                .status(VERSION_STATUS_DRAFT)
                .snapshot(source.getSnapshot())
                .label(draftLabel)
                .submittedBy(instructorId)
                .submittedAt(Instant.now())
                .build());

        return CourseVersionResponse.builder()
                .id(draft.getId())
                .status(VERSION_STATUS_DRAFT)
                .label(draft.getLabel())
                .submittedAt(draft.getSubmittedAt())
                .snapshot(draft.getSnapshot())
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public CourseVersionResponse submitVersion(UUID instructorId, UUID courseId, UUID versionId) {
        Course course = loadOwnedCourse(instructorId, courseId);

        if (course.getStatus() != CourseStatus.DRAFT && course.getStatus() != CourseStatus.REJECTED
                && course.getStatus() != CourseStatus.PUBLISHED && course.getStatus() != CourseStatus.PENDING_UPDATE) {
            throw new CourseStateException("Không thể nộp duyệt phiên bản khi khóa học đang ở trạng thái: " + course.getStatus());
        }

        CourseVersion target = courseVersionRepository.findById(versionId)
                .filter(v -> v.getCourseId().equals(courseId))
                .orElseThrow(() -> new IllegalArgumentException("Version không tồn tại hoặc không thuộc khóa học này"));

        if (!VERSION_STATUS_DRAFT.equals(target.getStatus())) {
            throw new CourseStateException("Chỉ có thể nộp duyệt bản nháp DRAFT");
        }

        // Revert version PENDING hiện tại (nếu có) về DRAFT
        revertPendingVersionsToDraft(courseId);

        // Gán version number mới và đổi status
        int nextNum = courseVersionRepository.findMaxVersionNumberByCourseId(courseId) + 1;
        target.setStatus("PENDING");
        target.setVersionNumber(nextNum);
        target.setSubmittedAt(Instant.now());
        courseVersionRepository.save(target);

        // Trước đây method này chỉ đổi CourseVersion mà không đụng course.status — submit xong,
        // course vẫn PUBLISHED/DRAFT như cũ: admin không thấy khóa học trong hàng chờ duyệt, và
        // nút "Gửi cập nhật" ở màn hình chính vẫn hiện ra như chưa gửi gì, cho phép gửi trùng
        // qua 2 đường khác nhau (đây là nguyên nhân "gửi cập nhật xong vẫn gửi tiếp được").
        if (course.getStatus() == CourseStatus.DRAFT || course.getStatus() == CourseStatus.REJECTED) {
            course.setStatus(CourseStatus.PENDING);
            course.setSubmittedAt(Instant.now());
            course.setRejectionReason(null);
        } else {
            course.setStatus(CourseStatus.PENDING_UPDATE);
            course.setPendingUpdateAt(Instant.now());
            course.setSubmittedAt(Instant.now());
            course.setDraftRejectionReason(null);
        }
        courseRepository.save(course);

        return CourseVersionResponse.builder()
                .id(target.getId())
                .versionNumber(target.getVersionNumber())
                .status("PENDING")
                .label(target.getLabel())
                .submittedAt(target.getSubmittedAt())
                .snapshot(target.getSnapshot())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingVersion(UUID instructorId, UUID courseId) {
        loadOwnedCourse(instructorId, courseId);
        return courseVersionRepository.countByCourseIdAndStatus(courseId, "PENDING") > 0;
    }

    /** Tạo CourseVersion record khi instructor submit. Trả về version đã lưu. */
    private CourseVersion createCourseVersion(UUID instructorId, UUID courseId, String changeSummary, Course course) {
        int nextNum = courseVersionRepository.findMaxVersionNumberByCourseId(courseId) + 1;
        String snapshotJson = null;
        try {
            snapshotJson = objectMapper.writeValueAsString(buildSnapshot(course));
        } catch (Exception e) {
            log.warn("Failed to serialize snapshot for version courseId={}: {}", courseId, e.getMessage());
        }
        return courseVersionRepository.save(CourseVersion.builder()
                .courseId(courseId)
                .versionNumber(nextNum)
                .status("PENDING")
                .snapshot(snapshotJson)
                .changeSummary(changeSummary)
                .submittedBy(instructorId)
                .submittedAt(Instant.now())
                .build());
    }

    private boolean isInstructorAction(String action) {
        return action != null && (action.startsWith("SUBMITTED") || action.equals("WITHDRAWN") || action.equals("DISCARDED"));
    }

    private Course loadOwnedCourse(UUID instructorId, UUID courseId) {
        Course course = courseRepository.findByIdWithCategory(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        assertOwner(course, instructorId);
        return course;
    }

    private void assertOwner(Course course, UUID instructorId) {
        if (!course.getInstructorId().equals(instructorId)) {
            throw new CourseNotOwnedException();
        }
    }

    private void assertEditable(Course course) {
        if (course.getStatus() == CourseStatus.PENDING) {
            throw new CourseStateException("Không thể chỉnh sửa khóa học đang chờ duyệt lần đầu");
        }
        if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
            throw new CourseStateException("Không thể chỉnh sửa khóa học đang chờ duyệt cập nhật — hãy đợi admin xử lý hoặc hủy cập nhật trước");
        }
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new CourseStateException("Cannot modify an archived course");
        }
    }

    private CourseCategory resolveCategory(UUID categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
    }

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private String generateUniqueSlug(String title, UUID excludeId) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .trim();
        base = WHITESPACE.matcher(base).replaceAll("-");
        base = NON_LATIN.matcher(base).replaceAll("");

        String slug = base;
        int suffix = 1;

        while (excludeId == null
                ? courseRepository.existsBySlug(slug)
                : courseRepository.existsBySlugAndIdNot(slug, excludeId)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
