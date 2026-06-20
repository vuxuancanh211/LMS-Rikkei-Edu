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
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.exception.CourseNotFoundException;
import project.lms_rikkei_edu.modules.course.exception.CourseStateException;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.CourseApprovalLogRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.repository.LessonResourceRepository;
import project.lms_rikkei_edu.modules.course.service.AdminCourseService;
import project.lms_rikkei_edu.modules.course.entity.CourseApprovalLog;

import java.time.Instant;
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

        saveLog(adminId, courseId, "APPROVED", null);

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

        course.setStatus(CourseStatus.PUBLISHED);
        course.setPendingUpdateAt(null);
        courseRepo.save(course);

        saveLog(adminId, courseId, "APPROVED", null);

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

        course.setStatus(CourseStatus.PUBLISHED);
        course.setPendingUpdateAt(null);
        course.setRejectionReason(reason);
        courseRepo.save(course);

        saveLog(adminId, courseId, "REJECTED", reason);
        log.info("Course update rejected: courseId={}, adminId={}", courseId, adminId);

        return courseMapper.toDetailResponse(course);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Course loadCourse(UUID courseId) {
        return courseRepo.findByIdWithCategory(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
    }

    private void saveLog(UUID adminId, UUID courseId, String action, String reason) {
        CourseApprovalLog log = CourseApprovalLog.builder()
                .courseId(courseId)
                .adminId(adminId)
                .action(action)
                .reason(reason)
                .createdAt(Instant.now())
                .build();
        approvalLogRepo.save(log);
    }
}
