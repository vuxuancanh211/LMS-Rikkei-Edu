package project.lms_rikkei_edu.modules.course.mapper;

import org.mapstruct.Mapper;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;

@Mapper(componentModel = "spring")
public interface LessonResourceMapper {

    LessonResourceResponse toResponse(LessonResource resource);
}
