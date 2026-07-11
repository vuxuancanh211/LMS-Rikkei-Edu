package project.lms_rikkei_edu.modules.quiz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.CourseEnrollmentEntity;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.repository.LessonRepository;
import project.lms_rikkei_edu.modules.notification.enums.NotificationType;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;
import project.lms_rikkei_edu.modules.quiz.dto.request.*;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.entity.*;
import project.lms_rikkei_edu.modules.quiz.enums.*;
import project.lms_rikkei_edu.modules.quiz.repository.*;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final BankQuestionRepository bankQuestionRepository;
    private final BankOptionRepository bankOptionRepository;
    private final BankQuestionEmbeddingService bankQuestionEmbeddingService;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final NotificationService notificationService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final CurrentUserProvider currentUserProvider;

    // ── Create / Update ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public QuizSummaryResponse create(UUID courseId, UUID instructorId, QuizMetadataRequest request) {
        QuizEntity quiz = new QuizEntity();
        quiz.setId(UUID.randomUUID());
        quiz.setStatus(QuizStatus.DRAFT);
        quiz.setCourseId(courseId);
        quiz.setCreatedBy(instructorId);
        applyMetadata(quiz, request);
        quizRepository.save(quiz);
        return toSummary(quiz);
    }

    @Override
    @Transactional
    public QuizSummaryResponse updateMetadata(UUID courseId, UUID quizId, QuizMetadataRequest request) {
        QuizEntity quiz = findDraftQuiz(courseId, quizId);
        applyMetadata(quiz, request);
        quizRepository.save(quiz);
        return toSummary(quiz);
    }

    @Override
    @Transactional
    public void delete(UUID courseId, UUID quizId) {
        QuizEntity quiz = findDraftQuiz(courseId, quizId);
        if (lessonRepository.findByQuizId(quizId).isPresent()) {
            throw new BusinessException("Không thể xoá đề đang gắn với bài học trong khoá học, hãy gỡ khỏi bài học trước");
        }
        quizQuestionRepository.deleteByQuizId(quizId);
        quizRepository.delete(quiz);
    }

    // ── Get ───────────────────────────────────────────────────────────────────

    @Override
    public QuizDetailResponse getDetail(UUID courseId, UUID quizId) {
        QuizEntity quiz = findQuiz(courseId, quizId);
        return toDetail(quiz, loadQuestions(quizId));
    }

    @Override
    public Page<QuizSummaryResponse> listByCourse(UUID courseId, String title, Pageable pageable) {
        Page<QuizEntity> page = (title != null && !title.isBlank())
                ? quizRepository.findByCourseIdAndTitleContainingIgnoreCase(courseId, title.trim(), pageable)
                : quizRepository.findByCourseId(courseId, pageable);
        return page.map(this::toSummary);
    }

    // ── Add questions (Type 1/2) ──────────────────────────────────────────────

    @Override
    @Transactional
    public QuizDetailResponse addBankQuestions(UUID courseId, UUID quizId,
                                               QuizAddBankQuestionsRequest request) {
        QuizEntity quiz = findDraftQuiz(courseId, quizId);
        int currentOrder = (int) quizQuestionRepository.countByQuizId(quizId);

        for (UUID bankId : request.getBankQuestionIds()) {
            BankQuestionEntity bankQ = bankQuestionRepository.findById(bankId)
                    .filter(q -> courseId.equals(q.getCourseId()))
                    .orElseThrow(() -> new BusinessException("Câu hỏi " + bankId + " không thuộc khóa học này"));

            QuizQuestionEntity qq = snapshotFromBank(bankQ, quizId, currentOrder++);
            quizQuestionRepository.save(qq);
            snapshotOptions(bankId, qq.getId());
        }

        return toDetail(quiz, loadQuestions(quizId));
    }

    @Override
    @Transactional
    public QuizDetailResponse addManualQuestion(UUID courseId, UUID quizId,
                                                UUID instructorId, QuizManualQuestionRequest request) {
        QuizEntity quiz = findDraftQuiz(courseId, quizId);
        validateManualOptions(request);

        int order = (int) quizQuestionRepository.countByQuizId(quizId);
        QuizQuestionEntity qq = new QuizQuestionEntity();
        qq.setQuizId(quizId);
        qq.setQuestionText(request.getQuestionText());
        qq.setQuestionType(request.getQuestionType());
        qq.setDifficulty(request.getDifficulty());
        qq.setSubjectTag(request.getSubjectTag());
        qq.setExplanation(request.getExplanation());
        qq.setOrderIndex(order);

        // Nếu saveToBank → tạo bản trong bank trước, lấy id làm source reference
        if (request.isSaveToBank()) {
            BankQuestionEntity bankQ = saveToBankFromManual(courseId, instructorId, request);
            qq.setBankQuestionId(bankQ.getId());
        }

        quizQuestionRepository.save(qq);
        saveQuizOptions(qq.getId(), request.getOptions());

        return toDetail(quiz, loadQuestions(quizId));
    }

    @Override
    @Transactional
    public void removeQuestion(UUID courseId, UUID quizId, UUID questionId) {
        findDraftQuiz(courseId, quizId);
        QuizQuestionEntity qq = quizQuestionRepository.findById(questionId)
                .filter(q -> quizId.equals(q.getQuizId()))
                .orElseThrow(() -> new BusinessException("Câu hỏi không tồn tại trong quiz này"));
        quizOptionRepository.deleteByQuestionId(questionId);
        quizQuestionRepository.delete(qq);
        renormalizeQuestionOrder(quizId);
    }

    @Override
    @Transactional
    public QuizDetailResponse reorderQuestions(UUID courseId, UUID quizId, List<UUID> questionIds) {
        QuizEntity quiz = findDraftQuiz(courseId, quizId);
        List<QuizQuestionEntity> questions = quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId);

        List<UUID> existingIds = questions.stream().map(QuizQuestionEntity::getId).toList();
        if (questionIds.size() != existingIds.size() || !new HashSet<>(questionIds).equals(new HashSet<>(existingIds))) {
            throw new BusinessException("Danh sách sắp xếp lại câu hỏi không khớp với danh sách hiện có");
        }

        Map<UUID, QuizQuestionEntity> byId = questions.stream()
                .collect(Collectors.toMap(QuizQuestionEntity::getId, q -> q));
        for (int i = 0; i < questionIds.size(); i++) {
            byId.get(questionIds.get(i)).setOrderIndex(i);
        }
        quizQuestionRepository.saveAll(questions);

        return toDetail(quiz, loadQuestions(quizId));
    }

    // ── Random Draw config (Type 3) ───────────────────────────────────────────

    @Override
    @Transactional
    public QuizSummaryResponse configureRandomDraw(UUID courseId, UUID quizId,
                                                    QuizRandomConfigRequest request) {
        QuizEntity quiz = findDraftQuiz(courseId, quizId);

        if (quiz.getQuizType() != QuizType.RANDOM_DRAW)
            throw new BusinessException("Quiz này không phải loại Random Draw");

        validateRandomConfig(courseId, request);

        quiz.setRandomMode(request.getRandomMode());
        quiz.setSubjectTagFilter(request.getSubjectTagFilter());

        if (request.getRandomMode() == RandomMode.BY_DIFFICULTY) {
            quiz.setDifficultyConfig(request.getDifficultyConfig());
            quiz.setRandomTotalCount(null);
        } else {
            quiz.setDifficultyConfig(null);
            quiz.setRandomTotalCount(request.getTotalCount());
        }

        quizRepository.save(quiz);
        return toSummary(quiz);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public QuizSummaryResponse publish(UUID courseId, UUID quizId) {
        QuizEntity quiz = findQuiz(courseId, quizId);

        if (quiz.getStatus() == QuizStatus.PUBLISHED)
            throw new BusinessException("Quiz đã được xuất bản");
        if (quiz.getStatus() == QuizStatus.ARCHIVED)
            throw new BusinessException("Quiz đang ARCHIVED, hãy unarchive trước");

        validatePublish(quiz, quizId);

        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setPublishedAt(OffsetDateTime.now());
        quizRepository.save(quiz);

        notifyStudentsQuizPublished(courseId, quiz);

        return toSummary(quiz);
    }

    // Thông báo cho học viên đã đăng ký khóa học biết có đề trắc nghiệm mới — fail-soft, lỗi ở đây
    // (VD: notification service lỗi) không được làm hỏng việc publish quiz.
    private void notifyStudentsQuizPublished(UUID courseId, QuizEntity quiz) {
        try {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null) return;
            List<CourseEnrollmentEntity> enrollments = courseEnrollmentRepository.findAllByCourseId(courseId);
            if (enrollments.isEmpty()) return;

            UUID actorId = currentUserProvider.getCurrentUserId().orElse(null);
            String actorName = currentUserProvider.getCurrentUser().map(UserPrincipal::getUsername).orElse(null);
            String title = "Đề trắc nghiệm mới";
            String body = "Đề \"" + quiz.getTitle() + "\" trong khóa \"" + course.getTitle() + "\" đã được xuất bản.";

            for (CourseEnrollmentEntity enrollment : enrollments) {
                UUID studentId = enrollment.getStudentId();
                if (!notificationPreferenceService.isInAppEnabled(studentId, NotificationType.QUIZ_PUBLISHED.name())) {
                    continue;
                }
                notificationService.createNotification(
                        studentId,
                        NotificationType.QUIZ_PUBLISHED.name(),
                        title,
                        body,
                        "QUIZ",
                        quiz.getId(),
                        actorId,
                        actorName,
                        "quiz-published-" + quiz.getId() + ":" + studentId);
            }
        } catch (Exception ex) {
            log.warn("Không thể gửi thông báo publish quiz cho quizId={}: {}", quiz.getId(), ex.getMessage());
        }
    }

    @Override
    @Transactional
    public QuizSummaryResponse archive(UUID courseId, UUID quizId) {
        QuizEntity quiz = findQuiz(courseId, quizId);

        if (quiz.getStatus() != QuizStatus.PUBLISHED)
            throw new BusinessException("Chỉ có thể archive quiz đang PUBLISHED");

        quiz.setStatus(QuizStatus.ARCHIVED);
        quiz.setArchivedAt(OffsetDateTime.now());
        quizRepository.save(quiz);
        return toSummary(quiz);
    }

    @Override
    @Transactional
    public QuizSummaryResponse unarchive(UUID courseId, UUID quizId) {
        QuizEntity quiz = findQuiz(courseId, quizId);

        if (quiz.getStatus() != QuizStatus.ARCHIVED)
            throw new BusinessException("Quiz không ở trạng thái ARCHIVED");

        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setArchivedAt(null);
        quizRepository.save(quiz);
        return toSummary(quiz);
    }

    // ── Dry Run ───────────────────────────────────────────────────────────────

    @Override
    public DryRunResponse dryRun(UUID courseId, UUID quizId) {
        QuizEntity quiz = findQuiz(courseId, quizId);

        List<QuizQuestionResponse> questions;

        if (quiz.getQuizType() == QuizType.RANDOM_DRAW) {
            // Rút ngẫu nhiên từ bank nhưng không lưu
            questions = simulateRandomDraw(quiz, courseId);
        } else {
            questions = loadQuestions(quizId);
        }

        // Xáo thứ tự câu hỏi — cùng điều kiện với lúc học viên start attempt thật
        // (xem QuizAttemptServiceImpl#startAttempt): shuffleQuestions=true HOẶC quizType != STATIC
        if (Boolean.TRUE.equals(quiz.getShuffleQuestions()) || quiz.getQuizType() != QuizType.STATIC) {
            questions = new ArrayList<>(questions);
            Collections.shuffle(questions);
        }

        // Xáo thứ tự đáp án — độc lập với xáo câu hỏi, chỉ theo cờ shuffleOptions,
        // giống hệt logic học viên thật (QuizAttemptServiceImpl#loadOptionsForStudent)
        if (Boolean.TRUE.equals(quiz.getShuffleOptions())) {
            questions = questions.stream().map(q -> {
                List<QuizOptionResponse> opts = new ArrayList<>(q.getOptions());
                Collections.shuffle(opts);
                return q.toBuilder().options(opts).build();
            }).toList();
        }

        return DryRunResponse.builder()
                .questions(questions)
                .totalQuestions(questions.size())
                .note("Đây là bản xem thử — không lưu kết quả vào hệ thống")
                .durationMinutes(quiz.getDurationMinutes())
                .build();
    }

    /**
     * Chấm điểm bản xem thử — tính đúng/sai + điểm dựa trên dữ liệu gốc
     * (bank_options cho RANDOM_DRAW, quiz_options cho STATIC/SHUFFLED_POOL).
     * Không ghi bất kỳ bảng nào, không ảnh hưởng thống kê thật của quiz.
     */
    @Override
    public DryRunGradeResponse gradeDryRun(UUID courseId, UUID quizId, DryRunGradeRequest request) {
        QuizEntity quiz = findQuiz(courseId, quizId);

        List<UUID> questionIds = request.getQuestionIds() != null ? request.getQuestionIds() : List.of();
        Map<UUID, List<UUID>> answers = request.getAnswers() != null ? request.getAnswers() : Map.of();
        boolean isRandomDraw = quiz.getQuizType() == QuizType.RANDOM_DRAW;

        // Mọi câu hỏi cùng trọng số — điểm chỉ còn là % số câu trả lời đúng / tổng số câu.
        int correctCount = 0, incorrectCount = 0, unansweredCount = 0, gradedCount = 0;
        List<DryRunAnswerResult> results = new ArrayList<>();

        for (UUID questionId : questionIds) {
            Set<UUID> correctOptionIds;

            if (isRandomDraw) {
                BankQuestionEntity bq = bankQuestionRepository.findById(questionId).orElse(null);
                if (bq == null) continue;
                correctOptionIds = bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(questionId).stream()
                        .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                        .map(BankOptionEntity::getId)
                        .collect(Collectors.toSet());
            } else {
                QuizQuestionEntity qq = quizQuestionRepository.findById(questionId).orElse(null);
                if (qq == null) continue;
                correctOptionIds = quizOptionRepository.findByQuestionIdOrderByOrderIndex(questionId).stream()
                        .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                        .map(QuizOptionEntity::getId)
                        .collect(Collectors.toSet());
            }

            List<UUID> selected = answers.getOrDefault(questionId, List.of());
            boolean answered = !selected.isEmpty();
            boolean isCorrect = answered && new HashSet<>(selected).equals(correctOptionIds);

            gradedCount++;
            if (!answered) unansweredCount++;
            else if (isCorrect) correctCount++;
            else incorrectCount++;

            results.add(DryRunAnswerResult.builder()
                    .questionId(questionId)
                    .answered(answered)
                    .isCorrect(isCorrect)
                    .correctOptionIds(new ArrayList<>(correctOptionIds))
                    .build());
        }

        BigDecimal pct = gradedCount > 0
                ? BigDecimal.valueOf(correctCount)
                        .divide(BigDecimal.valueOf(gradedCount), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        boolean passed = quiz.getPassScore() != null && pct.compareTo(quiz.getPassScore()) >= 0;

        return DryRunGradeResponse.builder()
                .score(BigDecimal.valueOf(correctCount))
                .maxScore(BigDecimal.valueOf(gradedCount))
                .scorePercentage(pct)
                .isPassed(passed)
                .correctCount(correctCount)
                .incorrectCount(incorrectCount)
                .unansweredCount(unansweredCount)
                .totalQuestions(gradedCount)
                .answers(results)
                .build();
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void autoArchiveExpiredQuizzes() {
        List<QuizEntity> expired = quizRepository
                .findByStatusAndEndDateBefore(QuizStatus.PUBLISHED, OffsetDateTime.now());
        expired.forEach(q -> {
            q.setStatus(QuizStatus.ARCHIVED);
            q.setArchivedAt(OffsetDateTime.now());
            log.info("Auto-archived quiz {} (end_date passed)", q.getId());
        });
        quizRepository.saveAll(expired);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private QuizEntity findQuiz(UUID courseId, UUID quizId) {
        return quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException("Quiz không tồn tại", HttpStatus.NOT_FOUND));
    }

    private QuizEntity findDraftQuiz(UUID courseId, UUID quizId) {
        QuizEntity quiz = findQuiz(courseId, quizId);
        if (quiz.getStatus() != QuizStatus.DRAFT)
            throw new BusinessException("Chỉ có thể chỉnh sửa quiz đang ở trạng thái DRAFT");
        return quiz;
    }

    private void applyMetadata(QuizEntity quiz, QuizMetadataRequest request) {
        quiz.setTitle(request.getTitle());
        quiz.setDescription(request.getDescription());
        quiz.setQuizType(request.getQuizType());
        quiz.setDurationMinutes(request.getDurationMinutes());
        // null = không giới hạn số lần làm bài (xem QuizAttemptServiceImpl.startAttempt) — không fallback về 3.
        quiz.setMaxAttempts(request.getMaxAttempts());
        quiz.setPassScore(request.getPassScore());
        quiz.setShuffleQuestions(Boolean.TRUE.equals(request.getShuffleQuestions()));
        quiz.setShuffleOptions(Boolean.TRUE.equals(request.getShuffleOptions()));
        quiz.setProctoringEnabled(Boolean.TRUE.equals(request.getProctoringEnabled()));
        quiz.setCooldownMinutes(request.getCooldownMinutes() != null ? request.getCooldownMinutes() : 20);
        quiz.setEndDate(request.getEndDate());
    }

    private void validatePublish(QuizEntity quiz, UUID quizId) {
        if (quiz.getQuizType() != QuizType.RANDOM_DRAW) {
            long count = quizQuestionRepository.countByQuizId(quizId);
            if (count == 0)
                throw new BusinessException("Quiz phải có ít nhất 1 câu hỏi trước khi xuất bản");
        } else {
            if (quiz.getRandomMode() == null)
                throw new BusinessException("Quiz Random Draw phải cấu hình random mode trước khi xuất bản");
        }
    }

    private void validateRandomConfig(UUID courseId, QuizRandomConfigRequest request) {
        if (request.getRandomMode() == RandomMode.FULLY_RANDOM) {
            if (request.getTotalCount() == null || request.getTotalCount() < 1)
                throw new BusinessException("Phải chỉ định số câu cần rút (totalCount)");
            long available = bankQuestionRepository.countByCourseIdAndStatus(courseId, QuestionStatus.ACTIVE);
            if (available < request.getTotalCount())
                throw new BusinessException("Bank chỉ có " + available
                        + " câu ACTIVE, không đủ để rút " + request.getTotalCount() + " câu");
        } else {
            if (request.getDifficultyConfig() == null || request.getDifficultyConfig().isEmpty())
                throw new BusinessException("BY_DIFFICULTY cần cấu hình difficultyConfig");

            for (Map.Entry<String, Integer> entry : request.getDifficultyConfig().entrySet()) {
                QuestionDifficulty diff = parseEnum(QuestionDifficulty.class, entry.getKey().toUpperCase());
                if (diff == null) throw new BusinessException("Độ khó không hợp lệ: " + entry.getKey());
                long available = bankQuestionRepository.countByCourseIdAndStatusAndDifficulty(
                        courseId, QuestionStatus.ACTIVE, diff);
                if (available < entry.getValue())
                    throw new BusinessException("Bank chỉ có " + available
                            + " câu " + diff + ", cần " + entry.getValue());
            }
        }
    }

    private void validateManualOptions(QuizManualQuestionRequest request) {
        var options = request.getOptions();
        if (options == null || options.size() < 2)
            throw new BusinessException("Phải có ít nhất 2 đáp án");

        long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).count();
        switch (request.getQuestionType()) {
            case SINGLE_CHOICE, TRUE_FALSE -> {
                if (correctCount != 1)
                    throw new BusinessException("Loại câu hỏi này phải có đúng 1 đáp án đúng");
            }
            case MULTIPLE_CHOICE -> {
                if (correctCount < 2)
                    throw new BusinessException("Multiple choice phải có ít nhất 2 đáp án đúng");
            }
        }
    }

    private QuizQuestionEntity snapshotFromBank(BankQuestionEntity bank, UUID quizId, int order) {
        QuizQuestionEntity qq = new QuizQuestionEntity();
        qq.setQuizId(quizId);
        qq.setBankQuestionId(bank.getId());
        qq.setQuestionText(bank.getQuestionText());
        qq.setQuestionType(bank.getQuestionType());
        qq.setDifficulty(bank.getDifficulty());
        qq.setSubjectTag(bank.getSubjectTag());
        qq.setOrderIndex(order);
        return qq;
    }

    private void snapshotOptions(UUID bankQuestionId, UUID quizQuestionId) {
        bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(bankQuestionId)
                .forEach(bo -> {
                    QuizOptionEntity qo = new QuizOptionEntity();
                    qo.setQuestionId(quizQuestionId);
                    qo.setOptionText(bo.getOptionText());
                    qo.setIsCorrect(bo.getIsCorrect());
                    qo.setOrderIndex(bo.getOrderIndex());
                    quizOptionRepository.save(qo);
                });
    }

    private void saveQuizOptions(UUID questionId, List<BankOptionRequest> options) {
        IntStream.range(0, options.size()).forEach(i -> {
            var opt = options.get(i);
            QuizOptionEntity qo = new QuizOptionEntity();
            qo.setQuestionId(questionId);
            qo.setOptionText(opt.getOptionText());
            qo.setIsCorrect(opt.getIsCorrect());
            qo.setOrderIndex(opt.getOrderIndex() != null ? opt.getOrderIndex() : i);
            quizOptionRepository.save(qo);
        });
    }

    private BankQuestionEntity saveToBankFromManual(UUID courseId, UUID instructorId,
                                                     QuizManualQuestionRequest request) {
        BankQuestionEntity bank = new BankQuestionEntity();
        bank.setCourseId(courseId);
        bank.setCreatedBy(instructorId);
        bank.setQuestionText(request.getQuestionText());
        bank.setQuestionType(request.getQuestionType());
        bank.setDifficulty(request.getDifficulty());
        bank.setSubjectTag(request.getSubjectTag());
        // saveAndFlush — embedAndSaveSafe() ghi qua JdbcTemplate (raw SQL, bỏ qua Hibernate session),
        // FK bank_question_embeddings -> bank_questions cần dòng cha đã thực sự tồn tại trong DB.
        bankQuestionRepository.saveAndFlush(bank);

        request.getOptions().forEach(opt -> {
            BankOptionEntity bo = new BankOptionEntity();
            bo.setBankQuestionId(bank.getId());
            bo.setOptionText(opt.getOptionText());
            bo.setIsCorrect(opt.getIsCorrect());
            bankOptionRepository.save(bo);
        });
        bankQuestionEmbeddingService.embedAndSaveSafe(bank.getId(), bank.getQuestionText());
        return bank;
    }

    // Dồn lại orderIndex liên tục (0..n-1) sau khi xoá 1 câu — không nhận thứ tự cụ thể, chỉ lấp
    // khoảng trống. Khác với reorderQuestions() public (API sắp xếp lại theo danh sách id cụ thể).
    private void renormalizeQuestionOrder(UUID quizId) {
        List<QuizQuestionEntity> questions = quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId);
        IntStream.range(0, questions.size()).forEach(i -> questions.get(i).setOrderIndex(i));
        quizQuestionRepository.saveAll(questions);
    }

    private List<QuizQuestionResponse> loadQuestions(UUID quizId) {
        return quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)
                .stream()
                .map(this::toQuestionResponse)
                .toList();
    }

    private List<QuizQuestionResponse> simulateRandomDraw(QuizEntity quiz, UUID courseId) {
        List<BankQuestionEntity> drawn;
        if (quiz.getRandomMode() == RandomMode.FULLY_RANDOM) {
            int count = quiz.getRandomTotalCount() != null ? quiz.getRandomTotalCount() : 10;
            drawn = bankQuestionRepository.randomFully(courseId, quiz.getSubjectTagFilter(), count);
        } else {
            drawn = new ArrayList<>();
            if (quiz.getDifficultyConfig() != null) {
                quiz.getDifficultyConfig().forEach((diff, count) ->
                        drawn.addAll(bankQuestionRepository.randomByDifficulty(
                                courseId, diff.toUpperCase(), quiz.getSubjectTagFilter(), count)));
            }
        }
        return drawn.stream().map(bq -> {
            List<QuizOptionResponse> opts = bankOptionRepository
                    .findByBankQuestionIdOrderByOrderIndex(bq.getId())
                    .stream()
                    .map(o -> QuizOptionResponse.builder()
                            .id(o.getId()).optionText(o.getOptionText()).orderIndex(o.getOrderIndex()).build())
                    .toList();
            return QuizQuestionResponse.builder()
                    .id(bq.getId())
                    .bankQuestionId(bq.getId())
                    .questionText(bq.getQuestionText())
                    .questionType(bq.getQuestionType())
                    .difficulty(bq.getDifficulty())
                    .subjectTag(bq.getSubjectTag())
                    .options(opts)
                    .build();
        }).toList();
    }

    private QuizQuestionResponse toQuestionResponse(QuizQuestionEntity qq) {
        List<QuizOptionResponse> options = quizOptionRepository
                .findByQuestionIdOrderByOrderIndex(qq.getId())
                .stream()
                .map(o -> QuizOptionResponse.builder()
                        .id(o.getId())
                        .optionText(o.getOptionText())
                        .orderIndex(o.getOrderIndex())
                        .build())
                .toList();
        return QuizQuestionResponse.builder()
                .id(qq.getId())
                .bankQuestionId(qq.getBankQuestionId())
                .questionText(qq.getQuestionText())
                .questionType(qq.getQuestionType())
                .difficulty(qq.getDifficulty())
                .subjectTag(qq.getSubjectTag())
                .orderIndex(qq.getOrderIndex())
                .explanation(qq.getExplanation())
                .options(options)
                .build();
    }

    private QuizSummaryResponse toSummary(QuizEntity q) {
        return QuizSummaryResponse.builder()
                .id(q.getId())
                .title(q.getTitle())
                .description(q.getDescription())
                .quizType(q.getQuizType())
                .status(q.getStatus())
                .durationMinutes(q.getDurationMinutes())
                .maxAttempts(q.getMaxAttempts())
                .passScore(q.getPassScore())
                .shuffleQuestions(q.getShuffleQuestions())
                .shuffleOptions(q.getShuffleOptions())
                .proctoringEnabled(q.getProctoringEnabled())
                .cooldownMinutes(q.getCooldownMinutes())
                .endDate(q.getEndDate())
                .publishedAt(q.getPublishedAt())
                .archivedAt(q.getArchivedAt())
                .questionCount(quizQuestionRepository.countByQuizId(q.getId()))
                .build();
    }

    private QuizDetailResponse toDetail(QuizEntity q, List<QuizQuestionResponse> questions) {
        return QuizDetailResponse.builder()
                .id(q.getId())
                .courseId(q.getCourseId())
                .title(q.getTitle())
                .description(q.getDescription())
                .quizType(q.getQuizType())
                .status(q.getStatus())
                .durationMinutes(q.getDurationMinutes())
                .maxAttempts(q.getMaxAttempts())
                .passScore(q.getPassScore())
                .shuffleQuestions(q.getShuffleQuestions())
                .shuffleOptions(q.getShuffleOptions())
                .proctoringEnabled(q.getProctoringEnabled())
                .cooldownMinutes(q.getCooldownMinutes())
                .endDate(q.getEndDate())
                .publishedAt(q.getPublishedAt())
                .archivedAt(q.getArchivedAt())
                .randomMode(q.getRandomMode())
                .randomTotalCount(q.getRandomTotalCount())
                .difficultyConfig(q.getDifficultyConfig())
                .subjectTagFilter(q.getSubjectTagFilter())
                .questions(questions)
                .build();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> clazz, String value) {
        try { return Enum.valueOf(clazz, value); }
        catch (Exception e) { return null; }
    }
}
