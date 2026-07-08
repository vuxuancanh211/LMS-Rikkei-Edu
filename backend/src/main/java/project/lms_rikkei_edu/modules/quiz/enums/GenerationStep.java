package project.lms_rikkei_edu.modules.quiz.enums;

/** Tiến trình sinh câu hỏi bằng AI — chạy nền, FE poll để biết đang ở bước nào. */
public enum GenerationStep {
    RETRIEVING_CONTEXT,
    GENERATING,
    CHECKING_DUPLICATES,
    DONE,
    FAILED
}
