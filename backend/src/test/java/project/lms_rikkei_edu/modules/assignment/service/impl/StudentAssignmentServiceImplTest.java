package project.lms_rikkei_edu.modules.assignment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.notification.enums.NotificationType;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentAttachmentResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentListResponse;
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
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.math.BigDecimal;
import java.net.URL;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAssignmentServiceImplTest {

    @Mock private AssignmentRepository assignmentRepository;
    @Mock private AssignmentGroupRepository assignmentGroupRepository;
    @Mock private AssignmentAttachmentRepository assignmentAttachmentRepository;
    @Mock private AssignmentSubmissionRepository assignmentSubmissionRepository;
    @Mock private SubmissionFileRepository submissionFileRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock private S3Client s3Client;
    @Mock private S3Service s3Service;
    @Mock private AssignmentMapper assignmentMapper;
    @Mock private NotificationService notificationService;
    @Mock private NotificationPreferenceService notificationPreferenceService;
    @Mock private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StudentAssignmentServiceImpl service;

    private final UUID courseId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    private final OffsetDateTime futureDeadline = now.plusHours(48);
    private final OffsetDateTime pastDeadline = now.minusHours(1);

    @BeforeEach
    void setUp() {
        service = new StudentAssignmentServiceImpl(
                assignmentRepository, assignmentGroupRepository,
                assignmentAttachmentRepository, assignmentSubmissionRepository,
                submissionFileRepository, groupMemberRepository,
                courseRepository, courseEnrollmentRepository,
                notificationService, notificationPreferenceService, userRepository,
                s3Client, s3Service, objectMapper, assignmentMapper);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "presignedUrlExpiry", 3600L);
    }

    // ── getAssignments ─────────────────────────────────────────────────────

    @Test
    void getAssignments_notEnrolled_throws() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getAssignments(courseId, studentId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không tham gia");
    }

    @Test
    void getAssignments_returnsOnlyPublishedAndClosed() {
        enrollStudent();
        var published = assignmentEntity(AssignmentStatus.PUBLISHED);
        var closed = assignmentEntity(AssignmentStatus.CLOSED);
        closed.setId(UUID.randomUUID());
        var draft = assignmentEntity(AssignmentStatus.DRAFT);
        draft.setId(UUID.randomUUID());
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(published, closed, draft));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of(UUID.randomUUID()));
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).hasSize(2);
    }

    @Test
    void getAssignments_allGroupsScope_excludesStudentWithoutGroup() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).isEmpty();
    }

    @Test
    void getAssignments_specificGroupsScope_studentInGroup_includes() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.SPECIFIC_GROUPS);
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of(groupId));
        var ag = new AssignmentGroupEntity();
        ag.setGroupId(groupId);
        when(assignmentGroupRepository.findByAssignmentId(assignmentId))
                .thenReturn(List.of(ag));
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAssignments_specificGroupsScope_studentNotInGroup_excludes() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.SPECIFIC_GROUPS);
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());

        var ag = new AssignmentGroupEntity();
        ag.setGroupId(UUID.randomUUID());
        when(assignmentGroupRepository.findByAssignmentId(assignmentId))
                .thenReturn(List.of(ag));

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).isEmpty();
    }

    @Test
    void getAssignments_withSubmission_includesSubmissionStatus() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var submission = new AssignmentSubmissionEntity();
        submission.setAssignmentId(assignmentId);
        submission.setStatus("SUBMITTED");
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(assignment));

        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);
        when(assignmentSubmissionRepository.findByStudentIdAndAssignmentIdIn(studentId, List.of(assignmentId)))
                .thenReturn(List.of(submission));

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubmissionStatus()).isEqualTo("SUBMITTED");
        assertThat(result.get(0).getScore()).isNull();
    }

    @Test
    void getAssignments_withGradedSubmission_showsScore() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var submission = new AssignmentSubmissionEntity();
        submission.setAssignmentId(assignmentId);
        submission.setStatus("GRADED");
        submission.setScore(BigDecimal.valueOf(85));
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(assignment));

        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);
        when(assignmentSubmissionRepository.findByStudentIdAndAssignmentIdIn(studentId, List.of(assignmentId)))
                .thenReturn(List.of(submission));

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScore()).isEqualByComparingTo(BigDecimal.valueOf(85));
        assertThat(result.get(0).getPassScore()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    void getAllAssignments_noEnrolledCourses_returnsEmptyList() {
        when(courseEnrollmentRepository.findCourseIdsByStudentId(studentId)).thenReturn(List.of());

        List<StudentAssignmentListResponse> result = service.getAllAssignments(studentId);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllAssignments_returnsAccessibleAssignmentsWithCourseTitle() {
        UUID otherCourseId = UUID.randomUUID();
        UUID otherAssignmentId = UUID.randomUUID();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var otherAssignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        otherAssignment.setId(otherAssignmentId);
        otherAssignment.setCourseId(otherCourseId);
        var otherCourse = new Course();
        otherCourse.setId(otherCourseId);
        otherCourse.setTitle("Other Course");
        var submission = new AssignmentSubmissionEntity();
        submission.setAssignmentId(otherAssignmentId);
        submission.setStatus("SUBMITTED");

        when(courseEnrollmentRepository.findCourseIdsByStudentId(studentId))
                .thenReturn(List.of(courseId, otherCourseId));
        when(courseRepository.findAllById(List.of(courseId, otherCourseId)))
                .thenReturn(List.of(course(), otherCourse));
        when(assignmentRepository.findByCourseIdInOrderByCreatedAtDesc(List.of(courseId, otherCourseId)))
                .thenReturn(List.of(assignment, otherAssignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of(groupId));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, otherCourseId))
                .thenReturn(List.of(groupId));
        when(assignmentSubmissionRepository.findByStudentIdAndAssignmentIdIn(studentId, List.of(assignmentId, otherAssignmentId)))
                .thenReturn(List.of(submission));

        List<StudentAssignmentListResponse> result = service.getAllAssignments(studentId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(StudentAssignmentListResponse::getCourseTitle)
                .containsExactly("Course Title", "Other Course");
        assertThat(result.get(1).getSubmissionStatus()).isEqualTo("SUBMITTED");
    }

    // ── getAssignmentDetail ────────────────────────────────────────────────

    @Test
    void getAssignmentDetail_notEnrolled_throws() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getAssignmentDetail(courseId, assignmentId, studentId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không tham gia");
    }

    @Test
    void getAssignmentDetail_draft_throws() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.DRAFT);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.getAssignmentDetail(courseId, assignmentId, studentId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được xuất bản");
    }

    @Test
    void getAssignmentDetail_notAssignedToStudent_throws() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.SPECIFIC_GROUPS);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());

        var ag = new AssignmentGroupEntity();
        ag.setGroupId(UUID.randomUUID());
        when(assignmentGroupRepository.findByAssignmentId(assignmentId))
                .thenReturn(List.of(ag));

        assertThatThrownBy(() -> service.getAssignmentDetail(courseId, assignmentId, studentId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quyền truy cập");
    }

    @Test
    void getAssignmentDetail_success_returnsDetail() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));
        when(assignmentAttachmentRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId))
                .thenReturn(List.of());
        when(assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId))
                .thenReturn(List.of());

        StudentAssignmentDetailResponse result = service.getAssignmentDetail(courseId, assignmentId, studentId);

        assertThat(result.getId()).isEqualTo(assignmentId);
        assertThat(result.getTitle()).isEqualTo("Test Assignment");
        assertThat(result.getCourseTitle()).isEqualTo("Course Title");
        assertThat(result.getPassScore()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(result.getStudentSubmission()).isNull();
    }

    @Test
    void getAssignmentDetail_withAttachments_includesPresignedUrls() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var attachment = new AssignmentAttachmentEntity();
        attachment.setId(UUID.randomUUID());
        attachment.setAssignmentId(assignmentId);
        attachment.setS3Key("attachments/key.pdf");
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/doc.pdf"));
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));
        when(assignmentAttachmentRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId))
                .thenReturn(List.of(attachment));
        when(assignmentMapper.toAttachmentResponse(attachment))
                .thenReturn(AssignmentAttachmentResponse.builder()
                        .id(attachment.getId())
                        .assignmentId(assignmentId)
                        .originalFilename("doc.pdf")
                        .build());
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);
        when(assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId))
                .thenReturn(List.of());

        StudentAssignmentDetailResponse result = service.getAssignmentDetail(courseId, assignmentId, studentId);

        assertThat(result.getAttachments()).hasSize(1);
    }

    // ── submitAssignment ───────────────────────────────────────────────────

    @Test
    void submitAssignment_notEnrolled_throws() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không tham gia");
    }

    @Test
    void submitAssignment_assignmentNotFound_throws() {
        enrollStudent();
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(), null))
                .isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    void submitAssignment_notPublished_throws() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.DRAFT);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được xuất bản");
    }

    @Test
    void submitAssignment_studentNotInGroup_throws() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.SPECIFIC_GROUPS);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());

        var ag = new AssignmentGroupEntity();
        ag.setGroupId(UUID.randomUUID());
        when(assignmentGroupRepository.findByAssignmentId(assignmentId))
                .thenReturn(List.of(ag));

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quyền truy cập");
    }

    @Test
    void submitAssignment_deadlineExceeded_notAllowed_throws() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setDeadline(pastDeadline);
        assignment.setAllowLateSubmission(false);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));


        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá hạn nộp");
    }

    @Test
    void submitAssignment_deadlineExceeded_lateAllowed_marksAsLate() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setDeadline(pastDeadline);
        assignment.setAllowLateSubmission(true);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/hw.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        SubmissionResponse result = service.submitAssignment(
                courseId, assignmentId, studentId, null, List.of(file), null);

        assertThat(result.getStatus()).isEqualTo("LATE");
        assertThat(result.isLate()).isTrue();
    }

    @Test
    void submitAssignment_onTime_marksAsSubmitted() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setDeadline(futureDeadline);
        assignment.setAllowLateSubmission(true);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/hw.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        SubmissionResponse result = service.submitAssignment(
                courseId, assignmentId, studentId, null, List.of(file), null);

        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
        assertThat(result.isLate()).isFalse();
    }

    @Test
    void submitAssignment_emptyFiles_throws() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));


        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 1 file");
    }

    @Test
    void submitAssignment_invalidFileType_throws() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setAllowedFileTypes("[\"image/png\",\"image/jpeg\"]");
        var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));


        var files = List.<MultipartFile>of(file);
        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, files, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được hỗ trợ");
    }

    @Test
    void submitAssignment_fileTooLarge_throws() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setMaxFileSizeMb(1);
        var file = new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[2 * 1024 * 1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));


        var files = List.<MultipartFile>of(file);
        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, files, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vượt quá");
    }

    @Test
    void submitAssignment_totalFileSizeExceeds_throws() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setMaxFileSizeMb(3);
        var file1 = new MockMultipartFile("file1", "a.pdf", "application/pdf", new byte[2 * 1024 * 1024]);
        var file2 = new MockMultipartFile("file2", "b.pdf", "application/pdf", new byte[2 * 1024 * 1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));


        var files = List.<MultipartFile>of(file1, file2);
        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, files, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vượt quá");
    }

    @Test
    void submitAssignment_success_uploadsToS3AndSaves() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/hw.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        SubmissionResponse result = service.submitAssignment(
                courseId, assignmentId, studentId, "My note", List.of(file), null);

        assertThat(result.getNote()).isEqualTo("My note");
        assertThat(result.getFiles()).hasSize(1);
        verify(assignmentSubmissionRepository).save(any(AssignmentSubmissionEntity.class));
        verify(s3Client).putObject(any(Consumer.class), any(RequestBody.class));
        verify(submissionFileRepository).save(any());
    }

    // ── new tests for submitAssignment ──

    @Test
    void submitAssignment_beforeStartDate_throws() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setStartDate(now.plusHours(2));
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));


        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa đến thời gian");
    }

    @Test
    void submitAssignment_previousSubmissionWithScorePublished_throws() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var existingSub = new AssignmentSubmissionEntity();
        existingSub.setId(UUID.randomUUID());
        existingSub.setScorePublishedAt(now);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId))
                .thenReturn(List.of(existingSub));

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("công bố điểm");
    }

    @Test
    void submitAssignment_withExistingSubmissions_preservesOldOnes() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var oldSub = new AssignmentSubmissionEntity();
        oldSub.setId(UUID.randomUUID());
        oldSub.setScorePublishedAt(null);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId))
                .thenReturn(List.of(oldSub));
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/hw.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        service.submitAssignment(courseId, assignmentId, studentId, null, List.of(file), null);

        verify(assignmentSubmissionRepository).delete(oldSub);
        verify(assignmentSubmissionRepository).save(any(AssignmentSubmissionEntity.class));
    }

    @Test
    void getAssignmentDetail_withSubmission_includesScorePublishedAt() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var submission = new AssignmentSubmissionEntity();
        submission.setId(UUID.randomUUID());
        submission.setScorePublishedAt(now);
        submission.setScore(BigDecimal.valueOf(8));
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));
        when(assignmentAttachmentRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId))
                .thenReturn(List.of());
        when(assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId))
                .thenReturn(List.of(submission));

        StudentAssignmentDetailResponse result = service.getAssignmentDetail(courseId, assignmentId, studentId);

        assertThat(result.getStudentSubmission()).isNotNull();
        assertThat(result.getStudentSubmission().getScorePublishedAt()).isEqualTo(now);
        assertThat(result.getStudentSubmission().getScore()).isEqualByComparingTo(BigDecimal.valueOf(8));
    }

    @Test
    void submitAssignment_uploadFails_throwsRuntimeException() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 error"));

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(file), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("S3 error");
    }

    @Test
    void submitAssignment_deadlineNull_notLate() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setDeadline(null);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/hw.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        SubmissionResponse result = service.submitAssignment(courseId, assignmentId, studentId, null, List.of(file), null);

        assertThat(result.isLate()).isFalse();
        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void submitAssignment_withExistingSubmission_keepsOldAndCreatesNew() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var oldSub = new AssignmentSubmissionEntity();
        oldSub.setId(UUID.randomUUID());
        oldSub.setScorePublishedAt(null);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        when(assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId))
                .thenReturn(List.of(oldSub));
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/hw.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        service.submitAssignment(courseId, assignmentId, studentId, null, List.of(file), null);

        verify(s3Service, never()).deleteObject(anyString());
        verify(submissionFileRepository, never()).delete(any());
        verify(assignmentSubmissionRepository).delete(oldSub);
        verify(assignmentSubmissionRepository).save(any(AssignmentSubmissionEntity.class));
    }

    @Test
    void submitAssignment_emptyFileInList_throws() {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));


        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(emptyFile), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("File không được để trống");
    }
    // ── resubmit paths ───────────────────────────────────────────────────

    @Test
    void submitAssignment_filesNull_keepFileIdsProvided_reassignsKeptFiles() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var oldSub = new AssignmentSubmissionEntity();
        oldSub.setId(UUID.randomUUID());
        oldSub.setScorePublishedAt(null);
        var oldFile = new SubmissionFileEntity();
        oldFile.setId(UUID.randomUUID());
        oldFile.setSubmissionId(oldSub.getId());
        oldFile.setS3Key("submissions/kept.pdf");
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId))
                .thenReturn(List.of(oldSub));
        when(submissionFileRepository.findBySubmissionIdOrderByOrderIndexAsc(oldSub.getId()))
                .thenReturn(List.of(oldFile));
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/kept.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        service.submitAssignment(courseId, assignmentId, studentId, null, null, List.of(oldFile.getId()));

        verify(s3Client, never()).putObject(any(Consumer.class), any(RequestBody.class));
        verify(submissionFileRepository).save(any(SubmissionFileEntity.class));
        verify(s3Service, never()).deleteObject(anyString());
        verify(submissionFileRepository, never()).delete(any());
        verify(assignmentSubmissionRepository).delete(oldSub);
        verify(assignmentSubmissionRepository).save(any(AssignmentSubmissionEntity.class));
    }

    @Test
    void submitAssignment_withExistingFiles_reassignsKeptAndDeletesOthers() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var oldSub = new AssignmentSubmissionEntity();
        oldSub.setId(UUID.randomUUID());
        oldSub.setScorePublishedAt(null);
        var keptFile = new SubmissionFileEntity();
        keptFile.setId(UUID.randomUUID());
        keptFile.setSubmissionId(oldSub.getId());
        keptFile.setS3Key("submissions/kept.pdf");
        var deletedFile = new SubmissionFileEntity();
        deletedFile.setId(UUID.randomUUID());
        deletedFile.setSubmissionId(oldSub.getId());
        deletedFile.setS3Key("submissions/deleted.pdf");
        var newFile = new MockMultipartFile("file", "new.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId))
                .thenReturn(List.of(oldSub));
        when(submissionFileRepository.findBySubmissionIdOrderByOrderIndexAsc(oldSub.getId()))
                .thenReturn(List.of(keptFile, deletedFile));
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/kept.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        service.submitAssignment(courseId, assignmentId, studentId, null, List.of(newFile), List.of(keptFile.getId()));

        verify(submissionFileRepository).save(argThat(f -> f.getId().equals(keptFile.getId())));
        verify(s3Service).deleteObject(deletedFile.getS3Key());
        verify(submissionFileRepository).delete(deletedFile);
        verify(assignmentSubmissionRepository).delete(oldSub);
    }

    @Test
    void submitAssignment_withExistingFiles_keepFileIdsNull_deletesAllOldFiles() throws Exception {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var oldSub = new AssignmentSubmissionEntity();
        oldSub.setId(UUID.randomUUID());
        oldSub.setScorePublishedAt(null);
        var oldFile = new SubmissionFileEntity();
        oldFile.setId(UUID.randomUUID());
        oldFile.setSubmissionId(oldSub.getId());
        oldFile.setS3Key("submissions/old.pdf");
        var newFile = new MockMultipartFile("file", "new.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentSubmissionRepository
                .findByAssignmentIdAndStudentIdOrderByCreatedAtDesc(assignmentId, studentId))
                .thenReturn(List.of(oldSub));
        when(submissionFileRepository.findBySubmissionIdOrderByOrderIndexAsc(oldSub.getId()))
                .thenReturn(List.of(oldFile));
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/new.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        service.submitAssignment(courseId, assignmentId, studentId, null, List.of(newFile), null);

        verify(s3Service).deleteObject(oldFile.getS3Key());
        verify(submissionFileRepository).delete(oldFile);
        verify(assignmentSubmissionRepository).delete(oldSub);
        verify(s3Client).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    void submitAssignment_s3UploadThrowsIoException_throws() throws IOException {
        enrollStudent();
        enrollInGroup();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("hw.pdf");
        when(file.getSize()).thenReturn(1024L);
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenThrow(new IOException("Stream closed"));
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of(file), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Upload file thất bại");
    }

    // ── end new tests ──
    private void enrollStudent() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(true);
    }

    private void enrollInGroup() {
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of(groupId));
    }

    private AssignmentEntity assignmentEntity(AssignmentStatus status) {
        return assignmentEntity(status, AssignmentScope.ALL_GROUPS);
    }

    private AssignmentEntity assignmentEntity(AssignmentStatus status, AssignmentScope scope) {
        var e = new AssignmentEntity();
        e.setId(assignmentId);
        e.setCourseId(courseId);
        e.setCreatedBy(UUID.randomUUID());
        e.setTitle("Test Assignment");
        e.setDescription("Description");
        e.setStatus(status);
        e.setScope(scope);
        e.setDeadline(futureDeadline);
        e.setStartDate(now);
        e.setAllowLateSubmission(true);
        e.setLatePenaltyPercent(10);
        e.setMaxScore(BigDecimal.TEN);
        e.setPassingScore(BigDecimal.valueOf(5));
        e.setMaxFileSizeMb(10);
        e.setDeadline(futureDeadline);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private Course course() {
        var c = new Course();
        c.setId(courseId);
        c.setTitle("Course Title");
        return c;
    }
}
