package project.lms_rikkei_edu.modules.assignment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
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
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.group.repository.StudyGroupRepository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.util.function.Consumer;

import java.math.BigDecimal;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceImplTest {

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private AssignmentGroupRepository assignmentGroupRepository;
    @Mock
    private AssignmentAttachmentRepository assignmentAttachmentRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private AssignmentMapper assignmentMapper;
    @Mock
    private S3Client s3Client;
    @Mock
    private S3Service s3Service;
    @Mock
    private StudyGroupRepository studyGroupRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AssignmentServiceImpl service;

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final OffsetDateTime futureDeadline = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
    private final OffsetDateTime futureStartDate = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);

    @BeforeEach
    void setUp() {
        service = new AssignmentServiceImpl(
                assignmentRepository, assignmentGroupRepository,
                assignmentAttachmentRepository, courseRepository,
                assignmentMapper, studyGroupRepository, s3Client, s3Service, objectMapper);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "presignedUrlExpiry", 3600L);
    }

    // ── createAssignment ─────────────────────────────────────────────────────

    @Test
    void createAssignment_success_allGroups() {
        var request = createRequest(AssignmentScope.ALL_GROUPS);
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        var response = assignmentResponse();

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));
        when(assignmentRepository.save(any())).thenReturn(assignment);
        when(assignmentMapper.toResponse(any())).thenReturn(response);

        AssignmentResponse result = service.createAssignment(courseId, instructorId, request);

        assertThat(result).isEqualTo(response);
        var captor = ArgumentCaptor.forClass(AssignmentEntity.class);
        verify(assignmentRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNotNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(AssignmentStatus.DRAFT);
        verify(assignmentGroupRepository, never()).save(any());
    }

    @Test
    void createAssignment_success_specificGroups() {
        UUID groupId = UUID.randomUUID();
        var request = createRequest(AssignmentScope.SPECIFIC_GROUPS);
        request.setGroupIds(List.of(groupId));
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.SPECIFIC_GROUPS);
        var response = assignmentResponse();

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));
        when(assignmentRepository.save(any())).thenReturn(assignment);
        when(assignmentMapper.toResponse(any())).thenReturn(response);

        AssignmentResponse result = service.createAssignment(courseId, instructorId, request);

        assertThat(result).isEqualTo(response);
        verify(assignmentGroupRepository).save(any(AssignmentGroupEntity.class));
    }

    @Test
    void createAssignment_courseNotOwned_throws() {
        var request = createRequest(null);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(false);

        assertThatThrownBy(() -> service.createAssignment(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn không sở hữu");
    }

    @Test
    void createAssignment_courseNotPublished_throws() {
        var course = publishedCourse();
        course.setStatus(CourseStatus.DRAFT);
        var request = createRequest(null);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.createAssignment(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã publish");
    }

    @Test
    void createAssignment_specificGroupsNoGroupIds_throws() {
        var request = createRequest(AssignmentScope.SPECIFIC_GROUPS);
        request.setGroupIds(null);

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));

        assertThatThrownBy(() -> service.createAssignment(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phải chọn");
    }

    @Test
    void createAssignment_invalidDateRange_throws() {
        var request = createRequest(AssignmentScope.ALL_GROUPS);
        request.setStartDate(futureDeadline);
        request.setDeadline(futureStartDate);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));

        assertThatThrownBy(() -> service.createAssignment(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("trước hạn nộp");
    }

    @Test
    void createAssignment_latePenaltyOutOfRange_throws() {
        var request = createRequest(AssignmentScope.ALL_GROUPS);
        request.setAllowLateSubmission(true);
        request.setLatePenaltyPercent(150);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));

        assertThatThrownBy(() -> service.createAssignment(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Late penalty");
    }

    @Test
    void createAssignment_deadlineTooSoon_throws() {
        var request = createRequest(AssignmentScope.ALL_GROUPS);
        request.setDeadline(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        request.setStartDate(null);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));

        assertThatThrownBy(() -> service.createAssignment(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Hạn nộp");
    }

    // ── getAssignments ───────────────────────────────────────────────────────

    @Test
    void getAssignments_success_returnsList() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId))
                .thenReturn(List.of(assignment));
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(3L);
        when(assignmentMapper.toResponse(assignment)).thenReturn(assignmentResponse());

        List<AssignmentResponse> result = service.getAssignments(courseId, instructorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAttachmentCount()).isEqualTo(3);
    }

    @Test
    void getAssignments_courseNotOwned_throws() {
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(false);

        assertThatThrownBy(() -> service.getAssignments(courseId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn không sở hữu");
    }

    // ── getAllAssignments ─────────────────────────────────────────────────────

    @Test
    void getAllAssignments_success_returnsList() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        when(assignmentRepository.findByCreatedByOrderByCreatedAtDesc(instructorId))
                .thenReturn(List.of(assignment));
        when(courseRepository.findAllById(any())).thenReturn(List.of(publishedCourse()));
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);
        when(assignmentMapper.toResponse(assignment)).thenReturn(assignmentResponse());

        List<AssignmentResponse> result = service.getAllAssignments(instructorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCourseTitle()).isEqualTo("Course Title");
    }

    @Test
    void getAllAssignments_noCourses_fallbackTitleNull() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        when(assignmentRepository.findByCreatedByOrderByCreatedAtDesc(instructorId))
                .thenReturn(List.of(assignment));
        when(courseRepository.findAllById(any())).thenReturn(List.of());
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);
        when(assignmentMapper.toResponse(assignment)).thenReturn(assignmentResponse());

        List<AssignmentResponse> result = service.getAllAssignments(instructorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCourseTitle()).isNull();
    }

    // ── getAssignmentDetail ──────────────────────────────────────────────────

    @Test
    void getAssignmentDetail_success_allGroups() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));

        AssignmentDetailResponse result = service.getAssignmentDetail(courseId, assignmentId, instructorId);

        assertThat(result.getGroupIds()).isNull();
        assertThat(result.getCourseTitle()).isEqualTo("Course Title");
    }

    @Test
    void getAssignmentDetail_success_specificGroups() {
        UUID groupId = UUID.randomUUID();
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.SPECIFIC_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        var ag = new AssignmentGroupEntity();
        ag.setGroupId(groupId);
        when(assignmentGroupRepository.findByAssignmentId(assignmentId)).thenReturn(List.of(ag));
        when(assignmentAttachmentRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));

        AssignmentDetailResponse result = service.getAssignmentDetail(courseId, assignmentId, instructorId);

        assertThat(result.getGroupIds()).containsExactly(groupId);
    }

    @Test
    void getAssignmentDetail_attachments_includePresignedUrls() throws Exception {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        var attachmentEntity = new AssignmentAttachmentEntity();
        attachmentEntity.setId(UUID.randomUUID());
        attachmentEntity.setS3Key("assignments/key.pdf");
        var presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://presigned.url/file.pdf"));

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId))
                .thenReturn(List.of(attachmentEntity));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));
        when(s3Service.generatePresignedInlineUrl(attachmentEntity.getS3Key(), 3600L))
                .thenReturn(presignedRequest);
        when(assignmentMapper.toAttachmentResponse(attachmentEntity))
                .thenReturn(AssignmentAttachmentResponse.builder().id(attachmentEntity.getId()).build());

        AssignmentDetailResponse result = service.getAssignmentDetail(courseId, assignmentId, instructorId);

        assertThat(result.getAttachments()).hasSize(1);
        verify(s3Service).generatePresignedInlineUrl(attachmentEntity.getS3Key(), 3600L);
    }

    @Test
    void getAssignmentDetail_assignmentNotFound_throws() {
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAssignmentDetail(courseId, assignmentId, instructorId))
                .isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    void getAssignmentDetail_courseNotOwned_throws() {
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(false);

        assertThatThrownBy(() -> service.getAssignmentDetail(courseId, assignmentId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn không sở hữu");
    }

    // ── updateAssignment ─────────────────────────────────────────────────────

    @Test
    void updateAssignment_draft_updatesAllFields() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        var request = new UpdateAssignmentRequest();
        request.setTitle("Updated Title");
        request.setDescription("Updated Desc");
        var response = assignmentResponse();

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenReturn(assignment);
        when(assignmentMapper.toResponse(any())).thenReturn(response);

        AssignmentResponse result = service.updateAssignment(courseId, assignmentId, instructorId, request);

        assertThat(result).isEqualTo(response);
        assertThat(assignment.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void updateAssignment_draft_specificGroups_replaceGroupIds() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.SPECIFIC_GROUPS);
        UUID newGroupId = UUID.randomUUID();
        var request = new UpdateAssignmentRequest();
        request.setScope(AssignmentScope.SPECIFIC_GROUPS);
        request.setGroupIds(List.of(newGroupId));

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        service.updateAssignment(courseId, assignmentId, instructorId, request);

        verify(assignmentGroupRepository).deleteByAssignmentId(assignmentId);
        verify(assignmentGroupRepository).save(any(AssignmentGroupEntity.class));
    }

    @Test
    void updateAssignment_published_updatesDeadlineOnly() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.ALL_GROUPS);
        var request = new UpdateAssignmentRequest();
        request.setDeadline(futureDeadline);
        var response = assignmentResponse();

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenReturn(assignment);
        when(assignmentMapper.toResponse(any())).thenReturn(response);

        AssignmentResponse result = service.updateAssignment(courseId, assignmentId, instructorId, request);

        assertThat(result).isEqualTo(response);
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void updateAssignment_published_noPublishableChanges_throws() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.ALL_GROUPS);
        var request = new UpdateAssignmentRequest();
        request.setTitle("Can't change title after publish");

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.updateAssignment(courseId, assignmentId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Sau khi publish");
    }

    @Test
    void updateAssignment_closed_throws() {
        var assignment = assignmentEntity(AssignmentStatus.CLOSED, AssignmentScope.ALL_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.updateAssignment(courseId, assignmentId, instructorId, new UpdateAssignmentRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã đóng");
    }

    @Test
    void updateAssignment_published_updatesMaxFileSizeMb() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.ALL_GROUPS);
        var request = new UpdateAssignmentRequest();
        request.setMaxFileSizeMb(50);
        var response = assignmentResponse();

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenReturn(assignment);
        when(assignmentMapper.toResponse(any())).thenReturn(response);

        AssignmentResponse result = service.updateAssignment(courseId, assignmentId, instructorId, request);

        assertThat(result).isEqualTo(response);
        assertThat(assignment.getMaxFileSizeMb()).isEqualTo(50);
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void updateAssignment_published_updatesAllowedFileTypes() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.ALL_GROUPS);
        var request = new UpdateAssignmentRequest();
        request.setAllowedFileTypes(List.of("application/pdf", "image/png"));
        var response = assignmentResponse();

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenReturn(assignment);
        when(assignmentMapper.toResponse(any())).thenReturn(response);

        AssignmentResponse result = service.updateAssignment(courseId, assignmentId, instructorId, request);

        assertThat(result).isEqualTo(response);
        verify(assignmentRepository).save(assignment);
    }

    // ── deleteAssignment ─────────────────────────────────────────────────────

    @Test
    void deleteAssignment_draft_success() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId))
                .thenReturn(List.of());

        service.deleteAssignment(courseId, assignmentId, instructorId);

        verify(assignmentRepository).delete(assignment);
        verify(assignmentGroupRepository).deleteByAssignmentId(assignmentId);
    }

    @Test
    void deleteAssignment_published_throws() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.ALL_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.deleteAssignment(courseId, assignmentId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã publish");
    }

    // ── publishAssignment ────────────────────────────────────────────────────

    @Test
    void publishAssignment_draft_success() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenReturn(assignment);
        when(assignmentMapper.toResponse(any())).thenReturn(assignmentResponse());

        AssignmentResponse result = service.publishAssignment(courseId, assignmentId, instructorId);

        assertThat(result).isNotNull();
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PUBLISHED);
        assertThat(assignment.getPublishedAt()).isNotNull();
    }

    @Test
    void publishAssignment_alreadyPublished_throws() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.ALL_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.publishAssignment(courseId, assignmentId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void publishAssignment_titleTooShort_throws() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        assignment.setTitle("AB");
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.publishAssignment(courseId, assignmentId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5 ký tự");
    }

    @Test
    void publishAssignment_specificGroupsNoGroups_throws() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.SPECIFIC_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentGroupRepository.findByAssignmentId(assignmentId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.publishAssignment(courseId, assignmentId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 1 nhóm");
    }

    // ── closeAssignment ──────────────────────────────────────────────────────

    @Test
    void closeAssignment_published_success() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.ALL_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenReturn(assignment);
        when(assignmentMapper.toResponse(any())).thenReturn(assignmentResponse());

        AssignmentResponse result = service.closeAssignment(courseId, assignmentId, instructorId);

        assertThat(result).isNotNull();
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.CLOSED);
    }

    @Test
    void closeAssignment_draft_throws() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.closeAssignment(courseId, assignmentId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PUBLISHED");
    }

    // ── uploadAttachment ─────────────────────────────────────────────────────

    @Test
    void uploadAttachment_draft_success() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        var file = new MockMultipartFile("file", "image.png", "image/png", new byte[1024]);
        var attachmentResponse = AssignmentAttachmentResponse.builder().id(UUID.randomUUID()).build();

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);
        when(assignmentAttachmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(assignmentMapper.toAttachmentResponse(any())).thenReturn(attachmentResponse);

        AssignmentAttachmentResponse result = service.uploadAttachment(
                courseId, assignmentId, instructorId, file);

        assertThat(result).isEqualTo(attachmentResponse);
        verify(s3Client).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    void uploadAttachment_published_success() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.ALL_GROUPS);
        var file = new MockMultipartFile("file", "image.png", "image/png", new byte[1024]);
        var attachmentResponse = AssignmentAttachmentResponse.builder().id(UUID.randomUUID()).build();

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);
        when(assignmentAttachmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(assignmentMapper.toAttachmentResponse(any())).thenReturn(attachmentResponse);

        AssignmentAttachmentResponse result = service.uploadAttachment(
                courseId, assignmentId, instructorId, file);

        assertThat(result).isEqualTo(attachmentResponse);
        verify(s3Client).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    void uploadAttachment_emptyFile_throws() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        var file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.uploadAttachment(courseId, assignmentId, instructorId, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("File không được");
    }

    @Test
    void uploadAttachment_s3Fails_throws() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        var file = new MockMultipartFile("file", "image.png", "image/png", new byte[1024]);

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 error"));

        assertThatThrownBy(() -> service.uploadAttachment(courseId, assignmentId, instructorId, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("S3 error");
    }

    @Test
    void uploadAttachment_nonImageContentType_doesNotThrow() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[1024]);
        var attachmentResponse = AssignmentAttachmentResponse.builder().id(UUID.randomUUID()).build();

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.countByAssignmentId(assignmentId)).thenReturn(0L);
        when(assignmentAttachmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(assignmentMapper.toAttachmentResponse(any())).thenReturn(attachmentResponse);

        AssignmentAttachmentResponse result = service.uploadAttachment(
                courseId, assignmentId, instructorId, file);

        assertThat(result).isEqualTo(attachmentResponse);
    }

    // ── deleteAttachment ─────────────────────────────────────────────────────

    @Test
    void deleteAttachment_draft_success() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        UUID attachmentId = UUID.randomUUID();
        var attachment = new AssignmentAttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setAssignmentId(assignmentId);
        attachment.setS3Key("assignments/key.pdf");

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        service.deleteAttachment(courseId, assignmentId, attachmentId, instructorId);

        verify(s3Client).deleteObject(any(Consumer.class));
        verify(assignmentAttachmentRepository).delete(attachment);
    }

    @Test
    void deleteAttachment_published_success() {
        var assignment = assignmentEntity(AssignmentStatus.PUBLISHED, AssignmentScope.ALL_GROUPS);
        UUID attachmentId = UUID.randomUUID();
        var attachment = new AssignmentAttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setAssignmentId(assignmentId);
        attachment.setS3Key("assignments/key.pdf");

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        service.deleteAttachment(courseId, assignmentId, attachmentId, instructorId);

        verify(s3Client).deleteObject(any(Consumer.class));
        verify(assignmentAttachmentRepository).delete(attachment);
    }

    @Test
    void deleteAttachment_notOwnedByAssignment_throws() {
        var assignment = assignmentEntity(AssignmentStatus.DRAFT, AssignmentScope.ALL_GROUPS);
        UUID attachmentId = UUID.randomUUID();
        var attachment = new AssignmentAttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setAssignmentId(UUID.randomUUID());

        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
        when(assignmentRepository.findByIdAndCourseId(assignmentId, courseId))
                .thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.deleteAttachment(courseId, assignmentId, attachmentId, instructorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc bài tập");
    }

    // ── helper methods ───────────────────────────────────────────────────────

    private CreateAssignmentRequest createRequest(AssignmentScope scope) {
        var req = new CreateAssignmentRequest();
        req.setTitle("Test Assignment");
        req.setDescription("A description");
        req.setScope(scope);
        req.setDeadline(futureDeadline);
        req.setStartDate(futureStartDate);
        req.setAllowLateSubmission(false);
        req.setLatePenaltyPercent(0);
        req.setMaxScore(BigDecimal.TEN);
        req.setMaxFileSizeMb(10);
        return req;
    }

    private AssignmentEntity assignmentEntity(AssignmentStatus status, AssignmentScope scope) {
        var e = new AssignmentEntity();
        e.setId(assignmentId);
        e.setCourseId(courseId);
        e.setCreatedBy(instructorId);
        e.setTitle("Test Assignment");
        e.setDescription("A description");
        e.setStatus(status);
        e.setScope(scope);
        e.setDeadline(futureDeadline);
        e.setStartDate(futureStartDate);
        e.setAllowLateSubmission(false);
        e.setLatePenaltyPercent(0);
        e.setMaxScore(BigDecimal.TEN);
        e.setMaxFileSizeMb(10);
        e.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        e.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return e;
    }

    private AssignmentResponse assignmentResponse() {
        return AssignmentResponse.builder()
                .id(assignmentId)
                .courseId(courseId)
                .createdBy(instructorId)
                .title("Test Assignment")
                .status(AssignmentStatus.DRAFT)
                .scope(AssignmentScope.ALL_GROUPS)
                .build();
    }

    private Course publishedCourse() {
        var c = new Course();
        c.setId(courseId);
        c.setTitle("Course Title");
        c.setStatus(CourseStatus.PUBLISHED);
        return c;
    }
}
