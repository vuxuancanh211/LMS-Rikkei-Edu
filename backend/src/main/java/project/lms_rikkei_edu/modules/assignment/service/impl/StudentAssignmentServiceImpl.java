package project.lms_rikkei_edu.modules.assignment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentAttachmentResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentListResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.SubmissionFileResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.SubmissionResponse;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentAttachmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentGroupEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentSubmissionEntity;
import project.lms_rikkei_edu.modules.assignment.entity.SubmissionFileEntity;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;
import project.lms_rikkei_edu.modules.assignment.exception.AssignmentNotFoundException;
import project.lms_rikkei_edu.modules.assignment.mapper.AssignmentMapper;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentAttachmentRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentGroupRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentSubmissionRepository;
import project.lms_rikkei_edu.modules.assignment.repository.SubmissionFileRepository;
import project.lms_rikkei_edu.modules.assignment.service.StudentAssignmentService;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAssignmentServiceImpl implements StudentAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentGroupRepository assignmentGroupRepository;
    private final AssignmentAttachmentRepository assignmentAttachmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final S3Client s3Client;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    private final AssignmentMapper assignmentMapper;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.presigned-url-expiry:3600}")
    private long presignedUrlExpiry;

    @Override
    @Transactional(readOnly = true)
    public List<StudentAssignmentListResponse> getAssignments(UUID courseId, UUID studentId) {
        validateEnrollment(courseId, studentId);

        List<UUID> studentGroupIds = groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId);
        Set<UUID> groupSet = Set.copyOf(studentGroupIds);

        List<AssignmentEntity> allAssignments = assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId);

        return allAssignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.PUBLISHED || a.getStatus() == AssignmentStatus.CLOSED)
                .filter(a -> canAccess(a, groupSet))
                .map(a -> {
                    long attachmentCount = assignmentAttachmentRepository.countByAssignmentId(a.getId());
                    AssignmentSubmissionEntity sub = assignmentSubmissionRepository
                            .findByAssignmentIdAndStudentId(a.getId(), studentId).orElse(null);
                    return StudentAssignmentListResponse.builder()
                            .id(a.getId())
                            .courseId(a.getCourseId())
                            .title(a.getTitle())
                            .status(a.getStatus())
                            .deadline(a.getDeadline())
                            .startDate(a.getStartDate())
                            .maxScore(a.getMaxScore())
                            .passScore(a.getPassingScore())
                            .attachmentCount((int) attachmentCount)
                            .submissionStatus(sub != null ? sub.getStatus() : null)
                            .score(sub != null ? sub.getScore() : null)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAssignmentDetailResponse getAssignmentDetail(UUID courseId, UUID assignmentId, UUID studentId) {
        validateEnrollment(courseId, studentId);

        AssignmentEntity assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
                .orElseThrow(AssignmentNotFoundException::new);

        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            throw new BusinessException("Bài tập chưa được xuất bản");
        }

        List<UUID> studentGroupIds = groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId);
        if (!canAccess(assignment, Set.copyOf(studentGroupIds))) {
            throw new BusinessException("Bạn không có quyền truy cập bài tập này", HttpStatus.FORBIDDEN);
        }

        String courseTitle = courseRepository.findById(courseId)
                .map(Course::getTitle)
                .orElse(null);

        List<AssignmentAttachmentResponse> attachments = assignmentAttachmentRepository
                .findByAssignmentIdOrderByOrderIndexAsc(assignmentId)
                .stream()
                .map(entity -> {
                    AssignmentAttachmentResponse resp = assignmentMapper.toAttachmentResponse(entity);
                    if (entity.getS3Key() == null) return resp;
                    var presigned = s3Service.generatePresignedInlineUrl(entity.getS3Key(), presignedUrlExpiry);
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

        SubmissionResponse studentSubmission = buildSubmissionResponse(assignmentId, studentId);

        return StudentAssignmentDetailResponse.builder()
                .id(assignment.getId())
                .courseId(assignment.getCourseId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .status(assignment.getStatus())
                .scope(assignment.getScope())
                .deadline(assignment.getDeadline())
                .startDate(assignment.getStartDate())
                .allowLateSubmission(assignment.getAllowLateSubmission())
                .latePenaltyPercent(assignment.getLatePenaltyPercent())
                .maxScore(assignment.getMaxScore())
                .passScore(assignment.getPassingScore())
                .maxFileSizeMb(assignment.getMaxFileSizeMb())
                .allowedFileTypes(parseAllowedFileTypes(assignment.getAllowedFileTypes()))
                .publishedAt(assignment.getPublishedAt())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .courseTitle(courseTitle)
                .attachments(attachments)
                .studentSubmission(studentSubmission)
                .build();
    }

    @Override
    @Transactional
    public SubmissionResponse submitAssignment(UUID courseId, UUID assignmentId, UUID studentId,
                                                String note, List<MultipartFile> files) {
        validateEnrollment(courseId, studentId);
        AssignmentEntity assignment = resolveAssignment(courseId, assignmentId, studentId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (assignment.getStartDate() != null && now.isBefore(assignment.getStartDate())) {
            throw new BusinessException("Bài tập chưa đến thời gian cho phép nộp");
        }

        // Nếu đã có bài nộp cũ và đã công bố điểm → không cho nộp lại
        List<AssignmentSubmissionEntity> existingList = assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId);
        if (!existingList.isEmpty()) {
            for (AssignmentSubmissionEntity existing : existingList) {
                if (existing.getScorePublishedAt() != null) {
                    throw new BusinessException("Không thể nộp lại bài tập đã được công bố điểm");
                }
            }
            // Xoá tất cả file cũ trên S3 và DB
            for (AssignmentSubmissionEntity oldSub : existingList) {
                List<SubmissionFileEntity> oldFiles = submissionFileRepository
                        .findBySubmissionIdOrderByOrderIndexAsc(oldSub.getId());
                for (SubmissionFileEntity f : oldFiles) {
                    if (f.getS3Key() != null) {
                        s3Service.deleteObject(f.getS3Key());
                    }
                    submissionFileRepository.delete(f);
                }
                assignmentSubmissionRepository.delete(oldSub);
            }
        }

        boolean isLate = checkLateSubmission(assignment, now);

        List<String> allowedTypes = parseAllowedFileTypes(assignment.getAllowedFileTypes());
        int maxSizeMb = assignment.getMaxFileSizeMb() != null ? assignment.getMaxFileSizeMb() : 10;
        validateSubmissionFiles(files, allowedTypes, maxSizeMb);

        UUID submissionId = UUID.randomUUID();
        createAndSaveSubmission(submissionId, assignmentId, studentId, courseId, note, isLate, now);
        List<SubmissionFileResponse> fileResponses = uploadFiles(courseId, assignmentId, submissionId, files, now);

        log.info("Student {} submitted assignment {}", studentId, assignmentId);
        return SubmissionResponse.builder()
                .id(submissionId)
                .status(isLate ? "LATE" : "SUBMITTED")
                .note(note)
                .isLate(isLate)
                .submittedAt(now)
                .files(fileResponses)
                .build();
    }

    private AssignmentEntity resolveAssignment(UUID courseId, UUID assignmentId, UUID studentId) {
        AssignmentEntity assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
                .orElseThrow(AssignmentNotFoundException::new);
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new BusinessException("Bài tập chưa được xuất bản hoặc đã đóng");
        }
        List<UUID> studentGroupIds = groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId);
        if (!canAccess(assignment, Set.copyOf(studentGroupIds))) {
            throw new BusinessException("Bạn không có quyền truy cập bài tập này", HttpStatus.FORBIDDEN);
        }
        return assignment;
    }

    private boolean checkLateSubmission(AssignmentEntity assignment, OffsetDateTime now) {
        if (assignment.getDeadline() != null && now.isAfter(assignment.getDeadline())) {
            if (!Boolean.TRUE.equals(assignment.getAllowLateSubmission())) {
                throw new BusinessException("Đã quá hạn nộp bài");
            }
            return true;
        }
        return false;
    }

    private void validateSubmissionFiles(List<MultipartFile> files, List<String> allowedTypes, int maxSizeMb) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất 1 file để nộp");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("File không được để trống");
            }
            if (!allowedTypes.isEmpty()) {
                String mime = file.getContentType();
                if (mime != null && allowedTypes.stream().noneMatch(t -> t.equalsIgnoreCase(mime))) {
                    throw new BusinessException("Loại file " + mime + " không được hỗ trợ");
                }
            }
            long maxBytes = (long) maxSizeMb * 1024 * 1024;
            if (file.getSize() > maxBytes) {
                throw new BusinessException("File " + file.getOriginalFilename() + " vượt quá kích thước tối đa (" + maxSizeMb + " MB)");
            }
        }
    }

    private AssignmentSubmissionEntity createAndSaveSubmission(UUID submissionId, UUID assignmentId, UUID studentId,
                                                                UUID courseId, String note, boolean isLate, OffsetDateTime now) {
        AssignmentSubmissionEntity submission = new AssignmentSubmissionEntity();
        submission.setId(submissionId);
        submission.setAssignmentId(assignmentId);
        submission.setStudentId(studentId);
        submission.setCourseId(courseId);
        submission.setStatus(isLate ? "LATE" : "SUBMITTED");
        submission.setNote(note);
        submission.setIsLate(isLate);
        submission.setSubmittedAt(now);
        submission.setCreatedAt(now);
        assignmentSubmissionRepository.save(submission);
        return submission;
    }

    private List<SubmissionFileResponse> uploadFiles(UUID courseId, UUID assignmentId, UUID submissionId,
                                                      List<MultipartFile> files, OffsetDateTime now) {
        List<SubmissionFileResponse> fileResponses = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            UUID fileId = UUID.randomUUID();
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
            }
            String s3Key = "submissions/" + courseId + "/" + assignmentId + "/" + submissionId + "/" + fileId + ext;

            try {
                s3Client.putObject(
                        req -> req.bucket(bucket).key(s3Key).contentType(file.getContentType()),
                        RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
                log.info("Uploaded submission file {} to S3 key={}", fileId, s3Key);
            } catch (IOException e) {
                log.error("Failed to upload submission file {}: {}", fileId, e.getMessage());
                throw new BusinessException("Upload file thất bại: " + originalFilename);
            }

            SubmissionFileEntity sfe = new SubmissionFileEntity();
            sfe.setId(fileId);
            sfe.setSubmissionId(submissionId);
            sfe.setOriginalFilename(originalFilename);
            sfe.setS3Key(s3Key);
            sfe.setFileSizeBytes(file.getSize());
            sfe.setMimeType(file.getContentType());
            sfe.setExtension(ext.replace(".", ""));
            sfe.setOrderIndex(i);
            sfe.setUploadedAt(now);
            submissionFileRepository.save(sfe);

            var presigned = s3Service.generatePresignedInlineUrl(s3Key, presignedUrlExpiry);
            fileResponses.add(SubmissionFileResponse.builder()
                    .id(fileId)
                    .originalFilename(originalFilename)
                    .fileSizeBytes(file.getSize())
                    .mimeType(file.getContentType())
                    .extension(ext.replace(".", ""))
                    .url(presigned.url().toString())
                    .build());
        }
        return fileResponses;
    }

    private boolean canAccess(AssignmentEntity assignment, Set<UUID> studentGroupIds) {
        if (assignment.getScope() == AssignmentScope.ALL_GROUPS) {
            return true;
        }
        List<UUID> assignedGroupIds = assignmentGroupRepository.findByAssignmentId(assignment.getId())
                .stream()
                .map(AssignmentGroupEntity::getGroupId)
                .toList();
        return assignedGroupIds.stream().anyMatch(studentGroupIds::contains);
    }

    private SubmissionResponse buildSubmissionResponse(UUID assignmentId, UUID studentId) {
        List<AssignmentSubmissionEntity> subs = assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId);
        if (subs.isEmpty()) return null;

        AssignmentSubmissionEntity latest = subs.get(0);

        List<SubmissionFileEntity> fileEntities = submissionFileRepository
                .findBySubmissionIdOrderByOrderIndexAsc(latest.getId());

        List<SubmissionFileResponse> files = fileEntities.stream()
                .map(fe -> {
                    String url = null;
                    if (fe.getS3Key() != null) {
                        var presigned = s3Service.generatePresignedInlineUrl(fe.getS3Key(), presignedUrlExpiry);
                        url = presigned.url().toString();
                    }
                    return SubmissionFileResponse.builder()
                            .id(fe.getId())
                            .originalFilename(fe.getOriginalFilename())
                            .fileSizeBytes(fe.getFileSizeBytes())
                            .mimeType(fe.getMimeType())
                            .extension(fe.getExtension())
                            .url(url)
                            .build();
                })
                .toList();

        return SubmissionResponse.builder()
                .id(latest.getId())
                .status(latest.getStatus())
                .note(latest.getNote())
                .isLate(Boolean.TRUE.equals(latest.getIsLate()))
                .score(latest.getScore())
                .feedback(latest.getFeedback())
                .scorePublishedAt(latest.getScorePublishedAt())
                .submittedAt(latest.getSubmittedAt())
                .files(files)
                .build();
    }

    private void validateEnrollment(UUID courseId, UUID studentId) {
        if (!courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw new BusinessException("Bạn không tham gia khóa học này", HttpStatus.FORBIDDEN);
        }
    }

    private List<String> parseAllowedFileTypes(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
