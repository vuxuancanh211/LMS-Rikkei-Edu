package project.lms_rikkei_edu.modules.quiz.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.course.repository.LessonRepository;
import project.lms_rikkei_edu.modules.course.service.StudentCourseService;
import project.lms_rikkei_edu.modules.quiz.dto.request.AutosaveRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.SubmitAttemptRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.entity.*;
import project.lms_rikkei_edu.modules.quiz.enums.*;
import project.lms_rikkei_edu.modules.quiz.repository.*;
import project.lms_rikkei_edu.modules.quiz.service.QuizAttemptService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizAttemptServiceImpl implements QuizAttemptService {

    private static final long AUTOSAVE_TTL_SECONDS = 24 * 60 * 60L;
    private static final String AUTOSAVE_KEY_PREFIX = "quiz:autosave:";
    private static final DateTimeFormatter RETRY_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository answerRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final BankQuestionRepository bankQuestionRepository;
    private final BankOptionRepository bankOptionRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final LessonRepository lessonRepository;
    private final StudentCourseService studentCourseService;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ── Start ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public StartAttemptResponse startAttempt(UUID courseId, UUID quizId, UUID studentId, String ipAddress) {
        // Khóa advisory theo (quizId, studentId) trong phạm vi transaction hiện tại — nếu 2 request
        // startAttempt cùng lúc lọt qua đây (VD: React StrictMode gọi effect 2 lần khi dev, double-click,
        // 2 tab), request thứ 2 sẽ chờ ở đây đến khi request 1 commit xong, rồi mới thấy attempt IN_PROGRESS
        // vừa tạo và báo lỗi hợp lệ — thay vì cả 2 cùng tính attemptNumber=1 và vỡ unique constraint lúc commit.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(?1))")
                .setParameter(1, quizId.toString() + ":" + studentId.toString())
                .getSingleResult();

        QuizEntity quiz = findPublishedQuiz(courseId, quizId);

        if (!courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId))
            throw new BusinessException("Bạn chưa đăng ký khóa học này");

        // Kiểm tra attempt đang IN_PROGRESS chưa hoàn thành
        attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, studentId, AttemptStatus.IN_PROGRESS)
                .ifPresent(a -> { throw new BusinessException("Bạn đang có một lần thi chưa hoàn thành"); });

        // Kiểm tra max_attempts
        long doneCount = attemptRepository.countByQuizIdAndStudentId(quizId, studentId);
        if (quiz.getMaxAttempts() != null && doneCount >= quiz.getMaxAttempts())
            throw new BusinessException("Bạn đã sử dụng hết số lần làm bài (" + quiz.getMaxAttempts() + " lần)");

        // Kiểm tra cooldown
        attemptRepository.findLatestByQuizIdAndStudentId(quizId, studentId).ifPresent(latest -> {
            if (latest.getSubmittedAt() != null) {
                int cooldown = quiz.getCooldownMinutes() != null ? quiz.getCooldownMinutes() : 20;
                OffsetDateTime canRetryAt = latest.getSubmittedAt().plusMinutes(cooldown);
                if (OffsetDateTime.now().isBefore(canRetryAt))
                    throw new BusinessException("Bạn cần chờ đến " + RETRY_AT_FORMAT.format(canRetryAt) + " để làm lại");
            }
        });

        // Rút câu hỏi
        List<QuizQuestionEntity> rawQuestions = drawQuestions(quiz, courseId);

        // Shuffle nếu cần
        List<UUID> questionOrder = rawQuestions.stream().map(QuizQuestionEntity::getId).collect(Collectors.toList());
        if (Boolean.TRUE.equals(quiz.getShuffleQuestions()) || quiz.getQuizType() != QuizType.STATIC) {
            Collections.shuffle(questionOrder);
        }

        // Tạo attempt
        QuizAttemptEntity attempt = new QuizAttemptEntity();
        attempt.setId(UUID.randomUUID());
        attempt.setQuizId(quizId);
        attempt.setCourseId(courseId);
        attempt.setStudentId(studentId);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setAttemptNumber((int) doneCount + 1);
        attempt.setQuestionOrder(questionOrder);
        attempt.setProctoringEnabled(Boolean.TRUE.equals(quiz.getProctoringEnabled()));
        attempt.setStartedAt(OffsetDateTime.now());
        attempt.setIpAddress(ipAddress);
        attemptRepository.save(attempt);

        // Build response (câu hỏi theo questionOrder, shuffle options nếu cần)
        Map<UUID, QuizQuestionEntity> qMap = rawQuestions.stream()
                .collect(Collectors.toMap(QuizQuestionEntity::getId, q -> q));
        List<QuizQuestionResponse> questions = buildQuestionsForStudent(
                questionOrder, qMap, Boolean.TRUE.equals(quiz.getShuffleOptions()));

        int duration = quiz.getDurationMinutes() != null ? quiz.getDurationMinutes() : 60;
        return StartAttemptResponse.builder()
                .attemptId(attempt.getId())
                .quizId(quizId)
                .attemptNumber(attempt.getAttemptNumber())
                .startedAt(attempt.getStartedAt())
                .expiresAt(attempt.getStartedAt().plusMinutes(duration))
                .durationMinutes(duration)
                .proctoringEnabled(Boolean.TRUE.equals(attempt.getProctoringEnabled()))
                .questions(questions)
                .build();
    }

    // ── Autosave ──────────────────────────────────────────────────────────────

    @Override
    public void autosave(UUID attemptId, UUID studentId, AutosaveRequest request) {
        QuizAttemptEntity attempt = findInProgressAttempt(attemptId, studentId);
        String key = AUTOSAVE_KEY_PREFIX + attemptId;
        try {
            String json = objectMapper.writeValueAsString(request.getAnswers());
            redisService.set(key, json, AUTOSAVE_TTL_SECONDS);
        } catch (Exception e) {
            throw new BusinessException("Không thể lưu tạm bài làm");
        }
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AttemptResultResponse submit(UUID attemptId, UUID studentId, SubmitAttemptRequest request) {
        QuizAttemptEntity attempt = findInProgressAttempt(attemptId, studentId);
        Map<UUID, List<UUID>> answers = resolveAnswers(attemptId, request);
        return doSubmit(attempt, answers, false);
    }

    // ── Get result ────────────────────────────────────────────────────────────

    @Override
    public AttemptResultResponse getResult(UUID attemptId, UUID requesterId) {
        QuizAttemptEntity attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("Attempt không tồn tại", HttpStatus.NOT_FOUND));

        if (!attempt.getStudentId().equals(requesterId))
            throw new BusinessException("Bạn không có quyền xem kết quả này", HttpStatus.FORBIDDEN);

        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS)
            throw new BusinessException("Bài làm chưa được nộp");

        List<QuizAttemptAnswerEntity> answerEntities = answerRepository.findByAttemptId(attemptId);
        Map<UUID, QuizQuestionEntity> qMap = loadQuestionMap(attempt);

        List<AttemptAnswerResult> results = answerEntities.stream()
                .map(a -> buildAnswerResult(a, qMap))
                .toList();

        return toResultResponse(attempt, results);
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void autoSubmitExpiredAttempts() {
        // Tìm attempt IN_PROGRESS đã bắt đầu > 3 giờ trước (quá mức tối đa có thể)
        // Mỗi attempt sẽ được check xem có thực sự hết giờ theo duration của quiz không
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(1);
        List<QuizAttemptEntity> candidates = attemptRepository.findExpiredAttempts(cutoff);

        candidates.forEach(a -> {
            QuizEntity quiz = quizRepository.findById(a.getQuizId()).orElse(null);
            if (quiz == null) return;
            int duration = quiz.getDurationMinutes() != null ? quiz.getDurationMinutes() : 60;
            if (a.getStartedAt().plusMinutes(duration).isBefore(OffsetDateTime.now())) {
                Map<UUID, List<UUID>> answers = resolveAnswers(a.getId(), null);
                doSubmit(a, answers, true);
                log.info("Auto-submitted expired attempt {} for student {}", a.getId(), a.getStudentId());
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private QuizEntity findPublishedQuiz(UUID courseId, UUID quizId) {
        return quizRepository.findByIdAndCourseId(quizId, courseId)
                .filter(q -> q.getStatus() == QuizStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("Quiz không tồn tại hoặc chưa được xuất bản",
                        HttpStatus.NOT_FOUND));
    }

    private QuizAttemptEntity findInProgressAttempt(UUID attemptId, UUID studentId) {
        QuizAttemptEntity attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("Attempt không tồn tại", HttpStatus.NOT_FOUND));
        if (!attempt.getStudentId().equals(studentId))
            throw new BusinessException("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS)
            throw new BusinessException("Bài làm đã được nộp hoặc hết hạn");
        return attempt;
    }

    private List<QuizQuestionEntity> drawQuestions(QuizEntity quiz, UUID courseId) {
        if (quiz.getQuizType() == QuizType.RANDOM_DRAW) {
            return drawRandom(quiz, courseId);
        }
        // STATIC / SHUFFLED_POOL: dùng câu hỏi đã được cấu hình sẵn
        return questionRepository.findByQuizIdOrderByOrderIndex(quiz.getId());
    }

    private List<QuizQuestionEntity> drawRandom(QuizEntity quiz, UUID courseId) {
        if (quiz.getRandomMode() == RandomMode.FULLY_RANDOM) {
            int count = quiz.getRandomTotalCount() != null ? quiz.getRandomTotalCount() : 10;
            List<BankQuestionEntity> drawn = bankQuestionRepository.randomFully(
                    courseId, quiz.getSubjectTagFilter(), count);
            return drawn.stream().map(bq -> snapshotToTransient(bq)).toList();
        } else {
            List<QuizQuestionEntity> result = new ArrayList<>();
            if (quiz.getDifficultyConfig() != null) {
                quiz.getDifficultyConfig().forEach((diff, cnt) -> {
                    bankQuestionRepository.randomByDifficulty(courseId, diff.toUpperCase(),
                            quiz.getSubjectTagFilter(), cnt)
                            .stream().map(this::snapshotToTransient).forEach(result::add);
                });
            }
            return result;
        }
    }

    // Chuyển BankQuestion thành QuizQuestion tạm (không persist) để dùng khi RANDOM_DRAW
    private QuizQuestionEntity snapshotToTransient(BankQuestionEntity bq) {
        QuizQuestionEntity qq = new QuizQuestionEntity();
        qq.setId(bq.getId()); // dùng bankQuestion id như quiz question id tạm
        qq.setBankQuestionId(bq.getId());
        qq.setQuestionText(bq.getQuestionText());
        qq.setQuestionType(bq.getQuestionType());
        qq.setDifficulty(bq.getDifficulty());
        qq.setSubjectTag(bq.getSubjectTag());
        return qq;
    }

    private List<QuizQuestionResponse> buildQuestionsForStudent(
            List<UUID> questionOrder,
            Map<UUID, QuizQuestionEntity> qMap,
            boolean shuffleOptions) {

        return questionOrder.stream().map(qId -> {
            QuizQuestionEntity qq = qMap.get(qId);
            if (qq == null) return null;

            // Load options — from quiz_options nếu cloned, từ bank_options nếu RANDOM_DRAW transient
            List<QuizOptionResponse> options = loadOptionsForStudent(qId, qq.getBankQuestionId(), shuffleOptions);

            return QuizQuestionResponse.builder()
                    .id(qq.getId())
                    .bankQuestionId(qq.getBankQuestionId())
                    .questionText(qq.getQuestionText())
                    .questionType(qq.getQuestionType())
                    .difficulty(qq.getDifficulty())
                    .subjectTag(qq.getSubjectTag())
                    .options(options)
                    .build();
        }).filter(Objects::nonNull).toList();
    }

    private List<QuizOptionResponse> loadOptionsForStudent(UUID questionId, UUID bankQuestionId,
                                                            boolean shuffle) {
        List<QuizOptionResponse> options;
        // Nếu có quiz_options (cloned) thì dùng
        List<?> quizOpts = optionRepository.findByQuestionIdOrderByOrderIndex(questionId);
        if (!quizOpts.isEmpty()) {
            options = ((List<QuizOptionEntity>) quizOpts).stream()
                    .map(o -> QuizOptionResponse.builder()
                            .id(o.getId())
                            .optionText(o.getOptionText())
                            .orderIndex(o.getOrderIndex())
                            .build())
                    .collect(Collectors.toList());
        } else if (bankQuestionId != null) {
            // RANDOM_DRAW transient — dùng bank_options
            options = bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(bankQuestionId)
                    .stream()
                    .map(o -> QuizOptionResponse.builder()
                            .id(o.getId())
                            .optionText(o.getOptionText())
                            .orderIndex(o.getOrderIndex())
                            .build())
                    .collect(Collectors.toList());
        } else {
            options = new ArrayList<>();
        }
        if (shuffle) Collections.shuffle(options);
        return options;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, List<UUID>> resolveAnswers(UUID attemptId, SubmitAttemptRequest request) {
        // Nếu client gửi answers → dùng
        if (request != null && request.getAnswers() != null && !request.getAnswers().isEmpty()) {
            return request.getAnswers();
        }
        // Fallback: lấy từ Redis autosave
        String key = AUTOSAVE_KEY_PREFIX + attemptId;
        return redisService.get(key).map(raw -> {
            try {
                return objectMapper.readValue(raw.toString(),
                        new TypeReference<Map<UUID, List<UUID>>>() {});
            } catch (Exception e) {
                return new HashMap<UUID, List<UUID>>();
            }
        }).orElse(new HashMap<>());
    }

    @Transactional
    AttemptResultResponse doSubmit(QuizAttemptEntity attempt, Map<UUID, List<UUID>> answers,
                                   boolean autoSubmitted) {
        UUID attemptId = attempt.getId();

        // Load all questions for this attempt
        List<QuizQuestionEntity> questions = loadQuestionsForAttempt(attempt);
        Map<UUID, QuizQuestionEntity> qMap = questions.stream()
                .collect(Collectors.toMap(QuizQuestionEntity::getId, q -> q));

        // Load correct options per question
        Map<UUID, Set<UUID>> correctOptionsMap = buildCorrectOptionsMap(questions, attempt);

        // Mọi câu hỏi cùng trọng số — điểm chỉ còn là % số câu trả lời đúng / tổng số câu,
        // không còn khái niệm "points" theo từng câu nữa.
        int correctCount = 0, incorrectCount = 0, unansweredCount = 0;

        List<QuizAttemptAnswerEntity> answerEntities = new ArrayList<>();
        for (QuizQuestionEntity q : questions) {
            List<UUID> selected = answers.getOrDefault(q.getId(), List.of());
            Set<UUID> correct = correctOptionsMap.getOrDefault(q.getId(), Set.of());

            boolean isCorrect = !selected.isEmpty() && new HashSet<>(selected).equals(correct);

            if (selected.isEmpty()) unansweredCount++;
            else if (isCorrect) correctCount++;
            else incorrectCount++;

            QuizAttemptAnswerEntity ans = new QuizAttemptAnswerEntity();
            ans.setAttemptId(attemptId);
            ans.setQuestionId(q.getId());
            ans.setSelectedOptionIds(selected.isEmpty() ? null : selected);
            ans.setIsCorrect(isCorrect);
            ans.setAnsweredAt(OffsetDateTime.now());
            answerEntities.add(ans);
        }

        answerRepository.saveAll(answerEntities);

        // Tính score percentage — số câu đúng / tổng số câu
        int totalQuestions = questions.size();
        BigDecimal pct = totalQuestions > 0
                ? BigDecimal.valueOf(correctCount)
                        .divide(BigDecimal.valueOf(totalQuestions), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Kiểm tra pass
        QuizEntity quiz = quizRepository.findById(attempt.getQuizId()).orElseThrow();
        boolean passed = quiz.getPassScore() != null && pct.compareTo(quiz.getPassScore()) >= 0;

        int timeSpent = (int) java.time.Duration.between(attempt.getStartedAt(), OffsetDateTime.now()).getSeconds();

        // Cập nhật attempt
        attempt.setStatus(AttemptStatus.GRADED);
        attempt.setScore(BigDecimal.valueOf(correctCount));
        attempt.setScorePercentage(pct);
        attempt.setIsPassed(passed);
        attempt.setCorrectCount(correctCount);
        attempt.setIncorrectCount(incorrectCount);
        attempt.setUnansweredCount(unansweredCount);
        attempt.setSubmittedAt(OffsetDateTime.now());
        attempt.setTimeSpentSeconds(timeSpent);
        attempt.setAutoSubmitted(autoSubmitted);
        attemptRepository.save(attempt);

        // Quiz gắn với 1 lesson (dạy như 1 bài học) + đậu bài → đánh dấu lesson đó hoàn thành.
        // Fail-soft: lỗi ở đây không được làm hỏng việc nộp bài của học viên.
        if (passed) {
            try {
                lessonRepository.findByQuizId(quiz.getId()).ifPresent((Lesson lesson) ->
                        studentCourseService.completeQuizLesson(attempt.getStudentId(), quiz.getCourseId(), lesson.getId()));
            } catch (Exception ex) {
                log.warn("Không thể đánh dấu hoàn thành lesson cho quizId={}: {}", quiz.getId(), ex.getMessage());
            }
        }

        // Xóa autosave
        redisService.delete(AUTOSAVE_KEY_PREFIX + attemptId);

        // Build result
        List<AttemptAnswerResult> resultDetails = answerEntities.stream()
                .map(a -> buildAnswerResult(a, qMap))
                .toList();

        return toResultResponse(attempt, resultDetails);
    }

    private List<QuizQuestionEntity> loadQuestionsForAttempt(QuizAttemptEntity attempt) {
        List<UUID> order = attempt.getQuestionOrder();
        if (order == null || order.isEmpty()) {
            return questionRepository.findByQuizIdOrderByOrderIndex(attempt.getQuizId());
        }
        // Load questions theo questionOrder (có thể là quiz_questions hoặc bank_questions cho RANDOM_DRAW)
        List<QuizQuestionEntity> fromQuiz = questionRepository.findByQuizIdOrderByOrderIndex(attempt.getQuizId());
        if (!fromQuiz.isEmpty()) return fromQuiz;

        // RANDOM_DRAW: order chứa bank_question_id → load từ bank
        return order.stream()
                .map(id -> bankQuestionRepository.findById(id).map(this::snapshotToTransient).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<UUID, Set<UUID>> buildCorrectOptionsMap(List<QuizQuestionEntity> questions,
                                                         QuizAttemptEntity attempt) {
        Map<UUID, Set<UUID>> map = new HashMap<>();
        for (QuizQuestionEntity q : questions) {
            // Kiểm tra quiz_options trước
            List<QuizOptionEntity> opts = optionRepository.findByQuestionIdOrderByOrderIndex(q.getId());
            if (!opts.isEmpty()) {
                Set<UUID> correct = opts.stream()
                        .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                        .map(QuizOptionEntity::getId)
                        .collect(Collectors.toSet());
                map.put(q.getId(), correct);
            } else if (q.getBankQuestionId() != null) {
                // RANDOM_DRAW — đáp án từ bank_options
                Set<UUID> correct = bankOptionRepository
                        .findByBankQuestionIdOrderByOrderIndex(q.getBankQuestionId())
                        .stream()
                        .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                        .map(BankOptionEntity::getId)
                        .collect(Collectors.toSet());
                map.put(q.getId(), correct);
            }
        }
        return map;
    }

    private Map<UUID, QuizQuestionEntity> loadQuestionMap(QuizAttemptEntity attempt) {
        List<QuizQuestionEntity> questions = loadQuestionsForAttempt(attempt);
        return questions.stream().collect(Collectors.toMap(QuizQuestionEntity::getId, q -> q));
    }

    private AttemptAnswerResult buildAnswerResult(QuizAttemptAnswerEntity a,
                                                   Map<UUID, QuizQuestionEntity> qMap) {
        QuizQuestionEntity q = qMap.get(a.getQuestionId());
        return AttemptAnswerResult.builder()
                .questionId(a.getQuestionId())
                .questionText(q != null ? q.getQuestionText() : null)
                .selectedOptionIds(a.getSelectedOptionIds() != null ? a.getSelectedOptionIds() : List.of())
                .isCorrect(Boolean.TRUE.equals(a.getIsCorrect()))
                .build();
    }

    private AttemptResultResponse toResultResponse(QuizAttemptEntity attempt,
                                                    List<AttemptAnswerResult> answers) {
        return AttemptResultResponse.builder()
                .attemptId(attempt.getId())
                .quizId(attempt.getQuizId())
                .status(attempt.getStatus())
                .score(attempt.getScore())
                .scorePercentage(attempt.getScorePercentage())
                .isPassed(attempt.getIsPassed())
                .correctCount(attempt.getCorrectCount() != null ? attempt.getCorrectCount() : 0)
                .incorrectCount(attempt.getIncorrectCount() != null ? attempt.getIncorrectCount() : 0)
                .unansweredCount(attempt.getUnansweredCount() != null ? attempt.getUnansweredCount() : 0)
                .totalQuestions(answers.size())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .submittedAt(attempt.getSubmittedAt())
                .autoSubmitted(Boolean.TRUE.equals(attempt.getAutoSubmitted()))
                .violationCount(attempt.getViolationCount() != null ? attempt.getViolationCount() : 0)
                .answers(answers)
                .build();
    }
}
