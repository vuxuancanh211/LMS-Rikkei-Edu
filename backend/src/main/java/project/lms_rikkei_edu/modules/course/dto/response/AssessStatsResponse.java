package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessStatsResponse {
    private long quizCount;
    private long bankQuestionCount;
}
