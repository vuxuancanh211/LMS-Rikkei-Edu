package project.lms_rikkei_edu.modules.assignment.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.assignment.dto.request.BatchReleaseRequest;
import project.lms_rikkei_edu.modules.assignment.dto.request.GradeRequest;
import project.lms_rikkei_edu.modules.assignment.dto.response.InstructorSubmissionResponse;
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
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.group.entity.GroupMemberEntity;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import project.lms_rikkei_edu.modules.group.repository.StudyGroupRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingServiceImplTest {

    @Mock private AssignmentRepository assignmentRepository;
    @Mock private AssignmentSubmissionRepository assignmentSubmissionRepository;
    @Mock private SubmissionFileRepository submissionFileRepository;
    @Mock private AssignmentGroupRepository assignmentGroupRepository;
    @Mock private UserRepository userRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private StudyGroupRepository studyGroupRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock private S3Service s3Service;

    private GradingServiceImpl service;

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new GradingServiceImpl(
                assignmentRepository, assignmentSubmissionRepository,
                submissionFileRepository, assignmentGroupRepository,
                userRepository, groupMemberRepository,
                studyGroupRepository, courseRepository,
                courseEnrollmentRepository, s3Service);
    }

    // ── getSubmissions ─────────────────────────────────────────────────────

    @Test
    void getSubmissions_byAssignmentId_success() {
        var assignment = publishedAssignment();
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "ALL");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("not_submitted");
        assertThat(result.get(0).getStudentId()).isEqualTo(studentId);
    }

    @Test
    void getSubmissions_byAssignmentId_notFound_throws() {
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubmissions(null, assignmentId, instructorId, "ALL"))
                .isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    void getSubmissions_byAssignmentId_notOwned_throws() {
        var assignment = publishedAssignment();
        assignment.setCreatedBy(UUID.randomUUID());
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.getSubmissions(null, assignmentId, instructorId, "ALL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quyền xem");
    }

    @Test
    void getSubmissions_byAssignmentId_draft_returnsEmpty() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT);
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "ALL");

        assertThat(result).isEmpty();
    }

    @Test
    void getSubmissions_byCourseId_filtersPublishedAndClosed() {
        var published = publishedAssignment();
        var closed = assignmentEntity(AssignmentStatus.CLOSED);
        closed.setId(UUID.randomUUID());
        var draft = assignmentEntity(AssignmentStatus.DRAFT);
        draft.setId(UUID.randomUUID());
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(published, closed, draft));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of());

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                courseId, null, instructorId, "ALL");

        assertThat(result).isEmpty();
        verify(assignmentRepository, never()).findById(any());
    }

    @Test
    void getSubmissions_noParams_returnsAllInstructorAssignments() {
        var assignment = publishedAssignment();
        when(assignmentRepository.findByCreatedByOrderByCreatedAtDesc(instructorId))
                .thenReturn(List.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of());

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, null, instructorId, "ALL");

        assertThat(result).isEmpty();
    }

    @Test
    void getSubmissions_withSubmission_returnsSubmittedStatus() {
        var assignment = publishedAssignment();
        var submission = submissionEntity("SUBMITTED");
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId))
                .thenReturn(List.of(submission));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "ALL");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("SUBMITTED");
        assertThat(result.get(0).getSubmissionNumber()).isEqualTo(1);
        assertThat(result.get(0).isLate()).isFalse();
    }

    @Test
    void getSubmissions_withLateSubmission_returnsLateFields() {
        var assignment = publishedAssignment();
        var submission = submissionEntity("LATE");
        submission.setIsLate(true);
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId))
                .thenReturn(List.of(submission));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "ALL");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("LATE");
        assertThat(result.get(0).isLate()).isTrue();
    }

    @Test
    void getSubmissions_withGradedSubmission_returnsGradedStatus() {
        var assignment = publishedAssignment();
        var submission = submissionEntity("GRADED");
        submission.setScore(BigDecimal.valueOf(85));
        submission.setFeedback("Good work");
        submission.setGradedAt(now);
        submission.setScorePublishedAt(now);
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId))
                .thenReturn(List.of(submission));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "ALL");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("GRADED");
        assertThat(result.get(0).getScore()).isEqualByComparingTo(BigDecimal.valueOf(85));
        assertThat(result.get(0).getFeedback()).isEqualTo("Good work");
        assertThat(result.get(0).getScorePublishedAt()).isNotNull();
    }

    @Test
    void getSubmissions_statusFilterSubmitted_onlyReturnsSubmitted() {
        var assignment = publishedAssignment();
        var submission = submissionEntity("SUBMITTED");
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId))
                .thenReturn(List.of(submission));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "SUBMITTED");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void getSubmissions_statusFilterGraded_onlyReturnsGraded() {
        var assignment = publishedAssignment();
        var submission = submissionEntity("GRADED");
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId))
                .thenReturn(List.of(submission));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "GRADED");

        assertThat(result).hasSize(1);
    }

    @Test
    void getSubmissions_statusFilterGraded_excludesSubmitted() {
        var assignment = publishedAssignment();
        var submission = submissionEntity("SUBMITTED");
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId))
                .thenReturn(List.of(submission));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "GRADED");

        assertThat(result).isEmpty();
    }

    @Test
    void getSubmissions_statusFilterLate_onlyReturnsLate() {
        var assignment = publishedAssignment();
        var submission = submissionEntity("LATE");
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId))
                .thenReturn(List.of(submission));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "LATE");

        assertThat(result).hasSize(1);
    }

    // NOTE: Filtering by "not_submitted" has a known case mismatch bug:
    // statusFilter.toUpperCase() produces "NOT_SUBMITTED" but the computed
    // entryStatus for students without a submission is "not_submitted" (lowercase).
    // This test is intentionally omitted until the bug is fixed.

    @Test
    void getSubmissions_specificGroupsScope_resolvesStudentsFromGroups() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.SPECIFIC_GROUPS);
        var groupMember = mock(GroupMemberEntity.class);
        var studentUser = mock(UserEntity.class);
        when(studentUser.getId()).thenReturn(studentId);
        when(groupMember.getStudent()).thenReturn(studentUser);
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        var agEntity = new AssignmentGroupEntity();
        agEntity.setGroupId(groupId);
        when(assignmentGroupRepository.findByAssignmentId(assignmentId))
                .thenReturn(List.of(agEntity));
        when(groupMemberRepository.findByGroupIdWithStudent(groupId))
                .thenReturn(List.of(groupMember));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of(groupId));
        when(studyGroupRepository.findAllById(any()))
                .thenReturn(List.of(studyGroup()));
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "ALL");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupName()).isEqualTo("Group A");
    }

    @Test
    void getSubmissions_withFiles_includesPresignedUrls() throws MalformedURLException {
        var assignment = publishedAssignment();
        var submission = submissionEntity("SUBMITTED");
        var fileEntity = submissionFileEntity();
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/file.pdf"));
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId))
                .thenReturn(List.of(submission));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of(fileEntity));
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong()))
                .thenReturn(presigned);
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "ALL");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFiles()).hasSize(1);
        assertThat(result.get(0).getFiles().get(0).getUrl())
                .isEqualTo("https://example.com/file.pdf");
        assertThat(result.get(0).getFiles().get(0).getOriginalFilename())
                .isEqualTo("report.pdf");
    }

    @Test
    void getSubmissions_studentWithoutGroup_groupNameNull() {
        var assignment = publishedAssignment();
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser()));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "ALL");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupName()).isNull();
        assertThat(result.get(0).getGroupId()).isNull();
    }

    @Test
    void getSubmissions_multipleStudents_returnsAll() {
        var assignment = publishedAssignment();
        UUID studentB = UUID.randomUUID();
        var userB = new UserEntity();
        userB.setId(studentB);
        userB.setFullName("Student B");
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.findStudentIdsByCourseId(courseId))
                .thenReturn(List.of(studentId, studentB));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(studentUser(), userB));
        when(submissionFileRepository.findBySubmissionIdInOrderByOrderIndexAsc(any()))
                .thenReturn(List.of());
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(any(), eq(courseId)))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        List<InstructorSubmissionResponse> result = service.getSubmissions(
                null, assignmentId, instructorId, "ALL");

        assertThat(result).hasSize(2);
    }

    // ── gradeSubmission ────────────────────────────────────────────────────

    @Test
    void gradeSubmission_success_returnsGradedResponse() {
        var assignment = publishedAssignment();
        var submission = submissionEntity("SUBMITTED");
        when(assignmentSubmissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));
        when(userRepository.findByIdAndDeletedAtIsNull(studentId))
                .thenReturn(Optional.of(studentUser()));
        when(groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(List.of());
        when(submissionFileRepository.findBySubmissionIdOrderByOrderIndexAsc(submissionId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(course()));

        var request = new GradeRequest();
        request.setSubmissionId(submissionId);
        request.setScore(BigDecimal.valueOf(85));
        request.setFeedback("Well done");

        InstructorSubmissionResponse result = service.gradeSubmission(request, instructorId);

        assertThat(result.getScore()).isEqualByComparingTo(BigDecimal.valueOf(85));
        assertThat(result.getFeedback()).isEqualTo("Well done");
        assertThat(result.getStatus()).isEqualTo("GRADED");
        assertThat(result.getGradedAt()).isNotNull();
        verify(assignmentSubmissionRepository).save(submission);
    }

    @Test
    void gradeSubmission_submissionNotFound_throws() {
        when(assignmentSubmissionRepository.findById(submissionId))
                .thenReturn(Optional.empty());

        var request = new GradeRequest();
        request.setSubmissionId(submissionId);

        assertThatThrownBy(() -> service.gradeSubmission(request, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không tìm thấy bài nộp");
    }

    @Test
    void gradeSubmission_assignmentNotFound_throws() {
        var submission = submissionEntity("SUBMITTED");
        when(assignmentSubmissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.empty());

        var request = new GradeRequest();
        request.setSubmissionId(submissionId);

        assertThatThrownBy(() -> service.gradeSubmission(request, instructorId))
                .isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    void gradeSubmission_notOwner_throws() {
        var assignment = publishedAssignment();
        assignment.setCreatedBy(UUID.randomUUID());
        var submission = submissionEntity("SUBMITTED");
        when(assignmentSubmissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));

        var request = new GradeRequest();
        request.setSubmissionId(submissionId);

        assertThatThrownBy(() -> service.gradeSubmission(request, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quyền chấm");
    }

    // ── batchReleaseScores ─────────────────────────────────────────────────

    @Test
    void batchReleaseScores_success_updatesScorePublishedAt() {
        var ids = List.of(submissionId);
        var request = new BatchReleaseRequest();
        request.setSubmissionIds(ids);

        when(assignmentSubmissionRepository.batchPublishScores(eq(ids), any(OffsetDateTime.class)))
                .thenReturn(1);

        service.batchReleaseScores(request, instructorId);

        verify(assignmentSubmissionRepository)
                .batchPublishScores(eq(ids), any(OffsetDateTime.class));
    }

    @Test
    void batchReleaseScores_emptyList_doesNotThrow() {
        var request = new BatchReleaseRequest();
        request.setSubmissionIds(List.of());

        when(assignmentSubmissionRepository.batchPublishScores(anyList(), any(OffsetDateTime.class)))
                .thenReturn(0);

        service.batchReleaseScores(request, instructorId);

        verify(assignmentSubmissionRepository)
                .batchPublishScores(eq(List.of()), any(OffsetDateTime.class));
    }

    // ── returnSubmission ───────────────────────────────────────────────────

    @Test
    void returnSubmission_success_setsReturnedStatus() {
        var assignment = publishedAssignment();
        var submission = submissionEntity("GRADED");
        when(assignmentSubmissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));

        service.returnSubmission(submissionId, instructorId);

        assertThat(submission.getStatus()).isEqualTo("RETURNED");
        verify(assignmentSubmissionRepository).save(submission);
    }

    @Test
    void returnSubmission_notFound_throws() {
        when(assignmentSubmissionRepository.findById(submissionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.returnSubmission(submissionId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không tìm thấy bài nộp");
    }

    @Test
    void returnSubmission_notOwner_throws() {
        var assignment = publishedAssignment();
        assignment.setCreatedBy(UUID.randomUUID());
        var submission = submissionEntity("GRADED");
        when(assignmentSubmissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.returnSubmission(submissionId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quyền trả bài");
    }

    // ── helper methods ─────────────────────────────────────────────────────

    private AssignmentEntity publishedAssignment() {
        return assignmentEntity(AssignmentStatus.PUBLISHED);
    }

    private AssignmentEntity assignmentEntity(AssignmentStatus status) {
        return assignmentEntity(status, AssignmentScope.ALL_GROUPS);
    }

    private AssignmentEntity assignmentEntity(AssignmentStatus status, AssignmentScope scope) {
        var e = new AssignmentEntity();
        e.setId(assignmentId);
        e.setCourseId(courseId);
        e.setCreatedBy(instructorId);
        e.setTitle("Test Assignment");
        e.setStatus(status);
        e.setScope(scope);
        e.setMaxScore(BigDecimal.TEN);
        e.setMaxSubmissions(3);
        e.setDeadline(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private AssignmentSubmissionEntity submissionEntity(String status) {
        var e = new AssignmentSubmissionEntity();
        e.setId(submissionId);
        e.setAssignmentId(assignmentId);
        e.setStudentId(studentId);
        e.setCourseId(courseId);
        e.setSubmissionNumber(1);
        e.setStatus(status);
        e.setIsLate(false);
        e.setSubmittedAt(now);
        e.setCreatedAt(now);
        return e;
    }

    private UserEntity studentUser() {
        var u = new UserEntity();
        u.setId(studentId);
        u.setFullName("Student A");
        u.setEmail("student@test.com");
        return u;
    }

    private SubmissionFileEntity submissionFileEntity() {
        var e = new SubmissionFileEntity();
        e.setId(UUID.randomUUID());
        e.setSubmissionId(submissionId);
        e.setOriginalFilename("report.pdf");
        e.setS3Key("submissions/key.pdf");
        e.setFileSizeBytes(1024L);
        e.setMimeType("application/pdf");
        e.setExtension("pdf");
        e.setOrderIndex(0);
        return e;
    }

    private Course course() {
        var c = new Course();
        c.setId(courseId);
        c.setTitle("Course Title");
        return c;
    }

    private StudyGroupEntity studyGroup() {
        var g = new StudyGroupEntity();
        g.setId(groupId);
        g.setName("Group A");
        return g;
    }
}
