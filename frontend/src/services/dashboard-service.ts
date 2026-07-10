import { httpClient } from '../lib/axios';

export interface DashboardStats {
  activeCoursesCount: number;
  nearCompletionCoursesCount: number;
  pendingTasksCount: number;
  dueSoonTasksCount: number;
  certificatesCount: number;
  weeklyHours: number;
  weeklyHoursTrend?: number | null;
}

export interface CourseSummary {
  id: string;
  title: string;
  category: string;
  thumbnailUrl: string;
  progress: number;
}

export interface DueAssignment {
  id: string;
  title: string;
  type: string;
  deadline: string;
  status: 'pending' | 'late';
}

export interface SkillProgress {
  title: string;
  progress: number;
}

export interface StudentDashboardResponse {
  studentName: string;
  stats: DashboardStats;
  inProgressCourses: CourseSummary[];
  dueAssignments: DueAssignment[];
  weeklyStudyHours: number[];
  skillProgress: SkillProgress[];
}

export async function getStudentDashboard(): Promise<StudentDashboardResponse> {
  const response = await httpClient.get<StudentDashboardResponse>('/student/dashboard');
  return response.data;
}

export interface CourseDistribution {
  title: string;
  studentCount: number;
  color: string;
}

export interface PendingSubmission {
  id: string;
  studentName: string;
  assignmentTitle: string;
  groupName: string;
  submittedAt: string;
  status: string;
}

export interface InstructorDashboardResponse {
  activeCoursesCount: number;
  pendingCoursesCount: number;
  totalStudentsCount: number;
  totalGroupsCount: number;
  pendingSubmissionsCount: number;
  monthlyCompletionRates: number[];
  monthlyLabels: string[];
  averageCompletionRate: number;
  courseDistributions: CourseDistribution[];
  pendingSubmissions: PendingSubmission[];
}

export async function getInstructorDashboard(): Promise<InstructorDashboardResponse> {
  const response = await httpClient.get<InstructorDashboardResponse>('/instructor/dashboard');
  return response.data;
}

export interface PendingApproval {
  id: string;
  courseName: string;
  instructorName: string;
  submittedDate: string;
  status: string;
}

export interface SystemActivity {
  id: string;
  who: string;
  act: string;
  time: string;
  type: string;
}

export interface AdminDashboardResponse {
  totalStudentsCount: number;
  totalInstructorsCount: number;
  activeCoursesCount: number;
  averageCompletionRate: number;
  trafficData: number[];
  trafficLabels: string[];
  newCoursesData: number[];
  newCoursesLabels: string[];
  pendingApprovals: PendingApproval[];
  recentActivities: SystemActivity[];
}

export async function getAdminDashboard(): Promise<AdminDashboardResponse> {
  const response = await httpClient.get<AdminDashboardResponse>('/admin/dashboard');
  return response.data;
}

