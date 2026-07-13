package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.quiz.entity.ProctoringViolationLogEntity;

import java.util.List;
import java.util.UUID;

public interface ProctoringViolationLogRepository extends JpaRepository<ProctoringViolationLogEntity, UUID> {

    List<ProctoringViolationLogEntity> findByAttemptIdOrderByViolationOrder(UUID attemptId);

    long countByAttemptId(UUID attemptId);

    void deleteByAttemptIdIn(List<UUID> attemptIds);
}
