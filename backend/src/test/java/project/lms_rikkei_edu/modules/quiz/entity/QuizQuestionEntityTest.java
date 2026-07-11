package project.lms_rikkei_edu.modules.quiz.entity;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuizQuestionEntityTest {

    @Test
    void prePersist_generatesId_whenUnset() {
        QuizQuestionEntity q = new QuizQuestionEntity();

        q.prePersist();

        assertThat(q.getId()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingId() {
        QuizQuestionEntity q = new QuizQuestionEntity();
        UUID id = UUID.randomUUID();
        q.setId(id);

        q.prePersist();

        assertThat(q.getId()).isEqualTo(id);
    }

    @Test
    void gettersAndSetters_roundTrip() {
        QuizQuestionEntity q = new QuizQuestionEntity();
        UUID quizId = UUID.randomUUID();
        UUID bankQuestionId = UUID.randomUUID();

        q.setQuizId(quizId);
        q.setBankQuestionId(bankQuestionId);
        q.setQuestionText("2+2=?");
        q.setQuestionType(QuestionType.SINGLE_CHOICE);
        q.setDifficulty(QuestionDifficulty.EASY);
        q.setSubjectTag("Math");
        q.setOrderIndex(3);
        q.setExplanation("basic arithmetic");

        assertThat(q.getQuizId()).isEqualTo(quizId);
        assertThat(q.getBankQuestionId()).isEqualTo(bankQuestionId);
        assertThat(q.getQuestionText()).isEqualTo("2+2=?");
        assertThat(q.getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(q.getDifficulty()).isEqualTo(QuestionDifficulty.EASY);
        assertThat(q.getSubjectTag()).isEqualTo("Math");
        assertThat(q.getOrderIndex()).isEqualTo(3);
        assertThat(q.getExplanation()).isEqualTo("basic arithmetic");
    }
}
