package project.lms_rikkei_edu.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import project.lms_rikkei_edu.modules.course.dto.response.ChapterResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseDetailResponse;
import project.lms_rikkei_edu.modules.course.dto.response.CourseResponse;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResponse;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;
import project.lms_rikkei_edu.modules.course.service.impl.CourseListCacheGateway;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips the DTOs cached under "course-detail"/"course-list" through the exact
 * serializer built in {@link RedisConfig} (Object-typed, DefaultTyping.NON_FINAL).
 * A @Cacheable annotation compiles and passes normal unit tests even when the cached
 * type can't actually survive a Redis round-trip — Spring MVC only ever serializes
 * these response DTOs, never deserializes them, so a missing no-args constructor/setter
 * (Lombok @Builder-only classes) or an implicitly-final wrapper (a record) only breaks
 * on the second real request, in production. This test exists to catch that class of
 * bug before it does.
 */
class CourseCacheSerializationTest {

    private GenericJacksonJsonRedisSerializer serializer() {
        ObjectMapper mapper = JsonMapper.builder()
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                        DefaultTyping.NON_FINAL
                )
                .build();
        return new GenericJacksonJsonRedisSerializer(mapper);
    }

    @Test
    void roundTripsCourseResponse() {
        GenericJacksonJsonRedisSerializer serializer = serializer();
        CourseResponse response = CourseResponse.builder()
                .id(UUID.randomUUID()).title("Test").slug("test").status(CourseStatus.DRAFT).build();

        byte[] bytes = serializer.serialize(response);
        Object result = serializer.deserialize(bytes);
        assertThat(result).isInstanceOf(CourseResponse.class);
        assertThat(((CourseResponse) result).getTitle()).isEqualTo("Test");
    }

    @Test
    void roundTripsCourseListCacheGatewayEntry() {
        GenericJacksonJsonRedisSerializer serializer = serializer();
        CourseResponse c1 = CourseResponse.builder().id(UUID.randomUUID()).title("A").build();
        CourseResponse c2 = CourseResponse.builder().id(UUID.randomUUID()).title("B").build();
        CourseListCacheGateway.Entry entry = new CourseListCacheGateway.Entry(List.of(c1, c2), 2);

        byte[] bytes = serializer.serialize(entry);
        Object result = serializer.deserialize(bytes);
        assertThat(result).isInstanceOf(CourseListCacheGateway.Entry.class);
        CourseListCacheGateway.Entry deserialized = (CourseListCacheGateway.Entry) result;
        assertThat(deserialized.getTotalElements()).isEqualTo(2);
        assertThat(deserialized.getContent()).hasSize(2);
        assertThat(deserialized.getContent().get(0).getTitle()).isEqualTo("A");
    }

    @Test
    void roundTripsCourseDetailResponseWithNestedChaptersLessonsResources() {
        GenericJacksonJsonRedisSerializer serializer = serializer();
        LessonResourceResponse resource = LessonResourceResponse.builder()
                .id(UUID.randomUUID()).resourceType(ResourceType.VIDEO).displayName("Res").build();
        LessonResponse lesson = LessonResponse.builder()
                .id(UUID.randomUUID()).title("Lesson 1").type(LessonType.VIDEO)
                .resources(List.of(resource)).build();
        ChapterResponse chapter = ChapterResponse.builder()
                .id(UUID.randomUUID()).title("Chapter 1").lessons(List.of(lesson)).build();
        CourseDetailResponse detail = CourseDetailResponse.builder()
                .id(UUID.randomUUID()).title("Course").slug("course").status(CourseStatus.DRAFT)
                .chapters(List.of(chapter)).build();

        byte[] bytes = serializer.serialize(detail);
        Object result = serializer.deserialize(bytes);
        assertThat(result).isInstanceOf(CourseDetailResponse.class);
        CourseDetailResponse deserialized = (CourseDetailResponse) result;
        assertThat(deserialized.getChapters()).hasSize(1);
        assertThat(deserialized.getChapters().get(0).getLessons()).hasSize(1);
        assertThat(deserialized.getChapters().get(0).getLessons().get(0).getResources()).hasSize(1);
        assertThat(deserialized.getChapters().get(0).getLessons().get(0).getResources().get(0).getDisplayName())
                .isEqualTo("Res");
    }
}
