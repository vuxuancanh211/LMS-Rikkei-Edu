CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE "users" (
                         "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
                         "email" varchar(255) UNIQUE NOT NULL,
                         "full_name" varchar(200) NOT NULL,
                         "password_hash" varchar(255) NOT NULL,
                         "role" varchar(20) NOT NULL,
                         "status" varchar(20) NOT NULL DEFAULT 'PENDING_ACTIVATION',
                         "phone_number" varchar(20) UNIQUE,
                         "avatar_url" text,
                         "birth_date" date,
                         "gender" varchar(10),
                         "bio" varchar(500),
                         "activation_token" varchar(255),
                         "activation_token_expires_at" timestamptz,
                         "password_changed_at" timestamptz,
                         "last_login_at" timestamptz,
                         "disabled_at" timestamptz,
                         "disabled_by" uuid,
                         "disabled_reason" varchar(500),
                         "deleted_at" timestamptz,
                         "created_by" uuid,
                         "created_at" timestamptz DEFAULT (now()),
                         "updated_at" timestamptz
);

CREATE TABLE "user_preferences" (
                                    "id" uuid PRIMARY KEY,
                                    "user_id" uuid NOT NULL,
                                    "pref_key" varchar(50) NOT NULL,
                                    "pref_value" jsonb
);

CREATE TABLE "bulk_import_jobs" (
                                    "id" uuid PRIMARY KEY,
                                    "uploaded_by" uuid NOT NULL,
                                    "file_name" varchar(255),
                                    "s3_key" varchar(500),
                                    "status" varchar(20),
                                    "total_rows" int,
                                    "success_count" int,
                                    "failed_count" int,
                                    "error_report_s3_key" varchar(500),
                                    "created_at" timestamptz,
                                    "completed_at" timestamptz
);

CREATE TABLE "audit_logs" (
                              "id" uuid PRIMARY KEY,
                              "actor_id" uuid,
                              "actor_email" varchar(255),
                              "action" varchar(60) NOT NULL,
                              "target_type" varchar(50),
                              "target_id" uuid,
                              "payload_before" jsonb,
                              "payload_after" jsonb,
                              "reason" varchar(500),
                              "ip_address" varchar(45),
                              "user_agent" text,
                              "created_at" timestamptz DEFAULT (now())
);

CREATE TABLE "course_categories" (
                                     "id" uuid PRIMARY KEY,
                                     "name" varchar(100) UNIQUE NOT NULL,
                                     "slug" varchar(100) UNIQUE NOT NULL,
                                     "is_active" boolean DEFAULT true,
                                     "created_at" timestamptz
);

CREATE TABLE "courses" (
                           "id" uuid PRIMARY KEY,
                           "instructor_id" uuid NOT NULL,
                           "category_id" uuid,
                           "title" varchar(200) NOT NULL,
                           "slug" varchar(250) UNIQUE NOT NULL,
                           "description" text,
                           "level" varchar(20),
                           "thumbnail_url" text,
                           "status" varchar(30) NOT NULL DEFAULT 'DRAFT',
                           "rejection_reason" text,
                           "chat_enabled" boolean DEFAULT false,
                           "submitted_at" timestamptz,
                           "published_at" timestamptz,
                           "pending_update_at" timestamptz,
                           "deleted_at" timestamptz,
                           "created_at" timestamptz,
                           "updated_at" timestamptz
);

CREATE TABLE "course_approval_logs" (
                                        "id" uuid PRIMARY KEY,
                                        "course_id" uuid NOT NULL,
                                        "admin_id" uuid NOT NULL,
                                        "action" varchar(20),
                                        "reason" text,
                                        "created_at" timestamptz
);

CREATE TABLE "course_enrollments" (
                                      "id" uuid PRIMARY KEY,
                                      "course_id" uuid NOT NULL,
                                      "student_id" uuid NOT NULL,
                                      "enrolled_by" uuid,
                                      "enrolled_at" timestamptz DEFAULT (now())
);

CREATE TABLE "chapters" (
                            "id" uuid PRIMARY KEY,
                            "course_id" uuid NOT NULL,
                            "title" varchar(200) NOT NULL,
                            "description" varchar(1000),
                            "order_index" int NOT NULL,
                            "created_at" timestamptz
);

CREATE TABLE "lessons" (
                           "id" uuid PRIMARY KEY,
                           "chapter_id" uuid NOT NULL,
                           "course_id" uuid NOT NULL,
                           "title" varchar(200) NOT NULL,
                           "order_index" int NOT NULL,
                           "type" varchar(20),
                           "content_text" text,
                           "duration_seconds" int,
                           "is_preview" boolean DEFAULT false,
                           "video_status" varchar(20),
                           "video_s3_key" varchar(500),
                           "hls_manifest_url" text,
                           "created_at" timestamptz,
                           "updated_at" timestamptz
);

