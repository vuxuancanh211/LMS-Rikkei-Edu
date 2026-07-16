package project.lms_rikkei_edu.modules.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.dto.request.CreateLessonRequest;
import project.lms_rikkei_edu.modules.course.dto.request.UpdateLessonRequest;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.exception.*;
import project.lms_rikkei_edu.modules.course.mapper.ChapterMapper;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.mapper.LessonMapper;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.StudentCourseService;
import project.lms_rikkei_edu.modules.course.service.impl.CourseServiceImpl;
import project.lms_rikkei_edu.modules.course.service.impl.CourseVersionReferenceChecker;
import project.lms_rikkei_edu.modules.quiz.dto.request.QuizMetadataRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.QuizSummaryResponse;
import project.lms_rikkei_edu.modules.quiz.entity.QuizEntity;
import project.lms_rikkei_edu.modules.quiz.repository.QuizRepository;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseServiceLessonAndVersionTest {

    @Mock private CourseRepository courseRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private LessonResourceRepository lessonResourceRepository;
    @Mock private CourseCategoryRepository categoryRepository;
    @Mock private CourseApprovalLogRepository approvalLogRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private CourseMapper courseMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private ChapterMapper chapterMapper;
    @Mock private LessonMapper lessonMapper;
    @Mock private EntityManager entityManager;
    @Mock private S3Service s3Service;
    @Mock private QuizService quizService;
    @Mock private QuizRepository quizRepository;
    @Mock private StudentCourseService studentCourseService;
    @Mock private CourseVersionReferenceChecker courseVersionReferenceChecker;

    @InjectMocks private CourseServiceImpl courseService;

    private UUID instructorId;
    private UUID courseId;
    private UUID chapterId;
    private UUID lessonId;
    private UUID versionId;
    private Course course;

    @BeforeEach
    void setUp() {
        instructorId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
        lessonId = UUID.randomUUID();
        versionId = UUID.randomUUID();

        course = new Course();
        course.setId(courseId);
        course.setInstructorId(instructorId);
        course.setStatus(CourseStatus.DRAFT);
        course.setChapters(new ArrayList<>());
    }

    @Test
    void addLesson_success() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        Chapter ch = new Chapter();
        ch.setId(chapterId);
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(ch));
        when(lessonRepository.findMaxOrderIndexByChapterId(chapterId)).thenReturn(1);
        when(lessonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lessonMapper.toResponse(any())).thenReturn(LessonResponse.builder().id(lessonId).title("New Lesson").build());

        CreateLessonRequest req = new CreateLessonRequest();
        req.setTitle("New Lesson");
        req.setType(LessonType.VIDEO);
        req.setContentText("Cont");
        req.setIsPreview(true);

        LessonResponse resp = courseService.addLesson(instructorId, courseId, chapterId, req);
        assertThat(resp).isNotNull();
        assertThat(resp.getTitle()).isEqualTo("New Lesson");
        verify(lessonRepository).save(any());
    }

    @Test
    void updateLesson_liveCourseNotDraftLesson_updatesDraftFields() {
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setIsDraft(false);
        when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson));
        when(lessonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lessonMapper.toResponse(any())).thenReturn(LessonResponse.builder().build());

        UpdateLessonRequest req = new UpdateLessonRequest();
        req.setTitle("Draft Title");
        req.setContentText("Draft Content");
        req.setIsPreview(true);

        courseService.updateLesson(instructorId, courseId, chapterId, lessonId, req);
        assertThat(lesson.getDraftTitle()).isEqualTo("Draft Title");
        assertThat(lesson.getDraftContentText()).isEqualTo("Draft Content");
    }

    @Test
    void updateLesson_draftCourse_updatesDirectFields() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setIsDraft(false);
        when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson));
        when(lessonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lessonMapper.toResponse(any())).thenReturn(LessonResponse.builder().build());

        UpdateLessonRequest req = new UpdateLessonRequest();
        req.setTitle("Direct Title");
        req.setType(LessonType.TEXT);
        req.setContentText("Direct Content");
        req.setIsPreview(false);

        courseService.updateLesson(instructorId, courseId, chapterId, lessonId, req);
        assertThat(lesson.getTitle()).isEqualTo("Direct Title");
        assertThat(lesson.getType()).isEqualTo(LessonType.TEXT);
    }

    @Test
    void deleteLesson_liveCourseDraftLesson_deletesHard() {
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setIsDraft(true);
        when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson));

        courseService.deleteLesson(instructorId, courseId, chapterId, lessonId);
        verify(lessonRepository).delete(lesson);
    }

    @Test
    void deleteLesson_liveCourseLiveLesson_marksPendingDelete() {
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setIsDraft(false);
        when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson));

        courseService.deleteLesson(instructorId, courseId, chapterId, lessonId);
        assertThat(lesson.getPendingDelete()).isTrue();
        verify(lessonRepository).save(lesson);
    }

    @Test
    void deleteLesson_draftCourse_deletesHard() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson));

        courseService.deleteLesson(instructorId, courseId, chapterId, lessonId);
        verify(lessonRepository).delete(lesson);
    }

    @Test
    void getCourseHistory_returnsLogs() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        CourseApprovalLog log1 = new CourseApprovalLog();
        log1.setId(UUID.randomUUID());
        log1.setAction("SUBMITTED");
        log1.setReason("Submitting");
        log1.setCreatedAt(Instant.now());

        CourseApprovalLog log2 = new CourseApprovalLog();
        log2.setId(UUID.randomUUID());
        log2.setAction("APPROVE");
        log2.setReason("Approved");
        log2.setCreatedAt(Instant.now());

        when(approvalLogRepository.findByCourseIdOrderByCreatedAtAsc(courseId)).thenReturn(List.of(log1, log2));

        List<CourseApprovalLogResponse> result = courseService.getCourseHistory(instructorId, courseId);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getActorType()).isEqualTo("INSTRUCTOR");
        assertThat(result.get(1).getActorType()).isEqualTo("ADMIN");
    }

    @Test
    void getCourseVersions_returnsVersions() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        CourseVersion v = new CourseVersion();
        v.setId(versionId);
        v.setVersionNumber(1);
        v.setStatus("APPROVED");
        when(courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(courseId)).thenReturn(List.of(v));

        List<CourseVersionResponse> result = courseService.getCourseVersions(instructorId, courseId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVersionNumber()).isEqualTo(1);
    }

    @Test
    void saveDraft_successAndLimits() throws Exception {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(courseVersionRepository.countByCourseIdAndStatus(courseId, "DRAFT")).thenReturn(1L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        CourseVersion v = CourseVersion.builder().id(versionId).status("DRAFT").snapshot("{}").label("Label").build();
        when(courseVersionRepository.save(any())).thenReturn(v);

        CourseVersionResponse resp = courseService.saveDraft(instructorId, courseId, "Label");
        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo("DRAFT");

        // Test limit >= 3 throws exception
        when(courseVersionRepository.countByCourseIdAndStatus(courseId, "DRAFT")).thenReturn(3L);
        assertThatThrownBy(() -> courseService.saveDraft(instructorId, courseId, "Label"))
                .isInstanceOf(CourseStateException.class);
    }

    @Test
    void saveDraft_invalidCourseState_throwsException() {
        course.setStatus(CourseStatus.PENDING_UPDATE);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        assertThatThrownBy(() -> courseService.saveDraft(instructorId, courseId, "Label"))
                .isInstanceOf(CourseStateException.class);

        course.setStatus(CourseStatus.PENDING);
        assertThatThrownBy(() -> courseService.saveDraft(instructorId, courseId, "Label"))
                .isInstanceOf(CourseStateException.class);
    }

    @Test
    void deleteDraftVersion_successAndValidations() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        CourseVersion v = CourseVersion.builder().id(versionId).courseId(courseId).status("DRAFT").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        courseService.deleteDraftVersion(instructorId, courseId, versionId);
        verify(courseVersionRepository).delete(v);

        // Test non-draft status
        CourseVersion vApprove = CourseVersion.builder().id(versionId).courseId(courseId).status("APPROVED").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(vApprove));
        assertThatThrownBy(() -> courseService.deleteDraftVersion(instructorId, courseId, versionId))
                .isInstanceOf(CourseStateException.class);
    }

    @Test
    void renameDraftVersion_successAndValidations() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        CourseVersion v = CourseVersion.builder().id(versionId).courseId(courseId).status("DRAFT").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        courseService.renameDraftVersion(instructorId, courseId, versionId, "New Label");
        assertThat(v.getLabel()).isEqualTo("New Label");
        verify(courseVersionRepository).save(v);
    }

    @Test
    void cloneVersionAsDraft_successAndValidations() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        CourseVersion source = CourseVersion.builder().id(versionId).courseId(courseId).status("APPROVED").versionNumber(1).snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(source));
        when(courseVersionRepository.countByCourseIdAndStatus(courseId, "DRAFT")).thenReturn(0L);
        when(courseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseVersionResponse resp = courseService.cloneVersionAsDraft(instructorId, courseId, versionId, null);
        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void submitVersion_successAndValidations() {
        // course status DRAFT (mặc định từ @BeforeEach) → submit lần đầu, course phải chuyển PENDING
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        CourseVersion target = CourseVersion.builder().id(versionId).courseId(courseId).status("DRAFT").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(target));
        when(courseVersionRepository.findMaxVersionNumberByCourseId(courseId)).thenReturn(2);
        when(courseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(courseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseVersionResponse resp = courseService.submitVersion(instructorId, courseId, versionId);
        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo("PENDING");
        assertThat(resp.getVersionNumber()).isEqualTo(3);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.PENDING);
    }

    @Test
    void submitVersion_whenCoursePublished_transitionsCourseToPendingUpdate() {
        // Regression: submitVersion() trước đây chỉ đổi CourseVersion mà không đụng course.status
        // — khiến course vẫn PUBLISHED sau khi submit, admin không thấy trong hàng chờ duyệt, và
        // nút "Gửi cập nhật" ở màn hình chính vẫn hiện ra như chưa gửi gì.
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        CourseVersion target = CourseVersion.builder().id(versionId).courseId(courseId).status("DRAFT").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(target));
        when(courseVersionRepository.findMaxVersionNumberByCourseId(courseId)).thenReturn(2);
        when(courseVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(courseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        courseService.submitVersion(instructorId, courseId, versionId);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.PENDING_UPDATE);
        assertThat(course.getPendingUpdateAt()).isNotNull();
    }

    @Test
    void submitVersion_whenCoursePending_throws() {
        course.setStatus(CourseStatus.PENDING);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.submitVersion(instructorId, courseId, versionId))
                .isInstanceOf(CourseStateException.class);
    }

    @Test
    void hasPendingVersion_returnsBoolean() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(courseVersionRepository.countByCourseIdAndStatus(courseId, "PENDING")).thenReturn(1L);
        assertThat(courseService.hasPendingVersion(instructorId, courseId)).isTrue();
    }

    @Test
    void rollbackToVersion_success() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId)
                .courseId(courseId)
                .status("APPROVED")
                .snapshot("{\"title\":\"Old Title\",\"description\":\"Old Desc\",\"level\":\"ADVANCED\",\"chapters\":[]}")
                .build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        CourseSnapshotDto snap = CourseSnapshotDto.builder()
                .title("Old Title")
                .description("Old Desc")
                .level("ADVANCED")
                .chapters(new ArrayList<>())
                .build();
        when(objectMapper.readValue(v.getSnapshot(), CourseSnapshotDto.class)).thenReturn(snap);
        when(courseMapper.toDetailResponse(any())).thenReturn(CourseDetailResponse.builder().build());

        CourseDetailResponse resp = courseService.rollbackToVersion(instructorId, courseId, versionId);
        assertThat(resp).isNotNull();
        assertThat(course.getDraftTitle()).isEqualTo("Old Title");
        assertThat(course.getDraftDescription()).isEqualTo("Old Desc");
        assertThat(course.getDraftLevel()).isEqualTo(CourseLevel.ADVANCED);
    }

    // Regression: rollback về 1 version còn cần 1 lesson đã bị admin duyệt xóa (soft-delete) phải
    // "hồi sinh" lại lesson gốc (giữ nguyên resources) thay vì tạo lesson trắng tay mới — nếu không,
    // tài liệu (đã được soft-delete giữ lại đúng thiết kế) vẫn không bao giờ hiện lại được cho
    // người dùng dù row còn nguyên trong DB (xem phản hồi người dùng: "rollback lại không thấy tài liệu").
    @Test
    void rollbackToVersion_resurrectsSoftDeletedLesson_insteadOfCreatingEmptyDraft() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);
        Chapter ch = new Chapter();
        ch.setId(chapterId);
        ch.setTitle("Chapter 1");
        ch.setOrderIndex(1);
        ch.setLessons(new ArrayList<>()); // không còn lesson nào "sống" ở chapter này
        course.setChapters(new ArrayList<>(List.of(ch)));
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("{...}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        LessonResource keptResource = new LessonResource();
        keptResource.setDisplayName("keepme.pdf");
        keptResource.setS3Key("ext://https://example.com/keepme.pdf");

        Lesson softDeletedLesson = new Lesson();
        softDeletedLesson.setId(lessonId);
        softDeletedLesson.setChapter(ch);
        softDeletedLesson.setOrderIndex(1);
        softDeletedLesson.setTitle("Lesson A");
        softDeletedLesson.setContentText("hello");
        softDeletedLesson.setDeletedAt(Instant.now());
        softDeletedLesson.setResources(new ArrayList<>(List.of(keptResource)));
        when(lessonRepository.findSoftDeletedByChapterIdAndOrderIndex(chapterId, 1))
                .thenReturn(Optional.of(softDeletedLesson));

        CourseSnapshotDto.ResourceSnap resSnap = CourseSnapshotDto.ResourceSnap.builder()
                .displayName("keepme.pdf").build();
        CourseSnapshotDto.LessonSnap lessonSnap = CourseSnapshotDto.LessonSnap.builder()
                .title("Lesson A").contentText("hello").orderIndex(1).resources(List.of(resSnap)).build();
        CourseSnapshotDto.ChapterSnap chapterSnap = CourseSnapshotDto.ChapterSnap.builder()
                .title("Chapter 1").orderIndex(1).lessons(List.of(lessonSnap)).build();
        CourseSnapshotDto snap = CourseSnapshotDto.builder()
                .title("T").chapters(List.of(chapterSnap)).build();
        when(objectMapper.readValue(v.getSnapshot(), CourseSnapshotDto.class)).thenReturn(snap);
        when(courseMapper.toDetailResponse(any())).thenReturn(CourseDetailResponse.builder().build());

        courseService.rollbackToVersion(instructorId, courseId, versionId);

        assertThat(softDeletedLesson.getDeletedAt()).isNull();
        // isDraft=true — chưa nằm trong bản đã duyệt hiện tại, cần gửi duyệt lại (nếu không nút
        // "Gửi duyệt" sẽ không hiện vì Course.isHasPendingDraft() không nhận ra có gì cần gửi).
        assertThat(softDeletedLesson.getIsDraft()).isTrue();
        assertThat(softDeletedLesson.getPendingDelete()).isFalse();
        assertThat(keptResource.getPendingDelete()).isNotEqualTo(true);
        assertThat(course.isHasPendingDraft()).isTrue();
        verify(lessonRepository).save(softDeletedLesson);
    }

    // Cùng loại regression như trên nhưng ở tầng chapter — cả chapter lẫn lesson bên trong đều đã
    // bị soft-delete cùng lúc (xem AdminCourseServiceImpl.approveUpdate).
    @Test
    void rollbackToVersion_resurrectsSoftDeletedChapter_insteadOfCreatingEmptyDraft() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);
        course.setChapters(new ArrayList<>()); // không còn chapter nào "sống"
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("{...}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        Lesson softDeletedLesson = new Lesson();
        softDeletedLesson.setId(lessonId);
        softDeletedLesson.setOrderIndex(1);
        softDeletedLesson.setTitle("Lesson A");
        softDeletedLesson.setDeletedAt(Instant.now());
        softDeletedLesson.setResources(new ArrayList<>());

        Chapter softDeletedChapter = new Chapter();
        softDeletedChapter.setId(chapterId);
        softDeletedChapter.setOrderIndex(1);
        softDeletedChapter.setTitle("Chapter 1");
        softDeletedChapter.setDeletedAt(Instant.now());
        // Rỗng — mô phỏng đúng @SQLRestriction thật sự sẽ ẩn lesson (deleted_at riêng của nó vẫn
        // còn set) khỏi chapter.getLessons() dù đã fetch lại chapter; lesson chỉ được tìm thấy qua
        // findSoftDeletedByChapterIdAndOrderIndex (native query, bỏ qua restriction) bên dưới.
        softDeletedChapter.setLessons(new ArrayList<>());
        when(chapterRepository.findSoftDeletedByCourseIdAndOrderIndex(courseId, 1))
                .thenReturn(Optional.of(softDeletedChapter));
        when(chapterRepository.save(softDeletedChapter)).thenReturn(softDeletedChapter);
        when(lessonRepository.findSoftDeletedByChapterIdAndOrderIndex(chapterId, 1))
                .thenReturn(Optional.of(softDeletedLesson));

        CourseSnapshotDto.LessonSnap lessonSnap = CourseSnapshotDto.LessonSnap.builder()
                .title("Lesson A").orderIndex(1).resources(List.of()).build();
        CourseSnapshotDto.ChapterSnap chapterSnap = CourseSnapshotDto.ChapterSnap.builder()
                .title("Chapter 1").orderIndex(1).lessons(List.of(lessonSnap)).build();
        CourseSnapshotDto snap = CourseSnapshotDto.builder()
                .title("T").chapters(List.of(chapterSnap)).build();
        when(objectMapper.readValue(v.getSnapshot(), CourseSnapshotDto.class)).thenReturn(snap);
        when(courseMapper.toDetailResponse(any())).thenReturn(CourseDetailResponse.builder().build());

        courseService.rollbackToVersion(instructorId, courseId, versionId);

        assertThat(softDeletedChapter.getDeletedAt()).isNull();
        assertThat(softDeletedLesson.getDeletedAt()).isNull();
        verify(chapterRepository).save(softDeletedChapter);
        verify(lessonRepository).save(softDeletedLesson);
    }

    // Regression: rollback về 1 version khi chapter/lesson VẪN CÒN SỐNG ở đúng vị trí (không cần
    // hồi sinh) — phải đi vào nhánh diff resources (applyResourceDiffForRollback) thay vì nhánh
    // resurrect/tạo mới. Bao phủ cả 3 nhánh của applyResourceDiffForRollback: tài liệu sống nhưng
    // không có trong snapshot (đánh dấu pending_delete), tài liệu có trong snapshot nhưng đã bị
    // soft-delete và tìm lại được (hồi sinh), và tài liệu có trong snapshot nhưng KHÔNG tìm lại
    // được bản soft-delete nào (bỏ qua, không lỗi).
    @Test
    void rollbackToVersion_liveLessonMatchedByOrderIndex_diffsResourcesAndTitleContent() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);

        LessonResource keepResource = new LessonResource();
        keepResource.setDisplayName("keep.pdf");
        LessonResource staleResource = new LessonResource();
        staleResource.setDisplayName("stale.pdf");

        Lesson liveLesson = new Lesson();
        liveLesson.setId(lessonId);
        liveLesson.setOrderIndex(1);
        liveLesson.setTitle("Old Title");
        liveLesson.setContentText("Old Content");
        liveLesson.setResources(new ArrayList<>(List.of(keepResource, staleResource)));

        Chapter ch = new Chapter();
        ch.setId(chapterId);
        ch.setOrderIndex(1);
        ch.setTitle("Chapter 1");
        ch.setLessons(new ArrayList<>(List.of(liveLesson)));
        course.setChapters(new ArrayList<>(List.of(ch)));
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("{...}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        LessonResource resurrectedResource = new LessonResource();
        resurrectedResource.setDisplayName("resurrect.pdf");
        resurrectedResource.setDeletedAt(Instant.now());
        when(lessonResourceRepository.findSoftDeletedByLessonIdAndDisplayName(lessonId, "resurrect.pdf"))
                .thenReturn(Optional.of(resurrectedResource));
        when(lessonResourceRepository.findSoftDeletedByLessonIdAndDisplayName(lessonId, "truly-gone.pdf"))
                .thenReturn(Optional.empty());

        CourseSnapshotDto.ResourceSnap keepSnap = CourseSnapshotDto.ResourceSnap.builder().displayName("keep.pdf").build();
        CourseSnapshotDto.ResourceSnap resurrectSnap = CourseSnapshotDto.ResourceSnap.builder().displayName("resurrect.pdf").build();
        CourseSnapshotDto.ResourceSnap goneSnap = CourseSnapshotDto.ResourceSnap.builder().displayName("truly-gone.pdf").build();
        CourseSnapshotDto.LessonSnap lessonSnap = CourseSnapshotDto.LessonSnap.builder()
                .title("New Title").contentText("New Content").orderIndex(1)
                .resources(List.of(keepSnap, resurrectSnap, goneSnap)).build();
        CourseSnapshotDto.ChapterSnap chapterSnap = CourseSnapshotDto.ChapterSnap.builder()
                .title("Chapter 1").orderIndex(1).lessons(List.of(lessonSnap)).build();
        CourseSnapshotDto snap = CourseSnapshotDto.builder().title("T").chapters(List.of(chapterSnap)).build();
        when(objectMapper.readValue(v.getSnapshot(), CourseSnapshotDto.class)).thenReturn(snap);
        when(courseMapper.toDetailResponse(any())).thenReturn(CourseDetailResponse.builder().build());

        courseService.rollbackToVersion(instructorId, courseId, versionId);

        // Title/content diff — matched-lesson branch of applyLessonDiffForRollback
        assertThat(liveLesson.getDraftTitle()).isEqualTo("New Title");
        assertThat(liveLesson.getDraftContentText()).isEqualTo("New Content");
        // keep.pdf stays untouched
        assertThat(keepResource.getPendingDelete()).isNotEqualTo(true);
        // stale.pdf lives but isn't in the snapshot -> marked pending delete
        assertThat(staleResource.getPendingDelete()).isTrue();
        assertThat(staleResource.getStatus()).isEqualTo("PENDING_DELETE");
        // resurrect.pdf is in the snapshot, was soft-deleted, and gets found -> resurrected
        assertThat(resurrectedResource.getDeletedAt()).isNull();
        assertThat(resurrectedResource.getStatus()).isEqualTo("ACTIVE");
        assertThat(resurrectedResource.getPendingDelete()).isFalse();
        assertThat(resurrectedResource.getIsNewInUpdate()).isTrue();
        verify(lessonResourceRepository).save(resurrectedResource);
        // truly-gone.pdf is in the snapshot but no soft-deleted match exists -> silently skipped
        verify(lessonResourceRepository, never()).save(argThat(r -> "truly-gone.pdf".equals(r.getDisplayName())));
    }

    @Test
    void rollbackToVersion_notPublishedAndNotDraftRestore_throws() {
        course.setStatus(CourseStatus.DRAFT); // not PUBLISHED
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> courseService.rollbackToVersion(instructorId, courseId, versionId))
                .isInstanceOf(CourseStateException.class)
                .hasMessageContaining("PUBLISHED");
    }

    @Test
    void rollbackToVersion_versionNotApprovedNorDraft_throws() {
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("REJECTED").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> courseService.rollbackToVersion(instructorId, courseId, versionId))
                .isInstanceOf(CourseStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void rollbackToVersion_coursePendingUpdate_throwsEvenForDraftRestore() {
        // version DRAFT -> isDraftRestore=true bỏ qua 2 guard đầu, nhưng vẫn phải chặn nếu course
        // đang PENDING/PENDING_UPDATE.
        course.setStatus(CourseStatus.PENDING_UPDATE);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("DRAFT").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> courseService.rollbackToVersion(instructorId, courseId, versionId))
                .isInstanceOf(CourseStateException.class)
                .hasMessageContaining("chờ duyệt");
    }

    @Test
    void rollbackToVersion_malformedSnapshotJson_throwsIllegalState() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("not-json").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));
        when(objectMapper.readValue(anyString(), eq(CourseSnapshotDto.class)))
                .thenThrow(new RuntimeException("bad token"));

        assertThatThrownBy(() -> courseService.rollbackToVersion(instructorId, courseId, versionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshot");
    }

    @Test
    void rollbackToVersion_hardDeletesIsNewInUpdateResources_andRestoresPendingDeleteResources() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        LessonResource newInUpdateSafe = new LessonResource();
        newInUpdateSafe.setS3Key("courses/safe.pdf");
        LessonResource newInUpdateExternal = new LessonResource();
        newInUpdateExternal.setS3Key("ext://https://cdn.example.com/video");
        when(lessonResourceRepository.findAllByCourseIdAndIsNewInUpdateTrue(courseId))
                .thenReturn(List.of(newInUpdateSafe, newInUpdateExternal));
        when(courseVersionReferenceChecker.isSafeToDelete(courseId, "courses/safe.pdf")).thenReturn(true);

        LessonResource pendingDeleteResource = new LessonResource();
        pendingDeleteResource.setPendingDelete(true);
        pendingDeleteResource.setStatus("PENDING_DELETE");
        when(lessonResourceRepository.findAllByCourseIdAndPendingDeleteTrue(courseId))
                .thenReturn(List.of(pendingDeleteResource));

        CourseSnapshotDto snap = CourseSnapshotDto.builder().title(course.getTitle()).chapters(List.of()).build();
        when(objectMapper.readValue(v.getSnapshot(), CourseSnapshotDto.class)).thenReturn(snap);
        when(courseMapper.toDetailResponse(any())).thenReturn(CourseDetailResponse.builder().build());

        courseService.rollbackToVersion(instructorId, courseId, versionId);

        assertThat(newInUpdateSafe.getDeletedAt()).isNotNull();
        assertThat(newInUpdateSafe.getStatus()).isEqualTo("DELETED");
        verify(s3Service).deleteObjectAsync("courses/safe.pdf");
        // external URL resource: hard-deleted in DB but never sent to S3 cleanup
        assertThat(newInUpdateExternal.getDeletedAt()).isNotNull();
        verify(s3Service, never()).deleteObjectAsync("ext://https://cdn.example.com/video");

        assertThat(pendingDeleteResource.getPendingDelete()).isFalse();
        assertThat(pendingDeleteResource.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rollbackToVersion_draftThumbnailUrlDiff_setWhenChanged() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);
        course.setThumbnailUrl("old-thumb.png");
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        CourseSnapshotDto snap = CourseSnapshotDto.builder()
                .title(course.getTitle()).thumbnailUrl("new-thumb.png").chapters(List.of()).build();
        when(objectMapper.readValue(v.getSnapshot(), CourseSnapshotDto.class)).thenReturn(snap);
        when(courseMapper.toDetailResponse(any())).thenReturn(CourseDetailResponse.builder().build());

        courseService.rollbackToVersion(instructorId, courseId, versionId);

        assertThat(course.getDraftThumbnailUrl()).isEqualTo("new-thumb.png");
    }

    @Test
    void rollbackToVersion_liveChapterNotInSnapshot_marksPendingDelete() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);
        Chapter staleChapter = new Chapter();
        staleChapter.setId(UUID.randomUUID());
        staleChapter.setOrderIndex(5); // not present in the snapshot's chapter order set
        staleChapter.setLessons(new ArrayList<>());
        course.setChapters(new ArrayList<>(List.of(staleChapter)));
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        CourseSnapshotDto snap = CourseSnapshotDto.builder().title(course.getTitle()).chapters(List.of()).build();
        when(objectMapper.readValue(v.getSnapshot(), CourseSnapshotDto.class)).thenReturn(snap);
        when(courseMapper.toDetailResponse(any())).thenReturn(CourseDetailResponse.builder().build());

        courseService.rollbackToVersion(instructorId, courseId, versionId);

        assertThat(staleChapter.getPendingDelete()).isTrue();
        verify(chapterRepository).save(staleChapter);
    }

    @Test
    void rollbackToVersion_brandNewChapterNeverExisted_createsAsDraftWithLessons() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);
        course.setChapters(new ArrayList<>()); // nothing live at all
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        // No soft-deleted chapter/lesson to resurrect -> both resolve to Optional.empty()
        when(chapterRepository.findSoftDeletedByCourseIdAndOrderIndex(eq(courseId), any())).thenReturn(Optional.empty());
        Chapter savedChapter = new Chapter();
        savedChapter.setId(chapterId);
        when(chapterRepository.save(any())).thenReturn(savedChapter);

        CourseSnapshotDto.LessonSnap lessonSnap = CourseSnapshotDto.LessonSnap.builder()
                .title("Brand New Lesson").orderIndex(1).build();
        CourseSnapshotDto.ChapterSnap chapterSnap = CourseSnapshotDto.ChapterSnap.builder()
                .title("Brand New Chapter").orderIndex(1).lessons(List.of(lessonSnap)).build();
        CourseSnapshotDto snap = CourseSnapshotDto.builder()
                .title(course.getTitle()).chapters(List.of(chapterSnap)).build();
        when(objectMapper.readValue(v.getSnapshot(), CourseSnapshotDto.class)).thenReturn(snap);
        when(courseMapper.toDetailResponse(any())).thenReturn(CourseDetailResponse.builder().build());

        courseService.rollbackToVersion(instructorId, courseId, versionId);

        ArgumentCaptor<Chapter> chapterCaptor = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterRepository).save(chapterCaptor.capture());
        assertThat(chapterCaptor.getValue().getTitle()).isEqualTo("Brand New Chapter");
        assertThat(chapterCaptor.getValue().getIsDraft()).isTrue();

        ArgumentCaptor<Lesson> lessonCaptor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository).save(lessonCaptor.capture());
        assertThat(lessonCaptor.getValue().getTitle()).isEqualTo("Brand New Lesson");
        assertThat(lessonCaptor.getValue().getIsDraft()).isTrue();
    }

    @Test
    void rollbackToVersion_resurrectedLessonWithDifferentTitleAndContent_setsDraftFields() throws Exception {
        course.setStatus(CourseStatus.PUBLISHED);
        Chapter ch = new Chapter();
        ch.setId(chapterId);
        ch.setOrderIndex(1);
        ch.setTitle("Chapter 1");
        ch.setLessons(new ArrayList<>()); // no live lesson at this position
        course.setChapters(new ArrayList<>(List.of(ch)));
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        CourseVersion v = CourseVersion.builder()
                .id(versionId).courseId(courseId).status("APPROVED").snapshot("{}").build();
        when(courseVersionRepository.findById(versionId)).thenReturn(Optional.of(v));

        Lesson softDeleted = new Lesson();
        softDeleted.setId(lessonId);
        softDeleted.setChapter(ch);
        softDeleted.setOrderIndex(1);
        softDeleted.setTitle("Old Title");
        softDeleted.setContentText("Old Content");
        softDeleted.setDeletedAt(Instant.now());
        softDeleted.setResources(new ArrayList<>());
        when(lessonRepository.findSoftDeletedByChapterIdAndOrderIndex(chapterId, 1))
                .thenReturn(Optional.of(softDeleted));

        CourseSnapshotDto.LessonSnap lessonSnap = CourseSnapshotDto.LessonSnap.builder()
                .title("New Title").contentText("New Content").orderIndex(1).resources(List.of()).build();
        CourseSnapshotDto.ChapterSnap chapterSnap = CourseSnapshotDto.ChapterSnap.builder()
                .title("Chapter 1").orderIndex(1).lessons(List.of(lessonSnap)).build();
        CourseSnapshotDto snap = CourseSnapshotDto.builder()
                .title(course.getTitle()).chapters(List.of(chapterSnap)).build();
        when(objectMapper.readValue(v.getSnapshot(), CourseSnapshotDto.class)).thenReturn(snap);
        when(courseMapper.toDetailResponse(any())).thenReturn(CourseDetailResponse.builder().build());

        courseService.rollbackToVersion(instructorId, courseId, versionId);

        assertThat(softDeleted.getDraftTitle()).isEqualTo("New Title");
        assertThat(softDeleted.getDraftContentText()).isEqualTo("New Content");
    }

    // ── reorderChapters ──────────────────────────────────────────────────────

    @Test
    void reorderChapters_success_setsOrderIndexPerRequestedOrder() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        Chapter chapter1 = new Chapter();
        chapter1.setId(c1);
        Chapter chapter2 = new Chapter();
        chapter2.setId(c2);
        when(chapterRepository.findAllByCourseIdOrderByOrderIndexAsc(courseId))
                .thenReturn(List.of(chapter1, chapter2));
        when(chapterMapper.toResponse(any())).thenReturn(ChapterResponse.builder().build());

        List<ChapterResponse> result = courseService.reorderChapters(instructorId, courseId, List.of(c2, c1));

        assertThat(result).hasSize(2);
        assertThat(chapter2.getOrderIndex()).isEqualTo(1);
        assertThat(chapter1.getOrderIndex()).isEqualTo(2);
        verify(chapterRepository).saveAll(List.of(chapter1, chapter2));
    }

    @Test
    void reorderChapters_mismatchedIdSet_throwsBusinessException() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        Chapter chapter1 = new Chapter();
        chapter1.setId(UUID.randomUUID());
        when(chapterRepository.findAllByCourseIdOrderByOrderIndexAsc(courseId)).thenReturn(List.of(chapter1));

        assertThatThrownBy(() -> courseService.reorderChapters(instructorId, courseId, List.of(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reorderChapters_pendingCourse_throwsCourseStateException() {
        course.setStatus(CourseStatus.PENDING);
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.reorderChapters(instructorId, courseId, List.of()))
                .isInstanceOf(CourseStateException.class);
        verifyNoInteractions(chapterRepository);
    }

    // ── reorderLessons ───────────────────────────────────────────────────────

    @Test
    void reorderLessons_success_setsOrderIndexPerRequestedOrder() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        UUID l1 = UUID.randomUUID();
        UUID l2 = UUID.randomUUID();
        Lesson lesson1 = new Lesson();
        lesson1.setId(l1);
        Lesson lesson2 = new Lesson();
        lesson2.setId(l2);
        when(lessonRepository.findAllByChapterIdOrderByOrderIndexAsc(chapterId))
                .thenReturn(List.of(lesson1, lesson2));
        when(lessonMapper.toResponse(any())).thenReturn(LessonResponse.builder().build());

        List<LessonResponse> result = courseService.reorderLessons(instructorId, courseId, chapterId, List.of(l2, l1));

        assertThat(result).hasSize(2);
        assertThat(lesson2.getOrderIndex()).isEqualTo(1);
        assertThat(lesson1.getOrderIndex()).isEqualTo(2);
        verify(lessonRepository).saveAll(List.of(lesson1, lesson2));
    }

    @Test
    void reorderLessons_mismatchedIdSet_throwsBusinessException() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        Lesson lesson1 = new Lesson();
        lesson1.setId(UUID.randomUUID());
        when(lessonRepository.findAllByChapterIdOrderByOrderIndexAsc(chapterId)).thenReturn(List.of(lesson1));

        assertThatThrownBy(() -> courseService.reorderLessons(instructorId, courseId, chapterId, List.of(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reorderLessons_chapterNotInCourse_throwsChapterNotFoundException() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.reorderLessons(instructorId, courseId, chapterId, List.of()))
                .isInstanceOf(ChapterNotFoundException.class);
    }

    // ── addLesson: QUIZ type / resolveQuizForLesson ─────────────────────────

    @Test
    void addLesson_quizTypeWithExistingQuizId_attachesQuiz() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        Chapter ch = new Chapter();
        ch.setId(chapterId);
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(ch));
        when(lessonRepository.findMaxOrderIndexByChapterId(chapterId)).thenReturn(0);

        UUID quizId = UUID.randomUUID();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(new QuizEntity()));
        when(lessonRepository.findByQuizId(quizId)).thenReturn(Optional.empty());
        when(lessonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lessonMapper.toResponse(any())).thenAnswer(inv -> {
            Lesson saved = inv.getArgument(0);
            return LessonResponse.builder().id(saved.getId()).title(saved.getTitle()).build();
        });

        CreateLessonRequest req = new CreateLessonRequest();
        req.setTitle("Quiz Lesson");
        req.setType(LessonType.QUIZ);
        req.setQuizId(quizId);

        courseService.addLesson(instructorId, courseId, chapterId, req);

        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository).save(captor.capture());
        assertThat(captor.getValue().getQuizId()).isEqualTo(quizId);
        verifyNoInteractions(quizService);
    }

    @Test
    void addLesson_quizTypeWithNewQuiz_createsQuizAndAttaches() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        Chapter ch = new Chapter();
        ch.setId(chapterId);
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(ch));
        when(lessonRepository.findMaxOrderIndexByChapterId(chapterId)).thenReturn(0);

        UUID createdQuizId = UUID.randomUUID();
        when(quizService.create(eq(courseId), eq(instructorId), any(QuizMetadataRequest.class)))
                .thenReturn(QuizSummaryResponse.builder().id(createdQuizId).build());
        when(lessonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lessonMapper.toResponse(any())).thenReturn(LessonResponse.builder().build());

        CreateLessonRequest req = new CreateLessonRequest();
        req.setTitle("Quiz Lesson");
        req.setType(LessonType.QUIZ);
        req.setNewQuiz(new QuizMetadataRequest());

        courseService.addLesson(instructorId, courseId, chapterId, req);

        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository).save(captor.capture());
        assertThat(captor.getValue().getQuizId()).isEqualTo(createdQuizId);
        verifyNoInteractions(quizRepository);
    }

    @Test
    void addLesson_quizTypeWithBothQuizIdAndNewQuiz_throwsBusinessException() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        CreateLessonRequest req = new CreateLessonRequest();
        req.setTitle("Quiz Lesson");
        req.setType(LessonType.QUIZ);
        req.setQuizId(UUID.randomUUID());
        req.setNewQuiz(new QuizMetadataRequest());

        assertThatThrownBy(() -> courseService.addLesson(instructorId, courseId, chapterId, req))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addLesson_quizTypeWithNeitherQuizIdNorNewQuiz_throwsBusinessException() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        CreateLessonRequest req = new CreateLessonRequest();
        req.setTitle("Quiz Lesson");
        req.setType(LessonType.QUIZ);

        assertThatThrownBy(() -> courseService.addLesson(instructorId, courseId, chapterId, req))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addLesson_quizIdNotInCourse_throwsBusinessException() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        UUID quizId = UUID.randomUUID();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.empty());

        CreateLessonRequest req = new CreateLessonRequest();
        req.setTitle("Quiz Lesson");
        req.setType(LessonType.QUIZ);
        req.setQuizId(quizId);

        assertThatThrownBy(() -> courseService.addLesson(instructorId, courseId, chapterId, req))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addLesson_quizAlreadyAttachedToAnotherLesson_throwsBusinessException() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        UUID quizId = UUID.randomUUID();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(new QuizEntity()));
        Lesson otherLesson = new Lesson();
        otherLesson.setId(UUID.randomUUID());
        when(lessonRepository.findByQuizId(quizId)).thenReturn(Optional.of(otherLesson));

        CreateLessonRequest req = new CreateLessonRequest();
        req.setTitle("Quiz Lesson");
        req.setType(LessonType.QUIZ);
        req.setQuizId(quizId);

        assertThatThrownBy(() -> courseService.addLesson(instructorId, courseId, chapterId, req))
                .isInstanceOf(BusinessException.class);
    }

    // ── updateLesson: QUIZ reassignment ─────────────────────────────────────

    @Test
    void updateLesson_quizType_reassignsQuizId() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setType(LessonType.QUIZ);
        lesson.setQuizId(UUID.randomUUID());
        when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson));

        UUID newQuizId = UUID.randomUUID();
        when(quizRepository.findByIdAndCourseId(newQuizId, courseId)).thenReturn(Optional.of(new QuizEntity()));
        when(lessonRepository.findByQuizId(newQuizId)).thenReturn(Optional.empty());
        when(lessonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lessonMapper.toResponse(any())).thenReturn(LessonResponse.builder().build());

        UpdateLessonRequest req = new UpdateLessonRequest();
        req.setQuizId(newQuizId);

        courseService.updateLesson(instructorId, courseId, chapterId, lessonId, req);

        assertThat(lesson.getQuizId()).isEqualTo(newQuizId);
        verifyNoInteractions(studentCourseService);
    }

    @Test
    void updateLesson_quizReassignmentWithResetFlag_resetsInProgressStudents() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setType(LessonType.QUIZ);
        lesson.setQuizId(UUID.randomUUID());
        when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson));

        UUID newQuizId = UUID.randomUUID();
        when(quizRepository.findByIdAndCourseId(newQuizId, courseId)).thenReturn(Optional.of(new QuizEntity()));
        when(lessonRepository.findByQuizId(newQuizId)).thenReturn(Optional.empty());
        when(lessonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lessonMapper.toResponse(any())).thenReturn(LessonResponse.builder().build());

        UpdateLessonRequest req = new UpdateLessonRequest();
        req.setQuizId(newQuizId);
        req.setResetProgressForInProgressStudents(true);

        courseService.updateLesson(instructorId, courseId, chapterId, lessonId, req);

        verify(studentCourseService).resetLessonProgressForInProgressStudents(courseId, lessonId);
    }

    @Test
    void updateLesson_quizTypeWithNoQuizChangeRequested_doesNotTouchQuizRepository() {
        when(courseRepository.findByIdWithCategory(courseId)).thenReturn(Optional.of(course));
        when(chapterRepository.findByIdAndCourseId(chapterId, courseId)).thenReturn(Optional.of(new Chapter()));

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setType(LessonType.QUIZ);
        lesson.setQuizId(UUID.randomUUID());
        when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson));
        when(lessonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lessonMapper.toResponse(any())).thenReturn(LessonResponse.builder().build());

        UpdateLessonRequest req = new UpdateLessonRequest();
        req.setTitle("Renamed");

        courseService.updateLesson(instructorId, courseId, chapterId, lessonId, req);

        verifyNoInteractions(quizRepository, quizService, studentCourseService);
    }
}
