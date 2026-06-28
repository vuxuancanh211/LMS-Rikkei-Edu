package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.quiz.entity.QuizQuestionEntity;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestionEntity, UUID> {

    List<QuizQuestionEntity> findByQuizIdOrderByOrderIndex(UUID quizId);

    long countByQuizId(UUID quizId);

    void deleteByQuizId(UUID quizId);
}
