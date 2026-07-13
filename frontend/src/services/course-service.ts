import { httpClient } from '../lib';

export type CourseResponse = {
  id: string;
  title: string;
  slug: string;
  status: string;
  level: string;
  thumbnailUrl?: string | null;
  description?: string | null;
  chatEnabled?: boolean;
  instructorId: string;
  instructorName?: string | null;
  submittedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type CourseDetailResponse = CourseResponse & {
  rejectionReason?: string | null;
  publishedAt?: string | null;
  pendingUpdateAt?: string | null;
  category?: { id: string; name: string; slug: string } | null;
  chapters?: Array<{
    id: string;
    title: string;
    orderIndex?: number;
    lessons?: Array<{
      id: string;
      title: string;
      orderIndex?: number;
      contentText?: string | null;
      resources?: Array<{ id: string; fileName?: string; type?: string }>;
    }>;
  }>;
};

type SpringPage<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export async function getCourses(params?: { size?: number }) {
  const response = await httpClient.get<SpringPage<CourseResponse>>(
    '/admin/courses',
    { params: { ...params, size: params?.size ?? 100 } },
  );
  return response.data;
}

export async function getCourseDetail(courseId: string) {
  const response = await httpClient.get<CourseDetailResponse>(`/admin/courses/${courseId}`);
  return response.data;
}

export async function getMyCourses() {
  const response = await httpClient.get<SpringPage<CourseResponse>>(
    '/instructor/courses',
    { params: { size: 100 } },
  );
  return response.data;
}
