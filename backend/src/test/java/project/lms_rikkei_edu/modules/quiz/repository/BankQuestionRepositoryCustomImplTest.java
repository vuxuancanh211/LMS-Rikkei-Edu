package project.lms_rikkei_edu.modules.quiz.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import project.lms_rikkei_edu.modules.quiz.entity.BankQuestionEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankQuestionRepositoryCustomImplTest {

    private EntityManager em;
    private Query query;
    private BankQuestionRepositoryCustomImpl repository;

    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        em = mock(EntityManager.class);
        query = mock(Query.class);
        repository = new BankQuestionRepositoryCustomImpl();
        java.lang.reflect.Field field;
        try {
            field = BankQuestionRepositoryCustomImpl.class.getDeclaredField("em");
            field.setAccessible(true);
            field.set(repository, em);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(em.createNativeQuery(anyString(), eq(BankQuestionEntity.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(java.util.List.of());
    }

    @Test
    void noFilters_buildsBaseQueryWithOnlyCourseId() {
        repository.searchAndFilter(courseId, null, null, null, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sqlCaptor.capture(), eq(BankQuestionEntity.class));
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("WHERE course_id = :courseId");
        assertThat(sql).doesNotContain("AND status")
                .doesNotContain("AND difficulty")
                .doesNotContain("AND subject_tag")
                .doesNotContain("to_tsvector");

        verify(query).setParameter("courseId", courseId);
        verify(query, times(1)).setParameter(anyString(), any());
    }

    @Test
    void blankFilters_areTreatedAsAbsent() {
        repository.searchAndFilter(courseId, "  ", "  ", "  ", "  ");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sqlCaptor.capture(), eq(BankQuestionEntity.class));
        String sql = sqlCaptor.getValue();

        assertThat(sql).doesNotContain("AND status")
                .doesNotContain("AND difficulty")
                .doesNotContain("AND subject_tag")
                .doesNotContain("to_tsvector");

        verify(query).setParameter("courseId", courseId);
        verify(query, times(1)).setParameter(anyString(), any());
    }

    @Test
    void statusFilter_addsStatusClauseAndParameter() {
        repository.searchAndFilter(courseId, null, "ACTIVE", null, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sqlCaptor.capture(), eq(BankQuestionEntity.class));
        assertThat(sqlCaptor.getValue()).contains("AND status = :status");

        verify(query).setParameter("status", "ACTIVE");
    }

    @Test
    void difficultyFilter_addsDifficultyClauseAndParameter() {
        repository.searchAndFilter(courseId, null, null, "EASY", null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sqlCaptor.capture(), eq(BankQuestionEntity.class));
        assertThat(sqlCaptor.getValue()).contains("AND difficulty = :difficulty");

        verify(query).setParameter("difficulty", "EASY");
    }

    @Test
    void subjectTagFilter_addsSubjectTagClauseAndParameter() {
        repository.searchAndFilter(courseId, null, null, null, "Math");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sqlCaptor.capture(), eq(BankQuestionEntity.class));
        assertThat(sqlCaptor.getValue()).contains("AND subject_tag = :subjectTag");

        verify(query).setParameter("subjectTag", "Math");
    }

    @Test
    void queryFilter_addsFullTextSearchClauseWithTrimmedParameter() {
        repository.searchAndFilter(courseId, "  hello world  ", null, null, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sqlCaptor.capture(), eq(BankQuestionEntity.class));
        assertThat(sqlCaptor.getValue())
                .contains("to_tsvector('simple', immutable_unaccent(coalesce(question_text, '')))")
                .contains("websearch_to_tsquery('simple', immutable_unaccent(:query))");

        verify(query).setParameter("query", "hello world");
    }

    @Test
    void allFilters_combineIntoSingleQuery() {
        repository.searchAndFilter(courseId, "topic", "ACTIVE", "HARD", "Physics");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sqlCaptor.capture(), eq(BankQuestionEntity.class));
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("AND status = :status")
                .contains("AND difficulty = :difficulty")
                .contains("AND subject_tag = :subjectTag")
                .contains("to_tsvector");

        verify(query).setParameter("courseId", courseId);
        verify(query).setParameter("status", "ACTIVE");
        verify(query).setParameter("difficulty", "HARD");
        verify(query).setParameter("subjectTag", "Physics");
        verify(query).setParameter("query", "topic");
    }

    @Test
    void returnsResultListFromQuery() {
        BankQuestionEntity entity = new BankQuestionEntity();
        when(query.getResultList()).thenReturn(java.util.List.of(entity));

        var result = repository.searchAndFilter(courseId, null, null, null, null);

        assertThat(result).containsExactly(entity);
    }
}
