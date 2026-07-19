package project.lms_rikkei_edu.modules.quiz.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;
import project.lms_rikkei_edu.modules.ai.service.embedding.EmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.llm.LlmService;
import project.lms_rikkei_edu.modules.ai.service.retrieval.ScoredChunk;
import project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService;
import project.lms_rikkei_edu.modules.quiz.dto.request.AiGenerateQuestionsRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGeneratedQuestion;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGeneratedQuestion.AiGeneratedOption;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerateQuestionsResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerationJobStatusResponse;
import project.lms_rikkei_edu.modules.quiz.entity.AiQuestionGenerationJob;
import project.lms_rikkei_edu.modules.quiz.enums.GenerationStep;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;
import project.lms_rikkei_edu.modules.quiz.repository.AiQuestionGenerationJobRepository;
import project.lms_rikkei_edu.modules.quiz.service.AiQuestionGeneratorService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService.toVectorString;

/**
 * Sinh câu hỏi trắc nghiệm bằng LLM, có tham khảo tài liệu AI (RAG) của khóa học
 * nếu có, sau đó kiểm tra trùng lặp với ngân hàng câu hỏi hiện có bằng cosine
 * similarity (pgvector).
 *
 * <p>Chạy nền theo từng bước (không block request thread — bước "gọi LLM" có thể mất
 * 30-90s tuỳ số câu yêu cầu): {@code startGenerate} tạo job và trả về ngay, pipeline
 * thật chạy trong {@code generateAsync} (dispatch qua {@link Async}, phải được gọi từ
 * bean khác — xem controller — vì self-invocation trong cùng class sẽ bỏ qua {@code @Async}).
 * FE poll {@code getJobStatus} để biết đang ở bước nào.
 *
 * <p>Luồng:
 * <ol>
 *   <li>Embed chủ đề → tìm các đoạn tài liệu liên quan nhất trong {@code document_chunks} của khóa học</li>
 *   <li>Gọi LLM với structured prompt (kèm tài liệu nếu có) → JSON danh sách câu hỏi</li>
 *   <li>Parse JSON → List&lt;AiGeneratedQuestion&gt;</li>
 *   <li>Embed từng questionText → float[]</li>
 *   <li>So sánh với embedding của câu hỏi trong bank (pgvector cosine distance)</li>
 *   <li>Đánh dấu duplicate nếu similarity &ge; threshold</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionGeneratorServiceImpl implements AiQuestionGeneratorService {

    private final LlmService llmService;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearch;
    private final OpenAiProperties props;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final AiQuestionGenerationJobRepository jobRepo;

    @Qualifier("aiGenerationExecutor")
    private final Executor aiGenerationExecutor;

    /**
     * Số câu tối đa sinh trong 1 lượt gọi LLM. Model reasoning (GPT-5 family) có nguy cơ
     * tiêu hết toàn bộ ngân sách token vào reasoning ẩn thay vì viết output khi yêu cầu 1 lượt
     * lớn (đã xác nhận qua log thực tế: yêu cầu 20 câu → completion_tokens = reasoning_tokens =
     * đúng giới hạn, content rỗng, finish_reason=length) — 5 câu/lượt đã xác nhận hoạt động ổn
     * định. Yêu cầu lớn hơn được chia thành nhiều lô, mỗi lô tối đa ngần này câu
     * (xem {@link #generateAllQuestions}) — các lô chạy SONG SONG (bounded, xem
     * {@code aiGenerationExecutor}) thay vì tuần tự để giảm thời gian chờ.
     */
    private static final int MAX_QUESTIONS_PER_LLM_CALL = 5;

    /**
     * Số lần gọi lại thêm (ngoài lượt đầu) cho MỘT LÔ nếu model trả về thiếu câu hợp lệ
     * (VD: MULTIPLE_CHOICE chỉ có 1 đáp án đúng bị {@link #isValidOptionSet} loại bỏ) — chỉ
     * xin bù đúng phần còn thiếu, không xin lại nguyên lô. Không thể đảm bảo tuyệt đối 100%
     * (vẫn có thể hết lượt retry mà chưa đủ), nhưng giảm mạnh tỉ lệ trả thiếu câu so với chỉ
     * lọc bỏ một lần rồi thôi.
     */
    private static final int MAX_RETRY_ROUNDS_PER_BATCH = 2;

    /**
     * Tạo job (step=RETRIEVING_CONTEXT) và trả về ngay — KHÔNG chạy pipeline ở đây.
     * Caller (controller) phải gọi {@link #generateAsync} riêng sau đó — cross-bean call
     * để {@code @Async} có hiệu lực (gọi ngay trong method này sẽ là self-invocation, bỏ qua proxy).
     *
     * <p>Không {@code @Transactional}: nếu bọc transaction ở đây, thread nền của
     * {@code generateAsync} có thể query job trước khi transaction này commit → not found.
     * {@code jobRepo.save(job)} tự commit ngay (transaction riêng theo mặc định Spring Data JPA).
     */
    @Override
    public UUID startGenerate(UUID courseId, AiGenerateQuestionsRequest req, UUID requestedBy) {
        AiQuestionGenerationJob job = new AiQuestionGenerationJob();
        job.setCourseId(courseId);
        job.setRequestedBy(requestedBy);
        job.setStep(GenerationStep.RETRIEVING_CONTEXT);
        job = jobRepo.save(job);
        return job.getId();
    }

    /** Chạy nền — pipeline thật, cập nhật {@code job.step} trước mỗi giai đoạn để FE poll thấy tiến trình. */
    @Override
    @Async
    public void generateAsync(UUID jobId, UUID courseId, AiGenerateQuestionsRequest req) {
        AiQuestionGenerationJob job = jobRepo.findById(jobId).orElseThrow();
        try {
            // 1. Tìm tài liệu khóa học liên quan đến chủ đề (RAG) — không chặn luồng nếu lỗi
            List<ScoredChunk> chunks = retrieveRelevantChunks(courseId, req);

            // 2. Sinh câu hỏi từ LLM, có kèm tài liệu tìm được (nếu có) — bước chậm nhất.
            // Chia thành nhiều lượt gọi nhỏ nếu count > MAX_QUESTIONS_PER_LLM_CALL (xem lý do
            // ở khai báo hằng số) — mỗi lượt cập nhật lại job để FE thấy vẫn đang GENERATING.
            job.setStep(GenerationStep.GENERATING);
            jobRepo.save(job);

            List<AiGeneratedQuestion> questions = generateAllQuestions(courseId, req, chunks);
            if (questions.isEmpty()) {
                throw new BusinessException("AI không sinh được câu hỏi hợp lệ. Hãy thử mô tả chủ đề cụ thể hơn.");
            }

            // 4. Loại trùng NGỮ NGHĨA giữa chính các câu vừa sinh, rồi kiểm tra trùng với bank
            // của CHÍNH khóa học này (theo courseId)
            job.setStep(GenerationStep.CHECKING_DUPLICATES);
            jobRepo.save(job);
            questions = deduplicateAndCheckBank(courseId, questions, req.getDuplicateThreshold());

            long dupCount = questions.stream().filter(AiGeneratedQuestion::isDuplicate).count();
            AiGenerateQuestionsResponse response = new AiGenerateQuestionsResponse(
                    questions,
                    questions.size(),
                    (int) dupCount,
                    (int) (questions.size() - dupCount)
            );

            job.setResultJson(objectMapper.writeValueAsString(response));
            job.setStep(GenerationStep.DONE);
            jobRepo.save(job);

        } catch (Exception ex) {
            log.error("Question generation failed for jobId={}: {}", jobId, ex.getMessage(), ex);
            job.setStep(GenerationStep.FAILED);
            job.setErrorMessage(ex.getMessage());
            jobRepo.save(job);
        }
    }

    /** FE poll endpoint này để biết job đang ở bước nào; khi DONE thì kèm luôn kết quả. */
    @Override
    public AiGenerationJobStatusResponse getJobStatus(UUID jobId) {
        AiQuestionGenerationJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tiến trình sinh câu hỏi", HttpStatus.NOT_FOUND));

        AiGenerateQuestionsResponse result = null;
        if (job.getStep() == GenerationStep.DONE && job.getResultJson() != null) {
            try {
                result = objectMapper.readValue(job.getResultJson(), AiGenerateQuestionsResponse.class);
            } catch (Exception ex) {
                log.error("Failed to deserialize job result for jobId={}: {}", jobId, ex.getMessage());
                return AiGenerationJobStatusResponse.builder()
                        .step(GenerationStep.FAILED)
                        .errorMessage("Lỗi đọc kết quả đã lưu")
                        .build();
            }
        }

        return AiGenerationJobStatusResponse.builder()
                .step(job.getStep())
                .result(result)
                .errorMessage(job.getErrorMessage())
                .build();
    }

    // ── LLM generation (chia lượt) ───────────────────────────────────────────

    /**
     * Gọi LLM sinh đủ {@code req.getCount()} câu hỏi — chia thành nhiều lô, mỗi lô tối đa
     * {@link #MAX_QUESTIONS_PER_LLM_CALL} câu. Các lô chạy SONG SONG qua
     * {@code aiGenerationExecutor} (pool cố định 3 luồng, xem {@link project.lms_rikkei_edu.config.AsyncConfig})
     * — giới hạn cứng số lô chạy cùng lúc để không dồn dập gọi OpenAI (tránh rate-limit) và
     * không tốn thread/heap ngoài kiểm soát khi nhiều giảng viên sinh câu hỏi cùng lúc. Mỗi lô
     * tự retry riêng nếu thiếu câu hợp lệ (xem {@link #generateBatchWithRetry}) — một lô lỗi/hết
     * retry không ảnh hưởng các lô khác.
     *
     * <p>Loại trùng lặp NGUYÊN VĂN giữa các lô (model không biết các lô khác đã sinh gì nên có
     * thể lặp lại câu hỏi) bằng cách so khớp questionText đã chuẩn hoá SAU KHI tất cả lô đã
     * chạy xong (không cần đồng bộ hoá vì merge tuần tự trên thread gọi) — đây chỉ là lưới an
     * toàn ở mức text; trùng NGỮ NGHĨA (diễn đạt khác nhau) được xử lý riêng ở bước sau, xem
     * {@link #deduplicateAndCheckBank}.
     */
    private List<AiGeneratedQuestion> generateAllQuestions(UUID courseId, AiGenerateQuestionsRequest req, List<ScoredChunk> chunks) {
        List<Integer> batchSizes = new ArrayList<>();
        int remaining = req.getCount();
        while (remaining > 0) {
            int batchCount = Math.min(remaining, MAX_QUESTIONS_PER_LLM_CALL);
            batchSizes.add(batchCount);
            remaining -= batchCount;
        }

        List<CompletableFuture<List<AiGeneratedQuestion>>> futures = new ArrayList<>();
        for (int i = 0; i < batchSizes.size(); i++) {
            int batchNo = i + 1;
            int batchCount = batchSizes.get(i);
            futures.add(CompletableFuture.supplyAsync(
                    () -> generateBatchWithRetry(courseId, req, chunks, batchCount, batchNo),
                    aiGenerationExecutor));
        }

        List<AiGeneratedQuestion> all = new ArrayList<>();
        Set<String> seenText = new HashSet<>();
        for (CompletableFuture<List<AiGeneratedQuestion>> f : futures) {
            List<AiGeneratedQuestion> batch;
            try {
                batch = f.join();
            } catch (Exception ex) {
                log.warn("Một lô sinh câu hỏi thất bại, bỏ qua lô đó: {}", ex.getMessage());
                continue;
            }
            for (AiGeneratedQuestion q : batch) {
                if (seenText.add(q.getQuestionText().trim().toLowerCase())) {
                    all.add(q);
                }
            }
        }
        return all;
    }

    /**
     * Sinh 1 lô {@code targetCount} câu hỏi, tự động xin bù thêm nếu model trả về thiếu câu
     * hợp lệ (bị {@link #isValidOptionSet} loại) — tối đa {@link #MAX_RETRY_ROUNDS_PER_BATCH}
     * lượt xin bù, mỗi lượt chỉ xin đúng phần còn thiếu (không xin lại nguyên lô, đỡ tốn token
     * lẫn thời gian). Hết lượt retry mà vẫn thiếu thì trả về những gì thu được — không throw,
     * để các lô khác không bị ảnh hưởng.
     */
    private List<AiGeneratedQuestion> generateBatchWithRetry(UUID courseId, AiGenerateQuestionsRequest req,
                                                               List<ScoredChunk> chunks, int targetCount, int batchNo) {
        List<AiGeneratedQuestion> collected = new ArrayList<>();
        int round = 0;

        while (collected.size() < targetCount && round <= MAX_RETRY_ROUNDS_PER_BATCH) {
            int needed = targetCount - collected.size();
            round++;

            String systemPrompt = buildSystemPrompt(req, chunks);
            String userMessage = buildUserMessage(req, needed);

            String raw;
            try {
                raw = llmService.completeForJson(systemPrompt, userMessage);
            } catch (Exception ex) {
                log.warn("LLM call failed for courseId={} (batch {}, lượt {}, cần {} câu): {}",
                        courseId, batchNo, round, needed, ex.getMessage());
                break; // lô khác vẫn tiếp tục — không throw để không huỷ cả job
            }

            List<AiGeneratedQuestion> got = parseQuestions(raw, req.getQuestionType(), req.getDifficulty());
            log.info("generateBatchWithRetry: batch {} lượt {} cần {} câu, model trả về {} câu hợp lệ",
                    batchNo, round, needed, got.size());
            collected.addAll(got);
        }

        if (collected.size() > targetCount) {
            collected = new ArrayList<>(collected.subList(0, targetCount));
        }
        if (collected.size() < targetCount) {
            log.warn("Batch {} chỉ thu được {}/{} câu hợp lệ sau {} lượt thử",
                    batchNo, collected.size(), targetCount, round);
        }
        return collected;
    }

    // ── RAG retrieval ─────────────────────────────────────────────────────────

    /**
     * Tìm các đoạn tài liệu AI của khóa học liên quan nhất đến chủ đề cần ra đề.
     * Dùng chung ngưỡng/topK với chatbot RAG ({@link OpenAiProperties}) để nhất quán.
     *
     * <p>Instructor không chọn tài liệu nào (sourceIds rỗng) → BỎ QUA RAG hoàn toàn,
     * sinh câu hỏi chỉ dựa theo chủ đề (topic) đã nhập — đây là lựa chọn nhanh nhất
     * (không tốn round-trip embed + vector search). Muốn tham khảo tài liệu thì phải
     * chủ động chọn (kể cả chọn hết trong popup) — không còn mặc định quét toàn bộ
     * tài liệu khóa học như trước, vì đó chính là trường hợp chậm nhất.
     *
     * <p>Không throw — nếu embed/search lỗi (VD: chưa có tài liệu nào được index),
     * trả về danh sách rỗng và việc sinh câu hỏi vẫn tiếp tục bằng kiến thức chung của LLM.
     */
    private List<ScoredChunk> retrieveRelevantChunks(UUID courseId, AiGenerateQuestionsRequest req) {
        if (req.getSourceIds() == null || req.getSourceIds().isEmpty()) {
            return List.of();
        }
        try {
            String query = req.getTopic()
                    + (req.getSubjectTag() != null && !req.getSubjectTag().isBlank() ? " " + req.getSubjectTag() : "");
            float[] queryEmbedding = embeddingService.embed(query);
            return vectorSearch.search(courseId, req.getSourceIds(), queryEmbedding, props.getTopK(), props.getSimilarityThreshold());
        } catch (Exception ex) {
            log.debug("RAG retrieval unavailable for courseId={}: {}", courseId, ex.getMessage());
            return List.of();
        }
    }

    // ── Prompt engineering ──────────────────────────────────────────────────

    private String buildSystemPrompt(AiGenerateQuestionsRequest req, List<ScoredChunk> chunks) {
        String typeGuide = switch (req.getQuestionType()) {
            case SINGLE_CHOICE -> "Mỗi câu có đúng 1 đáp án đúng, các đáp án còn lại sai. Có 4 đáp án.";
            case MULTIPLE_CHOICE -> "Mỗi câu có từ 2 đến 3 đáp án đúng trong tổng số 4-5 đáp án.";
            default -> "Mỗi câu có đúng 1 đáp án đúng, có 4 đáp án.";
        };

        String diffGuide = switch (req.getDifficulty()) {
            case EASY -> "Mức độ: CƠ BẢN — kiểm tra định nghĩa, khái niệm đơn giản.";
            case MEDIUM -> "Mức độ: TRUNG BÌNH — áp dụng kiến thức, phân tích tình huống.";
            case HARD -> "Mức độ: KHÓ — tổng hợp, đánh giá, so sánh chuyên sâu.";
        };

        String exampleOptions = req.getQuestionType() == QuestionType.MULTIPLE_CHOICE
                ? """
                        { "text": "Đáp án A", "correct": true,  "explanation": "Vì..." },
                        { "text": "Đáp án B", "correct": true,  "explanation": "Vì..." },
                        { "text": "Đáp án C", "correct": false, "explanation": "Sai vì..." },
                        { "text": "Đáp án D", "correct": false, "explanation": "Sai vì..." }"""
                : """
                        { "text": "Đáp án A", "correct": true,  "explanation": "Vì..." },
                        { "text": "Đáp án B", "correct": false, "explanation": "Sai vì..." },
                        { "text": "Đáp án C", "correct": false, "explanation": "Sai vì..." },
                        { "text": "Đáp án D", "correct": false, "explanation": "Sai vì..." }""";

        String base = """
                Bạn là chuyên gia ra đề thi trắc nghiệm cho hệ thống LMS giáo dục đại học Việt Nam.
                Nhiệm vụ: sinh câu hỏi trắc nghiệm chất lượng cao bằng tiếng Việt.

                Quy tắc:
                - %s
                - %s
                - QUAN TRỌNG: ví dụ định dạng bên dưới phải được tuân theo ĐÚNG số lượng đáp án đúng
                  như quy tắc loại câu hỏi ở trên — không được chỉ đánh dấu 1 đáp án đúng nếu quy tắc
                  yêu cầu nhiều hơn.
                - Mỗi đáp án phải có trường "explanation" giải thích vì sao đúng hoặc sai.
                - Không lặp lại câu hỏi giống nhau.
                - Câu hỏi phải rõ ràng, không mơ hồ, không có lỗi chính tả.
                - Trả về JSON THUẦN TÚY, không kèm markdown fence (```).
                - Bắt buộc trả về 1 JSON OBJECT ở cấp cao nhất (không phải mảng) với đúng 1 khoá
                  "questions", giá trị là mảng câu hỏi theo định dạng bên dưới.

                Định dạng JSON trả về:
                {
                  "questions": [
                    {
                      "questionText": "Nội dung câu hỏi?",
                      "options": [
                %s
                      ]
                    }
                  ]
                }
                """.formatted(typeGuide, diffGuide, exampleOptions);

        if (chunks.isEmpty()) {
            return base;
        }

        StringBuilder sb = new StringBuilder(base);
        sb.append("\n=== TÀI LIỆU KHÓA HỌC (căn cứ nội dung để ra đề) ===\n");
        for (int i = 0; i < chunks.size(); i++) {
            ScoredChunk c = chunks.get(i);
            sb.append("\n[").append(i + 1).append("]");
            if (c.sectionTitle() != null) sb.append(" ").append(c.sectionTitle());
            sb.append("\n").append(c.chunkText()).append("\n");
        }
        sb.append("""
                === HẾT TÀI LIỆU ===

                Ưu tiên bám sát thuật ngữ, định nghĩa, ví dụ trong tài liệu trên khi ra câu hỏi —
                đây là nội dung giảng viên đã dạy trong khóa học này. Nếu tài liệu không đủ để
                sinh đủ số câu yêu cầu hoặc không liên quan đến chủ đề, dùng kiến thức chuyên môn
                chung để bổ sung, miễn đúng chủ đề và độ khó yêu cầu.
                """);
        return sb.toString();
    }

    private String buildUserMessage(AiGenerateQuestionsRequest req, int count) {
        String tagPart = (req.getSubjectTag() != null && !req.getSubjectTag().isBlank())
                ? " (chuyên đề: " + req.getSubjectTag() + ")"
                : "";
        return "Hãy sinh %d câu hỏi trắc nghiệm về chủ đề: %s%s."
                .formatted(count, req.getTopic(), tagPart);
    }

    // ── Parse LLM output ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<AiGeneratedQuestion> parseQuestions(String raw, QuestionType type, QuestionDifficulty diff) {
        // Loại bỏ markdown fence nếu model vẫn trả về
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-z]*\\s*", "").replaceAll("```\\s*$", "").strip();
        }
        // Tìm JSON array
        int start = json.indexOf('[');
        int end   = json.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            log.warn("LLM output does not contain JSON array: {}", json.substring(0, Math.min(300, json.length())));
            return List.of();
        }
        json = json.substring(start, end + 1);

        try {
            List<Map<String, Object>> raw2 = objectMapper.readValue(json, new TypeReference<>() {});
            List<AiGeneratedQuestion> result = new ArrayList<>();
            for (Map<String, Object> q : raw2) {
                String questionText = (String) q.get("questionText");
                if (questionText == null || questionText.isBlank()) continue;

                List<Map<String, Object>> optList = (List<Map<String, Object>>) q.get("options");
                List<AiGeneratedOption> opts = new ArrayList<>();
                if (optList != null) {
                    for (Map<String, Object> o : optList) {
                        opts.add(new AiGeneratedOption(
                                (String) o.get("text"),
                                Boolean.TRUE.equals(o.get("correct")),
                                (String) o.getOrDefault("explanation", "")
                        ));
                    }
                }
                if (!isValidOptionSet(opts, type)) {
                    log.warn("Bỏ qua câu hỏi AI sinh không hợp lệ (loại {}, số đáp án đúng không khớp): {}",
                            type, questionText);
                    continue;
                }

                AiGeneratedQuestion aq = new AiGeneratedQuestion(questionText, type, diff, opts, false, null, 0.0);
                result.add(aq);
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to parse LLM JSON output: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Chặn câu hỏi AI sinh ra không đúng số lượng đáp án đúng theo loại — VD MULTIPLE_CHOICE
     * chỉ có 1 đáp án đúng (model không tuân theo prompt) — tránh việc giảng viên chỉ phát
     * hiện ra khi bấm lưu (validate ở bước save mới báo lỗi).
     */
    private boolean isValidOptionSet(List<AiGeneratedOption> opts, QuestionType type) {
        if (opts == null || opts.size() < 2) return false;
        long correctCount = opts.stream().filter(AiGeneratedOption::isCorrect).count();
        return switch (type) {
            case SINGLE_CHOICE, TRUE_FALSE -> correctCount == 1;
            case MULTIPLE_CHOICE -> correctCount >= 2;
        };
    }

    // ── Duplicate detection ──────────────────────────────────────────────────

    /**
     * Embed TẤT CẢ câu hỏi vừa sinh trong 1 lệnh gọi batch duy nhất (thay vì N lệnh tuần tự —
     * đây từng là nguồn trễ chính khiến sinh N câu hỏi cần tới N+1 round-trip OpenAI), rồi:
     * <ol>
     *   <li>Loại các câu TRÙNG NGỮ NGHĨA với NHAU trong cùng lượt sinh này (khoảng trống trước
     *       đây: {@code checkDuplicates} cũ chỉ so với bank, không so các câu mới với nhau —
     *       nên 2 câu diễn đạt khác nhau nhưng cùng ý vẫn lọt qua nếu cả 2 đều chưa có
     *       trong bank).</li>
     *   <li>Với các câu còn lại, đánh dấu (KHÔNG loại) câu nào trùng với ngân hàng câu hỏi cũ
     *       của khóa học — để giảng viên tự quyết định có vẫn muốn lưu hay không.</li>
     * </ol>
     * Nếu không có embedding (bank chưa embed / lỗi) thì fallback toàn bộ sang exact-text match
     * — khi đó bỏ qua bước loại trùng ngữ nghĩa nội bộ vì không có gì để so cosine similarity.
     */
    private List<AiGeneratedQuestion> deduplicateAndCheckBank(UUID courseId, List<AiGeneratedQuestion> questions, double threshold) {
        List<float[]> embeddings;
        try {
            embeddings = embeddingService.embedBatch(questions.stream().map(AiGeneratedQuestion::getQuestionText).toList());
        } catch (Exception ex) {
            log.debug("Batch embedding unavailable, falling back to exact match for all questions: {}", ex.getMessage());
            questions.forEach(q -> checkExactDuplicate(courseId, q));
            return questions;
        }

        Deduped deduped = dedupSemanticWithinBatch(questions, embeddings, threshold);
        checkDuplicatesAgainstBank(courseId, deduped.questions(), deduped.embeddings(), threshold);
        return deduped.questions();
    }

    private record Deduped(List<AiGeneratedQuestion> questions, List<float[]> embeddings) {}

    /**
     * So cosine similarity giữa TỪNG CẶP câu hỏi vừa sinh (O(n²), nhưng n ≤ 40 câu/lượt —
     * @Max(40) ở {@code AiGenerateQuestionsRequest} — nên chi phí không đáng kể). Câu xuất
     * hiện sau trong danh sách bị loại nếu similarity ≥ threshold với câu xuất hiện trước.
     */
    private Deduped dedupSemanticWithinBatch(List<AiGeneratedQuestion> questions, List<float[]> embeddings, double threshold) {
        int n = questions.size();
        boolean[] removed = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (removed[i]) continue;
            for (int j = i + 1; j < n; j++) {
                if (removed[j]) continue;
                if (cosineSimilarity(embeddings.get(i), embeddings.get(j)) >= threshold) {
                    removed[j] = true;
                    log.info("Loại câu hỏi trùng ngữ nghĩa với câu khác vừa sinh trong cùng lượt: \"{}\"",
                            questions.get(j).getQuestionText());
                }
            }
        }
        List<AiGeneratedQuestion> kept = new ArrayList<>();
        List<float[]> keptEmbeddings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!removed[i]) {
                kept.add(questions.get(i));
                keptEmbeddings.add(embeddings.get(i));
            }
        }
        return new Deduped(kept, keptEmbeddings);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Với mỗi câu hỏi, tìm câu gần nhất trong bank của courseId bằng pgvector cosine distance —
     * dùng embeddings đã tính sẵn ở {@link #deduplicateAndCheckBank}, không embed lại.
     */
    private void checkDuplicatesAgainstBank(UUID courseId, List<AiGeneratedQuestion> questions, List<float[]> embeddings, double threshold) {
        for (int i = 0; i < questions.size(); i++) {
            AiGeneratedQuestion q = questions.get(i);
            try {
                String vecStr = toVectorString(embeddings.get(i));

                // Tìm câu hỏi gần nhất trong bank của courseId này
                List<Map<String, Object>> rows = jdbc.queryForList(
                        """
                        SELECT bq.id::text            AS id,
                               bq.question_text       AS question_text,
                               1 - (dc.embedding <=> ?::vector) AS similarity
                        FROM bank_question_embeddings dc
                        JOIN bank_questions bq ON bq.id = dc.question_id
                        WHERE bq.course_id = ?
                          AND dc.embedding IS NOT NULL
                          AND 1 - (dc.embedding <=> ?::vector) >= ?
                        ORDER BY dc.embedding <=> ?::vector
                        LIMIT 1
                        """,
                        vecStr, courseId, vecStr, threshold, vecStr
                );

                if (!rows.isEmpty()) {
                    Map<String, Object> top = rows.get(0);
                    q.setDuplicate(true);
                    q.setDuplicateOfId((String) top.get("id"));
                    q.setSimilarityScore(((Number) top.get("similarity")).doubleValue());
                }
            } catch (Exception ex) {
                // Bảng bank_question_embeddings chưa tồn tại hoặc lỗi vector → fallback exact match
                log.debug("Vector duplicate check unavailable, falling back to exact match: {}", ex.getMessage());
                checkExactDuplicate(courseId, q);
            }
        }
    }

    private void checkExactDuplicate(UUID courseId, AiGeneratedQuestion q) {
        try {
            Boolean exists = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM bank_questions WHERE course_id = ? AND lower(question_text) = lower(?))",
                    Boolean.class, courseId, q.getQuestionText()
            );
            if (Boolean.TRUE.equals(exists)) {
                q.setDuplicate(true);
                q.setSimilarityScore(1.0);
            }
        } catch (Exception ex) {
            log.warn("Exact duplicate check failed: {}", ex.getMessage());
        }
    }

}
