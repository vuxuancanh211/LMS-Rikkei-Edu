package project.lms_rikkei_edu.modules.course.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResponse;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    private LessonMapper mapper() {
        LessonMapper m = new LessonMapper() {
            @Override
            public LessonResponse toResponse(Lesson lesson) {
                return LessonResponse.builder()
                        .resources(mapResources(lesson))
                        .build();
            }
        };
        m.lessonResourceMapper = lessonResourceMapper;
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
}
