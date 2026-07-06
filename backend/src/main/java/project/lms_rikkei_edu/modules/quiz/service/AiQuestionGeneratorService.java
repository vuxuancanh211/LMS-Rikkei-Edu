package project.lms_rikkei_edu.modules.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.ai.service.embedding.EmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.llm.LlmService;
import project.lms_rikkei_edu.modules.quiz.dto.request.AiGenerateQuestionsRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGeneratedQuestion;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGeneratedQuestion.AiGeneratedOption;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerateQuestionsResponse;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.*;

/**
 * Sinh câu hỏi trắc nghiệm bằng LLM, sau đó kiểm tra trùng lặp
 * với ngân hàng câu hỏi hiện có của khóa học bằng cosine similarity (pgvector).
 *
 * <p>Luồng:
 * <ol>
 *   <li>Gọi LLM với structured prompt → JSON danh sách câu hỏi</li>
 *   <li>Parse JSON → List&lt;AiGeneratedQuestion&gt;</li>
 *   <li>Embed từng questionText → float[]</li>
 *   <li>So sánh với embedding của câu hỏi trong bank (pgvector cosine distance)</li>
 *   <li>Đánh dấu duplicate nếu similarity &ge; threshold</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionGeneratorService {

    private final LlmService llmService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public AiGenerateQuestionsResponse generate(UUID courseId, AiGenerateQuestionsRequest req) {
        // 1. Sinh câu hỏi từ LLM
        String systemPrompt = buildSystemPrompt(req);
        String userMessage  = buildUserMessage(req);

        String raw;
        try {
            raw = llmService.completeForJson(systemPrompt, userMessage);
        } catch (Exception ex) {
            log.error("LLM call failed for courseId={}: {}", courseId, ex.getMessage(), ex);
            throw new RuntimeException("Không thể kết nối dịch vụ AI. Vui lòng thử lại sau.", ex);
        }

        // 2. Parse JSON
        List<AiGeneratedQuestion> questions = parseQuestions(raw, req.getQuestionType(), req.getDifficulty());
        if (questions.isEmpty()) {
            throw new RuntimeException("AI không sinh được câu hỏi hợp lệ. Hãy thử mô tả chủ đề cụ thể hơn.");
        }

        // 3. Kiểm tra trùng với bank của CHÍNH khóa học này (theo courseId)
        checkDuplicates(courseId, questions, req.getDuplicateThreshold());

        long dupCount = questions.stream().filter(AiGeneratedQuestion::isDuplicate).count();
        return new AiGenerateQuestionsResponse(
                questions,
                questions.size(),
                (int) dupCount,
                (int) (questions.size() - dupCount)
        );
    }

    // ── Prompt engineering ──────────────────────────────────────────────────

    private String buildSystemPrompt(AiGenerateQuestionsRequest req) {
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

        return """
                Bạn là chuyên gia ra đề thi trắc nghiệm cho hệ thống LMS giáo dục đại học Việt Nam.
                Nhiệm vụ: sinh câu hỏi trắc nghiệm chất lượng cao bằng tiếng Việt.

                Quy tắc:
                - %s
                - %s
                - Mỗi đáp án phải có trường "explanation" giải thích vì sao đúng hoặc sai.
                - Không lặp lại câu hỏi giống nhau.
                - Câu hỏi phải rõ ràng, không mơ hồ, không có lỗi chính tả.
                - Trả về JSON THUẦN TÚY, không kèm markdown fence (```).

                Định dạng JSON trả về:
                [
                  {
                    "questionText": "Nội dung câu hỏi?",
                    "options": [
                      { "text": "Đáp án A", "correct": true,  "explanation": "Vì..." },
                      { "text": "Đáp án B", "correct": false, "explanation": "Sai vì..." },
                      { "text": "Đáp án C", "correct": false, "explanation": "Sai vì..." },
                      { "text": "Đáp án D", "correct": false, "explanation": "Sai vì..." }
                    ]
                  }
                ]
                """.formatted(typeGuide, diffGuide);
    }

    private String buildUserMessage(AiGenerateQuestionsRequest req) {
        String tagPart = (req.getSubjectTag() != null && !req.getSubjectTag().isBlank())
                ? " (chuyên đề: " + req.getSubjectTag() + ")"
                : "";
        return "Hãy sinh %d câu hỏi trắc nghiệm về chủ đề: %s%s."
                .formatted(req.getCount(), req.getTopic(), tagPart);
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
                AiGeneratedQuestion aq = new AiGeneratedQuestion(questionText, type, diff, opts, false, null, 0.0);
                result.add(aq);
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to parse LLM JSON output: {}", ex.getMessage());
            return List.of();
        }
    }

    // ── Duplicate detection ──────────────────────────────────────────────────

    /**
     * Với mỗi câu hỏi mới sinh, embed questionText rồi tìm câu gần nhất
     * trong bank của courseId bằng pgvector cosine distance.
     *
     * <p>Nếu không có embedding trong bank (chưa embed) thì fallback sang
     * exact-text match (existsByCourseIdAndQuestionText).
     */
    private void checkDuplicates(UUID courseId, List<AiGeneratedQuestion> questions, double threshold) {
        for (AiGeneratedQuestion q : questions) {
            try {
                float[] embedding = embeddingService.embed(q.getQuestionText());
                String vecStr = toVectorString(embedding);

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

    private static String toVectorString(float[] embedding) {
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (float v : embedding) sj.add(Float.toString(v));
        return sj.toString();
    }
}
