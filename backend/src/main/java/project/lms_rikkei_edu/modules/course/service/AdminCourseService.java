package project.lms_rikkei_edu.modules.course.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseResponse;
import project.lms_rikkei_edu.modules.course.dto.response.ResourceDownloadUrlResponse;

import java.util.UUID;

public interface AdminCourseService {

    Page<CourseResponse> listPendingCourses(Pageable pageable);

    Page<CourseResponse> listAllCourses(Pageable pageable);

    CourseDetailResponse getCourseDetail(UUID courseId);

    ResourceDownloadUrlResponse getResourceDownloadUrl(UUID resourceId);

    CourseDetailResponse approveCourse(UUID adminId, UUID courseId);

    CourseDetailResponse rejectCourse(UUID adminId, UUID courseId, String reason);

    CourseDetailResponse approveUpdate(UUID adminId, UUID courseId);

    CourseDetailResponse rejectUpdate(UUID adminId, UUID courseId, String reason);
}
