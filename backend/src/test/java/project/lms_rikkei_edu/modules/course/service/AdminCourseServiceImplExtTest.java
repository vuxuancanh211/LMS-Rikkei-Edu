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
import org.springframework.cache.CacheManager;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDiffResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.impl.AdminCourseServiceImpl;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCourseServiceImplExtTest {

    @Mock CourseRepository courseRepo;
    @Mock CourseMapper courseMapper;
    @Mock CourseApprovalLogRepository approvalLogRepo;
    @Mock LessonResourceRepository lessonResourceRepo;
    @Mock CourseVersionRepository courseVersionRepo;
    @Mock UserRepository userRepository;
    @Mock S3Service s3Service;
    @Mock CacheManager cacheManager;
    @Mock LessonProgressRepository lessonProgressRepo;
    @Mock VideoUploadJobRepository videoUploadJobRepo;
    @Mock project.lms_rikkei_edu.modules.ai.service.LessonAiDataCleanupService lessonAiDataCleanupService;
    @Mock project.lms_rikkei_edu.modules.course.service.impl.CourseVersionReferenceChecker courseVersionReferenceChecker;
    @Mock project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository courseEnrollmentRepository;

    AdminCourseServiceImpl service;

    private final UUID adminId  = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminCourseServiceImpl(
                courseRepo, courseMapper,
                approvalLogRepo, lessonResourceRepo, courseVersionRepo,
                userRepository, s3Service, new ObjectMapper(), cacheManager, lessonProgressRepo, videoUploadJobRepo,
                lessonAiDataCleanupService, courseVersionReferenceChecker, courseEnrollmentRepository
        );
        when(courseVersionReferenceChecker.isSafeToDelete(any(), any())).thenReturn(true);
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(List.of());
    }

    private Course courseWith(CourseStatus status, List<Chapter> chapters) {
        Course c = new Course();
        c.setId(courseId);
        c.setTitle("Test");
        c.setStatus(status);
        c.setChapters(new ArrayList<>(chapters));
        return c;
    }

    private CourseDetailResponse detailResponse() {
        return CourseDetailResponse.builder()
                .id(courseId).title("Test").status(CourseStatus.PUBLISHED)
                .chapters(List.of()).build();
    }

    private Chapter chapter(Boolean isDraft, Boolean pendingDelete, List<Lesson> lessons) {
        Chapter ch = new Chapter();
        ch.setTitle("Chapter");
        ch.setOrderIndex(1);
        ch.setIsDraft(isDraft);
        ch.setPendingDelete(pendingDelete);
        ch.setLessons(lessons != null ? new ArrayList<>(lessons) : new ArrayList<>());
        return ch;
    }

    private Lesson lesson(Boolean isDraft, Boolean pendingDelete, String draftTitle, String draftContent) {
        Lesson l = new Lesson();
        l.setTitle("Lesson");
        l.setOrderIndex(1);
        l.setIsDraft(isDraft);
        l.setPendingDelete(pendingDelete);
        l.setDraftTitle(draftTitle);
        l.setDraftContentText(draftContent);
        l.setResources(new ArrayList<>());
        return l;
    }

    // ── getCourseDetail: line 69 — forEach on lessons.getResources() ──────────

    @Nested
    class GetCourseDetailWithLessons {

        @Test
        void touchesLessonResources_whenChapterHasLessons() {
            Lesson l = lesson(false, false, null, null);
            Chapter ch = chapter(false, false, List.of(l));
            Course c = courseWith(CourseStatus.PENDING, List.of(ch));

            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            CourseDetailResponse result = service.getCourseDetail(courseId);

            assertThat(result.getId()).isEqualTo(courseId);
        }
    }

    // ── approveUpdate: chapter/lesson merge logic ─────────────────────────────

    @Nested
    class ApproveUpdateChapterMerge {

        private void stubApproveUpdate(Course c) {
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId)).thenReturn(List.of());
            when(lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId)).thenReturn(List.of());
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING")).thenReturn(Optional.empty());
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());
        }

        // line 151 — forEach on lessons.getResources() inside approveUpdate
        @Test
        void touchesLessonResources_onForceLoad() {
            Lesson l = lesson(false, false, null, null);
            Chapter ch = chapter(false, false, List.of(l));
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of(ch));
            stubApproveUpdate(c);

            service.approveUpdate(adminId, courseId);

            assertThat(c.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        }

        // lines 163–164 — chapter with pendingDelete=true is soft-deleted, not removed from DB —
        // xóa cứng sẽ làm mất tài liệu không thể phục hồi khi rollback về version cũ còn cần nó
        // (xem CourseVersionReferenceChecker).
        @Test
        void softDeletesPendingDeleteChapter() {
            Chapter ch = chapter(false, true, List.of());
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of(ch));
            stubApproveUpdate(c);

            service.approveUpdate(adminId, courseId);

            assertThat(c.getChapters()).contains(ch);
            assertThat(ch.getDeletedAt()).isNotNull();
            assertThat(ch.getPendingDelete()).isFalse();
        }

        // Regression: lesson_progress.lesson_id và video_upload_jobs.lesson_id có FK REFERENCES
        // lessons(id) nhưng KHÔNG có ON DELETE CASCADE — xóa hẳn 1 chương/bài đã từng LIVE (học
        // viên có thể đã học, hoặc đã từng upload video) mà không dọn 2 bảng này trước sẽ vỡ
        // foreign key constraint ở Postgres, Spring bọc thành lỗi 500 chung chung ("duyệt không
        // được"). Chỉ tái hiện được với dữ liệu thật có học viên/video — unit test ở đây chỉ xác
        // nhận repo dọn dẹp được GỌI đúng lesson id.
        @Test
        void removesPendingDeleteChapter_alsoCleansUpLessonProgressForItsLessons() {
            Lesson l = lesson(false, false, null, null);
            UUID lessonId = UUID.randomUUID();
            l.setId(lessonId);
            Chapter ch = chapter(false, true, List.of(l));
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of(ch));
            stubApproveUpdate(c);

            service.approveUpdate(adminId, courseId);

            verify(lessonProgressRepo).deleteByLessonIdIn(List.of(lessonId));
            verify(videoUploadJobRepo).deleteByLessonIdIn(List.of(lessonId));
            verify(lessonAiDataCleanupService).hardDeleteByLessonIds(List.of(lessonId), List.of());
        }

        @Test
        void removesPendingDeleteLesson_alsoCleansUpLessonProgress() {
            Lesson keep = lesson(false, false, null, null);
            Lesson toDelete = lesson(false, true, null, null);
            UUID lessonId = UUID.randomUUID();
            toDelete.setId(lessonId);
            Chapter ch = chapter(false, false, List.of(keep, toDelete));
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of(ch));
            stubApproveUpdate(c);

            service.approveUpdate(adminId, courseId);

            assertThat(ch.getLessons()).containsExactlyInAnyOrder(keep, toDelete);
            assertThat(toDelete.getDeletedAt()).isNotNull();
            verify(lessonProgressRepo).deleteByLessonIdIn(List.of(lessonId));
            verify(videoUploadJobRepo).deleteByLessonIdIn(List.of(lessonId));
            verify(lessonAiDataCleanupService).hardDeleteByLessonIds(List.of(lessonId), List.of());
        }

        // lines 165–167 — isDraft chapter: clear isDraft flag + all lessons
        @Test
        void clearsDraftFlagOnDraftChapterAndLessons() {
            Lesson l = lesson(true, false, null, null);
            Chapter ch = chapter(true, false, List.of(l));
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of(ch));
            stubApproveUpdate(c);

            service.approveUpdate(adminId, courseId);

            assertThat(ch.getIsDraft()).isFalse();
            assertThat(l.getIsDraft()).isFalse();
        }

        // lines 169–185 — existing chapter: pendingDelete lesson soft-deleted, not removed
        @Test
        void softDeletesPendingDeleteLesson() {
            Lesson l = lesson(false, true, null, null);
            Chapter ch = chapter(false, false, List.of(l));
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of(ch));
            stubApproveUpdate(c);

            service.approveUpdate(adminId, courseId);

            assertThat(ch.getLessons()).contains(l);
            assertThat(l.getDeletedAt()).isNotNull();
            assertThat(l.getPendingDelete()).isFalse();
        }

        // lines 174, 175–178 — existing lesson with isDraft + draftTitle
        @Test
        void clearsDraftFlagAndAppliesDraftTitleOnLesson() {
            Lesson l = lesson(true, false, "New Title", null);
            Chapter ch = chapter(false, false, List.of(l));
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of(ch));
            stubApproveUpdate(c);

            service.approveUpdate(adminId, courseId);

            assertThat(l.getIsDraft()).isFalse();
            assertThat(l.getTitle()).isEqualTo("New Title");
            assertThat(l.getDraftTitle()).isNull();
        }

        // lines 179–182 — lesson with draftContentText
        @Test
        void appliesDraftContentTextOnLesson() {
            Lesson l = lesson(false, false, null, "Updated content");
            Chapter ch = chapter(false, false, List.of(l));
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of(ch));
            stubApproveUpdate(c);

            service.approveUpdate(adminId, courseId);

            assertThat(l.getContentText()).isEqualTo("Updated content");
            assertThat(l.getDraftContentText()).isNull();
        }

        // line 200 — S3 cleanup chạy qua deleteObjectAsync (fire-and-forget, không chặn luồng
        // gọi) — lỗi S3 (nếu có) được S3Service tự log warn nội bộ, không còn cách nào propagate
        // ngược lại approveUpdate() để mock giả lập ở đây nữa (khác với try/catch cũ tại chỗ gọi).
        @Test
        void deletesResource_regardlessOfS3CleanupOutcome() {
            LessonResource r = new LessonResource();
            r.setS3Key("courses/file.pdf");
            r.setPendingDelete(true);

            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of());
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId)).thenReturn(List.of(r));
            when(lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId)).thenReturn(List.of());
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING")).thenReturn(Optional.empty());
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            service.approveUpdate(adminId, courseId);

            assertThat(r.getDeletedAt()).isNotNull();
            assertThat(r.getStatus()).isEqualTo("DELETED");
            verify(s3Service).deleteObjectAsync("courses/file.pdf");
        }

        // Tài liệu vẫn phải soft-delete ở DB (không còn hiện cho học viên/giảng viên) nhưng KHÔNG
        // được xóa file S3 nếu còn 1 CourseVersion khác (VD: bản đang PUBLISHED) còn tham chiếu
        // key này trong snapshot — nếu không, rollback về version đó sau này sẽ mất tài liệu vĩnh
        // viễn (xem CourseVersionReferenceChecker).
        @Test
        void keepsS3File_whenStillReferencedByAnotherCourseVersion() {
            LessonResource r = new LessonResource();
            r.setS3Key("courses/file.pdf");
            r.setPendingDelete(true);

            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of());
            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId)).thenReturn(List.of(r));
            when(lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId)).thenReturn(List.of());
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING")).thenReturn(Optional.empty());
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());
            when(courseVersionReferenceChecker.isSafeToDelete(courseId, "courses/file.pdf")).thenReturn(false);

            service.approveUpdate(adminId, courseId);

            assertThat(r.getDeletedAt()).isNotNull();
            assertThat(r.getStatus()).isEqualTo("DELETED");
            verify(s3Service, never()).deleteObjectAsync(anyString());
        }

        // lines 222–226 — version updated to APPROVED when present
        @Test
        void updatesVersionToApproved_whenVersionExists() {
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of());
            CourseVersion version = new CourseVersion();
            version.setStatus("PENDING");

            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId)).thenReturn(List.of());
            when(lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId)).thenReturn(List.of());
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(version));
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            service.approveUpdate(adminId, courseId);

            assertThat(version.getStatus()).isEqualTo("APPROVED");
            assertThat(version.getReviewedBy()).isEqualTo(adminId);
            assertThat(version.getReviewedAt()).isNotNull();
        }
    }

    // ── rejectUpdate: version update ─────────────────────────────────────────

    @Nested
    class RejectUpdateVersion {

        // lines 259–265 — version updated to REJECTED when present
        @Test
        void updatesVersionToRejected_whenVersionExists() {
            Course c = courseWith(CourseStatus.PENDING_UPDATE, List.of());

            CourseVersion version = new CourseVersion();
            version.setStatus("PENDING");

            when(courseRepo.findByIdWithCategory(courseId)).thenReturn(Optional.of(c));
            when(lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId)).thenReturn(List.of());
            when(lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId)).thenReturn(List.of());
            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(version));
            when(courseMapper.toDetailResponse(c)).thenReturn(detailResponse());

            service.rejectUpdate(adminId, courseId, "Ảnh bìa không đạt");

            assertThat(version.getStatus()).isEqualTo("REJECTED");
            assertThat(version.getRejectionReason()).isEqualTo("Ảnh bìa không đạt");
            assertThat(version.getReviewedBy()).isEqualTo(adminId);
        }
    }

    // ── getVersionDiff: parseSnapshot catch, diffChapters, diffLessons ────────

    @Nested
    class GetVersionDiffExtended {

        // lines 304–306 — malformed JSON in snapshot → parseSnapshot returns null
        @Test
        void handlesInvalidSnapshotJson_returnsNullMetadata() {
            CourseVersion pending = new CourseVersion();
            pending.setId(UUID.randomUUID());
            pending.setVersionNumber(1);
            pending.setSnapshot("{INVALID JSON");

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.empty());

            // Should not throw — bad JSON logs warn and returns null snapshot
            CourseDiffResponse result = service.getVersionDiff(courseId);

            assertThat(result).isNotNull();
            assertThat(result.getMetadata()).isNotNull();
        }

        // lines 352–355 — ADDED chapter (exists in new, not in old)
        @Test
        void diffChapters_detectsAddedChapter() {
            String oldSnap = "{\"title\":\"T\",\"chapters\":[]}";
            String newSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"New Ch\",\"orderIndex\":1,\"lessons\":[]}]}";

            CourseVersion pending = versionWith(2, newSnap);
            CourseVersion approved = versionWith(1, oldSnap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            assertThat(result.getChapters()).hasSize(1);
            assertThat(result.getChapters().get(0).getAction()).isEqualTo("ADDED");
            assertThat(result.getChapters().get(0).getTitle()).isEqualTo("New Ch");
        }

        // lines 356–359 — REMOVED chapter (exists in old, not in new)
        @Test
        void diffChapters_detectsRemovedChapter() {
            String oldSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Old Ch\",\"orderIndex\":1,\"lessons\":[]}]}";
            String newSnap = "{\"title\":\"T\",\"chapters\":[]}";

            CourseVersion pending = versionWith(2, newSnap);
            CourseVersion approved = versionWith(1, oldSnap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            assertThat(result.getChapters()).hasSize(1);
            assertThat(result.getChapters().get(0).getAction()).isEqualTo("REMOVED");
        }

        // lines 360–368 — MODIFIED chapter (title changed)
        @Test
        void diffChapters_detectsModifiedChapter_whenTitleChanged() {
            String oldSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Old Title\",\"orderIndex\":1,\"lessons\":[]}]}";
            String newSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"New Title\",\"orderIndex\":1,\"lessons\":[]}]}";

            CourseVersion pending = versionWith(2, newSnap);
            CourseVersion approved = versionWith(1, oldSnap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            assertThat(result.getChapters()).hasSize(1);
            assertThat(result.getChapters().get(0).getAction()).isEqualTo("MODIFIED");
        }

        // lines 360–368 — UNCHANGED chapter (same title, same lessons)
        @Test
        void diffChapters_detectsUnchangedChapter() {
            String snap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Same\",\"orderIndex\":1,\"lessons\":[]}]}";

            CourseVersion pending = versionWith(2, snap);
            CourseVersion approved = versionWith(1, snap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            assertThat(result.getChapters()).hasSize(1);
            assertThat(result.getChapters().get(0).getAction()).isEqualTo("UNCHANGED");
        }

        // lines 424–469 — diffLessons: ADDED lesson
        @Test
        void diffLessons_detectsAddedLesson() {
            String oldSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[]}]}";
            String newSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[{\"title\":\"L1\",\"orderIndex\":1,\"lessonType\":\"TEXT\",\"contentText\":\"x\"}]}]}";

            CourseVersion pending = versionWith(2, newSnap);
            CourseVersion approved = versionWith(1, oldSnap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            List<CourseDiffResponse.LessonDiff> lessons = result.getChapters().get(0).getLessons();
            assertThat(lessons).hasSize(1);
            assertThat(lessons.get(0).getAction()).isEqualTo("ADDED");
            assertThat(lessons.get(0).getTitle()).isEqualTo("L1");
        }

        // lines 397–400 — REMOVED lesson
        @Test
        void diffLessons_detectsRemovedLesson() {
            String oldSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[{\"title\":\"L1\",\"orderIndex\":1,\"lessonType\":\"TEXT\",\"contentText\":\"x\"}]}]}";
            String newSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[]}]}";

            CourseVersion pending = versionWith(2, newSnap);
            CourseVersion approved = versionWith(1, oldSnap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            List<CourseDiffResponse.LessonDiff> lessons = result.getChapters().get(0).getLessons();
            assertThat(lessons).hasSize(1);
            assertThat(lessons.get(0).getAction()).isEqualTo("REMOVED");
        }

        // lines 402–407 — MODIFIED lesson (title changed)
        @Test
        void diffLessons_detectsModifiedLesson_whenTitleChanged() {
            String oldSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[{\"title\":\"Old L\",\"orderIndex\":1,\"lessonType\":\"TEXT\",\"contentText\":\"a\"}]}]}";
            String newSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[{\"title\":\"New L\",\"orderIndex\":1,\"lessonType\":\"TEXT\",\"contentText\":\"a\"}]}]}";

            CourseVersion pending = versionWith(2, newSnap);
            CourseVersion approved = versionWith(1, oldSnap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            List<CourseDiffResponse.LessonDiff> lessons = result.getChapters().get(0).getLessons();
            assertThat(lessons).hasSize(1);
            assertThat(lessons.get(0).getAction()).isEqualTo("MODIFIED");
            assertThat(lessons.get(0).getNewTitle()).isEqualTo("New L");
        }

        // lines 402–407 — UNCHANGED lesson
        @Test
        void diffLessons_detectsUnchangedLesson() {
            String snap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[{\"title\":\"L\",\"orderIndex\":1,\"lessonType\":\"TEXT\",\"contentText\":\"x\"}]}]}";

            CourseVersion pending = versionWith(2, snap);
            CourseVersion approved = versionWith(1, snap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            List<CourseDiffResponse.LessonDiff> lessons = result.getChapters().get(0).getLessons();
            assertThat(lessons).hasSize(1);
            assertThat(lessons.get(0).getAction()).isEqualTo("UNCHANGED");
        }

        // lines 424–452 — diffResources: REMOVED resource
        @Test
        void diffResources_detectsRemovedResource() {
            String oldSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[{\"title\":\"L\",\"orderIndex\":1,\"lessonType\":\"TEXT\",\"contentText\":\"x\"," +
                    "\"resources\":[{\"displayName\":\"File.pdf\",\"resourceType\":\"DOCUMENT\",\"mimeType\":\"application/pdf\",\"fileSizeBytes\":1024}]}]}]}";
            String newSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[{\"title\":\"L\",\"orderIndex\":1,\"lessonType\":\"TEXT\",\"contentText\":\"x\"," +
                    "\"resources\":[]}]}]}";

            CourseVersion pending = versionWith(2, newSnap);
            CourseVersion approved = versionWith(1, oldSnap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            assertThat(result.getResources()).hasSize(1);
            assertThat(result.getResources().get(0).getAction()).isEqualTo("REMOVED");
            assertThat(result.getResources().get(0).getDisplayName()).isEqualTo("File.pdf");
        }

        // lines 438–452 — diffResources: ADDED resource
        @Test
        void diffResources_detectsAddedResource() {
            String oldSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[{\"title\":\"L\",\"orderIndex\":1,\"lessonType\":\"TEXT\",\"contentText\":\"x\"," +
                    "\"resources\":[]}]}]}";
            String newSnap = "{\"title\":\"T\",\"chapters\":[{\"title\":\"Ch\",\"orderIndex\":1," +
                    "\"lessons\":[{\"title\":\"L\",\"orderIndex\":1,\"lessonType\":\"TEXT\",\"contentText\":\"x\"," +
                    "\"resources\":[{\"displayName\":\"New.pdf\",\"resourceType\":\"DOCUMENT\",\"mimeType\":\"application/pdf\",\"fileSizeBytes\":512}]}]}]}";

            CourseVersion pending = versionWith(2, newSnap);
            CourseVersion approved = versionWith(1, oldSnap);

            when(courseVersionRepo.findFirstByCourseIdAndStatus(courseId, "PENDING"))
                    .thenReturn(Optional.of(pending));
            when(courseVersionRepo.findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, "APPROVED"))
                    .thenReturn(Optional.of(approved));

            CourseDiffResponse result = service.getVersionDiff(courseId);

            assertThat(result.getResources()).hasSize(1);
            assertThat(result.getResources().get(0).getAction()).isEqualTo("ADDED");
            assertThat(result.getResources().get(0).getDisplayName()).isEqualTo("New.pdf");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CourseVersion versionWith(int versionNumber, String snapshot) {
        CourseVersion v = new CourseVersion();
        v.setId(UUID.randomUUID());
        v.setVersionNumber(versionNumber);
        v.setSnapshot(snapshot);
        return v;
    }
}
