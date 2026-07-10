import { httpClient } from '../lib';

export type SourceReference = {
  chunkId: string;
  courseId: string | null;
  courseName: string | null;
  sourceName: string | null;
  sectionTitle: string | null;
  excerpt: string;
  similarity: number;
};

export type StructuredCourseItem = {
  courseId: string;
  title: string;
  status: string;
  metric: number | null;
};

export type StructuredData = {
  type: 'COURSE_LIST';
  items: StructuredCourseItem[];
} | null;

export type UiRender = {
  component: 'table' | 'stat_cards' | 'progress_bars' | 'list';
  props: Record<string, any>;
} | null;

export type ChatResponse = {
  conversationId: string;
  messageId: string;
  answer: string;
  sources: SourceReference[];
  totalTokens: number;
  structuredData: StructuredData;
  uiRender: UiRender;
};

export type AiConversation = {
  id: string;
  studentId: string;
  courseId: string;
  lessonId: string | null;
  title: string | null;
  status: 'ACTIVE' | 'CLOSED';
  messageCount: number;
  createdAt: string;
  lastMessageAt: string;
};

export type AiMessage = {
  id: string;
  conversationId: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  llmProvider: string | null;
  llmModel: string | null;
  responseTimeMs: number | null;
  createdAt: string;
};

export type AiIngestStatus = 'PENDING' | 'PROCESSING' | 'INDEXED' | 'FAILED';
export type AiSourceType = 'TEXT' | 'URL' | 'PDF' | 'DOC' | 'VIDEO';

export type AiSource = {
  id: string;
  /** Null for system-wide documents not tied to any course. */
  courseId: string | null;
  /** Null for system-wide documents, or if the course lookup wasn't resolved. */
  courseName: string | null;
  /** Owning instructor of courseId's course. Null for system-wide documents. */
  instructorId: string | null;
  sourceType: AiSourceType;
  sourceName: string;
  ingestStatus: AiIngestStatus;
  chunkCount: number | null;
  errorMessage: string | null;
  createdAt: string;
  indexedAt: string | null;
  /** Non-null when this document was added from an existing lesson resource, rather than uploaded standalone. */
  resourceId: string | null;
};

export async function sendChatMessage(payload: {
  message: string;
  courseId?: string | null;
  conversationId?: string | null;
  lessonId?: string | null;
}): Promise<ChatResponse> {
  const response = await httpClient.post<ChatResponse>('/ai/chat', payload);
  return response.data;
}

export async function getConversations(courseId?: string | null): Promise<AiConversation[]> {
  const response = await httpClient.get<AiConversation[]>('/ai/conversations', {
    params: courseId ? { courseId } : undefined,
  });
  return response.data;
}

export async function getConversationMessages(conversationId: string): Promise<AiMessage[]> {
  const response = await httpClient.get<AiMessage[]>(`/ai/conversations/${conversationId}/messages`);
  return response.data;
}

/**
 * Omit courseId to list every source the caller can manage: ADMIN gets everything in the system
 * (system-wide docs + every course's docs); INSTRUCTOR gets every doc across the courses they own.
 */
export async function listAiSources(courseId?: string): Promise<AiSource[]> {
  const response = await httpClient.get<AiSource[]>('/ai/sources', { params: courseId ? { courseId } : undefined });
  return response.data;
}

export async function presignAiSourceUpload(payload: {
  /** Null for a system-wide document not tied to any course (ADMIN only). */
  courseId: string | null;
  originalFilename: string;
  mimeType: string;
}): Promise<{ uploadUrl: string; s3Key: string }> {
  const response = await httpClient.post<{ uploadUrl: string; s3Key: string }>('/ai/sources/presign-upload', payload);
  return response.data;
}

export async function createAiSource(payload: {
  /** Null for a system-wide document not tied to any course (ADMIN only). */
  courseId: string | null;
  sourceType: AiSourceType;
  sourceName: string;
  metadata?: Record<string, unknown>;
}): Promise<AiSource> {
  const response = await httpClient.post<AiSource>('/ai/sources', payload);
  return response.data;
}

export async function deleteAiSource(id: string): Promise<void> {
  await httpClient.delete(`/ai/sources/${id}`);
}

export async function reingestAiSource(id: string): Promise<AiSource> {
  const response = await httpClient.post<AiSource>(`/ai/sources/${id}/reingest`);
  return response.data;
}

export type AvailableResource = {
  resourceId: string;
  lessonId: string;
  lessonTitle: string;
  chapterTitle: string;
  displayName: string;
  mimeType: string;
  alreadyAdded: boolean;
  aiSourceId: string | null;
};

export async function listAvailableResources(courseId: string): Promise<AvailableResource[]> {
  const response = await httpClient.get<AvailableResource[]>('/ai/sources/available-resources', { params: { courseId } });
  return response.data;
}

export async function addResourcesToAi(courseId: string, resourceIds: string[]): Promise<AiSource[]> {
  const response = await httpClient.post<AiSource[]>('/ai/sources/from-resources', { courseId, resourceIds });
  return response.data;
}

/** Presigned, inline-viewable URL for a source's original uploaded file. PDF/DOC only. */
export async function getAiSourceViewUrl(id: string): Promise<{ url: string }> {
  const response = await httpClient.get<{ url: string }>(`/ai/sources/${id}/view-url`);
  return response.data;
}

export type AiChunk = {
  chunkIndex: number;
  sectionTitle: string | null;
  chunkText: string;
};

/** The text chunks actually extracted and embedded for a source — what the AI "read" from it. */
export async function getAiSourceChunks(id: string): Promise<AiChunk[]> {
  const response = await httpClient.get<AiChunk[]>(`/ai/sources/${id}/chunks`);
  return response.data;
}
