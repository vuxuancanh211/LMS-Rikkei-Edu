package project.lms_rikkei_edu.modules.assignment.service;

import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentListResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.SubmissionResponse;

import java.util.List;
import java.util.UUID;

public interface StudentAssignmentService {

    List<StudentAssignmentListResponse> getAssignments(UUID courseId, UUID studentId);

    StudentAssignmentDetailResponse getAssignmentDetail(UUID courseId, UUID assignmentId, UUID studentId);

    SubmissionResponse submitAssignment(UUID courseId, UUID assignmentId, UUID studentId, String note, List<MultipartFile> files);
}
