package project.lms_rikkei_edu.modules.csvimport.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportRowResult;
import project.lms_rikkei_edu.modules.user.service.UserService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private RedisService redisService;

    private CsvImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CsvImportServiceImpl(userService, redisService);
        ReflectionTestUtils.setField(service, "maxCsvRows", 500);
    }

    // ── Preview: valid cases ─────────────────────────────────────────────────

    @Test
    void preview_success_allValid() {
        var file = createCsvMock("test.csv",
                "fullname,email,phone\n" +
                "John,john@test.com,0934567890\n" +
                "Jane,jane@test.com,0987654321");
        when(userService.existsByEmail("john@test.com")).thenReturn(false);
        when(userService.existsByEmail("jane@test.com")).thenReturn(false);
        when(userService.existsByPhoneNumber("0934567890")).thenReturn(false);
        when(userService.existsByPhoneNumber("0987654321")).thenReturn(false);
        doNothing().when(redisService).set(anyString(), anyString(), anyLong());

        CsvImportPreviewResponse result = service.preview(file, "STUDENT");

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getValidCount()).isEqualTo(2);
        assertThat(result.getFormatErrorCount()).isEqualTo(0);
        assertThat(result.getDuplicateInFileCount()).isEqualTo(0);
        assertThat(result.getDuplicateInDbCount()).isEqualTo(0);
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows()).allMatch(r -> "VALID".equals(r.getStatus()));
    }

    @Test
    void preview_withFormatError() {
        var file = createCsvMock("test.csv",
                "fullname,email,phone\n" +
                "John,john@test.com,0934567890\n" +
                ",jane@test.com,0987654321");
        when(userService.existsByEmail("john@test.com")).thenReturn(false);
        when(userService.existsByPhoneNumber("0934567890")).thenReturn(false);
        doNothing().when(redisService).set(anyString(), anyString(), anyLong());

        CsvImportPreviewResponse result = service.preview(file, "STUDENT");

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getFormatErrorCount()).isEqualTo(1);
        assertThat(result.getRows().get(1).getStatus()).isEqualTo("FORMAT_ERROR");
        assertThat(result.getRows().get(1).getErrors()).isNotEmpty();
    }

    @Test
    void preview_withDuplicateInFile() {
        var file = createCsvMock("test.csv",
                "fullname,email,phone\n" +
                "John,john@test.com,0934567890\n" +
                "Jane,john@test.com,0987654321");
        when(userService.existsByEmail("john@test.com")).thenReturn(false);
        when(userService.existsByPhoneNumber("0934567890")).thenReturn(false);
        doNothing().when(redisService).set(anyString(), anyString(), anyLong());

        CsvImportPreviewResponse result = service.preview(file, "STUDENT");

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getDuplicateInFileCount()).isEqualTo(1);
        assertThat(result.getRows().get(1).getStatus()).isEqualTo("DUPLICATE_IN_FILE");
    }

    @Test
    void preview_withDuplicateInDb() {
        var file = createCsvMock("test.csv",
                "fullname,email,phone\n" +
                "John,john@test.com,0934567890\n" +
                "Jane,jane@test.com,0987654321");
        when(userService.existsByEmail("john@test.com")).thenReturn(true);
        when(userService.existsByEmail("jane@test.com")).thenReturn(false);
        doNothing().when(redisService).set(anyString(), anyString(), anyLong());

        CsvImportPreviewResponse result = service.preview(file, "STUDENT");

        assertThat(result.getDuplicateInDbCount()).isEqualTo(1);
        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo("DUPLICATE_IN_DB");
    }

    @Test
    void preview_withDuplicatePhoneInFile() {
        var file = createCsvMock("test.csv",
                "fullname,email,phone\n" +
                "John,john@test.com,0934567890\n" +
                "Jane,jane@test.com,0934567890");
        when(userService.existsByEmail("john@test.com")).thenReturn(false);
        when(userService.existsByPhoneNumber("0934567890")).thenReturn(false);
        doNothing().when(redisService).set(anyString(), anyString(), anyLong());

        CsvImportPreviewResponse result = service.preview(file, "STUDENT");

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getDuplicateInFileCount()).isEqualTo(1);
    }

    // ── Preview: validation errors ───────────────────────────────────────────

    @Test
    void preview_invalidFileExtension_throwsException() {
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("data.txt");

        assertThatThrownBy(() -> service.preview(file, "STUDENT"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không đúng định dạng CSV");
    }

    @Test
    void preview_emptyFile_throwsException() {
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> service.preview(file, "STUDENT"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có dữ liệu");
    }

    @Test
    void preview_noDataRows_throwsException() {
        var file = createCsvMock("test.csv",
                "fullname,email,phone");

        assertThatThrownBy(() -> service.preview(file, "STUDENT"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất");
    }

    @Test
    void preview_exceedsMaxRows_throwsException() {
        ReflectionTestUtils.setField(service, "maxCsvRows", 1);
        var file = createCsvMock("test.csv",
                "fullname,email\nJohn,john@test.com\nJane,jane@test.com");

        assertThatThrownBy(() -> service.preview(file, "STUDENT"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vượt quá");
    }

    @Test
    void preview_invalidRole_throwsException() {
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.isEmpty()).thenReturn(false);

        assertThatThrownBy(() -> service.preview(file, "INVALID_ROLE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vai trò không hợp lệ");
    }

    @Test
    void preview_missingRequiredColumns_throwsException() {
        var file = createCsvMock("test.csv",
                "name,email_address\nJohn,john@test.com");

        assertThatThrownBy(() -> service.preview(file, "STUDENT"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Thiếu cột bắt buộc");
    }

    // ── Confirm ──────────────────────────────────────────────────────────────

    @Test
    void confirm_success_importsAllValidRows() {
        UUID adminId = UUID.randomUUID();
        String json = createPreviewJson("VALID", "VALID");
        when(redisService.get(anyString())).thenReturn(Optional.of(json));
        doNothing().when(redisService).delete(anyString());
        when(userService.batchCreateUsers(any(UUID.class), anyList())).thenReturn(List.of());

        CsvImportConfirmResponse result = service.confirm("test-token", adminId);

        assertThat(result.getTotalProcessed()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailCount()).isEqualTo(0);
        assertThat(result.getResults()).allMatch(r -> "IMPORTED".equals(r.getStatus()));
        verify(userService).batchCreateUsers(eq(adminId), anyList());
    }

    @Test
    void confirm_tokenExpired_throwsException() {
        when(redisService.get(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("invalid-token", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã hết hạn");
    }

    @Test
    void confirm_batchCreateFails_marksRowsAsFailed() {
        UUID adminId = UUID.randomUUID();
        String json = createPreviewJson("VALID", "VALID");
        when(redisService.get(anyString())).thenReturn(Optional.of(json));
        doNothing().when(redisService).delete(anyString());
        when(userService.batchCreateUsers(any(UUID.class), anyList()))
                .thenThrow(new RuntimeException("DB connection error"));

        CsvImportConfirmResponse result = service.confirm("test-token", adminId);

        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailCount()).isEqualTo(2);
        assertThat(result.getResults()).allMatch(r -> "IMPORT_FAILED".equals(r.getStatus()));
    }

    @Test
    void confirm_skipsNonValidRows() {
        UUID adminId = UUID.randomUUID();
        String json = createPreviewJson("VALID", "DUPLICATE_IN_FILE");
        when(redisService.get(anyString())).thenReturn(Optional.of(json));
        doNothing().when(redisService).delete(anyString());
        when(userService.batchCreateUsers(any(UUID.class), anyList())).thenReturn(List.of());

        CsvImportConfirmResponse result = service.confirm("test-token", adminId);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isEqualTo(0);
        assertThat(result.getResults()).hasSize(2);
        assertThat(result.getResults().get(0).getStatus()).isEqualTo("DUPLICATE_IN_FILE");
        assertThat(result.getResults().get(1).getStatus()).isEqualTo("IMPORTED");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MultipartFile createCsvMock(String filename, String content) {
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.isEmpty()).thenReturn(false);
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(
                    content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Unexpected", e);
        }
        return file;
    }

    private String createPreviewJson(String... statuses) {
        StringBuilder json = new StringBuilder("{\"rows\":[");
        for (int i = 0; i < statuses.length; i++) {
            if (i > 0) json.append(",");
            json.append("{\"rowNumber\":").append(i + 2).append(",");
            json.append("\"fullName\":\"User ").append(i + 1).append("\",");
            json.append("\"email\":\"user").append(i + 1).append("@test.com\",");
            json.append("\"phoneNumber\":\"0934567890\",");
            json.append("\"status\":\"").append(statuses[i]).append("\",");
            json.append("\"errors\":[]}");
        }
        json.append("],\"defaultRole\":\"STUDENT\"}");
        return json.toString();
    }
}