CREATE TABLE "lesson_resources" (
                                    "id" uuid PRIMARY KEY,
                                    "lesson_id" uuid NOT NULL,
                                    "course_id" uuid NOT NULL,
                                    "uploaded_by" uuid,
                                    "display_name" varchar(255),
                                    "original_filename" varchar(255),
                                    "s3_key" varchar(500) NOT NULL,
                                    "file_size_bytes" bigint,
                                    "mime_type" varchar(100),
                                    "resource_type" varchar(20),
                                    "is_downloadable" boolean DEFAULT true,
                                    "order_index" int,
                                    "status" varchar(20) DEFAULT 'ACTIVE',
                                    "deleted_at" timestamptz,
                                    "uploaded_at" timestamptz,
                                    "is_new_in_update" boolean NOT NULL DEFAULT false,
                                    "pending_delete" boolean NOT NULL DEFAULT false
);

CREATE TABLE "video_upload_jobs" (
                                     "id" uuid PRIMARY KEY,
                                     "lesson_id" uuid NOT NULL,
                                     "instructor_id" uuid NOT NULL,
                                     "s3_key" varchar(500),
                                     "original_filename" varchar(255),
                                     "file_size_bytes" bigint,
                                     "upload_status" varchar(30),
                                     "transcoding_started_at" timestamptz,
                                     "transcoding_completed_at" timestamptz,
                                     "error_message" text,
                                     "created_at" timestamptz
);

CREATE TABLE "ai_sources" (
                              "id" uuid PRIMARY KEY,
                              "course_id" uuid,
                              "lesson_id" uuid,
                              "resource_id" uuid,
                              "uploaded_by" uuid,
                              "source_type" varchar(30),
                              "source_name" varchar(255),
                              "source_url" text,
                              "external_id" varchar(255),
                              "status" varchar(20),
                              "ingest_status" varchar(20),
                              "chunk_count" int,
                              "metadata" jsonb,
                              "error_message" text,
                              "created_at" timestamptz,
                              "indexed_at" timestamptz,
                              "deleted_at" timestamptz
);

CREATE TABLE "ai_ingestion_jobs" (
                                     "id" uuid PRIMARY KEY,
                                     "source_id" uuid NOT NULL,
                                     "job_type" varchar(30),
                                     "status" varchar(20),
                                     "retry_count" int,
                                     "started_at" timestamptz,
                                     "completed_at" timestamptz,
                                     "error_message" text,
                                     "created_at" timestamptz
);

CREATE TABLE "document_chunks" (
                                   "id" uuid PRIMARY KEY,
                                   "source_id" uuid NOT NULL,
                                   "course_id" uuid,
                                   "chunk_index" int,
                                   "page_number" int,
                                   "section_title" varchar(255),
                                   "chunk_text" text,
                                   "embedding" vector(1024),
                                   "metadata" jsonb,
                                   "created_at" timestamptz
);

CREATE TABLE "ai_chunk_references" (
                                       "id" uuid PRIMARY KEY,
                                       "chunk_id" uuid NOT NULL,
                                       "entity_type" varchar(30),
                                       "entity_id" uuid,
                                       "created_at" timestamptz
);

CREATE TABLE "lesson_progress" (
                                   "id" uuid PRIMARY KEY,
                                   "student_id" uuid NOT NULL,
                                   "lesson_id" uuid NOT NULL,
                                   "course_id" uuid NOT NULL,
                                   "status" varchar(20),
                                   "watched_percentage" decimal(5,2),
                                   "last_playback_position" int,
                                   "first_accessed_at" timestamptz,
                                   "last_accessed_at" timestamptz,
                                   "completed_at" timestamptz
);

CREATE TABLE "course_progress" (
                                   "id" uuid PRIMARY KEY,
                                   "student_id" uuid NOT NULL,
                                   "course_id" uuid NOT NULL,
                                   "completed_lessons_count" int DEFAULT 0,
                                   "total_lessons_count" int DEFAULT 0,
                                   "overall_percentage" decimal(5,2) DEFAULT 0,
                                   "status" varchar(20),
                                   "updated_at" timestamptz
);

CREATE TABLE "study_groups" (
                                "id" uuid PRIMARY KEY,
                                "course_id" uuid NOT NULL,
                                "instructor_id" uuid NOT NULL,
                                "name" varchar(100) NOT NULL,
                                "description" text,
                                "max_capacity" int,
                                "created_at" timestamptz
);

CREATE TABLE "group_members" (
                                 "id" uuid PRIMARY KEY,
                                 "group_id" uuid NOT NULL,
                                 "student_id" uuid NOT NULL,
                                 "joined_at" timestamptz
);

CREATE TABLE "quizzes" (
                           "id" uuid PRIMARY KEY,
                           "course_id" uuid NOT NULL,
                           "created_by" uuid NOT NULL,
                           "title" varchar(200) NOT NULL,
                           "description" text,
                           "creation_mode" varchar(20),
                           "duration_minutes" int,
                           "max_attempts" int DEFAULT 1,
                           "pass_score" decimal(5,2),
                           "total_points" decimal(10,2),
                           "show_answers_policy" varchar(30),
                           "show_correct_answer" boolean DEFAULT false,
                           "shuffle_questions" boolean DEFAULT false,
                           "shuffle_options" boolean DEFAULT false,
                           "proctoring_enabled" boolean DEFAULT false,
                           "status" varchar(20),
                           "random_subject_tag" varchar(100),
                           "random_difficulty" varchar(10),
                           "random_question_count" int,
                           "published_at" timestamptz,
                           "created_at" timestamptz,
                           "updated_at" timestamptz
);

