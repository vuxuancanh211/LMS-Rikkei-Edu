package project.lms_rikkei_edu.modules.course.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.ai.service.ingestion.CourseEmbeddingService;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseResponse;
import project.lms_rikkei_edu.modules.course.dto.response.ResourceDownloadUrlResponse;
import project.lms_rikkei_edu.modules.course.entity.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.exception.CourseNotFoundException;
import project.lms_rikkei_edu.modules.course.exception.CourseStateException;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.*;
import project.lms_rikkei_edu.modules.course.service.AdminCourseService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminCourseServiceImpl implements AdminCourseService {

    private final CourseRepository courseRepo;
    private final CourseMapper courseMapper;
    private final CourseEmbeddingService embeddingService;
    private final CourseApprovalLogRepository approvalLogRepo;
    private final LessonResourceRepository lessonResourceRepo;
    private final S3Service s3Service;

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

        saveLog(adminId, courseId, "APPROVED_FIRST", null);

        embeddingService.embedCourseAsync(courseId);
        log.info("Course approved and embedding started: courseId={}, adminId={}", courseId, adminId);

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

        saveLog(adminId, courseId, "REJECTED", reason);
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

        // 3. Xóa draft metadata
        course.setDraftTitle(null);
        course.setDraftDescription(null);
        course.setDraftLevel(null);
        course.setDraftThumbnailUrl(null);
        course.setChangeSummary(null);
        course.setDraftRejectionReason(null);

        // 4. Cập nhật status
        course.setStatus(CourseStatus.PUBLISHED);
        course.setPendingUpdateAt(null);
        courseRepo.save(course);

        saveLog(adminId, courseId, "APPROVED_UPDATE", null);

        embeddingService.embedCourseAsync(courseId);
        log.info("Course update approved and re-embedding started: courseId={}, adminId={}", courseId, adminId);

        return courseMapper.toDetailResponse(course);
    }

    @Override
    public CourseDetailResponse rejectUpdate(UUID adminId, UUID courseId, String reason) {
        Course course = loadCourse(courseId);

        if (course.getStatus() != CourseStatus.PENDING_UPDATE) {
            throw new CourseStateException("Only PENDING_UPDATE courses can have updates rejected. Current status: " + course.getStatus());
        }

        // Giữ lại toàn bộ draft — instructor có thể xem thay đổi bị từ chối và sửa lại
        course.setDraftRejectionReason(reason);
        course.setChangeSummary(null);
        course.setStatus(CourseStatus.PUBLISHED);
        course.setPendingUpdateAt(null);
        courseRepo.save(course);

        saveLog(adminId, courseId, "REJECTED_UPDATE", reason);
        log.info("Course update rejected (drafts kept): courseId={}, adminId={}, reason={}", courseId, adminId, reason);

        return courseMapper.toDetailResponse(course);
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
