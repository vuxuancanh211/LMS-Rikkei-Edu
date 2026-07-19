package project.lms_rikkei_edu.modules.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.exception.*;
import project.lms_rikkei_edu.modules.course.mapper.ChapterMapper;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.mapper.LessonMapper;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.impl.CourseListCacheGateway;
import project.lms_rikkei_edu.modules.course.service.impl.CourseServiceImpl;
import project.lms_rikkei_edu.modules.course.service.impl.CourseVersionReferenceChecker;
import project.lms_rikkei_edu.modules.quiz.repository.QuizRepository;
import project.lms_rikkei_edu.modules.quiz.repository.BankQuestionRepository;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseLessonAndVersionServiceImplTest {

    @Mock CourseRepository courseRepository;
    @Mock ChapterRepository chapterRepository;
    @Mock LessonRepository lessonRepository;
    @Mock LessonResourceRepository lessonResourceRepository;
    @Mock CourseCategoryRepository categoryRepository;
    @Mock CourseApprovalLogRepository approvalLogRepository;
    @Mock CourseVersionRepository courseVersionRepository;
    @Mock CourseMapper courseMapper;
    @Mock ChapterMapper chapterMapper;
    @Mock LessonMapper lessonMapper;
    @Mock ObjectMapper objectMapper;
    @Mock EntityManager entityManager;
    @Mock S3Service s3Service;
    @Mock QuizService quizService;
    @Mock QuizRepository quizRepository;
    @Mock BankQuestionRepository bankQuestionRepository;
    @Mock StudentCourseService studentCourseService;
    @Mock CourseVersionReferenceChecker courseVersionReferenceChecker;
    @Mock CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock project.lms_rikkei_edu.modules.user.repository.UserRepository userRepository;
    @Mock project.lms_rikkei_edu.modules.notification.service.NotificationService notificationService;

    CourseServiceImpl courseService;

    static final UUID INSTRUCTOR_ID = UUID.randomUUID();
    static final UUID COURSE_ID     = UUID.randomUUID();
    static final UUID CHAPTER_ID    = UUID.randomUUID();
    static final UUID LESSON_ID     = UUID.randomUUID();
    static final UUID VERSION_ID    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(
                courseRepository, chapterRepository, lessonRepository,
                lessonResourceRepository, categoryRepository,
                approvalLogRepository, courseVersionRepository,
                courseMapper, objectMapper, chapterMapper, lessonMapper,
                entityManager, s3Service, quizService, quizRepository, bankQuestionRepository, studentCourseService,
                new CourseListCacheGateway(courseRepository, courseMapper),
                courseVersionReferenceChecker, courseEnrollmentRepository, userRepository, notificationService
        );
        when(courseVersionReferenceChecker.isSafeToDelete(any(), any())).thenReturn(true);
    }

    private Course draftCourse() {
        return Course.builder()
                .id(COURSE_ID).instructorId(INSTRUCTOR_ID)
                .title("Test Course").slug("test-course")
                .status(CourseStatus.DRAFT).chapters(new ArrayList<>())
                .build();
    }

    private Course publishedCourse() {
        return Course.builder()
                .id(COURSE_ID).instructorId(INSTRUCTOR_ID)
                .title("Test Course").slug("test-course")
                .status(CourseStatus.PUBLISHED).publishedAt(Instant.now())
                .chapters(new ArrayList<>())
                .build();
    }

    private Chapter chapter() {
        return Chapter.builder()
                .id(CHAPTER_ID)
                .title("Chapter 1").orderIndex(1)
                .lessons(new ArrayList<>())
                .build();
    }

    private Lesson lesson(boolean isDraft) {
        return Lesson.builder()
                .id(LESSON_ID).courseId(COURSE_ID)
                .chapter(chapter()).title("Lesson 1")
                .type(LessonType.TEXT).orderIndex(1)
                .isDraft(isDraft).resources(new ArrayList<>())
                .build();
    }

    private CourseVersion draftVersion() {
        return CourseVersion.builder()
                .id(VERSION_ID).courseId(COURSE_ID)
                .status("DRAFT").label("v1 draft")
                .submittedBy(INSTRUCTOR_ID).submittedAt(Instant.now())
                .build();
    }

    // ── addLesson ─────────────────────────────────────────────────────────────

    @Nested
    class AddLesson {

        @Test
        void addsLesson_whenDraftCourse() {
            Course course = draftCourse();
            Chapter ch    = chapter();

            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(ch));
            when(lessonRepository.findMaxOrderIndexByChapterId(CHAPTER_ID)).thenReturn(0);

            Lesson saved = lesson(false);
            when(lessonRepository.save(any(Lesson.class))).thenReturn(saved);
            when(lessonMapper.toResponse(saved)).thenReturn(
                    LessonResponse.builder().id(LESSON_ID).title("Lesson 1").build());

            CreateLessonRequest req = new CreateLessonRequest();
            req.setTitle("Lesson 1");
            req.setType(LessonType.TEXT);

            LessonResponse resp = courseService.addLesson(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, req);

            assertThat(resp.getId()).isEqualTo(LESSON_ID);
            verify(lessonRepository).save(any(Lesson.class));
        }

        @Test
        void addsLessonAsDraft_whenPublishedCourse() {
            Course course = publishedCourse();
            Chapter ch    = chapter();

            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(ch));
            when(lessonRepository.findMaxOrderIndexByChapterId(CHAPTER_ID)).thenReturn(2);

            Lesson saved = lesson(true); // isDraft=true for published course
            when(lessonRepository.save(any(Lesson.class))).thenReturn(saved);
            when(lessonMapper.toResponse(saved)).thenReturn(
                    LessonResponse.builder().id(LESSON_ID).title("New Lesson").build());

            CreateLessonRequest req = new CreateLessonRequest();
            req.setTitle("New Lesson");
            req.setType(LessonType.TEXT);

            courseService.addLesson(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, req);

            verify(lessonRepository).save(argThat(l -> Boolean.TRUE.equals(l.getIsDraft())));
        }

        @Test
        void throws_whenChapterNotInCourse() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            CreateLessonRequest req = new CreateLessonRequest();
            req.setTitle("X"); req.setType(LessonType.TEXT);

            assertThatThrownBy(() -> courseService.addLesson(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, req))
                    .isInstanceOf(ChapterNotFoundException.class);
        }
    }

    // ── updateLesson ──────────────────────────────────────────────────────────

    @Nested
    class UpdateLesson {

        @Test
        void updatesDirectly_whenDraftCourse() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(chapter()));

            Lesson existing = lesson(false);
            when(lessonRepository.findByIdAndCourseId(LESSON_ID, COURSE_ID))
                    .thenReturn(Optional.of(existing));
            when(lessonRepository.save(any())).thenReturn(existing);
            when(lessonMapper.toResponse(existing)).thenReturn(
                    LessonResponse.builder().id(LESSON_ID).title("Updated").build());

            UpdateLessonRequest req = new UpdateLessonRequest();
            req.setTitle("Updated");

            courseService.updateLesson(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, LESSON_ID, req);

            assertThat(existing.getTitle()).isEqualTo("Updated");
        }

        @Test
        void updatesDraftFields_whenPublishedCourseAndLivLesson() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(publishedCourse()));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(chapter()));

            Lesson existing = lesson(false); // live lesson (isDraft=false) in published course
            when(lessonRepository.findByIdAndCourseId(LESSON_ID, COURSE_ID))
                    .thenReturn(Optional.of(existing));
            when(lessonRepository.save(any())).thenReturn(existing);
            when(lessonMapper.toResponse(existing)).thenReturn(
                    LessonResponse.builder().id(LESSON_ID).build());

            UpdateLessonRequest req = new UpdateLessonRequest();
            req.setTitle("Draft Title");
            req.setContentText("Draft content");

            courseService.updateLesson(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, LESSON_ID, req);

            assertThat(existing.getDraftTitle()).isEqualTo("Draft Title");
            assertThat(existing.getDraftContentText()).isEqualTo("Draft content");
        }

        @Test
        void throws_whenLessonNotFound() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(chapter()));
            when(lessonRepository.findByIdAndCourseId(LESSON_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.updateLesson(
                    INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, LESSON_ID, new UpdateLessonRequest()))
                    .isInstanceOf(LessonNotFoundException.class);
        }
    }

    // ── deleteLesson ──────────────────────────────────────────────────────────

    @Nested
    class DeleteLesson {

        @Test
        void hardDeletes_whenDraftCourse() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(chapter()));

            Lesson l = lesson(false);
            when(lessonRepository.findByIdAndCourseId(LESSON_ID, COURSE_ID)).thenReturn(Optional.of(l));

            courseService.deleteLesson(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, LESSON_ID);

            verify(lessonRepository).delete(l);
        }

        @Test
        void hardDeletes_whenDraftLesson_inPublishedCourse() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(publishedCourse()));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(chapter()));

            Lesson l = lesson(true); // draft lesson
            when(lessonRepository.findByIdAndCourseId(LESSON_ID, COURSE_ID)).thenReturn(Optional.of(l));

            courseService.deleteLesson(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, LESSON_ID);

            verify(lessonRepository).delete(l);
        }

        @Test
        void marksPendingDelete_whenLiveLessonInPublishedCourse() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(publishedCourse()));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(chapter()));

            Lesson l = lesson(false); // live lesson
            when(lessonRepository.findByIdAndCourseId(LESSON_ID, COURSE_ID)).thenReturn(Optional.of(l));
            when(lessonRepository.save(any())).thenReturn(l);

            courseService.deleteLesson(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, LESSON_ID);

            assertThat(l.getPendingDelete()).isTrue();
            verify(lessonRepository, never()).delete(any());
        }
    }

    // ── saveDraft ─────────────────────────────────────────────────────────────

    @Nested
    class SaveDraft {

        @Test
        void savesDraftVersion_whenDraftCourse() throws Exception {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(0L);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            CourseVersion saved = draftVersion();
            when(courseVersionRepository.save(any())).thenReturn(saved);

            CourseVersionResponse resp = courseService.saveDraft(INSTRUCTOR_ID, COURSE_ID, "My Draft");

            assertThat(resp.getId()).isEqualTo(VERSION_ID);
            assertThat(resp.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        void throws_whenDraftLimitReached() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(3L);

            assertThatThrownBy(() -> courseService.saveDraft(INSTRUCTOR_ID, COURSE_ID, "label"))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("giới hạn 3 bản nháp");
        }

        @Test
        void throws_whenPendingCourse() {
            Course pending = Course.builder()
                    .id(COURSE_ID).instructorId(INSTRUCTOR_ID)
                    .status(CourseStatus.PENDING).chapters(new ArrayList<>()).build();
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> courseService.saveDraft(INSTRUCTOR_ID, COURSE_ID, "label"))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── deleteDraftVersion ────────────────────────────────────────────────────

    @Nested
    class DeleteDraftVersion {

        @Test
        void deletesVersion_whenDraftStatus() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            CourseVersion v = draftVersion();
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(v));

            courseService.deleteDraftVersion(INSTRUCTOR_ID, COURSE_ID, VERSION_ID);

            verify(courseVersionRepository).delete(v);
        }

        @Test
        void throws_whenVersionNotFound() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.deleteDraftVersion(INSTRUCTOR_ID, COURSE_ID, VERSION_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throws_whenVersionBelongsToOtherCourse() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            CourseVersion v = draftVersion();
            v.setCourseId(UUID.randomUUID()); // different course
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> courseService.deleteDraftVersion(INSTRUCTOR_ID, COURSE_ID, VERSION_ID))
                    .isInstanceOf(CourseStateException.class);
        }

        @Test
        void throws_whenVersionIsNotDraftOrRejected() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            CourseVersion v = draftVersion();
            v.setStatus("PENDING");
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> courseService.deleteDraftVersion(INSTRUCTOR_ID, COURSE_ID, VERSION_ID))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── renameDraftVersion ────────────────────────────────────────────────────

    @Nested
    class RenameDraftVersion {

        @Test
        void renamesVersion() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            CourseVersion v = draftVersion();
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(v));
            when(courseVersionRepository.save(any())).thenReturn(v);

            courseService.renameDraftVersion(INSTRUCTOR_ID, COURSE_ID, VERSION_ID, "New Label");

            assertThat(v.getLabel()).isEqualTo("New Label");
        }

        @Test
        void throws_whenVersionNotDraft() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            CourseVersion v = draftVersion();
            v.setStatus("PENDING");
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> courseService.renameDraftVersion(INSTRUCTOR_ID, COURSE_ID, VERSION_ID, "X"))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── cloneVersionAsDraft ───────────────────────────────────────────────────

    @Nested
    class CloneVersionAsDraft {

        @Test
        void clonesDraftVersion() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            CourseVersion source = draftVersion();
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(source));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(1L);
            CourseVersion cloned = draftVersion();
            cloned.setId(UUID.randomUUID());
            cloned.setLabel("Clone");
            when(courseVersionRepository.save(any())).thenReturn(cloned);

            CourseVersionResponse resp = courseService.cloneVersionAsDraft(
                    INSTRUCTOR_ID, COURSE_ID, VERSION_ID, "Clone");

            assertThat(resp.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        void throws_whenDraftLimitReached() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(draftVersion()));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(3L);

            assertThatThrownBy(() -> courseService.cloneVersionAsDraft(
                    INSTRUCTOR_ID, COURSE_ID, VERSION_ID, null))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── submitVersion ─────────────────────────────────────────────────────────

    @Nested
    class SubmitVersion {

        @Test
        void submitsDraftVersion() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            CourseVersion v = draftVersion();
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(v));
            when(courseVersionRepository.findByCourseIdAndStatusOrderBySubmittedAtDesc(COURSE_ID, "PENDING"))
                    .thenReturn(List.of());
            when(courseVersionRepository.findMaxVersionNumberByCourseId(COURSE_ID)).thenReturn(0);
            when(courseVersionRepository.save(any())).thenReturn(v);

            CourseVersionResponse resp = courseService.submitVersion(INSTRUCTOR_ID, COURSE_ID, VERSION_ID);

            assertThat(resp.getStatus()).isEqualTo("PENDING");
        }

        @Test
        void throws_whenVersionNotDraft() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            CourseVersion v = draftVersion();
            v.setStatus("PENDING");
            when(courseVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> courseService.submitVersion(INSTRUCTOR_ID, COURSE_ID, VERSION_ID))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── hasPendingVersion ─────────────────────────────────────────────────────

    @Nested
    class HasPendingVersion {

        @Test
        void returnsTrue_whenPendingVersionExists() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "PENDING")).thenReturn(1L);

            assertThat(courseService.hasPendingVersion(INSTRUCTOR_ID, COURSE_ID)).isTrue();
        }

        @Test
        void returnsFalse_whenNoPendingVersion() {
            when(courseRepository.findByIdWithCategory(COURSE_ID))
                    .thenReturn(Optional.of(draftCourse()));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "PENDING")).thenReturn(0L);

            assertThat(courseService.hasPendingVersion(INSTRUCTOR_ID, COURSE_ID)).isFalse();
        }
    }
}
