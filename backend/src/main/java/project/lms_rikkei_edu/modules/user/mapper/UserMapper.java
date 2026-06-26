package project.lms_rikkei_edu.modules.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import project.lms_rikkei_edu.modules.user.dto.response.AdminUserDetailResponse;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", expression = "java(user.getRole() == null ? null : user.getRole().name())")
    @Mapping(target = "status", expression = "java(user.getStatus() == null ? null : user.getStatus().name())")
    UserResponse toResponse(UserEntity user);

    @Mapping(target = "role", expression = "java(user.getRole() == null ? null : user.getRole().name())")
    @Mapping(target = "status", expression = "java(user.getStatus() == null ? null : user.getStatus().name())")
    AdminUserDetailResponse toAdminDetailResponse(UserEntity user);
}
