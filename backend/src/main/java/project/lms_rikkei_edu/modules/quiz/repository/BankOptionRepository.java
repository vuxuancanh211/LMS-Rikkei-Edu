package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.quiz.entity.BankOptionEntity;

import java.util.List;
import java.util.UUID;

public interface BankOptionRepository extends JpaRepository<BankOptionEntity, UUID> {

    List<BankOptionEntity> findByBankQuestionIdOrderByOrderIndex(UUID bankQuestionId);

    void deleteByBankQuestionId(UUID bankQuestionId);
}
