package project.lms_rikkei_edu.modules.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
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
class CourseServiceImplTest {

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

    // ── helpers ───────────────────────────────────────────────────────────────

    private Course draftCourse() {
        return Course.builder()
                .id(COURSE_ID)
                .instructorId(INSTRUCTOR_ID)
                .title("Test Course")
                .slug("test-course")
                .status(CourseStatus.DRAFT)
                .chapters(new ArrayList<>())
                .build();
    }

    private Course publishedCourse() {
        return Course.builder()
                .id(COURSE_ID)
                .instructorId(INSTRUCTOR_ID)
                .title("Test Course")
                .slug("test-course")
                .status(CourseStatus.PUBLISHED)
                .publishedAt(Instant.now())
                .chapters(new ArrayList<>())
                .build();
    }

    private CourseResponse stubCourseResponse(UUID id, CourseStatus status) {
        return CourseResponse.builder()
                .id(id)
                .title("Test Course")
                .status(status)
                .build();
    }

    private CourseDetailResponse stubDetailResponse() {
        return CourseDetailResponse.builder()
                .id(COURSE_ID)
                .title("Test Course")
                .status(CourseStatus.DRAFT)
                .build();
    }

    // ── createCourse ──────────────────────────────────────────────────────────

    @Nested
    class CreateCourse {

        @Test
        void savesCourseWithDraftStatusAndInstructorId() {
            CreateCourseRequest req = new CreateCourseRequest();
            req.setTitle("Spring Boot Deep Dive");

            Course saved = draftCourse();
            CourseResponse expected = stubCourseResponse(COURSE_ID, CourseStatus.DRAFT);

            when(courseRepository.existsBySlug(anyString())).thenReturn(false);
            when(courseRepository.save(any(Course.class))).thenReturn(saved);
            when(courseMapper.toResponse(saved)).thenReturn(expected);

            CourseResponse result = courseService.createCourse(INSTRUCTOR_ID, req);

            assertThat(result.getStatus()).isEqualTo(CourseStatus.DRAFT);

            ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
            verify(courseRepository).save(captor.capture());
            assertThat(captor.getValue().getInstructorId()).isEqualTo(INSTRUCTOR_ID);
            assertThat(captor.getValue().getStatus()).isEqualTo(CourseStatus.DRAFT);
        }

        @Test
        void resolvesCategory_whenCategoryIdProvided() {
            UUID catId = UUID.randomUUID();
            CreateCourseRequest req = new CreateCourseRequest();
            req.setTitle("Course With Category");
            req.setCategoryId(catId);

            CourseCategory cat = new CourseCategory();
            cat.setId(catId);
            Course saved = draftCourse();

            when(categoryRepository.findById(catId)).thenReturn(Optional.of(cat));
            when(courseRepository.existsBySlug(anyString())).thenReturn(false);
            when(courseRepository.save(any())).thenReturn(saved);
            when(courseMapper.toResponse(saved)).thenReturn(stubCourseResponse(COURSE_ID, CourseStatus.DRAFT));

            courseService.createCourse(INSTRUCTOR_ID, req);

            ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
            verify(courseRepository).save(captor.capture());
            assertThat(captor.getValue().getCategory()).isEqualTo(cat);
        }

        @Test
        void generatesUniqueSlug_appendingSuffix_whenSlugTaken() {
            CreateCourseRequest req = new CreateCourseRequest();
            req.setTitle("My Course");

            Course saved = draftCourse();
            when(courseRepository.existsBySlug("my-course")).thenReturn(true);
            when(courseRepository.existsBySlug(contains("my-course-"))).thenReturn(false);
            when(courseRepository.save(any())).thenReturn(saved);
            when(courseMapper.toResponse(saved)).thenReturn(stubCourseResponse(COURSE_ID, CourseStatus.DRAFT));

            courseService.createCourse(INSTRUCTOR_ID, req);

            ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
            verify(courseRepository).save(captor.capture());
            assertThat(captor.getValue().getSlug()).isNotEqualTo("my-course");
        }
    }

    // ── getCourseDetail ───────────────────────────────────────────────────────

    @Nested
    class GetCourseDetail {

