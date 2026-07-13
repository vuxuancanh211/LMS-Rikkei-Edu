package project.lms_rikkei_edu.modules.quiz.entity;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankQuestionEntityTest {

    @Test
    void prePersist_generatesIdAndDefaultStatus_whenUnset() {
        BankQuestionEntity q = new BankQuestionEntity();

        q.prePersist();

        assertThat(q.getId()).isNotNull();
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.ACTIVE);
        assertThat(q.getCreatedAt()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingIdAndStatus() {
        BankQuestionEntity q = new BankQuestionEntity();
        UUID id = UUID.randomUUID();
        q.setId(id);
        q.setStatus(QuestionStatus.INACTIVE);

        q.prePersist();

        assertThat(q.getId()).isEqualTo(id);
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.INACTIVE);
    }

    @Test
    void gettersAndSetters_roundTrip() {
        BankQuestionEntity q = new BankQuestionEntity();
        UUID courseId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        q.setCourseId(courseId);
        q.setCreatedBy(createdBy);
        q.setSubjectTag("Index");
        q.setQuestionText("What is 1+1?");
        q.setQuestionType(QuestionType.SINGLE_CHOICE);
        q.setDifficulty(QuestionDifficulty.EASY);

        assertThat(q.getCourseId()).isEqualTo(courseId);
        assertThat(q.getCreatedBy()).isEqualTo(createdBy);
        assertThat(q.getSubjectTag()).isEqualTo("Index");
        assertThat(q.getQuestionText()).isEqualTo("What is 1+1?");
        assertThat(q.getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(q.getDifficulty()).isEqualTo(QuestionDifficulty.EASY);
    }
}
