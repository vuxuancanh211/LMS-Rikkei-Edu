package project.lms_rikkei_edu.modules.course.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.modules.course.dto.request.CourseRejectRequest;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseResponse;
import project.lms_rikkei_edu.modules.course.service.AdminCourseService;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private final AdminCourseService adminCourseService;

    @GetMapping("/pending")
    public ResponseEntity<Page<CourseResponse>> listPending(Pageable pageable) {
        return ResponseEntity.ok(adminCourseService.listPendingCourses(pageable));
    }

    @PostMapping("/{courseId}/approve")
    public ResponseEntity<CourseDetailResponse> approve(
            @RequestHeader("X-User-Id") UUID adminId,
            @PathVariable UUID courseId) {

        return ResponseEntity.ok(adminCourseService.approveCourse(adminId, courseId));
    }

    @PostMapping("/{courseId}/reject")
    public ResponseEntity<CourseDetailResponse> reject(
            @RequestHeader("X-User-Id") UUID adminId,
            @PathVariable UUID courseId,
            @Valid @RequestBody CourseRejectRequest request) {
        return ResponseEntity.ok(adminCourseService.rejectCourse(adminId, courseId, request.getReason()));
    }

    @PostMapping("/{courseId}/approve-update")
    public ResponseEntity<CourseDetailResponse> approveUpdate(
            @RequestHeader("X-User-Id") UUID adminId,
            @PathVariable UUID courseId) {
        return ResponseEntity.ok(adminCourseService.approveUpdate(adminId, courseId));
    }

    @PostMapping("/{courseId}/reject-update")
    public ResponseEntity<CourseDetailResponse> rejectUpdate(
            @RequestHeader("X-User-Id") UUID adminId,
            @PathVariable UUID courseId,
            @Valid @RequestBody CourseRejectRequest request) {
        return ResponseEntity.ok(adminCourseService.rejectUpdate(adminId, courseId, request.getReason()));
    }
}
