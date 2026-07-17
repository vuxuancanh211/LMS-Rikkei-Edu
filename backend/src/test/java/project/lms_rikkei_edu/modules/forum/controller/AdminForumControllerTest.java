package project.lms_rikkei_edu.modules.forum.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.modules.forum.dto.response.AdminForumPostResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.AdminForumReportResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAuthorResponse;
import project.lms_rikkei_edu.modules.forum.service.AdminForumService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminForumControllerTest {

    private AdminForumService adminForumService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminForumService = mock(AdminForumService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminForumController(adminForumService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getPostsReturnsPagedPosts() throws Exception {
        UUID postId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 10);
        var post = AdminForumPostResponse.builder()
                .id(postId)
                .courseId(UUID.randomUUID())
                .courseTitle("Java Spring")
                .author(author("Forum Author"))
                .topic("qa")
                .title("Question")
                .contentPreview("Preview")
                .reportCount(2)
                .pendingReportCount(1)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(adminForumService.getPosts(eq("spring"), eq(true), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));

        mockMvc.perform(get("/api/admin/forum/posts")
                        .param("keyword", "spring")
                        .param("reportedOnly", "true")
                        .param("includeDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId.toString()))
                .andExpect(jsonPath("$.content[0].courseTitle").value("Java Spring"))
                .andExpect(jsonPath("$.content[0].reportCount").value(2))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getReportsReturnsPagedReports() throws Exception {
        UUID reportId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 10);
        var report = AdminForumReportResponse.builder()
                .id(reportId)
                .targetType("POST")
                .targetId(UUID.randomUUID())
                .targetTitle("Reported post")
                .targetContentPreview("Bad content")
                .postId(UUID.randomUUID())
                .courseTitle("Java Spring")
                .reason("SPAM")
                .status("PENDING")
                .reporter(author("Reporter"))
                .createdAt(OffsetDateTime.now())
                .build();
        when(adminForumService.getReports(eq("PENDING"), eq("POST"), any()))
                .thenReturn(new PageImpl<>(List.of(report), pageable, 1));

        mockMvc.perform(get("/api/admin/forum/reports")
                        .param("status", "PENDING")
                        .param("targetType", "POST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(reportId.toString()))
                .andExpect(jsonPath("$.content[0].targetTitle").value("Reported post"))
                .andExpect(jsonPath("$.content[0].reporter.fullName").value("Reporter"));
    }

    @Test
    void reviewReportDelegatesToService() throws Exception {
        UUID reportId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/forum/reports/{reportId}", reportId)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\",\"deleteTarget\":true}"))
                .andExpect(status().isNoContent());

        verify(adminForumService).reviewReport(eq(reportId), any());
    }

    @Test
    void deletePostDelegatesToService() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/forum/posts/{postId}", postId))
                .andExpect(status().isNoContent());

        verify(adminForumService).deletePost(postId);
    }

    @Test
    void deleteReplyDelegatesToService() throws Exception {
        UUID replyId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/forum/replies/{replyId}", replyId))
                .andExpect(status().isNoContent());

        verify(adminForumService).deleteReply(replyId);
    }

    private ForumAuthorResponse author(String fullName) {
        return ForumAuthorResponse.builder()
                .id(UUID.randomUUID())
                .fullName(fullName)
                .role("STUDENT")
                .avatarUrl("avatar.png")
                .build();
    }
}
