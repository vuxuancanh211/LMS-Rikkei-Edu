package project.lms_rikkei_edu.modules.assignment.service;

import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.assignment.dto.request.CreateAssignmentRequest;
import project.lms_rikkei_edu.modules.assignment.dto.request.UpdateAssignmentRequest;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentAttachmentResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentResponse;

import java.util.List;
import java.util.UUID;

public interface AssignmentService {

    AssignmentResponse createAssignment(UUID courseId, UUID instructorId, CreateAssignmentRequest request);

    List<AssignmentResponse> getAssignments(UUID courseId, UUID instructorId);

    List<AssignmentResponse> getAllAssignments(UUID instructorId);

    AssignmentDetailResponse getAssignmentDetail(UUID courseId, UUID assignmentId, UUID instructorId);

    AssignmentResponse updateAssignment(UUID courseId, UUID assignmentId, UUID instructorId, UpdateAssignmentRequest request);

    void deleteAssignment(UUID courseId, UUID assignmentId, UUID instructorId);

    AssignmentResponse publishAssignment(UUID courseId, UUID assignmentId, UUID instructorId);

    AssignmentResponse closeAssignment(UUID courseId, UUID assignmentId, UUID instructorId);

    AssignmentAttachmentResponse uploadAttachment(UUID courseId, UUID assignmentId, UUID instructorId, MultipartFile file);

    void deleteAttachment(UUID courseId, UUID assignmentId, UUID attachmentId, UUID instructorId);
}