CREATE TABLE "bank_questions" (
                                  "id" uuid PRIMARY KEY,
                                  "course_id" uuid NOT NULL,
                                  "created_by" uuid NOT NULL,
                                  "subject_tag" varchar(100),
                                  "question_text" text NOT NULL,
                                  "question_type" varchar(20),
                                  "difficulty" varchar(10),
                                  "points" decimal(5,2),
                                  "status" varchar(20) DEFAULT 'ACTIVE',
                                  "used_count" int DEFAULT 0,
                                  "created_at" timestamptz
);

CREATE TABLE "bank_options" (
                                "id" uuid PRIMARY KEY,
                                "bank_question_id" uuid NOT NULL,
                                "option_text" text NOT NULL,
                                "is_correct" boolean,
                                "order_index" int
);

CREATE TABLE "quiz_questions" (
                                  "id" uuid PRIMARY KEY,
                                  "quiz_id" uuid NOT NULL,
                                  "bank_question_id" uuid,
                                  "question_text" text NOT NULL,
                                  "question_type" varchar(20),
                                  "points" decimal(5,2),
                                  "order_index" int,
                                  "explanation" text
);

CREATE TABLE "quiz_options" (
                                "id" uuid PRIMARY KEY,
                                "question_id" uuid NOT NULL,
                                "option_text" text NOT NULL,
                                "is_correct" boolean,
                                "order_index" int
);

CREATE TABLE "quiz_attempts" (
                                 "id" uuid PRIMARY KEY,
                                 "quiz_id" uuid NOT NULL,
                                 "student_id" uuid NOT NULL,
                                 "course_id" uuid NOT NULL,
                                 "attempt_number" int NOT NULL,
                                 "status" varchar(20),
                                 "score" decimal(5,2),
                                 "score_percentage" decimal(5,2),
                                 "is_passed" boolean,
                                 "correct_count" int,
                                 "incorrect_count" int,
                                 "unanswered_count" int,
                                 "question_order" jsonb,
                                 "option_order" jsonb,
                                 "auto_submitted" boolean DEFAULT false,
                                 "proctoring_enabled" boolean DEFAULT false,
                                 "violation_count" int DEFAULT 0,
                                 "started_at" timestamptz,
                                 "submitted_at" timestamptz,
                                 "time_spent_seconds" int,
                                 "ip_address" varchar(45)
);

CREATE TABLE "quiz_attempt_answers" (
                                        "id" uuid PRIMARY KEY,
                                        "attempt_id" uuid NOT NULL,
                                        "question_id" uuid NOT NULL,
                                        "selected_option_ids" jsonb,
                                        "is_correct" boolean,
                                        "points_earned" decimal(5,2),
                                        "answered_at" timestamptz
);

CREATE TABLE "proctoring_violation_logs" (
                                             "id" uuid PRIMARY KEY,
                                             "attempt_id" uuid NOT NULL,
                                             "student_id" uuid NOT NULL,
                                             "violation_type" varchar(30),
                                             "severity" varchar(10),
                                             "violation_order" int,
                                             "action_taken" varchar(20),
                                             "screenshot_s3_key" varchar(500),
                                             "description" text,
                                             "client_timestamp" timestamptz,
                                             "server_timestamp" timestamptz
);

CREATE TABLE "proctoring_configs" (
                                      "id" uuid PRIMARY KEY,
                                      "course_id" uuid UNIQUE NOT NULL,
                                      "max_violations" int DEFAULT 3,
                                      "warning_threshold_1" int,
                                      "warning_threshold_2" int,
                                      "auto_submit_message" text,
                                      "updated_by" uuid,
                                      "updated_at" timestamptz
);

CREATE TABLE "assignments" (
                               "id" uuid PRIMARY KEY,
                               "course_id" uuid NOT NULL,
                               "created_by" uuid NOT NULL,
                               "title" varchar(200) NOT NULL,
                               "description" text,
                               "status" varchar(20),
                               "scope" varchar(20),
                               "deadline" timestamptz,
                               "allow_late_submission" boolean DEFAULT false,
                               "late_penalty_percent" int DEFAULT 0,
                               "max_score" decimal(5,2),
                               "max_file_size_mb" int,
                               "allowed_file_types" jsonb,
                               "max_submissions" int DEFAULT 1,
                               "published_at" timestamptz,
                               "created_at" timestamptz,
                               "updated_at" timestamptz
);

CREATE TABLE "assignment_groups" (
                                     "id" uuid PRIMARY KEY,
                                     "assignment_id" uuid NOT NULL,
                                     "group_id" uuid NOT NULL,
                                     "assigned_at" timestamptz
);

