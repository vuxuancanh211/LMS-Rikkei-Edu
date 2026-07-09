package project.lms_rikkei_edu.modules.course.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResponse;
import project.lms_rikkei_edu.modules.course.entity.Lesson;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class LessonMapper {

    @Autowired
    protected LessonResourceMapper lessonResourceMapper;

    @Mapping(target = "resources", expression = "java(mapResources(lesson))")
    @Mapping(target = "progress", ignore = true)
    @Mapping(target = "progressPercentage", ignore = true)
    public abstract LessonResponse toResponse(Lesson lesson);

    protected List<LessonResourceResponse> mapResources(Lesson lesson) {
        if (lesson.getResources() == null) return List.of();
        return lesson.getResources().stream()
                .filter(r -> r.getDeletedAt() == null)
                .map(lessonResourceMapper::toResponse)
                .toList();
    }
}
