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
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.csvimport.dto.request.CsvImportConfirmRequest;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportRowResult;
import project.lms_rikkei_edu.modules.csvimport.service.CsvImportService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
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

class CsvImportControllerTest {

    private CsvImportService csvImportService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        csvImportService = mock(CsvImportService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CsvImportController(csvImportService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    private CsvImportRowResult row(int rowNumber, String fullName, String email, String status) {
        return CsvImportRowResult.builder()
                .rowNumber(rowNumber)
                .fullName(fullName)
                .email(email)
                .phoneNumber("")
                .status(status)
                .errors(List.of())
                .build();
    }

    @Nested
    class Preview {

        @Test
        void returns200_withPreviewRows() throws Exception {
            when(csvImportService.preview(any(), eq("STUDENT"), any()))
                    .thenReturn(CsvImportPreviewResponse.builder()
                            .token("token-1")
                            .totalRows(2)
                            .validCount(1)
                            .existingUserCount(1)
                            .formatErrorCount(0)
                            .duplicateInFileCount(0)
                            .duplicateInDbCount(0)
                            .alreadyEnrolledCount(0)
                            .nameMismatchCount(0)
                            .rows(List.of(
                                    row(2, "John", "john@test.com", "VALID"),
                                    row(3, "Jane", "jane@test.com", "EXISTING_USER")
                            ))
                            .build());

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "users.csv",
                    MediaType.TEXT_PLAIN_VALUE,
                    "fullname,email,phone\nJohn,john@test.com,0934567890\nJane,jane@test.com,0987654321"
                            .getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/api/admin/users/import-csv/preview")
                            .file(file)
                            .param("defaultRole", "STUDENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("token-1"))
                    .andExpect(jsonPath("$.totalRows").value(2))
                    .andExpect(jsonPath("$.validCount").value(1))
                    .andExpect(jsonPath("$.existingUserCount").value(1))
                    .andExpect(jsonPath("$.rows", hasSize(2)));

            verify(csvImportService).preview(any(), eq("STUDENT"), any());
        }

        @Test
        void returns400_whenMissingDefaultRole() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "users.csv",
                    MediaType.TEXT_PLAIN_VALUE,
                    "fullname,email\nJohn,john@test.com".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/api/admin/users/import-csv/preview")
                            .file(file))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Confirm {

        @Test
        void returns200_withConfirmResult() throws Exception {
            UUID adminId = UUID.randomUUID();
            when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));
            when(csvImportService.confirm(eq("token-1"), eq(adminId), any()))
                    .thenReturn(CsvImportConfirmResponse.builder()
                            .totalProcessed(2)
                            .successCount(1)
                            .failCount(0)
                            .results(List.of(
                                    row(2, "John", "john@test.com", "IMPORTED"),
                                    row(3, "Jane", "jane@test.com", "IMPORTED")
                            ))
                            .build());

            CsvImportConfirmRequest request = new CsvImportConfirmRequest();
            request.setToken("token-1");

            mockMvc.perform(post("/api/admin/users/import-csv/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalProcessed").value(2))
                    .andExpect(jsonPath("$.successCount").value(1))
                    .andExpect(jsonPath("$.results[0].status").value("IMPORTED"));

            verify(csvImportService).confirm(eq("token-1"), eq(adminId), any());
        }

        @Test
        void returns400_whenTokenMissing() throws Exception {
            mockMvc.perform(post("/api/admin/users/import-csv/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns400_whenTokenBlank() throws Exception {
            mockMvc.perform(post("/api/admin/users/import-csv/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
