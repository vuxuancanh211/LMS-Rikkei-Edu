package project.lms_rikkei_edu.modules.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.exception.CourseNotFoundException;
import project.lms_rikkei_edu.modules.course.exception.CourseStateException;
import project.lms_rikkei_edu.modules.course.service.AdminCourseService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminCourseControllerTest {

    private AdminCourseService adminCourseService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID adminId  = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminCourseService  = mock(AdminCourseService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminCourseController(adminCourseService, currentUserProvider))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CourseResponse courseResponse(CourseStatus status) {
        return CourseResponse.builder().id(courseId).title("Test Course").status(status).build();
    }

    private CourseDetailResponse detailResponse(CourseStatus status) {
        return CourseDetailResponse.builder()
                .id(courseId).title("Test Course").status(status).chapters(List.of()).build();
    }

    // ── GET /api/admin/courses/pending ────────────────────────────────────────

    @Nested
    class ListPending {

        @Test
        void returns200_withPagedPendingCourses() throws Exception {
            when(adminCourseService.listPendingCourses(any()))
                    .thenReturn(new PageImpl<>(
                            List.of(courseResponse(CourseStatus.PENDING)),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/admin/courses/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void returns200_withEmptyPage() throws Exception {
            when(adminCourseService.listPendingCourses(any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/admin/courses/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }
    }

    // ── GET /api/admin/courses ────────────────────────────────────────────────

    @Nested
    class ListAll {

        @Test
        void returns200_withAllCourses() throws Exception {
            when(adminCourseService.listAllCourses(any()))
                    .thenReturn(new PageImpl<>(
                            List.of(courseResponse(CourseStatus.PUBLISHED)),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/admin/courses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    // ── GET /api/admin/courses/{courseId} ─────────────────────────────────────

    @Nested
    class GetCourseDetail {

        @Test
        void returns200_withDetail() throws Exception {
            when(adminCourseService.getCourseDetail(courseId))
                    .thenReturn(detailResponse(CourseStatus.PENDING));

            mockMvc.perform(get("/api/admin/courses/{id}", courseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(courseId.toString()));
        }

        @Test
        void returns404_whenCourseNotFound() throws Exception {
            when(adminCourseService.getCourseDetail(courseId))
                    .thenThrow(new CourseNotFoundException(courseId));

            mockMvc.perform(get("/api/admin/courses/{id}", courseId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── POST /api/admin/courses/{courseId}/approve ────────────────────────────

    @Nested
    class Approve {

        @Test
        void returns200_whenApproved() throws Exception {
            when(adminCourseService.approveCourse(adminId, courseId))
                    .thenReturn(detailResponse(CourseStatus.PUBLISHED));

            mockMvc.perform(post("/api/admin/courses/{id}/approve", courseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(courseId.toString()));
        }

        @Test
        void returns409_whenNotPending() throws Exception {
            when(adminCourseService.approveCourse(adminId, courseId))
                    .thenThrow(new CourseStateException("Only PENDING courses can be approved"));

            mockMvc.perform(post("/api/admin/courses/{id}/approve", courseId))
                    .andExpect(status().isConflict());
        }
    }

    // ── POST /api/admin/courses/{courseId}/reject ─────────────────────────────

    @Nested
    class Reject {

        @Test
        void returns200_whenRejected() throws Exception {
            when(adminCourseService.rejectCourse(eq(adminId), eq(courseId), any()))
                    .thenReturn(detailResponse(CourseStatus.REJECTED));

            mockMvc.perform(post("/api/admin/courses/{id}/reject", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Nội dung không đạt yêu cầu\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void returns400_whenReasonBlank() throws Exception {
            mockMvc.perform(post("/api/admin/courses/{id}/reject", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns409_whenNotPending() throws Exception {
            when(adminCourseService.rejectCourse(any(), any(), any()))
                    .thenThrow(new CourseStateException("Only PENDING courses can be rejected"));

            mockMvc.perform(post("/api/admin/courses/{id}/reject", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"reason\"}"))
                    .andExpect(status().isConflict());
        }
    }

    // ── POST /api/admin/courses/{courseId}/approve-update ─────────────────────

    @Nested
    class ApproveUpdate {

        @Test
        void returns200_whenApproved() throws Exception {
            when(adminCourseService.approveUpdate(adminId, courseId))
                    .thenReturn(detailResponse(CourseStatus.PUBLISHED));

            mockMvc.perform(post("/api/admin/courses/{id}/approve-update", courseId))
                    .andExpect(status().isOk());
        }

        @Test
        void returns409_whenNotPendingUpdate() throws Exception {
            when(adminCourseService.approveUpdate(adminId, courseId))
                    .thenThrow(new CourseStateException("Only PENDING_UPDATE courses can have updates approved"));

            mockMvc.perform(post("/api/admin/courses/{id}/approve-update", courseId))
                    .andExpect(status().isConflict());
        }
    }

    // ── POST /api/admin/courses/{courseId}/reject-update ──────────────────────

    @Nested
    class RejectUpdate {

        @Test
        void returns200_whenRejected() throws Exception {
            when(adminCourseService.rejectUpdate(eq(adminId), eq(courseId), any()))
                    .thenReturn(detailResponse(CourseStatus.PUBLISHED));

            mockMvc.perform(post("/api/admin/courses/{id}/reject-update", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Ảnh bìa không phù hợp\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void returns400_whenReasonBlank() throws Exception {
            mockMvc.perform(post("/api/admin/courses/{id}/reject-update", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/admin/courses/{courseId}/versions/diff ───────────────────────

    @Nested
    class GetVersionDiff {

        @Test
        void returns200_withDiff() throws Exception {
            CourseDiffResponse diff = CourseDiffResponse.builder()
                    .pendingVersionId(UUID.randomUUID())
                    .pendingVersionNumber(2)
                    .chapters(List.of())
                    .resources(List.of())
                    .metadata(CourseDiffResponse.MetaDiff.builder()
                            .title(CourseDiffResponse.FieldDiff.builder()
                                    .oldValue("Old").newValue("New").changed(true).build())
                            .build())
                    .build();

            when(adminCourseService.getVersionDiff(courseId)).thenReturn(diff);

            mockMvc.perform(get("/api/admin/courses/{id}/versions/diff", courseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pendingVersionNumber").value(2));
        }

        @Test
        void returns409_whenNoPendingVersion() throws Exception {
            when(adminCourseService.getVersionDiff(courseId))
                    .thenThrow(new CourseStateException("Không có version PENDING để so sánh"));

            mockMvc.perform(get("/api/admin/courses/{id}/versions/diff", courseId))
                    .andExpect(status().isConflict());
        }
    }
}
