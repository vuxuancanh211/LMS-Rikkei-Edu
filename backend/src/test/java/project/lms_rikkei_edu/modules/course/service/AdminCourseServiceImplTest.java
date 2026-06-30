package project.lms_rikkei_edu.modules.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.exception.CourseNotFoundException;
import project.lms_rikkei_edu.modules.course.exception.CourseStateException;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.impl.AdminCourseServiceImpl;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCourseServiceImplTest {

    @Mock CourseRepository courseRepo;
    @Mock CourseMapper courseMapper;
    @Mock CourseApprovalLogRepository approvalLogRepo;
    @Mock LessonResourceRepository lessonResourceRepo;
    @Mock CourseVersionRepository courseVersionRepo;
    @Mock S3Service s3Service;

    AdminCourseServiceImpl adminCourseService;

    private final UUID adminId  = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminCourseService = new AdminCourseServiceImpl(
                courseRepo, courseMapper,
                approvalLogRepo, lessonResourceRepo, courseVersionRepo,
                s3Service, new ObjectMapper()
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Course courseWithStatus(CourseStatus status) {
        Course c = new Course();
        c.setId(courseId);
        c.setTitle("Test Course");
        c.setStatus(status);
        c.setChapters(new ArrayList<>());
        return c;
    }

    private CourseDetailResponse detailResponse() {
        return CourseDetailResponse.builder()
                .id(courseId).title("Test Course")
                .status(CourseStatus.PUBLISHED).chapters(List.of()).build();
    }

    private CourseResponse courseResponse() {
        return CourseResponse.builder().id(courseId).title("Test Course")
                .status(CourseStatus.PENDING).build();
    }

    // ── listPendingCourses ────────────────────────────────────────────────────

    @Nested
    class ListPendingCourses {

        @Test
        void returnsPageOfPendingCourses() {
            Course c = courseWithStatus(CourseStatus.PENDING);
            PageImpl<Course> page = new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1);
            when(courseRepo.findAllByStatusIn(anyList(), any())).thenReturn(page);
            when(courseMapper.toResponse(c)).thenReturn(courseResponse());

            Page<CourseResponse> result = adminCourseService.listPendingCourses(PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(courseId);
        }

        @Test
        void returnsEmptyPage_whenNoPendingCourses() {
            when(courseRepo.findAllByStatusIn(anyList(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            Page<CourseResponse> result = adminCourseService.listPendingCourses(PageRequest.of(0, 20));

            assertThat(result.isEmpty()).isTrue();
        }
    }

    // ── listAllCourses ────────────────────────────────────────────────────────

    @Nested
    class ListAllCourses {

        @Test
        void returnsAllCourses() {
            Course c = courseWithStatus(CourseStatus.PUBLISHED);
            PageImpl<Course> page = new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1);
            when(courseRepo.findAllByStatusIn(anyList(), any())).thenReturn(page);
            when(courseMapper.toResponse(c)).thenReturn(courseResponse());

            Page<CourseResponse> result = adminCourseService.listAllCourses(PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // ── getCourseDetail ───────────────────────────────────────────────────────

    @Nested
    class GetCourseDetail {

        @Test
        void returnsCourseDetail() {
            Course c = courseWithStatus(CourseStatus.PENDING);
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            CourseDetailResponse result = adminCourseService.getCourseDetail(courseId);

            assertThat(result.getId()).isEqualTo(courseId);
        }

        @Test
        void throwsCourseNotFoundException_whenNotFound() {
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminCourseService.getCourseDetail(courseId))
                    .isInstanceOf(CourseNotFoundException.class);
        }
    }

    // ── getResourceDownloadUrl ────────────────────────────────────────────────

    @Nested
    class GetResourceDownloadUrl {

        @Test
        void returnsPresignedUrl() throws Exception {
            UUID resourceId = UUID.randomUUID();
            LessonResource resource = new LessonResource();
            resource.setS3Key("courses/file.pdf");

            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(URI.create("https://s3.example.com/file").toURL());
            when(lessonResourceRepo.findById(resourceId)).thenReturn(Optional.of(resource));
            when(s3Service.generatePresignedGetUrl("courses/file.pdf")).thenReturn(presigned);

            ResourceDownloadUrlResponse result = adminCourseService.getResourceDownloadUrl(resourceId);

            assertThat(result.getUrl()).contains("s3.example.com");
        }

        @Test
        void throws_whenResourceNotFound() {
            UUID resourceId = UUID.randomUUID();
            when(lessonResourceRepo.findById(resourceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminCourseService.getResourceDownloadUrl(resourceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throws_whenResourceDeleted() {
            UUID resourceId = UUID.randomUUID();
            LessonResource resource = new LessonResource();
            resource.setDeletedAt(Instant.now());

            when(lessonResourceRepo.findById(resourceId)).thenReturn(Optional.of(resource));

            assertThatThrownBy(() -> adminCourseService.getResourceDownloadUrl(resourceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── approveCourse ─────────────────────────────────────────────────────────

    @Nested
    class ApproveCourse {

        @Test
        void approvesAndPublishes_whenPending() {
            Course c = courseWithStatus(CourseStatus.PENDING);
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            CourseDetailResponse result = adminCourseService.approveCourse(adminId, courseId);

            assertThat(c.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(c.getPublishedAt()).isNotNull();
            assertThat(c.getRejectionReason()).isNull();
            verify(approvalLogRepo).save(argThat(log -> "APPROVED_FIRST".equals(log.getAction())));
            assertThat(result.getId()).isEqualTo(courseId);
        }

        @Test
        void updatesCourseVersion_whenVersionExists() {
            Course c = courseWithStatus(CourseStatus.PENDING);
            CourseVersion version = new CourseVersion();
            version.setStatus("PENDING");

            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(version));
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            adminCourseService.approveCourse(adminId, courseId);

            assertThat(version.getStatus()).isEqualTo("APPROVED");
            assertThat(version.getReviewedBy()).isEqualTo(adminId);
        }

        @Test
        void throwsCourseStateException_whenNotPending() {
            Course c = courseWithStatus(CourseStatus.DRAFT);
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> adminCourseService.approveCourse(adminId, courseId))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("PENDING");
        }

        @Test
        void throwsCourseNotFoundException_whenCourseNotFound() {
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminCourseService.approveCourse(adminId, courseId))
                    .isInstanceOf(CourseNotFoundException.class);
        }
    }

    // ── rejectCourse ──────────────────────────────────────────────────────────

    @Nested
    class RejectCourse {

        @Test
        void rejectsAndSetsReason_whenPending() {
            Course c = courseWithStatus(CourseStatus.PENDING);
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            adminCourseService.rejectCourse(adminId, courseId, "Nội dung không đạt yêu cầu");

            assertThat(c.getStatus()).isEqualTo(CourseStatus.REJECTED);
            assertThat(c.getRejectionReason()).isEqualTo("Nội dung không đạt yêu cầu");
            verify(approvalLogRepo).save(argThat(log -> "REJECTED".equals(log.getAction())));
        }

        @Test
        void updatesCourseVersionToRejected() {
            Course c = courseWithStatus(CourseStatus.PENDING);
            CourseVersion version = new CourseVersion();
            version.setStatus("PENDING");

            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(version));
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            adminCourseService.rejectCourse(adminId, courseId, "reason");

            assertThat(version.getStatus()).isEqualTo("REJECTED");
            assertThat(version.getRejectionReason()).isEqualTo("reason");
        }

        @Test
        void throwsCourseStateException_whenNotPending() {
            Course c = courseWithStatus(CourseStatus.PUBLISHED);
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> adminCourseService.rejectCourse(adminId, courseId, "reason"))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("PENDING");
        }
    }

    // ── approveUpdate ─────────────────────────────────────────────────────────

    @Nested
    class ApproveUpdate {

        @Test
        void mergesDraftMetadataAndPublishes() {
            Course c = courseWithStatus(CourseStatus.PENDING_UPDATE);
            c.setDraftTitle("New Title");
            c.setDraftDescription("New Desc");

            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId))
                    .thenReturn(List.of());
            when(lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId))
                    .thenReturn(List.of());
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            adminCourseService.approveUpdate(adminId, courseId);

            assertThat(c.getTitle()).isEqualTo("New Title");
            assertThat(c.getDescription()).isEqualTo("New Desc");
            assertThat(c.getDraftTitle()).isNull();
            assertThat(c.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(c.getPendingUpdateAt()).isNull();
        }

        @Test
        void softDeletesPendingDeleteResources() {
            Course c = courseWithStatus(CourseStatus.PENDING_UPDATE);

            LessonResource r = new LessonResource();
            r.setS3Key("courses/file.pdf");
            r.setPendingDelete(true);

            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId))
                    .thenReturn(List.of(r));
            when(lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId))
                    .thenReturn(List.of());
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            adminCourseService.approveUpdate(adminId, courseId);

            assertThat(r.getDeletedAt()).isNotNull();
            assertThat(r.getStatus()).isEqualTo("DELETED");
            verify(s3Service).deleteObject("courses/file.pdf");
        }

        @Test
        void throwsCourseStateException_whenNotPendingUpdate() {
            Course c = courseWithStatus(CourseStatus.PUBLISHED);
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> adminCourseService.approveUpdate(adminId, courseId))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("PENDING_UPDATE");
        }
    }

    // ── rejectUpdate ──────────────────────────────────────────────────────────

    @Nested
    class RejectUpdate {

        @Test
        void revertsToPublishedAndSetsDraftRejectionReason() {
            Course c = courseWithStatus(CourseStatus.PENDING_UPDATE);
            c.setChangeSummary("some change");

            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId))
                    .thenReturn(List.of());
            when(lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId))
                    .thenReturn(List.of());
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            adminCourseService.rejectUpdate(adminId, courseId, "Ảnh bìa không đạt");

            assertThat(c.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(c.getDraftRejectionReason()).isEqualTo("Ảnh bìa không đạt");
            assertThat(c.getChangeSummary()).isNull();
            assertThat(c.getPendingUpdateAt()).isNull();
            verify(approvalLogRepo).save(argThat(log -> "REJECTED_UPDATE".equals(log.getAction())));
        }

        @Test
        void restoresPendingDeleteResources() {
            Course c = courseWithStatus(CourseStatus.PENDING_UPDATE);
            LessonResource r = new LessonResource();
            r.setPendingDelete(true);

            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId))
                    .thenReturn(List.of(r));
            when(lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId))
                    .thenReturn(List.of());
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            adminCourseService.rejectUpdate(adminId, courseId, "reason");

            assertThat(r.getPendingDelete()).isFalse();
            assertThat(r.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        void throwsCourseStateException_whenNotPendingUpdate() {
            Course c = courseWithStatus(CourseStatus.PENDING);
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> adminCourseService.rejectUpdate(adminId, courseId, "reason"))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── getVersionDiff ────────────────────────────────────────────────────────

    @Nested
    class GetVersionDiff {

        @Test
        void returnsDiff_whenPendingVersionExists() {
            CourseVersion pending = new CourseVersion();
            pending.setId(UUID.randomUUID());
            pending.setVersionNumber(2);
            pending.setSnapshot("{\"title\":\"New\",\"chapters\":[]}");

            CourseVersion approved = new CourseVersion();
            approved.setId(UUID.randomUUID());
            approved.setVersionNumber(1);
            approved.setSnapshot("{\"title\":\"Old\",\"chapters\":[]}");

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = adminCourseService.getVersionDiff(courseId);

            assertThat(result.getPendingVersionNumber()).isEqualTo(2);
            assertThat(result.getApprovedVersionNumber()).isEqualTo(1);
            assertThat(result.getMetadata().getTitle().isChanged()).isTrue();
        }

        @Test
        void returnsDiff_whenNoApprovedVersionYet() {
            CourseVersion pending = new CourseVersion();
            pending.setId(UUID.randomUUID());
            pending.setVersionNumber(1);
            pending.setSnapshot("{\"title\":\"First\",\"chapters\":[]}");

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.empty());

            CourseDiffResponse result = adminCourseService.getVersionDiff(courseId);

            assertThat(result.getApprovedVersionId()).isNull();
            assertThat(result.getPendingVersionNumber()).isEqualTo(1);
        }

        @Test
        void throwsCourseStateException_whenNoPendingVersion() {
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminCourseService.getVersionDiff(courseId))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("PENDING");
        }
    }
}
