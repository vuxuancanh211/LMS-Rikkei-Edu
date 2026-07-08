import { httpClient } from '../../lib';

// Types

export type ChatRoomResponse = {
  id: string;
  name: string;
  groupId: string;
  groupName: string;
  active: boolean;
  lastMessageAt: string | null;
  createdAt: string;
  lastMessage: ChatMessageResponse | null;
  unreadCount: number;
  members: ChatMemberResponse[] | null;
};

export type ChatMessageResponse = {
  id: string;
  roomId: string;
  senderId: string;
  senderName: string;
  senderAvatar: string | null;
  messageType: 'TEXT' | 'FILE' | 'SYSTEM';
  content: string | null;
  attachmentUrl: string | null;
  attachmentName: string | null;
  attachmentSizeBytes: number | null;
  replyToId: string | null;
  replyToContent: string | null;
  replyToAttachmentName: string | null;
  replyToSenderName: string | null;
  edited: boolean;
  editedAt: string | null;
  deleted: boolean;
  createdAt: string;
  reactions: Record<string, number>;
};

export type ChatMemberResponse = {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  role: 'MEMBER' | 'MODERATOR';
  joinedAt: string;
};

export type PageResponse<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

export type SendMessagePayload = {
  content?: string;
  messageType: 'TEXT' | 'FILE' | 'SYSTEM';
  replyToId?: string;
  attachmentUrl?: string;
  attachmentName?: string;
  attachmentSizeBytes?: number;
};

export type MarkAsReadPayload = {
  messageId: string;
};

export type PresignUploadPayload = {
  fileName: string;
  mimeType: string;
};

export type AttachmentPresignResponse = {
  uploadUrl: string;
  viewUrl: string;
  s3Key: string;
};

// API Functions

export async function getRooms(): Promise<ChatRoomResponse[]> {
  const res = await httpClient.get<ChatRoomResponse[]>('/chat/rooms');
  return res.data;
}

export async function getRoomDetail(roomId: string): Promise<ChatRoomResponse> {
  const res = await httpClient.get<ChatRoomResponse>(`/chat/rooms/${roomId}`);
  return res.data;
}

export async function getMessages(
  roomId: string,
  page = 0,
  size = 20,
): Promise<PageResponse<ChatMessageResponse>> {
  const res = await httpClient.get<PageResponse<ChatMessageResponse>>(
    `/chat/rooms/${roomId}/messages`,
    { params: { page, size } },
  );
  return res.data;
}

export async function sendMessage(
  roomId: string,
  payload: SendMessagePayload,
): Promise<ChatMessageResponse> {
  const res = await httpClient.post<ChatMessageResponse>(`/chat/rooms/${roomId}/messages`, payload);
  return res.data;
}

export async function editMessage(
  messageId: string,
  content: string,
): Promise<ChatMessageResponse> {
  const res = await httpClient.put<ChatMessageResponse>(`/chat/messages/${messageId}`, { content });
  return res.data;
}

export async function deleteMessage(messageId: string): Promise<void> {
  await httpClient.delete(`/chat/messages/${messageId}`);
}

export async function addReaction(
  messageId: string,
  emoji: string,
): Promise<Record<string, number>> {
  const res = await httpClient.post<Record<string, number>>(
    `/chat/messages/${messageId}/reactions`,
    { emoji },
  );
  return res.data;
}

export async function removeReaction(
  messageId: string,
  emoji: string,
): Promise<Record<string, number>> {
  const res = await httpClient.delete<Record<string, number>>(
    `/chat/messages/${messageId}/reactions`,
    { params: { emoji } },
  );
  return res.data;
}

export async function markAsRead(roomId: string, messageId: string): Promise<void> {
  await httpClient.post(`/chat/rooms/${roomId}/read`, { messageId } as MarkAsReadPayload);
}

export async function presignUpload(
  roomId: string,
  fileName: string,
  mimeType: string,
): Promise<AttachmentPresignResponse> {
  const res = await httpClient.post<AttachmentPresignResponse>(
    `/chat/rooms/${roomId}/attachments/presign-upload`,
    { fileName, mimeType } as PresignUploadPayload,
  );
  return res.data;
}