CREATE TABLE "assignment_attachments" (
                                          "id" uuid PRIMARY KEY,
                                          "assignment_id" uuid NOT NULL,
                                          "display_name" varchar(255),
                                          "original_filename" varchar(255),
                                          "s3_key" varchar(500),
                                          "file_size_bytes" bigint,
                                          "mime_type" varchar(100),
                                          "order_index" int,
                                          "uploaded_at" timestamptz
);

CREATE TABLE "assignment_submissions" (
                                          "id" uuid PRIMARY KEY,
                                          "assignment_id" uuid NOT NULL,
                                          "student_id" uuid NOT NULL,
                                          "course_id" uuid NOT NULL,
                                          "submission_number" int NOT NULL DEFAULT 1,
                                          "status" varchar(20),
                                          "note" text,
                                          "is_late" boolean DEFAULT false,
                                          "score" decimal(5,2),
                                          "feedback" text,
                                          "graded_by" uuid,
                                          "graded_at" timestamptz,
                                          "submitted_at" timestamptz,
                                          "created_at" timestamptz
);

CREATE TABLE "submission_files" (
                                    "id" uuid PRIMARY KEY,
                                    "submission_id" uuid NOT NULL,
                                    "original_filename" varchar(255),
                                    "s3_key" varchar(500) NOT NULL,
                                    "file_size_bytes" bigint,
                                    "mime_type" varchar(100),
                                    "extension" varchar(20),
                                    "scan_status" varchar(20),
                                    "scan_threat_name" varchar(255),
                                    "order_index" int,
                                    "uploaded_at" timestamptz
);

CREATE TABLE "forum_posts" (
                               "id" uuid PRIMARY KEY,
                               "course_id" uuid NOT NULL,
                               "author_id" uuid NOT NULL,
                               "title" varchar(200) NOT NULL,
                               "content" text,
                               "is_pinned" boolean DEFAULT false,
                               "reply_count" int DEFAULT 0,
                               "is_deleted" boolean DEFAULT false,
                               "deleted_by" uuid,
                               "deleted_at" timestamptz,
                               "created_at" timestamptz,
                               "updated_at" timestamptz
);

CREATE TABLE "forum_replies" (
                                 "id" uuid PRIMARY KEY,
                                 "post_id" uuid NOT NULL,
                                 "course_id" uuid NOT NULL,
                                 "author_id" uuid NOT NULL,
                                 "content" text,
                                 "is_deleted" boolean DEFAULT false,
                                 "deleted_by" uuid,
                                 "deleted_at" timestamptz,
                                 "created_at" timestamptz,
                                 "updated_at" timestamptz
);

CREATE TABLE "forum_reports" (
                                 "id" uuid PRIMARY KEY,
                                 "target_type" varchar(10),
                                 "target_id" uuid NOT NULL,
                                 "reporter_id" uuid NOT NULL,
                                 "reason" varchar(20),
                                 "description" varchar(500),
                                 "status" varchar(20),
                                 "reviewed_by" uuid,
                                 "reviewed_at" timestamptz,
                                 "created_at" timestamptz
);

CREATE TABLE "chat_rooms" (
                              "id" uuid PRIMARY KEY,
                              "name" varchar(100),
                              "room_type" varchar(20),
                              "course_id" uuid,
                              "group_id" uuid,
                              "created_by" uuid,
                              "is_active" boolean DEFAULT true,
                              "last_message_at" timestamptz,
                              "created_at" timestamptz
);

CREATE TABLE "chat_room_members" (
                                     "id" uuid PRIMARY KEY,
                                     "room_id" uuid NOT NULL,
                                     "user_id" uuid NOT NULL,
                                     "role" varchar(20),
                                     "joined_at" timestamptz,
                                     "last_read_message_id" uuid,
                                     "is_muted" boolean DEFAULT false,
                                     "muted_until" timestamptz
);

CREATE TABLE "chat_messages" (
                                 "id" uuid PRIMARY KEY,
                                 "room_id" uuid NOT NULL,
                                 "sender_id" uuid NOT NULL,
                                 "message_type" varchar(20),
                                 "content" text,
                                 "attachment_url" text,
                                 "attachment_name" varchar(255),
                                 "attachment_size_bytes" bigint,
                                 "reply_to_id" uuid,
                                 "is_edited" boolean DEFAULT false,
                                 "edited_at" timestamptz,
                                 "is_deleted" boolean DEFAULT false,
                                 "deleted_at" timestamptz,
                                 "created_at" timestamptz
);

CREATE TABLE "chat_message_reactions" (
                                          "id" uuid PRIMARY KEY,
                                          "message_id" uuid NOT NULL,
                                          "user_id" uuid NOT NULL,
                                          "emoji" varchar(10),
                                          "created_at" timestamptz
);

