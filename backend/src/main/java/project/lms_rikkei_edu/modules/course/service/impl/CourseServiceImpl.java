package project.lms_rikkei_edu.modules.course.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.Chapter;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.CourseCategory;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.exception.*;
import project.lms_rikkei_edu.modules.course.mapper.ChapterMapper;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.mapper.LessonMapper;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.CourseService;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final CourseCategoryRepository categoryRepository;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;

    @Override
    public CourseResponse createCourse(UUID instructorId, CreateCourseRequest request) {
        CourseCategory category = resolveCategory(request.getCategoryId());
        String slug = generateUniqueSlug(request.getTitle(), null);

        Course course = Course.builder()
                .instructorId(instructorId)
                .title(request.getTitle())
                .slug(slug)
                .description(request.getDescription())
                .level(request.getLevel())
                .thumbnailUrl(request.getThumbnailUrl())
                .chatEnabled(request.getChatEnabled() != null ? request.getChatEnabled() : false)
                .category(category)
                .status(CourseStatus.DRAFT)
                .build();

        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetail(UUID instructorId, UUID courseId) {
        Course course = courseRepository.findByIdWithCategory(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        assertOwner(course, instructorId);
        course.getChapters().forEach(ch ->
            ch.getLessons().forEach(l -> l.getResources().size())
        );
        return courseMapper.toDetailResponse(course);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseResponse> listCourses(UUID instructorId, Pageable pageable) {
        return courseRepository.findAllByInstructorId(instructorId, pageable)
                .map(courseMapper::toResponse);
    }

    @Override
    public CourseResponse updateCourse(UUID instructorId, UUID courseId, UpdateCourseRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        if (isLive(course)) {
            // Khóa học đang published — ghi vào draft fields thay vì sửa trực tiếp
            if (request.getTitle() != null) course.setDraftTitle(request.getTitle());
            if (request.getDescription() != null) course.setDraftDescription(request.getDescription());
            if (request.getLevel() != null) course.setDraftLevel(request.getLevel());
            if (request.getThumbnailUrl() != null) course.setDraftThumbnailUrl(request.getThumbnailUrl());
            markPendingUpdate(course);
        } else {
            if (request.getTitle() != null) {
                course.setTitle(request.getTitle());
                course.setSlug(generateUniqueSlug(request.getTitle(), courseId));
            }
            if (request.getDescription() != null) course.setDescription(request.getDescription());
            if (request.getLevel() != null) course.setLevel(request.getLevel());
            if (request.getThumbnailUrl() != null) course.setThumbnailUrl(request.getThumbnailUrl());
            if (request.getChatEnabled() != null) course.setChatEnabled(request.getChatEnabled());
            if (request.getCategoryId() != null) course.setCategory(resolveCategory(request.getCategoryId()));
        }

        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Override
    public void deleteCourse(UUID instructorId, UUID courseId) {
        Course course = loadOwnedCourse(instructorId, courseId);
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            throw new CourseStateException("Cannot delete a published course");
        }
        course.setDeletedAt(Instant.now());
        courseRepository.save(course);
    }

    @Override
    public CourseDetailResponse submitForApproval(UUID instructorId, UUID courseId, String changeSummary) {
        Course course = loadOwnedCourse(instructorId, courseId);

        if (course.getStatus() == CourseStatus.DRAFT || course.getStatus() == CourseStatus.REJECTED) {
            // Lần đầu submit
            long lessonCount = lessonRepository.countByCourseId(courseId);
            if (lessonCount == 0) {
                throw new CourseStateException("Course must have at least one lesson before submitting");
            }
            course.setStatus(CourseStatus.PENDING);
            course.setSubmittedAt(Instant.now());
            course.setRejectionReason(null);

        } else if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
            // Submit bản cập nhật để admin duyệt
            course.setChangeSummary(changeSummary);
            course.setSubmittedAt(Instant.now());
            course.setDraftRejectionReason(null);
            // Status giữ nguyên PENDING_UPDATE, admin sẽ thấy trong queue

        } else {
            throw new CourseStateException("Course cannot be submitted in current status: " + course.getStatus());
        }

        return courseMapper.toDetailResponse(courseRepository.save(course));
    }

    @Override
    public CourseDetailResponse withdrawFromReview(UUID instructorId, UUID courseId) {
        Course course = loadOwnedCourse(instructorId, courseId);

        if (course.getStatus() == CourseStatus.PENDING) {
            // Lần đầu pending → về DRAFT
            course.setStatus(CourseStatus.DRAFT);
            course.setSubmittedAt(null);

        } else if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
            // Rút lại update → về PUBLISHED, xóa tất cả draft content
            initChapters(course);
            clearAllDrafts(course);
            course.setStatus(CourseStatus.PUBLISHED);
            course.setPendingUpdateAt(null);
            course.setSubmittedAt(null);

        } else {
            throw new CourseStateException("Only PENDING or PENDING_UPDATE courses can be withdrawn");
        }

        return courseMapper.toDetailResponse(courseRepository.save(course));
    }

    @Override
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

        if (draft) {
            markPendingUpdate(course);
            courseRepository.save(course);
        }

        return chapterMapper.toResponse(chapterRepository.save(chapter));
    }

    @Override
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
                // Chương live → đánh dấu chờ admin duyệt xóa
                chapter.setPendingDelete(true);
                chapterRepository.save(chapter);
                markPendingUpdate(course);
                courseRepository.save(course);
            }
        } else {
            chapterRepository.delete(chapter);
        }
    }

    @Override
    public LessonResponse addLesson(UUID instructorId, UUID courseId, UUID chapterId, CreateLessonRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        Chapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        int nextOrder = lessonRepository.findMaxOrderIndexByChapterId(chapterId) + 1;
        boolean draft = isLive(course);

        Lesson lesson = Lesson.builder()
                .chapter(chapter)
                .courseId(courseId)
                .title(request.getTitle())
                .type(request.getType())
                .contentText(request.getContentText())
                .isPreview(request.getIsPreview() != null ? request.getIsPreview() : false)
                .orderIndex(nextOrder)
                .isDraft(draft)
                .build();

        if (draft) {
            markPendingUpdate(course);
            courseRepository.save(course);
        }

        return lessonMapper.toResponse(lessonRepository.save(lesson));
    }

    @Override
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
            markPendingUpdate(course);
            courseRepository.save(course);
        } else {
            // DRAFT/REJECTED course, hoặc lesson chính nó đang là draft → sửa trực tiếp
            if (request.getTitle() != null) lesson.setTitle(request.getTitle());
            if (request.getType() != null) lesson.setType(request.getType());
            if (request.getContentText() != null) lesson.setContentText(request.getContentText());
            if (request.getIsPreview() != null) lesson.setIsPreview(request.getIsPreview());
        }

        return lessonMapper.toResponse(lessonRepository.save(lesson));
    }

    @Override
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
                // Lesson live → đánh dấu chờ admin duyệt xóa
                lesson.setPendingDelete(true);
                lessonRepository.save(lesson);
                markPendingUpdate(course);
                courseRepository.save(course);
            }
        } else {
            lessonRepository.delete(lesson);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True nếu khóa học đang live (PUBLISHED hoặc PENDING_UPDATE) */
    private boolean isLive(Course course) {
        return course.getStatus() == CourseStatus.PUBLISHED
                || course.getStatus() == CourseStatus.PENDING_UPDATE;
    }

    /** Chuyển PUBLISHED → PENDING_UPDATE nếu cần */
    private void markPendingUpdate(Course course) {
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            course.setStatus(CourseStatus.PENDING_UPDATE);
            course.setPendingUpdateAt(Instant.now());
        }
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
                // Xóa draft lessons qua orphanRemoval
                ch.getLessons().removeAll(draftLessons);
            }
        }
        // Xóa draft chapters qua orphanRemoval
        course.getChapters().removeAll(draftChapters);
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
            throw new CourseStateException("Cannot modify a course pending first approval");
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
