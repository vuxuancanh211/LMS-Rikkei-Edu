package project.lms_rikkei_edu.modules.csvimport.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportRowResult;
import project.lms_rikkei_edu.modules.csvimport.service.GroupMemberCsvImportService;
import project.lms_rikkei_edu.modules.group.dto.request.AddGroupMembersRequest;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import project.lms_rikkei_edu.modules.group.repository.StudyGroupRepository;
import project.lms_rikkei_edu.modules.group.service.GroupService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GroupMemberCsvImportServiceImpl implements GroupMemberCsvImportService {

    private static final long PREVIEW_TTL_SECONDS = 30L * 60;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final RedisService redisService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.csv-import.max-rows:500}")
    private int maxCsvRows;

    @Override
    @Transactional(readOnly = true)
    public GroupMemberCsvImportPreviewResponse preview(UUID groupId, MultipartFile file) {
        UserPrincipal currentUser = requireCurrentUser();
        StudyGroupEntity group = findGroupForInstructor(groupId, currentUser.getId());
        validateCsvFile(file);

        List<String[]> rows = parseCsv(file);
        EmailColumn emailColumn = resolveEmailColumn(rows);
        int totalRows = rows.size() - emailColumn.startIndex;
        if (totalRows <= 0) {
            throw new BusinessException("File CSV không có email học viên");
        }
        if (totalRows > maxCsvRows) {
            throw new BusinessException("File CSV vượt quá số dòng tối đa cho phép (" + maxCsvRows + " dòng)");
        }

        PreviewResult result = buildPreview(group, groupId, rows, emailColumn);
        String token = savePreview(groupId, result.validEmails, result.rows);
        return GroupMemberCsvImportPreviewResponse.builder()
                .token(token)
                .totalRows(totalRows)
                .validCount(result.validCount)
                .formatErrorCount(result.formatErrorCount)
                .duplicateInFileCount(result.duplicateInFileCount)
                .notFoundCount(result.notFoundCount)
                .alreadyInGroupCount(result.alreadyInGroupCount)
                .capacityExceededCount(result.capacityExceededCount)
                .rows(result.rows)
                .build();
    }

    @Override
    @Transactional
    public GroupMemberCsvImportConfirmResponse confirm(UUID groupId, String token) {
        PreviewData previewData = loadPreview(token);
        if (!previewData.groupId.equals(groupId)) {
            throw new BusinessException("CSV preview token does not match this group", HttpStatus.BAD_REQUEST);
        }
        redisService.delete(RedisKeyConstants.GROUP_MEMBER_CSV_IMPORT_PREVIEW + token);

        List<GroupMemberCsvImportRowResult> results = new ArrayList<>();
        if (previewData.validEmails.isEmpty()) {
            return GroupMemberCsvImportConfirmResponse.builder()
                    .totalProcessed(0)
                    .successCount(0)
                    .failCount(0)
                    .results(results)
                    .build();
        }

        try {
            AddGroupMembersRequest request = new AddGroupMembersRequest();
            request.setEmails(previewData.validEmails);
            groupService.addMembers(groupId, request);
            previewData.rows.stream()
                    .filter(r -> "VALID".equals(r.getStatus()))
                    .map(r -> row(r.getRowNumber(), r.getEmail(), "IMPORTED", List.of()))
                    .forEach(results::add);
            return GroupMemberCsvImportConfirmResponse.builder()
                    .totalProcessed(results.size())
                    .successCount(results.size())
                    .failCount(0)
                    .results(results)
                    .build();
        } catch (Exception e) {
            previewData.rows.stream()
                    .filter(r -> "VALID".equals(r.getStatus()))
                    .map(r -> row(r.getRowNumber(), r.getEmail(), "IMPORT_FAILED", List.of(e.getMessage())))
                    .forEach(results::add);
            return GroupMemberCsvImportConfirmResponse.builder()
                    .totalProcessed(results.size())
                    .successCount(0)
                    .failCount(results.size())
                    .results(results)
                    .build();
        }
    }

    private PreviewResult buildPreview(StudyGroupEntity group, UUID groupId, List<String[]> rows, EmailColumn emailColumn) {
        List<GroupMemberCsvImportRowResult> results = new ArrayList<>();
        List<String> validEmails = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        List<EmailRow> syntacticallyValidRows = new ArrayList<>();
        int formatErrorCount = 0;
        int duplicateInFileCount = 0;

        for (int i = emailColumn.startIndex; i < rows.size(); i++) {
            String email = emailAt(rows.get(i), emailColumn.index);
            int rowNumber = i + 1;
            if (email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
                formatErrorCount++;
                results.add(row(rowNumber, email, "FORMAT_ERROR", List.of("Email không hợp lệ")));
                continue;
            }
            String normalized = email.toLowerCase(Locale.ROOT);
            if (!seenEmails.add(normalized)) {
                duplicateInFileCount++;
                results.add(row(rowNumber, email, "DUPLICATE_IN_FILE", List.of("Email bị trùng trong file")));
                continue;
            }
            syntacticallyValidRows.add(new EmailRow(rowNumber, normalized));
        }

        Map<String, UserEntity> studentsByEmail = findStudentsByEmail(syntacticallyValidRows);
        List<UUID> studentIds = studentsByEmail.values().stream().map(UserEntity::getId).toList();
        Set<UUID> existingStudentIds = studentIds.isEmpty()
                ? Set.of()
                : new HashSet<>(groupMemberRepository.findExistingStudentIds(groupId, studentIds));
        long remaining = group.getMaxCapacity() == null
                ? Long.MAX_VALUE
                : Math.max(0, group.getMaxCapacity() - groupMemberRepository.countByGroupId(groupId));

        int validCount = 0;
        int notFoundCount = 0;
        int alreadyInGroupCount = 0;
        int capacityExceededCount = 0;
        for (EmailRow emailRow : syntacticallyValidRows) {
            UserEntity student = studentsByEmail.get(emailRow.email);
            if (student == null) {
                notFoundCount++;
                results.add(row(emailRow.rowNumber, emailRow.email, "NOT_FOUND", List.of("Không tìm thấy học viên với email này")));
                continue;
            }
            if (existingStudentIds.contains(student.getId())) {
                alreadyInGroupCount++;
                results.add(row(emailRow.rowNumber, emailRow.email, "ALREADY_IN_GROUP", List.of("Học viên đã có trong nhóm")));
                continue;
            }
            if (validCount >= remaining) {
                capacityExceededCount++;
                results.add(row(emailRow.rowNumber, emailRow.email, "CAPACITY_EXCEEDED", List.of("Vượt quá sức chứa còn lại của nhóm")));
                continue;
            }
            validCount++;
            validEmails.add(emailRow.email);
            results.add(row(emailRow.rowNumber, emailRow.email, "VALID", List.of()));
        }

        results.sort(Comparator.comparingInt(GroupMemberCsvImportRowResult::getRowNumber));
        return new PreviewResult(results, validEmails, validCount, formatErrorCount, duplicateInFileCount,
                notFoundCount, alreadyInGroupCount, capacityExceededCount);
    }

    private Map<String, UserEntity> findStudentsByEmail(List<EmailRow> rows) {
        List<String> emails = rows.stream().map(EmailRow::email).toList();
        if (emails.isEmpty()) return Map.of();
        Map<String, UserEntity> result = new HashMap<>();
        for (UserEntity user : userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(emails)) {
            if (user.getRole() == UserRole.STUDENT && user.getStatus() != UserStatus.DELETED) {
                result.put(user.getEmail().toLowerCase(Locale.ROOT), user);
            }
        }
        return result;
    }

    private void validateCsvFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BusinessException("File không đúng định dạng CSV. Vui lòng chọn file có đuôi .csv");
        }
        if (file.isEmpty()) {
            throw new BusinessException("File CSV không có dữ liệu");
        }
    }

    private List<String[]> parseCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) rows.add(splitCsvLine(line));
            }
            if (rows.isEmpty()) throw new BusinessException("File CSV không có dữ liệu");
            return rows;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Không thể đọc file CSV");
        }
    }

    private String[] splitCsvLine(String line) {
        List<String> cols = new ArrayList<>();
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
                cols.add(clean(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cols.add(clean(current.toString()));
        return cols.toArray(String[]::new);
    }

    private EmailColumn resolveEmailColumn(List<String[]> rows) {
        String[] first = rows.getFirst();
        for (int i = 0; i < first.length; i++) {
            if ("email".equals(clean(first[i]).toLowerCase(Locale.ROOT))) {
                return new EmailColumn(i, 1);
            }
        }
        return new EmailColumn(0, 0);
    }

    private String emailAt(String[] row, int index) {
        if (index >= row.length) return "";
        return clean(row[index]).toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.startsWith("\uFEFF")) cleaned = cleaned.substring(1);
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }

    private String savePreview(UUID groupId, List<String> validEmails, List<GroupMemberCsvImportRowResult> rows) {
        String token = UUID.randomUUID().toString();
        try {
            redisService.set(
                    RedisKeyConstants.GROUP_MEMBER_CSV_IMPORT_PREVIEW + token,
                    objectMapper.writeValueAsString(new PreviewData(groupId, validEmails, rows)),
                    PREVIEW_TTL_SECONDS);
            return token;
        } catch (Exception e) {
            throw new BusinessException("Không thể lưu dữ liệu xem trước, vui lòng thử lại");
        }
    }

    private PreviewData loadPreview(String token) {
        Object raw = redisService.get(RedisKeyConstants.GROUP_MEMBER_CSV_IMPORT_PREVIEW + token)
                .orElseThrow(() -> new BusinessException("CSV preview token is invalid or expired", HttpStatus.NOT_FOUND));
        try {
            return objectMapper.readValue(raw.toString(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException("CSV preview data is invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private StudyGroupEntity findGroupForInstructor(UUID groupId, UUID instructorId) {
        return studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)
                .orElseThrow(() -> new BusinessException("Group not found", HttpStatus.NOT_FOUND));
    }

    private UserPrincipal requireCurrentUser() {
        return currentUserProvider.getCurrentUser()
                .orElseThrow(() -> new BusinessException("User is not authenticated", HttpStatus.UNAUTHORIZED));
    }

    private GroupMemberCsvImportRowResult row(int rowNumber, String email, String status, List<String> errors) {
        return GroupMemberCsvImportRowResult.builder()
                .rowNumber(rowNumber)
                .email(email)
                .status(status)
                .errors(errors)
                .build();
    }

    private record EmailColumn(int index, int startIndex) {}
    private record EmailRow(int rowNumber, String email) {}
    private record PreviewResult(
            List<GroupMemberCsvImportRowResult> rows,
            List<String> validEmails,
            int validCount,
            int formatErrorCount,
            int duplicateInFileCount,
            int notFoundCount,
            int alreadyInGroupCount,
            int capacityExceededCount) {}
    private record PreviewData(UUID groupId, List<String> validEmails, List<GroupMemberCsvImportRowResult> rows) {}
}
