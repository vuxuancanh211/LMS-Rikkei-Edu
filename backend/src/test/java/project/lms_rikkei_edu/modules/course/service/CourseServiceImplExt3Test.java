package project.lms_rikkei_edu.modules.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseVersionResponse;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResponse;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.exception.CourseStateException;
import project.lms_rikkei_edu.modules.course.mapper.*;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.impl.CourseServiceImpl;
import project.lms_rikkei_edu.modules.quiz.repository.QuizRepository;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseServiceImplExt3Test {

    @Mock CourseRepository courseRepository;
    @Mock ChapterRepository chapterRepository;
    @Mock LessonRepository lessonRepository;
    @Mock LessonResourceRepository lessonResourceRepository;
    @Mock CourseCategoryRepository categoryRepository;
    @Mock CourseApprovalLogRepository approvalLogRepository;
    @Mock CourseVersionRepository courseVersionRepository;
    @Mock CourseMapper courseMapper;
    @Mock ChapterMapper chapterMapper;
    @Mock LessonMapper lessonMapper;
    @Mock ObjectMapper objectMapper;
    @Mock EntityManager entityManager;
    @Mock S3Service s3Service;
    @Mock QuizService quizService;
    @Mock QuizRepository quizRepository;
    @Mock StudentCourseService studentCourseService;

    CourseServiceImpl service;

    static final UUID INSTRUCTOR_ID = UUID.randomUUID();
    static final UUID COURSE_ID     = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CourseServiceImpl(
                courseRepository, chapterRepository, lessonRepository,
                lessonResourceRepository, categoryRepository,
                approvalLogRepository, courseVersionRepository,
                courseMapper, objectMapper, chapterMapper, lessonMapper,
                entityManager, s3Service, quizService, quizRepository, studentCourseService
        );
    }

    private Course course(CourseStatus status) {
        return Course.builder()
                .id(COURSE_ID).instructorId(INSTRUCTOR_ID).title("T").slug("t")
                .status(status).chapters(new ArrayList<>()).build();
    }

    private CourseDetailResponse detailResponse() {
        return CourseDetailResponse.builder().id(COURSE_ID).title("T")
                .status(CourseStatus.PUBLISHED).chapters(List.of()).build();
    }

    // ── withdrawFromReview: PUBLISHED + no changes (line 211) ────────────────

    @Nested
    class WithdrawPublishedNoChanges {

        @Test
        void throwsCourseState_whenPublishedWithNoDraftAndNoResourceChanges() {
            Course c = course(CourseStatus.PUBLISHED); // no draft fields, empty chapters

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(lessonResourceRepository.existsByCourseIdAndIsNewInUpdateTrue(COURSE_ID)).thenReturn(false);
            when(lessonResourceRepository.existsByCourseIdAndPendingDeleteTrue(COURSE_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("No pending draft to withdraw");
        }
    }

    // ── hasPendingVersion ─────────────────────────────────────────────────────

    @Nested
    class HasPendingVersion {

        @Test
        void returnsTrue_whenPendingVersionExists() {
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course(CourseStatus.PUBLISHED)));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "PENDING")).thenReturn(1L);

            assertThat(service.hasPendingVersion(INSTRUCTOR_ID, COURSE_ID)).isTrue();
        }

        @Test
        void returnsFalse_whenNoPendingVersion() {
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "PENDING")).thenReturn(0L);

            assertThat(service.hasPendingVersion(INSTRUCTOR_ID, COURSE_ID)).isFalse();
        }
    }

    // ── saveDraft ─────────────────────────────────────────────────────────────

    @Nested
    class SaveDraft {

        @Test
        void savesDraft_whenCourseIsPublished() throws Exception {
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(0L);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            CourseVersion saved = new CourseVersion();
            saved.setId(UUID.randomUUID());
            saved.setStatus("DRAFT");
            when(courseVersionRepository.save(any())).thenReturn(saved);

            CourseVersionResponse result = service.saveDraft(INSTRUCTOR_ID, COURSE_ID, "my draft");

            assertThat(result.getStatus()).isEqualTo("DRAFT");
            verify(courseVersionRepository).save(argThat(v -> "DRAFT".equals(v.getStatus())));
        }

        @Test
        void throwsCourseState_whenPendingUpdate() {
            Course c = course(CourseStatus.PENDING_UPDATE);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.saveDraft(INSTRUCTOR_ID, COURSE_ID, "label"))
                    .isInstanceOf(CourseStateException.class);
        }

        @Test
        void throwsCourseState_whenPending() {
            Course c = course(CourseStatus.PENDING);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.saveDraft(INSTRUCTOR_ID, COURSE_ID, "label"))
                    .isInstanceOf(CourseStateException.class);
        }

        @Test
        void throwsCourseState_whenDraftLimitReached() {
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(3L);

            assertThatThrownBy(() -> service.saveDraft(INSTRUCTOR_ID, COURSE_ID, "label"))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("giới hạn 3 bản nháp");
        }

        @Test
        void savesWithNullLabel_whenLabelIsBlank() throws Exception {
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(0L);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            CourseVersion saved = new CourseVersion();
            saved.setId(UUID.randomUUID());
            saved.setStatus("DRAFT");
            when(courseVersionRepository.save(any())).thenReturn(saved);

            service.saveDraft(INSTRUCTOR_ID, COURSE_ID, "  ");

            verify(courseVersionRepository).save(argThat(v -> v.getLabel() == null));
        }
    }

    // ── deleteDraftVersion ────────────────────────────────────────────────────

    @Nested
    class DeleteDraftVersion {

        @Test
        void deletesDraftVersion_successfully() {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.DRAFT);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(COURSE_ID);
            version.setStatus("DRAFT");
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            service.deleteDraftVersion(INSTRUCTOR_ID, COURSE_ID, versionId);

            verify(courseVersionRepository).delete(version);
        }

        @Test
        void deletesRejectedVersion_successfully() {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.DRAFT);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(COURSE_ID);
            version.setStatus("REJECTED");
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            service.deleteDraftVersion(INSTRUCTOR_ID, COURSE_ID, versionId);

            verify(courseVersionRepository).delete(version);
        }

        @Test
        void throwsCourseState_whenVersionNotBelongToCourse() {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.DRAFT);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(UUID.randomUUID()); // different course
            version.setStatus("DRAFT");
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            assertThatThrownBy(() -> service.deleteDraftVersion(INSTRUCTOR_ID, COURSE_ID, versionId))
                    .isInstanceOf(CourseStateException.class);
        }

        @Test
        void throwsCourseState_whenVersionIsApproved() {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(COURSE_ID);
            version.setStatus("APPROVED");
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            assertThatThrownBy(() -> service.deleteDraftVersion(INSTRUCTOR_ID, COURSE_ID, versionId))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("DRAFT hoặc REJECTED");
        }

        @Test
        void cleansUpS3Keys_whenSnapshotHasKeys() throws Exception {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.DRAFT);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            String snapshot = """
                {"chapters":[{"lessons":[{"resources":[{"s3Key":"courses/file.pdf"}]}]}]}
                """;
            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(COURSE_ID);
            version.setStatus("DRAFT");
            version.setSnapshot(snapshot);
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            // Use real ObjectMapper for snapshot parsing
            ObjectMapper realMapper = new ObjectMapper();
            service = new CourseServiceImpl(
                    courseRepository, chapterRepository, lessonRepository,
                    lessonResourceRepository, categoryRepository,
                    approvalLogRepository, courseVersionRepository,
                    courseMapper, realMapper, chapterMapper, lessonMapper,
                    entityManager, s3Service, quizService, quizRepository, studentCourseService
            );
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
            when(lessonResourceRepository.existsByS3KeyAndDeletedAtIsNull("courses/file.pdf")).thenReturn(false);

            service.deleteDraftVersion(INSTRUCTOR_ID, COURSE_ID, versionId);

            verify(s3Service).deleteObject("courses/file.pdf");
            verify(courseVersionRepository).delete(version);
        }
    }

    // ── renameDraftVersion ────────────────────────────────────────────────────

    @Nested
    class RenameDraftVersion {

        @Test
        void renames_successfully() {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.DRAFT);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(COURSE_ID);
            version.setStatus("DRAFT");
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
            when(courseVersionRepository.save(version)).thenReturn(version);

            service.renameDraftVersion(INSTRUCTOR_ID, COURSE_ID, versionId, "  new label  ");

            assertThat(version.getLabel()).isEqualTo("new label");
        }

        @Test
        void setsNullLabel_whenLabelIsNull() {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.DRAFT);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(COURSE_ID);
            version.setStatus("DRAFT");
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
            when(courseVersionRepository.save(version)).thenReturn(version);

            service.renameDraftVersion(INSTRUCTOR_ID, COURSE_ID, versionId, null);

            assertThat(version.getLabel()).isNull();
        }

        @Test
        void throwsCourseState_whenVersionNotDraft() {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(COURSE_ID);
            version.setStatus("APPROVED");
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            assertThatThrownBy(() -> service.renameDraftVersion(INSTRUCTOR_ID, COURSE_ID, versionId, "new"))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── submitVersion ─────────────────────────────────────────────────────────

    @Nested
    class SubmitVersion {

        @Test
        void submitsDraftVersion_andAssignsVersionNumber() {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.DRAFT);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(COURSE_ID);
            version.setStatus("DRAFT");
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
            when(courseVersionRepository.findByCourseIdAndStatusOrderBySubmittedAtDesc(COURSE_ID, "PENDING"))
                    .thenReturn(List.of());
            when(courseVersionRepository.findMaxVersionNumberByCourseId(COURSE_ID)).thenReturn(2);
            when(courseVersionRepository.save(version)).thenReturn(version);

            CourseVersionResponse result = service.submitVersion(INSTRUCTOR_ID, COURSE_ID, versionId);

            assertThat(result.getStatus()).isEqualTo("PENDING");
            assertThat(version.getVersionNumber()).isEqualTo(3);
        }

        @Test
        void throwsCourseState_whenVersionNotDraft() {
            UUID versionId = UUID.randomUUID();
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion version = new CourseVersion();
            version.setId(versionId);
            version.setCourseId(COURSE_ID);
            version.setStatus("PENDING");
            when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            assertThatThrownBy(() -> service.submitVersion(INSTRUCTOR_ID, COURSE_ID, versionId))
                    .isInstanceOf(CourseStateException.class)
                    .hasMessageContaining("bản nháp DRAFT");
        }
    }

    // ── cloneVersionAsDraft ───────────────────────────────────────────────────

    @Nested
    class CloneVersionAsDraft {

        @Test
        void clonesApprovedVersion_withDefaultLabel() {
            UUID sourceId = UUID.randomUUID();
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion source = new CourseVersion();
            source.setId(sourceId);
            source.setCourseId(COURSE_ID);
            source.setStatus("APPROVED");
            source.setVersionNumber(2);
            source.setSnapshot("{}");
            when(courseVersionRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(0L);

            CourseVersion saved = new CourseVersion();
            saved.setId(UUID.randomUUID());
            saved.setStatus("DRAFT");
            saved.setLabel("Clone từ v2");
            when(courseVersionRepository.save(any())).thenReturn(saved);

            CourseVersionResponse result = service.cloneVersionAsDraft(INSTRUCTOR_ID, COURSE_ID, sourceId, null);

            assertThat(result.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        void clonesWithCustomLabel() {
            UUID sourceId = UUID.randomUUID();
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion source = new CourseVersion();
            source.setId(sourceId);
            source.setCourseId(COURSE_ID);
            source.setStatus("APPROVED");
            source.setVersionNumber(1);
            source.setSnapshot("{}");
            when(courseVersionRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(1L);

            CourseVersion saved = new CourseVersion();
            saved.setId(UUID.randomUUID());
            saved.setStatus("DRAFT");
            saved.setLabel("my label");
            when(courseVersionRepository.save(any())).thenReturn(saved);

            CourseVersionResponse result = service.cloneVersionAsDraft(INSTRUCTOR_ID, COURSE_ID, sourceId, "my label");

            verify(courseVersionRepository).save(argThat(v -> "my label".equals(v.getLabel())));
        }

        @Test
        void throwsCourseState_whenDraftLimitReached() {
            UUID sourceId = UUID.randomUUID();
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion source = new CourseVersion();
            source.setId(sourceId);
            source.setCourseId(COURSE_ID);
            source.setStatus("APPROVED");
            when(courseVersionRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(courseVersionRepository.countByCourseIdAndStatus(COURSE_ID, "DRAFT")).thenReturn(3L);

            assertThatThrownBy(() -> service.cloneVersionAsDraft(INSTRUCTOR_ID, COURSE_ID, sourceId, null))
                    .isInstanceOf(CourseStateException.class);
        }

        @Test
        void throwsCourseState_whenVersionNotBelongToCourse() {
            UUID sourceId = UUID.randomUUID();
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion source = new CourseVersion();
            source.setId(sourceId);
            source.setCourseId(UUID.randomUUID());
            when(courseVersionRepository.findById(sourceId)).thenReturn(Optional.of(source));

            assertThatThrownBy(() -> service.cloneVersionAsDraft(INSTRUCTOR_ID, COURSE_ID, sourceId, null))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── getCourseVersions ─────────────────────────────────────────────────────

    @Nested
    class GetCourseVersions {

        @Test
        void returnsVersionList() {
            Course c = course(CourseStatus.PUBLISHED);
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));

            CourseVersion v = new CourseVersion();
            v.setId(UUID.randomUUID());
            v.setVersionNumber(1);
            v.setStatus("APPROVED");
            when(courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(COURSE_ID))
                    .thenReturn(List.of(v));

            var result = service.getCourseVersions(INSTRUCTOR_ID, COURSE_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getVersionNumber()).isEqualTo(1);
        }
    }

    // ── addLesson: isDraft branch when course is live ─────────────────────────

    @Nested
    class AddLesson {

        @Test
        void createsLessonAsDraft_whenCourseIsPublished() {
            UUID chapterId = UUID.randomUUID();
            Course c = course(CourseStatus.PUBLISHED);
            Chapter ch = new Chapter();
            ch.setId(chapterId);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(chapterRepository.findByIdAndCourseId(chapterId, COURSE_ID)).thenReturn(Optional.of(ch));
            when(lessonRepository.findMaxOrderIndexByChapterId(chapterId)).thenReturn(0);

            Lesson saved = new Lesson();
            when(lessonRepository.save(any())).thenReturn(saved);
            when(lessonMapper.toResponse(saved)).thenReturn(LessonResponse.builder().build());

            var request = new project.lms_rikkei_edu.modules.course.dto.request.CreateLessonRequest();
            request.setTitle("Lesson 1");
            request.setType(project.lms_rikkei_edu.modules.course.enums.LessonType.TEXT);

            service.addLesson(INSTRUCTOR_ID, COURSE_ID, chapterId, request);

            verify(lessonRepository).save(argThat(l -> Boolean.TRUE.equals(l.getIsDraft())));
        }
    }
}