CREATE TABLE "ai_conversations" (
                                    "id" uuid PRIMARY KEY,
                                    "student_id" uuid NOT NULL,
                                    "course_id" uuid NOT NULL,
                                    "lesson_id" uuid,
                                    "title" varchar(200),
                                    "status" varchar(20),
                                    "message_count" int,
                                    "created_at" timestamptz,
                                    "last_message_at" timestamptz
);

CREATE TABLE "ai_messages" (
                               "id" uuid PRIMARY KEY,
                               "conversation_id" uuid NOT NULL,
                               "role" varchar(20),
                               "content" text NOT NULL,
                               "llm_provider" varchar(50),
                               "llm_model" varchar(100),
                               "response_time_ms" int,
                               "created_at" timestamptz
);

CREATE TABLE "ai_message_debugs" (
                                     "id" uuid PRIMARY KEY,
                                     "message_id" uuid NOT NULL,
                                     "retrieved_chunks" jsonb,
                                     "prompt_tokens" int,
                                     "completion_tokens" int,
                                     "total_tokens" int,
                                     "created_at" timestamptz
);

CREATE TABLE "outbox_events" (
                                 "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
                                 "event_type" varchar(60) NOT NULL,
                                 "aggregate_type" varchar(40) NOT NULL,
                                 "aggregate_id" uuid NOT NULL,
                                 "payload" jsonb NOT NULL,
                                 "status" varchar(20) NOT NULL DEFAULT 'PENDING',
                                 "retry_count" int NOT NULL DEFAULT 0,
                                 "max_retries" int NOT NULL DEFAULT 3,
                                 "last_error" text,
                                 "scheduled_at" timestamptz NOT NULL DEFAULT (now()),
                                 "created_by" uuid,
                                 "created_at" timestamptz NOT NULL DEFAULT (now()),
                                 "processed_at" timestamptz
);

CREATE TABLE "notifications" (
                                 "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
                                 "recipient_id" uuid NOT NULL,
                                 "idempotency_key" varchar(200) UNIQUE,
                                 "type" varchar(40) NOT NULL,
                                 "title" varchar(200) NOT NULL,
                                 "body" varchar(500),
                                 "reference_type" varchar(30),
                                 "reference_id" uuid,
                                 "actor_id" uuid,
                                 "actor_name" varchar(200),
                                 "priority" varchar(10) NOT NULL DEFAULT 'NORMAL',
                                 "is_read" boolean NOT NULL DEFAULT false,
                                 "read_at" timestamptz,
                                 "email_sent" boolean NOT NULL DEFAULT false,
                                 "email_sent_at" timestamptz,
                                 "push_sent" boolean NOT NULL DEFAULT false,
                                 "push_sent_at" timestamptz,
                                 "expires_at" timestamptz,
                                 "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "notification_preferences" (
                                            "id" uuid PRIMARY KEY,
                                            "user_id" uuid NOT NULL,
                                            "notification_type" varchar(40) NOT NULL,
                                            "in_app_enabled" boolean DEFAULT true,
                                            "email_enabled" boolean DEFAULT true,
                                            "push_enabled" boolean DEFAULT true,
                                            "updated_at" timestamptz
);

CREATE TABLE "certificates" (
                                "id" uuid PRIMARY KEY,
                                "student_id" uuid NOT NULL,
                                "course_id" uuid NOT NULL,
                                "credential_id" varchar(50) UNIQUE NOT NULL,
                                "pdf_s3_key" varchar(500),
                                "pdf_url" text,
                                "status" varchar(20),
                                "revoke_reason" text,
                                "revoked_by" uuid,
                                "revoked_at" timestamptz,
                                "issued_at" timestamptz
);

CREATE UNIQUE INDEX ON "user_preferences" ("user_id", "pref_key");

CREATE UNIQUE INDEX ON "course_enrollments" ("course_id", "student_id");

CREATE INDEX ON "ai_sources" ("course_id");

CREATE INDEX ON "ai_sources" ("lesson_id");

CREATE INDEX ON "ai_sources" ("resource_id");

CREATE INDEX ON "ai_sources" ("source_type");

CREATE INDEX ON "ai_sources" ("ingest_status");

CREATE INDEX ON "ai_ingestion_jobs" ("source_id");

CREATE INDEX ON "ai_ingestion_jobs" ("status");

CREATE INDEX ON "document_chunks" ("course_id");

CREATE INDEX ON "document_chunks" ("source_id");

CREATE INDEX ON "document_chunks" ("chunk_index");

CREATE INDEX idx_document_chunks_embedding
    ON document_chunks
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE INDEX idx_lessons_chapter_id          ON lessons(chapter_id);

CREATE INDEX idx_lesson_progress_course_id   ON lesson_progress(course_id);

CREATE INDEX idx_quiz_attempts_student_id    ON quiz_attempts(student_id);

CREATE INDEX idx_asgn_submissions_asgn_id    ON assignment_submissions(assignment_id);

CREATE INDEX idx_forum_posts_course_active   ON forum_posts(course_id) WHERE is_deleted = false;

CREATE INDEX idx_chat_messages_room_time     ON chat_messages(room_id, created_at DESC);