        @Test
        void throwsCourseNotFoundException_whenCourseDoesNotExist() {
            when(courseRepository.findByIdWithFullContent(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.getCourseDetail(INSTRUCTOR_ID, COURSE_ID))
                    .isInstanceOf(CourseNotFoundException.class);
        }

        @Test
        void throwsCourseNotOwnedException_whenInstructorIsNotOwner() {
            Course course = draftCourse();
            course.setInstructorId(UUID.randomUUID());

            when(courseRepository.findByIdWithFullContent(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> courseService.getCourseDetail(INSTRUCTOR_ID, COURSE_ID))
                    .isInstanceOf(CourseNotOwnedException.class);
        }

        @Test
        void returnsCourseDetail_whenOwner() {
            Course course = draftCourse();
            CourseDetailResponse detail = stubDetailResponse();

            when(courseRepository.findByIdWithFullContent(COURSE_ID)).thenReturn(Optional.of(course));
            when(courseMapper.toDetailResponse(course)).thenReturn(detail);

            CourseDetailResponse result = courseService.getCourseDetail(INSTRUCTOR_ID, COURSE_ID);

            assertThat(result.getId()).isEqualTo(COURSE_ID);
        }
    }

    // ── getCourseDetailBySlug ─────────────────────────────────────────────────
    // Regression: getCourseDetailBySlug() phải resolve slug -> courseId rồi gọi getCourseDetail()
    // QUA self-proxy để dùng chung 1 cache entry "course-detail" theo courseId (xem field `self`
    // trong CourseServiceImpl) — trước đây cache riêng theo slug nên @CacheEvict(key=courseId) ở
    // các thao tác sửa không xóa được, khiến trang chi tiết hiện dữ liệu cũ sau khi F5.

    @Nested
    class GetCourseDetailBySlug {

        @BeforeEach
        void wireSelfProxy() {
            // Spring thật inject field `self` bằng @Autowired @Lazy lúc runtime; test dựng bean
            // thủ công (new CourseServiceImpl(...)) nên phải gán tay để mô phỏng đúng self-proxy.
            org.springframework.test.util.ReflectionTestUtils.setField(courseService, "self", courseService);
        }

        @Test
        void throwsCourseNotFoundException_whenSlugDoesNotExist() {
            when(courseRepository.findBySlug("missing-slug")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.getCourseDetailBySlug(INSTRUCTOR_ID, "missing-slug"))
                    .isInstanceOf(CourseNotFoundException.class);
        }

        @Test
        void throwsCourseNotOwnedException_whenInstructorIsNotOwner() {
            Course course = draftCourse();
            course.setInstructorId(UUID.randomUUID());
            when(courseRepository.findBySlug("test-course")).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> courseService.getCourseDetailBySlug(INSTRUCTOR_ID, "test-course"))
                    .isInstanceOf(CourseNotOwnedException.class);
        }

        @Test
        void delegatesToGetCourseDetail_andReturnsSameResult() {
            Course course = draftCourse();
            CourseDetailResponse detail = stubDetailResponse();
            when(courseRepository.findBySlug("test-course")).thenReturn(Optional.of(course));
            when(courseRepository.findByIdWithFullContent(COURSE_ID)).thenReturn(Optional.of(course));
            when(courseMapper.toDetailResponse(course)).thenReturn(detail);

            CourseDetailResponse result = courseService.getCourseDetailBySlug(INSTRUCTOR_ID, "test-course");

            assertThat(result.getId()).isEqualTo(COURSE_ID);
            verify(courseRepository).findByIdWithFullContent(COURSE_ID);
        }
    }

    // ── listCourses ───────────────────────────────────────────────────────────

    @Nested
    class ListCourses {

        @Test
        void returnsPagedCoursesForInstructor() {
            Course course = draftCourse();
            PageRequest pageable = PageRequest.of(0, 20);
            Page<Course> page = new PageImpl<>(List.of(course), pageable, 1);

            when(courseRepository.findAllByInstructorId(INSTRUCTOR_ID, pageable)).thenReturn(page);
            when(courseMapper.toResponse(course))
                    .thenReturn(stubCourseResponse(COURSE_ID, CourseStatus.DRAFT));

            Page<CourseResponse> result = courseService.listCourses(INSTRUCTOR_ID, pageable, null);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(COURSE_ID);
        }

        @Test
        void returnsEmptyPage_whenInstructorHasNoCourses() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(courseRepository.findAllByInstructorId(INSTRUCTOR_ID, pageable))
                    .thenReturn(Page.empty());

