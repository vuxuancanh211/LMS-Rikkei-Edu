package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.quiz.entity.QuizEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<QuizEntity, UUID> {

    List<QuizEntity> findByCourseIdAndStatus(UUID courseId, QuizStatus status);

    List<QuizEntity> findByCourseId(UUID courseId);

    Optional<QuizEntity> findByIdAndCourseId(UUID id, UUID courseId);

    // Tìm quiz đã hết end_date và vẫn đang PUBLISHED — để auto-archive
    List<QuizEntity> findByStatusAndEndDateBefore(QuizStatus status, OffsetDateTime now);
}
