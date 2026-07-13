package project.lms_rikkei_edu.modules.assignment.service;

import project.lms_rikkei_edu.modules.assignment.dto.request.BatchReleaseRequest;
import project.lms_rikkei_edu.modules.assignment.dto.request.GradeRequest;
import project.lms_rikkei_edu.modules.assignment.dto.response.InstructorSubmissionResponse;

import java.util.List;
import java.util.UUID;

public interface GradingService {

    List<InstructorSubmissionResponse> getSubmissions(UUID courseId, UUID assignmentId, UUID instructorId, String statusFilter);

    InstructorSubmissionResponse gradeSubmission(GradeRequest request, UUID instructorId);

    void batchReleaseScores(BatchReleaseRequest request, UUID instructorId);

    void returnSubmission(UUID submissionId, UUID instructorId);
}
