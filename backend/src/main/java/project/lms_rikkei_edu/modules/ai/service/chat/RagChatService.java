package project.lms_rikkei_edu.modules.ai.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;
import project.lms_rikkei_edu.modules.ai.dto.request.ChatRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.ChatResponse;
import project.lms_rikkei_edu.modules.ai.service.context.UserContext;
import project.lms_rikkei_edu.modules.ai.service.context.UserContextService;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceReference;
import project.lms_rikkei_edu.modules.ai.entity.AiConversation;
import project.lms_rikkei_edu.modules.ai.entity.AiMessage;
import project.lms_rikkei_edu.modules.ai.entity.AiMessageDebug;
import project.lms_rikkei_edu.modules.ai.entity.enums.ConversationStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.MessageRole;
import project.lms_rikkei_edu.modules.ai.repository.AiConversationRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageDebugRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.service.embedding.EmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.llm.ChatMessage;
import project.lms_rikkei_edu.modules.ai.service.llm.LlmResponse;
import project.lms_rikkei_edu.modules.ai.service.llm.LlmService;
import project.lms_rikkei_edu.modules.ai.service.retrieval.ScoredChunk;
import project.lms_rikkei_edu.modules.ai.exception.ConversationNotFoundException;
import project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Main RAG pipeline for course-aware Q&A.
 *
 * <pre>
 * Request
 *   → embed(question)
 *   → vector-search(course, topK)
 *   → build system prompt with context chunks
 *   → LLM completion with conversation history
 *   → persist messages + debug info
 *   → return ChatResponse with source attributions
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private static final String LLM_PROVIDER = "openai";

    private final AiConversationRepository  conversationRepo;
    private final AiMessageRepository       messageRepo;
    private final AiMessageDebugRepository  debugRepo;
    private final AiSourceRepository        sourceRepo;
    private final EmbeddingService          embeddingService;
    private final VectorSearchService       vectorSearch;
    private final LlmService                llmService;
    private final OpenAiProperties          props;
    private final ObjectMapper              objectMapper;
    private final UserContextService        userContextService;

    @Transactional
    public ChatResponse chat(ChatRequest req) {
        // 1. Load user context (role, courses, deadlines, groups)
        UserContext userCtx = userContextService.load(req.userId());

        // 2. Load or create conversation
        AiConversation conversation = resolveConversation(req, userCtx);

        // 3. Load recent message history (oldest first)
        List<AiMessage> history = loadHistory(conversation.getId());
        List<ChatMessage> llmHistory = toLlmHistory(history);

        // 4. Retrieve relevant chunks — scope to requested course or first enrolled/owned course
        float[] queryEmbedding = embeddingService.embed(req.message());
        UUID searchCourseId = resolveSearchCourse(req, userCtx);
        List<ScoredChunk> chunks = (searchCourseId != null)
                ? vectorSearch.search(searchCourseId, queryEmbedding, props.getTopK(), props.getSimilarityThreshold())
                : List.of();

        log.debug("Retrieved {} chunks for courseId={} userId={}", chunks.size(), searchCourseId, req.userId());

        // 5. Build role-aware system prompt with RAG context
        String systemPrompt = buildSystemPrompt(chunks, userCtx);

        // 6. Call LLM
        LlmResponse llmResp = llmService.complete(systemPrompt, llmHistory, req.message());

        // 7. Persist messages
        AiMessage userMsg = saveMessage(conversation, MessageRole.USER, req.message(), null, null, null);
        AiMessage assistantMsg = saveMessage(
                conversation, MessageRole.ASSISTANT, llmResp.content(),
                props.getChatModel(), (int) llmResp.responseTimeMs(), LLM_PROVIDER);

        // 8. Update conversation metadata
        conversation.setLastMessageAt(OffsetDateTime.now());
        conversation.setMessageCount((conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 2);
        conversationRepo.save(conversation);

        // 9. Persist debug info
        saveDebug(assistantMsg.getId(), chunks, llmResp);

        // 10. Build response with source attributions
        List<SourceReference> sources = buildSourceReferences(chunks);

        return new ChatResponse(
                conversation.getId(),
                assistantMsg.getId(),
                llmResp.content(),
                sources,
                llmResp.totalTokens()
        );
    }

    // ── conversation helpers ──────────────────────────────────────────────────

    private AiConversation resolveConversation(ChatRequest req, UserContext ctx) {
        if (req.conversationId() != null) {
            return conversationRepo.findById(req.conversationId())
                    .orElseThrow(() -> new ConversationNotFoundException(req.conversationId()));
        }
        UUID courseId = resolveSearchCourse(req, ctx);
        AiConversation conv = AiConversation.builder()
                .studentId(req.userId())
                .courseId(courseId)
                .lessonId(req.lessonId())
                .title(truncate(req.message(), 200))
                .status(ConversationStatus.ACTIVE)
                .messageCount(0)
                .createdAt(OffsetDateTime.now())
                .lastMessageAt(OffsetDateTime.now())
                .build();
        return conversationRepo.save(conv);
    }

    /** Returns the course to scope vector search: explicit courseId > first enrolled/owned course > null. */
    private UUID resolveSearchCourse(ChatRequest req, UserContext ctx) {
        if (req.courseId() != null) return req.courseId();
        if (!ctx.courses().isEmpty()) return ctx.courses().get(0).courseId();
        return null;
    }

    private List<AiMessage> loadHistory(UUID conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, props.getMaxHistoryMessages()))
                .reversed();
    }

    private List<ChatMessage> toLlmHistory(List<AiMessage> messages) {
        return messages.stream()
                .map(m -> new ChatMessage(m.getRole().name().toLowerCase(), m.getContent()))
                .toList();
    }

    // ── prompt building ───────────────────────────────────────────────────────

    private String buildSystemPrompt(List<ScoredChunk> chunks, UserContext ctx) {
        StringBuilder sb = new StringBuilder();

        switch (ctx.role()) {
            case INSTRUCTOR -> sb.append("""
                    Bạn là trợ lý AI thông minh cho Giảng viên của hệ thống LMS Rikkei Edu.
                    Hỗ trợ giảng viên về nội dung khóa học, tiến độ học viên, deadline bài tập và nhóm học.
                    Trả lời bằng tiếng Việt, chuyên nghiệp và rõ ràng.
                    """);
            case ADMIN -> sb.append("""
                    Bạn là trợ lý AI quản trị hệ thống LMS Rikkei Edu.
                    Hỗ trợ admin tra cứu thống kê, quản lý người dùng, khóa học và hệ thống.
                    Trả lời bằng tiếng Việt, ngắn gọn và chính xác với số liệu thực tế.
                    """);
            default -> sb.append("""
                    Bạn là AI Tutor của hệ thống LMS Rikkei Edu.
                    Hỗ trợ học viên giải đáp thắc mắc về nội dung bài học, deadline và tiến độ học tập.
                    Trả lời dựa trên tài liệu khóa học. Nếu không có trong tài liệu, nói rõ thay vì đoán.
                    Trả lời bằng tiếng Việt, thân thiện và khuyến khích.
                    """);
        }

        sb.append("\n=== THÔNG TIN NGƯỜI DÙNG ===\n");
        sb.append("Tên: ").append(ctx.fullName()).append("\n");
        sb.append("Vai trò: ").append(switch (ctx.role()) {
            case STUDENT    -> "Học viên";
            case INSTRUCTOR -> "Giảng viên";
            case ADMIN      -> "Quản trị viên";
        }).append("\n");

        // ── STUDENT sections ──────────────────────────────────────────────────
        if (ctx.role() == UserContext.UserRole.STUDENT) {
            if (!ctx.courses().isEmpty()) {
                sb.append("\nKHÓA HỌC ĐANG HỌC:\n");
                for (var c : ctx.courses()) {
                    sb.append("  - ").append(c.title())
                      .append(" — tiến độ: ").append(String.format("%.1f", c.progressPct())).append("%")
                      .append(" [").append(c.progressStatus()).append("]\n");
                }
            }

            if (!ctx.recentLessons().isEmpty()) {                                   // A1
                sb.append("\nBÀI HỌC GẦN NHẤT:\n");
                for (var l : ctx.recentLessons()) {
                    sb.append("  - ").append(l.lessonTitle())
                      .append(" (").append(l.chapterTitle()).append(" / ").append(l.courseName()).append(")")
                      .append(" — ").append(l.status())
                      .append(" — ").append(l.lastAccessedAt()).append("\n");
                }
            }

            if (!ctx.recentQuizResults().isEmpty()) {                               // A2
                sb.append("\nKẾT QUẢ QUIZ GẦN NHẤT:\n");
                for (var q : ctx.recentQuizResults()) {
                    sb.append("  - ").append(q.quizTitle())
                      .append(" [").append(q.courseName()).append("]")
                      .append(" — ").append(q.score()).append("/").append(q.maxScore())
                      .append(q.isPassed() ? " ✓ ĐẠT" : " ✗ CHƯA ĐẠT")
                      .append(" — ").append(q.submittedAt()).append("\n");
                }
            }

            if (!ctx.unsubmittedAssignments().isEmpty()) {                          // A3
                sb.append("\nBÀI TẬP CHƯA NỘP:\n");
                for (var a : ctx.unsubmittedAssignments()) {
                    sb.append("  - ").append(a.assignmentTitle())
                      .append(" [").append(a.courseName()).append("]")
                      .append(" — hạn: ").append(a.deadline());
                    if (a.isOverdue()) sb.append(" ⚠️ ĐÃ QUÁ HẠN");
                    sb.append("\n");
                }
            }

            if (!ctx.groups().isEmpty()) {
                sb.append("\nNHÓM HỌC:\n");
                for (var g : ctx.groups()) {
                    sb.append("  - ").append(g.groupName())
                      .append(" (").append(g.courseName()).append(")\n");
                }
            }

            if (!ctx.upcomingDeadlines().isEmpty()) {
                sb.append("\nDEADLINE SẮP ĐẾN:\n");
                for (var d : ctx.upcomingDeadlines()) {
                    sb.append("  - [").append(d.courseName()).append("] ")
                      .append(d.assignmentTitle())
                      .append(" — hạn: ").append(d.deadline());
                    if (d.isLate()) sb.append(" ⚠️ ĐÃ QUÁ HẠN");
                    sb.append("\n");
                }
            }
        }

        // ── INSTRUCTOR sections ───────────────────────────────────────────────
        if (ctx.role() == UserContext.UserRole.INSTRUCTOR) {
            if (!ctx.courses().isEmpty()) {
                sb.append("\nKHÓA HỌC ĐANG QUẢN LÝ:\n");
                for (var c : ctx.courses()) {
                    sb.append("  - ").append(c.title())
                      .append(" [").append(c.progressStatus()).append("]")
                      .append(" (").append(c.progressPct().intValue()).append(" học viên)\n");
                }
            }

            if (!ctx.groups().isEmpty()) {
                sb.append("\nNHÓM ĐANG QUẢN LÝ:\n");
                for (var g : ctx.groups()) {
                    sb.append("  - ").append(g.groupName())
                      .append(" (").append(g.courseName()).append(")\n");
                }
            }

            if (!ctx.upcomingDeadlines().isEmpty()) {
                sb.append("\nBÀI TẬP SẮP ĐẾN HẠN:\n");
                for (var d : ctx.upcomingDeadlines()) {
                    sb.append("  - [").append(d.courseName()).append("] ")
                      .append(d.assignmentTitle())
                      .append(" — hạn: ").append(d.deadline());
                    if (d.isLate()) sb.append(" ⚠️ ĐÃ QUÁ HẠN");
                    sb.append("\n");
                }
            }

            if (!ctx.submissionGaps().isEmpty()) {                                  // A4
                sb.append("\nTỈNH TRẠNG NỘP BÀI:\n");
                for (var s : ctx.submissionGaps()) {
                    sb.append("  - ").append(s.assignmentTitle())
                      .append(" [").append(s.courseName()).append("]")
                      .append(" — đã nộp: ").append(s.submitted()).append("/").append(s.totalEnrolled())
                      .append(", chưa nộp: ").append(s.notSubmitted())
                      .append(" — hạn: ").append(s.deadline()).append("\n");
                }
            }

            if (!ctx.atRiskStudents().isEmpty()) {                                  // A5
                sb.append("\nHỌC VIÊN CÓ TIẾN ĐỘ THẤP (< 20%):\n");
                for (var s : ctx.atRiskStudents()) {
                    sb.append("  - ").append(s.studentName())
                      .append(" — ").append(s.courseName())
                      .append(" — tiến độ: ").append(String.format("%.1f", s.progressPct())).append("%")
                      .append(" — đã đăng ký ").append(s.daysEnrolled()).append(" ngày\n");
                }
            }
        }

        // ── ADMIN sections ────────────────────────────────────────────────────
        if (ctx.role() == UserContext.UserRole.ADMIN && ctx.adminStats() != null) { // A6
            var s = ctx.adminStats();
            sb.append("\nTHỐNG KÊ HỆ THỐNG:\n");
            sb.append("  - Tổng người dùng: ").append(s.totalUsers())
              .append(" (").append(s.totalStudents()).append(" học viên, ")
              .append(s.totalInstructors()).append(" giảng viên)\n");
            sb.append("  - Khóa học: ").append(s.totalCourses())
              .append(" (đã xuất bản: ").append(s.publishedCourses()).append(")\n");
            sb.append("  - Tổng đăng ký: ").append(s.totalEnrollments()).append("\n");
            sb.append("  - Cuộc hội thoại AI đang active: ").append(s.activeConversations()).append("\n");
        }

        // ── RAG document context ──────────────────────────────────────────────
        if (!chunks.isEmpty()) {
            sb.append("\n=== TÀI LIỆU KHÓA HỌC ===\n");
            for (int i = 0; i < chunks.size(); i++) {
                ScoredChunk c = chunks.get(i);
                sb.append("\n[").append(i + 1).append("]");
                if (c.sectionTitle() != null) sb.append(" ").append(c.sectionTitle());
                sb.append("\n").append(c.chunkText()).append("\n");
            }
            sb.append("=== HẾT TÀI LIỆU ===\n");
        } else {
            sb.append("\nChưa có tài liệu khóa học được index cho câu hỏi này.\n");
        }

        return sb.toString();
    }

    // ── persistence helpers ───────────────────────────────────────────────────

    private AiMessage saveMessage(AiConversation conv, MessageRole role, String content,
                                  String model, Integer responseTimeMs, String provider) {
        AiMessage msg = AiMessage.builder()
                .conversationId(conv.getId())
                .role(role)
                .content(content)
                .llmModel(model)
                .llmProvider(provider)
                .responseTimeMs(responseTimeMs)
                .createdAt(OffsetDateTime.now())
                .build();
        return messageRepo.save(msg);
    }

    private void saveDebug(UUID messageId, List<ScoredChunk> chunks, LlmResponse llmResp) {
        try {
            String chunksJson = objectMapper.writeValueAsString(
                    chunks.stream().map(c -> Map.of(
                            "chunkId", c.chunkId(),
                            "similarity", c.similarity(),
                            "excerpt", truncate(c.chunkText(), 200)
                    )).toList()
            );
            AiMessageDebug debug = AiMessageDebug.builder()
                    .messageId(messageId)
                    .retrievedChunks(chunksJson)
                    .promptTokens(llmResp.promptTokens())
                    .completionTokens(llmResp.completionTokens())
                    .totalTokens(llmResp.totalTokens())
                    .createdAt(OffsetDateTime.now())
                    .build();
            debugRepo.save(debug);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize debug info for messageId={}", messageId, e);
        }
    }

    // ── response mapping ──────────────────────────────────────────────────────

    private List<SourceReference> buildSourceReferences(List<ScoredChunk> chunks) {
        return chunks.stream()
                .map(c -> new SourceReference(
                        c.chunkId(),
                        null, // source name loaded separately if needed
                        c.sectionTitle(),
                        truncate(c.chunkText(), 200),
                        c.similarity()
                ))
                .toList();
    }

    // ── utils ─────────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
