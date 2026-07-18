package project.lms_rikkei_edu.modules.assignment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentListResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.SubmissionResponse;
import project.lms_rikkei_edu.modules.assignment.service.StudentAssignmentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentAssignmentController {

    private final StudentAssignmentService studentAssignmentService;
    private final CurrentUserProvider currentUserProvider;

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/api/student/assignments")
    public ResponseEntity<List<StudentAssignmentListResponse>> getAllAssignments() {
        return ResponseEntity.ok(studentAssignmentService.getAllAssignments(currentUserId()));
    }

    @GetMapping("/api/student/courses/{courseId}/assignments")
    public ResponseEntity<List<StudentAssignmentListResponse>> getAssignments(@PathVariable UUID courseId) {
        return ResponseEntity.ok(studentAssignmentService.getAssignments(courseId, currentUserId()));
    }

    @GetMapping("/api/student/courses/{courseId}/assignments/{assignmentId}")
    public ResponseEntity<StudentAssignmentDetailResponse> getAssignmentDetail(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(studentAssignmentService.getAssignmentDetail(courseId, assignmentId, currentUserId()));
    }

    @PostMapping("/api/student/courses/{courseId}/assignments/{assignmentId}/submit")
    public ResponseEntity<SubmissionResponse> submitAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "keepFileIds", required = false) List<UUID> keepFileIds) {
        return ResponseEntity.ok(studentAssignmentService.submitAssignment(courseId, assignmentId, currentUserId(), note, files, keepFileIds));
    }
}
