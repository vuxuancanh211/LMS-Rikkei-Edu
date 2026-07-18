package project.lms_rikkei_edu.modules.course.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseResponse;
import project.lms_rikkei_edu.modules.course.entity.Course;

/* instructorName/instructorBio/instructorCourseCount/studentCount/completedAssignments/
   totalAssignments không có field nguồn tương ứng trên Course entity — luôn được set tay ở
   service sau khi map (xem CourseServiceImpl/StudentCourseServiceImpl/AdminCourseServiceImpl),
   nên khai báo ignore = true tường minh ở đây để MapStruct hết cảnh báo "unmapped target
   property" (không phải lỗi, chỉ là khai báo rõ ý định thay vì im lặng dựa vào default warning). */
@Mapper(componentModel = "spring", uses = {ChapterMapper.class})
public interface CourseMapper {

    @Mapping(target = "category", source = "category")
    @Mapping(target = "instructorName", ignore = true)
    @Mapping(target = "studentCount", ignore = true)
    CourseResponse toResponse(Course course);

    @Mapping(target = "category",        source = "category")
    @Mapping(target = "chapters",        source = "chapters")
    @Mapping(target = "hasPendingDraft", expression = "java(course.isHasPendingDraft())")
    @Mapping(target = "instructorName", ignore = true)
    @Mapping(target = "instructorBio", ignore = true)
    @Mapping(target = "instructorCourseCount", ignore = true)
    @Mapping(target = "studentCount", ignore = true)
    @Mapping(target = "completedAssignments", ignore = true)
    @Mapping(target = "totalAssignments", ignore = true)
    CourseDetailResponse toDetailResponse(Course course);
}
