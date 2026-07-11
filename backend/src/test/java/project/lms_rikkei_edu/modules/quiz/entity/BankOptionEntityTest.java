package project.lms_rikkei_edu.modules.quiz.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankOptionEntityTest {

    @Test
    void prePersist_generatesId_whenUnset() {
        BankOptionEntity opt = new BankOptionEntity();

        opt.prePersist();

        assertThat(opt.getId()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingId() {
        BankOptionEntity opt = new BankOptionEntity();
        UUID id = UUID.randomUUID();
        opt.setId(id);

        opt.prePersist();

        assertThat(opt.getId()).isEqualTo(id);
    }

    @Test
    void gettersAndSetters_roundTrip() {
        BankOptionEntity opt = new BankOptionEntity();
        UUID bankQuestionId = UUID.randomUUID();

        opt.setBankQuestionId(bankQuestionId);
        opt.setOptionText("B-Tree");
        opt.setIsCorrect(true);
        opt.setOrderIndex(1);

        assertThat(opt.getBankQuestionId()).isEqualTo(bankQuestionId);
        assertThat(opt.getOptionText()).isEqualTo("B-Tree");
        assertThat(opt.getIsCorrect()).isTrue();
        assertThat(opt.getOrderIndex()).isEqualTo(1);
    }
}
