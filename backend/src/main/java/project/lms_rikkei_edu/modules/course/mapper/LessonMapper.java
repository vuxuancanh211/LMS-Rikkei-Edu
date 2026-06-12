package project.lms_rikkei_edu.modules.course.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResponse;
import project.lms_rikkei_edu.modules.course.entity.Lesson;

@Mapper(componentModel = "spring", uses = {LessonResourceMapper.class})
public interface LessonMapper {

    @Mapping(target = "resources", source = "resources")
    LessonResponse toResponse(Lesson lesson);
}
