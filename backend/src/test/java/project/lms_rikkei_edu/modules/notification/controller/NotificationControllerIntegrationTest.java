package project.lms_rikkei_edu.modules.notification.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationResponse;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerIntegrationTest {

    private NotificationService notificationService;
    private SseEmitterRegistry sseEmitterRegistry;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        sseEmitterRegistry = mock(SseEmitterRegistry.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService, sseEmitterRegistry, currentUserProvider))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
    }

    @Test
    void getNotificationsReturnsPage() throws Exception {
        NotificationResponse notification = notificationResponse(UUID.randomUUID(), userId);
        when(notificationService.getNotifications(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(notification.getId().toString()))
                .andExpect(jsonPath("$.content[0].title").value("Notification title"));
    }

    @Test
    void getUnreadCountReturnsCountObject() throws Exception {
        when(notificationService.getUnreadCount(userId)).thenReturn(5L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    void markAsReadReturnsOk() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId))
                .andExpect(status().isOk());

        verify(notificationService).markAsRead(notificationId, userId);
    }

    @Test
    void markAllAsReadReturnsOk() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk());

        verify(notificationService).markAllAsRead(userId);
    }

    @Test
    void connectReturnsSseEmitter() throws Exception {
        SseEmitter emitter = new SseEmitter(1000L);
        when(sseEmitterRegistry.register(userId)).thenReturn(emitter);

        mockMvc.perform(get("/api/notifications/sse"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sseEmitterRegistry).register(userId);
    }

    private NotificationResponse notificationResponse(UUID notificationId, UUID recipientId) {
        return NotificationResponse.builder()
                .id(notificationId)
                .recipientId(recipientId)
                .type("FORUM_REPLY")
                .title("Notification title")
                .body("Notification body")
                .priority("NORMAL")
                .read(false)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
