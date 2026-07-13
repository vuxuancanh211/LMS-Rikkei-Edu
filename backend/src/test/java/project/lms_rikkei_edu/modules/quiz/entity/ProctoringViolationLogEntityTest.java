package project.lms_rikkei_edu.modules.quiz.entity;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.quiz.enums.ViolationType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProctoringViolationLogEntityTest {

    @Test
    void prePersist_generatesIdAndServerTimestamp_whenIdUnset() {
        ProctoringViolationLogEntity log = new ProctoringViolationLogEntity();

        log.prePersist();

        assertThat(log.getId()).isNotNull();
        assertThat(log.getServerTimestamp()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingId() {
        ProctoringViolationLogEntity log = new ProctoringViolationLogEntity();
        UUID id = UUID.randomUUID();
        log.setId(id);

        log.prePersist();

        assertThat(log.getId()).isEqualTo(id);
    }

    @Test
    void gettersAndSetters_roundTrip() {
        ProctoringViolationLogEntity log = new ProctoringViolationLogEntity();
        UUID attemptId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        log.setAttemptId(attemptId);
        log.setStudentId(studentId);
        log.setViolationType(ViolationType.TAB_SWITCH);
        log.setViolationOrder(2);
        log.setActionTaken("WARNED");
        log.setDescription("switched tabs");

        assertThat(log.getAttemptId()).isEqualTo(attemptId);
        assertThat(log.getStudentId()).isEqualTo(studentId);
        assertThat(log.getViolationType()).isEqualTo(ViolationType.TAB_SWITCH);
        assertThat(log.getViolationOrder()).isEqualTo(2);
        assertThat(log.getActionTaken()).isEqualTo("WARNED");
        assertThat(log.getDescription()).isEqualTo("switched tabs");
    }
}
