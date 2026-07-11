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
import project.lms_rikkei_edu.modules.course.dto.request.UpdateProgressRequest;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.impl.StudentCourseServiceImpl;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
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
        assertThat(resp.getProgress()).isEqualTo(0);
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
}

