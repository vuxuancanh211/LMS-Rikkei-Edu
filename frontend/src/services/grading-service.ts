import { httpClient } from '../lib';

export interface GradeRequest {
  submissionId: string;
  score: number | null;
  feedback: string | null;
}

export interface BatchReleaseRequest {
  submissionIds: string[];
}

export interface InstructorSubmissionResponse {
  id: string;
  status: string;
  note: string | null;
  late: boolean;
  score: number | null;
  feedback: string | null;
  submittedAt: string | null;
  gradedAt: string | null;
  scorePublishedAt: string | null;
  files: SubmissionFileResponse[];

  studentId: string;
  studentName: string | null;
  studentEmail: string | null;

  assignmentId: string;
  assignmentTitle: string | null;
  assignmentMaxScore: number | null;

  courseId: string;
  courseTitle: string | null;

  groupId: string | null;
  groupName: string | null;
}

export interface SubmissionFileResponse {
  id: string;
  originalFilename: string | null;
  fileSizeBytes: number | null;
  mimeType: string | null;
  extension: string | null;
  url: string | null;
}

export async function getSubmissions(courseId?: string, assignmentId?: string, status = 'ALL') {
  const params: Record<string, string> = { status };
  if (courseId) params.courseId = courseId;
  if (assignmentId) params.assignmentId = assignmentId;
  const response = await httpClient.get<InstructorSubmissionResponse[]>(
    '/instructor/submissions',
    { params }
  );
  return response.data;
}

export async function gradeSubmission(data: GradeRequest) {
  const response = await httpClient.patch<InstructorSubmissionResponse>(
    '/instructor/submissions/grade',
    data
  );
  return response.data;
}

export async function batchReleaseScores(data: BatchReleaseRequest) {
  await httpClient.patch('/instructor/submissions/batch/release', data);
}

export async function returnSubmission(submissionId: string) {
  await httpClient.patch(`/instructor/submissions/${submissionId}/return`);
}
