package project.lms_rikkei_edu.modules.notification.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.notification.dto.request.UpdateNotificationPreferenceRequest;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationPreferenceResponse;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationPreferenceControllerIntegrationTest {

    private NotificationPreferenceService preferenceService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        preferenceService = mock(NotificationPreferenceService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new NotificationPreferenceController(preferenceService, currentUserProvider)
        ).build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
    }

    @Test
    void getPreferencesReturnsList() throws Exception {
        List<NotificationPreferenceResponse> prefs = List.of(
                NotificationPreferenceResponse.builder()
                        .id(UUID.randomUUID())
                        .type("FORUM_REPLY")
                        .inAppEnabled(true)
                        .emailEnabled(true)
                        .pushEnabled(false)
                        .build(),
                NotificationPreferenceResponse.builder()
                        .id(null)
                        .type("QUIZ_PUBLISHED")
                        .inAppEnabled(true)
                        .emailEnabled(true)
                        .pushEnabled(true)
                        .build()
        );

        when(preferenceService.getPreferences(userId)).thenReturn(prefs);

        mockMvc.perform(get("/api/notifications/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("FORUM_REPLY"))
                .andExpect(jsonPath("$[0].inAppEnabled").value(true))
                .andExpect(jsonPath("$[0].pushEnabled").value(false))
                .andExpect(jsonPath("$[1].type").value("QUIZ_PUBLISHED"))
                .andExpect(jsonPath("$[1].pushEnabled").value(true));
    }

    @Test
    void updatePreferenceReturnsUpdated() throws Exception {
        String type = "FORUM_REPLY";
        NotificationPreferenceResponse response = NotificationPreferenceResponse.builder()
                .id(UUID.randomUUID())
                .type(type)
                .inAppEnabled(false)
                .emailEnabled(true)
                .pushEnabled(false)
                .build();

        when(preferenceService.updatePreference(eq(userId), eq(type), any(UpdateNotificationPreferenceRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/notifications/preferences/{type}", type)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "inAppEnabled": false,
                                    "emailEnabled": true,
                                    "pushEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.inAppEnabled").value(false))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(false));
    }

    @Test
    void updatePreferencePassesCorrectRequest() throws Exception {
        String type = "QUIZ_PUBLISHED";

        mockMvc.perform(put("/api/notifications/preferences/{type}", type)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "inAppEnabled": true,
                                    "emailEnabled": false,
                                    "pushEnabled": true
                                }
                                """))
                .andExpect(status().isOk());

        verify(preferenceService).updatePreference(eq(userId), eq(type), any(UpdateNotificationPreferenceRequest.class));
    }
}
