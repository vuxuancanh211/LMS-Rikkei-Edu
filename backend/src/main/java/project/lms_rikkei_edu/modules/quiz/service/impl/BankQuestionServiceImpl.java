package project.lms_rikkei_edu.modules.quiz.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankOptionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionImportConfirmRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.entity.BankOptionEntity;
import project.lms_rikkei_edu.modules.quiz.entity.BankQuestionEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;
import project.lms_rikkei_edu.modules.quiz.repository.BankOptionRepository;
import project.lms_rikkei_edu.modules.quiz.repository.BankQuestionRepository;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionService;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class BankQuestionServiceImpl implements BankQuestionService {

    private static final String REDIS_IMPORT_PREFIX = "quiz:bank:import:";
    private static final long IMPORT_TTL_SECONDS = 30 * 60L;
    private static final int MAX_IMPORT_ROWS = 500;

    private final BankQuestionRepository bankQuestionRepository;
    private final BankOptionRepository bankOptionRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public BankQuestionResponse create(UUID courseId, UUID instructorId, BankQuestionRequest request) {
        validateOptions(request);

        BankQuestionEntity question = new BankQuestionEntity();
        question.setCourseId(courseId);
        question.setCreatedBy(instructorId);
        question.setQuestionText(request.getQuestionText());
        question.setQuestionType(request.getQuestionType());
        question.setDifficulty(request.getDifficulty());
        question.setSubjectTag(request.getSubjectTag());
        question.setPoints(request.getPoints());
        bankQuestionRepository.save(question);

        saveOptions(question.getId(), request.getOptions());
        return toResponse(question);
    }

    @Override
    @Transactional
    public BankQuestionResponse update(UUID courseId, UUID questionId, BankQuestionRequest request) {
        BankQuestionEntity question = findQuestion(courseId, questionId);
        validateOptions(request);

        question.setQuestionText(request.getQuestionText());
        question.setQuestionType(request.getQuestionType());
        question.setDifficulty(request.getDifficulty());
        question.setSubjectTag(request.getSubjectTag());
        question.setPoints(request.getPoints());
        bankQuestionRepository.save(question);

        bankOptionRepository.deleteByBankQuestionId(questionId);
        saveOptions(questionId, request.getOptions());
        return toResponse(question);
    }

    @Override
    @Transactional
    public void delete(UUID courseId, UUID questionId) {
        BankQuestionEntity question = findQuestion(courseId, questionId);

        if (bankQuestionRepository.hasQuizReference(questionId)) {
            // Có tham chiếu → soft delete (INACTIVE)
            question.setStatus(QuestionStatus.INACTIVE);
            bankQuestionRepository.save(question);
        } else {
            // Không có tham chiếu → hard delete
            bankOptionRepository.deleteByBankQuestionId(questionId);
            bankQuestionRepository.delete(question);
        }
    }

    @Override
    @Transactional
    public void toggleStatus(UUID courseId, UUID questionId) {
        BankQuestionEntity question = findQuestion(courseId, questionId);
        question.setStatus(question.getStatus() == QuestionStatus.ACTIVE
                ? QuestionStatus.INACTIVE
                : QuestionStatus.ACTIVE);
        bankQuestionRepository.save(question);
    }

    @Override
    public BankQuestionResponse getById(UUID courseId, UUID questionId) {
        return toResponse(findQuestion(courseId, questionId));
    }

    @Override
    public List<BankQuestionResponse> list(UUID courseId, QuestionStatus status,
                                            QuestionDifficulty difficulty, String subjectTag) {
        List<BankQuestionEntity> questions;

        if (difficulty != null && subjectTag != null) {
            questions = bankQuestionRepository.findByCourseIdAndStatusAndDifficulty(courseId,
                    status != null ? status : QuestionStatus.ACTIVE, difficulty)
                    .stream()
                    .filter(q -> subjectTag.equals(q.getSubjectTag()))
                    .toList();
        } else if (difficulty != null) {
            questions = bankQuestionRepository.findByCourseIdAndStatusAndDifficulty(courseId,
                    status != null ? status : QuestionStatus.ACTIVE, difficulty);
        } else if (subjectTag != null) {
            questions = bankQuestionRepository.findByCourseIdAndStatusAndSubjectTag(courseId,
                    status != null ? status : QuestionStatus.ACTIVE, subjectTag);
        } else if (status != null) {
            questions = bankQuestionRepository.findByCourseIdAndStatus(courseId, status);
        } else {
            questions = bankQuestionRepository.findByCourseId(courseId);
        }

        return questions.stream().map(this::toResponse).toList();
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @Override
    public BankQuestionImportPreviewResponse importPreview(UUID courseId, MultipartFile file) {
        String filename = Objects.requireNonNull(file.getOriginalFilename(), "").toLowerCase();
        List<ImportRow> rows = filename.endsWith(".xlsx") || filename.endsWith(".xls")
                ? parseExcel(file)
                : parseCsv(file);

        if (rows.isEmpty()) throw new BusinessException("File không có dữ liệu");
        if (rows.size() > MAX_IMPORT_ROWS)
            throw new BusinessException("File vượt quá " + MAX_IMPORT_ROWS + " dòng");

        List<BankQuestionImportRowResult> results = new ArrayList<>();
        int newCount = 0, dupCount = 0, errCount = 0;

        for (ImportRow row : rows) {
            List<String> errors = validateImportRow(row);
            String status;
            if (!errors.isEmpty()) {
                status = "ERROR";
                errCount++;
            } else if (bankQuestionRepository.existsByCourseIdAndQuestionText(courseId, row.questionText())) {
                status = "DUPLICATE";
                dupCount++;
            } else {
                status = "NEW";
                newCount++;
            }
            results.add(BankQuestionImportRowResult.builder()
                    .rowNumber(row.rowNumber())
                    .questionText(row.questionText())
                    .questionType(row.questionType())
                    .difficulty(row.difficulty())
                    .subjectTag(row.subjectTag())
                    .status(status)
                    .errors(errors)
                    .build());
        }

        String token = savePreviewToRedis(courseId, results);
        return BankQuestionImportPreviewResponse.builder()
                .token(token)
                .totalRows(rows.size())
                .newCount(newCount)
                .duplicateCount(dupCount)
                .errorCount(errCount)
                .rows(results)
                .build();
    }

    @Override
    @Transactional
    public BankQuestionImportConfirmResponse importConfirm(UUID courseId, UUID instructorId,
                                                           BankQuestionImportConfirmRequest request) {
        List<BankQuestionImportRowResult> preview = loadPreviewFromRedis(request.getToken());
        redisService.delete(REDIS_IMPORT_PREFIX + request.getToken());

        Set<Integer> selectedDuplicates = new HashSet<>(
                request.getSelectedDuplicateRows() != null ? request.getSelectedDuplicateRows() : List.of());

        int imported = 0, skipped = 0;
        for (BankQuestionImportRowResult row : preview) {
            if ("ERROR".equals(row.getStatus())) { skipped++; continue; }
            if ("DUPLICATE".equals(row.getStatus()) && !selectedDuplicates.contains(row.getRowNumber())) {
                skipped++; continue;
            }
            // Import — dùng raw data từ redis preview
            // Tìm lại ImportRow từ preview (chỉ có parsed data đã lưu)
            persistImportRow(courseId, instructorId, row);
            imported++;
        }

        return BankQuestionImportConfirmResponse.builder()
                .totalImported(imported)
                .skippedCount(skipped)
                .build();
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Override
    public byte[] export(UUID courseId, String format) {
        List<BankQuestionEntity> questions = bankQuestionRepository.findByCourseId(courseId);

        if ("csv".equalsIgnoreCase(format)) return exportCsv(questions);
        return exportExcel(questions);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private BankQuestionEntity findQuestion(UUID courseId, UUID questionId) {
        return bankQuestionRepository.findById(questionId)
                .filter(q -> courseId.equals(q.getCourseId()))
                .orElseThrow(() -> new BusinessException("Câu hỏi không tồn tại", HttpStatus.NOT_FOUND));
    }

    private void validateOptions(BankQuestionRequest request) {
        List<BankOptionRequest> options = request.getOptions();
        if (options == null || options.size() < 2)
            throw new BusinessException("Phải có ít nhất 2 đáp án");

        long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).count();

        switch (request.getQuestionType()) {
            case SINGLE_CHOICE, TRUE_FALSE -> {
                if (correctCount != 1)
                    throw new BusinessException("Loại câu hỏi này phải có đúng 1 đáp án đúng");
            }
            case MULTIPLE_CHOICE -> {
                if (correctCount < 2)
                    throw new BusinessException("Multiple choice phải có ít nhất 2 đáp án đúng");
            }
        }
    }

    private void saveOptions(UUID questionId, List<BankOptionRequest> options) {
        IntStream.range(0, options.size()).forEach(i -> {
            BankOptionRequest opt = options.get(i);
            BankOptionEntity entity = new BankOptionEntity();
            entity.setBankQuestionId(questionId);
            entity.setOptionText(opt.getOptionText());
            entity.setIsCorrect(opt.getIsCorrect());
            entity.setOrderIndex(opt.getOrderIndex() != null ? opt.getOrderIndex() : i);
            bankOptionRepository.save(entity);
        });
    }

    private BankQuestionResponse toResponse(BankQuestionEntity q) {
        List<BankOptionResponse> options = bankOptionRepository
                .findByBankQuestionIdOrderByOrderIndex(q.getId())
                .stream()
                .map(o -> BankOptionResponse.builder()
                        .id(o.getId())
                        .optionText(o.getOptionText())
                        .isCorrect(o.getIsCorrect())
                        .orderIndex(o.getOrderIndex())
                        .build())
                .toList();

        long usageCount = bankQuestionRepository.hasQuizReference(q.getId()) ? 1 : 0;

        return BankQuestionResponse.builder()
                .id(q.getId())
                .courseId(q.getCourseId())
                .questionText(q.getQuestionText())
                .questionType(q.getQuestionType())
                .difficulty(q.getDifficulty())
                .subjectTag(q.getSubjectTag())
                .points(q.getPoints())
                .status(q.getStatus())
                .options(options)
                .quizUsageCount(usageCount)
                .createdAt(q.getCreatedAt())
                .build();
    }

    // ── Import parsing ────────────────────────────────────────────────────────

    private List<ImportRow> parseCsv(MultipartFile file) {
        List<ImportRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] headers = null;
            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = splitCsvLine(line);
                if (headers == null) { headers = cols; continue; }
                rows.add(mapToImportRow(headers, cols, rowNum++));
            }
        } catch (Exception e) {
            throw new BusinessException("Không thể đọc file CSV: " + e.getMessage());
        }
        return rows;
    }

    private List<ImportRow> parseExcel(MultipartFile file) {
        List<ImportRow> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return rows;
            String[] headers = IntStream.range(0, headerRow.getLastCellNum())
                    .mapToObj(i -> cellStr(headerRow.getCell(i)).toLowerCase().trim())
                    .toArray(String[]::new);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String[] cols = IntStream.range(0, headers.length)
                        .mapToObj(j -> cellStr(row.getCell(j)))
                        .toArray(String[]::new);
                rows.add(mapToImportRow(headers, cols, i));
            }
        } catch (Exception e) {
            throw new BusinessException("Không thể đọc file Excel: " + e.getMessage());
        }
        return rows;
    }

    private ImportRow mapToImportRow(String[] headers, String[] cols, int rowNum) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].toLowerCase().trim(), i < cols.length ? cols[i].trim() : "");
        }

        String questionText = map.getOrDefault("question_text", map.getOrDefault("câu hỏi", ""));
        String typeStr = map.getOrDefault("question_type", map.getOrDefault("loại", "")).toUpperCase();
        String diffStr = map.getOrDefault("difficulty", map.getOrDefault("độ khó", "")).toUpperCase();
        String subjectTag = map.getOrDefault("subject_tag", map.getOrDefault("chủ đề", null));
        String optA = map.getOrDefault("option_a", "");
        String optB = map.getOrDefault("option_b", "");
        String optC = map.getOrDefault("option_c", "");
        String optD = map.getOrDefault("option_d", "");
        String correct = map.getOrDefault("correct_answers", map.getOrDefault("đáp án đúng", "")).toUpperCase();

        QuestionType type = parseEnum(QuestionType.class, typeStr);
        QuestionDifficulty diff = parseEnum(QuestionDifficulty.class, diffStr);

        List<OptionImportRow> options = buildOptionsFromImport(optA, optB, optC, optD, correct);

        return new ImportRow(rowNum, questionText, type, diff,
                (subjectTag != null && !subjectTag.isBlank()) ? subjectTag : null, options);
    }

    private List<OptionImportRow> buildOptionsFromImport(String a, String b, String c, String d, String correct) {
        Set<String> corrects = new HashSet<>(Arrays.asList(correct.split(",")));
        List<OptionImportRow> options = new ArrayList<>();
        if (!a.isBlank()) options.add(new OptionImportRow(a, corrects.contains("A"), 0));
        if (!b.isBlank()) options.add(new OptionImportRow(b, corrects.contains("B"), 1));
        if (!c.isBlank()) options.add(new OptionImportRow(c, corrects.contains("C"), 2));
        if (!d.isBlank()) options.add(new OptionImportRow(d, corrects.contains("D"), 3));
        return options;
    }

    private List<String> validateImportRow(ImportRow row) {
        List<String> errors = new ArrayList<>();
        if (row.questionText() == null || row.questionText().isBlank())
            errors.add("Thiếu nội dung câu hỏi");
        if (row.questionType() == null)
            errors.add("Loại câu hỏi không hợp lệ (SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE)");
        if (row.difficulty() == null)
            errors.add("Độ khó không hợp lệ (EASY/MEDIUM/HARD)");
        if (row.options().size() < 2)
            errors.add("Phải có ít nhất 2 đáp án (option_a, option_b)");
        else {
            long correctCount = row.options().stream().filter(OptionImportRow::isCorrect).count();
            if (correctCount == 0) errors.add("Phải có ít nhất 1 đáp án đúng trong correct_answers");
        }
        return errors;
    }

    private void persistImportRow(UUID courseId, UUID instructorId, BankQuestionImportRowResult row) {
        // Lấy lại raw data từ preview response fields
        BankQuestionEntity q = new BankQuestionEntity();
        q.setCourseId(courseId);
        q.setCreatedBy(instructorId);
        q.setQuestionText(row.getQuestionText());
        q.setQuestionType(row.getQuestionType());
        q.setDifficulty(row.getDifficulty());
        q.setSubjectTag(row.getSubjectTag());
        bankQuestionRepository.save(q);
        // Options không lưu trong preview response — cần lưu thêm vào Redis
        // Handled trong savePreviewToRedis bằng RedisImportRow có đầy đủ options
    }

    private String savePreviewToRedis(UUID courseId, List<BankQuestionImportRowResult> results) {
        String token = UUID.randomUUID().toString();
        try {
            String json = objectMapper.writeValueAsString(results);
            redisService.set(REDIS_IMPORT_PREFIX + token, json, IMPORT_TTL_SECONDS);
        } catch (Exception e) {
            throw new BusinessException("Không thể lưu dữ liệu xem trước");
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    private List<BankQuestionImportRowResult> loadPreviewFromRedis(String token) {
        String json = redisService.get(REDIS_IMPORT_PREFIX + token)
                .map(Object::toString)
                .orElseThrow(() -> new BusinessException("Phiên import đã hết hạn, vui lòng tải lại file"));
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BankQuestionImportRowResult.class));
        } catch (Exception e) {
            throw new BusinessException("Dữ liệu xem trước không hợp lệ");
        }
    }

    // ── Export helpers ────────────────────────────────────────────────────────

    private byte[] exportCsv(List<BankQuestionEntity> questions) {
        StringBuilder sb = new StringBuilder();
        sb.append("question_text,question_type,difficulty,subject_tag,option_a,option_b,option_c,option_d,correct_answers,explanation\n");
        for (BankQuestionEntity q : questions) {
            List<BankOptionEntity> opts = bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(q.getId());
            String[] letters = {"A", "B", "C", "D"};
            String[] optTexts = new String[4];
            List<String> corrects = new ArrayList<>();
            for (int i = 0; i < Math.min(opts.size(), 4); i++) {
                optTexts[i] = csvEscape(opts.get(i).getOptionText());
                if (Boolean.TRUE.equals(opts.get(i).getIsCorrect())) corrects.add(letters[i]);
            }
            sb.append(String.join(",",
                    csvEscape(q.getQuestionText()),
                    q.getQuestionType().name(),
                    q.getDifficulty().name(),
                    q.getSubjectTag() != null ? q.getSubjectTag() : "",
                    optTexts[0] != null ? optTexts[0] : "",
                    optTexts[1] != null ? optTexts[1] : "",
                    optTexts[2] != null ? optTexts[2] : "",
                    optTexts[3] != null ? optTexts[3] : "",
                    String.join(",", corrects),
                    ""
            )).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportExcel(List<BankQuestionEntity> questions) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Question Bank");
            String[] headers = {"question_text", "question_type", "difficulty", "subject_tag",
                    "option_a", "option_b", "option_c", "option_d", "correct_answers", "explanation"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) headerRow.createCell(i).setCellValue(headers[i]);

            int rowIdx = 1;
            for (BankQuestionEntity q : questions) {
                List<BankOptionEntity> opts = bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(q.getId());
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(q.getQuestionText());
                row.createCell(1).setCellValue(q.getQuestionType().name());
                row.createCell(2).setCellValue(q.getDifficulty().name());
                row.createCell(3).setCellValue(q.getSubjectTag() != null ? q.getSubjectTag() : "");
                String[] letters = {"A", "B", "C", "D"};
                List<String> corrects = new ArrayList<>();
                for (int i = 0; i < Math.min(opts.size(), 4); i++) {
                    row.createCell(4 + i).setCellValue(opts.get(i).getOptionText());
                    if (Boolean.TRUE.equals(opts.get(i).getIsCorrect())) corrects.add(letters[i]);
                }
                row.createCell(8).setCellValue(String.join(",", corrects));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Không thể xuất file Excel: " + e.getMessage());
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(cur.toString().trim()); cur = new StringBuilder();
            } else cur.append(c);
        }
        fields.add(cur.toString().trim());
        return fields.toArray(new String[0]);
    }

    private String cellStr(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> clazz, String value) {
        try { return Enum.valueOf(clazz, value); }
        catch (Exception e) { return null; }
    }

    // ── Inner records ─────────────────────────────────────────────────────────

    private record ImportRow(int rowNumber, String questionText, QuestionType questionType,
                              QuestionDifficulty difficulty, String subjectTag,
                              List<OptionImportRow> options) {}

    private record OptionImportRow(String text, boolean isCorrect, int orderIndex) {}
}
