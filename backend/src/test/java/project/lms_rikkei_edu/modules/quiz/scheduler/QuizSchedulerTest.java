package project.lms_rikkei_edu.modules.quiz.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.modules.quiz.service.QuizAttemptService;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuizSchedulerTest {

    @Mock private QuizService quizService;
    @Mock private QuizAttemptService attemptService;

    private QuizScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new QuizScheduler(quizService, attemptService);
    }

    @Test
    void autoArchiveExpiredQuizzes_delegatesToQuizService() {
        scheduler.autoArchiveExpiredQuizzes();

        verify(quizService).autoArchiveExpiredQuizzes();
    }

    @Test
    void autoSubmitExpiredAttempts_delegatesToAttemptService() {
        scheduler.autoSubmitExpiredAttempts();

        verify(attemptService).autoSubmitExpiredAttempts();
    }
}
