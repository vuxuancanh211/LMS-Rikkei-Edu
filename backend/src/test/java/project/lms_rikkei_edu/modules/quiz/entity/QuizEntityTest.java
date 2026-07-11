package project.lms_rikkei_edu.modules.quiz.entity;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuizType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuizEntityTest {

    @Test
    void prePersist_generatesIdAndDefaultStatus_whenUnset() {
        QuizEntity quiz = new QuizEntity();

        quiz.prePersist();

        assertThat(quiz.getId()).isNotNull();
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.DRAFT);
        assertThat(quiz.getCreatedAt()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingIdAndStatus() {
        QuizEntity quiz = new QuizEntity();
        UUID id = UUID.randomUUID();
        quiz.setId(id);
        quiz.setStatus(QuizStatus.PUBLISHED);

        quiz.prePersist();

        assertThat(quiz.getId()).isEqualTo(id);
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
    }

    @Test
    void preUpdate_refreshesUpdatedAt() {
        QuizEntity quiz = new QuizEntity();

        quiz.preUpdate();

        assertThat(quiz.getUpdatedAt()).isNotNull();
    }

    @Test
    void gettersAndSetters_roundTrip() {
        QuizEntity quiz = new QuizEntity();
        UUID courseId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        quiz.setCourseId(courseId);
        quiz.setCreatedBy(createdBy);
        quiz.setTitle("Quiz 1");
        quiz.setQuizType(QuizType.STATIC);
        quiz.setDurationMinutes(30);
        quiz.setPassScore(BigDecimal.valueOf(60));

        assertThat(quiz.getCourseId()).isEqualTo(courseId);
        assertThat(quiz.getCreatedBy()).isEqualTo(createdBy);
        assertThat(quiz.getTitle()).isEqualTo("Quiz 1");
        assertThat(quiz.getQuizType()).isEqualTo(QuizType.STATIC);
        assertThat(quiz.getDurationMinutes()).isEqualTo(30);
        assertThat(quiz.getPassScore()).isEqualByComparingTo(BigDecimal.valueOf(60));
    }
}
