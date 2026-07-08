package project.lms_rikkei_edu.modules.assignment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.assignment.dto.request.CreateAssignmentRequest;
import project.lms_rikkei_edu.modules.assignment.dto.request.UpdateAssignmentRequest;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentAttachmentResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentResponse;
import project.lms_rikkei_edu.modules.assignment.service.AssignmentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instructor/courses/{courseId}/assignments")
@RequiredArgsConstructor
public class InstructorAssignmentController {

    private final AssignmentService assignmentService;
    private final CurrentUserProvider currentUserProvider;

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/api/instructor/assignments")
    public ResponseEntity<List<AssignmentResponse>> getAllAssignments() {
        return ResponseEntity.ok(assignmentService.getAllAssignments(currentUserId()));
    }

    @PostMapping
    public ResponseEntity<AssignmentResponse> createAssignment(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.createAssignment(courseId, currentUserId(), request));
    }

    @GetMapping
    public ResponseEntity<List<AssignmentResponse>> getAssignments(
            @PathVariable UUID courseId) {
        return ResponseEntity.ok(assignmentService.getAssignments(courseId, currentUserId()));
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<AssignmentDetailResponse> getAssignmentDetail(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(assignmentService.getAssignmentDetail(courseId, assignmentId, currentUserId()));
    }

    @PutMapping("/{assignmentId}")
    public ResponseEntity<AssignmentResponse> updateAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody UpdateAssignmentRequest request) {
        return ResponseEntity.ok(
                assignmentService.updateAssignment(courseId, assignmentId, currentUserId(), request));
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId) {
        assignmentService.deleteAssignment(courseId, assignmentId, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{assignmentId}/publish")
    public ResponseEntity<AssignmentResponse> publishAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(assignmentService.publishAssignment(courseId, assignmentId, currentUserId()));
    }

    @PutMapping("/{assignmentId}/close")
    public ResponseEntity<AssignmentResponse> closeAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(assignmentService.closeAssignment(courseId, assignmentId, currentUserId()));
    }

    @PostMapping(value = "/{assignmentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssignmentAttachmentResponse> uploadAttachment(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.uploadAttachment(courseId, assignmentId, currentUserId(), file));
    }

    @DeleteMapping("/{assignmentId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @PathVariable UUID attachmentId) {
        assignmentService.deleteAttachment(courseId, assignmentId, attachmentId, currentUserId());
        return ResponseEntity.noContent().build();
    }
}
