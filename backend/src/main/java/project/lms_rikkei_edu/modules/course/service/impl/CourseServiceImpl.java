package project.lms_rikkei_edu.modules.course.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.Chapter;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.CourseCategory;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.exception.*;
import project.lms_rikkei_edu.modules.course.mapper.ChapterMapper;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.mapper.LessonMapper;
import project.lms_rikkei_edu.modules.course.entity.CourseApprovalLog;
import project.lms_rikkei_edu.modules.course.entity.CourseVersion;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.CourseService;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final LessonResourceRepository lessonResourceRepository;
    private final CourseCategoryRepository categoryRepository;
    private final CourseApprovalLogRepository approvalLogRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final CourseMapper courseMapper;
    private final ObjectMapper objectMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;

    @Override
    public CourseResponse createCourse(UUID instructorId, CreateCourseRequest request) {
        CourseCategory category = resolveCategory(request.getCategoryId());
        String slug = generateUniqueSlug(request.getTitle(), null);

        Course course = Course.builder()
                .instructorId(instructorId)
                .title(request.getTitle())
                .slug(slug)
                .description(request.getDescription())
                .level(request.getLevel())
                .thumbnailUrl(request.getThumbnailUrl())
                .chatEnabled(request.getChatEnabled() != null ? request.getChatEnabled() : false)
                .category(category)
                .status(CourseStatus.DRAFT)
                .build();

        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetail(UUID instructorId, UUID courseId) {
        Course course = courseRepository.findByIdWithCategory(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        assertOwner(course, instructorId);
        // Trigger lazy load resources (deleted_at IS NULL filtered by @Where on entity)
        course.getChapters().forEach(ch ->
            ch.getLessons().forEach(l -> l.getResources().size())
        );
        return courseMapper.toDetailResponse(course);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseResponse> listCourses(UUID instructorId, Pageable pageable) {
        return courseRepository.findAllByInstructorId(instructorId, pageable)
                .map(courseMapper::toResponse);
    }

    @Override
    public CourseResponse updateCourse(UUID instructorId, UUID courseId, UpdateCourseRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        if (isLive(course)) {
            // Khóa học đang published — ghi vào draft fields thay vì sửa trực tiếp
            if (request.getTitle() != null) course.setDraftTitle(request.getTitle());
            if (request.getDescription() != null) course.setDraftDescription(request.getDescription());
            if (request.getLevel() != null) course.setDraftLevel(request.getLevel());
            if (request.getThumbnailUrl() != null) course.setDraftThumbnailUrl(request.getThumbnailUrl());
        } else {
            if (request.getTitle() != null) {
                course.setTitle(request.getTitle());
                course.setSlug(generateUniqueSlug(request.getTitle(), courseId));
            }
            if (request.getDescription() != null) course.setDescription(request.getDescription());
            if (request.getLevel() != null) course.setLevel(request.getLevel());
            if (request.getThumbnailUrl() != null) course.setThumbnailUrl(request.getThumbnailUrl());
            if (request.getChatEnabled() != null) course.setChatEnabled(request.getChatEnabled());
            if (request.getCategoryId() != null) course.setCategory(resolveCategory(request.getCategoryId()));
        }

        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Override
    public void deleteCourse(UUID instructorId, UUID courseId) {
        Course course = loadOwnedCourse(instructorId, courseId);
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            throw new CourseStateException("Cannot delete a published course");
        }
        course.setDeletedAt(Instant.now());
        courseRepository.save(course);
    }

    @Override
    public CourseDetailResponse submitForApproval(UUID instructorId, UUID courseId, String changeSummary) {
        Course course = loadOwnedCourse(instructorId, courseId);

        // Force-load toàn bộ nội dung để build snapshot
        course.getChapters().forEach(ch -> ch.getLessons().forEach(l -> l.getResources().size()));

        if (course.getStatus() == CourseStatus.DRAFT || course.getStatus() == CourseStatus.REJECTED) {
            long lessonCount = lessonRepository.countByCourseId(courseId);
            if (lessonCount == 0) {
                throw new CourseStateException("Course must have at least one lesson before submitting");
            }
            course.setStatus(CourseStatus.PENDING);
            course.setSubmittedAt(Instant.now());
            course.setRejectionReason(null);
            createCourseVersion(instructorId, courseId, changeSummary, course);
            saveLogWithSnapshot(instructorId, courseId, "SUBMITTED_FIRST", course);

        } else if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
            course.setChangeSummary(changeSummary);
            course.setSubmittedAt(Instant.now());
            course.setDraftRejectionReason(null);
            createCourseVersion(instructorId, courseId, changeSummary, course);
            saveLogWithSnapshot(instructorId, courseId, "SUBMITTED_UPDATE", course);

        } else if (course.getStatus() == CourseStatus.PUBLISHED) {
            boolean hasDraftChanges = course.isHasPendingDraft();
            boolean hasResourceChanges = lessonResourceRepository.existsByCourseIdAndIsNewInUpdateTrue(courseId)
                    || lessonResourceRepository.existsByCourseIdAndPendingDeleteTrue(courseId);
            if (!hasDraftChanges && !hasResourceChanges) {
                throw new CourseStateException("Không có thay đổi nào để gửi duyệt");
            }
            course.setChangeSummary(changeSummary);
            course.setSubmittedAt(Instant.now());
            course.setDraftRejectionReason(null);
            course.setStatus(CourseStatus.PENDING_UPDATE);
            course.setPendingUpdateAt(Instant.now());
            createCourseVersion(instructorId, courseId, changeSummary, course);
            saveLogWithSnapshot(instructorId, courseId, "SUBMITTED_UPDATE", course);

        } else {
            throw new CourseStateException("Course cannot be submitted in current status: " + course.getStatus());
        }

        return courseMapper.toDetailResponse(courseRepository.save(course));
    }

    @Override
    public CourseDetailResponse withdrawFromReview(UUID instructorId, UUID courseId) {
        Course course = loadOwnedCourse(instructorId, courseId);

        if (course.getStatus() == CourseStatus.PENDING) {
            // Lần đầu pending → về DRAFT
            course.setStatus(CourseStatus.DRAFT);
            course.setSubmittedAt(null);

        } else if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
            // Rút lại khỏi hàng chờ → về PUBLISHED, xóa tất cả draft content
            initChapters(course);
            clearAllDrafts(course);
            course.setStatus(CourseStatus.PUBLISHED);
            course.setPendingUpdateAt(null);
            course.setSubmittedAt(null);

        } else if (course.getStatus() == CourseStatus.PUBLISHED) {
            boolean hasDraft = course.isHasPendingDraft();
            boolean hasResourceChanges = lessonResourceRepository.existsByCourseIdAndIsNewInUpdateTrue(courseId)
                    || lessonResourceRepository.existsByCourseIdAndPendingDeleteTrue(courseId);
            if (!hasDraft && !hasResourceChanges) {
                throw new CourseStateException("No pending draft to withdraw");
            }
            // Hủy toàn bộ draft (kể cả resource changes)
            initChapters(course);
            clearAllDrafts(course);
            // Reset resource flags
            lessonResourceRepository.findAllByCourseIdAndIsNewInUpdateTrue(courseId)
                    .forEach(r -> { r.setIsNewInUpdate(false); lessonResourceRepository.save(r); });
            lessonResourceRepository.findAllByCourseIdAndPendingDeleteTrue(courseId)
                    .forEach(r -> { r.setPendingDelete(false); r.setStatus("ACTIVE"); lessonResourceRepository.save(r); });
            course.setPendingUpdateAt(null);
            course.setSubmittedAt(null);

        } else {
            throw new CourseStateException("No pending draft to withdraw");
        }

        return courseMapper.toDetailResponse(courseRepository.save(course));
    }

    @Override
    public ChapterResponse addChapter(UUID instructorId, UUID courseId, CreateChapterRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        int nextOrder = chapterRepository.findMaxOrderIndexByCourseId(courseId) + 1;
        boolean draft = isLive(course);

        Chapter chapter = Chapter.builder()
                .course(course)
                .title(request.getTitle())
                .description(request.getDescription())
                .orderIndex(nextOrder)
                .isDraft(draft)
                .build();

        return chapterMapper.toResponse(chapterRepository.save(chapter));
    }

    @Override
    public ChapterResponse updateChapter(UUID instructorId, UUID courseId, UUID chapterId, UpdateChapterRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        Chapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        // Chapter title update: sửa trực tiếp ngay cả khi published (ít tác động, không cần draft)
        if (request.getTitle() != null) chapter.setTitle(request.getTitle());
        if (request.getDescription() != null) chapter.setDescription(request.getDescription());

        return chapterMapper.toResponse(chapterRepository.save(chapter));
    }

    @Override
    public void deleteChapter(UUID instructorId, UUID courseId, UUID chapterId) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        Chapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        if (isLive(course)) {
            if (Boolean.TRUE.equals(chapter.getIsDraft())) {
                // Chương draft chưa duyệt → xóa ngay không cần pending
                chapterRepository.delete(chapter);
            } else {
                // Chương live → đánh dấu chờ xóa (sẽ xóa thật khi admin duyệt)
                chapter.setPendingDelete(true);
                chapterRepository.save(chapter);
            }
        } else {
            chapterRepository.delete(chapter);
        }
    }

    @Override
    public LessonResponse addLesson(UUID instructorId, UUID courseId, UUID chapterId, CreateLessonRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        Chapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        int nextOrder = lessonRepository.findMaxOrderIndexByChapterId(chapterId) + 1;
        boolean draft = isLive(course);

        Lesson lesson = Lesson.builder()
                .chapter(chapter)
                .courseId(courseId)
                .title(request.getTitle())
                .type(request.getType())
                .contentText(request.getContentText())
                .isPreview(request.getIsPreview() != null ? request.getIsPreview() : false)
                .orderIndex(nextOrder)
                .isDraft(draft)
                .build();

        return lessonMapper.toResponse(lessonRepository.save(lesson));
    }

    @Override
    public LessonResponse updateLesson(UUID instructorId, UUID courseId, UUID chapterId, UUID lessonId, UpdateLessonRequest request) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        Lesson lesson = lessonRepository.findByIdAndCourseId(lessonId, courseId)
                .orElseThrow(() -> new LessonNotFoundException(lessonId));

        if (isLive(course) && !Boolean.TRUE.equals(lesson.getIsDraft())) {
            // Lesson live trong published course → ghi vào draft fields
            if (request.getTitle() != null) lesson.setDraftTitle(request.getTitle());
            if (request.getContentText() != null) lesson.setDraftContentText(request.getContentText());
            if (request.getIsPreview() != null) lesson.setIsPreview(request.getIsPreview());
        } else {
            // DRAFT/REJECTED course, hoặc lesson chính nó đang là draft → sửa trực tiếp
            if (request.getTitle() != null) lesson.setTitle(request.getTitle());
            if (request.getType() != null) lesson.setType(request.getType());
            if (request.getContentText() != null) lesson.setContentText(request.getContentText());
            if (request.getIsPreview() != null) lesson.setIsPreview(request.getIsPreview());
        }

        return lessonMapper.toResponse(lessonRepository.save(lesson));
    }

    @Override
    public void deleteLesson(UUID instructorId, UUID courseId, UUID chapterId, UUID lessonId) {
        Course course = loadOwnedCourse(instructorId, courseId);
        assertEditable(course);

        chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new ChapterNotFoundException(chapterId));

        Lesson lesson = lessonRepository.findByIdAndCourseId(lessonId, courseId)
                .orElseThrow(() -> new LessonNotFoundException(lessonId));

        if (isLive(course)) {
            if (Boolean.TRUE.equals(lesson.getIsDraft())) {
                // Lesson draft chưa duyệt → xóa ngay
                lessonRepository.delete(lesson);
            } else {
                // Lesson live → đánh dấu chờ xóa (sẽ xóa thật khi admin duyệt)
                lesson.setPendingDelete(true);
                lessonRepository.save(lesson);
            }
        } else {
            lessonRepository.delete(lesson);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseApprovalLogResponse> getCourseHistory(UUID instructorId, UUID courseId) {
        loadOwnedCourse(instructorId, courseId); // verify ownership
        return approvalLogRepository.findByCourseIdOrderByCreatedAtAsc(courseId).stream()
                .map(log -> CourseApprovalLogResponse.builder()
                        .id(log.getId())
                        .action(log.getAction())
                        .reason(log.getReason())
                        .createdAt(log.getCreatedAt())
                        .actorType(isInstructorAction(log.getAction()) ? "INSTRUCTOR" : "ADMIN")
                        .snapshot(log.getSnapshot())
                        .build())
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True nếu khóa học đang live (PUBLISHED hoặc PENDING_UPDATE) */
    private boolean isLive(Course course) {
        return course.getStatus() == CourseStatus.PUBLISHED
                || course.getStatus() == CourseStatus.PENDING_UPDATE;
    }



    /** Force-load chapters + lessons (cần gọi trong transaction) */
    private void initChapters(Course course) {
        course.getChapters().forEach(ch ->
            ch.getLessons().forEach(l -> l.getResources().size())
        );
    }

    /**
     * Xóa toàn bộ nội dung draft khi instructor withdraw hoặc admin reject.
     * Gọi sau initChapters().
     */
    private void clearAllDrafts(Course course) {
        // Xóa draft metadata
        course.setDraftTitle(null);
        course.setDraftDescription(null);
        course.setDraftLevel(null);
        course.setDraftThumbnailUrl(null);
        course.setChangeSummary(null);
        course.setDraftRejectionReason(null);

        List<Chapter> draftChapters = new ArrayList<>();
        for (Chapter ch : course.getChapters()) {
            if (Boolean.TRUE.equals(ch.getIsDraft())) {
                draftChapters.add(ch);
            } else {
                ch.setPendingDelete(false);
                // Xử lý lessons trong chương live
                List<Lesson> draftLessons = new ArrayList<>();
                for (Lesson l : ch.getLessons()) {
                    if (Boolean.TRUE.equals(l.getIsDraft())) {
                        draftLessons.add(l);
                    } else {
                        l.setPendingDelete(false);
                        l.setDraftTitle(null);
                        l.setDraftContentText(null);
                    }
                }
                // Xóa draft lessons qua orphanRemoval
                ch.getLessons().removeAll(draftLessons);
            }
        }
        // Xóa draft chapters qua orphanRemoval
        course.getChapters().removeAll(draftChapters);
    }

    private void saveLog(UUID actorId, UUID courseId, String action) {
        approvalLogRepository.save(
                CourseApprovalLog.builder()
                        .courseId(courseId)
                        .adminId(actorId)
                        .action(action)
                        .createdAt(Instant.now())
                        .build()
        );
    }

    private void saveLogWithSnapshot(UUID actorId, UUID courseId, String action, Course course) {
        String snapshotJson = null;
        try {
            snapshotJson = objectMapper.writeValueAsString(buildSnapshot(course));
        } catch (Exception e) {
            log.warn("Failed to serialize course snapshot for courseId={}: {}", courseId, e.getMessage());
        }
        approvalLogRepository.save(
                CourseApprovalLog.builder()
                        .courseId(courseId)
                        .adminId(actorId)
                        .action(action)
                        .createdAt(Instant.now())
                        .snapshot(snapshotJson)
                        .build()
        );
    }

    /**
     * Snapshot phản ánh trạng thái course SAU KHI duyệt (preview of approved state):
     * - SUBMITTED_FIRST: toàn bộ chapter/lesson hiện tại
     * - SUBMITTED_UPDATE: apply draft — bao gồm chapter/lesson mới (isDraft=true),
     *   loại bỏ pendingDelete, apply draftTitle/draftContentText
     */
    private CourseSnapshotDto buildSnapshot(Course course) {
        boolean isUpdate = course.getStatus() == CourseStatus.PENDING_UPDATE
                || course.getStatus() == CourseStatus.PUBLISHED;

        String title       = isUpdate && course.getDraftTitle()       != null ? course.getDraftTitle()       : course.getTitle();
        String description = isUpdate && course.getDraftDescription() != null ? course.getDraftDescription() : course.getDescription();
        String level       = isUpdate && course.getDraftLevel()       != null ? course.getDraftLevel().name() : (course.getLevel() != null ? course.getLevel().name() : null);
        String thumbnail   = isUpdate && course.getDraftThumbnailUrl()!= null ? course.getDraftThumbnailUrl(): course.getThumbnailUrl();
        String catName     = course.getCategory() != null ? course.getCategory().getName() : null;

        List<CourseSnapshotDto.ChapterSnap> chapterSnaps = new ArrayList<>();
        for (Chapter ch : course.getChapters()) {
            if (isUpdate && Boolean.TRUE.equals(ch.getPendingDelete())) continue;

            List<CourseSnapshotDto.LessonSnap> lessonSnaps = new ArrayList<>();
            for (Lesson l : ch.getLessons()) {
                if (isUpdate && Boolean.TRUE.equals(l.getPendingDelete())) continue;

                String lTitle   = (isUpdate && l.getDraftTitle()       != null) ? l.getDraftTitle()       : l.getTitle();
                String lContent = (isUpdate && l.getDraftContentText() != null) ? l.getDraftContentText() : l.getContentText();

                List<CourseSnapshotDto.ResourceSnap> resSnaps = (l.getResources() == null) ? List.of()
                    : l.getResources().stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getPendingDelete()))
                        .map(r -> CourseSnapshotDto.ResourceSnap.builder()
                                .displayName(r.getDisplayName() != null ? r.getDisplayName() : r.getOriginalFilename())
                                .resourceType(r.getResourceType() != null ? r.getResourceType().name() : null)
                                .mimeType(r.getMimeType())
                                .fileSizeBytes(r.getFileSizeBytes())
                                .build())
                        .toList();

                lessonSnaps.add(CourseSnapshotDto.LessonSnap.builder()
                        .title(lTitle)
                        .lessonType(l.getType() != null ? l.getType().name() : null)
                        .contentText(lContent)
                        .durationSeconds(l.getDurationSeconds())
                        .orderIndex(l.getOrderIndex())
                        .resources(resSnaps)
                        .build());
            }

            chapterSnaps.add(CourseSnapshotDto.ChapterSnap.builder()
                    .title(ch.getTitle())
                    .orderIndex(ch.getOrderIndex())
                    .lessons(lessonSnaps)
                    .build());
        }

        return CourseSnapshotDto.builder()
                .title(title)
                .description(description)
                .level(level)
                .thumbnailUrl(thumbnail)
                .categoryName(catName)
                .chapters(chapterSnaps)
                .build();
    }

    @Override
    public CourseDetailResponse rollbackToVersion(UUID instructorId, UUID courseId, UUID versionId) {
        Course course = loadOwnedCourse(instructorId, courseId);

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new CourseStateException("Chỉ có thể rollback khi khóa học đang PUBLISHED (không có bản cập nhật đang chờ duyệt)");
        }

        CourseVersion version = courseVersionRepository.findById(versionId)
                .filter(v -> v.getCourseId().equals(courseId))
                .orElseThrow(() -> new IllegalArgumentException("Version không tồn tại hoặc không thuộc khóa học này"));

        if (!"APPROVED".equals(version.getStatus())) {
            throw new CourseStateException("Chỉ có thể rollback về version đã được duyệt (APPROVED)");
        }

        CourseSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(version.getSnapshot(), CourseSnapshotDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể đọc snapshot của version này: " + e.getMessage());
        }

        // Force-load toàn bộ nội dung hiện tại
        initChapters(course);

        // 1. Xóa draft cũ nếu có
        clearAllDrafts(course);
        lessonResourceRepository.findAllByCourseIdAndIsNewInUpdateTrue(courseId)
                .forEach(r -> { r.setIsNewInUpdate(false); lessonResourceRepository.save(r); });
        lessonResourceRepository.findAllByCourseIdAndPendingDeleteTrue(courseId)
                .forEach(r -> { r.setPendingDelete(false); r.setStatus("ACTIVE"); lessonResourceRepository.save(r); });

        // 2. Apply diff metadata
        if (!Objects.equals(snapshot.getTitle(), course.getTitle()))
            course.setDraftTitle(snapshot.getTitle());
        if (!Objects.equals(snapshot.getDescription(), course.getDescription()))
            course.setDraftDescription(snapshot.getDescription());
        if (snapshot.getLevel() != null) {
            String currentLevel = course.getLevel() != null ? course.getLevel().name() : null;
            if (!snapshot.getLevel().equals(currentLevel))
                course.setDraftLevel(CourseLevel.valueOf(snapshot.getLevel()));
        }
        if (!Objects.equals(snapshot.getThumbnailUrl(), course.getThumbnailUrl()))
            course.setDraftThumbnailUrl(snapshot.getThumbnailUrl());

        // 3. Apply diff chapters + lessons + resources
        List<CourseSnapshotDto.ChapterSnap> snapChapters =
                snapshot.getChapters() != null ? snapshot.getChapters() : List.of();
        Set<Integer> snapChapterOrders = snapChapters.stream()
                .map(CourseSnapshotDto.ChapterSnap::getOrderIndex)
                .collect(Collectors.toSet());

        // Đánh dấu chapters trong live nhưng không có trong snapshot → pendingDelete
        Map<Integer, Chapter> liveChaptersByOrder = new HashMap<>();
        for (Chapter ch : course.getChapters()) {
            liveChaptersByOrder.put(ch.getOrderIndex(), ch);
            if (!snapChapterOrders.contains(ch.getOrderIndex())) {
                ch.setPendingDelete(true);
                chapterRepository.save(ch);
            }
        }

        // Chapters trong snapshot — thêm mới hoặc diff lessons
        for (CourseSnapshotDto.ChapterSnap snapCh : snapChapters) {
            Chapter liveCh = liveChaptersByOrder.get(snapCh.getOrderIndex());
            if (liveCh == null) {
                // Chapter bị xóa trước đó → tạo lại dạng draft
                Chapter newCh = Chapter.builder()
                        .course(course)
                        .title(snapCh.getTitle())
                        .orderIndex(snapCh.getOrderIndex())
                        .isDraft(true)
                        .build();
                liveCh = chapterRepository.save(newCh);
                // Tạo luôn lessons trong chapter mới
                if (snapCh.getLessons() != null) {
                    for (CourseSnapshotDto.LessonSnap snapL : snapCh.getLessons()) {
                        Lesson newL = buildDraftLesson(liveCh, courseId, snapL);
                        lessonRepository.save(newL);
                    }
                }
            } else {
                // Chapter tồn tại → diff lessons
                applyLessonDiffForRollback(liveCh, snapCh, courseId);
            }
        }

        courseRepository.save(course);
        return courseMapper.toDetailResponse(course);
    }

    private void applyLessonDiffForRollback(Chapter chapter, CourseSnapshotDto.ChapterSnap snapCh, UUID courseId) {
        List<CourseSnapshotDto.LessonSnap> snapLessons =
                snapCh.getLessons() != null ? snapCh.getLessons() : List.of();
        Set<Integer> snapLessonOrders = snapLessons.stream()
                .map(CourseSnapshotDto.LessonSnap::getOrderIndex)
                .collect(Collectors.toSet());

        Map<Integer, Lesson> liveLessonsByOrder = new HashMap<>();
        for (Lesson l : chapter.getLessons()) {
            liveLessonsByOrder.put(l.getOrderIndex(), l);
            if (!snapLessonOrders.contains(l.getOrderIndex())) {
                l.setPendingDelete(true);
                lessonRepository.save(l);
            }
        }

        for (CourseSnapshotDto.LessonSnap snapL : snapLessons) {
            Lesson liveL = liveLessonsByOrder.get(snapL.getOrderIndex());
            if (liveL == null) {
                lessonRepository.save(buildDraftLesson(chapter, courseId, snapL));
            } else {
                // Apply title/content diff
                if (!Objects.equals(snapL.getTitle(), liveL.getTitle()))
                    liveL.setDraftTitle(snapL.getTitle());
                if (!Objects.equals(snapL.getContentText(), liveL.getContentText()))
                    liveL.setDraftContentText(snapL.getContentText());
                // Resource diff — chỉ đánh dấu xóa những gì không có trong snapshot
                applyResourceDiffForRollback(liveL, snapL);
                lessonRepository.save(liveL);
            }
        }
    }

    private void applyResourceDiffForRollback(Lesson lesson, CourseSnapshotDto.LessonSnap snapL) {
        List<CourseSnapshotDto.ResourceSnap> snapResources =
                snapL.getResources() != null ? snapL.getResources() : List.of();
        Set<String> snapNames = snapResources.stream()
                .map(CourseSnapshotDto.ResourceSnap::getDisplayName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (LessonResource r : lesson.getResources()) {
            if (r.getDeletedAt() != null) continue;
            String name = r.getDisplayName() != null ? r.getDisplayName() : r.getOriginalFilename();
            if (!snapNames.contains(name)) {
                r.setPendingDelete(true);
                r.setStatus("PENDING_DELETE");
                lessonResourceRepository.save(r);
            }
        }
        // Note: không thể tạo lại resource đã xóa (file không còn trên S3)
    }

    private Lesson buildDraftLesson(Chapter chapter, UUID courseId, CourseSnapshotDto.LessonSnap snapL) {
        LessonType type = null;
        if (snapL.getLessonType() != null) {
            try { type = LessonType.valueOf(snapL.getLessonType()); } catch (Exception ignored) {}
        }
        return Lesson.builder()
                .chapter(chapter)
                .courseId(courseId)
                .title(snapL.getTitle())
                .type(type)
                .contentText(snapL.getContentText())
                .orderIndex(snapL.getOrderIndex())
                .isDraft(true)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseVersionResponse> getCourseVersions(UUID instructorId, UUID courseId) {
        loadOwnedCourse(instructorId, courseId);
        return courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(courseId).stream()
                .map(v -> CourseVersionResponse.builder()
                        .id(v.getId())
                        .versionNumber(v.getVersionNumber())
                        .status(v.getStatus())
                        .changeSummary(v.getChangeSummary())
                        .rejectionReason(v.getRejectionReason())
                        .submittedAt(v.getSubmittedAt())
                        .reviewedAt(v.getReviewedAt())
                        .snapshot(v.getSnapshot())
                        .build())
                .toList();
    }

    /** Tạo CourseVersion record khi instructor submit. Trả về version đã lưu. */
    private CourseVersion createCourseVersion(UUID instructorId, UUID courseId, String changeSummary, Course course) {
        int nextNum = courseVersionRepository.findMaxVersionNumberByCourseId(courseId) + 1;
        String snapshotJson = null;
        try {
            snapshotJson = objectMapper.writeValueAsString(buildSnapshot(course));
        } catch (Exception e) {
            log.warn("Failed to serialize snapshot for version courseId={}: {}", courseId, e.getMessage());
        }
        return courseVersionRepository.save(CourseVersion.builder()
                .courseId(courseId)
                .versionNumber(nextNum)
                .status("PENDING")
                .snapshot(snapshotJson)
                .changeSummary(changeSummary)
                .submittedBy(instructorId)
                .submittedAt(Instant.now())
                .build());
    }

    private boolean isInstructorAction(String action) {
        return action != null && (action.startsWith("SUBMITTED") || action.equals("WITHDRAWN") || action.equals("DISCARDED"));
    }

    private Course loadOwnedCourse(UUID instructorId, UUID courseId) {
        Course course = courseRepository.findByIdWithCategory(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        assertOwner(course, instructorId);
        return course;
    }

    private void assertOwner(Course course, UUID instructorId) {
        if (!course.getInstructorId().equals(instructorId)) {
            throw new CourseNotOwnedException();
        }
    }

    private void assertEditable(Course course) {
        if (course.getStatus() == CourseStatus.PENDING) {
            throw new CourseStateException("Không thể chỉnh sửa khóa học đang chờ duyệt lần đầu");
        }
        if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
            throw new CourseStateException("Không thể chỉnh sửa khóa học đang chờ duyệt cập nhật — hãy đợi admin xử lý hoặc hủy cập nhật trước");
        }
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new CourseStateException("Cannot modify an archived course");
        }
    }

    private CourseCategory resolveCategory(UUID categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
    }

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private String generateUniqueSlug(String title, UUID excludeId) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .trim();
        base = WHITESPACE.matcher(base).replaceAll("-");
        base = NON_LATIN.matcher(base).replaceAll("");

        String slug = base;
        int suffix = 1;

        while (excludeId == null
                ? courseRepository.existsBySlug(slug)
                : courseRepository.existsBySlugAndIdNot(slug, excludeId)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
