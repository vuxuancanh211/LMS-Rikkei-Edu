package project.lms_rikkei_edu.modules.course.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.auth.InstructorContext;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.service.CourseService;
import project.lms_rikkei_edu.modules.course.service.LessonResourceService;

import java.util.List;

import java.util.UUID;

@RestController
@RequestMapping("/api/instructor/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final LessonResourceService lessonResourceService;
    private final InstructorContext instructorContext;

    // ── Course ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.createCourse(instructorId, request));
    }

    @GetMapping
    public ResponseEntity<Page<CourseResponse>> listCourses(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.ok(courseService.listCourses(instructorId, pageable));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponse> getCourseDetail(
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.ok(courseService.getCourseDetail(instructorId, courseId));
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<CourseResponse> updateCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.ok(courseService.updateCourse(instructorId, courseId, request));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> deleteCourse(
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        courseService.deleteCourse(instructorId, courseId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{courseId}/submit")
    public ResponseEntity<CourseDetailResponse> submitForApproval(
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.ok(courseService.submitForApproval(instructorId, courseId));
    }

    // ── Chapter ───────────────────────────────────────────────────────────────

    @PostMapping("/{courseId}/chapters")
    public ResponseEntity<ChapterResponse> addChapter(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateChapterRequest request,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.addChapter(instructorId, courseId, request));
    }

    @PutMapping("/{courseId}/chapters/{chapterId}")
    public ResponseEntity<ChapterResponse> updateChapter(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId,
            @Valid @RequestBody UpdateChapterRequest request,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.ok(courseService.updateChapter(instructorId, courseId, chapterId, request));
    }

    @DeleteMapping("/{courseId}/chapters/{chapterId}")
    public ResponseEntity<Void> deleteChapter(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        courseService.deleteChapter(instructorId, courseId, chapterId);
        return ResponseEntity.noContent().build();
    }

    // ── Lesson ────────────────────────────────────────────────────────────────

    @PostMapping("/{courseId}/chapters/{chapterId}/lessons")
    public ResponseEntity<LessonResponse> addLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId,
            @Valid @RequestBody CreateLessonRequest request,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.addLesson(instructorId, courseId, chapterId, request));
    }

    @PutMapping("/{courseId}/chapters/{chapterId}/lessons/{lessonId}")
    public ResponseEntity<LessonResponse> updateLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody UpdateLessonRequest request,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.ok(courseService.updateLesson(instructorId, courseId, chapterId, lessonId, request));
    }

    @DeleteMapping("/{courseId}/chapters/{chapterId}/lessons/{lessonId}")
    public ResponseEntity<Void> deleteLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId,
            @PathVariable UUID lessonId,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        courseService.deleteLesson(instructorId, courseId, chapterId, lessonId);
        return ResponseEntity.noContent().build();
    }

    // ── Lesson Resource (Upload tài liệu lên S3) ──────────────────────────────

    @PostMapping("/{courseId}/lessons/{lessonId}/resources/presign-upload")
    public ResponseEntity<ResourceUploadPresignResponse> requestUploadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody ResourceUploadPresignRequest request,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.ok(lessonResourceService.requestUploadUrl(instructorId, courseId, lessonId, request));
    }

    @PostMapping("/{courseId}/lessons/{lessonId}/resources/confirm-upload")
    public ResponseEntity<LessonResourceResponse> confirmUpload(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody ResourceConfirmUploadRequest request,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lessonResourceService.confirmUpload(instructorId, courseId, lessonId, request));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/resources")
    public ResponseEntity<List<LessonResourceResponse>> listResources(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.ok(lessonResourceService.listResources(instructorId, courseId, lessonId));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/resources/{resourceId}/download-url")
    public ResponseEntity<ResourceDownloadUrlResponse> getDownloadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        return ResponseEntity.ok(lessonResourceService.getDownloadUrl(instructorId, courseId, lessonId, resourceId));
    }

    @DeleteMapping("/{courseId}/lessons/{lessonId}/resources/{resourceId}")
    public ResponseEntity<Void> deleteResource(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId,
            HttpServletRequest httpRequest) {
        UUID instructorId = instructorContext.getCurrentInstructorId(httpRequest);
        lessonResourceService.deleteResource(instructorId, courseId, lessonId, resourceId);
        return ResponseEntity.noContent().build();
    }
}
