package project.lms_rikkei_edu.modules.quiz.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DryRunGradeRequestTest {

    @Test
    void gettersAndSetters_roundTrip() {
        DryRunGradeRequest req = new DryRunGradeRequest();
        UUID questionId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();

        req.setQuestionIds(List.of(questionId));
        req.setAnswers(Map.of(questionId, List.of(optionId)));

        assertThat(req.getQuestionIds()).containsExactly(questionId);
        assertThat(req.getAnswers()).containsEntry(questionId, List.of(optionId));
    }

    @Test
    void answers_defaultsToNull() {
        DryRunGradeRequest req = new DryRunGradeRequest();

        assertThat(req.getAnswers()).isNull();
    }
}
