// ─── Pagination (Spring Page<T> — content/totalElements/totalPages/number/size) ─────

export type SpringPage<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // 0-based trang hiện tại
  size: number;
};

// ─── Enums ────────────────────────────────────────────────────────────────────

export type QuizType = 'STATIC' | 'RANDOM_DRAW';
export type QuizStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED' | 'EXPIRED';
export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE';
export type QuestionDifficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type QuestionStatus = 'ACTIVE' | 'ARCHIVED';
export type RandomMode = 'FULLY_RANDOM' | 'BY_DIFFICULTY';
export type ViolationType = 'TAB_SWITCH' | 'WINDOW_BLUR' | 'EXIT_FULLSCREEN';

// ─── Question Bank ─────────────────────────────────────────────────────────────

export type BankOptionRequest = {
  optionText: string;
  isCorrect: boolean;
  orderIndex?: number;
};

export type BankQuestionRequest = {
  questionText: string;
  questionType: QuestionType;
  difficulty: QuestionDifficulty;
  subjectTag?: string;
  explanation?: string;
  options: BankOptionRequest[];
};

export type BankOptionResponse = {
  id: string;
  optionText: string;
  isCorrect: boolean;
  orderIndex: number;
};

export type BankQuestionResponse = {
  id: string;
  courseId: string;
  questionText: string;
  questionType: QuestionType;
  difficulty: QuestionDifficulty;
  subjectTag?: string | null;
  explanation?: string | null;
  status: QuestionStatus;
  options: BankOptionResponse[];
  quizUsageCount: number;
  createdAt: string;
};

export type BankQuestionSearchHit = {
  question: BankQuestionResponse;
  matchType: 'TEXT' | 'SEMANTIC';
  similarity?: number | null;
};

export type BankQuestionImportOption = {
  text: string;
  correct: boolean;
  orderIndex: number;
};

export type BankQuestionImportRowResult = {
  rowNumber: number;
  questionText: string;
  questionType: QuestionType | null;
  difficulty: QuestionDifficulty | null;
  subjectTag: string | null;
  options: BankQuestionImportOption[];
  status: 'NEW' | 'DUPLICATE' | 'ERROR';
  errors: string[];
};

export type BankQuestionImportPreviewResponse = {
  token: string;
  totalRows: number;
  newCount: number;
  duplicateCount: number;
  errorCount: number;
  rows: BankQuestionImportRowResult[];
};

export type BankQuestionImportConfirmResponse = {
  totalImported: number;
  skippedCount: number;
};

export type BankQuestionListParams = {
  status?: QuestionStatus;
  difficulty?: QuestionDifficulty;
  subjectTag?: string;
  page?: number;
  size?: number;
};

// ─── Quiz Management ───────────────────────────────────────────────────────────

export type QuizMetadataRequest = {
  title: string;
  description?: string;
  quizType: QuizType;
  durationMinutes: number;
  maxAttempts?: number;
  passScore?: number;
  shuffleQuestions?: boolean;
  shuffleOptions?: boolean;
  proctoringEnabled?: boolean;
  cooldownMinutes?: number;
  endDate?: string;
};

export type QuizAddBankQuestionsRequest = {
  bankQuestionIds: string[];
};

export type QuizManualQuestionRequest = {
  questionText: string;
  questionType: QuestionType;
  difficulty: QuestionDifficulty;
  subjectTag?: string;
  explanation?: string;
  saveToBank?: boolean;
  options: BankOptionRequest[];
};

export type QuizRandomConfigRequest = {
  randomMode: RandomMode;
  totalCount?: number;
  difficultyConfig?: Record<string, number>;
  subjectTagFilter?: string;
};

export type QuizOptionResponse = {
  id: string;
  optionText: string;
  orderIndex: number;
};

export type QuizQuestionResponse = {
  id: string;
  bankQuestionId?: string | null;
  questionText: string;
  questionType: QuestionType;
  difficulty: QuestionDifficulty;
  subjectTag?: string | null;
  orderIndex: number;
  explanation?: string | null;
  options: QuizOptionResponse[];
};

