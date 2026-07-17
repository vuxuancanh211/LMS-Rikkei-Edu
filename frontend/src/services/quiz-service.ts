import { httpClient } from '../lib';
import { useAuthStore } from '../store';
import type {
  // Bank
  BankQuestionListParams,
  BankQuestionRequest,
  BankQuestionResponse,
  BankQuestionImportPreviewResponse,
  BankQuestionImportConfirmResponse,
  BankQuestionSearchHit,
  SpringPage,
  // Quiz
  QuizMetadataRequest,
  QuizAddBankQuestionsRequest,
  QuizManualQuestionRequest,
  QuizRandomConfigRequest,
  QuizSummaryResponse,
  QuizDetailResponse,
  DryRunResponse,
  DryRunGradeRequest,
  DryRunGradeResponse,
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

/** Phân trang — page 0-based (khớp Spring Pageable), tránh tải hết câu hỏi lên 1 lượt gây lag. */
export async function listBankQuestions(courseId: string, params?: BankQuestionListParams) {
  const res = await httpClient.get<SpringPage<BankQuestionResponse>>(
    `/courses/${courseId}/bank-questions`,
    { params },
  );
  return res.data;
}

export async function getBankTags(courseId: string) {
  const res = await httpClient.get(`/api/v1/bank-questions/tags`, { params: { courseId } });
  return res.data; // List<String>
}

/** Hybrid search: khớp chữ xếp trước, tương đồng ngữ nghĩa (pgvector) nối sau. */
export async function searchBankQuestions(
  courseId: string,
  q: string,
  params?: BankQuestionListParams,
) {
  const res = await httpClient.get<BankQuestionSearchHit[]>(
    `/courses/${courseId}/bank-questions/search`,
    { params: { ...params, q } },
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

export async function confirmBankImport(courseId: string, token: string, selectedRows: number[] = []) {
  const res = await httpClient.post<BankQuestionImportConfirmResponse>(
    `/courses/${courseId}/bank-questions/import/confirm`,
    { token, selectedRows },
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

/** Phân trang — page 0-based (khớp Spring Pageable), tránh tải hết quiz lên 1 lượt gây lag. */
export async function listQuizzes(
  courseId: string,
  params?: { page?: number; size?: number; title?: string },
) {
  const res = await httpClient.get<SpringPage<QuizSummaryResponse>>(
    `/courses/${courseId}/quizzes`,
    { params },
  );
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

export async function gradeDryRunQuiz(courseId: string, quizId: string, data: DryRunGradeRequest) {
  const res = await httpClient.post<DryRunGradeResponse>(
    `/courses/${courseId}/quizzes/${quizId}/dry-run/grade`,
    data,
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

export async function reorderQuizQuestions(
  courseId: string,
  quizId: string,
  questionIds: string[],
) {
  const res = await httpClient.put<QuizDetailResponse>(
    `/courses/${courseId}/quizzes/${quizId}/questions/reorder`,
    questionIds,
  );
  return res.data;
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

/**
 * Nộp bài "best-effort" khi học viên rời trang (đóng tab/refresh/điều hướng đi) — gọi từ
 * beforeunload/pagehide. axios (XHR) không giữ được request khi trang unload, nên phải dùng
 * fetch({keepalive:true}) trực tiếp; sendBeacon không dùng được vì không set được header
 * Authorization (API xác thực bằng JWT Bearer, không phải cookie). Không await — trình duyệt
 * không đảm bảo chờ promise trong lúc unload, chỉ đảm bảo request được gửi đi nếu kịp trước khi
 * tab đóng hẳn.
 */
export function submitAttemptOnExit(
  courseId: string,
  quizId: string,
  attemptId: string,
  answers: Record<string, string[]>,
) {
  try {
    const { accessToken, tokenType } = useAuthStore.getState();
    const base = httpClient.defaults.baseURL || '';
    const url = `${base}/courses/${courseId}/quizzes/${quizId}/attempts/${attemptId}/submit`;
    fetch(url, {
      method: 'POST',
      keepalive: true,
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `${tokenType || 'Bearer'} ${accessToken}` } : {}),
      },
      body: JSON.stringify({ answers }),
    }).catch(() => {
      // Best-effort — nếu request không gửi được (VD: mất mạng đúng lúc đóng tab) thì
      // scheduler auto-submit theo thời hạn quiz ở backend vẫn sẽ chấm bài khi hết giờ.
    });
  } catch {
    // ignore — không có gì để làm thêm khi trang đang unload
  }
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

// ─── AI Question Generation ────────────────────────────────────────────────────

export interface AiGenerateRequest {
  topic: string;
  questionType: string;
  difficulty: string;
  subjectTag?: string;
  sourceIds?: string[];
  count: number;
  duplicateThreshold?: number;
}

export interface AiGeneratedOption {
  text: string;
  correct: boolean;
  explanation: string;
}

export interface AiGeneratedQuestion {
  questionText: string;
  questionType: string;
  difficulty: string;
  options: AiGeneratedOption[];
  duplicate: boolean;
  duplicateOfId: string | null;
  similarityScore: number;
}

export interface AiGenerateResponse {
  questions: AiGeneratedQuestion[];
  totalGenerated: number;
  duplicateCount: number;
  newCount: number;
}

export type AiGenerationStep = 'RETRIEVING_CONTEXT' | 'GENERATING' | 'CHECKING_DUPLICATES' | 'DONE' | 'FAILED';

export interface AiGenerationJobStatus {
  step: AiGenerationStep;
  result: AiGenerateResponse | null;
  errorMessage: string | null;
}

/** Bắt đầu sinh câu hỏi bằng AI — trả về jobId ngay, pipeline thật chạy nền (có thể mất 30-90s). */
export async function startAiGenerateQuestions(courseId: string, req: AiGenerateRequest) {
  const res = await httpClient.post<{ jobId: string }>(
    `/courses/${courseId}/bank-questions/ai/generate`,
    req,
  );
  return res.data;
}

/** Poll tiến trình 1 job sinh câu hỏi AI — gọi lặp lại cho tới khi step là DONE/FAILED. */
export async function getAiGenerateJobStatus(courseId: string, jobId: string) {
  const res = await httpClient.get<AiGenerationJobStatus>(
    `/courses/${courseId}/bank-questions/ai/generate/${jobId}`,
  );
  return res.data;
}

export async function aiSaveQuestions(courseId: string, questions: BankQuestionRequest[]) {
  const res = await httpClient.post<BankQuestionResponse[]>(
    `/courses/${courseId}/bank-questions/ai/save`,
    questions,
    { timeout: 60_000 },
  );
  return res.data;
}
