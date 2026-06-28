package project.lms_rikkei_edu.modules.quiz.service;

import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionImportConfirmRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionImportConfirmResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionImportPreviewResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionResponse;
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

    List<BankQuestionResponse> list(UUID courseId, QuestionStatus status,
                                    QuestionDifficulty difficulty, String subjectTag);

    BankQuestionImportPreviewResponse importPreview(UUID courseId, MultipartFile file);

    BankQuestionImportConfirmResponse importConfirm(UUID courseId, UUID instructorId,
                                                    BankQuestionImportConfirmRequest request);

    byte[] export(UUID courseId, String format);
}
