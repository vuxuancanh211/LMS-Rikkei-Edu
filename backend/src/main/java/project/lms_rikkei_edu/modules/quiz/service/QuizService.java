package project.lms_rikkei_edu.modules.quiz.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.lms_rikkei_edu.modules.quiz.dto.request.*;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;

import java.util.List;
import java.util.UUID;

public interface QuizService {

    QuizSummaryResponse create(UUID courseId, UUID instructorId, QuizMetadataRequest request);

    QuizSummaryResponse updateMetadata(UUID courseId, UUID quizId, QuizMetadataRequest request);

    void delete(UUID courseId, UUID quizId);

    QuizDetailResponse getDetail(UUID courseId, UUID quizId);

    // Phân trang — tránh tải toàn bộ quiz của khóa học lên giao diện 1 lượt gây lag
    Page<QuizSummaryResponse> listByCourse(UUID courseId, String title, Pageable pageable);

    // Thêm câu hỏi từ bank (Type 1/2) — snapshot ngay
    QuizDetailResponse addBankQuestions(UUID courseId, UUID quizId, QuizAddBankQuestionsRequest request);

    // Thêm câu hỏi thủ công (Type 1/2) — tùy chọn lưu bank
    QuizDetailResponse addManualQuestion(UUID courseId, UUID quizId,
                                         UUID instructorId, QuizManualQuestionRequest request);

    // Xóa câu hỏi khỏi quiz (chỉ khi DRAFT)
    void removeQuestion(UUID courseId, UUID quizId, UUID questionId);

    // Sắp xếp lại thứ tự câu hỏi trong quiz thủ công (STATIC/SHUFFLED_POOL, chỉ khi DRAFT) —
    // questionIds phải khớp chính xác tập câu hỏi hiện có, chỉ đổi thứ tự.
    QuizDetailResponse reorderQuestions(UUID courseId, UUID quizId, List<UUID> questionIds);

    // Cấu hình Random Draw (Type 3) + validate bank ngay
    QuizSummaryResponse configureRandomDraw(UUID courseId, UUID quizId, QuizRandomConfigRequest request);

    // Lifecycle
    QuizSummaryResponse publish(UUID courseId, UUID quizId);

    QuizSummaryResponse archive(UUID courseId, UUID quizId);

    QuizSummaryResponse unarchive(UUID courseId, UUID quizId);

    // Dry Run — chạy thử không lưu DB
    DryRunResponse dryRun(UUID courseId, UUID quizId);

    // Chấm điểm bản xem thử — tính đúng/sai + điểm, KHÔNG lưu DB
    DryRunGradeResponse gradeDryRun(UUID courseId, UUID quizId, DryRunGradeRequest request);

    // Scheduler gọi — auto-archive quiz đã hết end_date
    void autoArchiveExpiredQuizzes();
}
