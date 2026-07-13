package project.lms_rikkei_edu.modules.assignment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.assignment.dto.request.CreateAssignmentRequest;
import project.lms_rikkei_edu.modules.assignment.dto.request.UpdateAssignmentRequest;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentAttachmentResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentResponse;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentAttachmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentGroupEntity;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;
import project.lms_rikkei_edu.modules.assignment.exception.AssignmentNotFoundException;
import project.lms_rikkei_edu.modules.assignment.mapper.AssignmentMapper;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentAttachmentRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentGroupRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentRepository;
import project.lms_rikkei_edu.modules.assignment.service.AssignmentService;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.group.repository.StudyGroupRepository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentGroupRepository assignmentGroupRepository;
    private final AssignmentAttachmentRepository assignmentAttachmentRepository;
    private final CourseRepository courseRepository;
    private final AssignmentMapper assignmentMapper;
    private final StudyGroupRepository studyGroupRepository;
    private final S3Client s3Client;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.presigned-url-expiry:3600}")
    private long presignedUrlExpiry;

    @Override
    @Transactional
    public AssignmentResponse createAssignment(UUID courseId, UUID instructorId, CreateAssignmentRequest request) {
        validateCourseOwnership(courseId, instructorId);
        validateCoursePublished(courseId);

        if (request.getScope() == AssignmentScope.SPECIFIC_GROUPS
                && (request.getGroupIds() == null || request.getGroupIds().isEmpty())) {
            throw new BusinessException("Phải chọn ít nhất 1 nhóm khi scope=SPECIFIC_GROUPS");
        }

        validateDates(request.getStartDate(), request.getDeadline());

        if (Boolean.TRUE.equals(request.getAllowLateSubmission())
                && request.getLatePenaltyPercent() != null
                && (request.getLatePenaltyPercent() < 0 || request.getLatePenaltyPercent() > 100)) {
            throw new BusinessException("Late penalty percent phải từ 0-100");
        }

        UUID assignmentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setCourseId(courseId);
        assignment.setCreatedBy(instructorId);
        assignment.setTitle(request.getTitle().trim());
        assignment.setDescription(request.getDescription());
        assignment.setStatus(AssignmentStatus.DRAFT);
        assignment.setScope(request.getScope());
        assignment.setDeadline(request.getDeadline());
        assignment.setStartDate(request.getStartDate());
        assignment.setAllowLateSubmission(request.getAllowLateSubmission() != null && request.getAllowLateSubmission());
        assignment.setLatePenaltyPercent(request.getLatePenaltyPercent() != null ? request.getLatePenaltyPercent() : 0);
        assignment.setMaxScore(request.getMaxScore());
        assignment.setMaxFileSizeMb(request.getMaxFileSizeMb());
        assignment.setAllowedFileTypes(toJsonArray(request.getAllowedFileTypes()));
        assignment.setMaxSubmissions(request.getMaxSubmissions() != null ? request.getMaxSubmissions() : 1);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        assignmentRepository.save(assignment);

        if (request.getScope() == AssignmentScope.SPECIFIC_GROUPS && request.getGroupIds() != null) {
            for (UUID groupId : request.getGroupIds()) {
                AssignmentGroupEntity ag = new AssignmentGroupEntity();
                ag.setId(UUID.randomUUID());
                ag.setAssignmentId(assignmentId);
                ag.setGroupId(groupId);
                ag.setAssignedAt(now);
                assignmentGroupRepository.save(ag);
            }
        }

        log.info("Created assignment {} for course {} by instructor {}", assignmentId, courseId, instructorId);
        return assignmentMapper.toResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignments(UUID courseId, UUID instructorId) {
        validateCourseOwnership(courseId, instructorId);

        List<AssignmentEntity> assignments = assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
        return assignments.stream()
                .map(a -> {
                    AssignmentResponse resp = assignmentMapper.toResponse(a);
                    long count = assignmentAttachmentRepository.countByAssignmentId(a.getId());
                    return AssignmentResponse.builder()
                            .id(resp.getId())
                            .courseId(resp.getCourseId())
                            .createdBy(resp.getCreatedBy())
                            .title(resp.getTitle())
                            .description(resp.getDescription())
                            .status(resp.getStatus())
                            .scope(resp.getScope())
                            .deadline(resp.getDeadline())
                            .startDate(resp.getStartDate())
                            .allowLateSubmission(resp.getAllowLateSubmission())
                            .latePenaltyPercent(resp.getLatePenaltyPercent())
                            .maxScore(resp.getMaxScore())
                            .maxFileSizeMb(resp.getMaxFileSizeMb())
                            .allowedFileTypes(resp.getAllowedFileTypes())
                            .maxSubmissions(resp.getMaxSubmissions())
                            .publishedAt(resp.getPublishedAt())
                            .createdAt(resp.getCreatedAt())
                            .updatedAt(resp.getUpdatedAt())
                            .attachmentCount((int) count)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAllAssignments(UUID instructorId) {
        List<AssignmentEntity> assignments = assignmentRepository.findByCreatedByOrderByCreatedAtDesc(instructorId);

        Set<UUID> courseIds = assignments.stream()
                .map(AssignmentEntity::getCourseId)
                .collect(Collectors.toSet());

        Map<UUID, String> courseTitleMap = courseRepository.findAllById(courseIds)
                .stream()
                .collect(Collectors.toMap(Course::getId, Course::getTitle));

        // Fetch group assignments for SPECIFIC_GROUPS assignments
        Map<UUID, List<UUID>> assignmentGroupMap = new HashMap<>();
        Set<UUID> allGroupIds = new HashSet<>();
        for (AssignmentEntity a : assignments) {
            if (a.getScope() == AssignmentScope.SPECIFIC_GROUPS) {
                List<UUID> groupIds = assignmentGroupRepository.findByAssignmentId(a.getId())
                        .stream()
                        .map(AssignmentGroupEntity::getGroupId)
                        .toList();
                assignmentGroupMap.put(a.getId(), groupIds);
                allGroupIds.addAll(groupIds);
            }
        }

        Map<UUID, String> groupNameMap = studyGroupRepository.findAllById(allGroupIds)
                .stream()
                .collect(Collectors.toMap(StudyGroupEntity::getId, StudyGroupEntity::getName));

        return assignments.stream()
                .map(a -> {
                    AssignmentResponse resp = assignmentMapper.toResponse(a);
                    long count = assignmentAttachmentRepository.countByAssignmentId(a.getId());

                    List<String> groupNames = null;
                    if (a.getScope() == AssignmentScope.SPECIFIC_GROUPS) {
                        List<UUID> gids = assignmentGroupMap.getOrDefault(a.getId(), Collections.emptyList());
                        groupNames = gids.stream()
                                .map(gid -> groupNameMap.get(gid))
                                .filter(Objects::nonNull)
                                .toList();
                    }

                    return AssignmentResponse.builder()
                            .id(resp.getId())
                            .courseId(resp.getCourseId())
                            .createdBy(resp.getCreatedBy())
                            .title(resp.getTitle())
                            .description(resp.getDescription())
                            .status(resp.getStatus())
                            .scope(resp.getScope())
                            .deadline(resp.getDeadline())
                            .startDate(resp.getStartDate())
                            .allowLateSubmission(resp.getAllowLateSubmission())
                            .latePenaltyPercent(resp.getLatePenaltyPercent())
                            .maxScore(resp.getMaxScore())
                            .maxFileSizeMb(resp.getMaxFileSizeMb())
                            .allowedFileTypes(resp.getAllowedFileTypes())
                            .maxSubmissions(resp.getMaxSubmissions())
                            .publishedAt(resp.getPublishedAt())
                            .createdAt(resp.getCreatedAt())
                            .updatedAt(resp.getUpdatedAt())
                            .attachmentCount((int) count)
                            .courseTitle(courseTitleMap.getOrDefault(a.getCourseId(), null))
                            .groupNames(groupNames)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentDetailResponse getAssignmentDetail(UUID courseId, UUID assignmentId, UUID instructorId) {
        validateCourseOwnership(courseId, instructorId);
        AssignmentEntity assignment = findAssignment(courseId, assignmentId);

        List<UUID> groupIds = null;
        if (assignment.getScope() == AssignmentScope.SPECIFIC_GROUPS) {
            groupIds = assignmentGroupRepository.findByAssignmentId(assignmentId)
                    .stream()
                    .map(AssignmentGroupEntity::getGroupId)
                    .toList();
        }

        List<AssignmentAttachmentResponse> attachments = assignmentAttachmentRepository
                .findByAssignmentIdOrderByOrderIndexAsc(assignmentId)
                .stream()
                .map(entity -> {
                    AssignmentAttachmentResponse resp = assignmentMapper.toAttachmentResponse(entity);
                    if (entity.getS3Key() == null) return resp;
                    PresignedGetObjectRequest presigned = s3Service.generatePresignedInlineUrl(
                            entity.getS3Key(), presignedUrlExpiry);
                    return AssignmentAttachmentResponse.builder()
                            .id(resp.getId())
                            .assignmentId(resp.getAssignmentId())
                            .displayName(resp.getDisplayName())
                            .originalFilename(resp.getOriginalFilename())
                            .fileSizeBytes(resp.getFileSizeBytes())
                            .mimeType(resp.getMimeType())
                            .orderIndex(resp.getOrderIndex())
                            .uploadedAt(resp.getUploadedAt())
                            .url(presigned.url().toString())
                            .build();
                })
                .toList();

        String courseTitle = courseRepository.findById(courseId)
                .map(Course::getTitle)
                .orElse(null);

        return AssignmentDetailResponse.builder()
                .id(assignment.getId())
                .courseId(assignment.getCourseId())
                .createdBy(assignment.getCreatedBy())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .status(assignment.getStatus())
                .scope(assignment.getScope())
                .groupIds(groupIds)
                .deadline(assignment.getDeadline())
                .startDate(assignment.getStartDate())
                .allowLateSubmission(assignment.getAllowLateSubmission())
                .latePenaltyPercent(assignment.getLatePenaltyPercent())
                .maxScore(assignment.getMaxScore())
                .maxFileSizeMb(assignment.getMaxFileSizeMb())
                .allowedFileTypes(parseAllowedFileTypes(assignment.getAllowedFileTypes()))
                .maxSubmissions(assignment.getMaxSubmissions())
                .publishedAt(assignment.getPublishedAt())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .courseTitle(courseTitle)
                .attachments(attachments)
                .build();
    }

    @Override
    @Transactional
    public AssignmentResponse updateAssignment(UUID courseId, UUID assignmentId, UUID instructorId,
                                                UpdateAssignmentRequest request) {
        validateCourseOwnership(courseId, instructorId);
        AssignmentEntity assignment = findAssignment(courseId, assignmentId);

        if (assignment.getStatus() == AssignmentStatus.CLOSED) {
            throw new BusinessException("Không thể chỉnh sửa bài tập đã đóng");
        }

        if (assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            updatePublishedFields(assignment, request);
        } else {
            updateDraftFields(assignment, request);
        }

        assignment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        assignmentRepository.save(assignment);
        log.info("Updated assignment {}", assignmentId);

        return assignmentMapper.toResponse(assignment);
    }

    @Override
    @Transactional
    public void deleteAssignment(UUID courseId, UUID assignmentId, UUID instructorId) {
        validateCourseOwnership(courseId, instructorId);
        AssignmentEntity assignment = findAssignment(courseId, assignmentId);

        if (assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            throw new BusinessException("Không thể xoá bài tập đã publish");
        }

        assignmentGroupRepository.deleteByAssignmentId(assignmentId);
        assignmentAttachmentRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId)
                .forEach(att -> deleteS3Object(att.getS3Key()));
        assignmentAttachmentRepository.deleteAll(
                assignmentAttachmentRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId));
        assignmentRepository.delete(assignment);

        log.info("Deleted assignment {}", assignmentId);
    }

    @Override
    @Transactional
    public AssignmentResponse publishAssignment(UUID courseId, UUID assignmentId, UUID instructorId) {
        validateCourseOwnership(courseId, instructorId);
        AssignmentEntity assignment = findAssignment(courseId, assignmentId);

        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new BusinessException("Chỉ có thể publish bài tập ở trạng thái DRAFT");
        }

        if (assignment.getTitle() == null || assignment.getTitle().trim().length() < 5) {
            throw new BusinessException("Tiêu đề phải có ít nhất 5 ký tự trước khi publish");
        }

        if (assignment.getScope() == AssignmentScope.SPECIFIC_GROUPS) {
            long groupCount = assignmentGroupRepository.findByAssignmentId(assignmentId).size();
            if (groupCount == 0) {
                throw new BusinessException("Phải có ít nhất 1 nhóm được gán khi scope=SPECIFIC_GROUPS");
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setPublishedAt(now);
        assignment.setUpdatedAt(now);
        assignmentRepository.save(assignment);

        log.info("Published assignment {} for course {}", assignmentId, courseId);
        return assignmentMapper.toResponse(assignment);
    }

    @Override
    @Transactional
    public AssignmentResponse closeAssignment(UUID courseId, UUID assignmentId, UUID instructorId) {
        validateCourseOwnership(courseId, instructorId);
        AssignmentEntity assignment = findAssignment(courseId, assignmentId);

        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new BusinessException("Chỉ có thể đóng bài tập ở trạng thái PUBLISHED");
        }

        assignment.setStatus(AssignmentStatus.CLOSED);
        assignment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        assignmentRepository.save(assignment);

        log.info("Closed assignment {}", assignmentId);
        return assignmentMapper.toResponse(assignment);
    }

    @Override
    @Transactional
    public AssignmentAttachmentResponse uploadAttachment(UUID courseId, UUID assignmentId, UUID instructorId,
                                                          MultipartFile file) {
        validateCourseOwnership(courseId, instructorId);
        AssignmentEntity assignment = findAssignment(courseId, assignmentId);

        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new BusinessException("Chỉ có thể upload file đính kèm khi bài tập ở trạng thái DRAFT");
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessException("File không được để trống");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            if (contentType == null) {
                throw new BusinessException("Không thể xác định loại file");
            }
        }

        UUID attachmentId = UUID.randomUUID();
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String s3Key = "assignments/" + courseId + "/" + assignmentId + "/" + attachmentId + extension;

        try {
            s3Client.putObject(
                    req -> req.bucket(bucket).key(s3Key).contentType(contentType),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded attachment {} to S3 key={}", attachmentId, s3Key);
        } catch (IOException e) {
            log.error("Failed to upload attachment {}: {}", attachmentId, e.getMessage());
            throw new BusinessException("Upload file thất bại");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int nextOrder = (int) assignmentAttachmentRepository.countByAssignmentId(assignmentId);

        AssignmentAttachmentEntity attachment = new AssignmentAttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setAssignmentId(assignmentId);
        attachment.setDisplayName(originalFilename);
        attachment.setOriginalFilename(originalFilename);
        attachment.setS3Key(s3Key);
        attachment.setFileSizeBytes(file.getSize());
        attachment.setMimeType(contentType);
        attachment.setOrderIndex(nextOrder);
        attachment.setUploadedAt(now);
        assignmentAttachmentRepository.save(attachment);

        return assignmentMapper.toAttachmentResponse(attachment);
    }

    @Override
    @Transactional
    public void deleteAttachment(UUID courseId, UUID assignmentId, UUID attachmentId, UUID instructorId) {
        validateCourseOwnership(courseId, instructorId);
        AssignmentEntity assignment = findAssignment(courseId, assignmentId);

        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new BusinessException("Chỉ có thể xoá file đính kèm khi bài tập ở trạng thái DRAFT");
        }

        AssignmentAttachmentEntity attachment = assignmentAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy file đính kèm"));

        if (!attachment.getAssignmentId().equals(assignmentId)) {
            throw new BusinessException("File đính kèm không thuộc bài tập này");
        }

        deleteS3Object(attachment.getS3Key());
        assignmentAttachmentRepository.delete(attachment);
        log.info("Deleted attachment {} from assignment {}", attachmentId, assignmentId);
    }

    private String toJsonArray(List<String> values) {
        if (values == null) return null;
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new BusinessException("Lỗi xử lý allowed_file_types");
        }
    }

    private List<String> parseAllowedFileTypes(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private void validateCourseOwnership(UUID courseId, UUID instructorId) {
        if (!courseRepository.existsByIdAndInstructorId(courseId, instructorId)) {
            throw new BusinessException("Bạn không sở hữu khóa học này");
        }
    }

    private void validateCoursePublished(UUID courseId) {
        courseRepository.findById(courseId)
                .filter(c -> c.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("Chỉ có thể tạo bài tập trên khóa học đã publish"));
    }

    private AssignmentEntity findAssignment(UUID courseId, UUID assignmentId) {
        return assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
                .orElseThrow(AssignmentNotFoundException::new);
    }

    private void updateDraftFields(AssignmentEntity assignment, UpdateAssignmentRequest request) {
        if (request.getTitle() != null) assignment.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) assignment.setDescription(request.getDescription());
        if (request.getScope() != null) assignment.setScope(request.getScope());

        OffsetDateTime effectiveStartDate = request.getStartDate() != null ? request.getStartDate() : assignment.getStartDate();
        OffsetDateTime effectiveDeadline = request.getDeadline() != null ? request.getDeadline() : assignment.getDeadline();
        validateDates(effectiveStartDate, effectiveDeadline);

        if (request.getStartDate() != null) assignment.setStartDate(request.getStartDate());
        if (request.getDeadline() != null) assignment.setDeadline(request.getDeadline());
        if (request.getAllowLateSubmission() != null) {
            assignment.setAllowLateSubmission(request.getAllowLateSubmission());
        }
        if (request.getLatePenaltyPercent() != null) {
            assignment.setLatePenaltyPercent(request.getLatePenaltyPercent());
        }
        if (request.getMaxScore() != null) assignment.setMaxScore(request.getMaxScore());
        if (request.getMaxFileSizeMb() != null) assignment.setMaxFileSizeMb(request.getMaxFileSizeMb());
        if (request.getAllowedFileTypes() != null) {
            assignment.setAllowedFileTypes(toJsonArray(request.getAllowedFileTypes()));
        }
        if (request.getMaxSubmissions() != null) assignment.setMaxSubmissions(request.getMaxSubmissions());

        if (request.getScope() == AssignmentScope.SPECIFIC_GROUPS && request.getGroupIds() != null) {
            assignmentGroupRepository.deleteByAssignmentId(assignment.getId());
            assignmentGroupRepository.flush();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            for (UUID groupId : request.getGroupIds()) {
                AssignmentGroupEntity ag = new AssignmentGroupEntity();
                ag.setId(UUID.randomUUID());
                ag.setAssignmentId(assignment.getId());
                ag.setGroupId(groupId);
                ag.setAssignedAt(now);
                assignmentGroupRepository.save(ag);
            }
        } else if (request.getScope() == AssignmentScope.ALL_GROUPS) {
            assignmentGroupRepository.deleteByAssignmentId(assignment.getId());
        }
    }

    private void updatePublishedFields(AssignmentEntity assignment, UpdateAssignmentRequest request) {
        boolean hasChanges = false;

        if (request.getDeadline() != null) {
            OffsetDateTime effectiveStartDate = assignment.getStartDate();
            validateDates(effectiveStartDate, request.getDeadline());
            assignment.setDeadline(request.getDeadline());
            hasChanges = true;
        }
        if (request.getAllowLateSubmission() != null) {
            assignment.setAllowLateSubmission(request.getAllowLateSubmission());
            hasChanges = true;
        }
        if (request.getLatePenaltyPercent() != null) {
            assignment.setLatePenaltyPercent(request.getLatePenaltyPercent());
            hasChanges = true;
        }
        if (request.getMaxSubmissions() != null) {
            assignment.setMaxSubmissions(request.getMaxSubmissions());
            hasChanges = true;
        }

        if (request.getScope() != null) {
            assignment.setScope(request.getScope());
            hasChanges = true;
        }
        if (request.getScope() == AssignmentScope.SPECIFIC_GROUPS && request.getGroupIds() != null) {
            assignmentGroupRepository.deleteByAssignmentId(assignment.getId());
            assignmentGroupRepository.flush();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            for (UUID groupId : request.getGroupIds()) {
                AssignmentGroupEntity ag = new AssignmentGroupEntity();
                ag.setId(UUID.randomUUID());
                ag.setAssignmentId(assignment.getId());
                ag.setGroupId(groupId);
                ag.setAssignedAt(now);
                assignmentGroupRepository.save(ag);
            }
            hasChanges = true;
        } else if (request.getScope() == AssignmentScope.ALL_GROUPS) {
            assignmentGroupRepository.deleteByAssignmentId(assignment.getId());
            hasChanges = true;
        }

        if (!hasChanges) {
            throw new BusinessException("Sau khi publish chỉ có thể thay đổi deadline, allow_late_submission, late_penalty_percent, max_submissions, scope và group_ids");
        }
    }

    private void validateDates(OffsetDateTime startDate, OffsetDateTime deadline) {
        if (startDate != null && deadline != null) {
            if (!startDate.isBefore(deadline)) {
                throw new BusinessException("Ngày bắt đầu phải trước hạn nộp");
            }
            if (startDate.plusMinutes(1).isAfter(deadline)) {
                throw new BusinessException("Ngày bắt đầu phải trước hạn nộp ít nhất 1 phút");
            }
        }
        if (startDate != null && startDate.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5))) {
            throw new BusinessException("Ngày bắt đầu không thể ở quá khứ");
        }
        if (deadline != null) {
            if (deadline.isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2))) {
                throw new BusinessException("Hạn nộp phải ít nhất 2 phút sau thời điểm hiện tại");
            }
        }
    }

    private void deleteS3Object(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) return;
        try {
            s3Client.deleteObject(req -> req.bucket(bucket).key(s3Key));
            log.info("Deleted S3 object key={}", s3Key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object key={}: {}", s3Key, e.getMessage());
        }
    }
}
