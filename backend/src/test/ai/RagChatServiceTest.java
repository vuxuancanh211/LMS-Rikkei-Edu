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
import project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
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
    private final UUID courseId = UUID.randomUUID();

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

    @Test
    void instructorAskingAboutCourses_getsCourseListStructuredData() {
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(UUID.randomUUID(), "Spring Boot Microservices", "DRAFT", 0.0),
                new UserContext.CourseInfo(UUID.randomUUID(), "Docker & Kubernetes", "PUBLISHED", 3.0));
        when(userContextService.load(userId)).thenReturn(instructorContext(courses));

        ChatResponse resp = service.chat(new ChatRequest(userId, courseId, null, null, "Tôi có bao nhiêu khóa học?"));

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

        ChatResponse resp = service.chat(new ChatRequest(userId, courseId, null, null, "Deadline bài tập sắp tới là khi nào?"));

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
                userId, courseId, null, null, "Khóa học DEVVVVVVVV đang có bao nhiêu chương?"));

        assertThat(resp.structuredData()).isNull();
    }

    @Test
    void studentAskingAboutCourses_getsNoStructuredData() {
        List<UserContext.CourseInfo> courses = List.of(
                new UserContext.CourseInfo(UUID.randomUUID(), "Spring Boot Microservices", "IN_PROGRESS", 45.0));
        when(userContextService.load(userId)).thenReturn(studentContext(courses));

        ChatResponse resp = service.chat(new ChatRequest(userId, courseId, null, null, "Tôi có bao nhiêu khóa học?"));

        assertThat(resp.structuredData()).isNull();
    }

    @Test
    void uiRenderFromLlm_isPassedThroughToChatResponse() {
        when(userContextService.load(userId)).thenReturn(instructorContext(List.of()));
        UiRender uiRender = new UiRender("table", Map.of("columns", List.of("Quiz"), "rows", List.of()));
        when(llmService.complete(any(), any(), any()))
                .thenReturn(new LlmResponse("Đây là bảng thống kê quiz", 10, 5, 15, 100, uiRender));

        ChatResponse resp = service.chat(new ChatRequest(userId, courseId, null, null, "Quiz nào học viên làm kém nhất?"));

        assertThat(resp.uiRender()).isEqualTo(uiRender);
    }

    @Test
    void noToolCallFromLlm_leavesUiRenderNull() {
        when(userContextService.load(userId)).thenReturn(instructorContext(List.of()));

        ChatResponse resp = service.chat(new ChatRequest(userId, courseId, null, null, "Xin chào"));

        assertThat(resp.uiRender()).isNull();
    }
}
