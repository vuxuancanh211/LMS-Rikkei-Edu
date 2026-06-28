package project.lms_rikkei_edu.modules.course.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.exception.CourseNotFoundException;
import project.lms_rikkei_edu.modules.course.exception.CourseStateException;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.AdminCourseService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminCourseServiceImpl implements AdminCourseService {

    private static final String VERSION_PENDING   = "PENDING";
    private static final String VERSION_APPROVED  = "APPROVED";
    private static final String VERSION_REJECTED  = "REJECTED";
    private static final String DIFF_UNCHANGED    = "UNCHANGED";
    private static final String DIFF_MODIFIED     = "MODIFIED";

    private final CourseRepository courseRepo;
    private final CourseMapper courseMapper;
    private final CourseApprovalLogRepository approvalLogRepo;
    private final LessonResourceRepository lessonResourceRepo;
    private final CourseVersionRepository courseVersionRepo;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<CourseResponse> listPendingCourses(Pageable pageable) {
        return courseRepo.findAllByStatusIn(
                List.of(CourseStatus.PENDING, CourseStatus.PENDING_UPDATE), pageable)
                .map(courseMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseResponse> listAllCourses(Pageable pageable) {
        return courseRepo.findAllByStatusIn(
                List.of(CourseStatus.DRAFT, CourseStatus.PENDING, CourseStatus.PENDING_UPDATE,
                        CourseStatus.PUBLISHED, CourseStatus.REJECTED, CourseStatus.ARCHIVED), pageable)
                .map(courseMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetail(UUID courseId) {
        Course course = courseRepo.findByIdWithCategory(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        course.getChapters().forEach(ch ->
            ch.getLessons().forEach(l -> l.getResources().size())
        );
        return courseMapper.toDetailResponse(course);
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceDownloadUrlResponse getResourceDownloadUrl(UUID resourceId) {
        LessonResource resource = lessonResourceRepo.findById(resourceId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Resource không tồn tại: " + resourceId));
        String url = s3Service.generatePresignedGetUrl(resource.getS3Key()).url().toString();
        return ResourceDownloadUrlResponse.builder()
                .url(url)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Override
    public CourseDetailResponse approveCourse(UUID adminId, UUID courseId) {
        Course course = loadCourse(courseId);

        if (course.getStatus() != CourseStatus.PENDING) {
            throw new CourseStateException("Only PENDING courses can be approved. Current status: " + course.getStatus());
        }

        course.setStatus(CourseStatus.PUBLISHED);
        course.setPublishedAt(Instant.now());
        course.setRejectionReason(null);
        courseRepo.save(course);

        courseVersionRepo.findFirstByCourseIdAndStatus(courseId, VERSION_PENDING).ifPresent(v -> {
            v.setStatus(VERSION_APPROVED);
            v.setReviewedBy(adminId);
            v.setReviewedAt(Instant.now());
            courseVersionRepo.save(v);
        });

        saveLog(adminId, courseId, "APPROVED_FIRST", null);

        log.info("Course approved: courseId={}, adminId={}", courseId, adminId);

        return courseMapper.toDetailResponse(course);
    }

    @Override
    public CourseDetailResponse rejectCourse(UUID adminId, UUID courseId, String reason) {
        Course course = loadCourse(courseId);

        if (course.getStatus() != CourseStatus.PENDING) {
            throw new CourseStateException("Only PENDING courses can be rejected. Current status: " + course.getStatus());
        }

        course.setStatus(CourseStatus.REJECTED);
        course.setRejectionReason(reason);
        courseRepo.save(course);

        courseVersionRepo.findFirstByCourseIdAndStatus(courseId, VERSION_PENDING).ifPresent(v -> {
            v.setStatus(VERSION_REJECTED);
            v.setRejectionReason(reason);
            v.setReviewedBy(adminId);
            v.setReviewedAt(Instant.now());
            courseVersionRepo.save(v);
        });

        saveLog(adminId, courseId, VERSION_REJECTED, reason);
        log.info("Course rejected: courseId={}, adminId={}", courseId, adminId);

        return courseMapper.toDetailResponse(course);
    }

    @Override
    public CourseDetailResponse approveUpdate(UUID adminId, UUID courseId) {
        Course course = loadCourse(courseId);

        if (course.getStatus() != CourseStatus.PENDING_UPDATE) {
            throw new CourseStateException("Only PENDING_UPDATE courses can have updates approved. Current status: " + course.getStatus());
        }

        // Force-load toàn bộ nội dung
        course.getChapters().forEach(ch ->
            ch.getLessons().forEach(l -> l.getResources().size())
        );

        // 1. Áp dụng draft metadata
        if (course.getDraftTitle() != null) course.setTitle(course.getDraftTitle());
        if (course.getDraftDescription() != null) course.setDescription(course.getDraftDescription());
        if (course.getDraftLevel() != null) course.setLevel(course.getDraftLevel());
        if (course.getDraftThumbnailUrl() != null) course.setThumbnailUrl(course.getDraftThumbnailUrl());

        // 2. Áp dụng draft chapters & lessons
        List<Chapter> chaptersToRemove = new ArrayList<>();
        for (Chapter ch : course.getChapters()) {
            if (Boolean.TRUE.equals(ch.getPendingDelete())) {
                chaptersToRemove.add(ch);
            } else if (Boolean.TRUE.equals(ch.getIsDraft())) {
                ch.setIsDraft(false);
                ch.getLessons().forEach(l -> l.setIsDraft(false));
            } else {
                List<Lesson> lessonsToRemove = new ArrayList<>();
                for (Lesson l : ch.getLessons()) {
                    if (Boolean.TRUE.equals(l.getPendingDelete())) {
                        lessonsToRemove.add(l);
                    } else {
                        if (Boolean.TRUE.equals(l.getIsDraft())) l.setIsDraft(false);
                        if (l.getDraftTitle() != null) {
                            l.setTitle(l.getDraftTitle());
                            l.setDraftTitle(null);
                        }
                        if (l.getDraftContentText() != null) {
                            l.setContentText(l.getDraftContentText());
                            l.setDraftContentText(null);
                        }
                    }
                }
                ch.getLessons().removeAll(lessonsToRemove);
            }
        }
        course.getChapters().removeAll(chaptersToRemove);

        // 3. Xử lý resources: xóa thật pendingDelete, reset flag isNewInUpdate
        List<LessonResource> toDelete = lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId);
        for (LessonResource r : toDelete) {
            r.setDeletedAt(Instant.now());
            r.setStatus("DELETED");
            r.setPendingDelete(false);
            lessonResourceRepo.save(r);
            String s3Key = r.getS3Key();
            if (s3Key != null && !s3Key.startsWith("ext://")) {
                try { s3Service.deleteObject(s3Key); } catch (Exception ex) {
                    log.warn("S3 delete failed for key {}: {}", s3Key, ex.getMessage());
                }
            }
        }
        lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId)
                .forEach(r -> { r.setIsNewInUpdate(false); lessonResourceRepo.save(r); });

        // 4. Xóa draft metadata
        course.setDraftTitle(null);
        course.setDraftDescription(null);
        course.setDraftLevel(null);
        course.setDraftThumbnailUrl(null);
        course.setChangeSummary(null);
        course.setDraftRejectionReason(null);

        // 5. Cập nhật status
        course.setStatus(CourseStatus.PUBLISHED);
        course.setPendingUpdateAt(null);
        courseRepo.save(course);

        // 6. Cập nhật CourseVersion PENDING → APPROVED
        courseVersionRepo.findFirstByCourseIdAndStatus(courseId, VERSION_PENDING).ifPresent(v -> {
            v.setStatus(VERSION_APPROVED);
            v.setReviewedBy(adminId);
            v.setReviewedAt(Instant.now());
            courseVersionRepo.save(v);
        });

        saveLog(adminId, courseId, "APPROVED_UPDATE", null);

        log.info("Course update approved: courseId={}, adminId={}", courseId, adminId);

        return courseMapper.toDetailResponse(course);
    }

    @Override
    public CourseDetailResponse rejectUpdate(UUID adminId, UUID courseId, String reason) {
        Course course = loadCourse(courseId);

        if (course.getStatus() != CourseStatus.PENDING_UPDATE) {
            throw new CourseStateException("Only PENDING_UPDATE courses can have updates rejected. Current status: " + course.getStatus());
        }

        // Khôi phục resources bị đánh dấu pending_delete (giữ draft để instructor xem lại)
        lessonResourceRepo.findAllByCourseIdAndPendingDeleteTrue(courseId)
                .forEach(r -> { r.setPendingDelete(false); r.setStatus("ACTIVE"); lessonResourceRepo.save(r); });
        // Reset flag isNewInUpdate — resources mới thêm vẫn giữ nguyên nhưng không còn "mới"
        lessonResourceRepo.findAllByCourseIdAndIsNewInUpdateTrue(courseId)
                .forEach(r -> { r.setIsNewInUpdate(false); lessonResourceRepo.save(r); });

        // Giữ lại toàn bộ draft — instructor có thể xem thay đổi bị từ chối và sửa lại
        course.setDraftRejectionReason(reason);
        course.setChangeSummary(null);
        course.setStatus(CourseStatus.PUBLISHED);
        course.setPendingUpdateAt(null);
        courseRepo.save(course);

        // Cập nhật CourseVersion PENDING → REJECTED
        courseVersionRepo.findFirstByCourseIdAndStatus(courseId, VERSION_PENDING).ifPresent(v -> {
            v.setStatus(VERSION_REJECTED);
            v.setRejectionReason(reason);
            v.setReviewedBy(adminId);
            v.setReviewedAt(Instant.now());
            courseVersionRepo.save(v);
        });

        saveLog(adminId, courseId, "REJECTED_UPDATE", reason);
        log.info("Course update rejected (drafts kept): courseId={}, adminId={}, reason={}", courseId, adminId, reason);

        return courseMapper.toDetailResponse(course);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDiffResponse getVersionDiff(UUID courseId) {
        // Tìm version PENDING mới nhất
        CourseVersion pending = courseVersionRepo
                .findFirstByCourseIdAndStatus(courseId, VERSION_PENDING)
                .orElseThrow(() -> new CourseStateException("Không có version PENDING để so sánh"));

        // Tìm version APPROVED gần nhất (trước đó)
        CourseVersion approved = courseVersionRepo
                .findFirstByCourseIdAndStatusOrderByVersionNumberDesc(courseId, VERSION_APPROVED)
                .orElse(null);

        CourseSnapshotDto newSnap = parseSnapshot(pending.getSnapshot());
        CourseSnapshotDto oldSnap = approved != null ? parseSnapshot(approved.getSnapshot()) : null;

        return CourseDiffResponse.builder()
                .pendingVersionId(pending.getId())
                .pendingVersionNumber(pending.getVersionNumber())
                .approvedVersionId(approved != null ? approved.getId() : null)
                .approvedVersionNumber(approved != null ? approved.getVersionNumber() : null)
                .metadata(diffMetadata(oldSnap, newSnap))
                .chapters(diffChapters(oldSnap, newSnap))
                .resources(diffResources(oldSnap, newSnap))
                .build();
    }

    private CourseSnapshotDto parseSnapshot(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, CourseSnapshotDto.class);
        } catch (Exception e) {
            log.warn("Failed to parse snapshot JSON: {}", e.getMessage());
            return null;
        }
    }

    private CourseDiffResponse.MetaDiff diffMetadata(CourseSnapshotDto old, CourseSnapshotDto neu) {
        String oldTitle       = old != null ? old.getTitle()       : null;
        String oldDesc        = old != null ? old.getDescription() : null;
        String oldLevel       = old != null ? old.getLevel()       : null;
        String oldThumb       = old != null ? old.getThumbnailUrl(): null;
        String newTitle       = neu != null ? neu.getTitle()       : null;
        String newDesc        = neu != null ? neu.getDescription() : null;
        String newLevel       = neu != null ? neu.getLevel()       : null;
        String newThumb       = neu != null ? neu.getThumbnailUrl(): null;

        return CourseDiffResponse.MetaDiff.builder()
                .title(field(oldTitle, newTitle))
                .description(field(oldDesc, newDesc))
                .level(field(oldLevel, newLevel))
                .thumbnailUrl(field(oldThumb, newThumb))
                .build();
    }

    private CourseDiffResponse.FieldDiff field(String oldVal, String newVal) {
        boolean changed = !Objects.equals(oldVal, newVal);
        return CourseDiffResponse.FieldDiff.builder()
                .oldValue(oldVal).newValue(newVal).changed(changed).build();
    }

    private List<CourseDiffResponse.ChapterDiff> diffChapters(CourseSnapshotDto old, CourseSnapshotDto neu) {
        List<CourseSnapshotDto.ChapterSnap> oldChs = old != null && old.getChapters() != null ? old.getChapters() : List.of();
        List<CourseSnapshotDto.ChapterSnap> newChs = neu != null && neu.getChapters() != null ? neu.getChapters() : List.of();

        Map<Integer, CourseSnapshotDto.ChapterSnap> oldMap = oldChs.stream()
                .collect(Collectors.toMap(CourseSnapshotDto.ChapterSnap::getOrderIndex, c -> c));
        Map<Integer, CourseSnapshotDto.ChapterSnap> newMap = newChs.stream()
                .collect(Collectors.toMap(CourseSnapshotDto.ChapterSnap::getOrderIndex, c -> c));

        List<CourseDiffResponse.ChapterDiff> result = new ArrayList<>();
        Set<Integer> allOrders = new TreeSet<>();
        allOrders.addAll(oldMap.keySet());
        allOrders.addAll(newMap.keySet());

        for (Integer order : allOrders) {
            CourseSnapshotDto.ChapterSnap o = oldMap.get(order);
            CourseSnapshotDto.ChapterSnap n = newMap.get(order);

            if (o == null) {
                result.add(CourseDiffResponse.ChapterDiff.builder()
                        .action("ADDED").title(n.getTitle()).orderIndex(order)
                        .lessons(diffLessons(null, n)).build());
            } else if (n == null) {
                result.add(CourseDiffResponse.ChapterDiff.builder()
                        .action("REMOVED").title(o.getTitle()).orderIndex(order)
                        .lessons(diffLessons(o, null)).build());
            } else {
                List<CourseDiffResponse.LessonDiff> lessonDiffs = diffLessons(o, n);
                boolean anyLessonChanged = lessonDiffs.stream()
                        .anyMatch(l -> !DIFF_UNCHANGED.equals(l.getAction()));
                boolean titleChanged = !Objects.equals(o.getTitle(), n.getTitle());
                String action = (anyLessonChanged || titleChanged) ? DIFF_MODIFIED : DIFF_UNCHANGED;
                result.add(CourseDiffResponse.ChapterDiff.builder()
                        .action(action).title(n.getTitle()).orderIndex(order)
                        .lessons(lessonDiffs).build());
            }
        }
        return result;
    }

    private List<CourseDiffResponse.LessonDiff> diffLessons(
            CourseSnapshotDto.ChapterSnap oldCh, CourseSnapshotDto.ChapterSnap newCh) {
        List<CourseSnapshotDto.LessonSnap> oldLs = oldCh != null && oldCh.getLessons() != null ? oldCh.getLessons() : List.of();
        List<CourseSnapshotDto.LessonSnap> newLs = newCh != null && newCh.getLessons() != null ? newCh.getLessons() : List.of();

        Map<Integer, CourseSnapshotDto.LessonSnap> oldMap = oldLs.stream()
                .collect(Collectors.toMap(CourseSnapshotDto.LessonSnap::getOrderIndex, l -> l));
        Map<Integer, CourseSnapshotDto.LessonSnap> newMap = newLs.stream()
                .collect(Collectors.toMap(CourseSnapshotDto.LessonSnap::getOrderIndex, l -> l));

        List<CourseDiffResponse.LessonDiff> result = new ArrayList<>();
        Set<Integer> allOrders = new TreeSet<>();
        allOrders.addAll(oldMap.keySet());
        allOrders.addAll(newMap.keySet());

        for (Integer order : allOrders) {
            CourseSnapshotDto.LessonSnap o = oldMap.get(order);
            CourseSnapshotDto.LessonSnap n = newMap.get(order);

            if (o == null) {
                result.add(CourseDiffResponse.LessonDiff.builder()
                        .action("ADDED").title(n.getTitle()).orderIndex(order)
                        .lessonType(n.getLessonType()).build());
            } else if (n == null) {
                result.add(CourseDiffResponse.LessonDiff.builder()
                        .action("REMOVED").title(o.getTitle()).orderIndex(order)
                        .lessonType(o.getLessonType()).build());
            } else {
                boolean changed = !Objects.equals(o.getTitle(), n.getTitle())
                        || !Objects.equals(o.getContentText(), n.getContentText());
                result.add(CourseDiffResponse.LessonDiff.builder()
                        .action(changed ? DIFF_MODIFIED : DIFF_UNCHANGED)
                        .title(o.getTitle()).newTitle(changed && !Objects.equals(o.getTitle(), n.getTitle()) ? n.getTitle() : null)
                        .orderIndex(order).lessonType(n.getLessonType()).build());
            }
        }
        return result;
    }

    private List<CourseDiffResponse.ResourceDiff> diffResources(CourseSnapshotDto old, CourseSnapshotDto neu) {
        List<CourseDiffResponse.ResourceDiff> result = new ArrayList<>();

        Map<String, ResEntry> oldResMap = new LinkedHashMap<>();
        Map<String, ResEntry> newResMap = new LinkedHashMap<>();

        flatMapResources(old, oldResMap);
        flatMapResources(neu, newResMap);

        // Tìm resources bị xóa (có trong old, không có trong new)
        for (Map.Entry<String, ResEntry> e : oldResMap.entrySet()) {
            if (!newResMap.containsKey(e.getKey())) {
                ResEntry re = e.getValue();
                result.add(CourseDiffResponse.ResourceDiff.builder()
                        .action("REMOVED")
                        .displayName(re.snap().getDisplayName())
                        .resourceType(re.snap().getResourceType())
                        .mimeType(re.snap().getMimeType())
                        .fileSizeBytes(re.snap().getFileSizeBytes())
                        .lessonTitle(re.lessonTitle())
                        .chapterTitle(re.chapterTitle())
                        .build());
            }
        }

        // Tìm resources được thêm (có trong new, không có trong old)
        for (Map.Entry<String, ResEntry> e : newResMap.entrySet()) {
            if (!oldResMap.containsKey(e.getKey())) {
                ResEntry re = e.getValue();
                result.add(CourseDiffResponse.ResourceDiff.builder()
                        .action("ADDED")
                        .displayName(re.snap().getDisplayName())
                        .resourceType(re.snap().getResourceType())
                        .mimeType(re.snap().getMimeType())
                        .fileSizeBytes(re.snap().getFileSizeBytes())
                        .lessonTitle(re.lessonTitle())
                        .chapterTitle(re.chapterTitle())
                        .build());
            }
        }
        return result;
    }

    private record ResEntry(String chapterTitle, String lessonTitle, CourseSnapshotDto.ResourceSnap snap) {}

    private void flatMapResources(CourseSnapshotDto snap, Map<String, ResEntry> out) {
        if (snap == null || snap.getChapters() == null) return;
        for (CourseSnapshotDto.ChapterSnap ch : snap.getChapters()) {
            if (ch.getLessons() == null) continue;
            for (CourseSnapshotDto.LessonSnap l : ch.getLessons()) {
                if (l.getResources() == null) continue;
                for (CourseSnapshotDto.ResourceSnap r : l.getResources()) {
                    String key = ch.getTitle() + "|" + l.getTitle() + "|" + r.getDisplayName();
                    out.put(key, new ResEntry(ch.getTitle(), l.getTitle(), r));
                }
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Course loadCourse(UUID courseId) {
        return courseRepo.findByIdWithCategory(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
    }

    private void saveLog(UUID adminId, UUID courseId, String action, String reason) {
        CourseApprovalLog logEntry = CourseApprovalLog.builder()
                .courseId(courseId)
                .adminId(adminId)
                .action(action)
                .reason(reason)
                .createdAt(Instant.now())
                .build();
        approvalLogRepo.save(logEntry);
    }
}
