package project.lms_rikkei_edu.modules.quiz.service;

import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Vòng đời embedding cho câu hỏi ngân hàng ({@code bank_question_embeddings}) — phục vụ
 * hybrid semantic search và duplicate-check ngữ nghĩa của luồng sinh câu hỏi AI.
 * Xem {@code impl.BankQuestionEmbeddingServiceImpl} cho chi tiết.
 *
 * <p><b>Fail-soft toàn bộ:</b> các method ghi KHÔNG BAO GIỜ throw — embedding lỗi
 * chỉ log warn và bỏ qua; search lỗi trả danh sách rỗng để endpoint degrade về text-only.
 */
public interface BankQuestionEmbeddingService {

    /** Cặp (id, text) đầu vào cho embed batch. */
    record IdText(UUID id, String text) {}

    /** Kết quả semantic search: id câu hỏi + cosine similarity (0-1). */
    record SemanticHit(UUID questionId, double similarity) {}

    /** Embed 1 câu hỏi + upsert — fail-soft, không bao giờ throw. */
    void embedAndSaveSafe(UUID questionId, String questionText);

    /** Embed nhiều câu trong 1 lượt gọi API + batch upsert — fail-soft, không bao giờ throw. */
    void embedAndSaveBatchSafe(List<IdText> items);

    /**
     * Tìm câu hỏi tương đồng ngữ nghĩa với {@code query} trong bank của khóa học.
     * Embed lỗi → trả rỗng (endpoint degrade về text-only).
     */
    List<SemanticHit> searchSimilar(UUID courseId, String query,
                                     QuestionStatus status, QuestionDifficulty difficulty, String subjectTag,
                                     Collection<UUID> excludeIds, int topK, double threshold);
}
