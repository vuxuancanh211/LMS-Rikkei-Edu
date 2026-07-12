package project.lms_rikkei_edu.modules.quiz.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BankQuestionImportConfirmRequestTest {

    @Test
    void gettersAndSetters_roundTrip() {
        BankQuestionImportConfirmRequest req = new BankQuestionImportConfirmRequest();

        req.setToken("preview-token");
        req.setSelectedDuplicateRows(List.of(2, 5));

        assertThat(req.getToken()).isEqualTo("preview-token");
        assertThat(req.getSelectedDuplicateRows()).containsExactly(2, 5);
    }

    @Test
    void selectedDuplicateRows_defaultsToNull() {
        BankQuestionImportConfirmRequest req = new BankQuestionImportConfirmRequest();

        assertThat(req.getSelectedDuplicateRows()).isNull();
    }
}
