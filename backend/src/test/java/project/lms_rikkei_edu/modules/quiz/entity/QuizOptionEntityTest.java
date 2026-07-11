package project.lms_rikkei_edu.modules.quiz.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuizOptionEntityTest {

    @Test
    void prePersist_generatesId_whenUnset() {
        QuizOptionEntity opt = new QuizOptionEntity();

        opt.prePersist();

        assertThat(opt.getId()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingId() {
        QuizOptionEntity opt = new QuizOptionEntity();
        UUID id = UUID.randomUUID();
        opt.setId(id);

        opt.prePersist();

        assertThat(opt.getId()).isEqualTo(id);
    }

    @Test
    void gettersAndSetters_roundTrip() {
        QuizOptionEntity opt = new QuizOptionEntity();
        UUID questionId = UUID.randomUUID();

        opt.setQuestionId(questionId);
        opt.setOptionText("A");
        opt.setIsCorrect(false);
        opt.setOrderIndex(0);

        assertThat(opt.getQuestionId()).isEqualTo(questionId);
        assertThat(opt.getOptionText()).isEqualTo("A");
        assertThat(opt.getIsCorrect()).isFalse();
        assertThat(opt.getOrderIndex()).isEqualTo(0);
    }
}
