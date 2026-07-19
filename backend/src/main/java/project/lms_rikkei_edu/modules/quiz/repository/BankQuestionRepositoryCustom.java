package project.lms_rikkei_edu.modules.quiz.repository;

import project.lms_rikkei_edu.modules.quiz.entity.BankQuestionEntity;
import java.util.List;
import java.util.UUID;

public interface BankQuestionRepositoryCustom {
    List<BankQuestionEntity> searchAndFilter(
            UUID courseId,
            String query,
            String status,
            String difficulty,
            String subjectTag
    );
}
