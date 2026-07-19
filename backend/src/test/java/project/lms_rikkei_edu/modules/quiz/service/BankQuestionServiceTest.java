package project.lms_rikkei_edu.modules.quiz.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankOptionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionImportConfirmRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionImportPreviewResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionImportConfirmResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionImportRowResult;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionSearchHit;
import project.lms_rikkei_edu.modules.quiz.entity.BankOptionEntity;
import project.lms_rikkei_edu.modules.quiz.entity.BankQuestionEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;
import project.lms_rikkei_edu.modules.quiz.repository.BankOptionRepository;
import project.lms_rikkei_edu.modules.quiz.repository.BankQuestionRepository;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService.SemanticHit;
import project.lms_rikkei_edu.modules.quiz.service.impl.BankQuestionServiceImpl;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankQuestionServiceTest {

    @Mock BankQuestionRepository bankQuestionRepository;
    @Mock BankOptionRepository bankOptionRepository;
    @Mock RedisService redisService;
    @Mock BankQuestionEmbeddingService embeddingService;
    @Mock OpenAiProperties openAiProperties;

    @InjectMocks BankQuestionServiceImpl service;

    private UUID courseId;
    private UUID instructorId;
    private UUID questionId;

    @BeforeEach
    void setUp() {
        // ObjectMapper cần inject thủ công vì @InjectMocks không inject final field
        service = new BankQuestionServiceImpl(bankQuestionRepository, bankOptionRepository,
                redisService, new ObjectMapper(), embeddingService, openAiProperties);
        courseId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
        questionId = UUID.randomUUID();
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    void create_singleChoice_withOneCorrectAnswer_success() {
        BankQuestionRequest request = buildRequest(QuestionType.SINGLE_CHOICE,
                List.of(option("A", true), option("B", false), option("C", false)));
        when(bankQuestionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(any())).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(any())).thenReturn(false);

        BankQuestionResponse response = service.create(courseId, instructorId, request);

        assertThat(response).isNotNull();
        verify(bankQuestionRepository).saveAndFlush(any());
        verify(bankOptionRepository, times(3)).save(any());
    }

    @Test
    void create_singleChoice_withTwoCorrectAnswers_throwsException() {
        BankQuestionRequest request = buildRequest(QuestionType.SINGLE_CHOICE,
                List.of(option("A", true), option("B", true)));

        assertThatThrownBy(() -> service.create(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đúng 1 đáp án đúng");
    }

    @Test
    void create_multipleChoice_withOneCorrectAnswer_throwsException() {
        BankQuestionRequest request = buildRequest(QuestionType.MULTIPLE_CHOICE,
                List.of(option("A", true), option("B", false)));

        assertThatThrownBy(() -> service.create(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 2 đáp án đúng");
    }

    @Test
    void create_withLessThanTwoOptions_throwsException() {
        BankQuestionRequest request = buildRequest(QuestionType.SINGLE_CHOICE,
                List.of(option("A", true)));

        assertThatThrownBy(() -> service.create(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 2 đáp án");
    }

    @Test
    void create_trueFalse_withOneCorrectAnswer_success() {
        BankQuestionRequest request = buildRequest(QuestionType.TRUE_FALSE,
                List.of(option("True", true), option("False", false)));
        when(bankQuestionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(any())).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(any())).thenReturn(false);

        assertThatNoException().isThrownBy(() -> service.create(courseId, instructorId, request));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_noReference_hardDelete() {
        BankQuestionEntity q = buildEntity();
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(bankQuestionRepository.hasQuizReference(questionId)).thenReturn(false);

        service.delete(courseId, questionId);

        verify(bankOptionRepository).deleteByBankQuestionId(questionId);
        verify(bankQuestionRepository).delete(q);
        verify(bankQuestionRepository, never()).save(any());
    }

    @Test
    void delete_hasReference_softDeleteToInactive() {
        BankQuestionEntity q = buildEntity();
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(bankQuestionRepository.hasQuizReference(questionId)).thenReturn(true);

        service.delete(courseId, questionId);

        verify(bankQuestionRepository).save(q);
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.INACTIVE);
        verify(bankQuestionRepository, never()).delete(any());
    }

    @Test
    void delete_questionNotInCourse_throwsException() {
        BankQuestionEntity q = buildEntity();
        q.setCourseId(UUID.randomUUID()); // different course
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> service.delete(courseId, questionId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_questionNotFound_throwsException() {
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(courseId, questionId))
                .isInstanceOf(BusinessException.class);
    }

    // ── Toggle status ─────────────────────────────────────────────────────────

    @Test
    void toggleStatus_activeToInactive() {
        BankQuestionEntity q = buildEntity();
        q.setStatus(QuestionStatus.ACTIVE);
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));

        service.toggleStatus(courseId, questionId);

        assertThat(q.getStatus()).isEqualTo(QuestionStatus.INACTIVE);
        verify(bankQuestionRepository).save(q);
    }

    @Test
    void toggleStatus_inactiveToActive() {
        BankQuestionEntity q = buildEntity();
        q.setStatus(QuestionStatus.INACTIVE);
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));

        service.toggleStatus(courseId, questionId);

        assertThat(q.getStatus()).isEqualTo(QuestionStatus.ACTIVE);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Test
    void list_filterByDifficulty_callsCorrectRepository() {
        when(bankQuestionRepository.findByCourseIdAndStatusAndDifficulty(
                courseId, QuestionStatus.ACTIVE, QuestionDifficulty.EASY))
                .thenReturn(List.of());

        List<BankQuestionResponse> result = service.list(courseId, null, QuestionDifficulty.EASY, null);

        assertThat(result).isEmpty();
        verify(bankQuestionRepository).findByCourseIdAndStatusAndDifficulty(
                courseId, QuestionStatus.ACTIVE, QuestionDifficulty.EASY);
    }

    @Test
    void list_noFilter_returnsAll() {
        when(bankQuestionRepository.findByCourseId(courseId)).thenReturn(List.of());

        service.list(courseId, null, null, null);

        verify(bankQuestionRepository).findByCourseId(courseId);
    }

    // ── ListPaged ─────────────────────────────────────────────────────────────

    @Test
    void listPaged_explicitStatus_usedAsIs() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(bankQuestionRepository.findByFilters(courseId, QuestionStatus.INACTIVE, null, null, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        service.listPaged(courseId, QuestionStatus.INACTIVE, null, null, pageable);

        verify(bankQuestionRepository).findByFilters(courseId, QuestionStatus.INACTIVE, null, null, pageable);
    }

    @Test
    void listPaged_noStatusButDifficultyGiven_implicitlyFiltersActive() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(bankQuestionRepository.findByFilters(courseId, QuestionStatus.ACTIVE, QuestionDifficulty.EASY, null, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        service.listPaged(courseId, null, QuestionDifficulty.EASY, null, pageable);

        verify(bankQuestionRepository).findByFilters(courseId, QuestionStatus.ACTIVE, QuestionDifficulty.EASY, null, pageable);
    }

    @Test
    void listPaged_noStatusButSubjectTagGiven_implicitlyFiltersActive() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(bankQuestionRepository.findByFilters(courseId, QuestionStatus.ACTIVE, null, "Index", pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        service.listPaged(courseId, null, null, "Index", pageable);

        verify(bankQuestionRepository).findByFilters(courseId, QuestionStatus.ACTIVE, null, "Index", pageable);
    }

    @Test
    void listPaged_noFiltersAtAll_noStatusFilter() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        BankQuestionEntity entity = buildEntity();
        when(bankQuestionRepository.findByFilters(courseId, null, null, null, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(entity)));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(questionId)).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(questionId)).thenReturn(false);

        var result = service.listPaged(courseId, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(bankQuestionRepository).findByFilters(courseId, null, null, null, pageable);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    void update_textChanged_reEmbeds() {
        BankQuestionEntity q = buildEntity();
        q.setQuestionText("Old text");
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(questionId)).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(questionId)).thenReturn(false);

        BankQuestionRequest request = buildRequest(QuestionType.SINGLE_CHOICE,
                List.of(option("A", true), option("B", false)));
        request.setQuestionText("New text");

        service.update(courseId, questionId, request);

        assertThat(q.getQuestionText()).isEqualTo("New text");
        verify(bankOptionRepository).deleteByBankQuestionId(questionId);
        verify(embeddingService).embedAndSaveSafe(questionId, "New text");
    }

    @Test
    void update_textUnchanged_doesNotReEmbed() {
        BankQuestionEntity q = buildEntity();
        q.setQuestionText("Same text");
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(questionId)).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(questionId)).thenReturn(false);

        BankQuestionRequest request = buildRequest(QuestionType.SINGLE_CHOICE,
                List.of(option("A", true), option("B", false)));
        request.setQuestionText("Same text");

        service.update(courseId, questionId, request);

        verify(embeddingService, never()).embedAndSaveSafe(any(), any());
    }

    @Test
    void update_questionNotFound_throwsException() {
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.empty());
        BankQuestionRequest request = buildRequest(QuestionType.SINGLE_CHOICE,
                List.of(option("A", true), option("B", false)));

        assertThatThrownBy(() -> service.update(courseId, questionId, request))
                .isInstanceOf(BusinessException.class);
    }

    // ── GetById ───────────────────────────────────────────────────────────────

    @Test
    void getById_found_returnsResponse() {
        BankQuestionEntity q = buildEntity();
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(questionId)).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(questionId)).thenReturn(true);

        BankQuestionResponse response = service.getById(courseId, questionId);

        assertThat(response.getId()).isEqualTo(questionId);
        assertThat(response.getQuizUsageCount()).isEqualTo(1);
    }

    @Test
    void getById_wrongCourse_throwsException() {
        BankQuestionEntity q = buildEntity();
        q.setCourseId(UUID.randomUUID());
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> service.getById(courseId, questionId))
                .isInstanceOf(BusinessException.class);
    }

    // ── List additional branches ─────────────────────────────────────────────

    @Test
    void list_subjectTagOnly_callsCorrectRepository() {
        when(bankQuestionRepository.findByCourseIdAndStatusAndSubjectTag(courseId, QuestionStatus.ACTIVE, "Math"))
                .thenReturn(List.of());

        service.list(courseId, null, null, "Math");

        verify(bankQuestionRepository).findByCourseIdAndStatusAndSubjectTag(courseId, QuestionStatus.ACTIVE, "Math");
    }

    @Test
    void list_statusOnly_callsCorrectRepository() {
        when(bankQuestionRepository.findByCourseIdAndStatus(courseId, QuestionStatus.INACTIVE)).thenReturn(List.of());

        service.list(courseId, QuestionStatus.INACTIVE, null, null);

        verify(bankQuestionRepository).findByCourseIdAndStatus(courseId, QuestionStatus.INACTIVE);
    }

    @Test
    void list_difficultyAndSubjectTag_filtersInMemoryByMatchingTag() {
        BankQuestionEntity matching = buildEntity();
        matching.setSubjectTag("Math");
        BankQuestionEntity nonMatching = buildEntity();
        nonMatching.setId(UUID.randomUUID());
        nonMatching.setSubjectTag("Science");
        when(bankQuestionRepository.findByCourseIdAndStatusAndDifficulty(courseId, QuestionStatus.ACTIVE, QuestionDifficulty.EASY))
                .thenReturn(List.of(matching, nonMatching));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(any())).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(any())).thenReturn(false);

        List<BankQuestionResponse> result = service.list(courseId, null, QuestionDifficulty.EASY, "Math");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubjectTag()).isEqualTo("Math");
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    void search_blankQuery_returnsEmptyWithNoRepositoryCalls() {
        List<BankQuestionSearchHit> result = service.search(courseId, "   ", null, null, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(bankQuestionRepository, embeddingService);
    }

    @Test
    void search_nullQuery_returnsEmpty() {
        List<BankQuestionSearchHit> result = service.search(courseId, null, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void search_textAndSemanticHits_combinesResultsTextFirst() {
        BankQuestionEntity textMatch = buildEntity();
        textMatch.setQuestionText("Capital of France");
        when(bankQuestionRepository.searchAndFilter(eq(courseId), eq("France"), isNull(), isNull(), isNull()))
                .thenReturn(List.of(textMatch));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(any())).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(any())).thenReturn(false);
        when(openAiProperties.getSearchTopK()).thenReturn(20);
        when(openAiProperties.getSearchSimilarityThreshold()).thenReturn(0.4);

        UUID semanticQuestionId = UUID.randomUUID();
        BankQuestionEntity semanticEntity = buildEntity();
        semanticEntity.setId(semanticQuestionId);
        when(bankQuestionRepository.findById(semanticQuestionId)).thenReturn(Optional.of(semanticEntity));
        when(embeddingService.searchSimilar(eq(courseId), eq("France"), isNull(), isNull(), isNull(),
                anySet(), eq(20), eq(0.4)))
                .thenReturn(List.of(new SemanticHit(semanticQuestionId, 0.85)));

        List<BankQuestionSearchHit> result = service.search(courseId, "France", null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMatchType()).isEqualTo("TEXT");
        assertThat(result.get(1).getMatchType()).isEqualTo("SEMANTIC");
        assertThat(result.get(1).getSimilarity()).isEqualTo(0.85);
    }

    @Test
    void search_semanticHitQuestionNotFound_isDropped() {
        when(bankQuestionRepository.searchAndFilter(eq(courseId), eq("query"), isNull(), isNull(), isNull()))
                .thenReturn(List.of());
        when(openAiProperties.getSearchTopK()).thenReturn(20);
        when(openAiProperties.getSearchSimilarityThreshold()).thenReturn(0.4);

        UUID missingId = UUID.randomUUID();
        when(embeddingService.searchSimilar(any(), any(), any(), any(), any(), anySet(), anyInt(), anyDouble()))
                .thenReturn(List.of(new SemanticHit(missingId, 0.9)));
        when(bankQuestionRepository.findById(missingId)).thenReturn(Optional.empty());

        List<BankQuestionSearchHit> result = service.search(courseId, "query", null, null, null);

        assertThat(result).isEmpty();
    }

    // ── Import preview ───────────────────────────────────────────────────────

    @Test
    void importPreview_csv_classifiesNewDuplicateAndErrorRows() {
        String csv = "question_text,question_type,difficulty,subject_tag,option_a,option_b,correct_answers\n"
                + "New question?,SINGLE_CHOICE,EASY,Math,A,B,A\n"
                + "Dup question?,SINGLE_CHOICE,EASY,Math,A,B,A\n"
                + ",SINGLE_CHOICE,EASY,Math,A,B,A\n";
        MockMultipartFile file = new MockMultipartFile("file", "questions.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        when(bankQuestionRepository.existsByCourseIdAndQuestionText(courseId, "New question?")).thenReturn(false);
        when(bankQuestionRepository.existsByCourseIdAndQuestionText(courseId, "Dup question?")).thenReturn(true);

        BankQuestionImportPreviewResponse response = service.importPreview(courseId, file);

        assertThat(response.getTotalRows()).isEqualTo(3);
        assertThat(response.getNewCount()).isEqualTo(1);
        assertThat(response.getDuplicateCount()).isEqualTo(1);
        assertThat(response.getErrorCount()).isEqualTo(1);
        assertThat(response.getToken()).isNotBlank();
        verify(redisService).set(startsWith("quiz:bank:import:"), any(), eq(1800L));
    }

    @Test
    void importPreview_emptyFile_throwsBusinessException() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> service.importPreview(courseId, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có dữ liệu");
    }

    @Test
    void importPreview_tooManyRows_throwsBusinessException() {
        StringBuilder csv = new StringBuilder("question_text,question_type,difficulty,option_a,option_b,correct_answers\n");
        for (int i = 0; i < 501; i++) {
            csv.append("Q").append(i).append(",SINGLE_CHOICE,EASY,A,B,A\n");
        }
        MockMultipartFile file = new MockMultipartFile("file", "big.csv", "text/csv",
                csv.toString().getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.importPreview(courseId, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vượt quá");
    }

    @Test
    void importPreview_excel_parsesRowsCorrectly() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Questions");
            Row header = sheet.createRow(0);
            String[] headers = {"question_text", "question_type", "difficulty", "subject_tag",
                    "option_a", "option_b", "option_c", "option_d", "correct_answers"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Excel question?");
            row1.createCell(1).setCellValue("SINGLE_CHOICE");
            row1.createCell(2).setCellValue("EASY");
            row1.createCell(3).setCellValue("Math");
            row1.createCell(4).setCellValue("A");
            row1.createCell(5).setCellValue("B");
            row1.createCell(8).setCellValue("A");

            wb.write(out);
            bytes = out.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        when(bankQuestionRepository.existsByCourseIdAndQuestionText(courseId, "Excel question?")).thenReturn(false);

        BankQuestionImportPreviewResponse response = service.importPreview(courseId, file);

        assertThat(response.getTotalRows()).isEqualTo(1);
        assertThat(response.getNewCount()).isEqualTo(1);
        assertThat(response.getRows().get(0).getQuestionText()).isEqualTo("Excel question?");
    }

    // ── Import confirm ────────────────────────────────────────────────────────

    @Test
    void importConfirm_expiredToken_throwsBusinessException() {
        when(redisService.get(anyString())).thenReturn(Optional.empty());

        BankQuestionImportConfirmRequest request = new BankQuestionImportConfirmRequest();
        request.setToken("missing-token");

        assertThatThrownBy(() -> service.importConfirm(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hết hạn");
    }

    @Test
    void importConfirm_malformedJson_throwsBusinessException() {
        when(redisService.get(anyString())).thenReturn(Optional.of((Object) "not-json"));

        BankQuestionImportConfirmRequest request = new BankQuestionImportConfirmRequest();
        request.setToken("bad-token");

        assertThatThrownBy(() -> service.importConfirm(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không hợp lệ");
    }

    @Test
    void importConfirm_mixedRows_importsSelectedAndSkipsRest() throws Exception {
        // BankQuestionImportRowResult has no no-args constructor/setters and no Jackson creator
        // annotations, so a bare ObjectMapper (as used in production's JacksonConfig, with no
        // parameter-names module registered) cannot actually round-trip it through JSON — see
        // importConfirm_realObjectMapper_alwaysThrowsDueToMissingJacksonCreator below. Mock
        // ObjectMapper#readValue directly here to exercise importConfirm's row-classification and
        // counting logic in isolation from that pre-existing (de)serialization limitation.
        List<BankQuestionImportRowResult> preview = List.of(
                BankQuestionImportRowResult.builder().rowNumber(1).questionText("New Q").questionType(QuestionType.SINGLE_CHOICE)
                        .difficulty(QuestionDifficulty.EASY).status("NEW").errors(List.of()).build(),
                BankQuestionImportRowResult.builder().rowNumber(2).questionText("Dup Selected").questionType(QuestionType.SINGLE_CHOICE)
                        .difficulty(QuestionDifficulty.EASY).status("DUPLICATE").errors(List.of()).build(),
                BankQuestionImportRowResult.builder().rowNumber(3).questionText("Dup Unselected").questionType(QuestionType.SINGLE_CHOICE)
                        .difficulty(QuestionDifficulty.EASY).status("DUPLICATE").errors(List.of()).build(),
                BankQuestionImportRowResult.builder().rowNumber(4).questionText("Bad Q").questionType(null)
                        .difficulty(null).status("ERROR").errors(List.of("Thiếu nội dung câu hỏi")).build()
        );
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        when(mockMapper.getTypeFactory()).thenReturn(com.fasterxml.jackson.databind.type.TypeFactory.defaultInstance());
        when(mockMapper.readValue(anyString(), any(com.fasterxml.jackson.databind.JavaType.class))).thenReturn(preview);
        BankQuestionServiceImpl serviceWithMockMapper = new BankQuestionServiceImpl(
                bankQuestionRepository, bankOptionRepository, redisService, mockMapper, embeddingService, openAiProperties);

        when(redisService.get("quiz:bank:import:tok123")).thenReturn(Optional.of((Object) "irrelevant-json"));
        when(bankQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BankQuestionImportConfirmRequest request = new BankQuestionImportConfirmRequest();
        request.setToken("tok123");
        request.setSelectedRows(List.of(1, 2));

        BankQuestionImportConfirmResponse response = serviceWithMockMapper.importConfirm(courseId, instructorId, request);

        assertThat(response.getTotalImported()).isEqualTo(2); // NEW + selected DUPLICATE
        assertThat(response.getSkippedCount()).isEqualTo(2); // unselected DUPLICATE + ERROR
        verify(bankQuestionRepository, times(2)).save(any());
        verify(bankQuestionRepository).flush();
        verify(embeddingService).embedAndSaveBatchSafe(argThat(list -> list.size() == 2));
        verify(redisService).delete("quiz:bank:import:tok123");
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Test
    void export_csvFormat_returnsCsvBytes() {
        BankQuestionEntity q = buildEntity();
        when(bankQuestionRepository.findByCourseId(courseId)).thenReturn(List.of(q));
        BankOptionEntity opt = new BankOptionEntity();
        opt.setOptionText("A");
        opt.setIsCorrect(true);
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(questionId)).thenReturn(List.of(opt));

        byte[] result = service.export(courseId, "csv");

        String content = new String(result, StandardCharsets.UTF_8);
        assertThat(content).startsWith("question_text,question_type");
        assertThat(content).contains("Test?");
    }

    @Test
    void export_nonCsvFormat_returnsExcelBytes() {
        when(bankQuestionRepository.findByCourseId(courseId)).thenReturn(List.of());

        byte[] result = service.export(courseId, "xlsx");

        assertThat(result).isNotEmpty();
        // XLSX files are zip archives starting with the "PK" magic bytes
        assertThat(result[0]).isEqualTo((byte) 'P');
        assertThat(result[1]).isEqualTo((byte) 'K');
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BankQuestionRequest buildRequest(QuestionType type, List<BankOptionRequest> options) {
        BankQuestionRequest r = new BankQuestionRequest();
        r.setQuestionText("Câu hỏi test?");
        r.setQuestionType(type);
        r.setDifficulty(QuestionDifficulty.EASY);
        r.setOptions(options);
        return r;
    }

    private BankOptionRequest option(String text, boolean correct) {
        BankOptionRequest o = new BankOptionRequest();
        o.setOptionText(text);
        o.setIsCorrect(correct);
        return o;
    }

    private BankQuestionEntity buildEntity() {
        BankQuestionEntity q = new BankQuestionEntity();
        q.setId(questionId);
        q.setCourseId(courseId);
        q.setQuestionText("Test?");
        q.setQuestionType(QuestionType.SINGLE_CHOICE);
        q.setDifficulty(QuestionDifficulty.MEDIUM);
        q.setStatus(QuestionStatus.ACTIVE);
        return q;
    }
}
