package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.RandomMode;

import java.util.Map;

@Getter
@Setter
public class QuizRandomConfigRequest {

    @NotNull(message = "Random mode không được để trống")
    private RandomMode randomMode;

    // Dùng khi randomMode = FULLY_RANDOM
    private Integer totalCount;

    // Dùng khi randomMode = BY_DIFFICULTY: {"EASY": 3, "MEDIUM": 3, "HARD": 4}
    private Map<String, Integer> difficultyConfig;

    // Filter tùy chọn theo tag
    private String subjectTagFilter;
}
