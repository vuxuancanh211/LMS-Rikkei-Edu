package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.quiz.entity.QuizOptionEntity;

import java.util.List;
import java.util.UUID;

public interface QuizOptionRepository extends JpaRepository<QuizOptionEntity, UUID> {

    List<QuizOptionEntity> findByQuestionIdOrderByOrderIndex(UUID questionId);

    // Batch — tải option cho nhiều câu hỏi trong 1 query, tránh N+1 khi build câu hỏi/chấm điểm
    // cho cả 1 lượt thi (xem QuizAttemptServiceImpl).
    List<QuizOptionEntity> findByQuestionIdInOrderByOrderIndex(List<UUID> questionIds);

    void deleteByQuestionId(UUID questionId);
}
