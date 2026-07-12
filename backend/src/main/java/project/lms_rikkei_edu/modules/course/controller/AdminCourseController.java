package project.lms_rikkei_edu.modules.course.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.course.dto.request.CourseRejectRequest;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.service.AdminCourseService;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private final AdminCourseService adminCourseService;
    private final CurrentUserProvider currentUserProvider;

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<CourseResponse>> listPending(Pageable pageable) {
        return ResponseEntity.ok(adminCourseService.listPendingCourses(pageable));
    }

    @GetMapping
    public ResponseEntity<Page<CourseResponse>> listAll(
            Pageable pageable,
            @RequestParam(required = false) CourseStatus status) {
        return ResponseEntity.ok(adminCourseService.listAllCourses(pageable, status));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponse> getCourseDetail(@PathVariable UUID courseId) {
        return ResponseEntity.ok(adminCourseService.getCourseDetail(courseId));
    }

    @GetMapping("/resources/{resourceId}/download-url")
    public ResponseEntity<ResourceDownloadUrlResponse> getResourceDownloadUrl(@PathVariable UUID resourceId) {
        return ResponseEntity.ok(adminCourseService.getResourceDownloadUrl(resourceId));
    }

    @PostMapping("/{courseId}/approve")
    public ResponseEntity<CourseDetailResponse> approve(@PathVariable UUID courseId) {
        return ResponseEntity.ok(adminCourseService.approveCourse(currentUserId(), courseId));
    }

    @PostMapping("/{courseId}/reject")
    public ResponseEntity<CourseDetailResponse> reject(
            @PathVariable UUID courseId,
            @Valid @RequestBody CourseRejectRequest request) {
        return ResponseEntity.ok(adminCourseService.rejectCourse(currentUserId(), courseId, request.getReason()));
    }

    @PostMapping("/{courseId}/approve-update")
    public ResponseEntity<CourseDetailResponse> approveUpdate(@PathVariable UUID courseId) {
        return ResponseEntity.ok(adminCourseService.approveUpdate(currentUserId(), courseId));
    }

    @PostMapping("/{courseId}/reject-update")
    public ResponseEntity<CourseDetailResponse> rejectUpdate(
            @PathVariable UUID courseId,
            @Valid @RequestBody CourseRejectRequest request) {
        return ResponseEntity.ok(adminCourseService.rejectUpdate(currentUserId(), courseId, request.getReason()));
    }

    @GetMapping("/{courseId}/versions/diff")
    public ResponseEntity<CourseDiffResponse> getVersionDiff(@PathVariable UUID courseId) {
        return ResponseEntity.ok(adminCourseService.getVersionDiff(courseId));
    }
}
