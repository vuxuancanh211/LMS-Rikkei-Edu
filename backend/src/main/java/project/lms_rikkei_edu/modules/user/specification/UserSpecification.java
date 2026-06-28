package project.lms_rikkei_edu.modules.user.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    private UserSpecification() {}

    public static Specification<UserEntity> withDynamicQuery(String search, String role, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isNull(root.get("deletedAt")));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate emailLike = cb.like(cb.lower(root.get("email")), pattern);
                Predicate nameLike = cb.like(cb.lower(root.get("fullName")), pattern);
                predicates.add(cb.or(emailLike, nameLike));
            }

            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(root.get("role"), role.toUpperCase()));
            }

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.toUpperCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
