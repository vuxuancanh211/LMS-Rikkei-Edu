package project.lms_rikkei_edu.modules.quiz.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService.IdText;

import java.util.List;
import java.util.UUID;

/**
 * Job tự-hồi-phục: embed các câu hỏi ngân hàng chưa có embedding. Cover 3 trường hợp:
 * (1) câu tồn đọng từ trước khi có bảng bank_question_embeddings, (2) câu embed lỗi
 * lúc ghi (write path fail-soft để lại lỗ hổng), (3) câu tạo trong lúc OpenAI down.
 * Không còn gì thiếu thì tick gần như no-op (1 query SELECT rỗng).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankQuestionEmbeddingBackfillJob {

    private static final int BATCH_LIMIT = 100;

    private final JdbcTemplate jdbc;
    private final BankQuestionEmbeddingService embeddingService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void backfillMissingEmbeddings() {
        try {
            List<IdText> missing = jdbc.query("""
                    SELECT bq.id, bq.question_text
                    FROM bank_questions bq
                    WHERE NOT EXISTS (SELECT 1 FROM bank_question_embeddings e WHERE e.question_id = bq.id)
                    LIMIT ?
                    """,
                    (rs, i) -> new IdText(rs.getObject("id", UUID.class), rs.getString("question_text")),
                    BATCH_LIMIT);
            if (missing.isEmpty()) return;
            log.info("Backfill embedding cho {} câu hỏi ngân hàng còn thiếu", missing.size());
            embeddingService.embedAndSaveBatchSafe(missing);
        } catch (Exception ex) {
            // 1 dòng warn — không spam stacktrace mỗi 5 phút khi OpenAI/DB có vấn đề kéo dài
            log.warn("Backfill embedding tick thất bại: {}", ex.getMessage());
        }
    }
}
