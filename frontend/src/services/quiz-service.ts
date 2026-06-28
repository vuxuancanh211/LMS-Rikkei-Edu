import { httpClient } from '../lib';
import type {
  // Bank
  BankQuestionListParams,
  BankQuestionRequest,
  BankQuestionResponse,
  BankQuestionImportPreviewResponse,
  BankQuestionImportConfirmResponse,
  // Quiz
  QuizMetadataRequest,
  QuizAddBankQuestionsRequest,
  QuizManualQuestionRequest,
  QuizRandomConfigRequest,
  QuizSummaryResponse,
  QuizDetailResponse,
  DryRunResponse,
  // Attempt
  AutosaveRequest,
  SubmitAttemptRequest,
  StartAttemptResponse,
  AttemptResultResponse,
  // Proctoring
  ViolationRequest,
  ViolationResponse,
  // Stats
  QuizStatsResponse,
  AttemptHistoryEntry,
  StudentQuizProgressEntry,
} from '../types/quiz';

// ─── Question Bank ─────────────────────────────────────────────────────────────

export async function listBankQuestions(courseId: string, params?: BankQuestionListParams) {
  const res = await httpClient.get<BankQuestionResponse[]>(
    `/courses/${courseId}/bank-questions`,
    { params },
  );
  return res.data;
}

export async function getBankQuestion(courseId: string, questionId: string) {
  const res = await httpClient.get<BankQuestionResponse>(
    `/courses/${courseId}/bank-questions/${questionId}`,
  );
  return res.data;
}

export async function createBankQuestion(courseId: string, data: BankQuestionRequest) {
  const res = await httpClient.post<BankQuestionResponse>(
    `/courses/${courseId}/bank-questions`,
    data,
  );
  return res.data;
}

export async function updateBankQuestion(courseId: string, questionId: string, data: BankQuestionRequest) {
  const res = await httpClient.put<BankQuestionResponse>(
    `/courses/${courseId}/bank-questions/${questionId}`,
    data,
  );
  return res.data;
}

export async function deleteBankQuestion(courseId: string, questionId: string) {
  await httpClient.delete(`/courses/${courseId}/bank-questions/${questionId}`);
}

export async function toggleBankQuestionStatus(courseId: string, questionId: string) {
  await httpClient.patch(`/courses/${courseId}/bank-questions/${questionId}/toggle-status`);
}

