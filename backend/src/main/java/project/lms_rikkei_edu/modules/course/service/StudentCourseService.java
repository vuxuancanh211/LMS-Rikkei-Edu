package project.lms_rikkei_edu.modules.course.service;

import org.springframework.transaction.annotation.Transactional;
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

    /**
     * Reset toàn bộ tiến độ (course_progress, lesson_progress, quiz_attempts) cho danh sách học viên trong khóa học.
     * Được tự động gọi khi học viên được add/đăng ký vào khóa học để đảm bảo reset tiến độ học tập.
     */
    void resetProgressForStudents(UUID courseId, List<UUID> studentIds);

    void resetStudentCourseProgress(UUID courseId, UUID studentId);

    /**
     * Tính lại tiến độ course cho student (lesson + assignment) trong cùng transaction hiện tại.
     * Dùng khi publish assignment — cần thấy được dữ liệu chưa commit.
     */
    void recalculateCourseProgress(UUID studentId, UUID courseId);

    /**
     * Tính lại tiến độ course cho student.
     * Được gọi từ GradingServiceImpl khi giảng viên công bố điểm.
     */
    @Transactional
    void updateAssignmentProgress(UUID studentId, UUID courseId);
}
