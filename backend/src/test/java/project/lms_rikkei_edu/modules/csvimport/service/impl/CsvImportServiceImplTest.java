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
import project.lms_rikkei_edu.infrastructure.email.EmailAsyncService;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportRowResult;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
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

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EmailAsyncService emailAsyncService;

    private CsvImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CsvImportServiceImpl(userService, redisService,
                userRepository, courseEnrollmentRepository, courseRepository, emailAsyncService);
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

        CsvImportPreviewResponse result = service.preview(file, "STUDENT", null, null);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getValidCount()).isEqualTo(2);
        assertThat(result.getFormatErrorCount()).isEqualTo(0);
        assertThat(result.getDuplicateInFileCount()).isEqualTo(0);
        assertThat(result.getDuplicateInDbCount()).isEqualTo(0);
        assertThat(result.getExistingUserCount()).isEqualTo(0);
        assertThat(result.getAlreadyEnrolledCount()).isEqualTo(0);
        assertThat(result.getNameMismatchCount()).isEqualTo(0);
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

        CsvImportPreviewResponse result = service.preview(file, "STUDENT", null, null);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getFormatErrorCount()).isEqualTo(1);
        assertThat(result.getNameMismatchCount()).isEqualTo(0);
        assertThat(result.getAlreadyEnrolledCount()).isEqualTo(0);
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

        CsvImportPreviewResponse result = service.preview(file, "STUDENT", null, null);

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getDuplicateInFileCount()).isEqualTo(1);
        assertThat(result.getNameMismatchCount()).isEqualTo(0);
        assertThat(result.getAlreadyEnrolledCount()).isEqualTo(0);
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

        CsvImportPreviewResponse result = service.preview(file, "STUDENT", null, null);

        assertThat(result.getDuplicateInDbCount()).isEqualTo(1);
        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getNameMismatchCount()).isEqualTo(0);
        assertThat(result.getAlreadyEnrolledCount()).isEqualTo(0);
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

        CsvImportPreviewResponse result = service.preview(file, "STUDENT", null, null);

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getDuplicateInFileCount()).isEqualTo(1);
        assertThat(result.getNameMismatchCount()).isEqualTo(0);
        assertThat(result.getAlreadyEnrolledCount()).isEqualTo(0);
    }

    // ── Preview: with course assignment ──────────────────────────────────────

    @Test
    void preview_withCourse_existingEmailNotEnrolledNameMatch_showsExistingUser() {
        UUID courseId = UUID.randomUUID();
        var file = createCsvMock("test.csv",
                "fullname,email,phone\n" +
                "John,john@test.com,0934567890");
        when(userService.existsByEmail("john@test.com")).thenReturn(true);
        var user = mock(UserEntity.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getFullName()).thenReturn("John");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("john@test.com")).thenReturn(Optional.of(user));
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, user.getId())).thenReturn(false);
        doNothing().when(redisService).set(anyString(), anyString(), anyLong());

        CsvImportPreviewResponse result = service.preview(file, "STUDENT", courseId, null);

        assertThat(result.getExistingUserCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo("EXISTING_USER");
        assertThat(result.getRows().get(0).getErrors()).contains("Email đã tồn tại, sẽ thêm vào khoá học");
    }

    @Test
    void preview_withCourse_existingEmailNameMismatch_showsNameMismatch() {
        UUID courseId = UUID.randomUUID();
        var file = createCsvMock("test.csv",
                "fullname,email,phone\n" +
                "Bob,john@test.com,0934567890");
        when(userService.existsByEmail("john@test.com")).thenReturn(true);
        var user = mock(UserEntity.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getFullName()).thenReturn("John");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("john@test.com")).thenReturn(Optional.of(user));
        doNothing().when(redisService).set(anyString(), anyString(), anyLong());

        CsvImportPreviewResponse result = service.preview(file, "STUDENT", courseId, null);

        assertThat(result.getNameMismatchCount()).isEqualTo(1);
        assertThat(result.getAlreadyEnrolledCount()).isEqualTo(0);
        assertThat(result.getExistingUserCount()).isEqualTo(0);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo("NAME_MISMATCH");
        assertThat(result.getRows().get(0).getErrors()).anyMatch(e -> e.contains("Tên trong file"));
    }

    @Test
    void preview_withCourse_existingEmailAlreadyEnrolled_showsAlreadyEnrolled() {
        UUID courseId = UUID.randomUUID();
        var file = createCsvMock("test.csv",
                "fullname,email,phone\n" +
                "John,john@test.com,0934567890");
        when(userService.existsByEmail("john@test.com")).thenReturn(true);
        var user = mock(UserEntity.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("john@test.com")).thenReturn(Optional.of(user));
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, user.getId())).thenReturn(true);
        doNothing().when(redisService).set(anyString(), anyString(), anyLong());

        CsvImportPreviewResponse result = service.preview(file, "STUDENT", courseId, null);

        assertThat(result.getAlreadyEnrolledCount()).isEqualTo(1);
        assertThat(result.getNameMismatchCount()).isEqualTo(0);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo("ALREADY_ENROLLED");
        assertThat(result.getRows().get(0).getErrors()).anyMatch(e -> e.contains("đã tham gia khoá học này"));
    }

    // ── Preview: validation errors ───────────────────────────────────────────

    @Test
    void preview_invalidFileExtension_throwsException() {
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("data.txt");

        assertThatThrownBy(() -> service.preview(file, "STUDENT", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không đúng định dạng CSV");
    }

    @Test
    void preview_emptyFile_throwsException() {
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> service.preview(file, "STUDENT", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có dữ liệu");
    }

    @Test
    void preview_noDataRows_throwsException() {
        var file = createCsvMock("test.csv",
                "fullname,email,phone");

        assertThatThrownBy(() -> service.preview(file, "STUDENT", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất");
    }

    @Test
    void preview_exceedsMaxRows_throwsException() {
        ReflectionTestUtils.setField(service, "maxCsvRows", 1);
        var file = createCsvMock("test.csv",
                "fullname,email\nJohn,john@test.com\nJane,jane@test.com");

        assertThatThrownBy(() -> service.preview(file, "STUDENT", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vượt quá");
    }

    @Test
    void preview_invalidRole_throwsException() {
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.isEmpty()).thenReturn(false);

        assertThatThrownBy(() -> service.preview(file, "INVALID_ROLE", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vai trò không hợp lệ");
    }

    @Test
    void preview_missingRequiredColumns_throwsException() {
        var file = createCsvMock("test.csv",
                "name,email_address\nJohn,john@test.com");

        assertThatThrownBy(() -> service.preview(file, "STUDENT", null, null))
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
        when(userService.batchCreateUsers(any(UUID.class), anyList(), any())).thenReturn(List.of());

        CsvImportConfirmResponse result = service.confirm("test-token", adminId, null, null);

        assertThat(result.getTotalProcessed()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailCount()).isEqualTo(0);
        assertThat(result.getResults()).allMatch(r -> "IMPORTED".equals(r.getStatus()));
        verify(userService).batchCreateUsers(any(UUID.class), anyList(), any());
    }

    @Test
    void confirm_tokenExpired_throwsException() {
        when(redisService.get(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("invalid-token", UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã hết hạn");
    }

    @Test
    void confirm_batchCreateFails_marksRowsAsFailed() {
        UUID adminId = UUID.randomUUID();
        String json = createPreviewJson("VALID", "VALID");
        when(redisService.get(anyString())).thenReturn(Optional.of(json));
        doNothing().when(redisService).delete(anyString());
        when(userService.batchCreateUsers(any(UUID.class), anyList(), any()))
                .thenThrow(new RuntimeException("DB connection error"));

        CsvImportConfirmResponse result = service.confirm("test-token", adminId, null, null);

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
        when(userService.batchCreateUsers(any(UUID.class), anyList(), any())).thenReturn(List.of());

        CsvImportConfirmResponse result = service.confirm("test-token", adminId, null, null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isEqualTo(0);
        assertThat(result.getResults()).hasSize(2);
        assertThat(result.getResults().get(0).getStatus()).isEqualTo("IMPORTED");
        assertThat(result.getResults().get(1).getStatus()).isEqualTo("DUPLICATE_IN_FILE");
    }

    @Test
    void confirm_withCourse_createsNewUsersAndEnrollsExisting() {
        UUID adminId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        String json = createPreviewJson("VALID", "EXISTING_USER");
        when(redisService.get(anyString())).thenReturn(Optional.of(json));
        doNothing().when(redisService).delete(anyString());
        var course = mock(Course.class);
        when(course.getTitle()).thenReturn("Khoá học mẫu");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(userService.batchCreateUsers(any(UUID.class), anyList(), any())).thenReturn(List.of());
        var existingUser = mock(UserEntity.class);
        when(existingUser.getId()).thenReturn(UUID.randomUUID());
        when(existingUser.getEmail()).thenReturn("user2@test.com");
        when(existingUser.getFullName()).thenReturn("User 2");
        when(userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(anyList())).thenReturn(List.of(existingUser));

        CsvImportConfirmResponse result = service.confirm("test-token", adminId, courseId, null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isEqualTo(0);
        assertThat(result.getResults()).hasSize(2);
        assertThat(result.getResults().get(0).getStatus()).isEqualTo("IMPORTED");
        assertThat(result.getResults().get(1).getStatus()).isEqualTo("IMPORTED");
        verify(courseEnrollmentRepository).saveAll(anyList());
        verify(emailAsyncService).sendEnrolledToCourseMailAsync(eq("user2@test.com"), eq("User 2"), eq("Khoá học mẫu"));
    }

    @Test
    void confirm_withCourse_nameMismatch_enrollsUser() {
        UUID adminId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        String json = createPreviewJson("NAME_MISMATCH");
        when(redisService.get(anyString())).thenReturn(Optional.of(json));
        doNothing().when(redisService).delete(anyString());
        var course = mock(Course.class);
        when(course.getTitle()).thenReturn("Khoá học mẫu");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        var existingUser = mock(UserEntity.class);
        when(existingUser.getId()).thenReturn(UUID.randomUUID());
        when(existingUser.getEmail()).thenReturn("user1@test.com");
        when(existingUser.getFullName()).thenReturn("User 1");
        when(userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(anyList())).thenReturn(List.of(existingUser));

        CsvImportConfirmResponse result = service.confirm("test-token", adminId, courseId, null);

        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailCount()).isEqualTo(0);
        assertThat(result.getTotalProcessed()).isEqualTo(1);
        assertThat(result.getResults()).hasSize(1);
        assertThat(result.getResults().get(0).getStatus()).isEqualTo("IMPORTED");
        verify(courseEnrollmentRepository).saveAll(anyList());
        verify(emailAsyncService).sendEnrolledToCourseMailAsync(eq("user1@test.com"), eq("User 1"), eq("Khoá học mẫu"));
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
        json.append("],\"defaultRole\":\"STUDENT\",");
        json.append("\"courseId\":null}");
        return json.toString();
    }
}
