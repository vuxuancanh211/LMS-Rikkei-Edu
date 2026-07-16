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
import project.lms_rikkei_edu.modules.course.dto.request.UpdateChapterRequest;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.exception.*;
import project.lms_rikkei_edu.modules.course.mapper.*;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.impl.CourseListCacheGateway;
import project.lms_rikkei_edu.modules.course.service.impl.CourseServiceImpl;
import project.lms_rikkei_edu.modules.course.service.impl.CourseVersionReferenceChecker;
import project.lms_rikkei_edu.modules.quiz.repository.QuizRepository;
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
class CourseServiceImplExtTest {

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
    @Mock StudentCourseService studentCourseService;
    @Mock CourseVersionReferenceChecker courseVersionReferenceChecker;

    CourseServiceImpl courseService;

    static final UUID INSTRUCTOR_ID = UUID.randomUUID();
    static final UUID COURSE_ID     = UUID.randomUUID();
    static final UUID CHAPTER_ID    = UUID.randomUUID();
    static final UUID VERSION_ID    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(
                courseRepository, chapterRepository, lessonRepository,
                lessonResourceRepository, categoryRepository,
                approvalLogRepository, courseVersionRepository,
                courseMapper, objectMapper, chapterMapper, lessonMapper,
                entityManager, s3Service, quizService, quizRepository, studentCourseService,
                new CourseListCacheGateway(courseRepository, courseMapper),
                courseVersionReferenceChecker
        );
        when(courseVersionReferenceChecker.isSafeToDelete(any(), any())).thenReturn(true);
    }

    private Course draftCourse() {
        return Course.builder()
                .id(COURSE_ID).instructorId(INSTRUCTOR_ID).title("T")
                .status(CourseStatus.DRAFT).chapters(new ArrayList<>()).build();
    }

    private Course publishedCourse() {
        return Course.builder()
                .id(COURSE_ID).instructorId(INSTRUCTOR_ID).title("T")
                .status(CourseStatus.PUBLISHED).chapters(new ArrayList<>()).build();
    }

    // Chapter entity has no courseId field — use id + title only
    private Chapter chapter(UUID id, boolean isDraft) {
        return Chapter.builder()
                .id(id).title("Ch").orderIndex(1)
                .isDraft(isDraft).lessons(new ArrayList<>()).build();
    }

    private CourseApprovalLog buildLog(String action) {
        return CourseApprovalLog.builder()
                .id(UUID.randomUUID()).courseId(COURSE_ID).action(action)
                .createdAt(Instant.now()).build();
    }

    private CourseVersion buildVersion(String status) {
        return CourseVersion.builder()
                .id(VERSION_ID).courseId(COURSE_ID).status(status)
                .versionNumber(1).submittedAt(Instant.now()).build();
    }

    // ── updateChapter ─────────────────────────────────────────────────────────

    @Nested
    class UpdateChapter {

        @Test
        void updatesTitle_onDraftCourse() {
            Course course = draftCourse();
            Chapter ch = chapter(CHAPTER_ID, false);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(ch));
            when(chapterRepository.save(ch)).thenReturn(ch);
            ChapterResponse resp = ChapterResponse.builder().id(CHAPTER_ID).title("Updated").build();
            when(chapterMapper.toResponse(ch)).thenReturn(resp);

            UpdateChapterRequest req = new UpdateChapterRequest();
            req.setTitle("Updated");

            ChapterResponse result = courseService.updateChapter(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, req);

            assertThat(result.getTitle()).isEqualTo("Updated");
            assertThat(ch.getTitle()).isEqualTo("Updated");
        }

        @Test
        void updatesTitle_onPublishedCourse_directlyNoQueue() {
            Course course = publishedCourse();
            Chapter ch = chapter(CHAPTER_ID, false);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(ch));
            when(chapterRepository.save(ch)).thenReturn(ch);
            when(chapterMapper.toResponse(ch)).thenReturn(
                    ChapterResponse.builder().id(CHAPTER_ID).title("New").build());

            UpdateChapterRequest req = new UpdateChapterRequest();
            req.setTitle("New");

            courseService.updateChapter(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, req);

            assertThat(ch.getTitle()).isEqualTo("New");
        }

        @Test
        void throws_whenChapterNotFound() {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.updateChapter(
                    INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, new UpdateChapterRequest()))
                    .isInstanceOf(ChapterNotFoundException.class);
        }

        @Test
        void throws_whenCourseNotFound() {
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.updateChapter(
                    INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID, new UpdateChapterRequest()))
                    .isInstanceOf(CourseNotFoundException.class);
        }
    }

    // ── deleteChapter ─────────────────────────────────────────────────────────

    @Nested
    class DeleteChapter {

        @Test
        void hardDeletes_onDraftCourse() {
            Course course = draftCourse();
            Chapter ch = chapter(CHAPTER_ID, false);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(ch));

            courseService.deleteChapter(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID);

            verify(chapterRepository).delete(ch);
        }

        @Test
        void pendingDeletes_liveChapterInPublishedCourse() {
            Course course = publishedCourse();
            Chapter ch = chapter(CHAPTER_ID, false); // not draft → live
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(ch));

            courseService.deleteChapter(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID);

            assertThat(ch.getPendingDelete()).isTrue();
            verify(chapterRepository).save(ch);
            verify(chapterRepository, never()).delete(any());
        }

        @Test
        void hardDeletes_draftChapterInPublishedCourse() {
            Course course = publishedCourse();
            Chapter ch = chapter(CHAPTER_ID, true); // isDraft=true → not yet live
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.of(ch));

            courseService.deleteChapter(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID);

            verify(chapterRepository).delete(ch);
        }

        @Test
        void throws_whenChapterNotFound() {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(CHAPTER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.deleteChapter(INSTRUCTOR_ID, COURSE_ID, CHAPTER_ID))
                    .isInstanceOf(ChapterNotFoundException.class);
        }
    }

    // ── getCourseHistory ──────────────────────────────────────────────────────

    @Nested
    class GetCourseHistory {

        @Test
        void returnsHistory_withActorTypes() {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));

            CourseApprovalLog submitLog  = buildLog("SUBMITTED");
            CourseApprovalLog approveLog = buildLog("APPROVED_FIRST");
            when(approvalLogRepository.findByCourseIdOrderByCreatedAtAsc(COURSE_ID))
                    .thenReturn(List.of(submitLog, approveLog));

            List<CourseApprovalLogResponse> result = courseService.getCourseHistory(INSTRUCTOR_ID, COURSE_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getActorType()).isEqualTo("INSTRUCTOR");
            assertThat(result.get(1).getActorType()).isEqualTo("ADMIN");
        }

        @Test
        void returnsEmptyList_whenNoHistory() {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(approvalLogRepository.findByCourseIdOrderByCreatedAtAsc(COURSE_ID))
                    .thenReturn(List.of());

            List<CourseApprovalLogResponse> result = courseService.getCourseHistory(INSTRUCTOR_ID, COURSE_ID);

            assertThat(result).isEmpty();
        }

        @Test
        void throwsCourseNotFoundException_whenNotFound() {
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.getCourseHistory(INSTRUCTOR_ID, COURSE_ID))
                    .isInstanceOf(CourseNotFoundException.class);
        }

        @Test
        void categorizesBothInstructorAndAdminActions() {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));

            List<CourseApprovalLog> logs = List.of(
                    buildLog("SUBMITTED"),
                    buildLog("WITHDRAWN"),
                    buildLog("APPROVED_FIRST"),
                    buildLog("REJECTED"),
                    buildLog("APPROVED_UPDATE"),
                    buildLog("REJECTED_UPDATE")
            );
            when(approvalLogRepository.findByCourseIdOrderByCreatedAtAsc(COURSE_ID)).thenReturn(logs);

            List<CourseApprovalLogResponse> result = courseService.getCourseHistory(INSTRUCTOR_ID, COURSE_ID);

            assertThat(result.get(0).getActorType()).isEqualTo("INSTRUCTOR"); // SUBMITTED
            assertThat(result.get(1).getActorType()).isEqualTo("INSTRUCTOR"); // WITHDRAWN
            assertThat(result.get(2).getActorType()).isEqualTo("ADMIN");      // APPROVED_FIRST
            assertThat(result.get(3).getActorType()).isEqualTo("ADMIN");      // REJECTED
        }
    }

    // ── getCourseVersions ─────────────────────────────────────────────────────

    @Nested
    class GetCourseVersions {

        @Test
        void returnsVersionList() {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));

            CourseVersion v1 = buildVersion("APPROVED");
            CourseVersion v2 = buildVersion("DRAFT");
            when(courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(COURSE_ID))
                    .thenReturn(List.of(v2, v1));

            List<CourseVersionResponse> result = courseService.getCourseVersions(INSTRUCTOR_ID, COURSE_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getStatus()).isEqualTo("DRAFT");
            assertThat(result.get(1).getStatus()).isEqualTo("APPROVED");
        }

        @Test
        void returnsEmpty_whenNoVersions() {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(COURSE_ID))
                    .thenReturn(List.of());

            List<CourseVersionResponse> result = courseService.getCourseVersions(INSTRUCTOR_ID, COURSE_ID);

            assertThat(result).isEmpty();
        }

        @Test
        void throwsCourseNotFoundException_whenNotFound() {
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.getCourseVersions(INSTRUCTOR_ID, COURSE_ID))
                    .isInstanceOf(CourseNotFoundException.class);
        }
    }
}