CREATE INDEX idx_outbox_worker_poll          ON outbox_events(status, scheduled_at) WHERE status IN ('PENDING','PROCESSING');

CREATE INDEX ON "ai_chunk_references" ("chunk_id");

CREATE INDEX ON "ai_chunk_references" ("entity_type");

CREATE INDEX ON "ai_chunk_references" ("entity_id");

CREATE UNIQUE INDEX ON "lesson_progress" ("student_id", "lesson_id");

CREATE UNIQUE INDEX ON "course_progress" ("student_id", "course_id");

CREATE UNIQUE INDEX ON "group_members" ("group_id", "student_id");

CREATE UNIQUE INDEX ON "quiz_attempts" ("quiz_id", "student_id", "attempt_number");

CREATE UNIQUE INDEX ON "assignment_groups" ("assignment_id", "group_id");

CREATE UNIQUE INDEX ON "assignment_submissions" ("assignment_id", "student_id", "submission_number");

CREATE UNIQUE INDEX ON "forum_reports" ("target_type", "target_id", "reporter_id");

CREATE UNIQUE INDEX ON "chat_room_members" ("room_id", "user_id");

CREATE UNIQUE INDEX ON "chat_message_reactions" ("message_id", "user_id", "emoji");

CREATE INDEX ON "ai_messages" ("conversation_id");

CREATE INDEX ON "ai_messages" ("created_at");

-- CREATE INDEX "idx_outbox_worker_poll" ON "outbox_events" ("status", "scheduled_at");

CREATE INDEX "idx_notifications_inbox" ON "notifications" ("recipient_id", "is_read", "created_at");

CREATE UNIQUE INDEX ON "notification_preferences" ("user_id", "notification_type");

CREATE UNIQUE INDEX ON "certificates" ("student_id", "course_id");

COMMENT ON COLUMN "users"."role" IS 'ADMIN | INSTRUCTOR | STUDENT';

COMMENT ON COLUMN "users"."status" IS 'PENDING_ACTIVATION | ACTIVE | DISABLED | DELETED';

COMMENT ON COLUMN "bulk_import_jobs"."status" IS 'PENDING | PROCESSING | DONE | FAILED';

COMMENT ON COLUMN "courses"."level" IS 'BEGINNER | INTERMEDIATE | ADVANCED';

COMMENT ON COLUMN "courses"."status" IS 'DRAFT | PENDING | APPROVED | REJECTED | PUBLISHED | PENDING_UPDATE | ARCHIVED';

COMMENT ON COLUMN "course_approval_logs"."action" IS 'APPROVED | REJECTED | REVERTED';

COMMENT ON COLUMN "course_enrollments"."enrolled_by" IS 'null = tự đăng ký | value = admin enroll';

COMMENT ON COLUMN "lessons"."type" IS 'VIDEO | TEXT | PDF | DOC';

COMMENT ON COLUMN "lessons"."video_status" IS 'NONE | UPLOADING | TRANSCODING | READY | FAILED';

COMMENT ON COLUMN "lesson_resources"."resource_type" IS 'PDF | DOC | SLIDE | IMAGE | OTHER';

COMMENT ON COLUMN "video_upload_jobs"."upload_status" IS 'PENDING | UPLOADING | UPLOADED | TRANSCODING | READY | FAILED';

COMMENT ON COLUMN "document_chunks"."embedding" IS 'pgvector(1024)';

COMMENT ON COLUMN "lesson_progress"."status" IS 'NOT_STARTED | IN_PROGRESS | COMPLETED';

COMMENT ON COLUMN "course_progress"."status" IS 'IN_PROGRESS | COMPLETED';

COMMENT ON COLUMN "quizzes"."creation_mode" IS 'MANUAL | RANDOM';

COMMENT ON COLUMN "quizzes"."show_answers_policy" IS 'NEVER | AFTER_SUBMIT | AFTER_DEADLINE';

COMMENT ON COLUMN "quizzes"."status" IS 'DRAFT | PUBLISHED | CLOSED';

COMMENT ON COLUMN "bank_questions"."question_type" IS 'SINGLE_CHOICE | MULTIPLE_CHOICE';

COMMENT ON COLUMN "bank_questions"."difficulty" IS 'EASY | MEDIUM | HARD';

COMMENT ON COLUMN "quiz_questions"."bank_question_id" IS 'null nếu tạo tay';

COMMENT ON COLUMN "quiz_attempts"."status" IS 'IN_PROGRESS | SUBMITTED | GRADED | EXPIRED';

COMMENT ON COLUMN "quiz_attempts"."question_order" IS 'Snapshot thứ tự câu hỏi khi shuffle';

COMMENT ON COLUMN "quiz_attempts"."option_order" IS 'Snapshot thứ tự đáp án khi shuffle';

COMMENT ON COLUMN "proctoring_violation_logs"."violation_type" IS 'TAB_SWITCH | EXIT_FULLSCREEN | WINDOW_BLUR | FACE_NOT_DETECTED | MULTIPLE_FACES';

