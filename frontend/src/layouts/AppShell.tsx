// @ts-nocheck
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Navigate, Outlet, useLocation, useMatches, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { NotificationTypeMetadata, getNotificationTargetUrl, parseNotificationUrl } from '../constants/notification-types';
import { markAllAsRead, markAsRead, connectNotificationSSE } from '../services/notification-service';
import { logoutRequest } from '../services';
import { useAuthStore } from '../store';

const Ic = window.Icon;
const Avatar = window.Avatar;

const PERSONA = {
  student: { name: 'Hoàng Văn Em', email: 'em.hoang@gmail.com', role: 'Học viên', sub: 'Hệ thống Học tập' },
  instructor: { name: 'Nguyễn Văn An', email: 'an.nguyen@rikkei.edu', role: 'Giảng viên', sub: 'Cổng Giảng viên' },
  admin: { name: 'Admin Rikkei', email: 'admin@rikkei.edu', role: 'Quản trị viên', sub: 'Hệ thống Quản trị' },
};

const NAV = {
  student: [
    { k: 'dashboard', l: 'Tổng quan', ic: 'grid' },
    { k: 'courses', l: 'Khóa học của tôi', ic: 'cap' },
    { k: 'groups', l: 'Nhóm học', ic: 'layers' },
    { k: 'tasks', l: 'Bài tập & Trắc nghiệm', ic: 'clipboard' },
    { k: 'forum', l: 'Diễn đàn', ic: 'message' },
    { k: 'chat', l: 'Chat nhóm', ic: 'chat' },
    { k: 'certs', l: 'Chứng chỉ', ic: 'award' },
    { k: 'settings', l: 'Cài đặt', ic: 'settings' },
  ],
  instructor: [
    { k: 'dashboard', l: 'Tổng quan', ic: 'grid' },
    { k: 'courses', l: 'Khóa học', ic: 'book' },
    { k: 'groups', l: 'Quản lý Nhóm', ic: 'layers' },
    { k: 'assess', l: 'Bài tập & Trắc nghiệm', ic: 'clipboard' },
    { k: 'grading', l: 'Chấm điểm', ic: 'edit' },
    { k: 'forum', l: 'Diễn đàn', ic: 'message' },
    { k: 'chat', l: 'Chat nhóm', ic: 'chat' },
    { k: 'aiDocs', l: 'Tài liệu AI', ic: 'sparkles' },
    { k: 'settings', l: 'Cài đặt', ic: 'settings' },
  ],
  admin: [
    { k: 'dashboard', l: 'Tổng quan', ic: 'grid' },
    { k: 'users', l: 'Quản lý Người dùng', ic: 'users' },
    { k: 'courses', l: 'Quản lý Khóa học', ic: 'book' },
    { k: 'approval', l: 'Phê duyệt', ic: 'check_circle' },
    { k: 'certificates', l: 'Chứng chỉ', ic: 'award' },
    { k: 'forum', l: 'Quản lý Forum', ic: 'message' },
    { k: 'aiDocs', l: 'Quản lý Tài liệu AI', ic: 'sparkles' },
    { k: 'settings', l: 'Cài đặt', ic: 'settings' },
  ],
};

const SCREENS = {
  student: { dashboard: 'StuDashboard', courses: 'StuCourses', courseDetail: 'StuCourseDetail', groups: 'StuGroups', groupDetail: 'StuGroupDetail', tasks: 'StuTasks', forum: 'ForumPage', chat: 'ChatScreen', certs: 'StuCerts', certDetail: 'StuCertDetail', settings: 'Settings', notifications: 'NotificationsPage' },
  instructor: { dashboard: 'InsDashboard', courses: 'InsCourses', courseDetail: 'InsCourseDetail', groups: 'InsGroups', groupDetail: 'InsGroupDetail', assess: 'InsAssess', grading: 'InsGrading', forum: 'ForumPage', chat: 'ChatScreen', aiDocs: 'InsAiDocs', settings: 'Settings', notifications: 'NotificationsPage' },
  admin: { dashboard: 'AdminDashboard', users: 'AdminUsers', courses: 'AdminCourses', approval: 'AdminApproval', certificates: 'AdminCertificates', forum: 'AdminForum', forumBrowse: 'ForumPage', aiDocs: 'AdminAiDocs', settings: 'Settings', notifications: 'NotificationsPage' },
};

