package project.lms_rikkei_edu.modules.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;
import project.lms_rikkei_edu.modules.ai.service.embedding.EmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.llm.LlmService;
import project.lms_rikkei_edu.modules.ai.service.retrieval.ScoredChunk;
import project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService;
import project.lms_rikkei_edu.modules.quiz.dto.request.AiGenerateQuestionsRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerateQuestionsResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerationJobStatusResponse;
import project.lms_rikkei_edu.modules.quiz.entity.AiQuestionGenerationJob;
import project.lms_rikkei_edu.modules.quiz.enums.GenerationStep;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;
import project.lms_rikkei_edu.modules.quiz.repository.AiQuestionGenerationJobRepository;
import project.lms_rikkei_edu.modules.quiz.service.impl.AiQuestionGeneratorServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiQuestionGeneratorServiceTest {

    @Mock private LlmService llmService;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorSearchService vectorSearch;
    @Mock private JdbcTemplate jdbc;
    @Mock private AiQuestionGenerationJobRepository jobRepo;

    private OpenAiProperties props;
    private ObjectMapper objectMapper;
    private AiQuestionGeneratorService service;

    private final UUID courseId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID requestedBy = UUID.randomUUID();

    private static final String ONE_QUESTION_JSON = """
            {"questions":[{"questionText":"What is 1+1?","options":[\
            {"text":"2","correct":true,"explanation":"correct"},\
            {"text":"3","correct":false,"explanation":"wrong"}]}]}""";

    @BeforeEach
    void setUp() {
        props = new OpenAiProperties();
        objectMapper = new ObjectMapper();
        service = new AiQuestionGeneratorServiceImpl(
                llmService, embeddingService, vectorSearch, props, objectMapper, jdbc, jobRepo, Runnable::run);
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private AiGenerateQuestionsRequest buildRequest(int count) {
        AiGenerateQuestionsRequest req = new AiGenerateQuestionsRequest();
        req.setTopic("Indexing");
        req.setQuestionType(QuestionType.SINGLE_CHOICE);
        req.setDifficulty(QuestionDifficulty.EASY);
        req.setCount(count);
        req.setDuplicateThreshold(0.88);
        return req;
    }

    private AiQuestionGenerationJob buildJob() {
        AiQuestionGenerationJob job = new AiQuestionGenerationJob();
        job.setId(jobId);
        job.setCourseId(courseId);
        job.setRequestedBy(requestedBy);
        job.setStep(GenerationStep.RETRIEVING_CONTEXT);
        return job;
    }

    // ── startGenerate ─────────────────────────────────────────────────────────

    @Nested
    class StartGenerate {

        @Test
        void createsJob_andReturnsId() {
            AiGenerateQuestionsRequest req = buildRequest(5);

            UUID result = service.startGenerate(courseId, req, requestedBy);

            ArgumentCaptor<AiQuestionGenerationJob> captor = ArgumentCaptor.forClass(AiQuestionGenerationJob.class);
            verify(jobRepo).save(captor.capture());
            AiQuestionGenerationJob saved = captor.getValue();
            assertThat(saved.getCourseId()).isEqualTo(courseId);
            assertThat(saved.getRequestedBy()).isEqualTo(requestedBy);
            assertThat(saved.getStep()).isEqualTo(GenerationStep.RETRIEVING_CONTEXT);
            assertThat(result).isEqualTo(saved.getId());
        }
    }

    // ── generateAsync ─────────────────────────────────────────────────────────

    @Nested
    class GenerateAsync {

        @Test
        void happyPath_noSources_marksJobDone() {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(llmService.completeForJson(anyString(), anyString())).thenReturn(ONE_QUESTION_JSON);
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
            when(jdbc.queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString())).thenReturn(List.of());

            service.generateAsync(jobId, courseId, buildRequest(1));

            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
            assertThat(job.getResultJson()).isNotNull();
            verify(embeddingService, never()).embed(anyString());
            verify(vectorSearch, never()).search(any(), any(), any(), anyInt(), anyDouble());
        }

        @Test
        void withSourceIds_includesRetrievedChunksInPrompt() {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.2f});
            ScoredChunk chunk = new ScoredChunk(UUID.randomUUID(), UUID.randomUUID(), courseId, 0,
                    "Section 1", "Some course content about indexing.", 0.9);
            when(vectorSearch.search(eq(courseId), anyList(), any(), anyInt(), anyDouble()))
                    .thenReturn(List.of(chunk));
            when(llmService.completeForJson(anyString(), anyString())).thenReturn(ONE_QUESTION_JSON);
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
            when(jdbc.queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString())).thenReturn(List.of());

            AiGenerateQuestionsRequest req = buildRequest(1);
            req.setSourceIds(List.of(UUID.randomUUID()));

            service.generateAsync(jobId, courseId, req);

            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(llmService).completeForJson(promptCaptor.capture(), anyString());
            assertThat(promptCaptor.getValue()).contains("TÀI LIỆU KHÓA HỌC").contains("Some course content");
        }

        @Test
        void retrieveChunks_embeddingThrows_fallsBackToNoContext() {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("embedding down"));
            when(llmService.completeForJson(anyString(), anyString())).thenReturn(ONE_QUESTION_JSON);
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
            when(jdbc.queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString())).thenReturn(List.of());

            AiGenerateQuestionsRequest req = buildRequest(1);
            req.setSourceIds(List.of(UUID.randomUUID()));

            service.generateAsync(jobId, courseId, req);

            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
            verify(vectorSearch, never()).search(any(), any(), any(), anyInt(), anyDouble());
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(llmService).completeForJson(promptCaptor.capture(), anyString());
            assertThat(promptCaptor.getValue()).doesNotContain("TÀI LIỆU KHÓA HỌC");
        }

        @Test
        void llmCallFails_marksJobFailed() {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(llmService.completeForJson(anyString(), anyString())).thenThrow(new RuntimeException("LLM down"));

            service.generateAsync(jobId, courseId, buildRequest(1));

            assertThat(job.getStep()).isEqualTo(GenerationStep.FAILED);
            assertThat(job.getErrorMessage()).contains("không sinh được câu hỏi hợp lệ");
        }

        @Test
        void noValidQuestionsParsed_marksJobFailed() {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(llmService.completeForJson(anyString(), anyString())).thenReturn("not valid json at all");

            service.generateAsync(jobId, courseId, buildRequest(1));

            assertThat(job.getStep()).isEqualTo(GenerationStep.FAILED);
            assertThat(job.getErrorMessage()).contains("không sinh được câu hỏi hợp lệ");
        }

        @Test
        void stripsMarkdownFence_beforeParsing() {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(llmService.completeForJson(anyString(), anyString()))
                    .thenReturn("```json\n" + ONE_QUESTION_JSON + "\n```");
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
            when(jdbc.queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString())).thenReturn(List.of());

            service.generateAsync(jobId, courseId, buildRequest(1));

            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
        }

        @Test
        void countAboveBatchSize_makesMultipleLlmCalls() {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            // 7 câu = batch 1 (5 câu) + batch 2 (2 câu). Mỗi lần gọi phải trả đủ số câu hợp lệ
            // yêu cầu của batch đó (mỗi câu cần >=2 option để pass isValidOptionSet) để không
            // kích hoạt retry — questionText khác nhau để tránh bị loại trùng theo text.
            String batch1Json = """
                    {"questions":[
                        {"questionText":"Q1?","options":[{"text":"A","correct":true,"explanation":"e"},{"text":"B","correct":false,"explanation":"e"}]},
                        {"questionText":"Q2?","options":[{"text":"A","correct":true,"explanation":"e"},{"text":"B","correct":false,"explanation":"e"}]},
                        {"questionText":"Q3?","options":[{"text":"A","correct":true,"explanation":"e"},{"text":"B","correct":false,"explanation":"e"}]},
                        {"questionText":"Q4?","options":[{"text":"A","correct":true,"explanation":"e"},{"text":"B","correct":false,"explanation":"e"}]},
                        {"questionText":"Q5?","options":[{"text":"A","correct":true,"explanation":"e"},{"text":"B","correct":false,"explanation":"e"}]}
                    ]}""";
            String batch2Json = """
                    {"questions":[
                        {"questionText":"Q6?","options":[{"text":"A","correct":true,"explanation":"e"},{"text":"B","correct":false,"explanation":"e"}]},
                        {"questionText":"Q7?","options":[{"text":"A","correct":true,"explanation":"e"},{"text":"B","correct":false,"explanation":"e"}]}
                    ]}""";
            when(llmService.completeForJson(anyString(), anyString()))
                    .thenReturn(batch1Json)
                    .thenReturn(batch2Json);
            when(embeddingService.embedBatch(anyList()))
                    .thenAnswer(inv -> {
                        List<?> texts = inv.getArgument(0);
                        List<float[]> embeddings = new ArrayList<>();
                        for (int i = 0; i < texts.size(); i++) embeddings.add(new float[]{i + 1f});
                        return embeddings;
                    });
            when(jdbc.queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString())).thenReturn(List.of());

            service.generateAsync(jobId, courseId, buildRequest(7));

            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
            verify(llmService, times(2)).completeForJson(anyString(), anyString());
        }

        @Test
        void duplicateQuestionText_acrossBatches_isDeduped() throws Exception {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(llmService.completeForJson(anyString(), anyString())).thenReturn(ONE_QUESTION_JSON);
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
            when(jdbc.queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString())).thenReturn(List.of());

            // count=6 → 2 batches (5 + 1), cả 2 batch đều trả về CÙNG 1 câu hỏi (mock cố định)
            // → phải bị dedupe theo text, chỉ còn 1 câu trong kết quả cuối.
            service.generateAsync(jobId, courseId, buildRequest(6));

            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
            AiGenerateQuestionsResponse resp = objectMapper.readValue(job.getResultJson(), AiGenerateQuestionsResponse.class);
            assertThat(resp.getQuestions()).hasSize(1);
        }

        @Test
        void checkDuplicates_vectorMatchFound_marksQuestionDuplicate() throws Exception {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(llmService.completeForJson(anyString(), anyString())).thenReturn(ONE_QUESTION_JSON);
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
            UUID existingId = UUID.randomUUID();
            when(jdbc.queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString()))
                    .thenReturn(List.of(Map.of("id", existingId.toString(), "question_text", "dup",
                            "similarity", 0.95)));

            service.generateAsync(jobId, courseId, buildRequest(1));

            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
            AiGenerateQuestionsResponse resp = objectMapper.readValue(job.getResultJson(), AiGenerateQuestionsResponse.class);
            assertThat(resp.getQuestions().get(0).isDuplicate()).isTrue();
            assertThat(resp.getQuestions().get(0).getDuplicateOfId()).isEqualTo(existingId.toString());
            assertThat(resp.getDuplicateCount()).isEqualTo(1);
        }

        @Test
        void checkDuplicates_embeddingUnavailable_fallsBackToExactMatch() throws Exception {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(llmService.completeForJson(anyString(), anyString())).thenReturn(ONE_QUESTION_JSON);
            when(embeddingService.embedBatch(anyList())).thenThrow(new RuntimeException("embedding down"));
            when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(UUID.class), anyString())).thenReturn(true);

            service.generateAsync(jobId, courseId, buildRequest(1));

            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
            AiGenerateQuestionsResponse resp = objectMapper.readValue(job.getResultJson(), AiGenerateQuestionsResponse.class);
            assertThat(resp.getQuestions().get(0).isDuplicate()).isTrue();
            assertThat(resp.getQuestions().get(0).getSimilarityScore()).isEqualTo(1.0);
            verify(jdbc, never()).queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString());
        }

        @Test
        void checkDuplicates_perQuestionVectorQueryThrows_fallsBackToExactMatchForThatQuestion() throws Exception {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(llmService.completeForJson(anyString(), anyString())).thenReturn(ONE_QUESTION_JSON);
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
            when(jdbc.queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString()))
                    .thenThrow(new RuntimeException("bank_question_embeddings missing"));
            when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(UUID.class), anyString())).thenReturn(false);

            service.generateAsync(jobId, courseId, buildRequest(1));

            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
            AiGenerateQuestionsResponse resp = objectMapper.readValue(job.getResultJson(), AiGenerateQuestionsResponse.class);
            assertThat(resp.getQuestions().get(0).isDuplicate()).isFalse();
            verify(jdbc).queryForObject(anyString(), eq(Boolean.class), any(UUID.class), anyString());
        }

        @Test
        void checkExactDuplicate_queryThrows_isSwallowed() {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            when(llmService.completeForJson(anyString(), anyString())).thenReturn(ONE_QUESTION_JSON);
            when(embeddingService.embedBatch(anyList())).thenThrow(new RuntimeException("embedding down"));
            when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(UUID.class), anyString()))
                    .thenThrow(new RuntimeException("db down"));

            service.generateAsync(jobId, courseId, buildRequest(1));

            // Không throw ra ngoài — job vẫn DONE, câu hỏi chỉ đơn giản không được đánh dấu duplicate.
            assertThat(job.getStep()).isEqualTo(GenerationStep.DONE);
        }

        @Test
        void multipleChoiceType_usesMultipleChoicePromptGuide() {
            AiQuestionGenerationJob job = buildJob();
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
            // MULTIPLE_CHOICE cần >=2 đáp án đúng để pass isValidOptionSet — ONE_QUESTION_JSON
            // chỉ có 1 đáp án đúng nên không dùng được ở đây.
            String multipleChoiceJson = """
                    {"questions":[{"questionText":"What is 1+1?","options":[\
                    {"text":"2","correct":true,"explanation":"correct"},\
                    {"text":"4","correct":true,"explanation":"correct"},\
                    {"text":"3","correct":false,"explanation":"wrong"}]}]}""";
            when(llmService.completeForJson(anyString(), anyString())).thenReturn(multipleChoiceJson);
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
            when(jdbc.queryForList(anyString(), anyString(), any(UUID.class), anyString(), anyDouble(), anyString())).thenReturn(List.of());

            AiGenerateQuestionsRequest req = buildRequest(1);
            req.setQuestionType(QuestionType.MULTIPLE_CHOICE);
            req.setDifficulty(QuestionDifficulty.HARD);

            service.generateAsync(jobId, courseId, req);

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(llmService).completeForJson(promptCaptor.capture(), anyString());
            assertThat(promptCaptor.getValue())
                    .contains("2 đến 3 đáp án đúng")
                    .contains("Mức độ: KHÓ");
        }
    }

    // ── getJobStatus ──────────────────────────────────────────────────────────

    @Nested
    class GetJobStatus {

        @Test
        void throws_whenJobNotFound() {
            when(jobRepo.findById(jobId)).thenReturn(Optional.empty());

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getJobStatus(jobId))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void returnsStepWithoutResult_whenNotDone() {
            AiQuestionGenerationJob job = buildJob();
            job.setStep(GenerationStep.GENERATING);
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

            AiGenerationJobStatusResponse resp = service.getJobStatus(jobId);

            assertThat(resp.getStep()).isEqualTo(GenerationStep.GENERATING);
            assertThat(resp.getResult()).isNull();
        }

        @Test
        void returnsDeserializedResult_whenDone() throws Exception {
            AiQuestionGenerationJob job = buildJob();
            job.setStep(GenerationStep.DONE);
            AiGenerateQuestionsResponse resultObj = new AiGenerateQuestionsResponse(List.of(), 0, 0, 0);
            job.setResultJson(objectMapper.writeValueAsString(resultObj));
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

            AiGenerationJobStatusResponse resp = service.getJobStatus(jobId);

            assertThat(resp.getStep()).isEqualTo(GenerationStep.DONE);
            assertThat(resp.getResult()).isNotNull();
        }

        @Test
        void malformedResultJson_returnsFailedStatus() {
            AiQuestionGenerationJob job = buildJob();
            job.setStep(GenerationStep.DONE);
            job.setResultJson("{not valid json");
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

            AiGenerationJobStatusResponse resp = service.getJobStatus(jobId);

            assertThat(resp.getStep()).isEqualTo(GenerationStep.FAILED);
            assertThat(resp.getErrorMessage()).contains("Lỗi đọc kết quả đã lưu");
        }
    }
}
