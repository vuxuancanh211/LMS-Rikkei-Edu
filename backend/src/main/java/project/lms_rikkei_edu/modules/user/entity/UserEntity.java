package project.lms_rikkei_edu.modules.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(length = 255)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 10)
    private String gender;

    @Column(length = 500)
    private String bio;

    @Column(name = "activation_token", length = 255)
    private String activationToken;

    @Column(name = "activation_token_expires_at")
    private OffsetDateTime activationTokenExpiresAt;

    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "disabled_at")
    private OffsetDateTime disabledAt;

    @Column(name = "disabled_by")
    private UUID disabledBy;

    @Column(name = "disabled_reason", length = 500)
    private String disabledReason;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
