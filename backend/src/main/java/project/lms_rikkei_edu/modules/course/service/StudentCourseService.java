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

    /** Đánh dấu hoàn thành ngay lập tức 1 lesson (dùng cho lesson loại QUIZ khi đậu bài) — không qua ngưỡng %. */
    void completeQuizLesson(UUID studentId, UUID courseId, UUID lessonId);

    /**
     * Reset lesson_progress của {@code lessonId} cho các học viên đang học dở khóa này
     * (course_progress.status != COMPLETED) — dùng khi giảng viên đổi quiz gắn với lesson.
     * Học viên đã hoàn thành cả khóa không bị ảnh hưởng.
     */
    void resetLessonProgressForInProgressStudents(UUID courseId, UUID lessonId);
}
