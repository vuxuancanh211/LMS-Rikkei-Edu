package project.lms_rikkei_edu.modules.user.entity;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserEntityTest {

    @Test
    void createAndModifyUser() {
        UserEntity user = new UserEntity();
        UUID id = UUID.randomUUID();
        user.setId(id);
        user.setEmail("test@example.com");
        user.setFullName("Test User");
        user.setPasswordHash("hashed-password");
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setPhoneNumber("0123456789");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender("M");
        user.setBio("A test user");

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getFullName()).isEqualTo("Test User");
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPhoneNumber()).isEqualTo("0123456789");
        assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(user.getGender()).isEqualTo("M");
        assertThat(user.getBio()).isEqualTo("A test user");
    }

    @Test
    void userTimestamps() {
        UserEntity user = new UserEntity();
        OffsetDateTime now = OffsetDateTime.now();

        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeletedAt(null);
        user.setDisabledAt(null);
        user.setLastLoginAt(now);

        assertThat(user.getCreatedAt()).isEqualTo(now);
        assertThat(user.getUpdatedAt()).isEqualTo(now);
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getDisabledAt()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(now);
    }

    @Test
    void userRoleEnumValues() {
        assertThat(UserRole.values()).containsExactly(
                UserRole.ADMIN, UserRole.INSTRUCTOR, UserRole.STUDENT
        );
    }

    @Test
    void userStatusEnumValues() {
        assertThat(UserStatus.values()).containsExactly(
                UserStatus.ACTIVE, UserStatus.PENDING_ACTIVATION,
                UserStatus.DISABLED, UserStatus.DELETED
        );
    }
}
