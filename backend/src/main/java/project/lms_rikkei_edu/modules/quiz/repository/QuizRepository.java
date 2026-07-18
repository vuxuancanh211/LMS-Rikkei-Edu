package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.quiz.entity.QuizEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<QuizEntity, UUID> {

    List<QuizEntity> findByCourseIdAndStatus(UUID courseId, QuizStatus status);

    long countByCourseId(UUID courseId);

    // KHÔNG phân trang — dùng cho thống kê (QuizStatsServiceImpl) cần TOÀN BỘ quiz của khóa học.
    // Giao diện danh sách quiz (QuizController#list) dùng 2 method phân trang bên dưới.
    List<QuizEntity> findByCourseId(UUID courseId);

    List<QuizEntity> findByCourseIdIn(List<UUID> courseIds);

    Page<QuizEntity> findByCourseId(UUID courseId, Pageable pageable);

    Page<QuizEntity> findByCourseIdAndTitleContainingIgnoreCase(UUID courseId, String title, Pageable pageable);

    Optional<QuizEntity> findByIdAndCourseId(UUID id, UUID courseId);

    // Tìm quiz đã hết end_date và vẫn đang PUBLISHED — để auto-archive
    List<QuizEntity> findByStatusAndEndDateBefore(QuizStatus status, OffsetDateTime now);
}
