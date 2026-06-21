package project.lms_rikkei_edu.modules.course.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseResponse;
import project.lms_rikkei_edu.modules.course.entity.Course;

@Mapper(componentModel = "spring", uses = {ChapterMapper.class})
public interface CourseMapper {

    @Mapping(target = "category", source = "category")
    CourseResponse toResponse(Course course);

    @Mapping(target = "category",        source = "category")
    @Mapping(target = "chapters",        source = "chapters")
    @Mapping(target = "hasPendingDraft", expression = "java(course.isHasPendingDraft())")
    CourseDetailResponse toDetailResponse(Course course);
}
