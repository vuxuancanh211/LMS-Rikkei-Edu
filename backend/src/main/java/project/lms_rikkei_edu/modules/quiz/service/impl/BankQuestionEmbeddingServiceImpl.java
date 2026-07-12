package project.lms_rikkei_edu.modules.quiz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.ai.service.embedding.EmbeddingService;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService.toVectorString;

/**
 * Vòng đời embedding cho câu hỏi ngân hàng ({@code bank_question_embeddings}) — phục vụ
 * hybrid semantic search và duplicate-check ngữ nghĩa của luồng sinh câu hỏi AI.
 *
 * <p>JPA không map được kiểu pgvector nên mọi thao tác đi qua {@link JdbcTemplate}
 * (theo tiền lệ {@code document_chunks}).
 *
 * <p><b>Fail-soft toàn bộ:</b> các method ghi KHÔNG BAO GIỜ throw — embedding lỗi
 * (OpenAI down, hết quota...) chỉ log warn và bỏ qua, CRUD câu hỏi vẫn thành công;
 * {@code BankQuestionEmbeddingBackfillJob} sẽ vá lỗ hổng sau. Search lỗi trả danh sách
 * rỗng để endpoint degrade về text-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankQuestionEmbeddingServiceImpl implements BankQuestionEmbeddingService {

    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbc;

    private static final String UPSERT_SQL = """
            INSERT INTO bank_question_embeddings (question_id, embedding, updated_at)
            VALUES (?, ?::vector, now())
            ON CONFLICT (question_id) DO UPDATE SET embedding = EXCLUDED.embedding, updated_at = now()
            """;

    /** Embed 1 câu hỏi + upsert — fail-soft, không bao giờ throw. */
    @Override
    public void embedAndSaveSafe(UUID questionId, String questionText) {
        try {
            float[] embedding = embeddingService.embed(questionText);
            jdbc.update(UPSERT_SQL, questionId, toVectorString(embedding));
        } catch (Exception ex) {
            log.warn("Embed bank question thất bại (backfill job sẽ vá) questionId={}: {}", questionId, ex.getMessage());
        }
    }

    /** Embed nhiều câu trong 1 lượt gọi API + batch upsert — fail-soft, không bao giờ throw. */
    @Override
    public void embedAndSaveBatchSafe(List<IdText> items) {
        if (items == null || items.isEmpty()) return;
        try {
            List<float[]> embeddings = embeddingService.embedBatch(items.stream().map(IdText::text).toList());
            jdbc.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setObject(1, items.get(i).id());
                    ps.setString(2, toVectorString(embeddings.get(i)));
                }
                @Override
                public int getBatchSize() { return items.size(); }
            });
        } catch (Exception ex) {
            log.warn("Embed batch {} bank questions thất bại (backfill job sẽ vá): {}", items.size(), ex.getMessage());
        }
    }

    /**
     * Tìm câu hỏi tương đồng ngữ nghĩa với {@code query} trong bank của khóa học.
     * Embed lỗi → trả rỗng (endpoint degrade về text-only). Các filter tùy chọn
     * áp cùng chỗ với pha text để kết quả 2 pha nhất quán.
     */
    @Override
    public List<SemanticHit> searchSimilar(UUID courseId, String query,
                                           QuestionStatus status, QuestionDifficulty difficulty, String subjectTag,
                                           Collection<UUID> excludeIds, int topK, double threshold) {
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingService.embed(query);
        } catch (Exception ex) {
            log.warn("Embed search query thất bại — degrade về text-only: {}", ex.getMessage());
            return List.of();
        }

        String vecStr = toVectorString(queryEmbedding);
        StringBuilder sql = new StringBuilder("""
                SELECT bq.id, 1 - (e.embedding <=> ?::vector) AS similarity
                FROM bank_question_embeddings e
                JOIN bank_questions bq ON bq.id = e.question_id
                WHERE bq.course_id = ?
                  AND 1 - (e.embedding <=> ?::vector) >= ?
                """);
        List<Object> params = new ArrayList<>(List.of(vecStr, courseId, vecStr, threshold));

        if (status != null)     { sql.append("  AND bq.status = ?\n");      params.add(status.name()); }
        if (difficulty != null) { sql.append("  AND bq.difficulty = ?\n");  params.add(difficulty.name()); }
        if (subjectTag != null && !subjectTag.isBlank()) { sql.append("  AND bq.subject_tag = ?\n"); params.add(subjectTag); }
        if (excludeIds != null && !excludeIds.isEmpty()) {
            sql.append("  AND bq.id NOT IN (")
               .append(String.join(",", Collections.nCopies(excludeIds.size(), "?")))
               .append(")\n");
            params.addAll(excludeIds);
        }

        sql.append("ORDER BY e.embedding <=> ?::vector\nLIMIT ?");
        params.add(vecStr);
        params.add(topK);

        try {
            return jdbc.query(sql.toString(),
                    (rs, i) -> new SemanticHit(rs.getObject("id", UUID.class), rs.getDouble("similarity")),
                    params.toArray());
        } catch (Exception ex) {
            log.warn("Semantic search thất bại — degrade về text-only: {}", ex.getMessage());
            return List.of();
        }
    }
}
