package project.lms_rikkei_edu.modules.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.modules.course.entity.CourseVersion;
import project.lms_rikkei_edu.modules.course.repository.CourseVersionRepository;
import project.lms_rikkei_edu.modules.course.service.impl.CourseVersionReferenceChecker;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link CourseVersionReferenceChecker} bảo vệ đúng nguyên tắc: 1 tài liệu (s3Key) chỉ an toàn để
 * xóa thật khi KHÔNG còn CourseVersion nào (bất kỳ trạng thái) của khóa học còn tham chiếu nó —
 * tránh phá file mà 1 version khác (VD: bản PUBLISHED) vẫn cần để rollback về sau.
 */
@ExtendWith(MockitoExtension.class)
class CourseVersionReferenceCheckerTest {

    @Mock CourseVersionRepository courseVersionRepository;

    CourseVersionReferenceChecker checker;

    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        checker = new CourseVersionReferenceChecker(courseVersionRepository, new ObjectMapper());
    }

    private CourseVersion versionWithSnapshot(String snapshotJson) {
        CourseVersion v = new CourseVersion();
        v.setCourseId(courseId);
        v.setSnapshot(snapshotJson);
        return v;
    }

    private String snapshotWithResourceKey(String s3Key) {
        return """
            {"chapters":[{"lessons":[{"resources":[{"s3Key":"%s"}]}]}]}
            """.formatted(s3Key);
    }

    @Test
    void isSafeToDelete_whenNoVersionReferencesKey() {
        when(courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(courseId))
                .thenReturn(List.of(versionWithSnapshot(snapshotWithResourceKey("courses/other-file.pdf"))));

        assertThat(checker.isSafeToDelete(courseId, "courses/file.pdf")).isTrue();
    }

    @Test
    void notSafeToDelete_whenAnyVersionStillReferencesKey() {
        when(courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(courseId))
                .thenReturn(List.of(
                        versionWithSnapshot(snapshotWithResourceKey("courses/other-file.pdf")),
                        versionWithSnapshot(snapshotWithResourceKey("courses/file.pdf"))
                ));

        assertThat(checker.isSafeToDelete(courseId, "courses/file.pdf")).isFalse();
    }

    @Test
    void isSafeToDelete_whenCourseHasNoVersions() {
        when(courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(courseId)).thenReturn(List.of());

        assertThat(checker.isSafeToDelete(courseId, "courses/file.pdf")).isTrue();
    }

    @Test
    void isSafeToDelete_forExternalUrl_regardlessOfReferences() {
        assertThat(checker.isSafeToDelete(courseId, "ext://https://youtube.com/watch?v=abc")).isTrue();
    }

    @Test
    void isSafeToDelete_forNullOrBlankKey() {
        assertThat(checker.isSafeToDelete(courseId, null)).isTrue();
        assertThat(checker.isSafeToDelete(courseId, "  ")).isTrue();
    }

    @Test
    void notSafeToDelete_whenSnapshotUnparseable() {
        // Không đọc được snapshot — an toàn hơn là coi như CÓ tham chiếu, tránh xóa nhầm.
        when(courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(courseId))
                .thenReturn(List.of(versionWithSnapshot("{not valid json")));

        assertThat(checker.isSafeToDelete(courseId, "courses/file.pdf")).isFalse();
    }

    @Test
    void isSafeToDelete_ignoresVersionsWithNullSnapshot() {
        CourseVersion draftNoSnapshot = new CourseVersion();
        draftNoSnapshot.setCourseId(courseId);
        draftNoSnapshot.setSnapshot(null);

        when(courseVersionRepository.findByCourseIdOrderByVersionNumberDesc(courseId))
                .thenReturn(List.of(draftNoSnapshot));

        assertThat(checker.isSafeToDelete(courseId, "courses/file.pdf")).isTrue();
    }
}
