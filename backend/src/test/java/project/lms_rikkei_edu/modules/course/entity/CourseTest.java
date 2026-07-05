package project.lms_rikkei_edu.modules.course.entity;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseTest {

    private Course emptyCourse() {
        return Course.builder().title("T").slug("t").chapters(new ArrayList<>()).build();
    }

    private Chapter liveChapter(List<Lesson> lessons) {
        Chapter ch = new Chapter();
        ch.setIsDraft(false);
        ch.setPendingDelete(false);
        ch.setLessons(new ArrayList<>(lessons));
        return ch;
    }

    private Lesson liveLesson() {
        Lesson l = new Lesson();
        l.setIsDraft(false);
        l.setPendingDelete(false);
        return l;
    }

    @Test
    void returnsFalse_whenNoDraftAnything() {
        Course c = emptyCourse();
        c.getChapters().add(liveChapter(List.of(liveLesson())));

        assertThat(c.isHasPendingDraft()).isFalse();
    }

    @Test
    void returnsTrue_whenDraftTitleSet() {
        Course c = emptyCourse();
        c.setDraftTitle("New Title");

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsTrue_whenDraftDescriptionSet() {
        Course c = emptyCourse();
        c.setDraftDescription("New desc");

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsTrue_whenDraftLevelSet() {
        Course c = emptyCourse();
        c.setDraftLevel(CourseLevel.BEGINNER);

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsTrue_whenDraftThumbnailSet() {
        Course c = emptyCourse();
        c.setDraftThumbnailUrl("https://s3/thumb.jpg");

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsTrue_whenChapterIsDraft() {
        Course c = emptyCourse();
        Chapter ch = liveChapter(List.of());
        ch.setIsDraft(true);
        c.getChapters().add(ch);

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsTrue_whenChapterPendingDelete() {
        Course c = emptyCourse();
        Chapter ch = liveChapter(List.of());
        ch.setPendingDelete(true);
        c.getChapters().add(ch);

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsTrue_whenLessonIsDraft() {
        Course c = emptyCourse();
        Lesson l = liveLesson();
        l.setIsDraft(true);
        c.getChapters().add(liveChapter(List.of(l)));

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsTrue_whenLessonPendingDelete() {
        Course c = emptyCourse();
        Lesson l = liveLesson();
        l.setPendingDelete(true);
        c.getChapters().add(liveChapter(List.of(l)));

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsTrue_whenLessonHasDraftTitle() {
        Course c = emptyCourse();
        Lesson l = liveLesson();
        l.setDraftTitle("New Lesson Title");
        c.getChapters().add(liveChapter(List.of(l)));

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsTrue_whenLessonHasDraftContentText() {
        Course c = emptyCourse();
        Lesson l = liveLesson();
        l.setDraftContentText("New content");
        c.getChapters().add(liveChapter(List.of(l)));

        assertThat(c.isHasPendingDraft()).isTrue();
    }

    @Test
    void returnsFalse_whenChaptersIsNull() {
        Course c = emptyCourse();
        c.setChapters(null);

        assertThat(c.isHasPendingDraft()).isFalse();
    }
}
