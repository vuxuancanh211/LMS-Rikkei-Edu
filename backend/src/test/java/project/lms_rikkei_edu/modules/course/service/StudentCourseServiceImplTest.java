package project.lms_rikkei_edu.modules.course.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentGroupEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentSubmissionEntity;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentGroupRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentSubmissionRepository;
import project.lms_rikkei_edu.modules.certificate.service.CertificateService;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import project.lms_rikkei_edu.modules.course.dto.request.UpdateProgressRequest;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.impl.StudentCourseServiceImpl;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentCourseServiceImplTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock private CourseProgressRepository courseProgressRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private CourseMapper courseMapper;
    @Mock private S3Service s3Service;
    @Mock private UserRepository userRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query nativeQuery;
    @Mock private project.lms_rikkei_edu.modules.quiz.repository.QuizAttemptRepository quizAttemptRepository;
    @Mock private project.lms_rikkei_edu.modules.quiz.repository.QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    @Mock private project.lms_rikkei_edu.modules.quiz.repository.ProctoringViolationLogRepository proctoringViolationLogRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private AssignmentSubmissionRepository assignmentSubmissionRepository;
    @Mock private AssignmentGroupRepository assignmentGroupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private CertificateService certificateService;

    @InjectMocks private StudentCourseServiceImpl studentCourseService;

    private UUID studentId;
    private UUID courseId;
    private UUID lessonId;
    private UUID resourceId;
    private UUID instructorId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(studentCourseService, "presignedUrlExpiry", 3600L);
        ReflectionTestUtils.setField(studentCourseService, "entityManager", entityManager);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        lenient().when(nativeQuery.setParameter(anyInt(), any())).thenReturn(nativeQuery);
        lenient().when(nativeQuery.getSingleResult()).thenReturn(1);
        studentId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        lessonId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
    }

    @Test
    void getEnrolledCourses_empty_returnsEmptyList() {
        when(courseRepository.findEnrolledCoursesByStudentId(studentId)).thenReturn(Collections.emptyList());
        List<StudentCourseResponse> result = studentCourseService.getEnrolledCourses(studentId);
        assertThat(result).isEmpty();
    }

    @Test
    void getEnrolledCourses_success_returnsCoursesWithProgressAndInstructor() {
        Course course = new Course();
        course.setId(courseId);
        course.setTitle("Java Boot");
        course.setStatus(CourseStatus.PUBLISHED);
        course.setInstructorId(instructorId);

        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setDurationSeconds(7200);
        chapter.setLessons(List.of(lesson));
        course.setChapters(List.of(chapter));

        UserEntity instructor = new UserEntity();
        instructor.setId(instructorId);
        instructor.setFullName("Nguyễn Văn A");

        CourseProgressEntity progress = new CourseProgressEntity();
        progress.setCourseId(courseId);
        progress.setOverallPercentage(BigDecimal.valueOf(50));
        progress.setStatus("IN_PROGRESS");

        when(courseRepository.findEnrolledCoursesByStudentId(studentId)).thenReturn(List.of(course));
        when(userRepository.findAllById(any())).thenReturn(List.of(instructor));
        when(courseProgressRepository.findByStudentIdAndCourseIdIn(eq(studentId), any())).thenReturn(List.of(progress));

        List<StudentCourseResponse> result = studentCourseService.getEnrolledCourses(studentId);
        assertThat(result).hasSize(1);
        StudentCourseResponse resp = result.get(0);
        assertThat(resp.getTitle()).isEqualTo("Java Boot");
        assertThat(resp.getInstructor()).isEqualTo("Nguyễn Văn A");
        assertThat(resp.getProgress()).isEqualTo(50);
        assertThat(resp.getSStatus()).isEqualTo("learning");
        assertThat(resp.getHours()).isEqualTo(2);
    }

    @Test
    void getEnrolledCourses_pendingUpdateCourse_stillVisible() {
        // Regression: khóa học đã publish nhưng đang có bản cập nhật chờ duyệt (PENDING_UPDATE)
        // trước đây bị lọc mất khỏi danh sách "khóa học của tôi" — học viên vẫn phải thấy được
        // bản đã publish trong lúc chờ admin duyệt bản cập nhật.
        Course course = new Course();
        course.setId(courseId);
        course.setTitle("PostgreSQL Nâng Cao");
        course.setStatus(CourseStatus.PENDING_UPDATE);
        course.setInstructorId(instructorId);

        when(courseRepository.findEnrolledCoursesByStudentId(studentId)).thenReturn(List.of(course));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(courseProgressRepository.findByStudentIdAndCourseIdIn(eq(studentId), any())).thenReturn(List.of());

        List<StudentCourseResponse> result = studentCourseService.getEnrolledCourses(studentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("PostgreSQL Nâng Cao");
    }

    @Test
    void getEnrolledCourses_draftCourse_stillExcluded() {
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.DRAFT);
        course.setInstructorId(instructorId);
        when(courseRepository.findEnrolledCoursesByStudentId(studentId)).thenReturn(List.of(course));

        List<StudentCourseResponse> result = studentCourseService.getEnrolledCourses(studentId);

        assertThat(result).isEmpty();
    }

    @Test
    void getCourseDetail_notFound_throwsException() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> studentCourseService.getCourseDetail(studentId, courseId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không tìm thấy khóa học");
    }

    @Test
    void getCourseDetail_notPublished_throwsException() {
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.DRAFT);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> studentCourseService.getCourseDetail(studentId, courseId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Khóa học chưa được xuất bản");
    }

    @Test
    void getCourseDetail_notEnrolled_throwsException() {
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> studentCourseService.getCourseDetail(studentId, courseId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bạn chưa đăng ký khóa học này");
    }

    @Test
    void getCourseDetail_pendingUpdate_succeedsAndHidesDraftFields() {
        // Regression: trước đây PENDING_UPDATE bị chặn cứng ở đây, khiến học viên không xem được
        // khóa học đã publish trong lúc chờ duyệt bản cập nhật. Đồng thời phải che các trường
        // draft cấp khóa học (hasPendingDraft/draftTitle/...) khỏi học viên.
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.PENDING_UPDATE);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(List.of());

        CourseDetailResponse detailResp = CourseDetailResponse.builder()
                .id(courseId)
                .title("Bản đã publish")
                .hasPendingDraft(true)
                .draftTitle("Bản đang chờ duyệt")
                .draftDescription("Mô tả mới chưa duyệt")
                .changeSummary("Sửa lỗi chính tả")
                .build();
        when(courseMapper.toDetailResponse(course)).thenReturn(detailResp);

        CourseDetailResponse result = studentCourseService.getCourseDetail(studentId, courseId);

        assertThat(result.getTitle()).isEqualTo("Bản đã publish");
        assertThat(result.isHasPendingDraft()).isFalse();
        assertThat(result.getDraftTitle()).isNull();
        assertThat(result.getDraftDescription()).isNull();
        assertThat(result.getChangeSummary()).isNull();
    }

    @Test
    void getCourseDetail_success_attachesLessonProgress() {
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);

        ChapterResponse chResp = ChapterResponse.builder().build();
        LessonResponse lResp = LessonResponse.builder()
                .id(lessonId)
                .build();
        ReflectionTestUtils.setField(chResp, "lessons", List.of(lResp));

        CourseDetailResponse detailResp = CourseDetailResponse.builder()
                .id(courseId)
                .chapters(List.of(chResp))
                .build();
        when(courseMapper.toDetailResponse(course)).thenReturn(detailResp);

        LessonProgressEntity lProg = new LessonProgressEntity();
        lProg.setLessonId(lessonId);
        lProg.setStatus("COMPLETED");
        lProg.setLessonPercentage(BigDecimal.valueOf(100));
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(List.of(lProg));

        CourseDetailResponse result = studentCourseService.getCourseDetail(studentId, courseId);
        assertThat(result.getChapters().get(0).getLessons().get(0).getProgress()).isEqualTo("COMPLETED");
        assertThat(result.getChapters().get(0).getLessons().get(0).getProgressPercentage()).isEqualTo(100);
    }

    @Test
    void getCourseDetail_filtersOutDraftChaptersLessonsAndNeverPublishedResources() {
        // Regression: học viên trước đây thấy cả chương/bài mới thêm (isDraft=true, chưa duyệt)
        // và tài liệu vừa upload trong bản cập nhật đang chờ (isNewInUpdate=true, chưa duyệt) vì
        // getCourseDetail() dùng chung mapper với giảng viên mà không lọc.
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(List.of());

        UUID liveLessonId = UUID.randomUUID();
        UUID draftLessonId = UUID.randomUUID();
        LessonResourceResponse liveRes = LessonResourceResponse.builder().id(UUID.randomUUID()).isNewInUpdate(false).build();
        LessonResourceResponse newRes = LessonResourceResponse.builder().id(UUID.randomUUID()).isNewInUpdate(true).build();
        LessonResponse liveLesson = LessonResponse.builder().id(liveLessonId).isDraft(false).build();
        ReflectionTestUtils.setField(liveLesson, "resources", new ArrayList<>(List.of(liveRes, newRes)));
        LessonResponse draftLesson = LessonResponse.builder().id(draftLessonId).isDraft(true).build();

        ChapterResponse liveChapter = ChapterResponse.builder().isDraft(false).build();
        ReflectionTestUtils.setField(liveChapter, "lessons", new ArrayList<>(List.of(liveLesson, draftLesson)));
        ChapterResponse draftChapter = ChapterResponse.builder().isDraft(true).build();

        CourseDetailResponse detailResp = CourseDetailResponse.builder()
                .id(courseId)
                .chapters(new ArrayList<>(List.of(liveChapter, draftChapter)))
                .build();
        when(courseMapper.toDetailResponse(course)).thenReturn(detailResp);

        CourseDetailResponse result = studentCourseService.getCourseDetail(studentId, courseId);

        assertThat(result.getChapters()).hasSize(1);
        assertThat(result.getChapters().get(0).getLessons()).hasSize(1);
        assertThat(result.getChapters().get(0).getLessons().get(0).getId()).isEqualTo(liveLessonId);
        assertThat(result.getChapters().get(0).getLessons().get(0).getResources()).hasSize(1);
        assertThat(result.getChapters().get(0).getLessons().get(0).getResources().get(0).getIsNewInUpdate()).isFalse();
    }

    @Test
    void getResourceViewUrl_neverPublishedResource_throws() {
        // Regression: chặn truy cập trực tiếp bằng resourceId vào tài liệu chưa từng publish,
        // kể cả khi không đi qua danh sách đã lọc ở getCourseDetail().
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        LessonResource res = new LessonResource();
        res.setId(resourceId);
        res.setIsNewInUpdate(true);
        lesson.setResources(List.of(res));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> studentCourseService.getResourceViewUrl(studentId, courseId, lessonId, resourceId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không tìm thấy tài liệu");
    }

    @Test
    void getResourceViewUrl_extUrl_returnsDirectUrl() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        LessonResource res = new LessonResource();
        res.setId(resourceId);
        res.setS3Key("ext://https://external.com/doc.pdf");
        lesson.setResources(List.of(res));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        ResourceDownloadUrlResponse resp = studentCourseService.getResourceViewUrl(studentId, courseId, lessonId, resourceId);
        assertThat(resp.getUrl()).isEqualTo("https://external.com/doc.pdf");
    }

    @Test
    void getResourceViewUrl_s3Key_returnsPresignedUrl() throws Exception {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        LessonResource res = new LessonResource();
        res.setId(resourceId);
        res.setS3Key("resources/doc.pdf");
        lesson.setResources(List.of(res));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URI("https://s3.aws.com/doc.pdf").toURL());
        when(s3Service.generatePresignedInlineUrl(eq("resources/doc.pdf"), anyLong())).thenReturn(presigned);

        ResourceDownloadUrlResponse resp = studentCourseService.getResourceViewUrl(studentId, courseId, lessonId, resourceId);
        assertThat(resp.getUrl()).isEqualTo("https://s3.aws.com/doc.pdf");
    }

    @Test
    void getResourceDownloadUrl_success() throws Exception {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        LessonResource res = new LessonResource();
        res.setId(resourceId);
        res.setS3Key("resources/doc.pdf");
        lesson.setResources(List.of(res));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URI("https://s3.aws.com/doc.pdf").toURL());
        when(s3Service.generatePresignedGetUrl(eq("resources/doc.pdf"), anyLong())).thenReturn(presigned);

        ResourceDownloadUrlResponse resp = studentCourseService.getResourceDownloadUrl(studentId, courseId, lessonId, resourceId);
        assertThat(resp.getUrl()).isEqualTo("https://s3.aws.com/doc.pdf");
    }

    @Test
    void updateLessonProgress_videoLesson_updatesProgressAndCourseProgress() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setHlsManifestUrl("https://hls.com/master.m3u8");
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.empty());

        Course course = new Course();
        course.setId(courseId);
        Chapter chapter = new Chapter();
        chapter.setLessons(List.of(lesson));
        course.setChapters(List.of(chapter));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setWatchedPercentage(BigDecimal.valueOf(95));
        req.setLastPlaybackPosition(120);

        studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req);

        verify(lessonProgressRepository).save(argThat(p -> 
            p.getLessonId().equals(lessonId) &&
            "COMPLETED".equals(p.getStatus()) &&
            p.getLessonPercentage().intValue() == 95
        ));
        verify(courseProgressRepository).save(any());
    }

    @Test
    void updateLessonProgress_videoLesson_videoS3Key_detectsVideo() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setVideoS3Key("videos/lecture.mp4");
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.empty());

        Course course = new Course();
        course.setId(courseId);
        Chapter chapter = new Chapter();
        chapter.setLessons(List.of(lesson));
        course.setChapters(List.of(chapter));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setWatchedPercentage(BigDecimal.valueOf(50));

        studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req);

        verify(lessonProgressRepository).save(argThat(p ->
            "IN_PROGRESS".equals(p.getStatus())
        ));
    }

    @Test
    void updateLessonProgress_videoAndDocumentCombined_bothComplete_marksCompleted() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        LessonResource resVideo = new LessonResource();
        resVideo.setResourceType(ResourceType.VIDEO);
        LessonResource resPdf = new LessonResource();
        resPdf.setResourceType(ResourceType.PDF);
        lesson.setResources(List.of(resVideo, resPdf));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.empty());

        Course course = new Course();
        course.setId(courseId);
        Chapter chapter = new Chapter();
        chapter.setLessons(List.of(lesson));
        course.setChapters(List.of(chapter));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setWatchedPercentage(BigDecimal.valueOf(95));
        req.setDocumentViewSeconds(15);

        studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req);

        verify(lessonProgressRepository).save(argThat(p ->
            "COMPLETED".equals(p.getStatus()) &&
            p.getLessonPercentage().intValue() == 100
        ));
    }

    @Test
    void updateLessonProgress_videoAndDocumentCombined_partial_showsPartialPercentage() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        LessonResource resVideo = new LessonResource();
        resVideo.setResourceType(ResourceType.VIDEO);
        LessonResource resPdf = new LessonResource();
        resPdf.setResourceType(ResourceType.PDF);
        lesson.setResources(List.of(resVideo, resPdf));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.empty());

        Course course = new Course();
        course.setId(courseId);
        Chapter chapter = new Chapter();
        chapter.setLessons(List.of(lesson));
        course.setChapters(List.of(chapter));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        // wp=50, dv=5 -> vidScore=min(50/90,1)*80=44.44, docScore=min(5/10,1)*20=10 -> 54%
        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setWatchedPercentage(BigDecimal.valueOf(50));
        req.setDocumentViewSeconds(5);

        studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req);

        verify(lessonProgressRepository).save(argThat(p ->
            "IN_PROGRESS".equals(p.getStatus()) &&
            p.getLessonPercentage().intValue() == 54
        ));
    }

    @Test
    void updateLessonProgress_documentOnlyPartial_showsPartialPercentage() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        LessonResource resPdf = new LessonResource();
        resPdf.setResourceType(ResourceType.PDF);
        lesson.setResources(List.of(resPdf));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.empty());

        Course course = new Course();
        course.setId(courseId);
        Chapter chapter = new Chapter();
        chapter.setLessons(List.of(lesson));
        course.setChapters(List.of(chapter));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        // dv=10 (<20), wp=0 -> pctByTime=min(10*100/20,100)=50 -> 50%
        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setDocumentViewSeconds(10);

        studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req);

        verify(lessonProgressRepository).save(argThat(p ->
            "IN_PROGRESS".equals(p.getStatus()) &&
            p.getLessonPercentage().intValue() == 50
        ));
    }

    @Test
    void updateLessonProgress_textOnlyContentType_detectsAsDocument() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setType(LessonType.TEXT);
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.empty());

        Course course = new Course();
        course.setId(courseId);
        Chapter chapter = new Chapter();
        chapter.setLessons(List.of(lesson));
        course.setChapters(List.of(chapter));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setDocumentViewSeconds(25);
        req.setCompleted(true);

        studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req);

        verify(lessonProgressRepository).save(argThat(p ->
            "COMPLETED".equals(p.getStatus()) &&
            p.getLessonPercentage().intValue() == 100
        ));
    }

    @Test
    void updateLessonProgress_documentLesson_updatesProgress() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        LessonResource res = new LessonResource();
        res.setResourceType(ResourceType.PDF);
        lesson.setResources(List.of(res));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        LessonProgressEntity existingProg = new LessonProgressEntity();
        existingProg.setLessonId(lessonId);
        existingProg.setDocumentViewSeconds(5);
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.of(existingProg));

        Course course = new Course();
        course.setId(courseId);
        Chapter chapter = new Chapter();
        chapter.setLessons(List.of(lesson));
        course.setChapters(List.of(chapter));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setDocumentViewSeconds(25);
        req.setCompleted(true);

        studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req);

        verify(lessonProgressRepository).save(argThat(p -> 
            p.getDocumentViewSeconds() == 25 &&
            "COMPLETED".equals(p.getStatus()) &&
            p.getLessonPercentage().intValue() == 100
        ));
    }

    @Test
    void getEnrolledCourses_withNullAndBranches() {
        Course course = new Course();
        course.setId(courseId);
        course.setTitle("Java Advanced");
        course.setStatus(CourseStatus.PUBLISHED);
        course.setInstructorId(instructorId);
        course.setLevel(project.lms_rikkei_edu.modules.course.enums.CourseLevel.BEGINNER);

        CourseCategory cat = new CourseCategory();
        cat.setName("Backend");
        course.setCategory(cat);

        // chapter with null lessons
        Chapter chNullLessons = new Chapter();
        chNullLessons.setLessons(null);

        // chapter with lesson having null duration
        Chapter chLessonNullDur = new Chapter();
        Lesson lNullDur = new Lesson();
        lNullDur.setId(lessonId);
        lNullDur.setDurationSeconds(null);
        chLessonNullDur.setLessons(List.of(lNullDur));
        course.setChapters(List.of(chNullLessons, chLessonNullDur));

        UserEntity instructor = new UserEntity();
        instructor.setId(instructorId);
        instructor.setFullName("Nguyễn Văn B");

        CourseProgressEntity prog = new CourseProgressEntity();
        prog.setCourseId(courseId);
        prog.setStatus("IN_PROGRESS");
        prog.setOverallPercentage(null); // test null percentage branch

        when(courseRepository.findEnrolledCoursesByStudentId(studentId)).thenReturn(List.of(course));
        when(userRepository.findAllById(any())).thenReturn(List.of(instructor));
        when(courseProgressRepository.findByStudentIdAndCourseIdIn(eq(studentId), any())).thenReturn(List.of(prog));

        List<StudentCourseResponse> result = studentCourseService.getEnrolledCourses(studentId);
        assertThat(result).hasSize(1);
        StudentCourseResponse resp = result.get(0);
        assertThat(resp.getCategory()).isEqualTo("Backend");
        assertThat(resp.getLevel()).isEqualTo("BEGINNER");
        assertThat(resp.getProgress()).isZero();
        assertThat(resp.getSStatus()).isEqualTo("learning");
    }

    @Test
    void getCourseDetail_deletedCourse_throwsException() {
        Course course = new Course();
        course.setId(courseId);
        course.setDeletedAt(Instant.now());
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> studentCourseService.getCourseDetail(studentId, courseId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Khóa học đã bị xóa");
    }

    @Test
    void getCourseDetail_nullChaptersOrNullLessons_handledSafely() {
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);

        CourseDetailResponse detailResp = CourseDetailResponse.builder()
                .id(courseId)
                .chapters(null)
                .build();
        when(courseMapper.toDetailResponse(course)).thenReturn(detailResp);
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Collections.emptyList());

        CourseDetailResponse result = studentCourseService.getCourseDetail(studentId, courseId);
        assertThat(result.getChapters()).isNull();
    }

    @Test
    void updateLessonProgress_noVideoNoDoc_completesImmediately() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setResources(Collections.emptyList()); // no video, no doc
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        LessonProgressEntity prog = new LessonProgressEntity();
        prog.setLessonId(lessonId);
        prog.setStatus(null);
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.of(prog));
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(List.of(prog));

        Course course = new Course();
        course.setId(courseId);
        course.setChapters(null); // tests getCourseLessonCount when chapters == null
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        UpdateProgressRequest req = new UpdateProgressRequest();
        studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req);

        verify(lessonProgressRepository).save(argThat(p ->
            "COMPLETED".equals(p.getStatus()) &&
            p.getCompletedAt() != null &&
            p.getLessonPercentage().intValue() == 100
        ));
    }

    @Test
    void updateLessonProgress_videoLessonInProgressAndPendingDeleteFiltered() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        LessonResource resVideo = new LessonResource();
        resVideo.setResourceType(ResourceType.VIDEO);
        lesson.setResources(List.of(resVideo));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        LessonProgressEntity prog = new LessonProgressEntity();
        prog.setLessonId(lessonId);
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.of(prog));

        Course course = new Course();
        course.setId(courseId);
        Chapter ch1 = new Chapter();
        ch1.setPendingDelete(true); // filtered out
        Chapter ch2 = new Chapter();
        ch2.setPendingDelete(false);
        Lesson lDel = new Lesson();
        lDel.setPendingDelete(true); // filtered out
        Lesson lActive = new Lesson();
        lActive.setPendingDelete(false);
        ch2.setLessons(List.of(lDel, lActive));
        course.setChapters(List.of(ch1, ch2));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        CourseProgressEntity existingCourseProg = new CourseProgressEntity();
        existingCourseProg.setCourseId(courseId);
        when(courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.of(existingCourseProg));
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(List.of(prog));

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setWatchedPercentage(BigDecimal.valueOf(50)); // < 90 -> IN_PROGRESS
        req.setDocumentViewSeconds(10); // test lower accumulation branch when existing is higher
        prog.setDocumentViewSeconds(15);

        studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req);

        verify(lessonProgressRepository).save(argThat(p ->
            "IN_PROGRESS".equals(p.getStatus()) &&
            p.getLessonPercentage().intValue() == 50 &&
            p.getDocumentViewSeconds() == 15
        ));
        verify(courseProgressRepository).save(argThat(cp ->
            cp.getTotalLessonsCount() == 1 && cp.getCompletedLessonsCount() == 0
        ));
    }

    @Test
    void updateLessonProgress_quizTypeLesson_throwsBusinessException() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setType(LessonType.QUIZ);
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        UpdateProgressRequest req = new UpdateProgressRequest();

        assertThatThrownBy(() -> studentCourseService.updateLessonProgress(studentId, courseId, lessonId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đề trắc nghiệm");

        verify(lessonProgressRepository, never()).save(any());
    }

    @Test
    void completeQuizLesson_noExistingProgress_createsCompletedProgress() {
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.empty());
        Course course = new Course();
        course.setId(courseId);
        course.setChapters(List.of());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(List.of());

        studentCourseService.completeQuizLesson(studentId, courseId, lessonId);

        verify(lessonProgressRepository).save(argThat(p ->
                p.getLessonId().equals(lessonId) &&
                p.getStudentId().equals(studentId) &&
                "COMPLETED".equals(p.getStatus()) &&
                p.getLessonPercentage().intValue() == 100 &&
                p.getCompletedAt() != null
        ));
        verify(courseProgressRepository).save(any());
    }

    @Test
    void completeQuizLesson_existingProgressAlreadyCompleted_keepsOriginalCompletedAt() {
        Instant originalCompletedAt = Instant.now().minusSeconds(3600);
        LessonProgressEntity existing = new LessonProgressEntity();
        existing.setLessonId(lessonId);
        existing.setStudentId(studentId);
        existing.setStatus("COMPLETED");
        existing.setCompletedAt(originalCompletedAt);
        when(lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)).thenReturn(Optional.of(existing));
        Course course = new Course();
        course.setId(courseId);
        course.setChapters(List.of());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(List.of(existing));

        studentCourseService.completeQuizLesson(studentId, courseId, lessonId);

        verify(lessonProgressRepository).save(argThat(p -> p.getCompletedAt().equals(originalCompletedAt)));
    }

    @Test
    void resetLessonProgressForInProgressStudents_deletesProgressForNonCompletedStudents() {
        UUID inProgressStudent = UUID.randomUUID();
        UUID completedStudent = UUID.randomUUID();
        CourseProgressEntity inProgressCp = new CourseProgressEntity();
        inProgressCp.setStudentId(inProgressStudent);
        inProgressCp.setStatus("IN_PROGRESS");
        CourseProgressEntity completedCp = new CourseProgressEntity();
        completedCp.setStudentId(completedStudent);
        completedCp.setStatus("COMPLETED");
        when(courseProgressRepository.findByCourseId(courseId)).thenReturn(List.of(inProgressCp, completedCp));

        LessonProgressEntity progress = new LessonProgressEntity();
        progress.setStudentId(inProgressStudent);
        progress.setLessonId(lessonId);
        when(lessonProgressRepository.findByStudentIdAndLessonId(inProgressStudent, lessonId))
                .thenReturn(Optional.of(progress));

        Course course = new Course();
        course.setId(courseId);
        course.setChapters(List.of());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseProgressRepository.findByStudentIdAndCourseId(any(), eq(courseId))).thenReturn(Optional.empty());
        when(lessonProgressRepository.findByStudentIdAndCourseId(any(), eq(courseId))).thenReturn(List.of());

        studentCourseService.resetLessonProgressForInProgressStudents(courseId, lessonId);

        verify(lessonProgressRepository).delete(progress);
        verify(lessonProgressRepository, never()).findByStudentIdAndLessonId(completedStudent, lessonId);
        verify(courseProgressRepository, times(1)).save(any());
    }

    @Test
    void resetLessonProgressForInProgressStudents_noExistingLessonProgress_stillUpdatesCourseProgress() {
        UUID inProgressStudent = UUID.randomUUID();
        CourseProgressEntity inProgressCp = new CourseProgressEntity();
        inProgressCp.setStudentId(inProgressStudent);
        inProgressCp.setStatus("IN_PROGRESS");
        when(courseProgressRepository.findByCourseId(courseId)).thenReturn(List.of(inProgressCp));
        when(lessonProgressRepository.findByStudentIdAndLessonId(inProgressStudent, lessonId))
                .thenReturn(Optional.empty());

        Course course = new Course();
        course.setId(courseId);
        course.setChapters(List.of());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseProgressRepository.findByStudentIdAndCourseId(inProgressStudent, courseId)).thenReturn(Optional.empty());
        when(lessonProgressRepository.findByStudentIdAndCourseId(inProgressStudent, courseId)).thenReturn(List.of());

        studentCourseService.resetLessonProgressForInProgressStudents(courseId, lessonId);

        verify(lessonProgressRepository, never()).delete(any());
        verify(courseProgressRepository).save(any());
    }

    @Test
    void findResource_lessonOrResourceNotFound_throwsException() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentCourseService.getResourceViewUrl(studentId, courseId, lessonId, resourceId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không tìm thấy bài học");

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setResources(Collections.emptyList());
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> studentCourseService.getResourceViewUrl(studentId, courseId, lessonId, resourceId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không tìm thấy tài liệu");
    }

    // ── resetProgressForStudents ───────────────────────────────────────────────

    @Test
    void resetProgressForStudents_skips_whenCourseIdIsNull() {
        studentCourseService.resetProgressForStudents(null, List.of(studentId));
        verify(quizAttemptRepository, never()).findByCourseIdAndStudentIdIn(any(), any());
    }

    @Test
    void resetProgressForStudents_skips_whenStudentIdsIsNull() {
        studentCourseService.resetProgressForStudents(courseId, null);
        verify(quizAttemptRepository, never()).findByCourseIdAndStudentIdIn(any(), any());
    }

    @Test
    void resetProgressForStudents_skips_whenStudentIdsEmpty() {
        studentCourseService.resetProgressForStudents(courseId, List.of());
        verify(quizAttemptRepository, never()).findByCourseIdAndStudentIdIn(any(), any());
    }

    @Test
    void resetProgressForStudents_deletesQuizAndLessonProgress() {
        var attempt = new project.lms_rikkei_edu.modules.quiz.entity.QuizAttemptEntity();
        attempt.setId(UUID.randomUUID());
        when(quizAttemptRepository.findByCourseIdAndStudentIdIn(courseId, List.of(studentId)))
                .thenReturn(List.of(attempt));

        studentCourseService.resetProgressForStudents(courseId, List.of(studentId));

        verify(quizAttemptAnswerRepository).deleteByAttemptIdIn(anyList());
        verify(proctoringViolationLogRepository).deleteByAttemptIdIn(anyList());
        verify(quizAttemptRepository).deleteByCourseIdAndStudentIdIn(courseId, List.of(studentId));
        verify(lessonProgressRepository).deleteByCourseIdAndStudentIdIn(courseId, List.of(studentId));
        verify(courseProgressRepository).deleteByCourseIdAndStudentIdIn(courseId, List.of(studentId));
    }

    @Test
    void resetProgressForStudents_skipsQuizDeletion_whenNoAttempts() {
        when(quizAttemptRepository.findByCourseIdAndStudentIdIn(courseId, List.of(studentId)))
                .thenReturn(List.of());

        studentCourseService.resetProgressForStudents(courseId, List.of(studentId));

        verify(quizAttemptAnswerRepository, never()).deleteByAttemptIdIn(anyList());
        verify(proctoringViolationLogRepository, never()).deleteByAttemptIdIn(anyList());
        verify(quizAttemptRepository, never()).deleteByCourseIdAndStudentIdIn(any(), anyList());
        verify(lessonProgressRepository).deleteByCourseIdAndStudentIdIn(courseId, List.of(studentId));
        verify(courseProgressRepository).deleteByCourseIdAndStudentIdIn(courseId, List.of(studentId));
    }

    @Test
    void resetProgressForStudents_handlesExceptionGracefully() {
        when(quizAttemptRepository.findByCourseIdAndStudentIdIn(courseId, List.of(studentId)))
                .thenThrow(new RuntimeException("DB error"));

        studentCourseService.resetProgressForStudents(courseId, List.of(studentId));

        // Should not propagate exception
        verify(quizAttemptAnswerRepository, never()).deleteByAttemptIdIn(anyList());
        verify(lessonProgressRepository, never()).deleteByCourseIdAndStudentIdIn(any(), anyList());
    }

    @Test
    void resetStudentCourseProgress_delegatesToResetProgressForStudents() {
        studentCourseService.resetStudentCourseProgress(courseId, studentId);
        verify(lessonProgressRepository).deleteByCourseIdAndStudentIdIn(courseId, List.of(studentId));
        verify(courseProgressRepository).deleteByCourseIdAndStudentIdIn(courseId, List.of(studentId));
    }

    @Test
    void resetStudentCourseProgress_skips_whenCourseIdNull() {
        studentCourseService.resetStudentCourseProgress(null, studentId);
        verify(quizAttemptRepository, never()).findByCourseIdAndStudentIdIn(any(), any());
        verify(lessonProgressRepository, never()).deleteByCourseIdAndStudentIdIn(any(), anyList());
    }

    @Test
    void resetStudentCourseProgress_skips_whenStudentIdNull() {
        studentCourseService.resetStudentCourseProgress(courseId, null);
        verify(quizAttemptRepository, never()).findByCourseIdAndStudentIdIn(any(), any());
        verify(lessonProgressRepository, never()).deleteByCourseIdAndStudentIdIn(any(), anyList());
    }

    // ── new tests for assignment progress ──

    @Test
    void getCourseDetail_withProgress_setsAssignmentCounts() {
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)).thenReturn(true);
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Collections.emptyList());

        CourseDetailResponse detailResp = CourseDetailResponse.builder()
                .id(courseId)
                .build();
        when(courseMapper.toDetailResponse(course)).thenReturn(detailResp);

        CourseProgressEntity prog = new CourseProgressEntity();
        prog.setCompletedAssignmentsCount(3);
        prog.setTotalAssignmentsCount(5);
        when(courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.of(prog));

        CourseDetailResponse result = studentCourseService.getCourseDetail(studentId, courseId);

        assertThat(result.getCompletedAssignments()).isEqualTo(3);
        assertThat(result.getTotalAssignments()).isEqualTo(5);
    }

    @Test
    void recalculateCourseProgress_delegatesToUpdateCourseProgress() {
        when(assignmentRepository.findPublishedByCourseId(courseId)).thenReturn(Collections.emptyList());
        Course course = new Course();
        course.setId(courseId);
        course.setChapters(Collections.emptyList());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Collections.emptyList());
        when(courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());

        studentCourseService.recalculateCourseProgress(studentId, courseId);

        verify(courseProgressRepository).save(argThat(cp ->
            cp.getTotalAssignmentsCount() == 0 &&
            cp.getCompletedAssignmentsCount() == 0
        ));
    }

    @Test
    void updateAssignmentProgress_delegatesToUpdateCourseProgress() {
        when(assignmentRepository.findPublishedByCourseId(courseId)).thenReturn(Collections.emptyList());
        Course course = new Course();
        course.setId(courseId);
        course.setChapters(Collections.emptyList());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Collections.emptyList());
        when(courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());

        studentCourseService.updateAssignmentProgress(studentId, courseId);

        verify(courseProgressRepository).save(argThat(cp ->
            cp.getTotalAssignmentsCount() == 0 &&
            cp.getCompletedAssignmentsCount() == 0
        ));
    }

    @Test
    void updateCourseProgress_assignmentStats_allGroupsScope_countsCompleted() {
        UUID assignmentId = UUID.randomUUID();
        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setScope(AssignmentScope.ALL_GROUPS);
        assignment.setPassingScore(BigDecimal.valueOf(5));
        when(assignmentRepository.findPublishedByCourseId(courseId)).thenReturn(List.of(assignment));
        UUID groupId = UUID.randomUUID();
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId)).thenReturn(List.of(groupId));

        AssignmentSubmissionEntity submission = new AssignmentSubmissionEntity();
        submission.setAssignmentId(assignmentId);
        submission.setScore(BigDecimal.valueOf(7));
        submission.setScorePublishedAt(OffsetDateTime.now());
        when(assignmentSubmissionRepository.findByStudentIdAndAssignmentIdIn(eq(studentId), anyList())).thenReturn(List.of(submission));

        Course course = new Course();
        course.setId(courseId);
        course.setChapters(Collections.emptyList());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Collections.emptyList());
        when(courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());

        studentCourseService.recalculateCourseProgress(studentId, courseId);

        verify(courseProgressRepository).save(argThat(cp ->
            cp.getTotalAssignmentsCount() == 1 &&
            cp.getCompletedAssignmentsCount() == 1
        ));
    }

    @Test
    void updateCourseProgress_assignmentStats_specificGroupsScope_filtersByGroup() {
        UUID matchingId = UUID.randomUUID();
        UUID nonMatchingId = UUID.randomUUID();
        UUID studentGroupId = UUID.randomUUID();

        AssignmentEntity matchingAssignment = new AssignmentEntity();
        matchingAssignment.setId(matchingId);
        matchingAssignment.setScope(AssignmentScope.SPECIFIC_GROUPS);
        matchingAssignment.setPassingScore(BigDecimal.valueOf(5));

        AssignmentEntity nonMatchingAssignment = new AssignmentEntity();
        nonMatchingAssignment.setId(nonMatchingId);
        nonMatchingAssignment.setScope(AssignmentScope.SPECIFIC_GROUPS);
        nonMatchingAssignment.setPassingScore(BigDecimal.valueOf(5));

        when(assignmentRepository.findPublishedByCourseId(courseId)).thenReturn(List.of(matchingAssignment, nonMatchingAssignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId)).thenReturn(List.of(studentGroupId));

        AssignmentGroupEntity ag1 = new AssignmentGroupEntity();
        ag1.setAssignmentId(matchingId);
        ag1.setGroupId(studentGroupId);
        AssignmentGroupEntity ag2 = new AssignmentGroupEntity();
        ag2.setAssignmentId(nonMatchingId);
        ag2.setGroupId(UUID.randomUUID());
        when(assignmentGroupRepository.findByAssignmentId(matchingId)).thenReturn(List.of(ag1));
        when(assignmentGroupRepository.findByAssignmentId(nonMatchingId)).thenReturn(List.of(ag2));

        AssignmentSubmissionEntity submission = new AssignmentSubmissionEntity();
        submission.setAssignmentId(matchingId);
        submission.setScore(BigDecimal.valueOf(7));
        submission.setScorePublishedAt(OffsetDateTime.now());
        when(assignmentSubmissionRepository.findByStudentIdAndAssignmentIdIn(eq(studentId), anyList())).thenReturn(List.of(submission));

        Course course = new Course();
        course.setId(courseId);
        course.setChapters(Collections.emptyList());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Collections.emptyList());
        when(courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());

        studentCourseService.recalculateCourseProgress(studentId, courseId);

        verify(courseProgressRepository).save(argThat(cp ->
            cp.getTotalAssignmentsCount() == 1 &&
            cp.getCompletedAssignmentsCount() == 1
        ));
    }

    @Test
    void updateCourseProgress_assignmentStats_countsOnlyScorePublished() {
        UUID idPublished = UUID.randomUUID();
        UUID idNotPublished = UUID.randomUUID();

        AssignmentEntity a1 = new AssignmentEntity();
        a1.setId(idPublished);
        a1.setScope(AssignmentScope.ALL_GROUPS);
        a1.setPassingScore(BigDecimal.valueOf(4));

        AssignmentEntity a2 = new AssignmentEntity();
        a2.setId(idNotPublished);
        a2.setScope(AssignmentScope.ALL_GROUPS);
        a2.setPassingScore(BigDecimal.valueOf(5));

        when(assignmentRepository.findPublishedByCourseId(courseId)).thenReturn(List.of(a1, a2));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId)).thenReturn(Collections.emptyList());

        AssignmentSubmissionEntity s1 = new AssignmentSubmissionEntity();
        s1.setAssignmentId(idPublished);
        s1.setScorePublishedAt(OffsetDateTime.now());
        s1.setScore(BigDecimal.valueOf(5));

        AssignmentSubmissionEntity s2 = new AssignmentSubmissionEntity();
        s2.setAssignmentId(idNotPublished);
        s2.setScorePublishedAt(null);
        s2.setScore(BigDecimal.valueOf(7));

        when(assignmentSubmissionRepository.findByStudentIdAndAssignmentIdIn(eq(studentId), anyList())).thenReturn(List.of(s1, s2));

        Course course = new Course();
        course.setId(courseId);
        course.setChapters(Collections.emptyList());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(lessonProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Collections.emptyList());
        when(courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());

        studentCourseService.recalculateCourseProgress(studentId, courseId);

        verify(courseProgressRepository).save(argThat(cp ->
            cp.getTotalAssignmentsCount() == 2 &&
            cp.getCompletedAssignmentsCount() == 1
        ));
    }

    // ── end new tests ──
}
