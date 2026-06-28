package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.quiz.entity.QuizOptionEntity;

import java.util.List;
import java.util.UUID;

public interface QuizOptionRepository extends JpaRepository<QuizOptionEntity, UUID> {

    List<QuizOptionEntity> findByQuestionIdOrderByOrderIndex(UUID questionId);

    void deleteByQuestionId(UUID questionId);
}
