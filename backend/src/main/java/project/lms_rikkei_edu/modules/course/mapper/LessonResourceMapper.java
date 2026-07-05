package project.lms_rikkei_edu.modules.course.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;

@Mapper(componentModel = "spring")
public interface LessonResourceMapper {

    @Mapping(target = "externalUrl", expression = "java(resource.getS3Key() != null && resource.getS3Key().startsWith(\"ext://\") ? resource.getS3Key().substring(6) : null)")
    LessonResourceResponse toResponse(LessonResource resource);
}
