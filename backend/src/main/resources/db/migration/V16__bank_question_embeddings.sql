-- Embedding cho câu hỏi ngân hàng — phục vụ (1) hybrid semantic search trong tab Ngân hàng
-- câu hỏi, và (2) hồi sinh duplicate-check ngữ nghĩa của luồng sinh câu hỏi AI
-- (AiQuestionGeneratorService#checkDuplicates đã SELECT bảng này từ trước nhưng bảng chưa
-- từng được tạo — luôn rơi xuống fallback so text y hệt).
--
-- Bảng phụ thay vì cột trên bank_questions: JPA không map được kiểu vector nên mọi thao tác
-- đi qua JdbcTemplate (theo tiền lệ document_chunks.embedding); ON DELETE CASCADE để
-- hard-delete câu hỏi không cần code dọn dẹp — soft-delete (INACTIVE) giữ embedding có chủ đích.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE "bank_question_embeddings" (
    "question_id" uuid PRIMARY KEY REFERENCES "bank_questions"("id") ON DELETE CASCADE,
    "embedding"   vector(1024) NOT NULL,
    "updated_at"  timestamptz NOT NULL DEFAULT now()
);

-- Không tạo index vector (HNSW/ivfflat): bank mỗi khóa học chỉ vài trăm dòng (import cap
-- 500/file), seq scan cosine dưới 1ms. Cân nhắc HNSW (embedding vector_cosine_ops) khi
-- tổng bảng vượt ~50k dòng.
