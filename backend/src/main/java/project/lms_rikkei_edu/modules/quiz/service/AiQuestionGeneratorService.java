package project.lms_rikkei_edu.modules.quiz.service;

import project.lms_rikkei_edu.modules.quiz.dto.request.AiGenerateQuestionsRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerationJobStatusResponse;

import java.util.UUID;

/**
 * Sinh câu hỏi trắc nghiệm bằng LLM, có tham khảo tài liệu AI (RAG) của khóa học
 * nếu có, sau đó kiểm tra trùng lặp với ngân hàng câu hỏi hiện có bằng cosine
 * similarity (pgvector). Xem {@code impl.AiQuestionGeneratorServiceImpl} cho chi tiết pipeline.
 *
 * <p>Chạy nền theo từng bước (không block request thread — bước "gọi LLM" có thể mất
 * 30-90s tuỳ số câu yêu cầu): {@code startGenerate} tạo job và trả về ngay, pipeline
 * thật chạy trong {@code generateAsync} (dispatch qua {@code @Async}, phải được gọi từ
 * bean khác — xem controller — vì self-invocation trong cùng class sẽ bỏ qua {@code @Async}).
 * FE poll {@code getJobStatus} để biết đang ở bước nào.
 */
public interface AiQuestionGeneratorService {

    /**
     * Tạo job (step=RETRIEVING_CONTEXT) và trả về ngay — KHÔNG chạy pipeline ở đây.
     * Caller (controller) phải gọi {@link #generateAsync} riêng sau đó — cross-bean call
     * để {@code @Async} có hiệu lực (gọi ngay trong method này sẽ là self-invocation, bỏ qua proxy).
     */
    UUID startGenerate(UUID courseId, AiGenerateQuestionsRequest req, UUID requestedBy);

    /** Chạy nền — pipeline thật, cập nhật {@code job.step} trước mỗi giai đoạn để FE poll thấy tiến trình. */
    void generateAsync(UUID jobId, UUID courseId, AiGenerateQuestionsRequest req);

    /** FE poll endpoint này để biết job đang ở bước nào; khi DONE thì kèm luôn kết quả. */
    AiGenerationJobStatusResponse getJobStatus(UUID jobId);
}
