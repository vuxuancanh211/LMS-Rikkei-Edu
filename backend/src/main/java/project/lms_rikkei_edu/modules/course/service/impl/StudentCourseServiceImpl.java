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
import project.lms_rikkei_edu.modules.course.dto.request.UpdateProgressRequest;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
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
import project.lms_rikkei_edu.modules.quiz.entity.QuizAttemptEntity;
import project.lms_rikkei_edu.modules.quiz.repository.ProctoringViolationLogRepository;
import project.lms_rikkei_edu.modules.quiz.repository.QuizAttemptAnswerRepository;
import project.lms_rikkei_edu.modules.quiz.repository.QuizAttemptRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
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

    @Value("${app.s3.presigned-url-expiry:3600}")
    private long presignedUrlExpiry;

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
                .filter(c -> c.getStatus() == CourseStatus.PUBLISHED)
                .toList();

        if (courses.isEmpty()) return Collections.emptyList();

        // Batch fetch instructor names
        Map<UUID, String> instructorNames = userRepository.findAllById(
                courses.stream().map(Course::getInstructorId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName));

        // Batch fetch progress
        Map<UUID, CourseProgressEntity> progressMap = courseProgressRepository
                .findByStudentIdAndCourseIdIn(studentId,
                        courses.stream().map(Course::getId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(CourseProgressEntity::getCourseId, p -> p));

        return courses.stream().map(c -> {
            String categoryName = c.getCategory() != null ? c.getCategory().getName() : "";
            String instructorName = instructorNames.getOrDefault(c.getInstructorId(), "");
            CourseProgressEntity prog = progressMap.get(c.getId());

            int chaptersCount = c.getChapters() != null ? c.getChapters().size() : 0;
            int lessonsCount = 0;
            int totalHours = 0;
            if (c.getChapters() != null) {
                for (Chapter ch : c.getChapters()) {
                    if (ch.getLessons() != null) {
                        lessonsCount += ch.getLessons().size();
                        for (Lesson l : ch.getLessons()) {
                            if (l.getDurationSeconds() != null) {
                                totalHours += l.getDurationSeconds();
                            }
                        }
                    }
                }
            }
            totalHours = (int) Math.ceil(totalHours / 3600.0);

            int progress = 0;
            String sStatus;
            if (prog == null) {
                sStatus = "new";
            } else {
                progress = prog.getOverallPercentage() != null
                        ? prog.getOverallPercentage().intValue() : 0;
                sStatus = "COMPLETED".equals(prog.getStatus()) ? "done" : "learning";
            }

            String pubStatus = c.getStatus() != null ? c.getStatus().name().toLowerCase() : "draft";

            return StudentCourseResponse.builder()
                    .id(c.getId())
                    .title(c.getTitle())
                    .thumbnailUrl(c.getThumbnailUrl())
                    .category(categoryName)
                    .instructor(instructorName)
                    .lessons(lessonsCount)
                    .hours(totalHours)
                    .level(c.getLevel() != null ? c.getLevel().name() : "")
                    .rating(0)
                    .progress(progress)
                    .sStatus(sStatus)
                    .pubStatus(pubStatus)
                    .chapters(chaptersCount)
                    .build();
        }).toList();
    }

    @Override
    public CourseDetailResponse getCourseDetail(UUID studentId, UUID courseId) {
        Course course = courseRepository.findByIdWithCategory(courseId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy khóa học"));

        if (course.getDeletedAt() != null) {
            throw new BusinessException("Khóa học đã bị xóa");
        }
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessException("Khóa học chưa được xuất bản");
        }

        boolean enrolled = courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId);
        if (!enrolled) {
            throw new BusinessException("Bạn chưa đăng ký khóa học này");
        }

        CourseDetailResponse response = courseMapper.toDetailResponse(course);

        // Attach lesson progress + lessonPercentage
        List<LessonProgressEntity> lessonProgressList = lessonProgressRepository
                .findByStudentIdAndCourseId(studentId, courseId);
        Map<UUID, String> progressMap = lessonProgressList.stream()
                .collect(Collectors.toMap(LessonProgressEntity::getLessonId, LessonProgressEntity::getStatus));
        Map<UUID, BigDecimal> lessonPctMap = lessonProgressList.stream()
                .filter(e -> e.getLessonPercentage() != null)
                .collect(Collectors.toMap(LessonProgressEntity::getLessonId, LessonProgressEntity::getLessonPercentage));

        if (response.getChapters() != null) {
            for (var ch : response.getChapters()) {
                if (ch.getLessons() != null) {
                    for (var lesson : ch.getLessons()) {
                        String p = progressMap.get(lesson.getId());
                        if (p != null) {
                            lesson.setProgress(p);
                        }
                        BigDecimal lp = lessonPctMap.get(lesson.getId());
                        if (lp != null) {
                            lesson.setProgressPercentage(lp.intValue());
                        }
                    }
                }
            }
        }

        return response;
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

        // Update incoming data
        if (request.getWatchedPercentage() != null) {
            progress.setWatchedPercentage(request.getWatchedPercentage());
        }
        if (request.getLastPlaybackPosition() != null) {
            progress.setLastPlaybackPosition(request.getLastPlaybackPosition());
        }
        if (request.getDocumentViewSeconds() != null) {
            // Accumulate — always take the highest value sent
            int existing = progress.getDocumentViewSeconds() != null ? progress.getDocumentViewSeconds() : 0;
            if (request.getDocumentViewSeconds() > existing) {
                progress.setDocumentViewSeconds(request.getDocumentViewSeconds());
            }
        }
        progress.setLastAccessedAt(Instant.now());

        // Determine lesson content type
        boolean hasVideo = hasVideoContent(lesson);
        boolean hasDocument = hasDocumentContent(lesson);

        int wp = progress.getWatchedPercentage() != null ? progress.getWatchedPercentage().intValue() : 0;
        int dv = progress.getDocumentViewSeconds() != null ? progress.getDocumentViewSeconds() : 0;

        // Auto-determine completion based on content type and learning time/scroll depth
        boolean completed = false;
        if (hasVideo && hasDocument) {
            // "nếu cả 2 thì tính video và xem 10s tài liệu" -> xem >= 90% video VÀ xem >= 10s tài liệu
            completed = wp >= 90 && dv >= 10;
        } else if (hasVideo) {
            // "xem 90% video"
            completed = wp >= 90;
        } else if (hasDocument) {
            // "20s cho tài liệu"
            completed = dv >= 20 || wp >= 90;
        } else {
            // No video or document — complete on first access
            completed = progress.getStatus() == null || "COMPLETED".equals(progress.getStatus());
        }

        if (completed || "COMPLETED".equals(progress.getStatus())) {
            progress.setStatus("COMPLETED");
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(Instant.now());
            }
        } else {
            boolean started = wp > 0 || dv > 0;
            if (started && !"COMPLETED".equals(progress.getStatus())) {
                progress.setStatus("IN_PROGRESS");
            } else if (progress.getStatus() == null) {
                progress.setStatus("IN_PROGRESS");
            }
        }

        // Calculate lesson percentage
        progress.setLessonPercentage(calculateLessonPercentage(wp, dv, hasVideo, hasDocument, lesson));

        lessonProgressRepository.save(progress);

        // Always update course-level progress
        updateCourseProgress(studentId, courseId);
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

        progress.setStatus("COMPLETED");
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
                .filter(cp -> !"COMPLETED".equals(cp.getStatus()))
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
    public void resetProgressForStudents(UUID courseId, List<UUID> studentIds) {
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

    @Override
    @Transactional
    public void resetStudentCourseProgress(UUID courseId, UUID studentId) {
        if (courseId == null || studentId == null) return;
        resetProgressForStudents(courseId, List.of(studentId));
    }

    /* ── Private helpers ─────────────────────────────────── */

    private BigDecimal calculateLessonPercentage(int wp, int dv, boolean hasVideo, boolean hasDocument, Lesson lesson) {
        if (hasVideo && hasDocument) {
            if (wp >= 90 && dv >= 10) return BigDecimal.valueOf(100);
            double vidScore = Math.min(wp / 90.0, 1.0) * 80.0;
            double docScore = Math.min(dv / 10.0, 1.0) * 20.0;
            return BigDecimal.valueOf(Math.round(vidScore + docScore));
        } else if (hasVideo) {
            return BigDecimal.valueOf(Math.min(wp, 100));
        } else if (hasDocument) {
            if (dv >= 20 || wp >= 90) return BigDecimal.valueOf(100);
            double pctByTime = Math.min(dv * 100.0 / 20.0, 100);
            double finalPct = Math.max(pctByTime, Math.min(wp, 100));
            return BigDecimal.valueOf(Math.round(finalPct));
        } else {
            // No video or document — immediate 100%
            return BigDecimal.valueOf(100);
        }
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

        long completedCount = allProgress.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .count();

        double avgPercentage = 0;
        if (totalLessons > 0) {
            avgPercentage = (completedCount * 100.0) / totalLessons;
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
                    newProg.setOverallPercentage(BigDecimal.ZERO);
                    newProg.setStatus("IN_PROGRESS");
                    return newProg;
                });

        courseProgress.setCompletedLessonsCount((int) completedCount);
        courseProgress.setTotalLessonsCount(totalLessons);
        courseProgress.setOverallPercentage(BigDecimal.valueOf(Math.round(avgPercentage)));
        courseProgress.setUpdatedAt(Instant.now());

        if (completedCount >= totalLessons && totalLessons > 0) {
            courseProgress.setStatus("COMPLETED");
        } else {
            courseProgress.setStatus("IN_PROGRESS");
        }

        courseProgressRepository.save(courseProgress);
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

        return lesson.getResources().stream()
                .filter(r -> r.getId().equals(resourceId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài liệu"));
    }
}
