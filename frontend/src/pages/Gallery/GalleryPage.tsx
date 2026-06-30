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

function registerGalleryPage() {
  const { useState, useEffect } = React;
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
      { k: "settings", l: "Cài đặt", ic: "settings" },
    ],
    admin: [
      { k: "dashboard", l: "Tổng quan", ic: "grid" },
      { k: "users", l: "Quản lý Người dùng", ic: "users" },
      { k: "courses", l: "Quản lý Khóa học", ic: "book" },
      { k: "approval", l: "Phê duyệt", ic: "check_circle" },
      { k: "reports", l: "Báo cáo", ic: "chart" },
      { k: "logs", l: "Nhật ký hệ thống", ic: "file" },
      { k: "settings", l: "Cài đặt", ic: "settings" },
    ],
  };
  const SCREENS = {
    student: { dashboard: "StuDashboard", courses: "StuCourses", tasks: "StuTasks", forum: "ForumPage", chat: "ChatScreen", certs: "StuCerts", settings: "Settings", notifications: "NotificationsPage" },
    instructor: { dashboard: "InsDashboard", courses: "InsCourses", courseDetail: "InsCourseDetail", groups: "InsGroups", groupDetail: "InsGroupDetail", assess: "InsAssess", grading: "InsGrading", students: "InsStudents", forum: "ForumPage", chat: "ChatScreen", settings: "Settings", notifications: "NotificationsPage" },
    admin: { dashboard: "AdminDashboard", users: "AdminUsers", courses: "AdminCourses", approval: "AdminApproval", reports: "AdminReports", logs: "AdminLogs", settings: "Settings", notifications: "NotificationsPage" },
  };
  const FULLBARE = { player: "LecturePlayer", quiz: "QuizPlayer", result: "QuizResult", preview: "PreviewPlayer" };
  const ROLES = [["student", "Học viên"], ["instructor", "Giảng viên"], ["admin", "Quản trị"]];
  const ALIAS = { groupDetail: "groups", courseDetail: "courses" };

  function notifMeta(type) {
    const map = {
      FORUM_REPLY:          { icon: 'message',      color: '#8b5cf6' },
      FORUM_POST:           { icon: 'message',      color: '#6366f1' },
      QUIZ_PUBLISHED:       { icon: 'shield',       color: '#f59e0b' },
      SUBMISSION_GRADED:    { icon: 'edit',         color: '#10b981' },
      ASSIGNMENT_PUBLISHED: { icon: 'clipboard',    color: '#3b82f6' },
      ASSIGNMENT_SUBMITTED: { icon: 'upload',       color: '#06b6d4' },
      CERTIFICATE_ISSUED:   { icon: 'award',        color: '#10b981' },
      COURSE_ENROLLMENT:    { icon: 'user_plus',    color: '#2563eb' },
      COURSE_APPROVED:      { icon: 'check_circle', color: '#16a34a' },
      SYSTEM_ANNOUNCEMENT:  { icon: 'bell',         color: '#f97316' },
    };
    return map[type] || { icon: 'bell', color: '#2563eb' };
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
  function Shell({ role0, route0, demo0, authUser, onExit, onBare, onNavigate, onLogout }) {
    const [role, setRole] = useState(role0);
    const basePersona = PERSONA[role];
    const persona = authUser ? {
      ...basePersona,
      name: authUser.fullName || basePersona.name,
      email: authUser.email || basePersona.email,
    } : basePersona;
    const [route, setRoute] = useState(route0 || "dashboard");
    const [demo, setDemo] = useState(demo0 || null);
    const [back, setBack] = useState("dashboard");
    const [drawer, setDrawer] = useState(false);
    const [notif, setNotif] = useState(false);
    const [umenu, setUmenu] = useState(false);
    const [notificationList, setNotificationList] = useState([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [notifLoading, setNotifLoading] = useState(false);

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
      if (!authUser) return;
      loadNotifications();
    }, [authUser]);

    useEffect(() => {
      if (!authUser) return;
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

    const go = (k) => {
      setDemo(null);
      if (FULLBARE[k]) { if (onBare) { onBare(k); return; } setBack(SCREENS[role][route] ? route : "dashboard"); setRoute(k); setDrawer(false); const m = document.querySelector(".main"); if (m) m.scrollTop = 0; return; }
      if (SCREENS[role][k]) { if (onNavigate) { onNavigate(role, k); return; } setRoute(k); setDrawer(false); const m = document.querySelector(".main"); if (m) m.scrollTop = 0; }
    };
    const switchRole = (r) => {
      setDemo(null);
      setDrawer(false);
      if (onNavigate) { onNavigate(r, "dashboard"); return; }
      setRole(r);
      setRoute("dashboard");
    };

    if (FULLBARE[route]) {
      const Comp = window[FULLBARE[route]];
      return <div className="app"><Comp onBack={() => setRoute(back)} onSubmit={() => setRoute("result")} /></div>;
    }

    const activeKey = ALIAS[route] || route;
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
              <button className="icon-btn" onClick={() => { setNotif(!notif); setUmenu(false); }}><Ic n="bell" size={20} />{unreadCount > 0 && <span className="badge-count">{unreadCount > 99 ? '99+' : unreadCount}</span>}</button>
              {notif && (
                <div className="card" style={{ position: "absolute", right: 0, top: 52, width: 360, maxWidth: "90vw", padding: 0, boxShadow: "var(--sh-lg)", zIndex: 60, animation: "popIn .18s" }}>
                  <div className="between" style={{ padding: "14px 18px", borderBottom: "1px solid var(--border)" }}><b>Thông báo</b><div className="row gap-8">{unreadCount > 0 && <span className="link" onClick={handleMarkAllAsRead}>Đánh dấu đã đọc</span>}</div></div>
                  <div style={{ maxHeight: 340, overflowY: "auto" }}>
                    {notifLoading && <div className="t-sm muted" style={{ padding: "24px 18px", textAlign: "center" }}>Đang tải...</div>}
                    {!notifLoading && notificationList.length === 0 && <div className="t-sm muted" style={{ padding: "24px 18px", textAlign: "center" }}>Chưa có thông báo nào.</div>}
                    {!notifLoading && notificationList.slice(0, 5).map((n) => {
                      const meta = notifMeta(n.type);
                      return (
                        <div key={n.id} className="row gap-12" style={{ padding: "13px 16px", background: n.read ? "#fff" : "var(--accent-soft)", borderBottom: "1px solid var(--border)", cursor: "pointer" }} onClick={() => { if (!n.read) handleMarkAsRead(n.id); }}>
                          <div className="stat-ic" style={{ width: 38, height: 38, borderRadius: 10, background: meta.color + "1a", color: meta.color, flex: "none" }}><Ic n={meta.icon} size={18} /></div>
                          <div className="grow"><div className="t-sm" style={{ lineHeight: 1.4, fontWeight: n.read ? 400 : 600 }}>{n.title}</div><div className="t-xs dim" style={{ marginTop: 3 }}>{timeAgo(n.createdAt)}</div></div>
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
                  {!authUser && <div className="row gap-11" style={{ padding: "10px 12px", borderRadius: 9, cursor: "pointer", fontSize: 14, fontWeight: 500, color: "var(--error)", borderTop: "1px solid var(--border)", marginTop: 6 }} onClick={onExit}><Ic n="grid" size={18} />Thư viện màn hình</div>}
                  {authUser && <div className="row gap-11" style={{ padding: "10px 12px", borderRadius: 9, cursor: "pointer", fontSize: 14, fontWeight: 500, color: "var(--error)", borderTop: "1px solid var(--border)", marginTop: 6 }} onClick={onLogout}><Ic n="logout" size={18} />Đăng xuất</div>}
                </div>
              )}
            </div>
          </header>
          <main style={{ flex: 1 }}><Comp nav={go} persona={persona} demo={demo} /></main>
        </div>
        {(role === "student" || role === "instructor") && <window.AIChatbot />}
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