export async function previewBankImport(courseId: string, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  const res = await httpClient.post<BankQuestionImportPreviewResponse>(
    `/courses/${courseId}/bank-questions/import/preview`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return res.data;
}

export async function confirmBankImport(courseId: string, token: string) {
  const res = await httpClient.post<BankQuestionImportConfirmResponse>(
    `/courses/${courseId}/bank-questions/import/confirm`,
    { token },
  );
  return res.data;
}

export async function exportBankQuestions(courseId: string, format: 'csv' | 'xlsx' = 'xlsx') {
  const res = await httpClient.get(`/courses/${courseId}/bank-questions/export`, {
    params: { format },
    responseType: 'blob',
  });
  const url = URL.createObjectURL(res.data as Blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `bank-questions.${format}`;
  a.click();
  URL.revokeObjectURL(url);
}

// ─── Quiz CRUD ─────────────────────────────────────────────────────────────────

export async function listQuizzes(courseId: string) {
  const res = await httpClient.get<QuizSummaryResponse[]>(`/courses/${courseId}/quizzes`);
  return res.data;
}

export async function getQuizDetail(courseId: string, quizId: string) {
  const res = await httpClient.get<QuizDetailResponse>(`/courses/${courseId}/quizzes/${quizId}`);
  return res.data;
}

export async function createQuiz(courseId: string, data: QuizMetadataRequest) {
  const res = await httpClient.post<QuizSummaryResponse>(`/courses/${courseId}/quizzes`, data);
  return res.data;
}

export async function updateQuiz(courseId: string, quizId: string, data: QuizMetadataRequest) {
  const res = await httpClient.put<QuizSummaryResponse>(
    `/courses/${courseId}/quizzes/${quizId}`,
    data,
  );
  return res.data;
}

export async function deleteQuiz(courseId: string, quizId: string) {
  await httpClient.delete(`/courses/${courseId}/quizzes/${quizId}`);
}

// ─── Quiz Lifecycle ────────────────────────────────────────────────────────────

export async function publishQuiz(courseId: string, quizId: string) {
  const res = await httpClient.post<QuizSummaryResponse>(
    `/courses/${courseId}/quizzes/${quizId}/publish`,
  );
  return res.data;
}

export async function archiveQuiz(courseId: string, quizId: string) {
  const res = await httpClient.post<QuizSummaryResponse>(
    `/courses/${courseId}/quizzes/${quizId}/archive`,
  );
  return res.data;
}

export async function unarchiveQuiz(courseId: string, quizId: string) {
  const res = await httpClient.post<QuizSummaryResponse>(
    `/courses/${courseId}/quizzes/${quizId}/unarchive`,
  );
  return res.data;
}

export async function dryRunQuiz(courseId: string, quizId: string) {
  const res = await httpClient.get<DryRunResponse>(
    `/courses/${courseId}/quizzes/${quizId}/dry-run`,
  );
  return res.data;
}

// ─── Quiz Questions ────────────────────────────────────────────────────────────

export async function addBankQuestionsToQuiz(
  courseId: string,
  quizId: string,
  data: QuizAddBankQuestionsRequest,
) {
  const res = await httpClient.post<QuizDetailResponse>(
    `/courses/${courseId}/quizzes/${quizId}/questions/bank`,
    data,
  );
  return res.data;
}

export async function addManualQuestionToQuiz(
  courseId: string,
  quizId: string,
  data: QuizManualQuestionRequest,
) {
  const res = await httpClient.post<QuizDetailResponse>(
    `/courses/${courseId}/quizzes/${quizId}/questions/manual`,
    data,
  );
  return res.data;
}

export async function removeQuestionFromQuiz(
  courseId: string,
  quizId: string,
  questionId: string,
) {
  await httpClient.delete(`/courses/${courseId}/quizzes/${quizId}/questions/${questionId}`);
}

export async function configureRandomDraw(
  courseId: string,
  quizId: string,
  data: QuizRandomConfigRequest,
) {
  const res = await httpClient.put<QuizSummaryResponse>(
    `/courses/${courseId}/quizzes/${quizId}/random-config`,
    data,
  );
  return res.data;
}

// ─── Take Quiz (Attempt) ───────────────────────────────────────────────────────

export async function startAttempt(courseId: string, quizId: string) {
  const res = await httpClient.post<StartAttemptResponse>(
    `/courses/${courseId}/quizzes/${quizId}/attempts`,
  );
  return res.data;
}

export async function autosave(
  courseId: string,
  quizId: string,
  attemptId: string,
  data: AutosaveRequest,
) {
  await httpClient.put(
    `/courses/${courseId}/quizzes/${quizId}/attempts/${attemptId}/autosave`,
    data,
  );
}

export async function submitAttempt(
  courseId: string,
  quizId: string,
  attemptId: string,
  data: SubmitAttemptRequest,
) {
  const res = await httpClient.post<AttemptResultResponse>(
    `/courses/${courseId}/quizzes/${quizId}/attempts/${attemptId}/submit`,
    data,
  );
  return res.data;
}

export async function getAttemptResult(
  courseId: string,
  quizId: string,
  attemptId: string,
) {
  const res = await httpClient.get<AttemptResultResponse>(
    `/courses/${courseId}/quizzes/${quizId}/attempts/${attemptId}/result`,
  );
  return res.data;
}

// ─── Proctoring ───────────────────────────────────────────────────────────────

export async function reportViolation(attemptId: string, data: ViolationRequest) {
  const res = await httpClient.post<ViolationResponse>(
    `/attempts/${attemptId}/proctoring/violations`,
    data,
  );
  return res.data;
}

export async function getViolations(attemptId: string) {
  const res = await httpClient.get<ViolationResponse[]>(
    `/attempts/${attemptId}/proctoring/violations`,
  );
  return res.data;
}

// ─── Statistics ───────────────────────────────────────────────────────────────

export async function getQuizStats(courseId: string, quizId: string) {
  const res = await httpClient.get<QuizStatsResponse>(
    `/courses/${courseId}/quizzes/${quizId}/stats`,
  );
  return res.data;
}

export async function getAllAttemptsForQuiz(courseId: string, quizId: string) {
  const res = await httpClient.get<AttemptHistoryEntry[]>(
    `/courses/${courseId}/quizzes/${quizId}/attempts`,
  );
  return res.data;
}

export async function getMyAttempts(courseId: string, quizId: string) {
  const res = await httpClient.get<AttemptHistoryEntry[]>(
    `/courses/${courseId}/quizzes/${quizId}/my-attempts`,
  );
  return res.data;
}

export async function getStudentCourseProgress(courseId: string) {
  const res = await httpClient.get<StudentQuizProgressEntry[]>(
    `/courses/${courseId}/my-quiz-progress`,
  );
  return res.data;
}