const roleRoutes = {
  student: { dashboard: '/student/dashboard', courses: '/student/courses', courseDetail: '/student/courses/detail', tasks: '/student/tasks', forum: '/student/forum', chat: '/student/chat', certs: '/student/certs', certDetail: '/student/certs', groups: '/student/groups', groupDetail: '/student/groups/detail', settings: '/settings', notifications: '/notifications' },
  instructor: { dashboard: '/instructor/dashboard', courses: '/instructor/courses', courseDetail: '/instructor/courses', groups: '/instructor/groups', groupDetail: '/instructor/groups/detail', assess: '/instructor/assess', grading: '/instructor/grading', forum: '/instructor/forum', chat: '/instructor/chat', aiDocs: '/instructor/ai-docs', settings: '/settings', notifications: '/notifications' },
  admin: { dashboard: '/admin/dashboard', users: '/admin/users', courses: '/admin/courses', approval: '/admin/approval', certificates: '/admin/certificates', forum: '/admin/forum', forumBrowse: '/admin/forum/posts', aiDocs: '/admin/ai-docs', settings: '/settings', notifications: '/notifications' },
};

const playerRoutes = { player: '/player/lecture', quiz: '/player/quiz', result: '/player/quiz-result', preview: '/player/preview', dryRun: '/player/quiz-dry-run' };
const ALIAS = { groupDetail: 'groups', courseDetail: 'courses', certDetail: 'certs', settings: 'settings' };

function dashboardForRole(role) {
  return roleRoutes[role]?.dashboard || '/student/dashboard';
}

function notifMeta(type) {
  return NotificationTypeMetadata[type] || { icon: 'bell', color: '#2563eb', label: 'Hệ thống', category: 'Hệ thống' };
}

