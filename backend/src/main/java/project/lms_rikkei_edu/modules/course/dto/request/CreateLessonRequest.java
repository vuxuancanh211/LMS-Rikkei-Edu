package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.quiz.dto.request.QuizMetadataRequest;

import java.util.UUID;

@Getter
@Setter
public class CreateLessonRequest {

    @NotBlank
    @Size(min = 3, max = 200)
    private String title;

    @NotNull
    private LessonType type;

    @Size(max = 10000)
    private String contentText;

    private Boolean isPreview = false;

    /** Chỉ dùng khi type == QUIZ — gắn 1 quiz đã có sẵn trong khóa học. */
    private UUID quizId;

    /** Chỉ dùng khi type == QUIZ — tạo quiz mới (shell, chưa có câu hỏi) rồi gắn vào lesson này. */
    @Valid
    private QuizMetadataRequest newQuiz;
}
