package project.lms_rikkei_edu.modules.forum.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAttachmentResponse;
import project.lms_rikkei_edu.modules.forum.service.ForumAttachmentService;
import project.lms_rikkei_edu.modules.forum.service.ForumAttachmentService.AttachmentContent;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForumAttachmentControllerTest {

    private ForumAttachmentService forumAttachmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        forumAttachmentService = mock(ForumAttachmentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ForumAttachmentController(forumAttachmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadReturnsAttachmentResponse() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        ForumAttachmentResponse response = ForumAttachmentResponse.builder()
                .id(attachmentId)
                .fileName("photo.jpg")
                .url("/api/forum/attachments/" + attachmentId + "/content?token=abc")
                .contentType("image/jpeg")
                .sizeBytes(4096)
                .attachmentType("IMAGE")
                .build();

        when(forumAttachmentService.upload(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg",
                "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        mockMvc.perform(multipart("/api/forum/attachments").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attachmentId.toString()))
                .andExpect(jsonPath("$.fileName").value("photo.jpg"))
                .andExpect(jsonPath("$.attachmentType").value("IMAGE"));
    }

    @Test
    void contentReturnsFileBytes() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        byte[] fileBytes = "pdf-content".getBytes();
        AttachmentContent content = new AttachmentContent(fileBytes, "application/pdf", "doc.pdf");

        when(forumAttachmentService.getContent(eq(attachmentId), eq("valid-token"))).thenReturn(content);

        mockMvc.perform(get("/api/forum/attachments/{id}/content", attachmentId)
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(fileBytes));
    }

    @Test
    void contentRejectsInvalidToken() throws Exception {
        UUID attachmentId = UUID.randomUUID();

        when(forumAttachmentService.getContent(eq(attachmentId), eq("bad-token")))
                .thenThrow(new project.lms_rikkei_edu.common.exception.BusinessException(
                        "Invalid attachment token", org.springframework.http.HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/forum/attachments/{id}/content", attachmentId)
                        .param("token", "bad-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void contentReturns404WhenNotFound() throws Exception {
        UUID attachmentId = UUID.randomUUID();

        when(forumAttachmentService.getContent(eq(attachmentId), eq("token")))
                .thenThrow(new project.lms_rikkei_edu.common.exception.BusinessException(
                        "Attachment not found", org.springframework.http.HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/forum/attachments/{id}/content", attachmentId)
                        .param("token", "token"))
                .andExpect(status().isNotFound());
    }
}
