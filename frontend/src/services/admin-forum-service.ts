import { httpClient } from '../lib';
import type { ForumAuthor, ForumPageResponse } from './forum-service';

export type AdminForumPost = {
  id: string;
  courseId: string;
  courseTitle: string;
  author: ForumAuthor;
  topic?: string | null;
  title: string;
  contentPreview?: string | null;
  pinned: boolean;
  replyCount: number;
  upvoteCount: number;
  deleted: boolean;
  reportCount: number;
  pendingReportCount: number;
  createdAt: string;
  updatedAt?: string | null;
  deletedAt?: string | null;
};

export type AdminForumReport = {
  id: string;
  targetType: 'POST' | 'REPLY';
  targetId: string;
  targetTitle?: string | null;
  targetContentPreview?: string | null;
  postId?: string | null;
  courseTitle?: string | null;
  reason: string;
  description?: string | null;
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED';
  reporter?: ForumAuthor | null;
  createdAt: string;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  targetDeleted: boolean;
};

export async function getAdminForumPosts(params?: { keyword?: string; reportedOnly?: boolean; includeDeleted?: boolean; page?: number; size?: number }) {
  const response = await httpClient.get<ForumPageResponse<AdminForumPost>>('/admin/forum/posts', { params });
  return response.data;
}

export async function getAdminForumReports(params?: { status?: string; targetType?: string; page?: number; size?: number }) {
  const response = await httpClient.get<ForumPageResponse<AdminForumReport>>('/admin/forum/reports', { params });
  return response.data;
}

export async function reviewAdminForumReport(id: string, data: { status: 'RESOLVED' | 'DISMISSED'; deleteTarget?: boolean }) {
  await httpClient.patch(`/admin/forum/reports/${id}`, data);
}

export async function deleteAdminForumPost(id: string) {
  await httpClient.delete(`/admin/forum/posts/${id}`);
}

export async function deleteAdminForumReply(id: string) {
  await httpClient.delete(`/admin/forum/replies/${id}`);
}
