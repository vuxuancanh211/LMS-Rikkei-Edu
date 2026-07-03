import '../providers/register-modules';

import type { ReactNode } from 'react';
import { createBrowserRouter, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { logoutRequest } from '../services';
import { mapApiRole, useAuthStore } from '../store';

const roleRoutes = {
  student: {
    dashboard: '/student/dashboard',
    courses: '/student/courses',
    tasks: '/student/tasks',
    forum: '/student/forum',
    chat: '/student/chat',
    certs: '/student/certs',
    settings: '/settings',
    notifications: '/notifications',
  },
  instructor: {
    dashboard: '/instructor/dashboard',
    courses: '/instructor/courses',
    courseDetail: '/instructor/courses/detail',
    groups: '/instructor/groups',
    groupDetail: '/instructor/groups/detail',
    assess: '/instructor/assess',
    grading: '/instructor/grading',
    students: '/instructor/students',
    forum: '/instructor/forum',
    chat: '/instructor/chat',
    aiDocs: '/instructor/ai-docs',
    settings: '/settings',
    notifications: '/notifications',
  },
  admin: {
    dashboard: '/admin/dashboard',
    users: '/admin/users',
    courses: '/admin/courses',
    approval: '/admin/approval',
    reports: '/admin/reports',
    logs: '/admin/logs',
    aiDocs: '/admin/ai-docs',
    settings: '/settings',
    notifications: '/notifications',
  },
};

const playerRoutes = {
  player: '/player/lecture',
  quiz: '/player/quiz',
  result: '/player/quiz-result',
  preview: '/player/preview',
};

function dashboardForRole(role: keyof typeof roleRoutes | null) {
  if (!role) return '/student/dashboard';
  return roleRoutes[role]?.dashboard || '/student/dashboard';
}

function RequireAuth({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}

function PublicOnly({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const role = useAuthStore((state) => state.role);

  if (isAuthenticated) {
    return <Navigate to={dashboardForRole(role)} replace />;
  }

  return children;
}

function MissingRuntime({ name }: { name: string }) {
  return <div style={{ padding: 24 }}>Không tìm thấy runtime component: {name}</div>;
}

function RoutedShell({ role, route }: { role: keyof typeof roleRoutes; route: string }) {
  const navigate = useNavigate();
  const location = useLocation();
  const authUser = useAuthStore((state) => state.user);
  const currentRole = useAuthStore((state) => state.role);
  const AppShell = window.AppShell;

  if (!AppShell) return <MissingRuntime name="AppShell" />;

  if (currentRole && route !== 'settings' && currentRole !== role) {
    return <Navigate to={dashboardForRole(currentRole)} replace />;
  }

  const handleLogout = async () => {
    try {
      await logoutRequest();
    } catch {
      // Still clear the local session if the server-side logout fails.
    } finally {
      useAuthStore.getState().logout();
      navigate('/login', { replace: true });
    }
  };

  return (
    <AppShell
      key={`${role}:${route}:${location.pathname}`}
      role0={role}
      route0={route}
      authUser={authUser}
      onLogout={handleLogout}
      onExit={() => navigate('/gallery')}
      onBare={(key: keyof typeof playerRoutes) => navigate(playerRoutes[key] || '/player/lecture')}
      onNavigate={(nextRole: keyof typeof roleRoutes, nextRoute: string) => {
        const path = roleRoutes[nextRole]?.[nextRoute as keyof (typeof roleRoutes)[typeof nextRole]];
        navigate(path || '/gallery');
      }}
    />
  );
}

function LoginRoute() {
  const navigate = useNavigate();
  const LoginScreen = window.LoginScreen;

  if (!LoginScreen) return <MissingRuntime name="LoginScreen" />;

  return (
    <LoginScreen
      onForgot={() => navigate('/forgot-password')}
      onLogin={(apiRole: string) => {
        navigate(dashboardForRole(mapApiRole(apiRole)), { replace: true });
      }}
    />
  );
}

function ForgotPasswordRoute() {
  const navigate = useNavigate();
  const ForgotPasswordScreen = window.ForgotPasswordScreen;

  if (!ForgotPasswordScreen) return <MissingRuntime name="ForgotPasswordScreen" />;

  return <ForgotPasswordScreen onBack={() => navigate('/login')} />;
}

function ResetPasswordRoute() {
  const navigate = useNavigate();
  const location = useLocation();
  const ResetPasswordScreen = window.ResetPasswordScreen;
  const token = new URLSearchParams(location.search).get('token') || '';

  if (!ResetPasswordScreen) return <MissingRuntime name="ResetPasswordScreen" />;

  return <ResetPasswordScreen token={token} onBack={() => navigate('/login')} />;
}

function GalleryRoute() {
  const GalleryPage = window.GalleryPage;
  return GalleryPage ? <GalleryPage /> : <MissingRuntime name="GalleryPage" />;
}

function SettingsRoute() {
  const role = useAuthStore((state) => state.role) || 'student';
  return <RoutedShell role={role} route="settings" />;
}

function NotificationsRoute() {
  const role = useAuthStore((state) => state.role) || 'student';
  return <RoutedShell role={role} route="notifications" />;
}

function PlayerRoute({ name }: { name: string }) {
  const navigate = useNavigate();
  const Comp = window[name];

  if (!Comp) return <MissingRuntime name={name} />;

  return (
    <div className="app">
      <Comp onBack={() => navigate(-1)} onSubmit={() => navigate('/player/quiz-result')} />
    </div>
  );
}

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <PublicOnly><LoginRoute /></PublicOnly> },
  { path: '/forgot-password', element: <ForgotPasswordRoute /> },
  { path: '/reset-password', element: <ResetPasswordRoute /> },
  { path: '/gallery', element: <GalleryRoute /> },
  { path: '/settings', element: <RequireAuth><SettingsRoute /></RequireAuth> },
  { path: '/student/dashboard', element: <RequireAuth><RoutedShell role="student" route="dashboard" /></RequireAuth> },
  { path: '/student/courses', element: <RequireAuth><RoutedShell role="student" route="courses" /></RequireAuth> },
  { path: '/student/tasks', element: <RequireAuth><RoutedShell role="student" route="tasks" /></RequireAuth> },
  { path: '/student/forum', element: <RequireAuth><RoutedShell role="student" route="forum" /></RequireAuth> },
  { path: '/student/chat', element: <RequireAuth><RoutedShell role="student" route="chat" /></RequireAuth> },
  { path: '/student/certs', element: <RequireAuth><RoutedShell role="student" route="certs" /></RequireAuth> },
  { path: '/instructor/dashboard', element: <RequireAuth><RoutedShell role="instructor" route="dashboard" /></RequireAuth> },
  { path: '/instructor/courses', element: <RequireAuth><RoutedShell role="instructor" route="courses" /></RequireAuth> },
  { path: '/instructor/courses/detail', element: <RequireAuth><RoutedShell role="instructor" route="courseDetail" /></RequireAuth> },
  { path: '/instructor/groups', element: <RequireAuth><RoutedShell role="instructor" route="groups" /></RequireAuth> },
  { path: '/instructor/groups/detail', element: <RequireAuth><RoutedShell role="instructor" route="groupDetail" /></RequireAuth> },
  { path: '/instructor/assess', element: <RequireAuth><RoutedShell role="instructor" route="assess" /></RequireAuth> },
  { path: '/instructor/grading', element: <RequireAuth><RoutedShell role="instructor" route="grading" /></RequireAuth> },
  { path: '/instructor/students', element: <RequireAuth><RoutedShell role="instructor" route="students" /></RequireAuth> },
  { path: '/instructor/forum', element: <RequireAuth><RoutedShell role="instructor" route="forum" /></RequireAuth> },
  { path: '/instructor/chat', element: <RequireAuth><RoutedShell role="instructor" route="chat" /></RequireAuth> },
  { path: '/instructor/ai-docs', element: <RequireAuth><RoutedShell role="instructor" route="aiDocs" /></RequireAuth> },
  { path: '/notifications', element: <RequireAuth><NotificationsRoute /></RequireAuth> },
  { path: '/admin/dashboard', element: <RequireAuth><RoutedShell role="admin" route="dashboard" /></RequireAuth> },
  { path: '/admin/users', element: <RequireAuth><RoutedShell role="admin" route="users" /></RequireAuth> },
  { path: '/admin/courses', element: <RequireAuth><RoutedShell role="admin" route="courses" /></RequireAuth> },
  { path: '/admin/approval', element: <RequireAuth><RoutedShell role="admin" route="approval" /></RequireAuth> },
  { path: '/admin/reports', element: <RequireAuth><RoutedShell role="admin" route="reports" /></RequireAuth> },
  { path: '/admin/logs', element: <RequireAuth><RoutedShell role="admin" route="logs" /></RequireAuth> },
  { path: '/admin/ai-docs', element: <RequireAuth><RoutedShell role="admin" route="aiDocs" /></RequireAuth> },
  { path: '/player/lecture', element: <RequireAuth><PlayerRoute name="LecturePlayer" /></RequireAuth> },
  { path: '/player/quiz', element: <RequireAuth><PlayerRoute name="QuizPlayer" /></RequireAuth> },
  { path: '/player/quiz-result', element: <RequireAuth><PlayerRoute name="QuizResult" /></RequireAuth> },
  { path: '/player/preview', element: <RequireAuth><PlayerRoute name="PreviewPlayer" /></RequireAuth> },
  { path: '*', element: <Navigate to="/login" replace /> },
]);