function timeAgo(value) {
  if (!value) return '';
  const ms = Date.now() - new Date(value).getTime();
  const min = Math.floor(ms / 60000);
  if (min < 1) return 'Vừa xong';
  if (min < 60) return `${min} phút trước`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr} giờ trước`;
  const day = Math.floor(hr / 24);
  if (day < 7) return `${day} ngày trước`;
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value));
}

export function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const routeParams = useParams();
  const matches = useMatches();
  const matchedHandle = [...matches].reverse().find((match) => match.handle?.route)?.handle || {};
  const authUser = useAuthStore((state) => state.user);
  const currentRole = useAuthStore((state) => state.role) || 'student';
  const route = matchedHandle.route || 'dashboard';
  const routeRole = matchedHandle.role || currentRole;
  const role = route === 'settings' || route === 'notifications' ? currentRole : routeRole;
  const [drawer, setDrawer] = useState(false);
  const [notif, setNotif] = useState(false);
  const [notificationList, setNotificationList] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);

  if (routeRole && route !== 'settings' && route !== 'notifications' && currentRole !== routeRole) {
    return <Navigate to={dashboardForRole(currentRole)} replace />;
  }

  const basePersona = PERSONA[role] || PERSONA.student;
  const persona = authUser ? { ...basePersona, name: authUser.fullName || basePersona.name, email: authUser.email || basePersona.email } : basePersona;
  const params = { ...Object.fromEntries(new URLSearchParams(location.search)), ...routeParams };

  useEffect(() => {
    if (!authUser && !window.useAuthStore?.getState()?.accessToken) return;
    return connectNotificationSSE((item) => {
      setNotificationList((prev) => [item, ...prev]);
    }, undefined, undefined, setUnreadCount, setNotificationList);
  }, [authUser, role]);

  useEffect(() => {
    const close = () => setNotif(false);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

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

  const go = useCallback((key, extra) => {
    let nextParams = { ...(extra || {}) };
    if (key === 'courseDetail' || key === 'player' || key === 'preview') {
      const courseId = nextParams.courseId || window.__selectedCourseId || sessionStorage.getItem('selectedCourseId');
      if (courseId) nextParams.courseId = courseId;
    }
    if (key === 'groupDetail' || key === 'groups') {
      const groupId = nextParams.groupId || window.__selectedGroupId || sessionStorage.getItem('selectedGroupId');
      if (groupId && key === 'groupDetail') nextParams.groupId = groupId;
    }
    if (key === 'approval' || key === 'approvalDetail') {
      const approvalId = nextParams.approvalId || window.__selectedApprovalId || null;
      if (approvalId) nextParams.approvalId = approvalId;
    }
    if (key === 'certDetail' || key === 'certificates') {
      const certificateId = nextParams.certificateId || window.__selectedCertificateId || null;
      if (certificateId && key === 'certDetail') nextParams.certificateId = certificateId;
    }

    if (role === 'student' && key === 'certDetail' && nextParams.certificateId) {
      navigate(`/student/certs/${encodeURIComponent(nextParams.certificateId)}`);
      return;
    }
    if (role === 'student' && (key === 'player' || (key === 'courses' && nextParams.courseId))) {
      navigate(`/player/lecture?courseId=${encodeURIComponent(nextParams.courseId)}`);
      return;
    }
    if (key in playerRoutes) {
      const search = Object.keys(nextParams).length > 0 ? '?' + new URLSearchParams(nextParams).toString() : '';
      navigate(playerRoutes[key] + search);
      return;
    }
    if (role === 'instructor' && key === 'courseDetail' && nextParams.slug) {
      navigate(`/instructor/courses/${encodeURIComponent(nextParams.slug)}${nextParams.autoPreview ? '?autoPreview=1' : ''}`);
      return;
    }
    const path = roleRoutes[role]?.[key] || dashboardForRole(role);
    const search = Object.keys(nextParams).length > 0 ? '?' + new URLSearchParams(nextParams).toString() : '';
    navigate(path + search);
  }, [navigate, role]);

  window.AppShell = { go };

  const handleMarkAsRead = async (id) => {
    try {
      await markAsRead(id);
      setNotificationList((prev) => prev.map((item) => item.id === id ? { ...item, read: true } : item));
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch {
      // Giữ UI hiện tại nếu cập nhật thất bại.
    }
  };

  const handleMarkAllAsRead = async () => {
    try {
      await markAllAsRead();
      setNotificationList((prev) => prev.map((item) => ({ ...item, read: true })));
      setUnreadCount(0);
    } catch {
      // Giữ UI hiện tại nếu cập nhật thất bại.
    }
  };

  const activeKey = ALIAS[route] || route;

  const outletContext = useMemo(() => ({ role, route, go, persona, routeParams: params }), [role, route, go, persona, params]);

  return (
    <div className="app">
      {drawer && <div className="scrim" onClick={() => setDrawer(false)} />}
      <aside className={'sidebar' + (drawer ? ' open' : '')}>
        <div className="sb-brand">
          <div className="sb-logo"><Ic n="cap" size={24} /></div>
          <div className="sb-brand-txt"><div className="nm">Rikkei Edu</div><div className="sub">{persona.sub} LMS</div></div>
        </div>
        <nav className="sb-nav dark-scroll">
          {NAV[role].map((item) => (
            <div key={item.k} className={'sb-item' + (activeKey === item.k ? ' active' : '')} onClick={() => go(item.k)}>
              <Ic n={item.ic} size={20} /><span className="lbl">{item.l}</span>
            </div>
          ))}
        </nav>
        <div className="sb-foot">
          <div className="sb-item" onClick={handleLogout}><Ic n="logout" size={20} /><span className="lbl">Đăng xuất</span></div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button className="icon-btn hamburger" onClick={() => setDrawer(true)}><Ic n="menu" size={20} /></button>
          <div className="grow" />
          <div style={{ position: 'relative' }} onClick={(event) => event.stopPropagation()}>
            <button className="icon-btn" style={unreadCount > 0 ? { background: '#fff1f2', borderColor: '#fecdd3', color: '#e11d48' } : undefined} onClick={() => setNotif(!notif)}>
              <Ic n="bell" size={20} style={unreadCount > 0 ? { color: '#e11d48' } : undefined} />
              {unreadCount > 0 && <span className="badge-count" style={{ background: '#e11d48', borderColor: '#fff' }}>{unreadCount > 99 ? '99+' : unreadCount}</span>}
            </button>
            {notif && (
              <div className="card" style={{ position: 'absolute', right: 0, top: 52, width: 360, maxWidth: '90vw', padding: 0, boxShadow: 'var(--sh-lg)', zIndex: 60, animation: 'popIn .18s' }}>
                <div className="between" style={{ padding: '14px 18px', borderBottom: '1px solid var(--border)' }}><b>Thông báo</b>{unreadCount > 0 && <span className="link" onClick={handleMarkAllAsRead}>Đánh dấu đã đọc</span>}</div>
                <div style={{ maxHeight: 340, overflowY: 'auto' }}>
                  {notificationList.length === 0 && <div className="t-sm muted" style={{ padding: '24px 18px', textAlign: 'center' }}>Chưa có thông báo nào.</div>}
                  {notificationList.slice(0, 5).map((item) => {
                    const meta = notifMeta(item.type);
                    return (
                      <div key={item.id} className="row gap-12" style={{ padding: '13px 16px', background: item.read ? '#fff' : 'var(--accent-soft)', borderBottom: '1px solid var(--border)', cursor: 'pointer' }} onClick={() => {
                        if (!item.read) handleMarkAsRead(item.id);
                        setNotif(false);
                        const targetUrl = getNotificationTargetUrl(item, role);
                        const parsed = parseNotificationUrl(targetUrl);
                        go(parsed.routeKey, Object.keys(parsed.params).length > 0 ? parsed.params : undefined);
                      }}>
                        <div className="stat-ic" style={{ width: 38, height: 38, borderRadius: 10, background: meta.color + '1a', color: meta.color, flex: 'none' }}><Ic n={meta.icon} size={18} /></div>
                        <div className="grow" style={{ minWidth: 0 }}>
                          <div className="row gap-6" style={{ marginBottom: 3 }}><span className="chip" style={{ background: meta.color + '1a', color: meta.color, borderColor: meta.color + '33', fontSize: 10, padding: '1px 5px' }}>{meta.label || 'Hệ thống'}</span></div>
                          <div className="t-sm" style={{ lineHeight: 1.4, fontWeight: item.read ? 400 : 600 }}>{item.title}</div>
                          {item.body && <div className="t-xs muted" style={{ marginTop: 3, lineHeight: 1.3, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.body}</div>}
                          <div className="t-xs dim" style={{ marginTop: 4 }}>{timeAgo(item.createdAt)}</div>
                        </div>
                        {!item.read && <span style={{ width: 8, height: 8, borderRadius: 999, background: 'var(--accent)', flex: 'none' }} />}
                      </div>
                    );
                  })}
                </div>
                <div className="between" style={{ padding: '10px 18px', borderTop: '1px solid var(--border)' }}>
                  <span className="link t-sm" onClick={() => { setNotif(false); go('notifications'); }}>Xem tất cả</span>
                  <span className="t-xs dim">{unreadCount} chưa đọc</span>
                </div>
              </div>
            )}
          </div>
          <div className="user-pill" style={{ cursor: 'default' }}>
            <Avatar name={persona.name} size={34} />
            <div><div className="nm">{persona.name}</div><div className="rl">{persona.role}</div></div>
          </div>
        </header>
        <main style={{ flex: 1 }}><Outlet context={outletContext} /></main>
      </div>
      {(role === 'student' || role === 'instructor') && <window.AIChatbot />}
      {window.AccountLockedOverlay && <window.AccountLockedOverlay />}
    </div>
  );
}

export function RuntimeScreen() {
  const { role, route, go, persona, routeParams } = useOutletContext();
  const Comp = window[SCREENS[role][route]] || window[SCREENS[role].dashboard];
  return <Comp nav={go} persona={persona} groupId={routeParams?.groupId} courseId={routeParams?.courseId} certificateId={routeParams?.certificateId} slug={routeParams?.slug} quizId={routeParams?.quizId} postId={routeParams?.postId || routeParams?.id} />;
}
