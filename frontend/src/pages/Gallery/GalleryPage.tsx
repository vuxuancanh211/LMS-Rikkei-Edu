// @ts-nocheck
/* ============================================================
   RIKKEI EDU LMS — Screen Gallery mount (Vite/React build)
   Trang chính: THƯ VIỆN MÀN HÌNH liệt kê tất cả màn hình + popup.
   Bấm 1 thẻ -> mở màn hình đó full-viewport (responsive, tương tác thật).
   Trong mỗi màn vẫn điều hướng & mở popup được; "← Thư viện" để quay lại.
   ============================================================ */
import {
  getNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  connectNotificationSSE,
} from '../../services/notification-service';
import { login } from '../../services/auth-service';
import { useAuthStore, mapApiRole } from '../../store';
import { NotificationTypeMetadata, getNotificationTargetUrl, parseNotificationUrl } from '../../constants/notification-types';

/* Chuyển nhanh giữa các tài khoản seed sẵn khi phát triển/kiểm thử — chỉ hiện khi
   chạy `vite dev` (import.meta.env.DEV), không bao giờ lọt vào bản build production. */
const DEV_QUICK_ACCOUNTS = [
  { label: 'Admin', email: 'admin@rikkei.edu' },
  { label: 'Giảng viên 1', email: 'instructor1@rikkei.edu' },
  { label: 'Giảng viên 2', email: 'instructor2@rikkei.edu' },
  { label: 'Học viên 1', email: 'student1@rikkei.edu' },
  { label: 'Học viên 2', email: 'student2@rikkei.edu' },
  { label: 'Học viên 3', email: 'student3@rikkei.edu' },
];
const DEV_QUICK_PASSWORD = '123456'; // khớp mật khẩu seed thật (V2__seed_data.sql), không đổi DB

