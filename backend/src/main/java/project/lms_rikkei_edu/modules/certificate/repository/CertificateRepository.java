package project.lms_rikkei_edu.modules.certificate.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.certificate.entity.CertificateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<CertificateEntity, UUID>, JpaSpecificationExecutor<CertificateEntity> {

    @EntityGraph(attributePaths = {"student", "course", "revokedByUser"})
    Optional<CertificateEntity> findByCredentialId(String credentialId);

    Optional<CertificateEntity> findByIdAndStudentId(UUID id, UUID studentId);

    @EntityGraph(attributePaths = {"student", "course", "revokedByUser"})
    Optional<CertificateEntity> findWithRelationsByIdAndStudentId(UUID id, UUID studentId);

    @EntityGraph(attributePaths = {"student", "course", "revokedByUser"})
    List<CertificateEntity> findAllByStudentIdOrderByIssuedAtDesc(UUID studentId);

    @EntityGraph(attributePaths = {"student", "course", "revokedByUser"})
    List<CertificateEntity> findAllByOrderByIssuedAtDesc();

    @EntityGraph(attributePaths = {"student", "course", "revokedByUser"})
    @Query("SELECT c FROM CertificateEntity c WHERE c.id = :id")
    Optional<CertificateEntity> findWithRelationsById(@Param("id") UUID id);

    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

    boolean existsByCredentialId(String credentialId);
}
