package project.lms_rikkei_edu.modules.user.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void toResponseMapsFields() {
        UserEntity entity = new UserEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setEmail("test@example.com");
        entity.setFullName("Test User");
        entity.setRole(UserRole.INSTRUCTOR);
        entity.setStatus(UserStatus.ACTIVE);

        UserResponse response = mapper.toResponse(entity);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getFullName()).isEqualTo("Test User");
        assertThat(response.getRole()).isEqualTo("INSTRUCTOR");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void toResponseHandlesNullRoleAndStatus() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setEmail("null@example.com");
        entity.setFullName("Null Fields");
        entity.setRole(null);
        entity.setStatus(null);

        UserResponse response = mapper.toResponse(entity);

        assertThat(response.getRole()).isNull();
        assertThat(response.getStatus()).isNull();
    }
}