COMMENT ON COLUMN "proctoring_violation_logs"."severity" IS 'LOW | MEDIUM | HIGH';

COMMENT ON COLUMN "proctoring_violation_logs"."action_taken" IS 'WARNED | AUTO_SUBMITTED | FLAGGED';

COMMENT ON COLUMN "assignments"."status" IS 'DRAFT | PUBLISHED | CLOSED';

COMMENT ON COLUMN "assignments"."scope" IS 'ALL | GROUP';

COMMENT ON COLUMN "assignment_submissions"."status" IS 'DRAFT | SUBMITTED | LATE | GRADED | RETURNED';

COMMENT ON COLUMN "submission_files"."scan_status" IS 'PENDING | CLEAN | THREAT | ERROR';

COMMENT ON COLUMN "forum_reports"."target_type" IS 'POST | REPLY';

COMMENT ON COLUMN "forum_reports"."reason" IS 'SPAM | OFFENSIVE | MISINFORMATION | OTHER';

COMMENT ON COLUMN "forum_reports"."status" IS 'PENDING | REVIEWED | DISMISSED';

COMMENT ON COLUMN "chat_rooms"."room_type" IS 'COURSE | GROUP | DIRECT';

COMMENT ON COLUMN "chat_room_members"."role" IS 'MEMBER | MODERATOR';

COMMENT ON COLUMN "chat_messages"."message_type" IS 'TEXT | FILE | SYSTEM';

COMMENT ON COLUMN "outbox_events"."event_type" IS 'QUIZ_PUBLISHED | ASSIGNMENT_PUBLISHED | ASSIGNMENT_DEADLINE_REMINDER | SUBMISSION_GRADED | COURSE_APPROVED | COURSE_REJECTED | CERTIFICATE_ISSUED | FORUM_REPLY_ADDED | ENROLLMENT_CONFIRMED';

COMMENT ON COLUMN "outbox_events"."aggregate_type" IS 'QUIZ | ASSIGNMENT | COURSE | CERTIFICATE | FORUM_POST | ...';

COMMENT ON COLUMN "outbox_events"."payload" IS '{"course_id": "...","title": "...","recipient_scope": "ALL_ENROLLED",  // hoặc"recipient_ids": ["uuid1", "uuid2"], // hoặc"group_id": "..."}';

COMMENT ON COLUMN "outbox_events"."status" IS 'PENDING | PROCESSING | PROCESSED | FAILED | DEAD';

COMMENT ON COLUMN "notifications"."reference_type" IS 'COURSE | QUIZ | ASSIGNMENT | FORUM_POST | CERTIFICATE';

COMMENT ON COLUMN "notifications"."priority" IS 'LOW | NORMAL | HIGH';

COMMENT ON COLUMN "certificates"."status" IS 'ISSUED | REVOKED';

