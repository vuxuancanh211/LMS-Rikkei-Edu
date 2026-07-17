package project.lms_rikkei_edu.modules.csvimport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.email.EmailAsyncService;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportRowResult;
import project.lms_rikkei_edu.modules.csvimport.service.CsvImportService;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.entity.CourseEnrollmentEntity;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserCreateRequest;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import project.lms_rikkei_edu.modules.user.service.UserService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class CsvImportServiceImpl implements CsvImportService {

    private static final long CSV_PREVIEW_TTL_MINUTES = 30;

    private static final String STATUS_VALID = "VALID";
    private static final String STATUS_EXISTING_USER = "EXISTING_USER";
    private static final String STATUS_NAME_MISMATCH = "NAME_MISMATCH";
    private static final String STATUS_DUPLICATE_IN_FILE = "DUPLICATE_IN_FILE";
    private static final String STATUS_DUPLICATE_IN_DB = "DUPLICATE_IN_DB";
    private static final String STATUS_FORMAT_ERROR = "FORMAT_ERROR";
    private static final String STATUS_ALREADY_ENROLLED = "ALREADY_ENROLLED";
    private static final String STATUS_IMPORTED = "IMPORTED";
    private static final String STATUS_IMPORT_FAILED = "IMPORT_FAILED";

    private final UserService userService;
    private final RedisService redisService;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseRepository courseRepository;
    private final EmailAsyncService emailAsyncService;

    @Value("${app.csv-import.max-rows:500}")
    private int maxCsvRows;

    @Override
    public CsvImportPreviewResponse preview(MultipartFile file, String defaultRole,
                                             UUID courseId) {
        validateCsvFile(file);
        parseRole(defaultRole);
        List<String[]> rows = parseCsv(file);
        validateRowCount(rows);
        ColumnIndexes indexes = resolveColumnIndexes(rows.getFirst());
        PreviewResult previewResult = processAllRows(rows, indexes, defaultRole, courseId);
        String token = savePreviewToRedis(previewResult, defaultRole, courseId);
        return buildPreviewResponse(token, rows.size() - 1, previewResult);
    }

    @Override
    @Transactional
    public CsvImportConfirmResponse confirm(String token, UUID adminId,
                                             UUID courseId) {
        RedisPreviewData previewData = loadPreviewData(token);
        redisService.delete(RedisKeyConstants.CSV_IMPORT_PREVIEW + token);

        List<CsvImportRowResult> results = new ArrayList<>();
        List<AdminUserCreateRequest> batchRequests = new ArrayList<>();
        List<String> newUserEmails = new ArrayList<>();
        List<String> existingUserEmails = new ArrayList<>();

        processConfirmRows(previewData, results, batchRequests, newUserEmails, existingUserEmails);

        String courseTitle = null;
        if (courseId != null) {
            courseTitle = courseRepository.findById(courseId)
                    .map(Course::getTitle)
                    .orElse(null);
        }

        int successCount = 0;
        int failCount = 0;

        if (!batchRequests.isEmpty()) {
            try {
                userService.batchCreateUsers(adminId, batchRequests, courseTitle);
                successCount = batchRequests.size();
            } catch (Exception e) {
                failCount = batchRequests.size();
                handleBatchCreateFailure(results, e);
            }
        }

        if (courseId != null) {
            List<String> allEnrollEmails = new ArrayList<>();
            allEnrollEmails.addAll(newUserEmails);
            allEnrollEmails.addAll(existingUserEmails);
            if (!allEnrollEmails.isEmpty()) {
                enrollUsers(courseId, allEnrollEmails);
            }
        }

        if (courseTitle != null && !existingUserEmails.isEmpty()) {
            sendEnrollmentEmails(courseTitle, existingUserEmails);
        }

        return CsvImportConfirmResponse.builder()
                .totalProcessed(results.size())
                .successCount(successCount)
                .failCount(failCount)
                .results(results)
                .build();
    }

    private void processConfirmRows(RedisPreviewData previewData, List<CsvImportRowResult> results,
                                     List<AdminUserCreateRequest> batchRequests,
                                     List<String> newUserEmails, List<String> existingUserEmails) {
        for (CsvImportRowResult row : previewData.getRows()) {
            switch (row.getStatus()) {
                case STATUS_VALID -> {
                    AdminUserCreateRequest createRequest = new AdminUserCreateRequest();
                    createRequest.setFullName(row.getFullName());
                    createRequest.setEmail(row.getEmail());
                    createRequest.setRole(previewData.getDefaultRole());
                    createRequest.setPhoneNumber(row.getPhoneNumber().isEmpty() ? null : row.getPhoneNumber());
                    batchRequests.add(createRequest);
                    newUserEmails.add(row.getEmail());
                    results.add(buildImportedRowResult(row));
                }
                case STATUS_EXISTING_USER -> {
                    existingUserEmails.add(row.getEmail());
                    results.add(buildImportedRowResult(row));
                }
                case STATUS_NAME_MISMATCH -> {
                    existingUserEmails.add(row.getEmail());
                    results.add(buildImportedRowResult(row));
                }
                default -> results.add(row);
            }
        }
    }

    private void handleBatchCreateFailure(List<CsvImportRowResult> results, Exception e) {
        for (CsvImportRowResult row : results) {
            if (STATUS_IMPORTED.equals(row.getStatus())) {
                row.setStatus(STATUS_IMPORT_FAILED);
                row.setErrors(List.of(e.getMessage() != null ? e.getMessage() : "Lỗi không xác định"));
            }
        }
    }

    private void sendEnrollmentEmails(String courseTitle, List<String> existingUserEmails) {
        List<UserEntity> existingUsers = userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(existingUserEmails);
        for (UserEntity user : existingUsers) {
            emailAsyncService.sendEnrolledToCourseMailAsync(user.getEmail(), user.getFullName(), courseTitle);
        }
    }

    // ── Preview helpers ──────────────────────────────────────────────────────

    private void validateCsvFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new BusinessException("File không đúng định dạng CSV. Vui lòng chọn file có đuôi .csv");
        }
        if (file.isEmpty()) {
            throw new BusinessException("File CSV không có dữ liệu");
        }
    }

    private void parseRole(String defaultRole) {
        try {
            UserRole.valueOf(defaultRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Vai trò không hợp lệ: " + defaultRole);
        }
    }

    private void validateRowCount(List<String[]> rows) {
        if (rows.size() < 2) {
            throw new BusinessException("File CSV phải có ít nhất header + 1 dòng dữ liệu");
        }
        if (rows.size() - 1 > maxCsvRows) {
            throw new BusinessException("File CSV vượt quá số dòng tối đa cho phép (" + maxCsvRows + " dòng)");
        }
    }

    private ColumnIndexes resolveColumnIndexes(String[] headers) {
        int nameIdx = findHeaderIndex(headers, "fullname", "ho ten", "ho ten", "name", "ten");
        int emailIdx = findHeaderIndex(headers, "email");
        int phoneIdx = findHeaderIndex(headers, "phone", "phonenumber", "phone_number", "sdt", "so dien thoai", "so dien thoai");

        if (nameIdx == -1 || emailIdx == -1) {
            throw new BusinessException("Thiếu cột bắt buộc: fullname, email");
        }

        return new ColumnIndexes(nameIdx, emailIdx, phoneIdx);
    }

    private PreviewResult processAllRows(List<String[]> rawRows, ColumnIndexes indexes, String defaultRole,
                                          UUID courseId) {
        List<CsvImportRowResult> results = new ArrayList<>();
        Set<String> emailsInFile = new HashSet<>();
        Set<String> phonesInFile = new HashSet<>();
        int validCount = 0;
        int existingUserCount = 0;
        int formatErrorCount = 0;
        int duplicateInFileCount = 0;
        int duplicateInDbCount = 0;
        int alreadyEnrolledCount = 0;
        int nameMismatchCount = 0;

        for (int i = 1; i < rawRows.size(); i++) {
            CsvRowData rowData = extractRowData(rawRows.get(i), i, indexes);
            CsvImportRowResult result = validateRow(rowData, defaultRole, emailsInFile, phonesInFile,
                    rawRows, courseId);
            results.add(result);

            switch (result.getStatus()) {
                case STATUS_VALID -> validCount++;
                case STATUS_EXISTING_USER -> existingUserCount++;
                case STATUS_FORMAT_ERROR -> formatErrorCount++;
                case STATUS_DUPLICATE_IN_FILE -> duplicateInFileCount++;
                case STATUS_DUPLICATE_IN_DB -> duplicateInDbCount++;
                case STATUS_ALREADY_ENROLLED -> alreadyEnrolledCount++;
                case STATUS_NAME_MISMATCH -> nameMismatchCount++;
                default -> {
                    // unknown status already tracked in results, no counting needed
                }
            }
        }

        return new PreviewResult(results, validCount, existingUserCount, formatErrorCount,
                duplicateInFileCount, duplicateInDbCount, alreadyEnrolledCount, nameMismatchCount);
    }

    private CsvRowData extractRowData(String[] cols, int rowIndex, ColumnIndexes indexes) {
        String fullName = indexes.nameIdx < cols.length ? cols[indexes.nameIdx].trim() : "";
        String email = indexes.emailIdx < cols.length ? cols[indexes.emailIdx].trim().toLowerCase() : "";
        String phone = indexes.phoneIdx >= 0 && indexes.phoneIdx < cols.length ? cols[indexes.phoneIdx].trim() : "";

        fullName = cleanQuotes(fullName);
        email = cleanQuotes(email);
        phone = cleanQuotes(phone);

        if (!phone.isEmpty()) {
            String phoneClean = phone.replaceAll("[\\s\\-.)(]", "");
            if (phoneClean.startsWith("+84")) {
                phoneClean = "0" + phoneClean.substring(3);
            }
            phone = phoneClean;
        }

        return new CsvRowData(rowIndex + 1, fullName, email, phone);
    }

    private CsvImportRowResult validateRow(CsvRowData rowData, String defaultRole,
                                            Set<String> emailsInFile, Set<String> phonesInFile,
                                            List<String[]> rawRows, UUID courseId) {
        AdminUserCreateRequest createRequest = buildCreateRequest(rowData, defaultRole);

        List<String> errors = validateRequest(createRequest);
        if (!errors.isEmpty()) {
            return buildRowResult(rowData.rowNumber, rowData.fullName, rowData.email, rowData.phone, STATUS_FORMAT_ERROR, errors);
        }

        if (emailsInFile.contains(rowData.email)) {
            errors.add("Email bị trùng trong file (dòng " + (findRowByEmail(rawRows, rowData.email, rowData.rowNumber - 1) + 1) + ")");
            return buildRowResult(rowData.rowNumber, rowData.fullName, rowData.email, rowData.phone, STATUS_DUPLICATE_IN_FILE, errors);
        }
        emailsInFile.add(rowData.email);

        if (!rowData.phone.isEmpty()) {
            if (phonesInFile.contains(rowData.phone)) {
                errors.add("Số điện thoại bị trùng trong file");
                return buildRowResult(rowData.rowNumber, rowData.fullName, rowData.email, rowData.phone, STATUS_DUPLICATE_IN_FILE, errors);
            }
            phonesInFile.add(rowData.phone);
        }

        CsvImportRowResult existingResult = checkExistingUserInDb(rowData, courseId, errors);
        if (existingResult != null) return existingResult;

        if (!rowData.phone.isEmpty() && userService.existsByPhoneNumber(rowData.phone)) {
            errors.add("Số điện thoại đã tồn tại trong hệ thống");
            return buildRowResult(rowData.rowNumber, rowData.fullName, rowData.email, rowData.phone, STATUS_DUPLICATE_IN_DB, errors);
        }

        return buildRowResult(rowData.rowNumber, rowData.fullName, rowData.email, rowData.phone, STATUS_VALID, errors);
    }

    private CsvImportRowResult checkExistingUserInDb(CsvRowData rowData, UUID courseId, List<String> errors) {
        if (!userService.existsByEmail(rowData.email)) return null;
        if (courseId != null) {
            UserEntity existingUser = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(rowData.email).orElse(null);
            if (existingUser != null) {
                if (courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, existingUser.getId())) {
                    errors.add("Email [" + rowData.email + "] đã tham gia khoá học này");
                    return buildRowResult(rowData.rowNumber, rowData.fullName, rowData.email, rowData.phone,
                            STATUS_ALREADY_ENROLLED, errors);
                }
                if (!rowData.fullName.equalsIgnoreCase(existingUser.getFullName())) {
                    errors.add("Tên trong file (" + rowData.fullName + ") khác với tên thật ("
                            + existingUser.getFullName() + ")");
                    return buildRowResult(rowData.rowNumber, rowData.fullName, rowData.email, rowData.phone,
                            STATUS_NAME_MISMATCH, errors);
                }
            }
            return buildRowResult(rowData.rowNumber, rowData.fullName, rowData.email, rowData.phone,
                    STATUS_EXISTING_USER, List.of("Email đã tồn tại, sẽ thêm vào khoá học"));
        }
        errors.add("Email đã tồn tại trong hệ thống");
        return buildRowResult(rowData.rowNumber, rowData.fullName, rowData.email, rowData.phone, STATUS_DUPLICATE_IN_DB, errors);
    }

    private static AdminUserCreateRequest buildCreateRequest(CsvRowData rowData, String defaultRole) {
        AdminUserCreateRequest createRequest = new AdminUserCreateRequest();
        createRequest.setFullName(rowData.fullName);
        createRequest.setEmail(rowData.email);
        createRequest.setRole(defaultRole);
        createRequest.setPhoneNumber(rowData.phone.isEmpty() ? null : rowData.phone);
        return createRequest;
    }

    private List<String> validateRequest(AdminUserCreateRequest request) {
        Set<ConstraintViolation<AdminUserCreateRequest>> violations = validator.validate(request);
        return new ArrayList<>(violations.stream()
                .map(ConstraintViolation::getMessage)
                .toList());
    }

    private String savePreviewToRedis(PreviewResult previewResult, String role,
                                       UUID courseId) {
        String token = UUID.randomUUID().toString();
        String redisKey = RedisKeyConstants.CSV_IMPORT_PREVIEW + token;
        try {
            String json = objectMapper.writeValueAsString(
                    new RedisPreviewData(previewResult.results, role, courseId));
            redisService.set(redisKey, json, CSV_PREVIEW_TTL_MINUTES * 60);
        } catch (Exception e) {
            throw new BusinessException("Không thể lưu dữ liệu xem trước, vui lòng thử lại");
        }
        return token;
    }

    private CsvImportPreviewResponse buildPreviewResponse(String token, int totalRows, PreviewResult result) {
        return CsvImportPreviewResponse.builder()
                .token(token)
                .totalRows(totalRows)
                .validCount(result.validCount)
                .existingUserCount(result.existingUserCount)
                .formatErrorCount(result.formatErrorCount)
                .duplicateInFileCount(result.duplicateInFileCount)
                .duplicateInDbCount(result.duplicateInDbCount)
                .alreadyEnrolledCount(result.alreadyEnrolledCount)
                .nameMismatchCount(result.nameMismatchCount)
                .rows(result.results)
                .build();
    }

    // ── Confirm helpers ──────────────────────────────────────────────────────

    private RedisPreviewData loadPreviewData(String token) {
        String redisKey = RedisKeyConstants.CSV_IMPORT_PREVIEW + token;
        String json = redisService.get(redisKey).map(Object::toString).orElse(null);
        if (json == null) {
            throw new BusinessException("Phiên làm việc đã hết hạn hoặc không hợp lệ. Vui lòng tải lại file CSV");
        }
        try {
            return objectMapper.readValue(json, RedisPreviewData.class);
        } catch (Exception e) {
            throw new BusinessException("Dữ liệu xem trước không hợp lệ, vui lòng thử lại");
        }
    }

    private void enrollUsers(UUID courseId, List<String> emails) {
        List<UserEntity> users = userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(emails);

        List<CourseEnrollmentEntity> enrollments = new ArrayList<>();
        for (UserEntity user : users) {
            if (!courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, user.getId())) {
                CourseEnrollmentEntity enrollment = new CourseEnrollmentEntity();
                enrollment.setId(UUID.randomUUID());
                enrollment.setCourseId(courseId);
                enrollment.setStudentId(user.getId());
                enrollments.add(enrollment);
            }
        }

        if (!enrollments.isEmpty()) {
            courseEnrollmentRepository.saveAll(enrollments);
        }
    }

    private CsvImportRowResult buildImportedRowResult(CsvImportRowResult row) {
        return CsvImportRowResult.builder()
                .rowNumber(row.getRowNumber())
                .fullName(row.getFullName())
                .email(row.getEmail())
                .phoneNumber(row.getPhoneNumber())
                .status(STATUS_IMPORTED)
                .errors(List.of())
                .build();
    }

    // ── CSV parsing ──────────────────────────────────────────────────────────

    private List<String[]> parseCsv(MultipartFile file) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                rows.add(parseCsvLine(line));
            }
        } catch (Exception e) {
            throw new BusinessException("Không thể đọc file CSV: " + e.getMessage());
        }
        return rows;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
            i++;
        }
        fields.add(current.toString().trim());

        return fields.toArray(new String[0]);
    }

    private int findHeaderIndex(String[] headers, String... aliases) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase().replaceAll("\\s+", "");
            for (String alias : aliases) {
                if (h.equals(alias)) return i;
            }
        }
        return -1;
    }

    private String cleanQuotes(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private int findRowByEmail(List<String[]> rawRows, String email, int currentIndex) {
        for (int i = 1; i < currentIndex; i++) {
            String[] cols = rawRows.get(i);
            for (String col : cols) {
                if (col.trim().toLowerCase().contains(email)) return i;
            }
        }
        return -1;
    }

    private CsvImportRowResult buildRowResult(int rowNumber, String fullName, String email,
                                               String phone, String status, List<String> errors) {
        return CsvImportRowResult.builder()
                .rowNumber(rowNumber)
                .fullName(fullName)
                .email(email)
                .phoneNumber(phone)
                .status(status)
                .errors(errors)
                .build();
    }

    // ── Inner records ────────────────────────────────────────────────────────

    private record ColumnIndexes(int nameIdx, int emailIdx, int phoneIdx) {}

    private record CsvRowData(int rowNumber, String fullName, String email, String phone) {}

    private record PreviewResult(List<CsvImportRowResult> results, int validCount,
                                  int existingUserCount, int formatErrorCount,
                                  int duplicateInFileCount, int duplicateInDbCount,
                                  int alreadyEnrolledCount, int nameMismatchCount) {}

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class RedisPreviewData {
        private List<CsvImportRowResult> rows;
        private String defaultRole;
        private UUID courseId;
    }
}
