package project.lms_rikkei_edu.modules.quiz.dto.request;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerateQuestionsRequestTest {

    @Test
    void defaultValues_areAppliedWhenNotSet() {
        AiGenerateQuestionsRequest req = new AiGenerateQuestionsRequest();

        assertThat(req.getCount()).isEqualTo(5);
        assertThat(req.getDuplicateThreshold()).isEqualTo(0.88);
    }

    @Test
    void gettersAndSetters_roundTrip() {
        AiGenerateQuestionsRequest req = new AiGenerateQuestionsRequest();
        UUID sourceId = UUID.randomUUID();

        req.setTopic("Indexing");
        req.setQuestionType(QuestionType.SINGLE_CHOICE);
        req.setDifficulty(QuestionDifficulty.EASY);
        req.setSubjectTag("Index");
        req.setSourceIds(List.of(sourceId));
        req.setCount(10);
        req.setDuplicateThreshold(0.95);

        assertThat(req.getTopic()).isEqualTo("Indexing");
        assertThat(req.getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(req.getDifficulty()).isEqualTo(QuestionDifficulty.EASY);
        assertThat(req.getSubjectTag()).isEqualTo("Index");
        assertThat(req.getSourceIds()).containsExactly(sourceId);
        assertThat(req.getCount()).isEqualTo(10);
        assertThat(req.getDuplicateThreshold()).isEqualTo(0.95);
    }
}
