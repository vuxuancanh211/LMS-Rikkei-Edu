package project.lms_rikkei_edu.modules.course.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentSubmissionEntity;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentGroupRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentRepository;
import project.lms_rikkei_edu.modules.assignment.repository.AssignmentSubmissionRepository;
import project.lms_rikkei_edu.modules.course.dto.request.UpdateProgressRequest;
import project.lms_rikkei_edu.modules.course.dto.response.ChapterResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResponse;
import project.lms_rikkei_edu.modules.course.dto.response.ResourceDownloadUrlResponse;
import project.lms_rikkei_edu.modules.course.dto.response.StudentCourseResponse;
import project.lms_rikkei_edu.modules.course.entity.Chapter;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.CourseProgressEntity;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.entity.LessonProgressEntity;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseProgressRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.repository.LessonProgressRepository;
import project.lms_rikkei_edu.modules.course.repository.LessonRepository;
import project.lms_rikkei_edu.modules.course.service.StudentCourseService;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import project.lms_rikkei_edu.modules.quiz.entity.QuizAttemptEntity;
import project.lms_rikkei_edu.modules.quiz.repository.ProctoringViolationLogRepository;
import project.lms_rikkei_edu.modules.quiz.repository.QuizAttemptAnswerRepository;
import project.lms_rikkei_edu.modules.quiz.repository.QuizAttemptRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentCourseServiceImpl implements StudentCourseService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseProgressRepository courseProgressRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final CourseMapper courseMapper;
    private final S3Service s3Service;
    private final UserRepository userRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final ProctoringViolationLogRepository proctoringViolationLogRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final AssignmentGroupRepository assignmentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Value("${app.s3.presigned-url-expiry:3600}")
    private long presignedUrlExpiry;

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Khóa advisory theo cặp (studentId, lessonId) trong phạm vi transaction hiện tại — tránh race
     * condition khi 2 request cập nhật tiến độ cùng lúc (VD: player gửi progress dồn dập, React
     * StrictMode gọi effect 2 lần) cùng đọc "chưa có lesson_progress" và cùng cố INSERT, gây vỡ
     * unique constraint uq_lesson_progress_student_lesson. Lock tự giải phóng khi transaction kết thúc.
     */
    private void lockLessonProgress(UUID studentId, UUID lessonId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(?1))")
                .setParameter(1, studentId.toString() + ":" + lessonId.toString())
                .getSingleResult();
    }

    @Override
    public List<StudentCourseResponse> getEnrolledCourses(UUID studentId) {
        List<Course> courses = courseRepository.findEnrolledCoursesByStudentId(studentId)
                .stream()
                .filter(this::isLive)
                .toList();

        if (courses.isEmpty()) return Collections.emptyList();

        // Batch fetch instructor names
        Map<UUID, String> instructorNames = userRepository.findAllById(
                courses.stream().map(Course::getInstructorId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName));

        // Batch fetch progress
        Map<UUID, CourseProgressEntity> progressMap = courseProgressRepository
                .findByStudentIdAndCourseIdIn(studentId,
                        courses.stream().map(Course::getId).toList())
                .stream().collect(Collectors.toMap(CourseProgressEntity::getCourseId, p -> p));

        return courses.stream().map(c -> toEnrolledCourseResponse(c, instructorNames, progressMap)).toList();
    }

    private StudentCourseResponse toEnrolledCourseResponse(Course c, Map<UUID, String> instructorNames,
                                                            Map<UUID, CourseProgressEntity> progressMap) {
        String instructorName = instructorNames.getOrDefault(c.getInstructorId(), "");
        CourseProgressEntity prog = progressMap.get(c.getId());
        int chaptersCount = c.getChapters() != null ? c.getChapters().size() : 0;
        int[] stats = computeLessonStats(c);
        int totalHours = (int) Math.ceil(stats[1] / 3600.0);

        String sStatus;
        int progress;
        if (prog == null) {
            sStatus = "new";
            progress = 0;
        } else {
            progress = prog.getOverallPercentage() != null ? prog.getOverallPercentage().intValue() : 0;
            sStatus = STATUS_COMPLETED.equals(prog.getStatus()) ? "done" : "learning";
        }

        return StudentCourseResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .thumbnailUrl(c.getThumbnailUrl())
                .category(c.getCategory() != null ? c.getCategory().getName() : "")
                .instructor(instructorName)
                .lessons(stats[0])
                .hours(totalHours)
                .level(c.getLevel() != null ? c.getLevel().name() : "")
                .rating(0)
                .progress(progress)
                .sStatus(sStatus)
                .pubStatus(c.getStatus() != null ? c.getStatus().name().toLowerCase() : "draft")
                .chapters(chaptersCount)
                .build();
    }

    private int[] computeLessonStats(Course c) {
        int lessonsCount = 0;
        int totalSeconds = 0;
        if (c.getChapters() != null) {
            for (Chapter ch : c.getChapters()) {
                if (ch.getLessons() != null) {
                    lessonsCount += ch.getLessons().size();
                    for (Lesson l : ch.getLessons()) {
                        if (l.getDurationSeconds() != null) {
                            totalSeconds += l.getDurationSeconds();
                        }
                    }
                }
            }
        }
        return new int[]{lessonsCount, totalSeconds};
    }

    @Override
    public CourseDetailResponse getCourseDetail(UUID studentId, UUID courseId) {
        Course course = courseRepository.findByIdWithCategory(courseId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy khóa học"));

        if (course.getDeletedAt() != null) {
            throw new BusinessException("Khóa học đã bị xóa");
        }
        if (!isLive(course)) {
            throw new BusinessException("Khóa học chưa được xuất bản");
        }

        boolean enrolled = courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId);
        if (!enrolled) {
            throw new BusinessException("Bạn chưa đăng ký khóa học này");
        }

        CourseDetailResponse response = courseMapper.toDetailResponse(course);

        // Học viên chỉ được thấy nội dung ĐANG LIVE — courseMapper.toDetailResponse() dùng
        // chung cho cả giảng viên (cần thấy toàn bộ, kể cả draft chưa duyệt, để hiển thị badge
        // "Mới"/"Chờ xóa") lẫn học viên, nên trước đây chương/bài mới thêm (isDraft=true, chưa
        // duyệt) và tài liệu vừa upload trong lần cập nhật đang chờ (isNewInUpdate=true, chưa
        // duyệt) bị lộ thẳng cho học viên dù nội dung publish chưa hề có. pendingDelete KHÔNG bị
        // lọc ở đây vì nội dung đó vẫn đang live cho tới khi admin duyệt xóa thật.
        filterToLiveContentOnly(response);

        attachLessonProgress(studentId, courseId, response);
        attachCourseStats(response, courseId, course.getInstructorId());

        courseProgressRepository.findByStudentIdAndCourseId(studentId, courseId)
                .ifPresent(prog -> {
                    response.setCompletedAssignments(prog.getCompletedAssignmentsCount());
                    response.setTotalAssignments(prog.getTotalAssignmentsCount());
                });

        return response;
    }

    private void attachCourseStats(CourseDetailResponse response, UUID courseId, UUID instructorId) {
        response.setStudentCount((int) courseEnrollmentRepository.countByCourseId(courseId));
        userRepository.findById(instructorId).ifPresent(u -> {
            response.setInstructorName(u.getFullName());
            response.setInstructorBio(u.getBio());
        });
        response.setInstructorCourseCount((int) courseRepository.countByInstructorIdAndStatus(instructorId, CourseStatus.PUBLISHED));
    }

    @Override
    public ResourceDownloadUrlResponse getResourceViewUrl(UUID studentId, UUID courseId, UUID lessonId, UUID resourceId) {
        checkEnrollment(studentId, courseId);

        LessonResource resource = findResource(lessonId, resourceId);

        String s3Key = resource.getS3Key();
        if (s3Key != null && s3Key.startsWith("ext://")) {
            return ResourceDownloadUrlResponse.builder()
                    .url(s3Key.substring(6))
                    .expiresAt(Instant.now().plusSeconds(presignedUrlExpiry))
                    .build();
        }

        PresignedGetObjectRequest presigned = s3Service.generatePresignedInlineUrl(resource.getS3Key(), presignedUrlExpiry);

        return ResourceDownloadUrlResponse.builder()
                .url(presigned.url().toString())
                .expiresAt(Instant.now().plusSeconds(presignedUrlExpiry))
                .build();
    }

    private void attachLessonProgress(UUID studentId, UUID courseId, CourseDetailResponse response) {
        if (response.getChapters() == null) return;

        List<LessonProgressEntity> lessonProgressList = lessonProgressRepository
                .findByStudentIdAndCourseId(studentId, courseId);
        Map<UUID, String> progressMap = lessonProgressList.stream()
                .collect(Collectors.toMap(LessonProgressEntity::getLessonId, LessonProgressEntity::getStatus));
        Map<UUID, BigDecimal> lessonPctMap = lessonProgressList.stream()
                .filter(e -> e.getLessonPercentage() != null)
                .collect(Collectors.toMap(LessonProgressEntity::getLessonId, LessonProgressEntity::getLessonPercentage));

        for (var ch : response.getChapters()) {
            if (ch.getLessons() == null) continue;
            for (var lesson : ch.getLessons()) {
                applyProgressToLesson(lesson, progressMap, lessonPctMap);
            }
        }
    }

    private void applyProgressToLesson(LessonResponse lesson, Map<UUID, String> progressMap, Map<UUID, BigDecimal> lessonPctMap) {
        String p = progressMap.get(lesson.getId());
        if (p != null) lesson.setProgress(p);
        BigDecimal lp = lessonPctMap.get(lesson.getId());
        if (lp != null) lesson.setProgressPercentage(lp.intValue());
    }

    @Override
    public ResourceDownloadUrlResponse getResourceDownloadUrl(UUID studentId, UUID courseId, UUID lessonId, UUID resourceId) {
        checkEnrollment(studentId, courseId);

        LessonResource resource = findResource(lessonId, resourceId);

        PresignedGetObjectRequest presigned = s3Service.generatePresignedGetUrl(resource.getS3Key(), presignedUrlExpiry);

        return ResourceDownloadUrlResponse.builder()
                .url(presigned.url().toString())
                .expiresAt(Instant.now().plusSeconds(presignedUrlExpiry))
                .build();
    }

    @Override
    @Transactional
    public void updateLessonProgress(UUID studentId, UUID courseId, UUID lessonId, UpdateProgressRequest request) {
        checkEnrollment(studentId, courseId);
        lockLessonProgress(studentId, lessonId);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy bài học"));

        if (lesson.getType() == LessonType.QUIZ) {
            throw new BusinessException("Bài học loại đề trắc nghiệm không cập nhật tiến độ qua API này");
        }

        LessonProgressEntity progress = lessonProgressRepository
                .findByStudentIdAndLessonId(studentId, lessonId)
                .orElseGet(() -> {
                    LessonProgressEntity newProgress = new LessonProgressEntity();
                    newProgress.setId(UUID.randomUUID());
                    newProgress.setStudentId(studentId);
                    newProgress.setLessonId(lessonId);
                    newProgress.setCourseId(courseId);
                    newProgress.setFirstAccessedAt(Instant.now());
                    return newProgress;
                });

        String oldStatus = progress.getStatus();
        BigDecimal oldPct = progress.getLessonPercentage();

        // Update incoming data
        applyWatchedPercentage(progress, request);
        applyLastPlaybackPosition(progress, request);
        applyDocumentViewSeconds(progress, request);
        progress.setLastAccessedAt(Instant.now());

        // Determine lesson content type
        boolean hasVideo = hasVideoContent(lesson);
        boolean hasDocument = hasDocumentContent(lesson);

        int wp = progress.getWatchedPercentage() != null ? progress.getWatchedPercentage().intValue() : 0;
        int dv = progress.getDocumentViewSeconds() != null ? progress.getDocumentViewSeconds() : 0;

        boolean completed = determineCompleted(request, progress, hasVideo, hasDocument, wp);

        applyProgressStatus(progress, completed);

        // Calculate lesson percentage
        if (completed && !hasVideo) {
            progress.setLessonPercentage(BigDecimal.valueOf(100));
        } else {
            progress.setLessonPercentage(calculateLessonPercentage(wp, dv, hasVideo, hasDocument));
        }

        lessonProgressRepository.save(progress);

        // Optimize DB load: only update course-level progress if status or lesson percentage actually changed
        if (!java.util.Objects.equals(oldStatus, progress.getStatus()) || !java.util.Objects.equals(oldPct, progress.getLessonPercentage())) {
            updateCourseProgress(studentId, courseId);
        }
    }

    private boolean determineCompleted(UpdateProgressRequest request, LessonProgressEntity progress,
                                        boolean hasVideo, boolean hasDocument, int wp) {
        if (STATUS_COMPLETED.equals(progress.getStatus())) {
            return true;
        }
        if (Boolean.TRUE.equals(request.getCompleted())) {
            return true;
        }
        if (request.getCompleted() != null) {
            return request.getCompleted();
        }
        if (!hasVideo && !hasDocument) {
            return true;
        }
        if (hasVideo) {
            return wp >= 90;
        }
        return false;
    }

    private void applyProgressStatus(LessonProgressEntity progress, boolean completed) {
        if (completed || STATUS_COMPLETED.equals(progress.getStatus())) {
            progress.setStatus(STATUS_COMPLETED);
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(Instant.now());
            }
        } else if (progress.getStatus() == null || !STATUS_COMPLETED.equals(progress.getStatus())) {
            progress.setStatus(STATUS_IN_PROGRESS);
        }
    }

    /**
     * REQUIRES_NEW — hàm này được gọi fail-soft từ {@code QuizAttemptServiceImpl.doSubmit()} (trong
     * transaction nộp bài); nếu để propagation mặc định (REQUIRED) thì 1 exception ở đây sẽ đánh dấu
     * rollback-only cho CẢ transaction nộp bài dù caller có try/catch, khiến việc nộp bài thất bại âm
     * thầm (UnexpectedRollbackException). Tách transaction riêng để lỗi ở đây không ảnh hưởng nộp bài.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeQuizLesson(UUID studentId, UUID courseId, UUID lessonId) {
        lockLessonProgress(studentId, lessonId);

        LessonProgressEntity progress = lessonProgressRepository
                .findByStudentIdAndLessonId(studentId, lessonId)
                .orElseGet(() -> {
                    LessonProgressEntity newProgress = new LessonProgressEntity();
                    newProgress.setId(UUID.randomUUID());
                    newProgress.setStudentId(studentId);
                    newProgress.setLessonId(lessonId);
                    newProgress.setCourseId(courseId);
                    newProgress.setFirstAccessedAt(Instant.now());
                    return newProgress;
                });

        progress.setStatus(STATUS_COMPLETED);
        if (progress.getCompletedAt() == null) {
            progress.setCompletedAt(Instant.now());
        }
        progress.setLastAccessedAt(Instant.now());
        progress.setLessonPercentage(BigDecimal.valueOf(100));
        lessonProgressRepository.save(progress);

        updateCourseProgress(studentId, courseId);
    }

    @Override
    @Transactional
    public void resetLessonProgressForInProgressStudents(UUID courseId, UUID lessonId) {
        List<UUID> inProgressStudentIds = courseProgressRepository.findByCourseId(courseId).stream()
                .filter(cp -> !STATUS_COMPLETED.equals(cp.getStatus()))
                .map(CourseProgressEntity::getStudentId)
                .toList();

        for (UUID studentId : inProgressStudentIds) {
            lessonProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)
                    .ifPresent(lessonProgressRepository::delete);
            updateCourseProgress(studentId, courseId);
        }
    }

    @Override
    @Transactional
    public void recalculateCourseProgress(UUID studentId, UUID courseId) {
        updateCourseProgress(studentId, courseId);
    }

    @Override
    @Transactional
    public void updateAssignmentProgress(UUID studentId, UUID courseId) {
        updateCourseProgress(studentId, courseId);
    }

    @Override
    @Transactional
    public void resetProgressForStudents(UUID courseId, List<UUID> studentIds) {
        deleteProgressData(courseId, studentIds);
    }

    @Override
    @Transactional
    public void resetStudentCourseProgress(UUID courseId, UUID studentId) {
        if (courseId == null || studentId == null) return;
        deleteProgressData(courseId, List.of(studentId));
    }

    private void deleteProgressData(UUID courseId, List<UUID> studentIds) {
        if (courseId == null || studentIds == null || studentIds.isEmpty()) return;
        try {
            List<QuizAttemptEntity> attempts = quizAttemptRepository.findByCourseIdAndStudentIdIn(courseId, studentIds);
            if (!attempts.isEmpty()) {
                List<UUID> attemptIds = attempts.stream().map(QuizAttemptEntity::getId).toList();
                quizAttemptAnswerRepository.deleteByAttemptIdIn(attemptIds);
                proctoringViolationLogRepository.deleteByAttemptIdIn(attemptIds);
                quizAttemptRepository.deleteByCourseIdAndStudentIdIn(courseId, studentIds);
            }
            lessonProgressRepository.deleteByCourseIdAndStudentIdIn(courseId, studentIds);
            courseProgressRepository.deleteByCourseIdAndStudentIdIn(courseId, studentIds);
            log.info("Reset progress successfully for {} students in course {}", studentIds.size(), courseId);
        } catch (Exception e) {
            log.error("Failed to reset progress for students in course {}: {}", courseId, e.getMessage(), e);
        }
    }

    private void applyWatchedPercentage(LessonProgressEntity progress, UpdateProgressRequest request) {
        if (request.getWatchedPercentage() == null) return;
        int existing = progress.getWatchedPercentage() != null ? progress.getWatchedPercentage().intValue() : 0;
        if (request.getWatchedPercentage().intValue() > existing) {
            progress.setWatchedPercentage(request.getWatchedPercentage());
        }
    }

    private void applyLastPlaybackPosition(LessonProgressEntity progress, UpdateProgressRequest request) {
        if (request.getLastPlaybackPosition() == null) return;
        int existing = progress.getLastPlaybackPosition() != null ? progress.getLastPlaybackPosition() : 0;
        if (request.getLastPlaybackPosition() > existing) {
            progress.setLastPlaybackPosition(request.getLastPlaybackPosition());
        }
    }

    private void applyDocumentViewSeconds(LessonProgressEntity progress, UpdateProgressRequest request) {
        if (request.getDocumentViewSeconds() == null) return;
        int existing = progress.getDocumentViewSeconds() != null ? progress.getDocumentViewSeconds() : 0;
        if (request.getDocumentViewSeconds() > existing) {
            progress.setDocumentViewSeconds(request.getDocumentViewSeconds());
        }
    }

    /* ── Private helpers ─────────────────────────────────── */

    private BigDecimal calculateLessonPercentage(int wp, int dv, boolean hasVideo, boolean hasDocument) {
        if (hasVideo && hasDocument) {
            if (wp >= 90 && dv >= 10) return BigDecimal.valueOf(100);
            double vidScore = Math.clamp(wp / 90.0, 0, 1) * 80.0;
            double docScore = Math.clamp(dv / 10.0, 0, 1) * 20.0;
            return BigDecimal.valueOf(Math.round(vidScore + docScore));
        } else if (hasVideo) {
            return BigDecimal.valueOf(Math.clamp(wp, 0, 100));
        } else if (hasDocument) {
            if (dv >= 20 || wp >= 90) return BigDecimal.valueOf(100);
            double pctByTime = Math.clamp(dv * 100.0 / 20.0, 0, 100);
            double finalPct = Math.max(pctByTime, Math.clamp(wp, 0, 100));
            return BigDecimal.valueOf(Math.round(finalPct));
        } else {
            return BigDecimal.valueOf(100);
        }
    }

    /**
     * Học viên phải thấy được khóa học ở cả 2 trạng thái "đang live": PUBLISHED bình thường, và
     * PENDING_UPDATE (đã publish, đang có 1 bản cập nhật chờ duyệt) — trong lúc chờ duyệt, học
     * viên vẫn xem được bản đã publish trước đó, không bị mất quyền truy cập khóa học.
     */
    private boolean isLive(Course course) {
        return course.getStatus() == CourseStatus.PUBLISHED
                || course.getStatus() == CourseStatus.PENDING_UPDATE;
    }

    private void filterToLiveContentOnly(CourseDetailResponse response) {
        // Che các trường draft ở cấp khóa học — học viên không cần biết đang có bản cập nhật
        // chờ duyệt hay nội dung cụ thể của bản đó (chỉ giảng viên/admin cần thấy để duyệt).
        response.setHasPendingDraft(false);
        response.setDraftTitle(null);
        response.setDraftDescription(null);
        response.setDraftThumbnailUrl(null);
        response.setDraftLevel(null);
        response.setChangeSummary(null);
        response.setDraftRejectionReason(null);

        if (response.getChapters() == null) return;
        // Danh sách từ mapper có thể bất biến (List.of / Collections.unmodifiableList) — copy
        // sang ArrayList trước khi lọc, rồi gán ngược lại qua setter.
        List<ChapterResponse> chapters = new ArrayList<>(response.getChapters());
        chapters.removeIf(ch -> Boolean.TRUE.equals(ch.getIsDraft()));
        for (ChapterResponse ch : chapters) {
            if (ch.getLessons() == null) continue;
            List<LessonResponse> lessons = new ArrayList<>(ch.getLessons());
            lessons.removeIf(l -> Boolean.TRUE.equals(l.getIsDraft()));
            for (LessonResponse lesson : lessons) {
                lesson.setDraftTitle(null);
                lesson.setDraftContentText(null);
                if (lesson.getResources() != null) {
                    List<LessonResourceResponse> resources = new ArrayList<>(lesson.getResources());
                    resources.removeIf(r -> Boolean.TRUE.equals(r.getIsNewInUpdate()));
                    lesson.setResources(resources);
                }
            }
            ch.setLessons(lessons);
        }
        response.setChapters(chapters);
    }

    private boolean hasVideoContent(Lesson lesson) {
        if (lesson.getType() == LessonType.VIDEO) return true;
        if (lesson.getHlsManifestUrl() != null || lesson.getVideoS3Key() != null) return true;
        if (lesson.getResources() != null) {
            return lesson.getResources().stream()
                    .anyMatch(r -> r.getResourceType() == ResourceType.VIDEO && !Boolean.TRUE.equals(r.getPendingDelete()));
        }
        return false;
    }

    private boolean hasDocumentContent(Lesson lesson) {
        if (lesson.getType() != null && lesson.getType() != LessonType.VIDEO && lesson.getType() != LessonType.QUIZ) return true;
        if (lesson.getResources() != null) {
            return lesson.getResources().stream()
                    .anyMatch(r -> r.getResourceType() != ResourceType.VIDEO && !Boolean.TRUE.equals(r.getPendingDelete()));
        }
        return false;
    }

    private void updateCourseProgress(UUID studentId, UUID courseId) {
        int totalLessons = getCourseLessonCount(courseId);
        List<LessonProgressEntity> allProgress = lessonProgressRepository
                .findByStudentIdAndCourseId(studentId, courseId);

        long completedLessons = allProgress.stream()
                .filter(p -> STATUS_COMPLETED.equals(p.getStatus()))
                .count();

        // ── Assignment progress ──────────────────────────────────────────────
        int[] assignmentStats = getAssignmentProgressStats(studentId, courseId);
        int totalAssignments = assignmentStats[0];
        int completedAssignments = assignmentStats[1];

        int totalItems = totalLessons + totalAssignments;
        int completedItems = (int) completedLessons + completedAssignments;

        double avgPercentage = 0;
        if (totalItems > 0) {
            avgPercentage = (completedItems * 100.0) / totalItems;
        }

        CourseProgressEntity courseProgress = courseProgressRepository
                .findByStudentIdAndCourseId(studentId, courseId)
                .orElseGet(() -> {
                    CourseProgressEntity newProg = new CourseProgressEntity();
                    newProg.setId(UUID.randomUUID());
                    newProg.setStudentId(studentId);
                    newProg.setCourseId(courseId);
                    newProg.setTotalLessonsCount(totalLessons);
                    newProg.setCompletedLessonsCount(0);
                    newProg.setTotalAssignmentsCount(totalAssignments);
                    newProg.setCompletedAssignmentsCount(0);
                    newProg.setOverallPercentage(BigDecimal.ZERO);
                    newProg.setStatus(STATUS_IN_PROGRESS);
                    return newProg;
                });

        courseProgress.setCompletedLessonsCount((int) completedLessons);
        courseProgress.setTotalLessonsCount(totalLessons);
        courseProgress.setCompletedAssignmentsCount(completedAssignments);
        courseProgress.setTotalAssignmentsCount(totalAssignments);
        courseProgress.setOverallPercentage(BigDecimal.valueOf(Math.round(avgPercentage)));
        courseProgress.setUpdatedAt(Instant.now());

        if (completedItems >= totalItems && totalItems > 0) {
            courseProgress.setStatus(STATUS_COMPLETED);
        } else {
            courseProgress.setStatus(STATUS_IN_PROGRESS);
        }

        courseProgressRepository.save(courseProgress);
    }

    /**
     * Tính toán số assignment đã hoàn thành và tổng số assignment cho 1 học viên.
     * @return int[] – [totalAssignments, completedAssignments]
     */
    private int[] getAssignmentProgressStats(UUID studentId, UUID courseId) {
        List<AssignmentEntity> assignments = assignmentRepository.findPublishedByCourseId(courseId);
        if (assignments.isEmpty()) return new int[]{0, 0};

        List<UUID> studentGroupIds = groupMemberRepository.findGroupIdsByStudentIdAndCourseId(studentId, courseId);

        List<UUID> applicableAssignmentIds = filterApplicableAssignments(assignments, studentGroupIds);

        if (applicableAssignmentIds.isEmpty()) return new int[]{0, 0};

        List<AssignmentSubmissionEntity> submissions = assignmentSubmissionRepository
                .findByStudentIdAndAssignmentIdIn(studentId, applicableAssignmentIds);

        Map<UUID, BigDecimal> passScoreMap = assignments.stream()
                .collect(Collectors.toMap(
                        AssignmentEntity::getId,
                        AssignmentEntity::getPassingScore,
                        (a, b) -> a,
                        HashMap::new));

        int completed = 0;
        for (AssignmentSubmissionEntity s : submissions) {
            if (s.getScorePublishedAt() == null) continue;
            BigDecimal passScore = passScoreMap.get(s.getAssignmentId());
            if (passScore == null || (s.getScore() != null && s.getScore().compareTo(passScore) >= 0)) {
                completed++;
            }
        }

        return new int[]{applicableAssignmentIds.size(), completed};
    }

    private List<UUID> filterApplicableAssignments(List<AssignmentEntity> assignments, List<UUID> studentGroupIds) {
        List<UUID> applicableAssignmentIds = new ArrayList<>();
        for (AssignmentEntity a : assignments) {
            if (a.getScope() == AssignmentScope.ALL_GROUPS) {
                applicableAssignmentIds.add(a.getId());
            } else {
                List<UUID> assignedGroupIds = assignmentGroupRepository.findByAssignmentId(a.getId())
                        .stream()
                        .map(ag -> ag.getGroupId())
                        .toList();
                if (assignedGroupIds.stream().anyMatch(studentGroupIds::contains)) {
                    applicableAssignmentIds.add(a.getId());
                }
            }
        }
        return applicableAssignmentIds;
    }

    private int getCourseLessonCount(UUID courseId) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null || course.getChapters() == null) return 0;
        return course.getChapters().stream()
                .filter(ch -> !Boolean.TRUE.equals(ch.getPendingDelete()))
                .mapToInt(ch -> ch.getLessons() != null
                        ? (int) ch.getLessons().stream().filter(l -> !Boolean.TRUE.equals(l.getPendingDelete())).count()
                        : 0)
                .sum();
    }

    private void checkEnrollment(UUID studentId, UUID courseId) {
        boolean enrolled = courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId);
        if (!enrolled) {
            throw new BusinessException("Bạn chưa đăng ký khóa học này");
        }
    }

    private LessonResource findResource(UUID lessonId, UUID resourceId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy bài học"));

        LessonResource resource = lesson.getResources().stream()
                .filter(r -> r.getId().equals(resourceId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài liệu"));

        // Chặn truy cập trực tiếp bằng resourceId vào nội dung chưa từng publish — không chỉ lọc
        // ở danh sách hiển thị (getCourseDetail), vì học viên vẫn có thể gọi thẳng endpoint này
        // nếu biết resourceId (VD: cache cũ ở client, DevTools network tab).
        if (Boolean.TRUE.equals(resource.getIsNewInUpdate())
                || Boolean.TRUE.equals(lesson.getIsDraft())
                || (lesson.getChapter() != null && Boolean.TRUE.equals(lesson.getChapter().getIsDraft()))) {
            throw new BusinessException("Không tìm thấy tài liệu");
        }

        return resource;
    }
}
