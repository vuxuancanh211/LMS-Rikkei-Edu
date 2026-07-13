package project.lms_rikkei_edu.modules.quiz.entity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuizAttemptAnswerEntityTest {

    @Test
    void prePersist_generatesId_whenUnset() {
        QuizAttemptAnswerEntity ans = new QuizAttemptAnswerEntity();

        ans.prePersist();

        assertThat(ans.getId()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingId() {
        QuizAttemptAnswerEntity ans = new QuizAttemptAnswerEntity();
        UUID id = UUID.randomUUID();
        ans.setId(id);

        ans.prePersist();

        assertThat(ans.getId()).isEqualTo(id);
    }

    @Test
    void gettersAndSetters_roundTrip() {
        QuizAttemptAnswerEntity ans = new QuizAttemptAnswerEntity();
        UUID attemptId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();

        ans.setAttemptId(attemptId);
        ans.setQuestionId(questionId);
        ans.setSelectedOptionIds(List.of(optionId));
        ans.setIsCorrect(true);
        ans.setTimeSpentSeconds(42);

        assertThat(ans.getAttemptId()).isEqualTo(attemptId);
        assertThat(ans.getQuestionId()).isEqualTo(questionId);
        assertThat(ans.getSelectedOptionIds()).containsExactly(optionId);
        assertThat(ans.getIsCorrect()).isTrue();
        assertThat(ans.getTimeSpentSeconds()).isEqualTo(42);
    }
}
