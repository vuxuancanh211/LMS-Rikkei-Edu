import { httpClient } from '../lib';

export function toAbsoluteApiUrl(url?: string | null) {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;

  const baseURL = httpClient.defaults.baseURL || '';
  if (!baseURL) return url;

  try {
    const base = new URL(baseURL, window.location.origin);
    if (url.startsWith('/api/')) return `${base.origin}${url}`;
    return new URL(url, baseURL.endsWith('/') ? baseURL : `${baseURL}/`).toString();
  } catch {
    return url;
  }
}

export function toStableApiPath(url?: string | null) {
  if (!url) return '';
  try {
    const parsed = new URL(url, window.location.origin);
    return parsed.pathname.startsWith('/api/') ? parsed.pathname + (parsed.search || '') : url;
  } catch {
    return url;
  }
}

export type ForumAuthor = {
  id: string;
  fullName: string;
  role: 'ADMIN' | 'INSTRUCTOR' | 'STUDENT';
  avatarUrl?: string | null;
};

export type ForumAttachment = {
  id: string;
  fileName: string;
  url: string;
  contentType: string;
  sizeBytes: number;
  attachmentType: 'IMAGE' | 'FILE';
};

export type ForumPost = {
  id: string;
  courseId: string;
  courseTitle: string;
  author: ForumAuthor;
  topic?: string | null;
  title: string;
  upvoteCount: number;
  upvoted: boolean;
  content: string;
  attachments: ForumAttachment[];
  pinned: boolean;
  replyCount: number;
  createdAt: string;
  updatedAt: string;
};

export type ForumCourse = {
  id: string;
  title: string;
  canCreatePost: boolean;
  canPinPost: boolean;
};

export type ForumReply = {
  id: string;
  postId: string;
  courseId: string;
  parentReplyId?: string | null;
  author: ForumAuthor;
  content: string;
  attachments: ForumAttachment[];
  depth: number;
  upvoteCount: number;
  upvoted: boolean;
  replies: ForumReply[];
  createdAt: string;
  updatedAt: string;
};

export type ForumPageResponse<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ForumPostDetail = {
  post: ForumPost;
  replies: ForumReply[];
};

export type GetForumPostsParams = {
  courseId?: string;
  keyword?: string;
  topic?: string;
  page?: number;
  size?: number;
};

export type CreateForumPostPayload = {
  courseId: string;
  topic: string;
  title: string;
  content: string;
  pinned?: boolean;
  attachmentIds?: string[];
};

export type UpdateForumPostPayload = {
  topic: string;
  title: string;
  content: string;
  pinned?: boolean;
  attachmentIds?: string[];
};

export type CreateForumReplyPayload = {
  content: string;
  parentReplyId?: string | null;
  attachmentIds?: string[];
};

export async function getForumPosts(params: GetForumPostsParams = {}) {
  const response = await httpClient.get<ForumPageResponse<ForumPost>>('/forum/posts', { params });
  return response.data;
}

export async function getForumCourses() {
  const response = await httpClient.get<ForumCourse[]>('/forum/courses');
  return response.data;
}

export async function getForumPostDetail(postId: string) {
  const response = await httpClient.get<ForumPostDetail>(`/forum/posts/${postId}`);
  return response.data;
}

export async function createForumPost(payload: CreateForumPostPayload) {
  const response = await httpClient.post<ForumPost>('/forum/posts', payload);
  return response.data;
}

export async function createForumReply(postId: string, payload: CreateForumReplyPayload) {
  const response = await httpClient.post<ForumReply>(`/forum/posts/${postId}/replies`, payload);
  return response.data;
}

export async function updateForumPost(postId: string, payload: UpdateForumPostPayload) {
  const response = await httpClient.patch<ForumPost>(`/forum/posts/${postId}`, payload);
  return response.data;
}

export async function updateForumReply(replyId: string, payload: CreateForumReplyPayload) {
  const response = await httpClient.patch<ForumReply>(`/forum/replies/${replyId}`, payload);
  return response.data;
}

export async function togglePinPost(postId: string) {
  const response = await httpClient.patch<ForumPost>(`/forum/posts/${postId}/pin`);
  return response.data;
}

export async function deleteForumPost(postId: string) {
  await httpClient.delete(`/forum/posts/${postId}`);
}

export async function deleteForumReply(replyId: string) {
  await httpClient.delete(`/forum/replies/${replyId}`);
}

export async function toggleUpvotePost(postId: string) {
  const response = await httpClient.post<ForumPost>(`/forum/posts/${postId}/upvote`);
  return response.data;
}

export async function toggleUpvoteReply(replyId: string) {
  const response = await httpClient.post<ForumReply>(`/forum/replies/${replyId}/upvote`);
  return response.data;
}

export async function uploadForumAttachment(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  const response = await httpClient.post<ForumAttachment>('/forum/attachments', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return { ...response.data, url: toAbsoluteApiUrl(response.data.url) };
}

export type ReportPayload = {
  reason: string;
  description?: string;
};

export async function reportPost(postId: string, payload: ReportPayload) {
  await httpClient.post(`/forum/posts/${postId}/report`, payload);
}

export async function reportReply(replyId: string, payload: ReportPayload) {
  await httpClient.post(`/forum/replies/${replyId}/report`, payload);
}
