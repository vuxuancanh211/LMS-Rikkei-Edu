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
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportRowResult;
import project.lms_rikkei_edu.modules.csvimport.service.CsvImportService;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserCreateRequest;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
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

    private final UserService userService;
    private final RedisService redisService;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.csv-import.max-rows:500}")
    private int maxCsvRows;

    @Override
    public CsvImportPreviewResponse preview(MultipartFile file, String defaultRole) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new BusinessException("File không đúng định dạng CSV. Vui lòng chọn file có đuôi .csv");
        }

        if (file.isEmpty()) {
            throw new BusinessException("File CSV không có dữ liệu");
        }

        UserRole defaultUserRole;
        try {
            defaultUserRole = UserRole.valueOf(defaultRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Vai trò không hợp lệ: " + defaultRole);
        }

        List<String[]> rawRows = parseCsv(file);

        if (rawRows.size() < 2) {
            throw new BusinessException("File CSV phải có ít nhất header + 1 dòng dữ liệu");
        }

        if (rawRows.size() - 1 > maxCsvRows) {
            throw new BusinessException("File CSV vượt quá số dòng tối đa cho phép (" + maxCsvRows + " dòng)");
        }

        String[] headers = rawRows.get(0);
        int nameIdx = findHeaderIndex(headers, "fullname", "ho ten", "ho ten", "name", "ten");
        int emailIdx = findHeaderIndex(headers, "email");
        int phoneIdx = findHeaderIndex(headers, "phone", "phonenumber", "phone_number", "sdt", "so dien thoai", "so dien thoai");

        if (nameIdx == -1 || emailIdx == -1) {
            throw new BusinessException("Thiếu cột bắt buộc: fullname, email");
        }

        List<CsvImportRowResult> results = new ArrayList<>();
        Set<String> emailsInFile = new HashSet<>();
        Set<String> phonesInFile = new HashSet<>();
        int validCount = 0;
        int formatErrorCount = 0;
        int duplicateInFileCount = 0;
        int duplicateInDbCount = 0;

        for (int i = 1; i < rawRows.size(); i++) {
            String[] cols = rawRows.get(i);
            int rowNumber = i + 1;
            String fullName = nameIdx < cols.length ? cols[nameIdx].trim() : "";
            String email = emailIdx < cols.length ? cols[emailIdx].trim().toLowerCase() : "";
            String phone = phoneIdx >= 0 && phoneIdx < cols.length ? cols[phoneIdx].trim() : "";

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

            AdminUserCreateRequest createRequest = new AdminUserCreateRequest();
            createRequest.setFullName(fullName);
            createRequest.setEmail(email);
            createRequest.setRole(defaultRole);
            createRequest.setPhoneNumber(phone.isEmpty() ? null : phone);

            Set<ConstraintViolation<AdminUserCreateRequest>> violations = validator.validate(createRequest);
            List<String> errors = new ArrayList<>(violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .toList());

            if (!errors.isEmpty()) {
                formatErrorCount++;
                results.add(buildRowResult(rowNumber, fullName, email, phone, "FORMAT_ERROR", errors));
                continue;
            }

            if (emailsInFile.contains(email)) {
                duplicateInFileCount++;
                errors.add("Email bị trùng trong file (dòng " + (findRowByEmail(rawRows, email, i) + 1) + ")");
                results.add(buildRowResult(rowNumber, fullName, email, phone, "DUPLICATE_IN_FILE", errors));
                continue;
            }
            emailsInFile.add(email);

            if (!phone.isEmpty()) {
                if (phonesInFile.contains(phone)) {
                    duplicateInFileCount++;
                    errors.add("Số điện thoại bị trùng trong file");
                    results.add(buildRowResult(rowNumber, fullName, email, phone, "DUPLICATE_IN_FILE", errors));
                    continue;
                }
                phonesInFile.add(phone);
            }

            if (userService.existsByEmail(email)) {
                duplicateInDbCount++;
                errors.add("Email đã tồn tại trong hệ thống");
                results.add(buildRowResult(rowNumber, fullName, email, phone, "DUPLICATE_IN_DB", errors));
                continue;
            }

            if (!phone.isEmpty() && userService.existsByPhoneNumber(phone)) {
                duplicateInDbCount++;
                errors.add("Số điện thoại đã tồn tại trong hệ thống");
                results.add(buildRowResult(rowNumber, fullName, email, phone, "DUPLICATE_IN_DB", errors));
                continue;
            }

            validCount++;
            results.add(buildRowResult(rowNumber, fullName, email, phone, "VALID", errors));
        }

        String token = UUID.randomUUID().toString();
        String redisKey = RedisKeyConstants.CSV_IMPORT_PREVIEW + token;

        try {
            String json = objectMapper.writeValueAsString(new RedisPreviewData(results, defaultUserRole.name()));
            redisService.set(redisKey, json, CSV_PREVIEW_TTL_MINUTES * 60);
        } catch (Exception e) {
            throw new BusinessException("Không thể lưu dữ liệu xem trước, vui lòng thử lại");
        }

        return CsvImportPreviewResponse.builder()
                .token(token)
                .totalRows(rawRows.size() - 1)
                .validCount(validCount)
                .formatErrorCount(formatErrorCount)
                .duplicateInFileCount(duplicateInFileCount)
                .duplicateInDbCount(duplicateInDbCount)
                .rows(results)
                .build();
    }

    @Override
    @Transactional
    public CsvImportConfirmResponse confirm(String token, UUID adminId) {
        String redisKey = RedisKeyConstants.CSV_IMPORT_PREVIEW + token;
        String json = redisService.get(redisKey).map(Object::toString).orElse(null);

        if (json == null) {
            throw new BusinessException("Phiên làm việc đã hết hạn hoặc không hợp lệ. Vui lòng tải lại file CSV");
        }

        RedisPreviewData previewData;
        try {
            previewData = objectMapper.readValue(json, RedisPreviewData.class);
        } catch (Exception e) {
            throw new BusinessException("Dữ liệu xem trước không hợp lệ, vui lòng thử lại");
        }

        redisService.delete(redisKey);

        List<CsvImportRowResult> results = new ArrayList<>();
        List<AdminUserCreateRequest> batchRequests = new ArrayList<>();
        List<CsvImportRowResult> validRows = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (CsvImportRowResult row : previewData.getRows()) {
            if (!"VALID".equals(row.getStatus())) {
                results.add(row);
                continue;
            }

            AdminUserCreateRequest createRequest = new AdminUserCreateRequest();
            createRequest.setFullName(row.getFullName());
            createRequest.setEmail(row.getEmail());
            createRequest.setRole(previewData.getDefaultRole());
            createRequest.setPhoneNumber(row.getPhoneNumber().isEmpty() ? null : row.getPhoneNumber());

            batchRequests.add(createRequest);
            validRows.add(row);
        }

        if (!batchRequests.isEmpty()) {
            try {
                userService.batchCreateUsers(adminId, batchRequests);

                for (CsvImportRowResult row : validRows) {
                    successCount++;
                    results.add(CsvImportRowResult.builder()
                            .rowNumber(row.getRowNumber())
                            .fullName(row.getFullName())
                            .email(row.getEmail())
                            .phoneNumber(row.getPhoneNumber())
                            .status("IMPORTED")
                            .errors(List.of())
                            .build());
                }
            } catch (Exception e) {
                failCount = batchRequests.size();
                for (CsvImportRowResult row : validRows) {
                    results.add(CsvImportRowResult.builder()
                            .rowNumber(row.getRowNumber())
                            .fullName(row.getFullName())
                            .email(row.getEmail())
                            .phoneNumber(row.getPhoneNumber())
                            .status("IMPORT_FAILED")
                            .errors(List.of(e.getMessage() != null ? e.getMessage() : "Lỗi không xác định"))
                            .build());
                }
            }
        }

        return CsvImportConfirmResponse.builder()
                .totalProcessed(successCount + failCount)
                .successCount(successCount)
                .failCount(failCount)
                .results(results)
                .build();
    }

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

        for (int i = 0; i < line.length(); i++) {
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

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class RedisPreviewData {
        private List<CsvImportRowResult> rows;
        private String defaultRole;
    }
}
