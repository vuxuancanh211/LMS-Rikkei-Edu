package project.lms_rikkei_edu.modules.course.service;

import project.lms_rikkei_edu.modules.course.dto.request.UpdateProgressRequest;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.ResourceDownloadUrlResponse;
import project.lms_rikkei_edu.modules.course.dto.response.StudentCourseResponse;

import java.util.List;
import java.util.UUID;

public interface StudentCourseService {

    List<StudentCourseResponse> getEnrolledCourses(UUID studentId);

    CourseDetailResponse getCourseDetail(UUID studentId, UUID courseId);

    ResourceDownloadUrlResponse getResourceViewUrl(UUID studentId, UUID courseId, UUID lessonId, UUID resourceId);

    ResourceDownloadUrlResponse getResourceDownloadUrl(UUID studentId, UUID courseId, UUID lessonId, UUID resourceId);

    void updateLessonProgress(UUID studentId, UUID courseId, UUID lessonId, UpdateProgressRequest request);
}
