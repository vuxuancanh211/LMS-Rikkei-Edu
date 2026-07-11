package project.lms_rikkei_edu.modules.course.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResponse;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;
import project.lms_rikkei_edu.modules.quiz.entity.QuizEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;
import project.lms_rikkei_edu.modules.quiz.repository.QuizRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests LessonMapper.mapResources() via a concrete test subclass —
 * avoids needing a full Spring context for a MapStruct abstract class.
 */
@ExtendWith(MockitoExtension.class)
class LessonMapperTest {

    @Mock LessonResourceMapper lessonResourceMapper;
    @Mock QuizRepository quizRepository;

    private LessonMapper mapper() {
        LessonMapper m = new LessonMapper() {
            @Override
            public LessonResponse toResponse(Lesson lesson) {
                return LessonResponse.builder()
                        .resources(mapResources(lesson))
                        .quizTitle(resolveQuizTitle(lesson))
                        .quizStatus(resolveQuizStatus(lesson))
                        .build();
            }
        };
        m.lessonResourceMapper = lessonResourceMapper;
        m.quizRepository = quizRepository;
        return m;
    }

    @Test
    void mapResources_returnsEmpty_whenResourcesNull() {
        Lesson lesson = new Lesson();
        lesson.setResources(null);

        List<LessonResourceResponse> result = mapper().toResponse(lesson).getResources();

        assertThat(result).isEmpty();
    }

    @Test
    void mapResources_filtersOutDeletedResources() {
        LessonResource active = new LessonResource();
        active.setS3Key("key.pdf");
        active.setDeletedAt(null);

        LessonResource deleted = new LessonResource();
        deleted.setS3Key("old.pdf");
        deleted.setDeletedAt(Instant.now());

        Lesson lesson = new Lesson();
        lesson.setResources(new ArrayList<>(List.of(active, deleted)));

        LessonResourceResponse activeResponse = LessonResourceResponse.builder()
                .id(UUID.randomUUID()).build();
        when(lessonResourceMapper.toResponse(active)).thenReturn(activeResponse);

        List<LessonResourceResponse> result = mapper().toResponse(lesson).getResources();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(activeResponse);
    }

    @Test
    void mapResources_returnsAll_whenNoneDeleted() {
        LessonResource r1 = new LessonResource();
        r1.setS3Key("a.pdf");
        LessonResource r2 = new LessonResource();
        r2.setS3Key("b.pdf");

        Lesson lesson = new Lesson();
        lesson.setResources(new ArrayList<>(List.of(r1, r2)));

        LessonResourceResponse resp1 = LessonResourceResponse.builder().id(UUID.randomUUID()).build();
        LessonResourceResponse resp2 = LessonResourceResponse.builder().id(UUID.randomUUID()).build();
        when(lessonResourceMapper.toResponse(r1)).thenReturn(resp1);
        when(lessonResourceMapper.toResponse(r2)).thenReturn(resp2);

        List<LessonResourceResponse> result = mapper().toResponse(lesson).getResources();

        assertThat(result).containsExactly(resp1, resp2);
    }

    @Test
    void resolveQuizTitleAndStatus_returnNull_whenLessonHasNoQuiz() {
        Lesson lesson = new Lesson();
        lesson.setQuizId(null);

        LessonResponse result = mapper().toResponse(lesson);

        assertThat(result.getQuizTitle()).isNull();
        assertThat(result.getQuizStatus()).isNull();
    }

    @Test
    void resolveQuizTitleAndStatus_returnQuizData_whenQuizExists() {
        UUID quizId = UUID.randomUUID();
        Lesson lesson = new Lesson();
        lesson.setQuizId(quizId);

        QuizEntity quiz = new QuizEntity();
        quiz.setId(quizId);
        quiz.setTitle("Chương 1 Quiz");
        quiz.setStatus(QuizStatus.PUBLISHED);
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));

        LessonResponse result = mapper().toResponse(lesson);

        assertThat(result.getQuizTitle()).isEqualTo("Chương 1 Quiz");
        assertThat(result.getQuizStatus()).isEqualTo(QuizStatus.PUBLISHED);
    }

    @Test
    void resolveQuizTitleAndStatus_returnNull_whenQuizIdPointsToMissingQuiz() {
        UUID quizId = UUID.randomUUID();
        Lesson lesson = new Lesson();
        lesson.setQuizId(quizId);
        when(quizRepository.findById(quizId)).thenReturn(Optional.empty());

        LessonResponse result = mapper().toResponse(lesson);

        assertThat(result.getQuizTitle()).isNull();
        assertThat(result.getQuizStatus()).isNull();
    }
}
