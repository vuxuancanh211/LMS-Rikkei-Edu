-- Theo dõi tiến trình sinh câu hỏi bằng AI (chạy nền, không block request thread).
-- FE poll bảng này qua GET .../bank-questions/ai/generate/{jobId} để biết đang ở bước nào.
CREATE TABLE "ai_question_generation_jobs" (
    "id" uuid PRIMARY KEY,
    "course_id" uuid NOT NULL,
    "requested_by" uuid NOT NULL,
    "step" varchar(30) NOT NULL DEFAULT 'RETRIEVING_CONTEXT',
    "result_json" text,
    "error_message" text,
    "created_at" timestamptz NOT NULL DEFAULT now(),
    "updated_at" timestamptz
);

CREATE INDEX "idx_ai_question_gen_jobs_course" ON "ai_question_generation_jobs"("course_id");
