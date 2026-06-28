package project.lms_rikkei_edu.modules.quiz.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.lms_rikkei_edu.modules.quiz.service.QuizAttemptService;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuizScheduler {

    private final QuizService quizService;
    private final QuizAttemptService attemptService;

    // Chạy mỗi 5 phút — kiểm tra quiz hết end_date để auto-archive
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void autoArchiveExpiredQuizzes() {
        log.debug("Running auto-archive check for expired quizzes");
        quizService.autoArchiveExpiredQuizzes();
    }

    // Chạy mỗi phút — auto-submit attempt hết giờ
    @Scheduled(fixedDelay = 60 * 1000)
    public void autoSubmitExpiredAttempts() {
        log.debug("Running auto-submit check for expired attempts");
        attemptService.autoSubmitExpiredAttempts();
    }
}