            Page<CourseResponse> result = courseService.listCourses(INSTRUCTOR_ID, pageable, null);

            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ── updateCourse ──────────────────────────────────────────────────────────

    @Nested
    class UpdateCourse {

        @Test
        void updatesAllProvidedFields() {
            Course course = draftCourse();
            UpdateCourseRequest req = new UpdateCourseRequest();
            req.setTitle("New Title");
            req.setDescription("New description");
            req.setLevel(CourseLevel.ADVANCED);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(courseRepository.existsBySlugAndIdNot(anyString(), eq(COURSE_ID))).thenReturn(false);
            when(courseRepository.save(any())).thenReturn(course);
            when(courseMapper.toResponse(course))
                    .thenReturn(stubCourseResponse(COURSE_ID, CourseStatus.DRAFT));

            courseService.updateCourse(INSTRUCTOR_ID, COURSE_ID, req);

            assertThat(course.getTitle()).isEqualTo("New Title");
            assertThat(course.getDescription()).isEqualTo("New description");
            assertThat(course.getLevel()).isEqualTo(CourseLevel.ADVANCED);
        }

        @Test
        void skipsNullFields_leavingExistingValuesUnchanged() {
            Course course = draftCourse();
            course.setDescription("Original description");

            UpdateCourseRequest req = new UpdateCourseRequest();
            req.setTitle("Only Title Changed");

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(courseRepository.existsBySlugAndIdNot(anyString(), eq(COURSE_ID))).thenReturn(false);
            when(courseRepository.save(any())).thenReturn(course);
            when(courseMapper.toResponse(course))
                    .thenReturn(stubCourseResponse(COURSE_ID, CourseStatus.DRAFT));

            courseService.updateCourse(INSTRUCTOR_ID, COURSE_ID, req);

            assertThat(course.getDescription()).isEqualTo("Original description");
        }

        @Test
        void throwsCourseStateException_whenArchivedCourse() {
            Course course = draftCourse();
            course.setStatus(CourseStatus.ARCHIVED);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> courseService.updateCourse(INSTRUCTOR_ID, COURSE_ID, new UpdateCourseRequest()))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("archived");
        }

        @Test
        void throwsCourseNotOwnedException_whenDifferentInstructor() {
            Course course = draftCourse();
            course.setInstructorId(UUID.randomUUID());

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> courseService.updateCourse(INSTRUCTOR_ID, COURSE_ID, new UpdateCourseRequest()))
                    .isInstanceOf(CourseNotOwnedException.class);
        }
    }

    // ── deleteCourse ──────────────────────────────────────────────────────────

    @Nested
    class DeleteCourse {

        @Test
        void softDeletesDraftCourseBySettingDeletedAt() {
            Course course = draftCourse();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(courseRepository.save(any())).thenReturn(course);

            courseService.deleteCourse(INSTRUCTOR_ID, COURSE_ID);

            assertThat(course.getDeletedAt()).isNotNull();
            verify(courseRepository).save(course);
        }

        @Test
        void throwsCourseStateException_whenPublishedCourse() {
            Course course = publishedCourse();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> courseService.deleteCourse(INSTRUCTOR_ID, COURSE_ID))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("published");
        }

