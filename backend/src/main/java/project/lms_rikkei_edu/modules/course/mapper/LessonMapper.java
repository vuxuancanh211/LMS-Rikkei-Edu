package project.lms_rikkei_edu.modules.course.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResponse;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.quiz.entity.QuizEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;
import project.lms_rikkei_edu.modules.quiz.repository.QuizRepository;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class LessonMapper {

    @Autowired
    protected LessonResourceMapper lessonResourceMapper;

    @Autowired
    protected QuizRepository quizRepository;

    // quizTitle/quizStatus dùng @Mapping(expression=...) thay vì @AfterMapping vì LessonResponse
    // dùng Lombok @Builder — MapStruct build thẳng qua builder.build() rồi return, không có bước
    // nào gọi @AfterMapping trên bean đã build (đã xác nhận qua code sinh ra, @AfterMapping bị bỏ qua âm thầm).
    @Mapping(target = "resources", expression = "java(mapResources(lesson))")
    @Mapping(target = "progress", ignore = true)
    @Mapping(target = "progressPercentage", ignore = true)
    @Mapping(target = "quizTitle", expression = "java(resolveQuizTitle(lesson))")
    @Mapping(target = "quizStatus", expression = "java(resolveQuizStatus(lesson))")
    public abstract LessonResponse toResponse(Lesson lesson);

    protected String resolveQuizTitle(Lesson lesson) {
        if (lesson.getQuizId() == null) return null;
        return quizRepository.findById(lesson.getQuizId()).map(QuizEntity::getTitle).orElse(null);
    }

    protected QuizStatus resolveQuizStatus(Lesson lesson) {
        if (lesson.getQuizId() == null) return null;
        return quizRepository.findById(lesson.getQuizId()).map(QuizEntity::getStatus).orElse(null);
    }

    protected List<LessonResourceResponse> mapResources(Lesson lesson) {
        if (lesson.getResources() == null) return List.of();
        return lesson.getResources().stream()
                .filter(r -> r.getDeletedAt() == null)
                .map(lessonResourceMapper::toResponse)
                .toList();
    }
}