ALTER TABLE "users" ADD FOREIGN KEY ("disabled_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "users" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "user_preferences" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "bulk_import_jobs" ADD FOREIGN KEY ("uploaded_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "audit_logs" ADD FOREIGN KEY ("actor_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "courses" ADD FOREIGN KEY ("instructor_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "courses" ADD FOREIGN KEY ("category_id") REFERENCES "course_categories" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "course_approval_logs" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "course_approval_logs" ADD FOREIGN KEY ("admin_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "course_enrollments" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "course_enrollments" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "course_enrollments" ADD FOREIGN KEY ("enrolled_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chapters" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "lessons" ADD FOREIGN KEY ("chapter_id") REFERENCES "chapters" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "lessons" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "lesson_resources" ADD FOREIGN KEY ("lesson_id") REFERENCES "lessons" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "lesson_resources" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "lesson_resources" ADD FOREIGN KEY ("uploaded_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "video_upload_jobs" ADD FOREIGN KEY ("lesson_id") REFERENCES "lessons" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "video_upload_jobs" ADD FOREIGN KEY ("instructor_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_sources" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_sources" ADD FOREIGN KEY ("lesson_id") REFERENCES "lessons" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_sources" ADD FOREIGN KEY ("resource_id") REFERENCES "lesson_resources" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_sources" ADD FOREIGN KEY ("uploaded_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_ingestion_jobs" ADD FOREIGN KEY ("source_id") REFERENCES "ai_sources" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "document_chunks" ADD FOREIGN KEY ("source_id") REFERENCES "ai_sources" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "document_chunks" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_chunk_references" ADD FOREIGN KEY ("chunk_id") REFERENCES "document_chunks" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "lesson_progress" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "lesson_progress" ADD FOREIGN KEY ("lesson_id") REFERENCES "lessons" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "lesson_progress" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "course_progress" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "course_progress" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "study_groups" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "study_groups" ADD FOREIGN KEY ("instructor_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "group_members" ADD FOREIGN KEY ("group_id") REFERENCES "study_groups" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "group_members" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quizzes" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quizzes" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "bank_questions" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "bank_questions" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "bank_options" ADD FOREIGN KEY ("bank_question_id") REFERENCES "bank_questions" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quiz_questions" ADD FOREIGN KEY ("quiz_id") REFERENCES "quizzes" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quiz_questions" ADD FOREIGN KEY ("bank_question_id") REFERENCES "bank_questions" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quiz_options" ADD FOREIGN KEY ("question_id") REFERENCES "quiz_questions" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quiz_attempts" ADD FOREIGN KEY ("quiz_id") REFERENCES "quizzes" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quiz_attempts" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quiz_attempts" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quiz_attempt_answers" ADD FOREIGN KEY ("attempt_id") REFERENCES "quiz_attempts" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "quiz_attempt_answers" ADD FOREIGN KEY ("question_id") REFERENCES "quiz_questions" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proctoring_violation_logs" ADD FOREIGN KEY ("attempt_id") REFERENCES "quiz_attempts" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proctoring_violation_logs" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proctoring_configs" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proctoring_configs" ADD FOREIGN KEY ("updated_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "assignments" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "assignments" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "assignment_groups" ADD FOREIGN KEY ("assignment_id") REFERENCES "assignments" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "assignment_groups" ADD FOREIGN KEY ("group_id") REFERENCES "study_groups" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "assignment_attachments" ADD FOREIGN KEY ("assignment_id") REFERENCES "assignments" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "assignment_submissions" ADD FOREIGN KEY ("assignment_id") REFERENCES "assignments" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "assignment_submissions" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "assignment_submissions" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "assignment_submissions" ADD FOREIGN KEY ("graded_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "submission_files" ADD FOREIGN KEY ("submission_id") REFERENCES "assignment_submissions" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "forum_posts" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "forum_posts" ADD FOREIGN KEY ("author_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "forum_posts" ADD FOREIGN KEY ("deleted_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "forum_replies" ADD FOREIGN KEY ("post_id") REFERENCES "forum_posts" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "forum_replies" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "forum_replies" ADD FOREIGN KEY ("author_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "forum_replies" ADD FOREIGN KEY ("deleted_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "forum_reports" ADD FOREIGN KEY ("reporter_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "forum_reports" ADD FOREIGN KEY ("reviewed_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_rooms" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_rooms" ADD FOREIGN KEY ("group_id") REFERENCES "study_groups" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_rooms" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_room_members" ADD FOREIGN KEY ("room_id") REFERENCES "chat_rooms" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_room_members" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_room_members" ADD FOREIGN KEY ("last_read_message_id") REFERENCES "chat_messages" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_messages" ADD FOREIGN KEY ("room_id") REFERENCES "chat_rooms" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_messages" ADD FOREIGN KEY ("sender_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_messages" ADD FOREIGN KEY ("reply_to_id") REFERENCES "chat_messages" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_message_reactions" ADD FOREIGN KEY ("message_id") REFERENCES "chat_messages" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "chat_message_reactions" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_conversations" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_conversations" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_conversations" ADD FOREIGN KEY ("lesson_id") REFERENCES "lessons" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_messages" ADD FOREIGN KEY ("conversation_id") REFERENCES "ai_conversations" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ai_message_debugs" ADD FOREIGN KEY ("message_id") REFERENCES "ai_messages" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "outbox_events" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "notifications" ADD FOREIGN KEY ("recipient_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "notifications" ADD FOREIGN KEY ("actor_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "notification_preferences" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "certificates" ADD FOREIGN KEY ("student_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "certificates" ADD FOREIGN KEY ("course_id") REFERENCES "courses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "certificates" ADD FOREIGN KEY ("revoked_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

-- ============================================================
-- HYBRID VERSIONING MIGRATION
-- Thêm draft fields cho courses, chapters, lessons
-- ============================================================

ALTER TABLE "courses"
    ADD COLUMN IF NOT EXISTS "draft_title"             varchar(200),
    ADD COLUMN IF NOT EXISTS "draft_description"       text,
    ADD COLUMN IF NOT EXISTS "draft_thumbnail_url"     text,
    ADD COLUMN IF NOT EXISTS "draft_level"             varchar(20),
    ADD COLUMN IF NOT EXISTS "change_summary"          varchar(500),
    ADD COLUMN IF NOT EXISTS "draft_rejection_reason"  text;

ALTER TABLE "chapters"
    ADD COLUMN IF NOT EXISTS "is_draft"       boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS "pending_delete" boolean NOT NULL DEFAULT false;

ALTER TABLE "lessons"
    ADD COLUMN IF NOT EXISTS "is_draft"            boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS "pending_delete"      boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS "draft_title"         varchar(200),
    ADD COLUMN IF NOT EXISTS "draft_content_text"  text;

CREATE INDEX IF NOT EXISTS idx_chapters_is_draft        ON chapters(course_id) WHERE is_draft = true;
CREATE INDEX IF NOT EXISTS idx_lessons_is_draft         ON lessons(course_id) WHERE is_draft = true;
CREATE INDEX IF NOT EXISTS idx_lessons_pending_delete   ON lessons(course_id) WHERE pending_delete = true;