function registerGalleryPage() {
  const { useState, useEffect, useCallback, useMemo } = React;
  const Ic = window.Icon;
  const D = window.DATA;
  const Avatar = window.Avatar;

  const PERSONA = {
    student: { name: "Hoàng Văn Em", email: "em.hoang@gmail.com", role: "Học viên", sub: "Hệ thống Học tập" },
    instructor: { name: "Nguyễn Văn An", email: "an.nguyen@rikkei.edu", role: "Giảng viên", sub: "Cổng Giảng viên" },
    admin: { name: "Admin Rikkei", email: "admin@rikkei.edu", role: "Super Admin", sub: "Hệ thống Quản trị" },
  };
  const NAV = {
    student: [
      { k: "dashboard", l: "Tổng quan", ic: "grid" },
      { k: "courses", l: "Khóa học của tôi", ic: "cap" },
      { k: "groups", l: "Nhóm học", ic: "layers" },
      { k: "tasks", l: "Bài tập & Quiz", ic: "clipboard" },
      { k: "forum", l: "Diễn đàn", ic: "message" },
      { k: "chat", l: "Chat nhóm", ic: "chat" },
      { k: "certs", l: "Chứng chỉ", ic: "award" },
      { k: "settings", l: "Cài đặt", ic: "settings" },
    ],
    instructor: [
      { k: "dashboard", l: "Tổng quan", ic: "grid" },
      { k: "courses", l: "Khóa học", ic: "book" },
      { k: "groups", l: "Quản lý Nhóm", ic: "layers" },
      { k: "assess", l: "Bài tập & Quiz", ic: "clipboard" },
      { k: "grading", l: "Chấm điểm", ic: "edit" },
      { k: "students", l: "Học viên", ic: "users" },
      { k: "forum", l: "Diễn đàn", ic: "message" },
      { k: "chat", l: "Chat nhóm", ic: "chat" },
      { k: "aiDocs", l: "Tài liệu AI", ic: "sparkles" },
      { k: "settings", l: "Cài đặt", ic: "settings" },
    ],
    admin: [
      { k: "dashboard", l: "Tổng quan", ic: "grid" },
      { k: "users", l: "Quản lý Người dùng", ic: "users" },
      { k: "courses", l: "Quản lý Khóa học", ic: "book" },
      { k: "approval", l: "Phê duyệt", ic: "check_circle" },
      { k: "certificates", l: "Chứng chỉ", ic: "award" },
      // { k: "reports", l: "Báo cáo", ic: "chart" },
      { k: "logs", l: "Nhật ký hệ thống", ic: "file" },
      { k: "aiDocs", l: "Quản lý Tài liệu AI", ic: "sparkles" },
      { k: "settings", l: "Cài đặt", ic: "settings" },
    ],
  };
  const SCREENS = {
    student: { dashboard: "StuDashboard", courses: "StuCourses", courseDetail: "StuCourseDetail", groups: "StuGroups", groupDetail: "StuGroupDetail", tasks: "StuTasks", forum: "ForumPage", chat: "ChatScreen", certs: "StuCerts", settings: "Settings", notifications: "NotificationsPage" },
    instructor: { dashboard: "InsDashboard", courses: "InsCourses", courseDetail: "InsCourseDetail", groups: "InsGroups", groupDetail: "InsGroupDetail", assess: "InsAssess", grading: "InsGrading", students: "InsStudents", forum: "ForumPage", chat: "ChatScreen", aiDocs: "InsAiDocs", settings: "Settings", notifications: "NotificationsPage" },
    admin: { dashboard: "AdminDashboard", users: "AdminUsers", courses: "AdminCourses", approval: "AdminApproval", certificates: "AdminCertificates", reports: "AdminReports", logs: "AdminLogs", aiDocs: "AdminAiDocs", settings: "Settings", notifications: "NotificationsPage" },
  };
  const FULLBARE = { player: "LecturePlayer", quiz: "QuizPlayer", result: "QuizResult", preview: "PreviewPlayer", dryRun: "QuizDryRunPlayer" };
  const ROLES = [["student", "Học viên"], ["instructor", "Giảng viên"], ["admin", "Quản trị"]];
  const ALIAS = { groupDetail: "groups", courseDetail: "courses", certDetail: "certs", certificates: "certs", settings: "settings" };
  const DETAIL_ALIAS = { courses: "courseDetail", groups: "groupDetail" };

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

  /* ---------- App shell used inside the gallery (one role, full nav) ---------- */
  function Shell({ role0, route0, demo0, authUser, onExit, onBare, onNavigate, onLogout, routeParams: routeParamsProp }) {
    const [role, setRole] = useState(role0);
    const basePersona = PERSONA[role];
    const persona = authUser ? {
      ...basePersona,
      name: authUser.fullName || basePersona.name,
      email: authUser.email || basePersona.email,
    } : basePersona;
    const [route, setRoute] = useState(route0 || "dashboard");
    const [demo, setDemo] = useState(demo0 || null);
    const [routeParams, setRouteParams] = useState(routeParamsProp || {});
    useEffect(() => { setRouteParams(routeParamsProp || {}); }, [routeParamsProp]);
    const [back, setBack] = useState("dashboard");
    const [drawer, setDrawer] = useState(false);
    const [notif, setNotif] = useState(false);
    const [umenu, setUmenu] = useState(false);
    const [notificationList, setNotificationList] = useState([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [notifLoading, setNotifLoading] = useState(false);
    const [switchingAccount, setSwitchingAccount] = useState(null);
    const [switchAlert, setSwitchAlert] = useState(null); // { title, message }
    const loginSuccess = useAuthStore((state) => state.loginSuccess);

    const loadNotifications = async (showLoading = true) => {
      if (showLoading) setNotifLoading(true);
      try {
        const data = await getNotifications(0, 20);
        setNotificationList(data.content || []);
        const count = await getUnreadCount();
        setUnreadCount(count);
      } catch {
        // Notification failures should not block the shell layout.
      } finally {
        if (showLoading) setNotifLoading(false);
      }
    };

    useEffect(() => {
      if (!authUser && !window.useAuthStore?.getState()?.accessToken) return;
      loadNotifications();
    }, [authUser]);

    useEffect(() => {
      if (!authUser && !window.useAuthStore?.getState()?.accessToken) return;
      const disconnect = connectNotificationSSE((notif) => {
        setNotificationList(prev => [notif, ...prev]);
        setUnreadCount(prev => prev + 1);
      }, undefined, () => loadNotifications(false));
      return disconnect;
    }, [authUser]);

    const handleMarkAsRead = async (id) => {
      try {
        await markAsRead(id);
        setNotificationList(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
        setUnreadCount(prev => Math.max(0, prev - 1));
      } catch {
        // Keep the optimistic UI unchanged if the request fails.
      }
    };

    const handleMarkAllAsRead = async () => {
      try {
        await markAllAsRead();
        setNotificationList(prev => prev.map(n => ({ ...n, read: true })));
        setUnreadCount(0);
      } catch {
        // Keep the optimistic UI unchanged if the request fails.
      }
    };

    useEffect(() => {
      const close = () => { setNotif(false); setUmenu(false); };
      window.addEventListener("click", close);
      return () => window.removeEventListener("click", close);
    }, []);

    const go = useCallback((k, params) => {
      setDemo(null);
      let p = { ...params };
      if (k === 'courseDetail' || k === 'player' || k === 'preview') {
        // Chỉ fallback về courseId đã chọn trước đó (window/sessionStorage) cho các key vốn đã
        // mang ý định "vào 1 khóa cụ thể". "courses" là điều hướng tới DANH SÁCH khóa học — nếu
        // không có courseId tường minh (VD bấm mục "Khóa học của tôi" ở sidebar) thì không được
        // tự ý gắn courseId cũ vào, nếu không sẽ bị redirect nhầm sang màn học 1 khóa đã xem lần
        // trước (có thể học viên không còn đăng ký/không liên quan) thay vì hiện đúng danh sách.
        const cid = p.courseId || window.__selectedCourseId || sessionStorage.getItem("selectedCourseId");
        if (cid) p.courseId = cid;
      }
      if (k === 'groupDetail' || k === 'groups') {
        const gid = p.groupId || window.__selectedGroupId || sessionStorage.getItem("selectedGroupId");
        if (gid && k === 'groupDetail') p.groupId = gid;
      }
      let aliasKey = onNavigate ? k : (ALIAS[k] || k);
      if (p && Object.keys(p).length > 0) {
        const hasPathId = Object.keys(p).some(key => key === 'id' || key.endsWith('Id'));
        if (hasPathId && !onNavigate) {
          const detailKey = DETAIL_ALIAS[aliasKey] || aliasKey + 'Detail';
          if (SCREENS[role][detailKey]) aliasKey = detailKey;
        }
      }
      if (role === 'student' && (aliasKey === 'courseDetail' || (aliasKey === 'courses' && p.courseId))) {
        aliasKey = 'player';
      }
      if (FULLBARE[aliasKey]) { if (onBare) { onBare(aliasKey, p); return; } setBack(SCREENS[role][route] ? route : "dashboard"); setRoute(aliasKey); setRouteParams(p); setDrawer(false); const m = document.querySelector(".main"); if (m) m.scrollTop = 0; return; }
      if (SCREENS[role][aliasKey]) { if (onNavigate) { onNavigate(role, aliasKey, p); return; } setRouteParams(p); setRoute(aliasKey); setDrawer(false); const m = document.querySelector(".main"); if (m) m.scrollTop = 0; }
      if (onNavigate) { onNavigate(role, aliasKey, p); return; }
    }, [role, route, onNavigate, onBare]);
    window.AppShell.go = go;
    const switchRole = (r) => {
      setDemo(null);
      setDrawer(false);
      if (onNavigate) { onNavigate(r, "dashboard"); return; }
      setRole(r);
      setRoute("dashboard");
    };

    const quickSwitchAccount = async (email) => {
      if (switchingAccount) return;
      setSwitchingAccount(email);
      try {
        const response = await login({ email, password: DEV_QUICK_PASSWORD });
        loginSuccess(response, response.user);
        setUmenu(false);
        switchRole(mapApiRole(response.user.role));
      } catch (e) {
        setSwitchAlert({
          title: "Đăng nhập thất bại",
          message: `Không thể đăng nhập thử "${email}": ${e?.response?.data?.message || e?.message || "lỗi không xác định"}`,
        });
      } finally {
        setSwitchingAccount(null);
      }
    };

    if (FULLBARE[route]) {
      const Comp = window[FULLBARE[route]];
      const bareProps = {};
      if (route === "player" && routeParams.courseId) {
        window.__lectureCourse = { courseId: routeParams.courseId };
      }
      return <div className="app"><Comp onBack={() => setRoute(back)} onSubmit={() => setRoute("result")} {...bareProps} /></div>;
    }

    const resolvedRoute = ALIAS[route] || route;
    const activeKey = resolvedRoute;
    const Comp = window[SCREENS[role][route]] || window[SCREENS[role].dashboard];

    return (
      <div className="app">
        {drawer && <div className="scrim" onClick={() => setDrawer(false)} />}
        <aside className={"sidebar" + (drawer ? " open" : "")}>
          <div className="sb-brand">
            <div className="sb-logo"><Ic n="cap" size={24} /></div>
            <div className="sb-brand-txt"><div className="nm">Rikkei Edu</div><div className="sub">{persona.sub} LMS</div></div>
          </div>
          {!authUser && (
            <div className="sb-role">
              {ROLES.map(([r, l]) => <button key={r} className={role === r ? "on" : ""} onClick={() => switchRole(r)}>{l}</button>)}
            </div>
          )}
          <nav className="sb-nav dark-scroll">
            {NAV[role].map(it => (
              <div key={it.k} className={"sb-item" + (activeKey === it.k ? " active" : "")} onClick={() => go(it.k)}>
                <Ic n={it.ic} size={20} /><span className="lbl">{it.l}</span>
              </div>
            ))}
          </nav>
          <div className="sb-foot">
            <div className="sb-item"><Ic n="help" size={20} /><span className="lbl">Trợ giúp & Hỗ trợ</span></div>
            {!authUser && <div className="sb-item" onClick={onExit}><Ic n="grid" size={20} /><span className="lbl">Thư viện màn hình</span></div>}
            {authUser && <div className="sb-item" onClick={onLogout}><Ic n="logout" size={20} /><span className="lbl">Đăng xuất</span></div>}
          </div>
        </aside>

        <div className="main">
          <header className="topbar">
            <button className="icon-btn hamburger" onClick={() => setDrawer(true)}><Ic n="menu" size={20} /></button>
            <button className="btn btn-ghost btn-sm gallery-back" onClick={onExit} title="Về Thư viện màn hình"><Ic n="arrow_left" size={16} />Thư viện</button>
            <div className="search field-icon"><Ic n="search" /><input className="input" placeholder="Tìm kiếm khóa học, học viên..." /></div>
            <div className="grow" />
            <div style={{ position: "relative" }} onClick={e => e.stopPropagation()}>
              <button
                className="icon-btn"
                style={unreadCount > 0 ? { background: '#fff1f2', borderColor: '#fecdd3', color: '#e11d48' } : undefined}
                onClick={() => { setNotif(!notif); setUmenu(false); }}
              >
                <Ic n="bell" size={20} style={unreadCount > 0 ? { color: '#e11d48' } : undefined} />
                {unreadCount > 0 && <span className="badge-count" style={{ background: '#e11d48', borderColor: '#fff' }}>{unreadCount > 99 ? '99+' : unreadCount}</span>}
              </button>
              {notif && (
                <div className="card" style={{ position: "absolute", right: 0, top: 52, width: 360, maxWidth: "90vw", padding: 0, boxShadow: "var(--sh-lg)", zIndex: 60, animation: "popIn .18s" }}>
                  <div className="between" style={{ padding: "14px 18px", borderBottom: "1px solid var(--border)" }}><b>Thông báo</b><div className="row gap-8">{unreadCount > 0 && <span className="link" onClick={handleMarkAllAsRead}>Đánh dấu đã đọc</span>}</div></div>
                  <div style={{ maxHeight: 340, overflowY: "auto" }}>
                    {notifLoading && <div className="t-sm muted" style={{ padding: "24px 18px", textAlign: "center" }}>Đang tải...</div>}
                    {!notifLoading && notificationList.length === 0 && <div className="t-sm muted" style={{ padding: "24px 18px", textAlign: "center" }}>Chưa có thông báo nào.</div>}
                    {!notifLoading && notificationList.slice(0, 5).map((n) => {
                      const meta = notifMeta(n.type);
                      return (
                        <div key={n.id} className="row gap-12" style={{ padding: "13px 16px", background: n.read ? "#fff" : "var(--accent-soft)", borderBottom: "1px solid var(--border)", cursor: "pointer" }} onClick={() => {
                          if (!n.read) handleMarkAsRead(n.id);
                          setNotif(false);
                          const targetUrl = getNotificationTargetUrl(n, role);
                          const { routeKey, params: urlParams } = parseNotificationUrl(targetUrl);
                          go(routeKey, Object.keys(urlParams).length > 0 ? urlParams : undefined);
                        }}>
                          <div className="stat-ic" style={{ width: 38, height: 38, borderRadius: 10, background: meta.color + "1a", color: meta.color, flex: "none" }}><Ic n={meta.icon} size={18} /></div>
                          <div className="grow" style={{ minWidth: 0 }}>
                            <div className="row gap-6" style={{ marginBottom: 3 }}>
                              <span className="chip" style={{ background: meta.color + "1a", color: meta.color, borderColor: meta.color + "33", fontSize: 10, padding: "1px 5px" }}>{meta.label || 'Hệ thống'}</span>
                            </div>
                            <div className="t-sm" style={{ lineHeight: 1.4, fontWeight: n.read ? 400 : 600 }}>{n.title}</div>
                            {n.body && <div className="t-xs muted" style={{ marginTop: 3, lineHeight: 1.3, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{n.body}</div>}
                            <div className="t-xs dim" style={{ marginTop: 4 }}>{timeAgo(n.createdAt)}</div>
                          </div>
                          {!n.read && <span style={{ width: 8, height: 8, borderRadius: 999, background: "var(--accent)", flex: "none" }} />}
                        </div>
                      );
                    })}
                  </div>
                  <div className="between" style={{ padding: "10px 18px", borderTop: "1px solid var(--border)" }}>
                    <span className="link t-sm" onClick={() => { setNotif(false); go("notifications"); }}>Xem tất cả</span>
                    <span className="t-xs dim">{unreadCount} chưa đọc</span>
                  </div>
                </div>
              )}
            </div>
            <button className="icon-btn"><Ic n="help" size={20} /></button>
            <div style={{ position: "relative" }} onClick={e => e.stopPropagation()}>
              <div className="user-pill" onClick={() => { setUmenu(!umenu); setNotif(false); }}>
                <Avatar name={persona.name} size={34} />
                <div><div className="nm">{persona.name}</div><div className="rl">{persona.role}</div></div>
                <Ic n="chevron_down" size={16} style={{ color: "var(--text-3)" }} />
              </div>
              {umenu && (
                <div className="card" style={{ position: "absolute", right: 0, top: 52, width: 240, padding: 8, boxShadow: "var(--sh-lg)", zIndex: 60, animation: "popIn .18s" }}>
                  <div className="row gap-11" style={{ padding: "10px 12px", borderBottom: "1px solid var(--border)", marginBottom: 6 }}>
                    <Avatar name={persona.name} size={40} /><div style={{ minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 14 }} className="truncate">{persona.name}</div><div className="t-xs muted truncate">{persona.email}</div></div>
                  </div>
                  <div className="row gap-11" style={{ padding: "10px 12px", borderRadius: 9, cursor: "pointer", fontSize: 14, fontWeight: 500 }} onClick={() => { go("settings"); setUmenu(false); }}><Ic n="settings" size={18} style={{ color: "var(--text-2)" }} />Cài đặt</div>
                  {import.meta.env.DEV && (
                    <>
                      <div className="t-xs muted" style={{ padding: "8px 12px 4px", fontWeight: 700, borderTop: "1px solid var(--border)", marginTop: 6 }}>Chuyển tài khoản (dev)</div>
                      <div style={{ maxHeight: 220, overflowY: "auto" }}>
                        {DEV_QUICK_ACCOUNTS.map(acc => (
                          <div key={acc.email}
                            className="row gap-11"
                            style={{ padding: "8px 12px", borderRadius: 9, cursor: switchingAccount ? "default" : "pointer", fontSize: 13.5, opacity: switchingAccount && switchingAccount !== acc.email ? .5 : 1 }}
                            onClick={() => quickSwitchAccount(acc.email)}>
                            <Ic n="user" size={16} style={{ color: "var(--text-2)" }} />
                            <div style={{ minWidth: 0 }}>
                              <div className="truncate">{acc.label}{switchingAccount === acc.email ? "…" : ""}</div>
                              <div className="t-xs muted truncate">{acc.email}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </>
                  )}
                  {!authUser && <div className="row gap-11" style={{ padding: "10px 12px", borderRadius: 9, cursor: "pointer", fontSize: 14, fontWeight: 500, color: "var(--error)", borderTop: "1px solid var(--border)", marginTop: 6 }} onClick={onExit}><Ic n="grid" size={18} />Thư viện màn hình</div>}
                  {authUser && <div className="row gap-11" style={{ padding: "10px 12px", borderRadius: 9, cursor: "pointer", fontSize: 14, fontWeight: 500, color: "var(--error)", borderTop: "1px solid var(--border)", marginTop: 6 }} onClick={onLogout}><Ic n="logout" size={18} />Đăng xuất</div>}
                </div>
              )}
            </div>
          </header>
          <main style={{ flex: 1 }}>{useMemo(() => <Comp nav={go} persona={persona} demo={demo} groupId={routeParams?.groupId} courseId={routeParams?.courseId} slug={routeParams?.slug} quizId={routeParams?.quizId} postId={routeParams?.postId || routeParams?.id} />, [route, role, routeParams?.groupId, routeParams?.courseId, routeParams?.slug, routeParams?.quizId, routeParams?.postId, routeParams?.id, persona, demo, go])}</main>
        </div>
        {(role === "student" || role === "instructor") && <window.AIChatbot />}
        <window.AlertModal open={!!switchAlert} onClose={() => setSwitchAlert(null)} title={switchAlert?.title} message={switchAlert?.message} type="error" />
      </div>
    );
  }

  /* ---------- Fullscreen (player/quiz/result) + Login viewers ---------- */
  function BareView({ name, onExit }) {
    const [cur, setCur] = useState(name);
    const Comp = window[cur];
    return (
      <div className="app" style={{ position: "relative" }}>
        <button className="btn btn-ghost btn-sm gallery-float" onClick={onExit}><Ic n="arrow_left" size={16} />Thư viện</button>
        <Comp onBack={onExit} onSubmit={() => setCur("QuizResult")} />
      </div>
    );
  }
  function LoginView({ onExit }) {
    return (
      <div style={{ height: "100vh", position: "relative" }}>
        <button className="btn btn-ghost btn-sm gallery-float" onClick={onExit}><Ic n="arrow_left" size={16} />Thư viện</button>
        <window.LoginScreen onLogin={onExit} />
      </div>
    );
  }

  /* ---------- Catalog of every screen + popup ---------- */
  const CATALOG = [
    { group: "Xác thực", color: "#0ea5e9", items: [
      { id: "login", kind: "login", ic: "lock", label: "Đăng nhập", desc: "Split layout + chọn vai trò" },
    ]},
    { group: "Học viên", color: "#2563eb", items: [
      { kind: "screen", role: "student", route: "dashboard", ic: "grid", label: "Tổng quan", desc: "Dashboard học viên" },
      { kind: "screen", role: "student", route: "courses", ic: "cap", label: "Khóa học của tôi", desc: "Lưới thẻ + phân trang" },
      { kind: "screen", role: "student", route: "tasks", ic: "clipboard", label: "Bài tập & Quiz", desc: "Danh sách + nộp bài" },
      { kind: "screen", role: "student", route: "certs", ic: "award", label: "Chứng chỉ", desc: "Bộ sưu tập chứng chỉ" },
      { kind: "screen", role: "student", route: "groups", ic: "layers", label: "Nhóm học của tôi", desc: "Danh sách nhóm đã tham gia" },
      { kind: "screen", role: "student", route: "groupDetail", ic: "users", label: "Chi tiết Nhóm (HV)", desc: "Thông tin nhóm + thành viên" },
      { kind: "screen", role: "student", route: "settings", ic: "settings", label: "Cài đặt tài khoản", desc: "Hồ sơ · Bảo mật · Thông báo" },
    ]},
    { group: "Giảng viên", color: "#7c3aed", items: [
      { kind: "screen", role: "instructor", route: "dashboard", ic: "grid", label: "Tổng quan", desc: "Dashboard giảng viên" },
      { kind: "screen", role: "instructor", route: "courses", ic: "book", label: "Quản lý Khóa học", desc: "Lưới thẻ khóa học" },
      { kind: "screen", role: "instructor", route: "courseDetail", ic: "layers", label: "Chi tiết Khóa học", desc: "Trình dựng nội dung + video/tài liệu" },
      { kind: "screen", role: "instructor", route: "groups", ic: "layers", label: "Quản lý Nhóm", desc: "Lưới thẻ nhóm" },
      { kind: "screen", role: "instructor", route: "groupDetail", ic: "users", label: "Chi tiết Nhóm", desc: "Danh sách học viên + tiến độ" },
      { kind: "screen", role: "instructor", route: "assess", ic: "clipboard", label: "Bài tập & Trắc nghiệm", desc: "3 tab: bài tập / đề / ngân hàng" },
      { kind: "screen", role: "instructor", route: "grading", ic: "edit", label: "Chấm điểm", desc: "Bảng bài nộp + chấm" },
      { kind: "screen", role: "instructor", route: "students", ic: "users", label: "Học viên của tôi", desc: "Danh sách học viên" },
    ]},
    { group: "Quản trị", color: "#0f172a", items: [
      { kind: "screen", role: "admin", route: "dashboard", ic: "grid", label: "Tổng quan hệ thống", desc: "Thống kê + biểu đồ" },
      { kind: "screen", role: "admin", route: "users", ic: "users", label: "Quản lý Người dùng", desc: "Bảng + phân trang" },
      { kind: "screen", role: "admin", route: "courses", ic: "book", label: "Quản lý Khóa học", desc: "Toàn bộ khóa học" },
      { kind: "screen", role: "admin", route: "approval", ic: "check_circle", label: "Phê duyệt Khóa học", desc: "Kiểm duyệt nội dung" },
      { kind: "screen", role: "admin", route: "reports", ic: "chart", label: "Báo cáo & Thống kê", desc: "Doanh thu · hiệu suất" },
      { kind: "screen", role: "admin", route: "logs", ic: "file", label: "Nhật ký hệ thống", desc: "Activity log" },
    ]},
    { group: "Dùng chung · GV & HV", color: "#10b981", items: [
      { kind: "screen", role: "student", route: "forum", ic: "message", label: "Diễn đàn thảo luận", desc: "Danh sách chủ đề" },
      { kind: "screen", role: "student", route: "chat", ic: "chat", label: "Chat nhóm", desc: "Nhắn tin real-time" },
    ]},
    { group: "Toàn màn hình", color: "#f59e0b", items: [
      { kind: "bare", name: "LecturePlayer", ic: "play", label: "Trình phát bài giảng", desc: "Video + AI Chatbot + tài liệu" },
      { kind: "bare", name: "QuizPlayer", ic: "shield", label: "Làm Quiz (Proctoring)", desc: "Đề trắc nghiệm + giám sát" },
      { kind: "bare", name: "QuizResult", ic: "award", label: "Kết quả bài kiểm tra", desc: "Điểm + chi tiết đáp án" },
    ]},
    { group: "Popup · Cửa sổ", color: "#ec4899", items: [
      { kind: "screen", role: "instructor", route: "courses", demo: "create", ic: "plus", label: "Popup · Tạo khóa học", desc: "Wizard nhiều bước" },
      { kind: "screen", role: "instructor", route: "assess", demo: "addq", ic: "plus", label: "Popup · Thêm câu hỏi", desc: "Vào ngân hàng câu hỏi" },
      { kind: "screen", role: "instructor", route: "assess", demo: "random", ic: "sparkles", label: "Popup · Tạo trắc nghiệm ngẫu nhiên", desc: "Bốc câu theo độ khó" },
      { kind: "screen", role: "instructor", route: "assess", demo: "bank", ic: "layers", label: "Ngân hàng câu hỏi", desc: "Tab ngân hàng" },
      { kind: "screen", role: "admin", route: "approval", demo: "detail", ic: "eye", label: "Popup · Chi tiết phê duyệt", desc: "Xem trước tài liệu + video" },
      { kind: "screen", role: "admin", route: "approval", demo: "preview", ic: "video", label: "Popup · Xem trước Video", desc: "Trình phát kiểm duyệt" },
      { kind: "screen", role: "student", route: "forum", demo: "create", ic: "plus", label: "Popup · Tạo bài đăng", desc: "Đăng chủ đề diễn đàn" },
      { kind: "screen", role: "student", route: "forum", demo: "detail", ic: "message", label: "Chi tiết bài đăng", desc: "Bình luận + trả lời lồng nhau" },
    ]},
  ];
  const TOTAL = CATALOG.reduce((n, g) => n + g.items.length, 0);

  function Gallery() {
    const [view, setView] = useState(null);
    const exit = () => setView(null);

    if (view) {
      if (view.kind === "login") return <LoginView onExit={exit} />;
      if (view.kind === "bare") return <BareView name={view.name} onExit={exit} />;
      return <Shell key={view.role + view.route + (view.demo || "")} role0={view.role} route0={view.route} demo0={view.demo} onExit={exit} />;
    }

    return (
      <div className="gallery">
        <header className="gallery-head">
          <div className="row gap-14">
            <div className="sb-logo" style={{ width: 46, height: 46 }}><Ic n="cap" size={24} /></div>
            <div>
              <h1 className="t-h2" style={{ margin: 0 }}>Thư viện màn hình · LMS Rikkei Edu</h1>
              <p className="muted" style={{ margin: "4px 0 0", fontSize: 14 }}>{TOTAL} màn hình & popup — bấm để mở (responsive, tương tác thật)</p>
            </div>
          </div>
        </header>
        <div className="gallery-body">
          {CATALOG.map(sec => (
            <section key={sec.group} style={{ marginBottom: 34 }}>
              <div className="row gap-10" style={{ marginBottom: 16 }}>
                <span style={{ width: 10, height: 10, borderRadius: 3, background: sec.color }} />
                <h2 className="t-h3" style={{ margin: 0 }}>{sec.group}</h2>
                <span className="chip chip-neutral">{sec.items.length}</span>
              </div>
              <div className="gallery-grid">
                {sec.items.map((it, i) => (
                  <button key={i} className="gallery-card" onClick={() => setView(it)}>
                    <div className="gallery-card-ic" style={{ background: sec.color + "18", color: sec.color }}><Ic n={it.ic} size={22} /></div>
                    <div className="grow" style={{ minWidth: 0 }}>
                      <div className="gallery-card-t">{it.label}</div>
                      <div className="gallery-card-d">{it.desc}</div>
                    </div>
                    <Ic n="chevron_right" size={18} style={{ color: "var(--text-3)", flex: "none" }} />
                  </button>
                ))}
              </div>
            </section>
          ))}
          <div className="t-xs dim" style={{ textAlign: "center", padding: "8px 0 28px" }}>
            Mẹo: trong mỗi màn hình bạn vẫn đổi vai trò, điều hướng và mở mọi popup bằng cách bấm các nút bên trong. Thu nhỏ cửa sổ để xem responsive (tablet/mobile).
          </div>
        </div>
      </div>
    );
  }

  Object.assign(window, { AppShell: Shell, GalleryPage: Gallery });
}

registerGalleryPage();
