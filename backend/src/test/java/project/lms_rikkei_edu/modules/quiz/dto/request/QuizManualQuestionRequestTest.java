package project.lms_rikkei_edu.modules.quiz.dto.request;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizManualQuestionRequestTest {

    @Test
    void saveToBank_defaultsToFalse() {
        QuizManualQuestionRequest req = new QuizManualQuestionRequest();

        assertThat(req.isSaveToBank()).isFalse();
    }

    @Test
    void gettersAndSetters_roundTrip() {
        QuizManualQuestionRequest req = new QuizManualQuestionRequest();
        BankOptionRequest option = new BankOptionRequest();
        option.setOptionText("A");
        option.setIsCorrect(true);
        option.setOrderIndex(0);

        req.setQuestionText("2+2=?");
        req.setQuestionType(QuestionType.SINGLE_CHOICE);
        req.setDifficulty(QuestionDifficulty.EASY);
        req.setSubjectTag("Math");
        req.setExplanation("basic arithmetic");
        req.setOptions(List.of(option));
        req.setSaveToBank(true);

        assertThat(req.getQuestionText()).isEqualTo("2+2=?");
        assertThat(req.getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(req.getDifficulty()).isEqualTo(QuestionDifficulty.EASY);
        assertThat(req.getSubjectTag()).isEqualTo("Math");
        assertThat(req.getExplanation()).isEqualTo("basic arithmetic");
        assertThat(req.getOptions()).containsExactly(option);
        assertThat(req.isSaveToBank()).isTrue();
    }
}
