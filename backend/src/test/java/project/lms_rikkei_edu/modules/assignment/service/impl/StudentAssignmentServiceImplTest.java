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
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentAttachmentResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentListResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.SubmissionResponse;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentAttachmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentGroupEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentSubmissionEntity;
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
import static org.mockito.Mockito.mock;
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
                .thenReturn(List.of());

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).hasSize(2);
    }

    @Test
    void getAssignments_allGroupsScope_includesAllEnrolled() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(assignmentId);
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
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var submission = new AssignmentSubmissionEntity();
        submission.setStatus("SUBMITTED");
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);
        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId))
                .thenReturn(Optional.of(submission));

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubmissionStatus()).isEqualTo("SUBMITTED");
        assertThat(result.get(0).getScore()).isNull();
    }

    @Test
    void getAssignments_withGradedSubmission_showsScore() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var submission = new AssignmentSubmissionEntity();
        submission.setStatus("GRADED");
        submission.setScore(BigDecimal.valueOf(85));
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);
        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId))
                .thenReturn(Optional.of(submission));

        List<StudentAssignmentListResponse> result = service.getAssignments(courseId, studentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScore()).isEqualByComparingTo(BigDecimal.valueOf(85));
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
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
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
        assertThat(result.getStudentSubmission()).isNull();
    }

    @Test
    void getAssignmentDetail_withAttachments_includesPresignedUrls() throws Exception {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var attachment = new AssignmentAttachmentEntity();
        attachment.setId(UUID.randomUUID());
        attachment.setAssignmentId(assignmentId);
        attachment.setS3Key("attachments/key.pdf");
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/doc.pdf"));
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
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

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không tham gia");
    }

    @Test
    void submitAssignment_assignmentNotFound_throws() {
        enrollStudent();
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of()))
                .isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    void submitAssignment_notPublished_throws() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.DRAFT);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of()))
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

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quyền truy cập");
    }

    @Test
    void submitAssignment_deadlineExceeded_notAllowed_throws() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setDeadline(pastDeadline);
        assignment.setAllowLateSubmission(false);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá hạn nộp");
    }

    @Test
    void submitAssignment_deadlineExceeded_lateAllowed_marksAsLate() throws Exception {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setDeadline(pastDeadline);
        assignment.setAllowLateSubmission(true);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/hw.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        SubmissionResponse result = service.submitAssignment(
                courseId, assignmentId, studentId, null, List.of(file));

        assertThat(result.getStatus()).isEqualTo("LATE");
        assertThat(result.isLate()).isTrue();
    }

    @Test
    void submitAssignment_onTime_marksAsSubmitted() throws Exception {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setDeadline(futureDeadline);
        assignment.setAllowLateSubmission(true);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/hw.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        SubmissionResponse result = service.submitAssignment(
                courseId, assignmentId, studentId, null, List.of(file));

        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
        assertThat(result.isLate()).isFalse();
    }

    @Test
    void submitAssignment_emptyFiles_throws() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 1 file");
    }

    @Test
    void submitAssignment_invalidFileType_throws() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setAllowedFileTypes("[\"image/png\",\"image/jpeg\"]");
        var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());

        var files = List.<MultipartFile>of(file);
        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, files))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được hỗ trợ");
    }

    @Test
    void submitAssignment_fileTooLarge_throws() {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        assignment.setMaxFileSizeMb(1);
        var file = new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[2 * 1024 * 1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());

        var files = List.<MultipartFile>of(file);
        assertThatThrownBy(() -> service.submitAssignment(courseId, assignmentId, studentId, null, files))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vượt quá kích thước");
    }

    @Test
    void submitAssignment_success_uploadsToS3AndSaves() throws Exception {
        enrollStudent();
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED);
        var file = new MockMultipartFile("file", "hw.pdf", "application/pdf", new byte[1024]);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(null);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/hw.pdf"));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);

        SubmissionResponse result = service.submitAssignment(
                courseId, assignmentId, studentId, "My note", List.of(file));

        assertThat(result.getNote()).isEqualTo("My note");
        assertThat(result.getFiles()).hasSize(1);
        verify(assignmentSubmissionRepository).save(any(AssignmentSubmissionEntity.class));
        verify(s3Client).putObject(any(Consumer.class), any(RequestBody.class));
        verify(submissionFileRepository).save(any());
    }

    // ── helper methods ─────────────────────────────────────────────────────

    private void enrollStudent() {
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(true);
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
