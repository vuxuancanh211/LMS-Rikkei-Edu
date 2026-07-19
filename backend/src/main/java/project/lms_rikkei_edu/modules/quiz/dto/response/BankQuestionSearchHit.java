package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 1 kết quả hybrid search ngân hàng câu hỏi. Wrapper thay vì nhét similarity vào
 * {@link BankQuestionResponse} (DTO đó dùng chung 8+ endpoint, field null 95% thời gian).
 */
@Getter
@Builder
public class BankQuestionSearchHit {
    private BankQuestionResponse question;
    /** "TEXT" (khớp chữ — xếp trước) hoặc "SEMANTIC" (tương đồng ngữ nghĩa — nối sau). */
    private String matchType;
    /** Cosine similarity 0-1, chỉ có giá trị khi matchType=SEMANTIC. */
    private Double similarity;
}
