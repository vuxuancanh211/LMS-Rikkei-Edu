package project.lms_rikkei_edu.modules.course.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import project.lms_rikkei_edu.modules.course.dto.response.ChapterResponse;
import project.lms_rikkei_edu.modules.course.entity.Chapter;

@Mapper(componentModel = "spring", uses = {LessonMapper.class})
public interface ChapterMapper {

    @Mapping(target = "lessons", source = "lessons")
    ChapterResponse toResponse(Chapter chapter);
}
