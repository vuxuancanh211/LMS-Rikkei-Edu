import '../providers/register-modules';

import type { ReactNode } from 'react';
import { createBrowserRouter, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { CertificateVerifyPage } from '../pages/CertificateVerifyPage';
import { AppShell, RuntimeScreen } from '../layouts/AppShell';
import { logoutRequest } from '../services';
import { mapApiRole, useAuthStore } from '../store';

const roleRoutes = {
  student: { dashboard: '/student/dashboard' },
  instructor: { dashboard: '/instructor/dashboard' },
  admin: { dashboard: '/admin/dashboard' },
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

function PlayerRoute({ name }: { name: string }) {
  const navigate = useNavigate();
  const location = useLocation();
  const authUser = useAuthStore((state) => state.user);
  const Comp = window[name];

  if (!Comp) return <MissingRuntime name={name} />;

  const handleLogout = async () => {
    try {
      await logoutRequest();
    } catch {
      // Vẫn xóa phiên local nếu server logout lỗi.
    } finally {
      useAuthStore.getState().logout();
      navigate('/login', { replace: true });
    }
  };

  const params = Object.fromEntries(new URLSearchParams(location.search));
  let returnPath = '/student/courses';
  if (name === 'QuizPlayer' || name === 'QuizResult') {
    returnPath = params.from === 'lecture' && params.courseId
      ? `/player/lecture?courseId=${params.courseId}${params.lessonId ? '&lessonId=' + params.lessonId : ''}`
      : '/student/tasks';
  } else if (name === 'LecturePlayer') {
    returnPath = params.from === 'dashboard' ? '/student/dashboard' : '/student/courses';
  } else if (name === 'QuizDryRunPlayer') {
    returnPath = params.courseId ? `/instructor/assess?courseId=${params.courseId}` : '/instructor/assess';
  } else if (name === 'PreviewPlayer') {
    returnPath = '/instructor/courses';
  }

  return (
    <div className="app">
      <Comp
        {...params}
        proctoringEnabled={params.proctoringEnabled === 'true'}
        authUser={authUser}
        onBack={() => navigate(returnPath)}
        onDashboard={() => navigate('/student/dashboard')}
        onSettings={() => navigate('/settings')}
        onLogout={handleLogout}
        navigate={navigate}
        onSubmit={(attemptId: string, courseId: string, quizId: string) => {
          const extra = [
            params.from ? `from=${params.from}` : '',
            params.lessonId ? `lessonId=${params.lessonId}` : '',
          ].filter(Boolean).join('&');
          navigate(`/player/quiz-result?attemptId=${attemptId}&courseId=${courseId}&quizId=${quizId}${extra ? '&' + extra : ''}`);
        }}
      />
    </div>
  );
}

const screen = (role: string | null, route: string) => ({ element: <RuntimeScreen />, handle: { role, route } });

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <PublicOnly><LoginRoute /></PublicOnly> },
  { path: '/forgot-password', element: <ForgotPasswordRoute /> },
  { path: '/reset-password', element: <ResetPasswordRoute /> },
  { path: '/verify/:code', element: <CertificateVerifyPage /> },
  {
    element: <RequireAuth><AppShell /></RequireAuth>,
    children: [
      { path: '/settings', ...screen(null, 'settings') },
      { path: '/notifications', ...screen(null, 'notifications') },
      { path: '/student/dashboard', ...screen('student', 'dashboard') },
      { path: '/student/courses', ...screen('student', 'courses') },
      { path: '/student/courses/detail', ...screen('student', 'courseDetail') },
      { path: '/student/tasks', ...screen('student', 'tasks') },
      { path: '/student/forum', ...screen('student', 'forum') },
      { path: '/student/chat', ...screen('student', 'chat') },
      { path: '/student/certs', ...screen('student', 'certs') },
      { path: '/student/certs/:certificateId', ...screen('student', 'certDetail') },
      { path: '/student/groups', ...screen('student', 'groups') },
      { path: '/student/groups/detail', ...screen('student', 'groupDetail') },
      { path: '/instructor/dashboard', ...screen('instructor', 'dashboard') },
      { path: '/instructor/courses', ...screen('instructor', 'courses') },
      { path: '/instructor/courses/:slug', ...screen('instructor', 'courseDetail') },
      { path: '/instructor/groups', ...screen('instructor', 'groups') },
      { path: '/instructor/groups/detail', ...screen('instructor', 'groupDetail') },
      { path: '/instructor/assess', ...screen('instructor', 'assess') },
      { path: '/instructor/grading', ...screen('instructor', 'grading') },
      { path: '/instructor/forum', ...screen('instructor', 'forum') },
      { path: '/instructor/chat', ...screen('instructor', 'chat') },
      { path: '/instructor/ai-docs', ...screen('instructor', 'aiDocs') },
      { path: '/admin/dashboard', ...screen('admin', 'dashboard') },
      { path: '/admin/users', ...screen('admin', 'users') },
      { path: '/admin/courses', ...screen('admin', 'courses') },
      { path: '/admin/approval', ...screen('admin', 'approval') },
      { path: '/admin/certificates', ...screen('admin', 'certificates') },
      { path: '/admin/forum', ...screen('admin', 'forum') },
      { path: '/admin/forum/posts', ...screen('admin', 'forumBrowse') },
      { path: '/admin/ai-docs', ...screen('admin', 'aiDocs') },
    ],
  },
  { path: '/player/lecture', element: <RequireAuth><PlayerRoute name="LecturePlayer" /></RequireAuth> },
  { path: '/player/quiz', element: <RequireAuth><PlayerRoute name="QuizPlayer" /></RequireAuth> },
  { path: '/player/quiz-result', element: <RequireAuth><PlayerRoute name="QuizResult" /></RequireAuth> },
  { path: '/player/preview', element: <RequireAuth><PlayerRoute name="PreviewPlayer" /></RequireAuth> },
  { path: '/player/quiz-dry-run', element: <RequireAuth><PlayerRoute name="QuizDryRunPlayer" /></RequireAuth> },
  { path: '*', element: <Navigate to="/login" replace /> },
]);
