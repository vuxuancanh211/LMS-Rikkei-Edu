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
        // Load course + chapters + lessons (avoids MultipleBagFetchException)
        Course course = courseRepository.findByIdWithFullStructure(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        assertOwner(course, instructorId);
        // Force-init resources for each lesson while still in transaction
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

        if (request.getTitle() != null) {
            course.setTitle(request.getTitle());
            course.setSlug(generateUniqueSlug(request.getTitle(), courseId));
        }
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getLevel() != null) course.setLevel(request.getLevel());
        if (request.getThumbnailUrl() != null) course.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getChatEnabled() != null) course.setChatEnabled(request.getChatEnabled());
        if (request.getCategoryId() != null) course.setCategory(resolveCategory(request.getCategoryId()));

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
    public CourseDetailResponse submitForApproval(UUID instructorId, UUID courseId) {
        Course course = loadOwnedCourse(instructorId, courseId);

        if (course.getStatus() != CourseStatus.DRAFT && course.getStatus() != CourseStatus.REJECTED) {
            throw new CourseStateException("Only DRAFT or REJECTED courses can be submitted");
        }

        long lessonCount = lessonRepository.countByCourseId(courseId);
        if (lessonCount == 0) {
            throw new CourseStateException("Course must have at least one lesson before submitting");
        }

        course.setStatus(CourseStatus.PENDING);
        course.setSubmittedAt(Instant.now());
        course.setRejectionReason(null);

        Course saved = courseRepository.save(course);
        return courseMapper.toDetailResponse(saved);
    }

    @Override
    public ChapterResponse addChapter(UUID instructorId, UUID courseId, CreateChapterRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        int nextOrder = chapterRepository.findMaxOrderIndexByCourseId(courseId) + 1;

        Chapter chapter = Chapter.builder()
                .course(course)
                .title(request.getTitle())
                .description(request.getDescription())
                .orderIndex(nextOrder)
                .build();

        return chapterMapper.toResponse(chapterRepository.save(chapter));
    }

    @Override
    public ChapterResponse updateChapter(UUID instructorId, UUID courseId, UUID chapterId, UpdateChapterRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        Chapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

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

        chapterRepository.delete(chapter);
    }

    @Override
    public LessonResponse addLesson(UUID instructorId, UUID courseId, UUID chapterId, CreateLessonRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        Chapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        int nextOrder = lessonRepository.findMaxOrderIndexByChapterId(chapterId) + 1;

        Lesson lesson = Lesson.builder()
                .chapter(chapter)
                .courseId(courseId)
                .title(request.getTitle())
                .type(request.getType())
                .contentText(request.getContentText())
                .isPreview(request.getIsPreview() != null ? request.getIsPreview() : false)
                .orderIndex(nextOrder)
                .build();

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

        boolean contentChanged = request.getContentText() != null &&
                !request.getContentText().equals(lesson.getContentText());

        if (request.getTitle() != null) lesson.setTitle(request.getTitle());
        if (request.getType() != null) lesson.setType(request.getType());
        if (request.getContentText() != null) lesson.setContentText(request.getContentText());
        if (request.getIsPreview() != null) lesson.setIsPreview(request.getIsPreview());

        LessonResponse saved = lessonMapper.toResponse(lessonRepository.save(lesson));

        if (contentChanged && course.getStatus() == CourseStatus.PUBLISHED) {
            course.setStatus(CourseStatus.PENDING_UPDATE);
            course.setPendingUpdateAt(java.time.Instant.now());
            courseRepository.save(course);
        }

        return saved;
    }

    @Override
    public void deleteLesson(UUID instructorId, UUID courseId, UUID chapterId, UUID lessonId) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        Lesson lesson = lessonRepository.findByIdAndCourseId(lessonId, courseId)
                .orElseThrow(() -> new LessonNotFoundException(lessonId));

        lessonRepository.delete(lesson);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
            throw new CourseStateException("Cannot modify a course that is pending approval");
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
