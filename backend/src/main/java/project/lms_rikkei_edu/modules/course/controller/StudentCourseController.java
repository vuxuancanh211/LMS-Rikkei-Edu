package project.lms_rikkei_edu.modules.course.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.course.dto.request.UpdateProgressRequest;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.ResourceDownloadUrlResponse;
import project.lms_rikkei_edu.modules.course.dto.response.StudentCourseResponse;
import project.lms_rikkei_edu.modules.course.service.StudentCourseService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/student/courses")
@RequiredArgsConstructor
public class StudentCourseController {

    private final StudentCourseService studentCourseService;
    private final CurrentUserProvider currentUserProvider;

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    @GetMapping
    public ResponseEntity<List<StudentCourseResponse>> getEnrolledCourses() {
        return ResponseEntity.ok(studentCourseService.getEnrolledCourses(currentUserId()));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponse> getCourseDetail(@PathVariable UUID courseId) {
        return ResponseEntity.ok(studentCourseService.getCourseDetail(currentUserId(), courseId));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/resources/{resourceId}/view-url")
    public ResponseEntity<ResourceDownloadUrlResponse> getResourceViewUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId) {
        return ResponseEntity.ok(
                studentCourseService.getResourceViewUrl(currentUserId(), courseId, lessonId, resourceId));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/resources/{resourceId}/download-url")
    public ResponseEntity<ResourceDownloadUrlResponse> getResourceDownloadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId) {
        return ResponseEntity.ok(
                studentCourseService.getResourceDownloadUrl(currentUserId(), courseId, lessonId, resourceId));
    }

    @PostMapping("/{courseId}/lessons/{lessonId}/progress")
    public ResponseEntity<Void> updateLessonProgress(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestBody UpdateProgressRequest request) {
        studentCourseService.updateLessonProgress(currentUserId(), courseId, lessonId, request);
        return ResponseEntity.ok().build();
    }
}
