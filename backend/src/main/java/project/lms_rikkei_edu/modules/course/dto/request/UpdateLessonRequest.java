package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.quiz.dto.request.QuizMetadataRequest;

import java.util.UUID;

@Getter
@Setter
public class UpdateLessonRequest {

    @Size(min = 3, max = 200)
    private String title;

    private LessonType type;

    @Size(max = 10000)
    private String contentText;

    private Boolean isPreview;

    /** Chỉ dùng khi lesson đang là type == QUIZ — đổi sang 1 quiz khác đã có sẵn trong khóa học. */
    private UUID quizId;

    /** Chỉ dùng khi lesson đang là type == QUIZ — tạo quiz mới rồi gắn thay cho quiz hiện tại. */
    @Valid
    private QuizMetadataRequest newQuiz;

    /**
     * Chỉ áp dụng khi đổi quiz ({@code quizId}/{@code newQuiz} có giá trị) — true thì reset lại
     * lesson_progress của lesson này cho các học viên đang học dở khóa (không đụng học viên đã
     * hoàn thành cả khóa). Mặc định false (giữ nguyên tiến độ cũ).
     */
    private Boolean resetProgressForInProgressStudents = false;
}
