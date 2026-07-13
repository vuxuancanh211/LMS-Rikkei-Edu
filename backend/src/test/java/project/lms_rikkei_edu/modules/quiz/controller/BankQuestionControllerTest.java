package project.lms_rikkei_edu.modules.quiz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CourseOwnershipGuard;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankOptionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionImportConfirmRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionImportConfirmResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionImportPreviewResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionResponse;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankQuestionControllerTest {

    private BankQuestionService bankQuestionService;
    private CurrentUserProvider currentUserProvider;
    private CourseOwnershipGuard ownershipGuard;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID courseId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bankQuestionService = mock(BankQuestionService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        ownershipGuard = mock(CourseOwnershipGuard.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BankQuestionController(bankQuestionService, currentUserProvider, ownershipGuard))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        doNothing().when(ownershipGuard).requireOwnership(courseId);
    }

    private BankQuestionRequest buildRequest() {
        BankOptionRequest option = new BankOptionRequest();
        option.setOptionText("A");
        option.setIsCorrect(true);
        BankQuestionRequest req = new BankQuestionRequest();
        req.setQuestionText("2+2=?");
        req.setQuestionType(QuestionType.SINGLE_CHOICE);
        req.setDifficulty(QuestionDifficulty.EASY);
        req.setOptions(List.of(option));
        return req;
    }

    @Test
    void list_returnsPage() throws Exception {
        Page<BankQuestionResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(bankQuestionService.listPaged(eq(courseId), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/courses/{courseId}/bank-questions", courseId))
                .andExpect(status().isOk());

        verify(ownershipGuard).requireOwnership(courseId);
    }

    @Test
    void search_returnsHits() throws Exception {
        when(bankQuestionService.search(eq(courseId), eq("index"), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/courses/{courseId}/bank-questions/search", courseId).param("q", "index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getById_returnsQuestion() throws Exception {
        when(bankQuestionService.getById(courseId, questionId))
                .thenReturn(BankQuestionResponse.builder().id(questionId).build());

        mockMvc.perform(get("/api/courses/{courseId}/bank-questions/{questionId}", courseId, questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(questionId.toString()));
    }

    @Test
    void create_returnsCreatedQuestion() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(bankQuestionService.create(eq(courseId), eq(instructorId), any()))
                .thenReturn(BankQuestionResponse.builder().id(questionId).build());

        mockMvc.perform(post("/api/courses/{courseId}/bank-questions", courseId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(questionId.toString()));
    }

    @Test
    void update_returnsUpdatedQuestion() throws Exception {
        when(bankQuestionService.update(eq(courseId), eq(questionId), any()))
                .thenReturn(BankQuestionResponse.builder().id(questionId).build());

        mockMvc.perform(put("/api/courses/{courseId}/bank-questions/{questionId}", courseId, questionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/courses/{courseId}/bank-questions/{questionId}", courseId, questionId))
                .andExpect(status().isNoContent());

        verify(bankQuestionService).delete(courseId, questionId);
    }

    @Test
    void toggleStatus_returnsNoContent() throws Exception {
        mockMvc.perform(patch("/api/courses/{courseId}/bank-questions/{questionId}/toggle-status", courseId, questionId))
                .andExpect(status().isNoContent());

        verify(bankQuestionService).toggleStatus(courseId, questionId);
    }

    @Test
    void importPreview_returnsPreview() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});
        when(bankQuestionService.importPreview(eq(courseId), any()))
                .thenReturn(BankQuestionImportPreviewResponse.builder().token("tok").totalRows(1).build());

        mockMvc.perform(multipart("/api/courses/{courseId}/bank-questions/import/preview", courseId).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"));
    }

    @Test
    void importConfirm_returnsResult() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(bankQuestionService.importConfirm(eq(courseId), eq(instructorId), any()))
                .thenReturn(BankQuestionImportConfirmResponse.builder().totalImported(3).build());

        BankQuestionImportConfirmRequest request = new BankQuestionImportConfirmRequest();
        request.setToken("tok");

        mockMvc.perform(post("/api/courses/{courseId}/bank-questions/import/confirm", courseId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalImported").value(3));
    }

    @Test
    void export_xlsx_returnsXlsxContentType() throws Exception {
        when(bankQuestionService.export(courseId, "xlsx")).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/courses/{courseId}/bank-questions/export", courseId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=question-bank.xlsx"));
    }

    @Test
    void export_csv_returnsCsvContentType() throws Exception {
        when(bankQuestionService.export(courseId, "csv")).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/courses/{courseId}/bank-questions/export", courseId).param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=question-bank.csv"));
    }
}
