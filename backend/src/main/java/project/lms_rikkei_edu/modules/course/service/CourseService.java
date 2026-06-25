package project.lms_rikkei_edu.modules.course.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;

import java.util.List;

import java.util.UUID;

public interface CourseService {

    CourseResponse createCourse(UUID instructorId, CreateCourseRequest request);

    CourseDetailResponse getCourseDetail(UUID instructorId, UUID courseId);

    Page<CourseResponse> listCourses(UUID instructorId, Pageable pageable);

    CourseResponse updateCourse(UUID instructorId, UUID courseId, UpdateCourseRequest request);

    void deleteCourse(UUID instructorId, UUID courseId);

    CourseDetailResponse submitForApproval(UUID instructorId, UUID courseId, String changeSummary);

    CourseDetailResponse withdrawFromReview(UUID instructorId, UUID courseId);

    ChapterResponse addChapter(UUID instructorId, UUID courseId, CreateChapterRequest request);

    ChapterResponse updateChapter(UUID instructorId, UUID courseId, UUID chapterId, UpdateChapterRequest request);

    void deleteChapter(UUID instructorId, UUID courseId, UUID chapterId);

    LessonResponse addLesson(UUID instructorId, UUID courseId, UUID chapterId, CreateLessonRequest request);

    LessonResponse updateLesson(UUID instructorId, UUID courseId, UUID chapterId, UUID lessonId, UpdateLessonRequest request);

    void deleteLesson(UUID instructorId, UUID courseId, UUID chapterId, UUID lessonId);

    List<CourseApprovalLogResponse> getCourseHistory(UUID instructorId, UUID courseId);

    List<CourseVersionResponse> getCourseVersions(UUID instructorId, UUID courseId);

    CourseDetailResponse rollbackToVersion(UUID instructorId, UUID courseId, UUID versionId);

    CourseVersionResponse saveDraft(UUID instructorId, UUID courseId, String label);

    void renameDraftVersion(UUID instructorId, UUID courseId, UUID versionId, String label);

    /** Xóa cứng bản nháp DRAFT hoặc REJECTED (kèm cleanup S3). */
    void deleteDraftVersion(UUID instructorId, UUID courseId, UUID versionId);

    CourseVersionResponse cloneVersionAsDraft(UUID instructorId, UUID courseId, UUID sourceVersionId, String label);

    /** Nộp một DRAFT version cụ thể để admin duyệt. Nếu đang có PENDING, revert nó về DRAFT trước. */
    CourseVersionResponse submitVersion(UUID instructorId, UUID courseId, UUID versionId);

    /** Kiểm tra xem có version nào đang PENDING không. */
    boolean hasPendingVersion(UUID instructorId, UUID courseId);
}