export type QuizSummaryResponse = {
  id: string;
  courseId: string;
  title: string;
  description?: string | null;
  quizType: QuizType;
  status: QuizStatus;
  durationMinutes: number;
  maxAttempts: number;
  passScore?: number | null;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  proctoringEnabled: boolean;
  cooldownMinutes?: number | null;
  endDate?: string | null;
  publishedAt?: string | null;
  archivedAt?: string | null;
  questionCount: number;
};

export type QuizDetailResponse = QuizSummaryResponse & {
  randomMode?: RandomMode | null;
  randomTotalCount?: number | null;
  difficultyConfig?: Record<string, number> | null;
  subjectTagFilter?: string | null;
  questions: QuizQuestionResponse[];
};

export type DryRunResponse = {
  questions: QuizQuestionResponse[];
  totalQuestions: number;
  note?: string | null;
  durationMinutes?: number | null;
};

export type DryRunGradeRequest = {
  questionIds: string[];
  answers: Record<string, string[]>;
};

export type DryRunAnswerResult = {
  questionId: string;
  answered: boolean;
  isCorrect: boolean;
  correctOptionIds: string[];
};

export type DryRunGradeResponse = {
  score: number;
  maxScore: number;
  scorePercentage: number;
  isPassed: boolean;
  correctCount: number;
  incorrectCount: number;
  unansweredCount: number;
  totalQuestions: number;
  answers: DryRunAnswerResult[];
};

// ─── Take Quiz (Attempt) ───────────────────────────────────────────────────────

export type AutosaveRequest = {
  answers: Record<string, string[]>;
};

export type SubmitAttemptRequest = {
  answers?: Record<string, string[]> | null;
};

export type StartAttemptResponse = {
  attemptId: string;
  quizId: string;
  attemptNumber: number;
  startedAt: string;
  expiresAt: string;
  durationMinutes: number;
  proctoringEnabled: boolean;
  questions: QuizQuestionResponse[];
};

export type AttemptAnswerResult = {
  questionId: string;
  questionText: string;
  options: QuizOptionResponse[];
  selectedOptionIds: string[];
  correctOptionIds: string[];
  isCorrect: boolean;
};

export type AttemptResultResponse = {
  attemptId: string;
  quizId: string;
  status: AttemptStatus;
  score: number;
  scorePercentage: number;
  isPassed: boolean;
  correctCount: number;
  incorrectCount: number;
  unansweredCount: number;
  totalQuestions: number;
  timeSpentSeconds?: number | null;
  submittedAt?: string | null;
  autoSubmitted?: boolean;
  violationCount?: number;
  answers: AttemptAnswerResult[];
};

// ─── Proctoring ───────────────────────────────────────────────────────────────

export type ViolationRequest = {
  violationType: ViolationType;
  description?: string;
  clientTimestamp?: string;
};

export type ViolationResponse = {
  id: string;
  attemptId: string;
  violationType: ViolationType;
  violationOrder: number;
  totalViolations: number;
  maxViolations: number;
  actionTaken: string;
  lockedOut: boolean;
  serverTimestamp: string;
};

// ─── Results & Statistics ─────────────────────────────────────────────────────

export type QuizQuestionStatsResponse = {
  questionId: string;
  questionText: string;
  questionType: QuestionType;
  difficulty: QuestionDifficulty;
  totalAnswers: number;
  correctCount: number;
  correctRate: number;
};

export type QuizStatsResponse = {
  quizId: string;
  quizTitle: string;
  totalAttempts: number;
  uniqueStudents: number;
  avgScore: number;
  avgScorePercentage: number;
  passRate: number;
  passCount: number;
  avgTimeSpentSeconds?: number | null;
  questionStats: QuizQuestionStatsResponse[];
};

export type AttemptHistoryEntry = {
  attemptId: string;
  attemptNumber: number;
  status: AttemptStatus;
  score: number;
  scorePercentage: number;
  isPassed: boolean;
  correctCount: number;
  incorrectCount: number;
  unansweredCount: number;
  timeSpentSeconds?: number | null;
  autoSubmitted: boolean;
  violationCount: number;
  startedAt: string;
  submittedAt?: string | null;
};

export type StudentQuizProgressEntry = {
  quizId: string;
  quizTitle: string;
  quizType: QuizType;
  quizStatus: QuizStatus;
  maxAttempts: number;
  attemptsUsed: number;
  passed: boolean;
  bestScore?: number | null;
  bestScorePercentage?: number | null;
  canRetry: boolean;
};
