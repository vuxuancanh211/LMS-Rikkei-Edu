package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** Trả về ngay khi bắt đầu sinh câu hỏi AI — FE dùng jobId để poll tiến trình. */
@Getter
@AllArgsConstructor
public class AiGenerationJobStartResponse {
    private UUID jobId;
}
