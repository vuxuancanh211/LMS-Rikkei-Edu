package project.lms_rikkei_edu.modules.csvimport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.modules.csvimport.dto.request.GroupMemberCsvImportConfirmRequest;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportRowResult;
import project.lms_rikkei_edu.modules.csvimport.service.GroupMemberCsvImportService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupMemberCsvImportControllerTest {

    private GroupMemberCsvImportService service;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = mock(GroupMemberCsvImportService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GroupMemberCsvImportController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    class Preview {

        @Test
        void returns200_withPreviewRows() throws Exception {
            when(service.preview(eq(groupId), any())).thenReturn(GroupMemberCsvImportPreviewResponse.builder()
                    .token("token-1")
                    .totalRows(1)
                    .validCount(1)
                    .rows(List.of(row(2, "student@test.com", "VALID")))
                    .build());

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "students.csv",
                    MediaType.TEXT_PLAIN_VALUE,
                    "email\nstudent@test.com".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/api/instructor/groups/{groupId}/members/import-csv/preview", groupId)
                            .file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("token-1"))
                    .andExpect(jsonPath("$.validCount").value(1))
                    .andExpect(jsonPath("$.rows", hasSize(1)));

            verify(service).preview(eq(groupId), any());
        }
    }

    @Nested
    class Confirm {

        @Test
        void returns200_withConfirmResult() throws Exception {
            when(service.confirm(groupId, "token-1")).thenReturn(GroupMemberCsvImportConfirmResponse.builder()
                    .totalProcessed(1)
                    .successCount(1)
                    .failCount(0)
                    .results(List.of(row(2, "student@test.com", "IMPORTED")))
                    .build());
            GroupMemberCsvImportConfirmRequest request = new GroupMemberCsvImportConfirmRequest();
            request.setToken("token-1");

            mockMvc.perform(post("/api/instructor/groups/{groupId}/members/import-csv/confirm", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.successCount").value(1))
                    .andExpect(jsonPath("$.results[0].status").value("IMPORTED"));
        }

        @Test
        void returns400_whenTokenMissing() throws Exception {
            mockMvc.perform(post("/api/instructor/groups/{groupId}/members/import-csv/confirm", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    private GroupMemberCsvImportRowResult row(int rowNumber, String email, String status) {
        return GroupMemberCsvImportRowResult.builder()
                .rowNumber(rowNumber)
                .email(email)
                .status(status)
                .errors(List.of())
                .build();
    }
}
