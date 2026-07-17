package project.lms_rikkei_edu.modules.quiz.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import project.lms_rikkei_edu.modules.quiz.entity.BankQuestionEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class BankQuestionRepositoryCustomImpl implements BankQuestionRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<BankQuestionEntity> searchAndFilter(UUID courseId, String query, String status, String difficulty, String subjectTag) {
        StringBuilder sql = new StringBuilder("SELECT * FROM bank_questions WHERE course_id = :courseId");
        Map<String, Object> params = new HashMap<>();
        params.put("courseId", courseId);

        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status = :status");
            params.put("status", status);
        }

        if (difficulty != null && !difficulty.trim().isEmpty()) {
            sql.append(" AND difficulty = :difficulty");
            params.put("difficulty", difficulty);
        }

        if (subjectTag != null && !subjectTag.trim().isEmpty()) {
            sql.append(" AND subject_tag = :subjectTag");
            params.put("subjectTag", subjectTag);
        }

        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND to_tsvector('simple', immutable_unaccent(coalesce(question_text, ''))) @@ websearch_to_tsquery('simple', immutable_unaccent(:query))");
            params.put("query", query.trim());
        }

        Query nativeQuery = em.createNativeQuery(sql.toString(), BankQuestionEntity.class);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            nativeQuery.setParameter(entry.getKey(), entry.getValue());
        }

        return nativeQuery.getResultList();
    }
}
