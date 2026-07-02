import { httpClient } from '../lib';

export type SourceReference = {
  chunkId: string;
  sourceName: string;
  sectionTitle: string | null;
  excerpt: string;
  similarity: number;
};

export type ChatResponse = {
  conversationId: string;
  messageId: string;
  answer: string;
  sources: SourceReference[];
  totalTokens: number;
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
  courseId: string;
  sourceType: AiSourceType;
  sourceName: string;
  ingestStatus: AiIngestStatus;
  chunkCount: number | null;
  errorMessage: string | null;
  createdAt: string;
  indexedAt: string | null;
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

export async function listAiSources(courseId: string): Promise<AiSource[]> {
  const response = await httpClient.get<AiSource[]>('/ai/sources', { params: { courseId } });
  return response.data;
}

export async function presignAiSourceUpload(payload: {
  courseId: string;
  originalFilename: string;
  mimeType: string;
}): Promise<{ uploadUrl: string; s3Key: string }> {
  const response = await httpClient.post<{ uploadUrl: string; s3Key: string }>('/ai/sources/presign-upload', payload);
  return response.data;
}

export async function createAiSource(payload: {
  courseId: string;
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
