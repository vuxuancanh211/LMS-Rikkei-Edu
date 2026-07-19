package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.enums.VideoStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/* NoArgsConstructor cần cho Jackson deserialize lại từ Redis cache "course-detail" — @Builder một mình sẽ bỏ constructor mặc định. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonResponse {
    private UUID id;
    private String title;
    private Integer orderIndex;
    private LessonType type;
    private String contentText;
    private Integer durationSeconds;
    private Boolean isPreview;
    private VideoStatus videoStatus;
    private String hlsManifestUrl;
    private List<LessonResourceResponse> resources;
    private Instant createdAt;
    private Instant updatedAt;
    private Boolean isDraft;
    private Boolean pendingDelete;
    private String draftTitle;
    private String draftContentText;

    private String progress;
    private Integer progressPercentage;

    /** Chỉ có giá trị khi type == QUIZ. */
    private UUID quizId;
    private String quizTitle;
    private QuizStatus quizStatus;
}