        @Test
        void throwsCourseNotFoundException_whenCourseDoesNotExist() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.deleteCourse(INSTRUCTOR_ID, COURSE_ID))
                    .isInstanceOf(CourseNotFoundException.class);
        }
    }

    // ── submitForApproval ─────────────────────────────────────────────────────

    @Nested
    class SubmitForApproval {

        @Test
        void changesDraftToPending_whenHasLessons() throws Exception {
            Course course = draftCourse();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(lessonRepository.countByCourseId(COURSE_ID)).thenReturn(2L);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(courseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(approvalLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(courseRepository.save(any())).thenReturn(course);
            when(courseMapper.toDetailResponse(course)).thenReturn(stubDetailResponse());

            courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "Initial submission");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PENDING);
            assertThat(course.getSubmittedAt()).isNotNull();
        }

        @Test
        void throwsCourseStateException_whenNoLessons() {
            Course course = draftCourse();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(lessonRepository.countByCourseId(COURSE_ID)).thenReturn(0L);

            assertThatThrownBy(() -> courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, null))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("least one lesson");
        }

        @Test
        void throwsCourseStateException_whenAlreadyPending() {
            Course course = draftCourse();
            course.setStatus(CourseStatus.PENDING);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, null))
                    .isInstanceOf(CourseStateException.class);
        }

        @Test
        void publishedCourse_throwsWhenNoDraftChanges() {
            Course course = publishedCourse();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(lessonResourceRepository.existsByCourseIdAndIsNewInUpdateTrue(COURSE_ID)).thenReturn(false);
            when(lessonResourceRepository.existsByCourseIdAndPendingDeleteTrue(COURSE_ID)).thenReturn(false);

            assertThatThrownBy(() -> courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, null))
                    .isInstanceOf(CourseStateException.class);
        }

        // ── validateQuizzesActive ────────────────────────────────────────────

        private Course draftCourseWithQuizLesson(UUID quizId, boolean pendingDeleteChapter, boolean pendingDeleteLesson) {
            Course course = draftCourse();
            project.lms_rikkei_edu.modules.course.entity.Lesson lesson =
                    project.lms_rikkei_edu.modules.course.entity.Lesson.builder()
                            .id(UUID.randomUUID())
                            .type(project.lms_rikkei_edu.modules.course.enums.LessonType.QUIZ)
                            .quizId(quizId)
                            .pendingDelete(pendingDeleteLesson)
                            .build();
            project.lms_rikkei_edu.modules.course.entity.Chapter chapter =
                    project.lms_rikkei_edu.modules.course.entity.Chapter.builder()
                            .id(UUID.randomUUID())
                            .pendingDelete(pendingDeleteChapter)
                            .lessons(new ArrayList<>(List.of(lesson)))
                            .build();
            course.getChapters().add(chapter);
            return course;
        }

        @Test
        void noQuizLessons_passesThrough() throws Exception {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(lessonRepository.countByCourseId(COURSE_ID)).thenReturn(1L);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(courseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(approvalLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(courseRepository.save(any())).thenReturn(course);
            when(courseMapper.toDetailResponse(course)).thenReturn(stubDetailResponse());

            courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "no quiz lessons");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PENDING);
            verifyNoInteractions(quizRepository);
        }

        @Test
        void quizLessonPublished_passesThrough() throws Exception {
            UUID quizId = UUID.randomUUID();
            Course course = draftCourseWithQuizLesson(quizId, false, false);
            project.lms_rikkei_edu.modules.quiz.entity.QuizEntity quiz =
                    new project.lms_rikkei_edu.modules.quiz.entity.QuizEntity();
            quiz.setId(quizId);
            quiz.setTitle("Quiz 1");
            quiz.setStatus(project.lms_rikkei_edu.modules.quiz.enums.QuizStatus.PUBLISHED);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(quizRepository.findAllById(List.of(quizId))).thenReturn(List.of(quiz));
            when(lessonRepository.countByCourseId(COURSE_ID)).thenReturn(1L);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(courseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(approvalLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(courseRepository.save(any())).thenReturn(course);
            when(courseMapper.toDetailResponse(course)).thenReturn(stubDetailResponse());

            courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "quiz published");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PENDING);
        }

        @Test
        void quizLessonDraft_throwsWithQuizTitle() {
            UUID quizId = UUID.randomUUID();
            Course course = draftCourseWithQuizLesson(quizId, false, false);
            project.lms_rikkei_edu.modules.quiz.entity.QuizEntity quiz =
                    new project.lms_rikkei_edu.modules.quiz.entity.QuizEntity();
            quiz.setId(quizId);
            quiz.setTitle("Unpublished Quiz");
            quiz.setStatus(project.lms_rikkei_edu.modules.quiz.enums.QuizStatus.DRAFT);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(quizRepository.findAllById(List.of(quizId))).thenReturn(List.of(quiz));

            assertThatThrownBy(() -> courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "x"))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("Unpublished Quiz");
        }

        @Test
        void quizLessonPointsToMissingQuiz_throwsWithPlaceholder() {
            UUID quizId = UUID.randomUUID();
            Course course = draftCourseWithQuizLesson(quizId, false, false);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(quizRepository.findAllById(List.of(quizId))).thenReturn(List.of());

            assertThatThrownBy(() -> courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "x"))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("không tìm thấy đề");
        }

        @Test
        void quizLessonInPendingDeleteChapter_isExcludedFromCheck() throws Exception {
            UUID quizId = UUID.randomUUID();
            Course course = draftCourseWithQuizLesson(quizId, true, false);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(lessonRepository.countByCourseId(COURSE_ID)).thenReturn(1L);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(courseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(approvalLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(courseRepository.save(any())).thenReturn(course);
            when(courseMapper.toDetailResponse(course)).thenReturn(stubDetailResponse());

            courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "pending-delete chapter excluded");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PENDING);
            verifyNoInteractions(quizRepository);
        }

        @Test
        void quizLessonInPendingDeleteLesson_isExcludedFromCheck() throws Exception {
            // Same guard as the pending-delete CHAPTER case above, but for a lesson individually
            // marked pending_delete inside an otherwise-live chapter.
            UUID quizId = UUID.randomUUID();
            Course course = draftCourseWithQuizLesson(quizId, false, true);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(lessonRepository.countByCourseId(COURSE_ID)).thenReturn(1L);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(courseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(approvalLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(courseRepository.save(any())).thenReturn(course);
            when(courseMapper.toDetailResponse(course)).thenReturn(stubDetailResponse());

            courseService.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "pending-delete lesson excluded");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PENDING);
            verifyNoInteractions(quizRepository);
        }
    }

    // ── withdrawFromReview ────────────────────────────────────────────────────

    @Nested
    class WithdrawFromReview {

        @Test
        void pendingCourse_revertsToDraftAndRevokesVersion() {
            Course course = draftCourse();
            course.setStatus(CourseStatus.PENDING);
            course.setSubmittedAt(Instant.now());

            CourseVersion version = new CourseVersion();
            version.setStatus("PENDING");

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(courseVersionRepository.findFirstByCourseIdAndStatus(COURSE_ID, "PENDING"))
                    .thenReturn(Optional.of(version));
            when(courseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(courseRepository.save(any())).thenReturn(course);
            when(courseMapper.toDetailResponse(course)).thenReturn(stubDetailResponse());

            courseService.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID);

            assertThat(course.getStatus()).isEqualTo(CourseStatus.DRAFT);
            assertThat(course.getSubmittedAt()).isNull();
            assertThat(version.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        void pendingUpdateCourse_revertsToPublished() {
            Course course = publishedCourse();
            course.setStatus(CourseStatus.PENDING_UPDATE);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(courseVersionRepository.findFirstByCourseIdAndStatus(COURSE_ID, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseRepository.save(any())).thenReturn(course);
            when(courseMapper.toDetailResponse(course)).thenReturn(stubDetailResponse());

            courseService.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID);

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(course.getSubmittedAt()).isNull();
        }

        @Test
        void throwsCourseStateException_whenDraftCourse() {
            Course course = draftCourse();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> courseService.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── addChapter ────────────────────────────────────────────────────────────

    @Nested
    class AddChapter {

        @Test
        void addsChapterWithCorrectOrderIndex() {
            Course course = draftCourse();
            CreateChapterRequest req = new CreateChapterRequest();
            req.setTitle("Chapter 1");

            Chapter saved = Chapter.builder()
                    .id(UUID.randomUUID())
                    .course(course)
                    .title("Chapter 1")
                    .orderIndex(1)
                    .isDraft(false)
                    .build();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findMaxOrderIndexByCourseId(COURSE_ID)).thenReturn(0);
            when(chapterRepository.save(any())).thenReturn(saved);
            when(chapterMapper.toResponse(saved)).thenReturn(ChapterResponse.builder().build());

            courseService.addChapter(INSTRUCTOR_ID, COURSE_ID, req);

            ArgumentCaptor<Chapter> captor = ArgumentCaptor.forClass(Chapter.class);
            verify(chapterRepository).save(captor.capture());
            assertThat(captor.getValue().getTitle()).isEqualTo("Chapter 1");
            assertThat(captor.getValue().getOrderIndex()).isEqualTo(1);
            assertThat(captor.getValue().getIsDraft()).isFalse();
        }

        @Test
        void chapterFlaggedAsDraft_whenCourseIsPublished() {
            Course course = publishedCourse();
            CreateChapterRequest req = new CreateChapterRequest();
            req.setTitle("New Chapter");

            Chapter saved = Chapter.builder().id(UUID.randomUUID()).isDraft(true).build();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findMaxOrderIndexByCourseId(COURSE_ID)).thenReturn(2);
            when(chapterRepository.save(any())).thenReturn(saved);
            when(chapterMapper.toResponse(any())).thenReturn(ChapterResponse.builder().build());

            courseService.addChapter(INSTRUCTOR_ID, COURSE_ID, req);

            ArgumentCaptor<Chapter> captor = ArgumentCaptor.forClass(Chapter.class);
            verify(chapterRepository).save(captor.capture());
            assertThat(captor.getValue().getIsDraft()).isTrue();
        }
    }

    // ── deleteChapter ─────────────────────────────────────────────────────────

    @Nested
    class DeleteChapter {

        @Test
        void hardDeletesChapter_whenCourseIsNotLive() {
            Course course = draftCourse();
            UUID chapterId = UUID.randomUUID();
            Chapter chapter = Chapter.builder().id(chapterId).course(course).isDraft(false).build();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(chapterId, COURSE_ID))
                    .thenReturn(Optional.of(chapter));

            courseService.deleteChapter(INSTRUCTOR_ID, COURSE_ID, chapterId);

            verify(chapterRepository).delete(chapter);
            verify(chapterRepository, never()).save(any());
        }

        @Test
        void marksPendingDelete_whenLiveChapterInPublishedCourse() {
            Course course = publishedCourse();
            UUID chapterId = UUID.randomUUID();
            Chapter chapter = Chapter.builder().id(chapterId).course(course).isDraft(false).build();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(chapterId, COURSE_ID))
                    .thenReturn(Optional.of(chapter));
            when(chapterRepository.save(any())).thenReturn(chapter);

            courseService.deleteChapter(INSTRUCTOR_ID, COURSE_ID, chapterId);

            assertThat(chapter.getPendingDelete()).isTrue();
            verify(chapterRepository, never()).delete(any());
        }

        @Test
        void hardDeletesDraftChapter_evenWhenCourseIsLive() {
            Course course = publishedCourse();
            UUID chapterId = UUID.randomUUID();
            Chapter chapter = Chapter.builder().id(chapterId).course(course).isDraft(true).build();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(chapterId, COURSE_ID))
                    .thenReturn(Optional.of(chapter));

            courseService.deleteChapter(INSTRUCTOR_ID, COURSE_ID, chapterId);

            verify(chapterRepository).delete(chapter);
            verify(chapterRepository, never()).save(any());
        }

        @Test
        void throwsChapterNotFoundException_whenChapterNotInCourse() {
            Course course = draftCourse();
            UUID chapterId = UUID.randomUUID();

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(chapterRepository.findByIdAndCourseId(chapterId, COURSE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.deleteChapter(INSTRUCTOR_ID, COURSE_ID, chapterId))
                    .isInstanceOf(ChapterNotFoundException.class);
        }
    }

    @Nested
    class ListCoursesCompact {

        @Test
        void returnsCompactListMappedFromCache() {
            Course course = draftCourse();
            when(courseRepository.findAllByInstructorId(eq(INSTRUCTOR_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(course)));
            CourseResponse response = CourseResponse.builder()
                    .id(COURSE_ID).title("Test Course").status(CourseStatus.DRAFT)
                    .level(CourseLevel.BEGINNER).build();
            when(courseMapper.toResponse(course)).thenReturn(response);

            List<CourseCompactResponse> result = courseService.listCoursesCompact(INSTRUCTOR_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(COURSE_ID);
            assertThat(result.get(0).getTitle()).isEqualTo("Test Course");
        }

        @Test
        void returnsEmptyList_whenNoCourses() {
            when(courseRepository.findAllByInstructorId(eq(INSTRUCTOR_ID), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            List<CourseCompactResponse> result = courseService.listCoursesCompact(INSTRUCTOR_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetAssessStats {

        @Test
        void returnsQuizAndBankQuestionCounts() {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));
            when(quizRepository.countByCourseId(COURSE_ID)).thenReturn(3L);
            when(bankQuestionRepository.countByCourseId(COURSE_ID)).thenReturn(42L);

            AssessStatsResponse result = courseService.getAssessStats(INSTRUCTOR_ID, COURSE_ID);

            assertThat(result.getQuizCount()).isEqualTo(3L);
            assertThat(result.getBankQuestionCount()).isEqualTo(42L);
        }

        @Test
        void throwsCourseNotOwnedException_whenInstructorDoesNotOwnCourse() {
            Course course = draftCourse();
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> courseService.getAssessStats(UUID.randomUUID(), COURSE_ID))
                    .isInstanceOf(CourseNotOwnedException.class);
        }
    }
}
