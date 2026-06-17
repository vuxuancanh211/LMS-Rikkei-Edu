package project.lms_rikkei_edu.modules.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<UserEntity> findByActivationTokenAndDeletedAtIsNull(String activationToken);
}
