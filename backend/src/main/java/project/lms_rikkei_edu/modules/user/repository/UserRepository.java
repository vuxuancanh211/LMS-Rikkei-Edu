package project.lms_rikkei_edu.modules.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')) AND u.role = :role AND u.deletedAt IS NULL AND u.status <> 'DELETED'")
    List<UserEntity> searchStudentsByEmail(@Param("email") String email, @Param("role") UserRole role);

    Optional<UserEntity> findByActivationTokenAndDeletedAtIsNull(String activationToken);

    long countByRoleAndDeletedAtIsNull(UserRole role);

    long countByStatusAndDeletedAtIsNull(String status);

    long countByDeletedAtIsNull();

    @Query("SELECT u FROM UserEntity u WHERE u.role = :role AND u.deletedAt IS NULL AND u.status <> 'DELETED'")
    List<UserEntity> findByRoleAndNotDeleted(UserRole role);

    Optional<UserEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<UserEntity> findByPhoneNumberAndDeletedAtIsNull(String phoneNumber);

    List<UserEntity> findByEmailIgnoreCaseInAndDeletedAtIsNull(List<String> emails);

}
