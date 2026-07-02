package project.lms_rikkei_edu.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * One turn of a RAG-powered conversation.
 *
 * <p>Pass {@code conversationId} to continue an existing session;
 * omit it (or pass null) to start a new conversation.
 *
 * <p>{@code courseId} is optional for instructors — omit to let the AI
 * answer across all courses they manage.
 */
public record ChatRequest(

        /**
         * The caller's user ID. Ignored if sent by the client — the
         * controller always overrides this with the authenticated user's ID
         * before calling the service, so this field is never trusted as-is.
         */
        UUID userId,

        /** Optional: scope RAG retrieval to a specific course. */
        UUID courseId,

        /** Null → create a new conversation; non-null → append to existing one. */
        UUID conversationId,

        /** Optional: scopes retrieved chunks to the current lesson. */
        UUID lessonId,

        @NotBlank String message
) {}
