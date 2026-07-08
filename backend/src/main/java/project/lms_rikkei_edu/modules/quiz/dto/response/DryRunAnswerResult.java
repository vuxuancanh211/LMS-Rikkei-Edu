package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DryRunAnswerResult {
    private UUID questionId;
    private boolean answered;
    /**
     * Boolean (wrapper), KHÔNG phải boolean nguyên thủy — nếu để primitive, Lombok sinh getter
     * {@code isCorrect()} và Jackson sẽ bỏ tiền tố "is" khi suy ra tên field JSON, serialize
     * thành {@code "correct"} thay vì {@code "isCorrect"} khiến FE luôn đọc ra undefined.
     */
    private Boolean isCorrect;
    private BigDecimal pointsEarned;
    /** Chỉ dùng trong dry-run (giảng viên xem thử) — không áp dụng cho học viên làm bài thật. */
    private List<UUID> correctOptionIds;
}
