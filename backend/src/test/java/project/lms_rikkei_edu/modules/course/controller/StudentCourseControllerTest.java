package project.lms_rikkei_edu.modules.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.course.dto.request.UpdateProgressRequest;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.service.StudentCourseService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudentCourseControllerTest {

    private StudentCourseService studentCourseService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID studentId = UUID.randomUUID();
    private final UUID courseId  = UUID.randomUUID();
    private final UUID lessonId  = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        studentCourseService = mock(StudentCourseService.class);
        currentUserProvider  = mock(CurrentUserProvider.class);

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new StudentCourseController(studentCourseService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getEnrolledCourses_unauthorized_throwsException() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/student/courses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEnrolledCourses_success() throws Exception {
        StudentCourseResponse resp = StudentCourseResponse.builder()
                .id(courseId)
                .title("Spring Boot Fullstack")
                .progress(80)
                .build();
        when(studentCourseService.getEnrolledCourses(studentId)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/student/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Spring Boot Fullstack"))
                .andExpect(jsonPath("$[0].progress").value(80));
    }

    @Test
    void getCourseDetail_success() throws Exception {
        CourseDetailResponse resp = CourseDetailResponse.builder()
                .id(courseId)
                .title("Spring Boot Fullstack")
                .chapters(Collections.emptyList())
                .build();
        when(studentCourseService.getCourseDetail(studentId, courseId)).thenReturn(resp);

        mockMvc.perform(get("/api/student/courses/" + courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(courseId.toString()))
                .andExpect(jsonPath("$.title").value("Spring Boot Fullstack"));
    }

    @Test
    void getResourceViewUrl_success() throws Exception {
        ResourceDownloadUrlResponse resp = ResourceDownloadUrlResponse.builder()
                .url("https://s3.aws.com/doc.pdf")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(studentCourseService.getResourceViewUrl(studentId, courseId, lessonId, resourceId)).thenReturn(resp);

        mockMvc.perform(get("/api/student/courses/" + courseId + "/lessons/" + lessonId + "/resources/" + resourceId + "/view-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://s3.aws.com/doc.pdf"));
    }

    @Test
    void getResourceDownloadUrl_success() throws Exception {
        ResourceDownloadUrlResponse resp = ResourceDownloadUrlResponse.builder()
                .url("https://s3.aws.com/doc.pdf")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(studentCourseService.getResourceDownloadUrl(studentId, courseId, lessonId, resourceId)).thenReturn(resp);

        mockMvc.perform(get("/api/student/courses/" + courseId + "/lessons/" + lessonId + "/resources/" + resourceId + "/download-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://s3.aws.com/doc.pdf"));
    }

    @Test
    void updateLessonProgress_success() throws Exception {
        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setWatchedPercentage(BigDecimal.valueOf(100));
        req.setLastPlaybackPosition(300);

        mockMvc.perform(post("/api/student/courses/" + courseId + "/lessons/" + lessonId + "/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(studentCourseService).updateLessonProgress(eq(studentId), eq(courseId), eq(lessonId), any());
    }
}
