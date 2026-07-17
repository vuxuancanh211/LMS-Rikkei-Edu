package project.lms_rikkei_edu.modules.quiz.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionImportConfirmRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionImportConfirmResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionImportPreviewResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionSearchHit;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;

import java.util.List;
import java.util.UUID;

public interface BankQuestionService {

    BankQuestionResponse create(UUID courseId, UUID instructorId, BankQuestionRequest request);

    BankQuestionResponse update(UUID courseId, UUID questionId, BankQuestionRequest request);

    void delete(UUID courseId, UUID questionId);

    void toggleStatus(UUID courseId, UUID questionId);

    BankQuestionResponse getById(UUID courseId, UUID questionId);

    // KHÔNG phân trang — dùng cho pha text-match của search() và PickBankQuestionsModal (cần tải hết).
    List<BankQuestionResponse> list(UUID courseId, QuestionStatus status,
                                    QuestionDifficulty difficulty, String subjectTag);

    // Phân trang — tab Ngân hàng câu hỏi dùng, tránh tải hết 1 lượt gây lag giao diện.
    Page<BankQuestionResponse> listPaged(UUID courseId, QuestionStatus status,
                                        QuestionDifficulty difficulty, String subjectTag, Pageable pageable);

    /** Hybrid search: khớp chữ xếp trước, tương đồng ngữ nghĩa (pgvector) nối sau. */
    List<BankQuestionSearchHit> search(UUID courseId, String q, QuestionStatus status,
                                       QuestionDifficulty difficulty, String subjectTag);

    List<String> getTags(UUID courseId);

    BankQuestionImportPreviewResponse importPreview(UUID courseId, MultipartFile file);

    BankQuestionImportConfirmResponse importConfirm(UUID courseId, UUID instructorId,
                                                    BankQuestionImportConfirmRequest request);

    byte[] export(UUID courseId, String format);
}
