package project.lms_rikkei_edu.modules.user.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSpecificationTest {

    @Mock
    private Root<UserEntity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Predicate predicate;

    @Mock
    private Expression<String> lowerExpr;

    @Test
    void withDynamicQuery_noFilters_addsOnlyDeletedAtNull() {
        when(cb.isNull(any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<UserEntity> spec = UserSpecification.withDynamicQuery(null, null, null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
        verify(cb).isNull(any());
        verify(cb, never()).like(any(), anyString());
        verify(cb, never()).equal(any(), anyString());
        verify(cb, never()).or(any(), any());
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void withDynamicQuery_blankSearch_ignored() {
        when(cb.isNull(any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<UserEntity> spec = UserSpecification.withDynamicQuery("   ", null, null);
        spec.toPredicate(root, query, cb);

        verify(cb, never()).like(any(), anyString());
        verify(cb, never()).or(any(), any());
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void withDynamicQuery_withSearch_addsNameAndEmailLike() {
        when(cb.isNull(any())).thenReturn(predicate);
        when(cb.lower(any())).thenReturn(lowerExpr);
        when(cb.like(any(), anyString())).thenReturn(predicate);
        when(cb.or(any(), any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<UserEntity> spec = UserSpecification.withDynamicQuery("john", null, null);
        spec.toPredicate(root, query, cb);

        verify(cb, times(2)).lower(any());
        verify(cb, times(2)).like(any(), anyString());
        verify(cb).or(any(), any());
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void withDynamicQuery_withRole_addsRoleEqual() {
        when(cb.isNull(any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<UserEntity> spec = UserSpecification.withDynamicQuery(null, "STUDENT", null);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), anyString());
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void withDynamicQuery_withStatus_addsStatusEqual() {
        when(cb.isNull(any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<UserEntity> spec = UserSpecification.withDynamicQuery(null, null, "ACTIVE");
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), anyString());
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void withDynamicQuery_allFilters_combinesPredicates() {
        when(cb.isNull(any())).thenReturn(predicate);
        when(cb.lower(any())).thenReturn(lowerExpr);
        when(cb.like(any(), anyString())).thenReturn(predicate);
        when(cb.or(any(), any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<UserEntity> spec = UserSpecification.withDynamicQuery("john", "STUDENT", "ACTIVE");
        spec.toPredicate(root, query, cb);

        verify(cb, times(2)).lower(any());
        verify(cb, times(2)).like(any(), anyString());
        verify(cb).or(any(), any());
        verify(cb, times(2)).equal(any(), anyString());
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        assertThat(captor.getValue()).hasSize(4);
    }
}
