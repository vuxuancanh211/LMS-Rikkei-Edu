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
  courseId: string;
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

/* ── Caching & In-Flight Request Deduplication Layer ── */
const inFlightMap = new Map<string, Promise<any>>();
const memoryCache = new Map<string, { data: any; timestamp: number }>();

async function fetchCached<T>(url: string, ttlMs = 120000, forceRefresh = false): Promise<T> {
  if (!forceRefresh) {
    const cached = memoryCache.get(url);
    if (cached && Date.now() - cached.timestamp < ttlMs) {
      return cached.data;
    }
  } else {
    memoryCache.delete(url);
    inFlightMap.delete(url);
  }

  if (inFlightMap.has(url)) {
    return inFlightMap.get(url);
  }

  const promise = httpClient.get<T>(url).then((response) => {
    memoryCache.set(url, { data: response.data, timestamp: Date.now() });
    inFlightMap.delete(url);
    return response.data;
  }).catch((error) => {
    inFlightMap.delete(url);
    throw error;
  });

  inFlightMap.set(url, promise);
  return promise;
}

export function clearDashboardCache() {
  memoryCache.clear();
  inFlightMap.clear();
}

export async function getStudentDashboard(forceRefresh = false): Promise<StudentDashboardResponse> {
  return fetchCached<StudentDashboardResponse>('/student/dashboard', 120000, forceRefresh);
}

export async function getStudentStats(forceRefresh = false): Promise<{ studentName: string; stats: DashboardStats }> {
  return fetchCached<{ studentName: string; stats: DashboardStats }>('/student/dashboard/stats', 120000, forceRefresh);
}

export async function getStudentInProgressCourses(forceRefresh = false): Promise<CourseSummary[]> {
  return fetchCached<CourseSummary[]>('/student/dashboard/in-progress-courses', 120000, forceRefresh);
}

export async function getStudentDueAssignments(forceRefresh = false): Promise<DueAssignment[]> {
  return fetchCached<DueAssignment[]>('/student/dashboard/due-assignments', 120000, forceRefresh);
}

export async function getStudentDueQuizzes(forceRefresh = false): Promise<DueAssignment[]> {
  return fetchCached<DueAssignment[]>('/student/dashboard/due-quizzes', 120000, forceRefresh);
}

export async function getStudentWeeklyStudyHours(forceRefresh = false): Promise<number[]> {
  return fetchCached<number[]>('/student/dashboard/weekly-study-hours', 120000, forceRefresh);
}

export async function getStudentSkillProgress(forceRefresh = false): Promise<SkillProgress[]> {
  return fetchCached<SkillProgress[]>('/student/dashboard/skill-progress', 120000, forceRefresh);
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

export async function getInstructorDashboard(forceRefresh = false): Promise<InstructorDashboardResponse> {
  return fetchCached<InstructorDashboardResponse>('/instructor/dashboard', 120000, forceRefresh);
}

export async function getInstructorStats(forceRefresh = false): Promise<{
  activeCoursesCount: number;
  pendingCoursesCount: number;
  totalStudentsCount: number;
  totalGroupsCount: number;
  pendingSubmissionsCount: number;
  averageCompletionRate: number;
}> {
  return fetchCached('/instructor/dashboard/stats', 120000, forceRefresh);
}

export async function getInstructorCompletionChart(forceRefresh = false): Promise<{
  monthlyCompletionRates: number[];
  monthlyLabels: string[];
}> {
  return fetchCached('/instructor/dashboard/completion-chart', 120000, forceRefresh);
}

export async function getInstructorCourseDistributions(forceRefresh = false): Promise<{
  averageCompletionRate: number;
  courseDistributions: CourseDistribution[];
}> {
  return fetchCached('/instructor/dashboard/course-distributions', 120000, forceRefresh);
}

export async function getInstructorPendingSubmissions(forceRefresh = false): Promise<PendingSubmission[]> {
  return fetchCached<PendingSubmission[]>('/instructor/dashboard/pending-submissions', 120000, forceRefresh);
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

export async function getAdminDashboard(forceRefresh = false): Promise<AdminDashboardResponse> {
  return fetchCached<AdminDashboardResponse>('/admin/dashboard', 120000, forceRefresh);
}

export async function getAdminStats(forceRefresh = false): Promise<{
  totalStudentsCount: number;
  totalInstructorsCount: number;
  activeCoursesCount: number;
  averageCompletionRate: number;
}> {
  return fetchCached('/admin/dashboard/stats', 120000, forceRefresh);
}

export async function getAdminTrafficChart(forceRefresh = false): Promise<{
  trafficData: number[];
  trafficLabels: string[];
  weeklyTrafficData?: number[];
  weeklyTrafficLabels?: string[];
}> {
  return fetchCached('/admin/dashboard/traffic', 120000, forceRefresh);
}

export async function getAdminCoursesChart(forceRefresh = false): Promise<{
  newCoursesData: number[];
  newCoursesLabels: string[];
  weeklyCoursesData?: number[];
  weeklyCoursesLabels?: string[];
}> {
  return fetchCached('/admin/dashboard/courses-chart', 120000, forceRefresh);
}

export async function getAdminUsersChart(forceRefresh = false): Promise<{
  newUsersData: number[];
  newUsersLabels: string[];
  weeklyUsersData?: number[];
  weeklyUsersLabels?: string[];
}> {
  return fetchCached('/admin/dashboard/users-chart', 120000, forceRefresh);
}

export async function getAdminEnrollmentsChart(forceRefresh = false): Promise<{
  enrollmentsData: number[];
  enrollmentsLabels: string[];
  weeklyEnrollmentsData?: number[];
  weeklyEnrollmentsLabels?: string[];
}> {
  return fetchCached('/admin/dashboard/enrollments-chart', 120000, forceRefresh);
}

export async function getAdminPendingApprovals(forceRefresh = false): Promise<PendingApproval[]> {
  return fetchCached<PendingApproval[]>('/admin/dashboard/pending-approvals', 120000, forceRefresh);
}

export async function getAdminRecentActivities(forceRefresh = false): Promise<SystemActivity[]> {
  return fetchCached<SystemActivity[]>('/admin/dashboard/recent-activities', 120000, forceRefresh);
}
