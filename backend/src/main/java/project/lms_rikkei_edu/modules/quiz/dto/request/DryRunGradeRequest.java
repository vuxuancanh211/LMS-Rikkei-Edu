package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu chấm điểm bản xem thử (dry run) — không lưu vào DB.
 * {@code questionIds} là toàn bộ câu hỏi đã hiển thị cho giảng viên (kể cả câu chưa trả lời),
 * dùng để tính đúng số câu bỏ qua thay vì chỉ dựa vào {@code answers}.
 */
@Getter
@Setter
public class DryRunGradeRequest {

    @NotEmpty(message = "Danh sách câu hỏi không được để trống")
    private List<UUID> questionIds;

    /** questionId → danh sách optionId đã chọn. Câu không có trong map coi như bỏ qua. */
    private Map<UUID, List<UUID>> answers;
}
