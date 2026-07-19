package project.lms_rikkei_edu.modules.quiz.entity;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.quiz.enums.AttemptStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuizAttemptEntityTest {

    @Test
    void prePersist_generatesIdAndDefaults_whenUnset() {
        QuizAttemptEntity attempt = new QuizAttemptEntity();

        attempt.prePersist();

        assertThat(attempt.getId()).isNotNull();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attempt.getViolationCount()).isZero();
        assertThat(attempt.getStartedAt()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingIdStatusAndViolationCount() {
        QuizAttemptEntity attempt = new QuizAttemptEntity();
        UUID id = UUID.randomUUID();
        attempt.setId(id);
        attempt.setStatus(AttemptStatus.GRADED);
        attempt.setViolationCount(3);

        attempt.prePersist();

        assertThat(attempt.getId()).isEqualTo(id);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getViolationCount()).isEqualTo(3);
    }

    @Test
    void gettersAndSetters_roundTrip() {
        QuizAttemptEntity attempt = new QuizAttemptEntity();
        UUID quizId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        attempt.setQuizId(quizId);
        attempt.setStudentId(studentId);
        attempt.setCourseId(courseId);
        attempt.setAttemptNumber(1);
        attempt.setScore(BigDecimal.valueOf(8));
        attempt.setScorePercentage(BigDecimal.valueOf(80));
        attempt.setIsPassed(true);
        attempt.setCorrectCount(8);
        attempt.setIncorrectCount(2);
        attempt.setUnansweredCount(0);

        assertThat(attempt.getQuizId()).isEqualTo(quizId);
        assertThat(attempt.getStudentId()).isEqualTo(studentId);
        assertThat(attempt.getCourseId()).isEqualTo(courseId);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getScore()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(attempt.getScorePercentage()).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat(attempt.getIsPassed()).isTrue();
        assertThat(attempt.getCorrectCount()).isEqualTo(8);
        assertThat(attempt.getIncorrectCount()).isEqualTo(2);
        assertThat(attempt.getUnansweredCount()).isZero();
    }
}
