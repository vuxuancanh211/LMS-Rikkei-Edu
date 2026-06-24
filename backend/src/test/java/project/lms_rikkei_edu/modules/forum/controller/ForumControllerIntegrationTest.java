package project.lms_rikkei_edu.modules.forum.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumPostRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumReplyRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumReportRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.UpdateForumPostRequest;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAuthorResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumCourseResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostDetailResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumReplyResponse;
import project.lms_rikkei_edu.modules.forum.service.ForumService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForumControllerIntegrationTest {

    private ForumService forumService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        forumService = mock(ForumService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ForumController(forumService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void getPostsPassesFiltersToService() throws Exception {
        UUID courseId = UUID.randomUUID();
        ForumPostResponse post = postResponse(UUID.randomUUID());
        when(forumService.getPosts(eq(courseId), eq("spring"), eq("qa"), any()))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/forum/posts")
                        .param("courseId", courseId.toString())
                        .param("keyword", "spring")
                        .param("topic", "qa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Forum post"));
    }

    @Test
    void createPostReturnsCreatedResponse() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(forumService.createPost(any(CreateForumPostRequest.class))).thenReturn(postResponse(postId));

        CreateForumPostRequest request = new CreateForumPostRequest();
        request.setCourseId(courseId);
        request.setTopic("qa");
        request.setTitle("Forum post");
        request.setContent("Content");

        mockMvc.perform(post("/api/forum/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.upvoteCount").value(2));
    }

    @Test
    void createPostRejectsInvalidRequest() throws Exception {
        CreateForumPostRequest request = new CreateForumPostRequest();
        request.setCourseId(UUID.randomUUID());
        request.setContent("Content");

        mockMvc.perform(post("/api/forum/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReplyReturnsCreatedResponse() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        when(forumService.createReply(eq(postId), any(CreateForumReplyRequest.class))).thenReturn(replyResponse(replyId, postId));

        CreateForumReplyRequest request = new CreateForumReplyRequest();
        request.setContent("Reply content");

        mockMvc.perform(post("/api/forum/posts/{postId}/replies", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(replyId.toString()))
                .andExpect(jsonPath("$.postId").value(postId.toString()));
    }

    @Test
    void upvotePostReturnsUpdatedPost() throws Exception {
        UUID postId = UUID.randomUUID();
        when(forumService.toggleUpvote(postId)).thenReturn(postResponse(postId));

        mockMvc.perform(post("/api/forum/posts/{postId}/upvote", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upvoted").value(true));
    }

    @Test
    void reportPostReturnsOk() throws Exception {
        UUID postId = UUID.randomUUID();
        CreateForumReportRequest request = new CreateForumReportRequest();
        request.setReason("SPAM");

        mockMvc.perform(post("/api/forum/posts/{postId}/report", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(forumService).reportPost(eq(postId), any(CreateForumReportRequest.class));
    }

    @Test
    void getCoursesReturnsOk() throws Exception {
        ForumCourseResponse course = ForumCourseResponse.builder()
                .id(UUID.randomUUID()).title("Course").canCreatePost(true).canPinPost(false).build();
        when(forumService.getCourses()).thenReturn(List.of(course));

        mockMvc.perform(get("/api/forum/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Course"));
    }

    @Test
    void getPostDetailReturnsOk() throws Exception {
        UUID postId = UUID.randomUUID();
        ForumPostDetailResponse detail = ForumPostDetailResponse.builder()
                .post(postResponse(postId))
                .replies(List.of(replyResponse(UUID.randomUUID(), postId)))
                .build();
        when(forumService.getPostDetail(postId)).thenReturn(detail);

        mockMvc.perform(get("/api/forum/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.id").value(postId.toString()))
                .andExpect(jsonPath("$.replies", hasSize(1)));
    }

    @Test
    void updatePostReturnsOk() throws Exception {
        UUID postId = UUID.randomUUID();
        when(forumService.updatePost(eq(postId), any(UpdateForumPostRequest.class))).thenReturn(postResponse(postId));

        UpdateForumPostRequest request = new UpdateForumPostRequest();
        request.setTopic("announcement");
        request.setTitle("Updated");
        request.setContent("Updated content");

        mockMvc.perform(patch("/api/forum/posts/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()));
    }

    @Test
    void deletePostReturnsNoContent() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(delete("/api/forum/posts/{postId}", postId))
                .andExpect(status().isNoContent());

        verify(forumService).deletePost(postId);
    }

    @Test
    void togglePinReturnsOk() throws Exception {
        UUID postId = UUID.randomUUID();
        ForumPostResponse pinned = postResponse(postId);
        when(forumService.togglePin(postId)).thenReturn(pinned);

        mockMvc.perform(patch("/api/forum/posts/{postId}/pin", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()));
    }

    @Test
    void toggleReplyUpvoteReturnsOk() throws Exception {
        UUID replyId = UUID.randomUUID();
        ForumReplyResponse response = replyResponse(replyId, UUID.randomUUID());
        when(forumService.toggleReplyUpvote(replyId)).thenReturn(response);

        mockMvc.perform(post("/api/forum/replies/{replyId}/upvote", replyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(replyId.toString()));
    }

    @Test
    void reportReplyReturnsOk() throws Exception {
        UUID replyId = UUID.randomUUID();
        CreateForumReportRequest request = new CreateForumReportRequest();
        request.setReason("ABUSE");

        mockMvc.perform(post("/api/forum/replies/{replyId}/report", replyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(forumService).reportReply(eq(replyId), any(CreateForumReportRequest.class));
    }

    @Test
    void updateReplyReturnsOk() throws Exception {
        UUID replyId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(forumService.updateReply(eq(replyId), any(CreateForumReplyRequest.class)))
                .thenReturn(replyResponse(replyId, postId));

        CreateForumReplyRequest request = new CreateForumReplyRequest();
        request.setContent("Updated reply");

        mockMvc.perform(patch("/api/forum/replies/{replyId}", replyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(replyId.toString()));
    }

    @Test
    void deleteReplyReturnsNoContent() throws Exception {
        UUID replyId = UUID.randomUUID();

        mockMvc.perform(delete("/api/forum/replies/{replyId}", replyId))
                .andExpect(status().isNoContent());

        verify(forumService).deleteReply(replyId);
    }

    private ForumPostResponse postResponse(UUID postId) {
        return ForumPostResponse.builder()
                .id(postId)
                .courseId(UUID.randomUUID())
                .courseTitle("Course")
                .author(author())
                .topic("qa")
                .title("Forum post")
                .content("Content")
                .pinned(false)
                .replyCount(1)
                .upvoteCount(2)
                .upvoted(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private ForumReplyResponse replyResponse(UUID replyId, UUID postId) {
        return ForumReplyResponse.builder()
                .id(replyId)
                .postId(postId)
                .courseId(UUID.randomUUID())
                .author(author())
                .content("Reply content")
                .depth(1)
                .upvoteCount(0)
                .upvoted(false)
                .replies(List.of())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private ForumAuthorResponse author() {
        return ForumAuthorResponse.builder()
                .id(UUID.randomUUID())
                .fullName("User")
                .role("STUDENT")
                .build();
    }
}
