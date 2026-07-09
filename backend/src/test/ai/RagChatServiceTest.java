package project.lms_rikkei_edu.modules.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;
import project.lms_rikkei_edu.modules.ai.dto.request.ChatRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.ChatResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.UiRender;
import project.lms_rikkei_edu.modules.ai.entity.AiConversation;
import project.lms_rikkei_edu.modules.ai.entity.AiMessage;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.repository.AiConversationRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageDebugRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.service.chat.RagChatService;
import project.lms_rikkei_edu.modules.ai.service.context.UserContext;
import project.lms_rikkei_edu.modules.ai.service.context.UserContextService;
import project.lms_rikkei_edu.modules.ai.service.embedding.EmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.llm.LlmResponse;
import project.lms_rikkei_edu.modules.ai.service.llm.LlmService;
import project.lms_rikkei_edu.modules.ai.service.retrieval.ScoredChunk;
import project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentCaptor;
import project.lms_rikkei_edu.modules.ai.exception.ConversationNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatServiceTest {

    private AiConversationRepository conversationRepo;
    private AiMessageRepository messageRepo;
    private AiMessageDebugRepository debugRepo;
    private AiSourceRepository sourceRepo;
    private EmbeddingService embeddingService;
    private VectorSearchService vectorSearch;
    private LlmService llmService;
    private UserContextService userContextService;
    private RagChatService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        conversationRepo = mock(AiConversationRepository.class);
        messageRepo = mock(AiMessageRepository.class);
        debugRepo = mock(AiMessageDebugRepository.class);
        sourceRepo = mock(AiSourceRepository.class);
        embeddingService = mock(EmbeddingService.class);
        vectorSearch = mock(VectorSearchService.class);
        llmService = mock(LlmService.class);
        userContextService = mock(UserContextService.class);

        service = new RagChatService(
                conversationRepo, messageRepo, debugRepo, sourceRepo,
                embeddingService, vectorSearch, llmService,
                new OpenAiProperties(), new ObjectMapper(), userContextService);

        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f});
        when(vectorSearch.search(any(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        when(messageRepo.findByConversationIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(conversationRepo.save(any())).thenAnswer(inv -> {
            AiConversation c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(messageRepo.save(any())).thenAnswer(inv -> {
            AiMessage m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });
        when(llmService.complete(any(), any(), any()))
                .thenReturn(new LlmResponse("Câu trả lời", 10, 5, 15, 100, null));
    }

    private UserContext instructorContext(List<UserContext.CourseInfo> courses) {
        return new UserContext(userId, "Nguyễn Văn Minh", UserContext.UserRole.INSTRUCTOR,
                courses, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    private UserContext studentContext(List<UserContext.CourseInfo> courses) {
        return new UserContext(userId, "Học viên", UserContext.UserRole.STUDENT,
                courses, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    private UserContext adminContext() {
        return new UserContext(userId, "Admin", UserContext.UserRole.ADMIN,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    @Test
    void instructorAskingAboutCourses_getsCourseListStructuredData() {
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(UUID.randomUUID(), "Spring Boot Microservices", "DRAFT", 0.0),
                new UserContext.CourseInfo(UUID.randomUUID(), "Docker & Kubernetes", "PUBLISHED", 3.0));
        when(userContextService.load(userId)).thenReturn(instructorContext(courses));

        ChatResponse resp = service.chat(new ChatRequest(userId, null, null, null, "Tôi có bao nhiêu khóa học?"));

        assertThat(resp.structuredData()).isNotNull();
        assertThat(resp.structuredData().type()).isEqualTo("COURSE_LIST");
        assertThat(resp.structuredData().items()).hasSize(2);
        assertThat(resp.structuredData().items().get(0).title()).isEqualTo("Spring Boot Microservices");
    }

    @Test
    void instructorAskingUnrelatedQuestion_getsNoStructuredData() {
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(UUID.randomUUID(), "Spring Boot Microservices", "DRAFT", 0.0));
        when(userContextService.load(userId)).thenReturn(instructorContext(courses));

        ChatResponse resp = service.chat(new ChatRequest(userId, null, null, null, "Deadline bài tập sắp tới là khi nào?"));

        assertThat(resp.structuredData()).isNull();
    }

    @Test
    void instructorAskingAboutOneSpecificCoursesChapters_getsNoCourseListTable() {
        // Regression test: "khóa học DEVVVVVVVV có bao nhiêu chương" mentions
        // "khóa học" but is asking about ONE course's chapters, not requesting
        // the course list — must not attach the COURSE_LIST table.
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(UUID.randomUUID(), "DEVVVVVVVV", "PUBLISHED", 0.0),
                new UserContext.CourseInfo(UUID.randomUUID(), "TEsst1", "DRAFT", 0.0));
        when(userContextService.load(userId)).thenReturn(instructorContext(courses));

        ChatResponse resp = service.chat(new ChatRequest(
                userId, null, null, null, "Khóa học DEVVVVVVVV đang có bao nhiêu chương?"));

        assertThat(resp.structuredData()).isNull();
    }

    @Test
    void studentAskingAboutCourses_getsNoStructuredData() {
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(UUID.randomUUID(), "Spring Boot Microservices", "IN_PROGRESS", 45.0));
        when(userContextService.load(userId)).thenReturn(studentContext(courses));

        ChatResponse resp = service.chat(new ChatRequest(userId, null, null, null, "Tôi có bao nhiêu khóa học?"));

        assertThat(resp.structuredData()).isNull();
    }

    @Test
    void uiRenderFromLlm_isPassedThroughToChatResponse() {
        when(userContextService.load(userId)).thenReturn(instructorContext(List.of()));
        UiRender uiRender = new UiRender("table", Map.of("columns", List.of("Quiz"), "rows", List.of()));
        when(llmService.complete(any(), any(), any()))
                .thenReturn(new LlmResponse("Đây là bảng thống kê quiz", 10, 5, 15, 100, uiRender));

        ChatResponse resp = service.chat(new ChatRequest(userId, null, null, null, "Quiz nào học viên làm kém nhất?"));

        assertThat(resp.uiRender()).isEqualTo(uiRender);
    }

    @Test
    void noToolCallFromLlm_leavesUiRenderNull() {
        when(userContextService.load(userId)).thenReturn(instructorContext(List.of()));

        ChatResponse resp = service.chat(new ChatRequest(userId, null, null, null, "Xin chào"));

        assertThat(resp.uiRender()).isNull();
    }

    // ── course access control ─────────────────────────────────────────────────

    @Test
    void courseIdOutsideUsersCourses_returnsAnswerWithoutDocuments() {
        UUID ownCourseId = UUID.randomUUID();
        UUID otherCourseId = UUID.randomUUID();
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(ownCourseId, "Khóa của tôi", "IN_PROGRESS", 10.0));
        when(userContextService.load(userId)).thenReturn(studentContext(courses));

        ChatResponse resp = service.chat(new ChatRequest(
                userId, otherCourseId, null, null, "Tài liệu nói gì về K-means?"));

        // Never searches the course they don't belong to — but system-wide docs are still searched.
        verify(vectorSearch, never()).search(eq(otherCourseId), any(), anyInt(), anyDouble());
        verify(vectorSearch).search(isNull(), any(), anyInt(), anyDouble());
        assertThat(resp).isNotNull();
        assertThat(resp.sources()).isEmpty();
    }

    @Test
    void adminCanQueryAnyCourse_bypassesAccessCheck() {
        UUID anyCourseId = UUID.randomUUID();
        when(userContextService.load(userId)).thenReturn(adminContext());

        service.chat(new ChatRequest(userId, anyCourseId, null, null, "Khóa này có bao nhiêu học viên?"));

        verify(vectorSearch).search(eq(anyCourseId), any(), anyInt(), anyDouble());
    }

    @Test
    void sourceReferences_includeCourseNameAndSourceName() {
        UUID ownCourseId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(ownCourseId, "Khóa X", "IN_PROGRESS", 10.0));
        when(userContextService.load(userId)).thenReturn(studentContext(courses));
        when(vectorSearch.search(eq(ownCourseId), any(), anyInt(), anyDouble())).thenReturn(List.of(
                new ScoredChunk(chunkId, sourceId, ownCourseId, 0, "Chương 1", "Nội dung...", 0.9)));
        when(sourceRepo.findAllById(any())).thenReturn(List.of(
                AiSource.builder().id(sourceId).sourceName("Slide.pdf").build()));

        ChatResponse resp = service.chat(new ChatRequest(
                userId, ownCourseId, null, null, "Chương 1 nói gì?"));

        assertThat(resp.sources()).hasSize(1);
        assertThat(resp.sources().get(0).sourceName()).isEqualTo("Slide.pdf");
        assertThat(resp.sources().get(0).courseName()).isEqualTo("Khóa X");
        assertThat(resp.sources().get(0).courseId()).isEqualTo(ownCourseId);
    }

    // ── system-wide documents ────────────────────────────────────────────────

    @Test
    void systemDocsAreMergedIntoAnswer_orderedBySimilarity() {
        UUID ownCourseId = UUID.randomUUID();
        UUID courseSourceId = UUID.randomUUID();
        UUID systemSourceId = UUID.randomUUID();
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(ownCourseId, "Khóa X", "IN_PROGRESS", 10.0));
        when(userContextService.load(userId)).thenReturn(studentContext(courses));
        when(vectorSearch.search(eq(ownCourseId), any(), anyInt(), anyDouble())).thenReturn(List.of(
                new ScoredChunk(UUID.randomUUID(), courseSourceId, ownCourseId, 0, null, "Nội dung khóa học", 0.6)));
        when(vectorSearch.search(isNull(), any(), anyInt(), anyDouble())).thenReturn(List.of(
                new ScoredChunk(UUID.randomUUID(), systemSourceId, null, 0, null, "Quy chế hệ thống", 0.9)));
        when(sourceRepo.findAllById(any())).thenReturn(List.of(
                AiSource.builder().id(courseSourceId).sourceName("Course.pdf").build(),
                AiSource.builder().id(systemSourceId).sourceName("System.pdf").build()));

        ChatResponse resp = service.chat(new ChatRequest(
                userId, ownCourseId, null, null, "Quy định chung là gì?"));

        assertThat(resp.sources()).hasSize(2);
        // Higher-similarity system doc (0.9) must come first.
        assertThat(resp.sources().get(0).sourceName()).isEqualTo("System.pdf");
        assertThat(resp.sources().get(0).courseName()).isNull();
        assertThat(resp.sources().get(1).sourceName()).isEqualTo("Course.pdf");
    }

    @Test
    void systemDocsAreSearched_evenWhenNoCourseScope() {
        when(userContextService.load(userId)).thenReturn(instructorContext(List.of()));

        service.chat(new ChatRequest(userId, null, null, null, "Chính sách nghỉ phép là gì?"));

        verify(vectorSearch).search(isNull(), any(), anyInt(), anyDouble());
    }

    // ── resolveConversation ───────────────────────────────────────────────────

    @Test
    void existingConversationId_reusesConversation_doesNotCreateNew() {
        UUID conversationId = UUID.randomUUID();
        AiConversation existing = AiConversation.builder().id(conversationId).studentId(userId)
                .messageCount(4).createdAt(OffsetDateTime.now()).lastMessageAt(OffsetDateTime.now()).build();
        when(userContextService.load(userId)).thenReturn(studentContext(List.of()));
        when(conversationRepo.findById(conversationId)).thenReturn(java.util.Optional.of(existing));

        ChatResponse resp = service.chat(new ChatRequest(userId, null, conversationId, null, "Tiếp tục câu hỏi"));

        assertThat(resp.conversationId()).isEqualTo(conversationId);
        verify(conversationRepo, never()).save(argThat(c -> c.getId() == null));
    }

    @Test
    void unknownConversationId_throwsConversationNotFoundException() {
        UUID conversationId = UUID.randomUUID();
        when(userContextService.load(userId)).thenReturn(studentContext(List.of()));
        when(conversationRepo.findById(conversationId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.chat(new ChatRequest(userId, null, conversationId, null, "Xin chào")))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void existingHistory_isMappedIntoLlmHistory() {
        when(userContextService.load(userId)).thenReturn(studentContext(List.of()));
        AiMessage prior = AiMessage.builder().id(UUID.randomUUID()).role(
                        project.lms_rikkei_edu.modules.ai.entity.enums.MessageRole.USER)
                .content("Câu hỏi trước").createdAt(OffsetDateTime.now()).build();
        when(messageRepo.findByConversationIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(prior));

        service.chat(new ChatRequest(userId, null, null, null, "Câu hỏi mới"));

        ArgumentCaptor<List> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).complete(any(), historyCaptor.capture(), any());
        assertThat(historyCaptor.getValue()).hasSize(1);
    }

    // ── buildSystemPrompt — student sections ────────────────────────────────

    private UserContext fullStudentContext() {
        UUID courseId = UUID.randomUUID();
        return new UserContext(userId, "Nguyễn Thị Lan", UserContext.UserRole.STUDENT,
                List.of(new UserContext.CourseInfo(courseId, "ReactJS Nâng cao", "IN_PROGRESS", 45.0)),
                List.of(new UserContext.DeadlineInfo("Bài tập 1", "ReactJS Nâng cao", "10/07/2026 23:59", true),
                        new UserContext.DeadlineInfo("Bài tập 2", "ReactJS Nâng cao", "15/07/2026 23:59", false)),
                List.of(new UserContext.GroupInfo(UUID.randomUUID(), "Nhóm A1", "ReactJS Nâng cao")),
                List.of(new UserContext.RecentLessonInfo("Bài 1: Hooks", "Chương 1", "ReactJS Nâng cao", "COMPLETED", "01/07/2026 10:00")),
                List.of(new UserContext.QuizResultInfo("Quiz 1", "ReactJS Nâng cao", 8.0, 10.0, true, "01/07/2026 11:00"),
                        new UserContext.QuizResultInfo("Quiz 2", "ReactJS Nâng cao", 4.0, 10.0, false, "02/07/2026 11:00")),
                List.of(new UserContext.UnsubmittedAssignmentInfo("Bài tập 3", "ReactJS Nâng cao", "20/07/2026 23:59", true),
                        new UserContext.UnsubmittedAssignmentInfo("Bài tập 4", "ReactJS Nâng cao", "25/07/2026 23:59", false)),
                List.of(), List.of(), null,
                List.of(new UserContext.ChapterProgressInfo("ReactJS Nâng cao", "Chương 1", 3, 5)),
                List.of(new UserContext.AssignmentScoreInfo("Bài tập 1", "ReactJS Nâng cao", 9.0, 10.0, "01/07/2026 12:00")),
                List.of(), List.of(), List.of(),
                List.of(new UserContext.CourseStructureInfo("ReactJS Nâng cao", 4, 12)));
    }

    @Test
    void studentWithFullContext_includesAllSectionsInSystemPrompt() {
        when(userContextService.load(userId)).thenReturn(fullStudentContext());

        service.chat(new ChatRequest(userId, null, null, null, "Tình hình học tập của tôi thế nào?"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).complete(promptCaptor.capture(), any(), any());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("KHÓA HỌC ĐANG HỌC:");
        assertThat(prompt).contains("BÀI HỌC GẦN NHẤT:");
        assertThat(prompt).contains("KẾT QUẢ QUIZ GẦN NHẤT:");
        assertThat(prompt).contains("✓ ĐẠT");
        assertThat(prompt).contains("✗ CHƯA ĐẠT");
        assertThat(prompt).contains("BÀI TẬP CHƯA NỘP:");
        assertThat(prompt).contains("⚠️ ĐÃ QUÁ HẠN");
        assertThat(prompt).contains("TIẾN ĐỘ THEO CHƯƠNG:");
        assertThat(prompt).contains("ĐIỂM BÀI TẬP ĐÃ CHẤM:");
        assertThat(prompt).contains("NHÓM HỌC:");
        assertThat(prompt).contains("DEADLINE SẮP ĐẾN:");
        assertThat(prompt).contains("CẤU TRÚC KHÓA HỌC");
    }

    // ── buildSystemPrompt — instructor sections ─────────────────────────────

    private UserContext fullInstructorContext() {
        UUID courseId = UUID.randomUUID();
        return new UserContext(userId, "Trần Văn Bình", UserContext.UserRole.INSTRUCTOR,
                List.of(new UserContext.CourseInfo(courseId, "Spring Boot Microservices", "PUBLISHED", 30.0)),
                List.of(new UserContext.DeadlineInfo("Bài tập 1", "Spring Boot Microservices", "10/07/2026 23:59", true)),
                List.of(new UserContext.GroupInfo(UUID.randomUUID(), "Nhóm B1", "Spring Boot Microservices")),
                List.of(), List.of(), List.of(),
                List.of(new UserContext.SubmissionGapInfo("Bài tập 1", "Spring Boot Microservices", "10/07/2026 23:59", 30, 20, 10)),
                List.of(new UserContext.AtRiskStudentInfo("Lê Văn C", "Spring Boot Microservices", 10.0, 15)),
                null,
                List.of(), List.of(),
                List.of(new UserContext.QuizStatsInfo("Quiz 1", "Spring Boot Microservices", 7.5, 80.0, 25)),
                List.of(new UserContext.TopStudentInfo("Phạm Thị D", "Spring Boot Microservices", 95.0)),
                List.of(new UserContext.CourseApprovalInfo("Spring Boot Microservices", "REJECTED", "Thiếu nội dung"),
                        new UserContext.CourseApprovalInfo("Docker Cơ Bản", "PENDING", null)),
                List.of(new UserContext.CourseStructureInfo("Spring Boot Microservices", 6, 24)));
    }

    @Test
    void instructorWithFullContext_includesAllSectionsInSystemPrompt() {
        when(userContextService.load(userId)).thenReturn(fullInstructorContext());

        service.chat(new ChatRequest(userId, null, null, null, "Tình hình lớp học thế nào?"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).complete(promptCaptor.capture(), any(), any());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("KHÓA HỌC ĐANG QUẢN LÝ:");
        assertThat(prompt).contains("NHÓM ĐANG QUẢN LÝ:");
        assertThat(prompt).contains("BÀI TẬP SẮP ĐẾN HẠN:");
        assertThat(prompt).contains("TỈNH TRẠNG NỘP BÀI:");
        assertThat(prompt).contains("HỌC VIÊN CÓ TIẾN ĐỘ THẤP");
        assertThat(prompt).contains("THỐNG KÊ QUIZ:");
        assertThat(prompt).contains("HỌC VIÊN XUẤT SẮC");
        assertThat(prompt).contains("TRẠNG THÁI DUYỆT KHÓA HỌC:");
        assertThat(prompt).contains("lý do từ chối: Thiếu nội dung");
        assertThat(prompt).contains("CẤU TRÚC KHÓA HỌC");
    }

    // ── buildSystemPrompt — admin section ────────────────────────────────────

    @Test
    void adminWithStats_includesSystemStatsInSystemPrompt() {
        UserContext ctx = new UserContext(userId, "Admin Root", UserContext.UserRole.ADMIN,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                new UserContext.AdminStats(100, 80, 15, 20, 12, 300, 5),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(userContextService.load(userId)).thenReturn(ctx);

        service.chat(new ChatRequest(userId, null, null, null, "Thống kê hệ thống thế nào?"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).complete(promptCaptor.capture(), any(), any());
        assertThat(promptCaptor.getValue()).contains("THỐNG KÊ HỆ THỐNG:");
        assertThat(promptCaptor.getValue()).contains("Tổng người dùng: 100");
    }

    // ── buildSystemPrompt — RAG document context ─────────────────────────────

    @Test
    void chunksWithAndWithoutSectionTitle_areBothRenderedInPrompt() {
        UUID ownCourseId = UUID.randomUUID();
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(ownCourseId, "Khóa Y", "IN_PROGRESS", 20.0));
        when(userContextService.load(userId)).thenReturn(studentContext(courses));
        when(vectorSearch.search(eq(ownCourseId), any(), anyInt(), anyDouble())).thenReturn(List.of(
                new ScoredChunk(UUID.randomUUID(), UUID.randomUUID(), ownCourseId, 0, "Chương mở đầu", "Nội dung A", 0.8),
                new ScoredChunk(UUID.randomUUID(), UUID.randomUUID(), ownCourseId, 1, null, "Nội dung B", 0.7)));

        service.chat(new ChatRequest(userId, ownCourseId, null, null, "Tóm tắt tài liệu"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).complete(promptCaptor.capture(), any(), any());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("=== TÀI LIỆU KHÓA HỌC ===");
        assertThat(prompt).contains("Chương mở đầu");
        assertThat(prompt).contains("Nội dung A");
        assertThat(prompt).contains("Nội dung B");
    }
}
