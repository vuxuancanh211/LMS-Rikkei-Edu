package project.lms_rikkei_edu.modules.quiz.entity;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.quiz.enums.GenerationStep;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuestionGenerationJobTest {

    @Test
    void prePersist_generatesIdAndDefaultStep_whenUnset() {
        AiQuestionGenerationJob job = new AiQuestionGenerationJob();

        job.prePersist();

        assertThat(job.getId()).isNotNull();
        assertThat(job.getStep()).isEqualTo(GenerationStep.RETRIEVING_CONTEXT);
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isEqualTo(job.getCreatedAt());
    }

    @Test
    void prePersist_preservesExistingIdAndStep() {
        AiQuestionGenerationJob job = new AiQuestionGenerationJob();
        UUID id = UUID.randomUUID();
        job.setId(id);
        job.setStep(GenerationStep.DONE);

        job.prePersist();

        assertThat(job.getId()).isEqualTo(id);
        assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
    }

    @Test
    void preUpdate_refreshesUpdatedAt() {
        AiQuestionGenerationJob job = new AiQuestionGenerationJob();
        job.prePersist();
        var createdAt = job.getCreatedAt();

        job.preUpdate();

        assertThat(job.getUpdatedAt()).isNotNull();
        assertThat(job.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void gettersAndSetters_roundTrip() {
        AiQuestionGenerationJob job = new AiQuestionGenerationJob();
        UUID courseId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();

        job.setCourseId(courseId);
        job.setRequestedBy(requestedBy);
        job.setResultJson("{\"ok\":true}");
        job.setErrorMessage("boom");

        assertThat(job.getCourseId()).isEqualTo(courseId);
        assertThat(job.getRequestedBy()).isEqualTo(requestedBy);
        assertThat(job.getResultJson()).isEqualTo("{\"ok\":true}");
        assertThat(job.getErrorMessage()).isEqualTo("boom");
    }
}
