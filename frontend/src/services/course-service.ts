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
  submittedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

type SpringPage<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export async function getMyCourses() {
  const response = await httpClient.get<SpringPage<CourseResponse>>(
    '/instructor/courses',
    { params: { size: 100 } },
  );
  return response.data;
}
