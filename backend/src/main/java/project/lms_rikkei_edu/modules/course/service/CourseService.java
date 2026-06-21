package project.lms_rikkei_edu.modules.course.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;

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
}
