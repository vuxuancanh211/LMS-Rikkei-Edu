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
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.mapper.*;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.impl.CourseServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseServiceImplExt2Test {

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
                entityManager, s3Service
        );
    }

    // ── entity builders ───────────────────────────────────────────────────────

    private Course course(CourseStatus status, List<Chapter> chapters) {
        return Course.builder()
                .id(COURSE_ID).instructorId(INSTRUCTOR_ID).title("T")
                .status(status).chapters(new ArrayList<>(chapters)).build();
    }

    private Chapter chapter(boolean isDraft, boolean pendingDelete, List<Lesson> lessons) {
        Chapter ch = new Chapter();
        ch.setIsDraft(isDraft);
        ch.setPendingDelete(pendingDelete);
        ch.setTitle("Chapter");
        ch.setOrderIndex(1);
        ch.setLessons(new ArrayList<>(lessons));
        return ch;
    }

    private Lesson lesson(boolean isDraft, boolean pendingDelete, String draftTitle, String draftContent,
                          List<LessonResource> resources) {
        Lesson l = new Lesson();
        l.setIsDraft(isDraft);
        l.setPendingDelete(pendingDelete);
        l.setTitle("Lesson");
        l.setOrderIndex(1);
        l.setDraftTitle(draftTitle);
        l.setDraftContentText(draftContent);
        l.setResources(new ArrayList<>(resources));
        return l;
    }

    private LessonResource resource(String s3Key) {
        LessonResource r = new LessonResource();
        r.setS3Key(s3Key);
        return r;
    }

    private CourseDetailResponse detailResponse() {
        return CourseDetailResponse.builder()
                .id(COURSE_ID).title("T").status(CourseStatus.PUBLISHED).chapters(List.of()).build();
    }

    // ── getCourseDetail: line 88 — forEach on lessons.getResources() ──────────

    @Nested
    class GetCourseDetail {

        @Test
        void touchesLessonResources_whenChapterHasLessons() {
            Lesson l = lesson(false, false, null, null, List.of());
            Chapter ch = chapter(false, false, List.of(l));
            Course c = course(CourseStatus.DRAFT, List.of(ch));

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            CourseDetailResponse result = service.getCourseDetail(INSTRUCTOR_ID, COURSE_ID);

            assertThat(result).isNotNull();
        }
    }

    // ── submitForApproval: PENDING_UPDATE branch (lines 150–155) ─────────────

    @Nested
    class SubmitForApproval {

        private void stubVersionRepos() throws Exception {
            when(courseVersionRepository.findByCourseIdAndStatusOrderBySubmittedAtDesc(COURSE_ID, "PENDING"))
                    .thenReturn(List.of());
            when(courseVersionRepository.findMaxVersionNumberByCourseId(COURSE_ID)).thenReturn(1);
            when(courseVersionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        }

        // lines 150–155: PENDING_UPDATE branch
        @Test
        void submits_whenStatusIsPendingUpdate() throws Exception {
            Course c = course(CourseStatus.PENDING_UPDATE, List.of());
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());
            stubVersionRepos();

            service.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "Re-submit after rejection");

            assertThat(c.getChangeSummary()).isEqualTo("Re-submit after rejection");
            assertThat(c.getDraftRejectionReason()).isNull();
            verify(approvalLogRepository).save(argThat(log -> "SUBMITTED_UPDATE".equals(log.getAction())));
        }

        // lines 164–171: PUBLISHED branch with hasDraftChanges=true
        @Test
        void submits_whenStatusIsPublishedWithDraftChanges() throws Exception {
            // isDraft chapter → isHasPendingDraft() returns true
            Chapter draftCh = chapter(true, false, List.of());
            Course c = course(CourseStatus.PUBLISHED, List.of(draftCh));

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());
            stubVersionRepos();

            service.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "First update");

            assertThat(c.getStatus()).isEqualTo(CourseStatus.PENDING_UPDATE);
            assertThat(c.getPendingUpdateAt()).isNotNull();
            assertThat(c.getDraftRejectionReason()).isNull();
            verify(approvalLogRepository).save(argThat(log -> "SUBMITTED_UPDATE".equals(log.getAction())));
        }

        // lines 483–484: saveLogWithSnapshot catch when objectMapper throws
        @Test
        void logsWarn_whenObjectMapperThrowsInSaveLogWithSnapshot() throws Exception {
            Course c = course(CourseStatus.PENDING_UPDATE, List.of());
            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());
            when(courseVersionRepository.findByCourseIdAndStatusOrderBySubmittedAtDesc(COURSE_ID, "PENDING"))
                    .thenReturn(List.of());
            when(courseVersionRepository.findMaxVersionNumberByCourseId(COURSE_ID)).thenReturn(0);
            when(courseVersionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            // objectMapper always throws → createCourseVersion catch + saveLogWithSnapshot catch (483-484)
            when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("json error"));

            // Should NOT throw — both catch blocks handle the error and continue
            service.submitForApproval(INSTRUCTOR_ID, COURSE_ID, "summary");

            // approvalLogRepository.save is called with null snapshot (because json failed)
            verify(approvalLogRepository).save(any());
        }
    }

    // ── withdrawFromReview: various branches ──────────────────────────────────

    @Nested
    class WithdrawFromReview {

        // lines 202–204: PENDING_UPDATE — version present in ifPresent
        @Test
        void reverts_pendingVersionToDraft_whenStatusIsPendingUpdate() {
            Course c = course(CourseStatus.PENDING_UPDATE, List.of());

            CourseVersion version = new CourseVersion();
            version.setStatus("PENDING");

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseVersionRepository.findFirstByCourseIdAndStatus(COURSE_ID, "PENDING"))
                    .thenReturn(Optional.of(version));
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            service.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID);

            assertThat(version.getStatus()).isEqualTo("DRAFT");
            assertThat(c.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        }

        // lines 206–222: PUBLISHED branch — hasPendingDraft=true
        @Test
        void clearsDraft_whenStatusIsPublishedWithDraftChanges() {
            Chapter draftCh = chapter(true, false, List.of());
            Course c = course(CourseStatus.PUBLISHED, List.of(draftCh));

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(lessonResourceRepository.findAllByCourseIdAndIsNewInUpdateTrue(COURSE_ID)).thenReturn(List.of());
            when(lessonResourceRepository.findAllByCourseIdAndPendingDeleteTrue(COURSE_ID)).thenReturn(List.of());
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            service.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID);

            assertThat(c.getPendingUpdateAt()).isNull();
            assertThat(c.getSubmittedAt()).isNull();
        }

        // lines 206–222: resource flags reset in PUBLISHED branch
        @Test
        void resetsResourceFlags_whenStatusIsPublishedWithResourceChanges() {
            // hasDraft=false, but hasResourceChanges=true
            Course c = course(CourseStatus.PUBLISHED, List.of());

            LessonResource newResource = new LessonResource();
            newResource.setIsNewInUpdate(true);

            LessonResource pendingDeleteResource = new LessonResource();
            pendingDeleteResource.setPendingDelete(true);

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(lessonResourceRepository.existsByCourseIdAndIsNewInUpdateTrue(COURSE_ID)).thenReturn(true);
            when(lessonResourceRepository.findAllByCourseIdAndIsNewInUpdateTrue(COURSE_ID))
                    .thenReturn(List.of(newResource));
            when(lessonResourceRepository.findAllByCourseIdAndPendingDeleteTrue(COURSE_ID))
                    .thenReturn(List.of(pendingDeleteResource));
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            service.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID);

            assertThat(newResource.getIsNewInUpdate()).isFalse();
            assertThat(pendingDeleteResource.getPendingDelete()).isFalse();
            assertThat(pendingDeleteResource.getStatus()).isEqualTo("ACTIVE");
        }
    }

    // ── clearAllDrafts: lines 393, 411–445, 448,450,453,457, 461–463 ─────────

    @Nested
    class ClearAllDraftsViaWithdraw {

        /**
         * Full clearAllDrafts scenario in PENDING_UPDATE withdraw:
         * - Draft chapter with lesson+resource (S3 key) → draftChapters stream (448,450,453,457)
         * - Live chapter with isDraft lesson (has resource) → draftLessons → S3 delete (438-441)
         * - Live chapter with live lesson with draftTitle/draftContentText → cleared (422-425)
         * - S3 delete for chapter keys succeeds (460-463 happy path)
         */
        @Test
        void clearsAllDraftContent_withDraftChapterAndDraftLessons() {
            LessonResource chapRes = resource("courses/chapter-resource.mp4");
            Lesson draftChLesson = lesson(false, false, null, null, List.of(chapRes));
            Chapter draftCh = chapter(true, false, List.of(draftChLesson)); // isDraft=true

            LessonResource lessonRes = resource("courses/lesson-resource.pdf");
            Lesson draftLesson = lesson(true, false, null, null, List.of(lessonRes)); // isDraft=true
            Lesson liveLesson  = lesson(false, false, "New Title", "New Content", List.of());
            Chapter liveCh = chapter(false, false, List.of(draftLesson, liveLesson));

            Course c = course(CourseStatus.PENDING_UPDATE, List.of(draftCh, liveCh));

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseVersionRepository.findFirstByCourseIdAndStatus(COURSE_ID, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            service.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID);

            // Live lesson metadata was cleared
            assertThat(liveLesson.getDraftTitle()).isNull();
            assertThat(liveLesson.getDraftContentText()).isNull();
            // Draft lessons removed from live chapter
            assertThat(liveCh.getLessons()).doesNotContain(draftLesson);
            // Draft chapter removed
            assertThat(c.getChapters()).doesNotContain(draftCh);
            // S3 called for both keys
            verify(s3Service).deleteObject("courses/lesson-resource.pdf");
            verify(s3Service).deleteObject("courses/chapter-resource.mp4");
        }

        // line 393 (initChapters forEach body) + line 448,450,453 with ext:// S3 key skipped
        @Test
        void skipsExtS3Keys_fromDraftChapterStream() {
            LessonResource extRes = resource("ext://youtube.com/video");
            Lesson draftChLesson = lesson(false, false, null, null, List.of(extRes));
            Chapter draftCh = chapter(true, false, List.of(draftChLesson));

            Course c = course(CourseStatus.PENDING_UPDATE, List.of(draftCh));

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseVersionRepository.findFirstByCourseIdAndStatus(COURSE_ID, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            service.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID);

            // ext:// key filtered out → s3Service.deleteObject NOT called
            verify(s3Service, never()).deleteObject(anyString());
            assertThat(c.getChapters()).doesNotContain(draftCh);
        }

        // lines 461–463: S3 delete for draft chapter keys fails → log warn, no exception
        @Test
        void logsWarn_whenS3DeleteFailsForDraftChapterKey() {
            LessonResource chapRes = resource("courses/failing-key.mp4");
            Lesson draftChLesson = lesson(false, false, null, null, List.of(chapRes));
            Chapter draftCh = chapter(true, false, List.of(draftChLesson));

            Course c = course(CourseStatus.PENDING_UPDATE, List.of(draftCh));

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseVersionRepository.findFirstByCourseIdAndStatus(COURSE_ID, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());
            doThrow(new RuntimeException("S3 error")).when(s3Service).deleteObject("courses/failing-key.mp4");

            // Must not throw — logs warn and continues
            service.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID);

            assertThat(c.getChapters()).doesNotContain(draftCh);
        }

        // S3 delete for draft LESSON keys (inside live chapter) fails → log warn (lines 438-441)
        @Test
        void logsWarn_whenS3DeleteFailsForDraftLessonKey() {
            LessonResource lessonRes = resource("courses/lesson-s3-fail.pdf");
            Lesson draftLesson = lesson(true, false, null, null, List.of(lessonRes));
            Chapter liveCh = chapter(false, false, List.of(draftLesson));

            Course c = course(CourseStatus.PENDING_UPDATE, List.of(liveCh));

            when(courseRepository.findByIdWithCategory(COURSE_ID)).thenReturn(Optional.of(c));
            when(courseVersionRepository.findFirstByCourseIdAndStatus(COURSE_ID, "PENDING"))
                    .thenReturn(Optional.empty());
            when(courseRepository.save(c)).thenReturn(c);
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());
            doThrow(new RuntimeException("S3 fail")).when(s3Service).deleteObject("courses/lesson-s3-fail.pdf");

            // Must not throw
            service.withdrawFromReview(INSTRUCTOR_ID, COURSE_ID);

            assertThat(liveCh.getLessons()).doesNotContain(draftLesson);
        }
    }
}
