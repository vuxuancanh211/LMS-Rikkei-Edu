package project.lms_rikkei_edu.modules.assignment.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.modules.assignment.dto.request.BatchReleaseRequest;
import project.lms_rikkei_edu.modules.assignment.dto.request.GradeRequest;
import project.lms_rikkei_edu.modules.assignment.dto.response.InstructorSubmissionResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.SubmissionFileResponse;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentGroupEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentSubmissionEntity;
import project.lms_rikkei_edu.modules.assignment.entity.SubmissionFileEntity;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;
import project.lms_rikkei_edu.modules.assignment.exception.AssignmentNotFoundException;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentGroupRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentSubmissionRepository;
import project.lms_rikkei_edu.modules.assignment.repository.SubmissionFileRepository;
import project.lms_rikkei_edu.modules.assignment.service.GradingService;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.service.StudentCourseService;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import project.lms_rikkei_edu.modules.group.repository.StudyGroupRepository;
import project.lms_rikkei_edu.modules.notification.enums.NotificationType;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradingServiceImpl implements GradingService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final AssignmentGroupRepository assignmentGroupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StudyGroupRepository studyGroupRepository;
    private final CourseRepository courseRepository;
    private final S3Service s3Service;
    private final StudentCourseService studentCourseService;
    private final NotificationService notificationService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public List<InstructorSubmissionResponse> getSubmissions(UUID courseId, UUID assignmentId, UUID instructorId, String statusFilter) {
        List<AssignmentEntity> assignments = resolveAssignments(courseId, assignmentId, instructorId);

        List<InstructorSubmissionResponse> result = new ArrayList<>();
        for (AssignmentEntity assignment : assignments) {
            result.addAll(getSubmissionsForAssignment(assignment, statusFilter));
        }
        return result;
    }

    private List<AssignmentEntity> resolveAssignments(UUID courseId, UUID assignmentId, UUID instructorId) {
        if (assignmentId != null) {
            AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                    .orElseThrow(AssignmentNotFoundException::new);
            if (!assignment.getCreatedBy().equals(instructorId)) {
                throw new BusinessException("Bạn không có quyền xem bài nộp của bài tập này", HttpStatus.FORBIDDEN);
            }
            if (assignment.getStatus() == AssignmentStatus.DRAFT) {
                return Collections.emptyList();
            }
            return List.of(assignment);
        }

        List<AssignmentEntity> assignments;
        if (courseId != null) {
            assignments = assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
        } else {
            assignments = assignmentRepository.findByCreatedByOrderByCreatedAtDesc(instructorId);
        }

        return assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.PUBLISHED || a.getStatus() == AssignmentStatus.CLOSED)
                .toList();
    }

    private List<InstructorSubmissionResponse> getSubmissionsForAssignment(AssignmentEntity assignment, String statusFilter) {
        UUID courseId = assignment.getCourseId();
        UUID assignmentId = assignment.getId();

        Set<UUID> expectedStudentIds = getExpectedStudentIds(assignment, courseId);
        List<AssignmentSubmissionEntity> submissions = assignmentSubmissionRepository
                .findByAssignmentIdOrderBySubmittedAtDesc(assignmentId);

        Map<UUID, AssignmentSubmissionEntity> submissionMap = buildSubmissionMap(submissions);
        Map<UUID, UserEntity> userMap = fetchUserMap(expectedStudentIds);
        Map<UUID, List<SubmissionFileEntity>> fileMap = buildFileMap(submissions);
        Map<UUID, List<UUID>> studentGroupMap = buildStudentGroupMap(expectedStudentIds, courseId);
        Map<UUID, String> groupNameMap = fetchGroupNameMap(studentGroupMap);

        boolean filterByStatus = statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter);
        String filterStatus = filterByStatus ? statusFilter.toUpperCase() : null;

        List<InstructorSubmissionResponse> result = new ArrayList<>();
        for (UUID studentId : expectedStudentIds) {
            AssignmentSubmissionEntity sub = submissionMap.get(studentId);
            String entryStatus = sub != null ? sub.getStatus() : "not_submitted";

            if (filterByStatus && !entryStatus.equals(filterStatus)) {
                continue;
            }

            UserEntity user = userMap.get(studentId);
            List<UUID> groupIds = studentGroupMap.getOrDefault(studentId, Collections.emptyList());
            UUID groupId = null;
            String groupName = null;
            if (!groupIds.isEmpty()) {
                groupId = groupIds.get(0);
                groupName = groupNameMap.get(groupId);
            }
            List<SubmissionFileResponse> files = buildFileResponses(sub, fileMap);

            result.add(buildStudentSubmissionResponse(
                    studentId, sub, user, groupId, groupName, files, assignment));
        }

        return result;
    }

    private InstructorSubmissionResponse buildStudentSubmissionResponse(
            UUID studentId, AssignmentSubmissionEntity sub,
            UserEntity user, UUID groupId, String groupName,
            List<SubmissionFileResponse> files,
            AssignmentEntity assignment) {
        String entryStatus = sub != null ? sub.getStatus() : "not_submitted";
        String courseTitle = courseRepository.findById(assignment.getCourseId())
                .map(Course::getTitle).orElse(null);

        return InstructorSubmissionResponse.builder()
                .id(sub != null ? sub.getId() : null)
                .status(entryStatus)
                .note(sub != null ? sub.getNote() : null)
                .isLate(sub != null && Boolean.TRUE.equals(sub.getIsLate()))
                .score(sub != null ? sub.getScore() : null)
                .feedback(sub != null ? sub.getFeedback() : null)
                .submittedAt(sub != null ? sub.getSubmittedAt() : null)
                .gradedAt(sub != null ? sub.getGradedAt() : null)
                .scorePublishedAt(sub != null ? sub.getScorePublishedAt() : null)
                .files(files)
                .studentId(studentId)
                .studentName(user != null ? user.getFullName() : null)
                .studentEmail(user != null ? user.getEmail() : null)
                .assignmentId(assignment.getId())
                .assignmentTitle(assignment.getTitle())
                .assignmentMaxScore(assignment.getMaxScore())
                .assignmentPassScore(assignment.getPassingScore())
                .courseId(assignment.getCourseId())
                .courseTitle(courseTitle)
                .groupId(groupId)
                .groupName(groupName)
                .build();
    }

    private Map<UUID, AssignmentSubmissionEntity> buildSubmissionMap(List<AssignmentSubmissionEntity> submissions) {
        return submissions.stream()
                .collect(Collectors.toMap(
                        AssignmentSubmissionEntity::getStudentId,
                        sub -> sub,
                        (a, b) -> a));
    }

    private Map<UUID, UserEntity> fetchUserMap(Set<UUID> studentIds) {
        return userRepository.findAllByIdInAndDeletedAtIsNull(new ArrayList<>(studentIds))
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));
    }

    private Map<UUID, List<SubmissionFileEntity>> buildFileMap(List<AssignmentSubmissionEntity> submissions) {
        Set<UUID> submissionIds = submissions.stream()
                .map(AssignmentSubmissionEntity::getId)
                .collect(Collectors.toSet());
        return submissionFileRepository
                .findBySubmissionIdInOrderByOrderIndexAsc(submissionIds)
                .stream()
                .collect(Collectors.groupingBy(SubmissionFileEntity::getSubmissionId));
    }

    private Map<UUID, List<UUID>> buildStudentGroupMap(Set<UUID> studentIds, UUID courseId) {
        Map<UUID, List<UUID>> map = new HashMap<>();
        for (UUID studentId : studentIds) {
            map.put(studentId, groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId));
        }
        return map;
    }

    private Map<UUID, String> fetchGroupNameMap(Map<UUID, List<UUID>> studentGroupMap) {
        Set<UUID> allGroupIds = new HashSet<>();
        studentGroupMap.values().forEach(allGroupIds::addAll);
        return studyGroupRepository.findAllById(allGroupIds)
                .stream()
                .collect(Collectors.toMap(StudyGroupEntity::getId, StudyGroupEntity::getName));
    }

    private List<SubmissionFileResponse> buildFileResponses(AssignmentSubmissionEntity sub, Map<UUID, List<SubmissionFileEntity>> fileMap) {
        if (sub == null) return Collections.emptyList();
        return fileMap.getOrDefault(sub.getId(), Collections.emptyList())
                .stream()
                .map(fe -> {
                    String url = null;
                    if (fe.getS3Key() != null) {
                        var presigned = s3Service.generatePresignedInlineUrl(fe.getS3Key(), 3600);
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
    }

    private Set<UUID> getExpectedStudentIds(AssignmentEntity assignment, UUID courseId) {
        if (assignment.getScope() == AssignmentScope.ALL_GROUPS) {
            return new HashSet<>(groupMemberRepository.findStudentIdsByCourseId(courseId));
        }

        List<UUID> assignedGroupIds = assignmentGroupRepository.findByAssignmentId(assignment.getId())
                .stream()
                .map(AssignmentGroupEntity::getGroupId)
                .toList();

        if (assignedGroupIds.isEmpty()) {
            return Collections.emptySet();
        }

        return groupMemberRepository.findByGroupIdInWithStudent(assignedGroupIds)
                .stream()
                .map(m -> m.getStudent().getId())
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public InstructorSubmissionResponse gradeSubmission(GradeRequest request, UUID instructorId) {
        AssignmentSubmissionEntity submission = assignmentSubmissionRepository.findById(request.getSubmissionId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy bài nộp"));

        AssignmentEntity assignment = assignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(AssignmentNotFoundException::new);

        if (!assignment.getCreatedBy().equals(instructorId)) {
            throw new BusinessException("Bạn không có quyền chấm bài nộp này", HttpStatus.FORBIDDEN);
        }

        if (submission.getScorePublishedAt() != null) {
            throw new BusinessException("Không thể sửa điểm đã công bố", HttpStatus.BAD_REQUEST);
        }

        if (request.getScore() != null) {
            BigDecimal maxScore = assignment.getMaxScore();
            BigDecimal absoluteMax = BigDecimal.valueOf(100);
            BigDecimal limit = maxScore != null && maxScore.compareTo(absoluteMax) < 0 ? maxScore : absoluteMax;
            if (request.getScore().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("Điểm không được nhỏ hơn 0");
            }
            if (request.getScore().compareTo(limit) > 0) {
                throw new BusinessException("Điểm không được vượt quá " + limit);
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        submission.setScore(request.getScore());
        submission.setFeedback(request.getFeedback());
        submission.setStatus("GRADED");
        submission.setGradedBy(instructorId);
        submission.setGradedAt(now);
        assignmentSubmissionRepository.save(submission);

        log.info("Instructor {} graded submission {} with score {}", instructorId, submission.getId(), request.getScore());

        return getSingleResponse(submission, assignment);
    }

    @Override
    @Transactional
    public void batchReleaseScores(BatchReleaseRequest request, UUID instructorId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updated = assignmentSubmissionRepository.batchPublishScores(request.getSubmissionIds(), now);

        if (updated > 0) {
            List<AssignmentSubmissionEntity> submissions = assignmentSubmissionRepository.findAllById(request.getSubmissionIds());
            Set<String> processed = new HashSet<>();
            for (AssignmentSubmissionEntity s : submissions) {
                String key = s.getStudentId() + ":" + s.getCourseId();
                if (processed.add(key)) {
                    studentCourseService.updateAssignmentProgress(s.getStudentId(), s.getCourseId());
                }
                notifyStudentScorePublished(s, instructorId);
            }
        }

        log.info("Instructor {} published scores for {} submissions", instructorId, updated);
    }

    @Override
    @Transactional
    public void returnSubmission(UUID submissionId, UUID instructorId) {
        AssignmentSubmissionEntity submission = assignmentSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy bài nộp"));

        AssignmentEntity assignment = assignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(AssignmentNotFoundException::new);

        if (!assignment.getCreatedBy().equals(instructorId)) {
            throw new BusinessException("Bạn không có quyền trả bài nộp này", HttpStatus.FORBIDDEN);
        }

        submission.setStatus("RETURNED");
        assignmentSubmissionRepository.save(submission);

        log.info("Instructor {} returned submission {}", instructorId, submissionId);
    }

    private void notifyStudentScorePublished(AssignmentSubmissionEntity s, UUID instructorId) {
        try {
            AssignmentEntity assignment = assignmentRepository.findById(s.getAssignmentId()).orElse(null);
            if (assignment == null) return;

            String courseTitle = courseRepository.findById(s.getCourseId())
                    .map(Course::getTitle)
                    .orElse("");
            String actorName = currentUserProvider.getCurrentUser()
                    .map(UserPrincipal::getUsername)
                    .orElse(null);
            String title = "Điểm bài tập đã được công bố";
            String body = "Điểm bài tập \"" + assignment.getTitle() + "\" (khóa \"" + courseTitle + "\") đã được công bố.";

            if (!notificationPreferenceService.isInAppEnabled(s.getStudentId(), NotificationType.SUBMISSION_GRADED.name())) {
                return;
            }
            notificationService.createNotification(
                    s.getStudentId(),
                    NotificationType.SUBMISSION_GRADED.name(),
                    title,
                    body,
                    "ASSIGNMENT",
                    assignment.getId(),
                    instructorId,
                    actorName,
                    "score-released-" + s.getId() + ":" + s.getStudentId());
        } catch (Exception ex) {
            log.warn("Không thể gửi thông báo công bố điểm cho submissionId={}: {}", s.getId(), ex.getMessage());
        }
    }

    private InstructorSubmissionResponse getSingleResponse(AssignmentSubmissionEntity submission,
                                                           AssignmentEntity assignment) {
        UUID courseId = assignment.getCourseId();
        UUID studentId = submission.getStudentId();

        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(studentId).orElse(null);

        List<UUID> groupIds = groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId);
        String groupName = null;
        if (!groupIds.isEmpty()) {
            groupName = studyGroupRepository.findById(groupIds.get(0))
                    .map(StudyGroupEntity::getName)
                    .orElse(null);
        }

        List<SubmissionFileEntity> fileEntities = submissionFileRepository
                .findBySubmissionIdOrderByOrderIndexAsc(submission.getId());

        List<SubmissionFileResponse> files = fileEntities.stream()
                .map(fe -> {
                    String url = null;
                    if (fe.getS3Key() != null) {
                        var presigned = s3Service.generatePresignedInlineUrl(fe.getS3Key(), 3600);
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

        String courseTitle = courseRepository.findById(courseId).map(Course::getTitle).orElse(null);

        return InstructorSubmissionResponse.builder()
                .id(submission.getId())
                .status(submission.getStatus())
                .note(submission.getNote())
                .isLate(Boolean.TRUE.equals(submission.getIsLate()))
                .score(submission.getScore())
                .feedback(submission.getFeedback())
                .submittedAt(submission.getSubmittedAt())
                .gradedAt(submission.getGradedAt())
                .scorePublishedAt(submission.getScorePublishedAt())
                .files(files)
                .studentId(submission.getStudentId())
                .studentName(user != null ? user.getFullName() : null)
                .studentEmail(user != null ? user.getEmail() : null)
                .assignmentId(assignment.getId())
                .assignmentTitle(assignment.getTitle())
                .assignmentMaxScore(assignment.getMaxScore())
                .assignmentPassScore(assignment.getPassingScore())
                .courseId(assignment.getCourseId())
                .courseTitle(courseTitle)
                .groupId(!groupIds.isEmpty() ? groupIds.get(0) : null)
                .groupName(groupName)
                .build();
    }
}
