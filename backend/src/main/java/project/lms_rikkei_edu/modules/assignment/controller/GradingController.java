package project.lms_rikkei_edu.modules.assignment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.assignment.dto.request.BatchReleaseRequest;
import project.lms_rikkei_edu.modules.assignment.dto.request.GradeRequest;
import project.lms_rikkei_edu.modules.assignment.dto.response.InstructorSubmissionResponse;
import project.lms_rikkei_edu.modules.assignment.service.GradingService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instructor")
@RequiredArgsConstructor
public class GradingController {

    private final GradingService gradingService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/submissions")
    public ResponseEntity<List<InstructorSubmissionResponse>> getSubmissions(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID assignmentId,
            @RequestParam(required = false, defaultValue = "ALL") String status) {
        return ResponseEntity.ok(gradingService.getSubmissions(courseId, assignmentId, currentUserId(), status));
    }

    @PatchMapping("/submissions/grade")
    public ResponseEntity<InstructorSubmissionResponse> gradeSubmission(
            @Valid @RequestBody GradeRequest request) {
        return ResponseEntity.ok(gradingService.gradeSubmission(request, currentUserId()));
    }

    @PatchMapping("/submissions/batch/release")
    public ResponseEntity<Void> batchReleaseScores(
            @Valid @RequestBody BatchReleaseRequest request) {
        gradingService.batchReleaseScores(request, currentUserId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/submissions/{submissionId}/return")
    public ResponseEntity<Void> returnSubmission(@PathVariable UUID submissionId) {
        gradingService.returnSubmission(submissionId, currentUserId());
        return ResponseEntity.ok().build();
    }

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }
}
