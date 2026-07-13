package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.quiz.entity.AiQuestionGenerationJob;

import java.util.UUID;

public interface AiQuestionGenerationJobRepository extends JpaRepository<AiQuestionGenerationJob, UUID> {
}
