// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Trình phát bài giảng cho học viên (+ AI Chatbot)
   ============================================================ */
(function () {
  const { useState, useEffect, useRef, useMemo, useCallback } = React;
  const Ic  = window.Icon, api = window.httpClient;
  const Av  = window.Avatar;

  /* Resource meta */
  const RS = {
    PDF:   { bg: "#fdecec", color: "#dc2626", icon: "file"      },
    DOC:   { bg: "#eaf1ff", color: "#2563eb", icon: "file-text" },
    SLIDE: { bg: "#fef5e6", color: "#d97706", icon: "layers"    },
    IMAGE: { bg: "#f0fdf4", color: "#16a34a", icon: "image"     },
    VIDEO: { bg: "#f3edff", color: "#7c3aed", icon: "video"     },
    OTHER: { bg: "#f1f5f9", color: "#475569", icon: "file"      },
  };

  function getYoutubeId(url) {
    try {
      const u = new URL(url);
      if (u.hostname === "youtu.be") return u.pathname.slice(1).split("?")[0];
      if (u.hostname.includes("youtube.com")) return u.searchParams.get("v");
    } catch (e) { /* ignore invalid url */ }
    return null;
  }

  function fmtDur(s) {
    if (!s) return null;
    return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
  }

  function fmtBytes(bytes) {
    if (!bytes) return "";
    const units = ["B","KB","MB","GB"];
    let i = 0;
    let v = bytes;
    while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
    return v.toFixed(i > 0 ? 1 : 0) + " " + units[i];
  }

  /* ── Unified Resource Card (for both Video & Document) ──── */
  function ResourceCard({ title, subtitle, icon, iconBg, iconColor, isActive, onClick }) {
    return (
      <div onClick={onClick}
        style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 14px",
          border: "1.5px solid", borderRadius: 12, cursor: "pointer",
          background: isActive ? "#f5f3ff" : "#fff", transition: "all .15s ease",
          borderColor: isActive ? "#8b5cf6" : "var(--border)",
          boxShadow: isActive ? "0 2px 8px rgba(139,92,246,.12)" : "none" }}
        onMouseEnter={e => { if (!isActive) { e.currentTarget.style.borderColor = "#cbd5e1"; e.currentTarget.style.background = "#f8fafc"; } }}
        onMouseLeave={e => { if (!isActive) { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.background = "#fff"; } }}>
        <div style={{ width: 42, height: 42, borderRadius: 11, background: isActive ? "#ede9fe" : iconBg || "#f1f5f9",
          color: isActive ? "#7c3aed" : iconColor || "#64748b", display: "grid", placeItems: "center", flexShrink: 0 }}>
          <Ic n={icon} size={20} />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 13.5, color: isActive ? "#5b21b6" : "#0f172a" }} className="truncate">
            {title}
          </div>
          {subtitle && <div className="t-xs muted truncate" style={{ marginTop: 2, color: isActive ? "#7c3aed" : "#64748b" }}>{subtitle}</div>}
        </div>
        {isActive && (
          <div style={{ width: 8, height: 8, borderRadius: 999, background: "#8b5cf6", flexShrink: 0 }} />
        )}
      </div>
    );
  }

  /* ── Viewer ─────────────────────────────────────────────── */
  function Viewer({ res, url, onVideoTimeUpdate, loading, error, onRetry }: any) {
    useEffect(() => {
      const handleKeyDown = (e: any) => {
        if ((e.ctrlKey || e.metaKey) && (e.key === 's' || e.key === 'p' || e.key === 'S' || e.key === 'P' || e.key === 'u' || e.key === 'U')) {
          e.preventDefault();
        }
      };
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }, []);

    if (!res) return (
      <div style={{ aspectRatio: "16/9", background: "#0a0f1c", borderRadius: 16, overflow: "hidden",
        display: "grid", placeItems: "center", position: "relative" }}>
        <div style={{ color: "#334155" }}><Ic n="monitor" size={40} /></div>
      </div>
    );

    if (loading) return (
      <div style={{ borderRadius: 16, minHeight: 400, background: "#f8fafc", border: "1px solid var(--border)",
        display: "grid", placeItems: "center", padding: 32 }}>
        <div style={{ textAlign: "center", color: "#64748b", fontSize: 13.5 }}>
          <div style={{ marginBottom: 10, fontWeight: 600, color: "#0f172a" }}>Đang tải tài liệu...</div>
          <div style={{ fontSize: 12 }}>{res.displayName || res.originalFilename}</div>
        </div>
      </div>
    );

    if (error || !url) return (
      <div style={{ borderRadius: 16, minHeight: 360, background: "#f8fafc", border: "1px dashed #f87171",
        display: "grid", placeItems: "center", padding: 32 }}>
        <div style={{ textAlign: "center", display: "flex", flexDirection: "column", alignItems: "center", gap: 10 }}>
          <div style={{ width: 48, height: 48, borderRadius: 12, background: "#fef2f2", color: "#dc2626", display: "grid", placeItems: "center" }}>
            <Ic n="alert_triangle" size={24} />
          </div>
          <div style={{ fontWeight: 600, color: "#0f172a", fontSize: 14 }}>Không thể hiển thị tài liệu này</div>
          <div style={{ fontSize: 12.5, color: "#64748b", maxWidth: 360 }}>Đường dẫn truy cập chưa sẵn sàng hoặc kết nối bị gián đoạn.</div>
          {onRetry && (
            <button className="btn btn-secondary btn-sm" style={{ marginTop: 6 }} onClick={onRetry}>
              Thử lại ngay
            </button>
          )}
        </div>
      </div>
    );

    const t = res.resourceType;
    if (t === "VIDEO") {
      const ytId = url ? getYoutubeId(url) : null;
      if (ytId) return (
        <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", aspectRatio: "16/9", background: "#000" }}>
          <iframe src={`https://www.youtube-nocookie.com/embed/${ytId}?rel=0&autoplay=1`}
            style={{ width: "100%", height: "100%", border: "none", display: "block" }}
            allowFullScreen allow="autoplay" />
        </div>
      );
      return (
        <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", aspectRatio: "16/9", background: "#000" }}
          onContextMenu={(e: any) => e.preventDefault()}>
          <video key={url || res?.id} controls controlsList="nodownload" autoPlay src={url}
            style={{ width: "100%", height: "100%", display: "block", outline: "none" }}
            onContextMenu={(e: any) => e.preventDefault()}
            onTimeUpdate={onVideoTimeUpdate} />
        </div>
      );
    }

    if (t === "IMAGE") return (
      <div style={{ borderRadius: 16, overflow: "hidden", background: "#1e293b", display: "flex",
        alignItems: "center", justifyContent: "center", minHeight: 400, padding: 16 }}
        onContextMenu={(e: any) => e.preventDefault()}>
        <img src={url} alt={res.displayName}
          style={{ maxWidth: "100%", maxHeight: 500, objectFit: "contain", borderRadius: 8, display: "block", pointerEvents: "none" }} />
      </div>
    );

    if (t === "PDF") return (
      <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", background: "#f8fafc", border: "1px solid var(--border)", height: 600 }}
        onContextMenu={(e: any) => e.preventDefault()}>
        <iframe src={`${url}#toolbar=0&navpanes=0&scrollbar=0`} title={res.displayName}
          style={{ width: "100%", height: "100%", border: "none", display: "block" }} />
        <div style={{ position: "absolute", top: 0, right: 0, width: 220, height: 60, zIndex: 10, background: "transparent" }}
          onClick={(e: any) => { e.preventDefault(); e.stopPropagation(); }} />
      </div>
    );

    if (t === "DOC" || t === "SLIDE") {
      const isLocal = url.includes("localhost") || url.includes("127.0.0.1") || url.startsWith("/");
      if (isLocal) {
        return (
          <div style={{ borderRadius: 16, background: "#f8fafc", border: "1px solid var(--border)", padding: "40px 24px", textAlign: "center", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 12, minHeight: 320 }}
            onContextMenu={(e: any) => e.preventDefault()}>
            <div style={{ width: 56, height: 56, borderRadius: 16, background: t === "SLIDE" ? "#fff7ed" : "#eff6ff", color: t === "SLIDE" ? "#ea580c" : "#2563eb", display: "grid", placeItems: "center" }}>
              <Ic n="book" size={28} />
            </div>
            <div style={{ fontWeight: 600, fontSize: 14.5, color: "#0f172a" }}>
              {res.displayName || res.originalFilename || (t === "SLIDE" ? "Slide bài giảng PowerPoint" : "Tài liệu Word / Docx")}
            </div>
            <div style={{ fontSize: 13, color: "#64748b", maxWidth: 440, lineHeight: 1.5 }}>
              Trình xem trực tuyến không thể kết nối tới đường dẫn nội bộ (Localhost). Tài liệu sẽ hiển thị trực tiếp trong khung khi hệ thống chạy trên máy chủ chính thức (Production).
            </div>
          </div>
        );
      }
      return (
        <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", height: 600, background: "#f8fafc" }}
          onContextMenu={(e: any) => e.preventDefault()}>
          <iframe src={`https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(url)}&wdDownloadButton=False&wdPrint=0`}
            title={res.displayName} style={{ width: "100%", height: "100%", border: "none", display: "block" }} />
          <div style={{ position: "absolute", top: 0, right: 0, width: 180, height: 56, zIndex: 10, background: "transparent" }}
            onClick={(e: any) => { e.preventDefault(); e.stopPropagation(); }} />
          <div style={{ position: "absolute", bottom: 0, right: 0, width: 240, height: 52, zIndex: 10, background: "transparent" }}
            onClick={(e: any) => { e.preventDefault(); e.stopPropagation(); }} />
        </div>
      );
    }

    return (
      <div style={{ borderRadius: 16, minHeight: 300, background: "#f8fafc",
        display: "grid", placeItems: "center", padding: 32 }}>
        <div style={{ textAlign: "center", display: "flex", flexDirection: "column", alignItems: "center", gap: 12 }}>
          <div style={{ width: 60, height: 60, borderRadius: 14, background: "#f1f5f9",
            display: "grid", placeItems: "center" }}>
            <Ic n="file" size={28} style={{ color: "#94a3b8" }} />
          </div>
          <span style={{ fontSize: 14, color: "#64748b" }}>Định dạng tài liệu không hỗ trợ xem trực tiếp trên trình duyệt</span>
        </div>
      </div>
    );
  }

  /* ── Main component ─────────────────────────────────────── */
  function LecturePlayer({ onBack, onDashboard, onSettings, onLogout, navigate }) {
    const params = Object.fromEntries(new URLSearchParams(location.search));
    const courseId = params.courseId || (window.__lectureCourse?.courseId);

    const [course,   setCourse]   = useState(null);
    const [chapters, setChapters] = useState([]);
    const [loading,  setLoading]  = useState(!!courseId);
    const [error,    setError]    = useState(null);
    const [activeL,  setActiveL]  = useState(null);
    const [viewRes,  setViewRes]  = useState(null);
    const [resUrls,  setResUrls]  = useState({});
    const [openCh,   setOpenCh]   = useState({});
    const [chat,     setChat]     = useState(false);
    const [msgs, setMsgs] = useState([
      { me: false, t: "Xin chào! Mình là trợ lý AI của Rikkei Edu. Mình có thể giúp bạn giải đáp thắc mắc về bài giảng này. Bạn cần hỗ trợ gì? 🤖" },
    ]);
    const [input, setInput] = useState("");
    const [activeVideoIdx, setActiveVideoIdx] = useState(0);
    const [notifOpen, setNotifOpen] = useState(false);
    const [notifList, setNotifList] = useState([]);
    const [notifUnread, setNotifUnread] = useState(0);
    const [notifLoading, setNotifLoading] = useState(false);
    const [userMenu, setUserMenu] = useState(false);
    const [userName, setUserName] = useState("Học viên");
    
    /* State từ dev: quản lý gửi tin nhắn AI Chatbot thật */
    const [sending, setSending] = useState(false);
    const [conversationId, setConversationId] = useState(null);

    useEffect(() => {
      api.get('/profile').then(r => { if (r.data?.fullName) setUserName(r.data.fullName); }).catch(() => { /* ignore */ });
    }, []);

    function notifMeta(type) {
      const m = {
        FORUM_REPLY: { icon: 'message', color: '#8b5cf6' },
        FORUM_POST: { icon: 'message', color: '#6366f1' },
        QUIZ_PUBLISHED: { icon: 'shield', color: '#f59e0b' },
        SUBMISSION_GRADED: { icon: 'edit', color: '#10b981' },
        ASSIGNMENT_PUBLISHED: { icon: 'clipboard', color: '#3b82f6' },
        ASSIGNMENT_SUBMITTED: { icon: 'upload', color: '#06b6d4' },
        CERTIFICATE_ISSUED: { icon: 'award', color: '#10b981' },
        COURSE_ENROLLMENT: { icon: 'user_plus', color: '#2563eb' },
        COURSE_APPROVED: { icon: 'check_circle', color: '#16a34a' },
        SYSTEM_ANNOUNCEMENT: { icon: 'bell', color: '#f97316' },
      };
      return m[type] || { icon: 'bell', color: '#2563eb' };
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
    async function loadNotif(showLoading) {
      if (showLoading) setNotifLoading(true);
      try {
        const r = await api.get('/notifications', { params: { page: 0, size: 20 } });
        setNotifList(r.data.content || []);
        const u = await api.get('/notifications/unread-count');
        setNotifUnread(u.data.count || 0);
      } catch (e) { /* ignore */ }
      if (showLoading) setNotifLoading(false);
    }
    async function markRead(id) {
      try { await api.patch(`/notifications/${id}/read`); } catch (e) { /* ignore */ }
      setNotifList(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
      setNotifUnread(prev => Math.max(0, prev - 1));
    }
    async function markAllRead() {
      try { await api.patch('/notifications/read-all'); } catch (e) { /* ignore */ }
      setNotifList(prev => prev.map(n => ({ ...n, read: true })));
      setNotifUnread(0);
    }

    /* Close dropdowns on outside click */
    useEffect(() => {
      const close = () => { setNotifOpen(false); setUserMenu(false); };
      window.addEventListener("click", close);
      return () => window.removeEventListener("click", close);
    }, []);

    const allLessons      = useMemo(() => chapters.flatMap(c => c.lessons || []), [chapters]);
    const activeIndex     = useMemo(() => allLessons.findIndex(l => l.id === activeL?.id), [allLessons, activeL]);
    const activeCh        = useMemo(() => chapters.find(c => c.lessons?.some(l => l.id === activeL?.id)), [chapters, activeL]);
    const prevLesson      = activeIndex > 0 ? allLessons[activeIndex - 1] : null;
    const nextLesson      = activeIndex < allLessons.length - 1 ? allLessons[activeIndex + 1] : null;
    const totalLessons    = allLessons.length;

    const activeResources = useMemo(() => (activeL?.resources || []).filter((r: any) => !r.pendingDelete), [activeL]);
    const isVideoLesson   = useMemo(() => activeResources.some((r: any) => r.resourceType === "VIDEO"), [activeResources]);
    const videoResources  = useMemo(() => activeResources.filter((r: any) => r.resourceType === "VIDEO"), [activeResources]);
    const docResources    = useMemo(() => activeResources.filter((r: any) => r.resourceType !== "VIDEO"), [activeResources]);
    const activeVideoRes  = videoResources[activeVideoIdx] || videoResources[0] || null;
    const isVideoActive   = videoResources.length > 0;
    const videoUrl        = activeVideoRes ? (resUrls[activeVideoRes.id]?.url || activeVideoRes?.externalUrl) : null;
    const completedCount  = useMemo(() => allLessons.filter(l => l.progress === "COMPLETED").length, [allLessons]);
    const progressPct     = useMemo(() => totalLessons > 0 ? Math.round((completedCount / totalLessons) * 100) : 0, [completedCount, totalLessons]);

    /* Progress tracking */
    const progRef = useRef<any>({});
    const updateProgress = useCallback(async (watchedPct: any, position: any, docSeconds: any, isCompleted?: boolean) => {
      if (!courseId || !activeL?.id) return;
      const targetStatus = isCompleted || progRef.current[activeL.id] === "COMPLETED" || (watchedPct !== null && watchedPct >= 90) || (videoResources.length === 0 && docSeconds !== null && docSeconds >= 20) ? "COMPLETED" : "IN_PROGRESS";
      if (targetStatus === "COMPLETED") {
        progRef.current[activeL.id] = "COMPLETED";
      }
      setChapters(prev => prev.map(ch => ({
        ...ch,
        lessons: (ch.lessons || []).map(l =>
          l.id === activeL.id
            ? { ...l, progress: l.progress === "COMPLETED" ? "COMPLETED" : targetStatus }
            : l
        ),
      })));
      try {
        await api.post(`/student/courses/${courseId}/lessons/${activeL.id}/progress`, {
          watchedPercentage: watchedPct,
          lastPlaybackPosition: position,
          documentViewSeconds: docSeconds,
        });
      } catch (e) { /* ignore */ }
    }, [courseId, activeL?.id, videoResources.length]);

    const endRef = useRef();
    function scrollBottom() { setTimeout(() => endRef.current?.scrollIntoView({ behavior: "smooth" }), 60); }
    useEffect(() => { scrollBottom(); }, [msgs, sending]);

    /* Kết hợp AI Chatbot thật từ dev, nhưng truyền thêm activeL?.title/id của bạn */
    const send = async () => {
      if (!input.trim() || sending) return;
      const q = input.trim();
      setMsgs(m => [...m, { me: true, t: q }]);
      setInput("");
      scrollBottom();
      setSending(true);
      try {
        if (window.__aiService && typeof window.__aiService.sendChatMessage === "function") {
          const res = await window.__aiService.sendChatMessage({ 
            message: q, 
            courseId: courseId || window.__selectedCourseId || null, 
            lessonId: activeL?.id || null,
            conversationId 
          });
          if (res?.conversationId) setConversationId(res.conversationId);
          setMsgs(m => [...m, { me: false, t: res.answer || res.message || "Đã phản hồi từ AI" }]);
        } else {
          /* Fallback nhẹ nhàng nếu aiService chưa bật */
          setTimeout(() => {
            setMsgs(m => [...m, { me: false, t: `AI trợ giảng: Mình đã nhận câu hỏi "${q}" về bài học ${activeL?.title || "này"}.` }]);
            scrollBottom();
          }, 700);
        }
      } catch (e) {
        setMsgs(m => [...m, { me: false, t: "Xin lỗi, mình đang gặp sự cố kết nối AI. Bạn thử lại sau nhé." }]);
      } finally {
        setSending(false);
        scrollBottom();
      }
    };

    /* Fetch course */
    useEffect(() => {
      if (!courseId) { setLoading(false); return; }
      api.get(`/student/courses/${courseId}`)
        .then(r => {
          setCourse(r.data);
          const chs = r.data.chapters || [];
          setChapters(chs);
          const opened = {};
          chs.forEach(ch => { opened[ch.id] = true; });
          setOpenCh(opened);
          const allL = chs.flatMap(c => c.lessons || []);
          const urlLessonId = params.lessonId || (window.__lectureCourse?.lessonId) || localStorage.getItem('last_lesson_' + courseId);
          let target = allL.find(x => x.id === urlLessonId);
          if (!target) {
            target = allL.find(x => x.progress === "IN_PROGRESS") || allL.find(x => x.progress !== "COMPLETED") || allL[0];
          }
          if (target) {
            setActiveL(target);
            try {
              localStorage.setItem('last_lesson_' + courseId, target.id);
              const newUrl = window.location.pathname + '?courseId=' + courseId + '&lessonId=' + target.id;
              window.history.replaceState(null, '', newUrl);
            } catch (e) { /* ignore */ }
            const ch = chs.find(c => c.lessons?.some(x => x.id === target.id));
            if (ch) opened[ch.id] = true;
          }
        })
        .catch(e => setError(e.response?.data?.message || "Không thể tải khóa học"))
        .finally(() => setLoading(false));
    }, [courseId]);

    /* When active lesson changes, auto-load first video URL and show first doc if not video lesson */
    useEffect(() => {
      if (!activeL) { setViewRes(null); return; }
      setActiveVideoIdx(0);
      setResUrls({});
      const docs = (activeL.resources || []).filter(r => !r.pendingDelete && r.resourceType !== "VIDEO");
      const videos = (activeL.resources || []).filter(r => !r.pendingDelete && r.resourceType === "VIDEO");
      const hasRealVideo = videos.length > 0;
      setViewRes(!hasRealVideo ? (docs[0] || null) : null);
      videos.forEach(v => { if (!v.externalUrl) fetchResUrl(v, activeL.id); });
      if (docs[0] && !docs[0].externalUrl) fetchResUrl(docs[0], activeL.id);
    }, [activeL?.id]);

    function fetchResUrl(r, lessonId) {
      if (!r || r.externalUrl) return;
      if (resUrls[r.id]?.url || resUrls[r.id]?.loading) return;
      setResUrls(m => ({ ...m, [r.id]: { loading: true } }));
      api.get(`/student/courses/${courseId}/lessons/${lessonId}/resources/${r.id}/view-url`)
        .then(res => setResUrls(m => ({ ...m, [r.id]: { url: res.data?.url } })))
        .catch(() => setResUrls(m => ({ ...m, [r.id]: { error: true } })));
    }

    function handleViewRes(r) {
      setViewRes(r);
      if (r && !r.externalUrl) fetchResUrl(r, activeL.id);
    }

    function goLesson(l) {
      setActiveL(l);
      if (courseId && l?.id) {
        try {
          localStorage.setItem('last_lesson_' + courseId, l.id);
          const newUrl = window.location.pathname + '?courseId=' + courseId + '&lessonId=' + l.id;
          window.history.replaceState(null, '', newUrl);
        } catch (e) { /* ignore */ }
      }
      const ch = chapters.find(c => c.lessons?.some(x => x.id === l.id));
      if (ch) setOpenCh(p => ({ ...p, [ch.id]: true }));
    }

    function getViewerUrl(res) {
      if (!res) return null;
      if (res.externalUrl) return res.externalUrl;
      return resUrls[res.id]?.url || null;
    }

    /* ── Progress: Video time tracking ───────────────────── */
    const handleVideoTimeUpdate = useCallback((e: any) => {
      const video = e.target;
      if (!video?.duration || !activeL?.id || !videoUrl) return;
      if (video.currentSrc && videoUrl && !video.currentSrc.includes(videoUrl) && !videoUrl.includes(video.currentSrc)) return;
      const pct = (video.currentTime / video.duration) * 100;
      const curProg = progRef.current[activeL.id];
      if (curProg === "COMPLETED") return;
      if (pct >= 5 && curProg !== "IN_PROGRESS") {
        progRef.current[activeL.id] = "IN_PROGRESS";
        updateProgress(Math.round(pct), Math.floor(video.currentTime), null, false);
      }
      if (pct >= 90 && curProg !== "COMPLETED") {
        progRef.current[activeL.id] = "COMPLETED";
        updateProgress(Math.round(pct), Math.floor(video.currentTime), null, true);
      }
    }, [activeL?.id, updateProgress, activeVideoIdx, videoUrl]);

    /* Reset progress ref when lesson changes (run first) */
    useEffect(() => {
      if (activeL?.id) {
        if (activeL.progress === "COMPLETED") {
          progRef.current[activeL.id] = "COMPLETED";
        } else if (!progRef.current[activeL.id]) {
          progRef.current[activeL.id] = activeL.progress || "IN_PROGRESS";
        }
      }
    }, [activeL?.id, activeL?.progress]);


    /* ── Progress: YouTube iframe tracking (no onTimeUpdate) ── */
    useEffect(() => {
      if (!activeL?.durationSeconds || progRef.current[activeL.id] === "COMPLETED") return;
      if (!activeVideoRes || !getYoutubeId(activeVideoRes.externalUrl || "")) return;
      const threshold = (activeL.durationSeconds * 90) / 100;
      let elapsed = 0;
      const t = setInterval(() => {
        elapsed += 1;
        if (elapsed >= threshold) {
          clearInterval(t);
          progRef.current[activeL.id] = "COMPLETED";
          updateProgress(100, null, null, true);
        } else if (elapsed >= 5 && progRef.current[activeL.id] !== "IN_PROGRESS") {
          progRef.current[activeL.id] = "IN_PROGRESS";
          updateProgress(Math.round((elapsed / activeL.durationSeconds) * 100), null, null, false);
        }
      }, 1000);
      return () => clearInterval(t);
    }, [activeL?.id, activeVideoRes?.id, activeVideoIdx]);

    /* ── Progress: Document timer (count seconds while viewing) ── */
    const docSecRef = useRef(0);
    const docTimerRef = useRef(null);
    useEffect(() => {
      if (activeL?.progress === "COMPLETED") return;
      if (!viewRes || viewRes.resourceType === "VIDEO") return;
      if (progRef.current[activeL?.id] === "COMPLETED") return;

      // Reset counter when switching documents
      docSecRef.current = 0;

      // Send initial progress immediately, then every 5 seconds
      updateProgress(null, null, 0, false);

      docTimerRef.current = setInterval(() => {
        docSecRef.current += 1;
        const isComp = !isVideoLesson && docSecRef.current >= 20;
        if (isComp) progRef.current[activeL?.id] = "COMPLETED";
        if (docSecRef.current % 5 === 0 || isComp) {
          updateProgress(null, null, docSecRef.current, isComp);
        }
      }, 1000);

      return () => {
        if (docTimerRef.current) clearInterval(docTimerRef.current);
        // Flush final count on unmount
        if (docSecRef.current > 0) {
          updateProgress(null, null, docSecRef.current, !isVideoLesson && docSecRef.current >= 20);
        }
      };
    }, [viewRes?.id, activeL?.id, isVideoLesson]);

    /* ── Progress: Text-only lesson (no video/doc, mark on access) ── */
    useEffect(() => {
      if (!activeL || activeL.progress === "COMPLETED") return;
      const hasVid = (activeL.resources || []).some((r: any) => !r.pendingDelete && r.resourceType === "VIDEO");
      const hasDoc = (activeL.resources || []).some((r: any) => !r.pendingDelete && r.resourceType !== "VIDEO");
      if (!hasVid && !hasDoc && !progRef.current[activeL.id]) {
        progRef.current[activeL.id] = "COMPLETED";
        updateProgress(null, null, null, true);
      }
    }, [activeL?.id]);

    /* ── Render ──────────────────────────────────────────── */
    return (
      <div className="main" style={{ minHeight: "100vh", display: "flex", flexDirection: "column" }}>
        {/* Top bar */}
        <div style={{ height: 64, flexShrink: 0, borderBottom: "1px solid rgba(255,255,255,.08)",
          display: "flex", alignItems: "center", gap: 10, padding: "0 20px",
          background: "#0f172a", zIndex: 30, position: "sticky", top: 0 }}>
          <button onClick={() => { if (onBack) onBack(); else if (navigate) navigate('/student/courses'); else window.location.href = '/student/courses'; }}
            title="Quay lại Khóa học của tôi"
            style={{ width: 30, height: 30, borderRadius: 8, border: "1px solid rgba(255,255,255,.12)",
              background: "rgba(255,255,255,.06)", color: "#cbd5e1", display: "grid",
              placeItems: "center", cursor: "pointer", flexShrink: 0 }}>
            <Ic n="arrow_left" size={15} />
          </button>
          <div onClick={() => { if (onDashboard) onDashboard(); else if (navigate) navigate('/student/dashboard'); else window.location.href = '/student/dashboard'; }}
            title="Về Dashboard"
            style={{ display: "flex", alignItems: "center", gap: 6, flexShrink: 0, cursor: "pointer" }}>
            <div className="sb-logo" style={{ width: 28, height: 28 }}>
              <Ic n="cap" size={16} />
            </div>
            <span style={{ fontWeight: 700, fontSize: 14, color: "#e2e8f0" }}>Rikkei Edu</span>
          </div>
          <div style={{ flex: 1 }} />
          <div style={{ position: "relative" }} onClick={e => e.stopPropagation()}>
            <button className="icon-btn" style={{ background: "rgba(255,255,255,.08)", borderColor: "rgba(255,255,255,.12)", color: "#e2e8f0" }}
              onClick={() => { setNotifOpen(o => !o); setUserMenu(false); if (!notifList.length) loadNotif(true); }}>
              <Ic n="bell" size={20} />
              {notifUnread > 0 && <span className="badge-count" style={{ borderColor: "#0f172a" }}>{notifUnread > 99 ? '99+' : notifUnread}</span>}
            </button>
            {notifOpen && (
              <div className="card" style={{ position: "absolute", right: 0, top: 52, width: 360, maxWidth: "90vw", padding: 0, boxShadow: "var(--sh-lg)", zIndex: 60, animation: "popIn .18s" }}>
                <div className="between" style={{ padding: "14px 18px", borderBottom: "1px solid var(--border)" }}><b>Thông báo</b><div className="row gap-8">{notifUnread > 0 && <span className="link" onClick={markAllRead}>Đánh dấu đã đọc</span>}</div></div>
                <div style={{ maxHeight: 380, overflowY: "auto" }}>
                  {notifLoading && <div className="t-sm muted" style={{ padding: "24px 18px", textAlign: "center" }}>Đang tải...</div>}
                  {!notifLoading && notifList.length === 0 && <div className="t-sm muted" style={{ padding: "24px 18px", textAlign: "center" }}>Chưa có thông báo nào.</div>}
                  {!notifLoading && notifList.slice(0, 5).map(n => {
                    const meta = notifMeta(n.type);
                    return (
                      <div key={n.id} className="row gap-12" style={{ padding: "13px 16px", background: n.read ? "#fff" : "var(--accent-soft)", borderBottom: "1px solid var(--border)", cursor: "pointer" }} onClick={() => { if (!n.read) markRead(n.id); if (n.targetUrl) { if (navigate) navigate(n.targetUrl); else window.location.href = n.targetUrl; } }}>
                        <div className="stat-ic" style={{ width: 38, height: 38, borderRadius: 10, background: meta.color + "1a", color: meta.color, flex: "none" }}><Ic n={meta.icon} size={18} /></div>
                        <div className="grow"><div className="t-sm" style={{ lineHeight: 1.4, fontWeight: n.read ? 400 : 600 }}>{n.title}</div><div className="t-xs dim" style={{ marginTop: 3 }}>{timeAgo(n.createdAt)}</div></div>
                        {!n.read && <span style={{ width: 8, height: 8, borderRadius: 999, background: "var(--accent)", flex: "none" }} />}
                      </div>
                    );
                  })}
                </div>
                <div className="between" style={{ padding: "10px 18px", borderTop: "1px solid var(--border)" }}>
                  <span className="link t-sm" onClick={() => { setNotifOpen(false); if (navigate) navigate('/notifications'); else window.location.href = '/notifications'; }}>Xem tất cả</span>
                  <span className="t-xs dim">{notifUnread} chưa đọc</span>
                </div>
              </div>
            )}
          </div>
          <button className="icon-btn" style={{ background: "rgba(255,255,255,.08)", borderColor: "rgba(255,255,255,.12)", color: "#e2e8f0" }}
            onClick={() => { setChat(c => !c); setUserMenu(false); setNotifOpen(false); }} title="Trợ lý AI Hỗ trợ">
            <Ic n="help" size={20} />
          </button>
          <div style={{ position: "relative" }} onClick={e => e.stopPropagation()}>
            <div className="user-pill" style={{ background: "rgba(255,255,255,.08)", borderColor: "rgba(255,255,255,.12)" }}
              onClick={() => { setUserMenu(u => !u); setNotifOpen(false); }}>
              <Av name={userName} size={34} />
              <div><div className="nm" style={{ color: "#e2e8f0" }}>{userName}</div><div className="rl" style={{ color: "#94a3b8" }}>Học tập</div></div>
              <Ic n="chevron_down" size={16} style={{ color: "#64748b" }} />
            </div>
            {userMenu && (
              <div className="card" style={{ position: "absolute", right: 0, top: 52, width: 240, padding: 8, boxShadow: "var(--sh-lg)", zIndex: 60, animation: "popIn .18s" }}>
                <div className="row gap-11" style={{ padding: "10px 12px", borderBottom: "1px solid var(--border)", marginBottom: 6 }}>
                  <Av name={userName} size={36} /><div style={{ minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 13 }} className="truncate">{userName}</div><div className="t-xs muted truncate">Học tập</div></div>
                </div>
                <div className="row gap-11" style={{ padding: "10px 12px", borderRadius: 9, cursor: "pointer", fontSize: 13, fontWeight: 500 }}
                  onClick={() => { setUserMenu(false); if (onSettings) onSettings(); else if (navigate) navigate('/settings'); else window.location.href = '/settings'; }}>
                  <Ic n="settings" size={16} />Cài đặt
                </div>
                <div className="row gap-11" style={{ padding: "10px 12px", borderRadius: 9, cursor: "pointer", fontSize: 13, fontWeight: 500, color: "var(--error)", borderTop: "1px solid var(--border)", marginTop: 6 }}
                  onClick={() => { setUserMenu(false); if (onLogout) onLogout(); else window.location.href = '/login'; }}>
                  <Ic n="logout" size={16} />Đăng xuất
                </div>
              </div>
            )}
          </div>
        </div>

        {loading && (
          <div style={{ flex: 1, display: "grid", placeItems: "center", color: "#94a3b8", fontSize: 14 }}>
            <div style={{ textAlign: "center" }}>
              <div style={{ marginBottom: 10, opacity: 0.3 }}><Ic n="book" size={32} /></div>
              Đang tải khóa học...
            </div>
          </div>
        )}

        {error && (
          <div style={{ flex: 1, display: "grid", placeItems: "center" }}>
            <div style={{ textAlign: "center", padding: 24 }}>
              <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}><Ic n="book" size={40} /></div>
              <div style={{ color: "#dc2626", marginBottom: 14, fontSize: 15 }}>{error}</div>
              <button className="btn btn-ghost" onClick={onBack}><Ic n="arrow_left" size={15} />Quay lại</button>
            </div>
          </div>
        )}

        {!loading && !error && course && (
          <div className="lecture-wrap" style={{ display: "flex", gap: 0, flex: 1, alignItems: "stretch", minHeight: 0 }}>
            {/* ─── Left: Main content ─── */}
            <div style={{ flex: 1, minWidth: 0, minHeight: 0, overflowY: "auto",
              padding: "24px 24px 32px" }}>
              {!activeL ? (
                <div style={{ textAlign: "center", padding: 60, color: "#94a3b8" }}>
                  <Ic n="book" size={32} style={{ opacity: 0.3, marginBottom: 12 }} />
                  <div>Chọn một bài giảng từ danh sách</div>
                </div>
              ) : (
                <>
                  {/* Breadcrumb */}
                  <div className="t-sm muted" style={{ marginBottom: 6, display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap" }}>
                    <span>{course.title}</span>
                    {activeCh && <><Ic n="chevron_right" size={11} /><span>{activeCh.title}</span></>}
                    <Ic n="chevron_right" size={11} /><span style={{ color: "#0f172a", fontWeight: 600 }}>{activeL.title}</span>
                  </div>
                  {/* Lesson title */}
                  <h1 className="t-h1" style={{ marginBottom: 20, fontSize: 22 }}>
                    {activeL.title}
                  </h1>

                  {/* ─── Video Section ─── */}
                  {videoResources.length > 0 && (
                    <div style={{ marginBottom: isVideoActive ? 24 : 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
                        <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "#0f172a" }}>Danh sách Video</h3>
                        <span className="chip" style={{ background: "#f1f5f9", color: "#64748b", fontSize: 11, fontWeight: 600 }}>{videoResources.length}</span>
                      </div>
                      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))", gap: 10, marginBottom: 14 }}>
                        {videoResources.map((v, i) => (
                          <ResourceCard key={v.id}
                            title={v.displayName || v.originalFilename || `Video ${i + 1}`}
                            subtitle={v.fileSizeBytes ? fmtBytes(v.fileSizeBytes) + " • Video" : `Video bài giảng ${i + 1}`}
                            icon="video"
                            iconBg="#eaf1ff" iconColor="#2563eb"
                            isActive={i === activeVideoIdx}
                            onClick={() => setActiveVideoIdx(i)} />
                        ))}
                      </div>
                      {isVideoActive && (
                        <Viewer key={activeVideoRes?.id || videoUrl || activeVideoIdx} res={activeVideoRes} url={videoUrl} onVideoTimeUpdate={handleVideoTimeUpdate} />
                      )}
                    </div>
                  )}

                  {/* ─── Documents Section ─── */}
                  <div style={{ marginTop: isVideoActive || videoResources.length > 0 ? 12 : 0 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
                      <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "#0f172a" }}>Tài liệu học tập</h3>
                      <span className="chip" style={{ background: "#f1f5f9", color: "#64748b", fontSize: 11, fontWeight: 600 }}>{docResources.length}</span>
                    </div>
                    {docResources.length > 0 ? (
                      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))", gap: 10, marginBottom: viewRes && viewRes.resourceType !== "VIDEO" ? 14 : 0 }}>
                        {docResources.map(r => {
                          const rs = RS[r.resourceType] || RS.OTHER;
                          const sub = [fmtBytes(r.fileSizeBytes), r.resourceType].filter(Boolean).join(" • ") || r.resourceType;
                          return (
                            <ResourceCard key={r.id}
                              title={r.displayName || r.originalFilename}
                              subtitle={sub}
                              icon={rs.icon}
                              iconBg={rs.bg} iconColor={rs.color}
                              isActive={viewRes?.id === r.id}
                              onClick={() => handleViewRes(r)} />
                          );
                        })}
                      </div>
                    ) : (
                      <div style={{ padding: "24px 16px", textAlign: "center", color: "#94a3b8",
                        border: "1px dashed var(--border)", borderRadius: 12, fontSize: 13 }}>
                        Bài học này chưa có tài liệu kèm theo
                      </div>
                    )}
                    {viewRes && viewRes.resourceType !== "VIDEO" && (
                      <Viewer key={viewRes?.id || getViewerUrl(viewRes)} res={viewRes} url={getViewerUrl(viewRes)} />
                    )}
                  </div>

                  {/* Prev / Next */}
                  <div style={{ marginTop: 28, display: "flex", gap: 12 }}>
                    <button disabled={!prevLesson} onClick={() => prevLesson && goLesson(prevLesson)}
                      style={{ flex: 1, height: 48, display: "flex", alignItems: "center", gap: 10,
                        padding: "0 16px", border: "1px solid var(--border)", borderRadius: 12,
                        background: "#fff", cursor: prevLesson ? "pointer" : "default",
                        opacity: prevLesson ? 1 : 0.35 }}
                      onMouseEnter={e => { if (prevLesson) e.currentTarget.style.borderColor = "#2563eb"; }}
                      onMouseLeave={e => { e.currentTarget.style.borderColor = "var(--border)"; }}>
                      <Ic n="arrow_left" size={15} style={{ color: "#94a3b8", flexShrink: 0 }} />
                      <div style={{ textAlign: "left", minWidth: 0 }}>
                        <div style={{ fontSize: 10.5, color: "#94a3b8", fontWeight: 500 }}>Bài trước</div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "#0f172a" }} className="truncate">
                          {prevLesson ? prevLesson.title : "—"}
                        </div>
                      </div>
                    </button>
                    <button disabled={!nextLesson} onClick={() => nextLesson && goLesson(nextLesson)}
                      style={{ flex: 1, height: 48, display: "flex", alignItems: "center",
                        justifyContent: "flex-end", gap: 10, padding: "0 16px",
                        border: `1px solid ${nextLesson ? "#2563eb" : "var(--border)"}`,
                        borderRadius: 12, background: nextLesson ? "#eff6ff" : "#fff",
                        cursor: nextLesson ? "pointer" : "default",
                        opacity: nextLesson ? 1 : 0.35 }}
                      onMouseEnter={e => { if (nextLesson) e.currentTarget.style.background = "#dbeafe"; }}
                      onMouseLeave={e => { e.currentTarget.style.background = nextLesson ? "#eff6ff" : "#fff"; }}>
                      <div style={{ textAlign: "right", minWidth: 0 }}>
                        <div style={{ fontSize: 10.5, color: "#2563eb", fontWeight: 500 }}>Bài tiếp theo</div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "#0f172a" }} className="truncate">
                          {nextLesson ? nextLesson.title : "—"}
                        </div>
                      </div>
                      <Ic n="arrow_right" size={15} style={{ color: "#2563eb", flexShrink: 0 }} />
                    </button>
                  </div>
                </>
              )}
            </div>

            {/* ─── Right: Dark curriculum sidebar ─── */}
            <div className="dark-scroll lecture-rail"
              style={{ width: 380, flex: "none", background: "#0f172a", color: "#fff",
                padding: "22px 20px", overflowY: "auto",
                maxHeight: "calc(100vh - 64px)", position: "sticky", top: 64 }}>
              <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700, lineHeight: 1.4 }}>
                {course.title}
              </h3>
              <div style={{ fontSize: 12.5, color: "#94a3b8", marginBottom: 10, display: "flex", gap: 16 }}>
                <span>{chapters.length} chương</span>
                <span>•</span>
                <span>{totalLessons} bài giảng</span>
              </div>

              {/* Progress bar */}
              <div style={{ marginBottom: 16 }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "#94a3b8", marginBottom: 4 }}>
                  <span>Tiến độ</span>
                  <span>{progressPct}% ({completedCount}/{totalLessons})</span>
                </div>
                <div style={{ height: 4, borderRadius: 999, background: "rgba(255,255,255,.1)", overflow: "hidden" }}>
                  <div style={{ width: `${progressPct}%`, height: "100%", background: "#10b981", borderRadius: 999, transition: "width .3s" }} />
                </div>
              </div>

              {/* Chapter accordion */}
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                {chapters.map(ch => {
                  const isOpen = openCh[ch.id];
                  const chDone = ch.lessons?.every(l => l.progress === "COMPLETED");
                  return (
                    <div key={ch.id}
                      style={{ background: "rgba(255,255,255,.04)", border: "1px solid rgba(255,255,255,.07)",
                        borderRadius: 13, overflow: "hidden" }}>
                      {/* Chapter header */}
                      <div onClick={() => setOpenCh(p => ({ ...p, [ch.id]: !p[ch.id] }))}
                        style={{ display: "flex", alignItems: "center", gap: 10,
                          padding: "13px 14px", cursor: "pointer" }}>
                        <div
                          title={"Tiến độ chương: " + (chDone ? "Đã hoàn thành" : "Chưa hoàn thành")}
                          style={{ width: 22, height: 22, borderRadius: 999,
                            background: chDone ? "#10b981" : "transparent",
                            border: chDone ? "none" : "1.5px solid #475569",
                            display: "grid", placeItems: "center", flexShrink: 0,
                            transition: ".15s" }}>
                          {chDone && <span style={{ color: "#fff", fontSize: 13, lineHeight: 1, fontWeight: 700 }}>✓</span>}
                        </div>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: 13.5, fontWeight: 600 }} className="truncate">
                            {ch.title}
                          </div>
                          <div style={{ fontSize: 11, color: "#64748b", marginTop: 1 }}>
                            {ch.lessons?.length || 0} bài
                          </div>
                        </div>
                        <Ic n="chevron_down" size={16}
                          style={{ transform: isOpen ? "rotate(180deg)" : "none",
                            transition: ".2s", color: "#64748b", flexShrink: 0 }} />
                      </div>

                      {/* Lessons */}
                      {isOpen && (ch.lessons || []).map(l => {
                        const isAct = activeL?.id === l.id;
                        const lDone = l.progress === "COMPLETED";
                        const lProg = l.progress === "IN_PROGRESS";
                        const lHasVid = (l.resources || []).some((r: any) => !r.pendingDelete && r.resourceType === "VIDEO");
                        const lHasDoc = (l.resources || []).some((r: any) => !r.pendingDelete && r.resourceType !== "VIDEO");
                        const lIsVid  = lHasVid || (l.type === "VIDEO" && !lHasDoc);
                        const isQuiz     = l.type === "QUIZ";
                        const quizLocked = isQuiz && l.quizStatus !== "PUBLISHED";
                        return (
                          <div key={l.id}
                            onClick={() => {
                              if (quizLocked) return;
                              if (isQuiz) {
                                const url = `/player/quiz?courseId=${courseId}&quizId=${l.quizId}&from=lecture&lessonId=${l.id}`;
                                if (navigate) navigate(url); else window.location.href = url;
                                return;
                              }
                              goLesson(l);
                            }}
                            title={quizLocked ? "Giảng viên chưa xuất bản đề này" : undefined}
                            style={{ display: "flex", alignItems: "center", gap: 10,
                              padding: "10px 10px 10px 48px", cursor: quizLocked ? "not-allowed" : "pointer",
                              opacity: quizLocked ? 0.5 : 1,
                              background: isAct ? "rgba(16,185,129,.13)" : "transparent",
                              transition: ".12s" }}
                            onMouseEnter={e => { if (!isAct && !quizLocked) e.currentTarget.style.background = "rgba(255,255,255,.03)"; }}
                            onMouseLeave={e => { if (!isAct) e.currentTarget.style.background = "transparent"; }}>
                            <div
                              title={"Tiến độ bài học: " + (lDone ? "Đã hoàn thành" : lProg ? "Đang học" : "Chưa bắt đầu")}
                              style={{ width: 20, height: 20, borderRadius: 999, flexShrink: 0,
                                display: "grid", placeItems: "center", transition: ".15s",
                                background: lDone ? "#10b981" : "transparent",
                                border: lDone ? "none" : lProg ? "2px solid #34d399" : "1.5px solid #475569" }}>
                              {lDone && <span style={{ color: "#fff", fontSize: 12, lineHeight: 1, fontWeight: 700 }}>✓</span>}
                              {lProg && !lDone && <span style={{ width: 6, height: 6, borderRadius: 999, background: "#34d399" }} />}
                            </div>
                            <div style={{ flex: 1, minWidth: 0 }}>
                              <div style={{ fontSize: 13, fontWeight: isAct ? 700 : 500,
                                color: isAct ? "#6ff5c0" : "#cbd5e1" }} className="truncate">
                                {l.title}
                              </div>
                              <div style={{ fontSize: 11, color: "#64748b", marginTop: 2,
                                display: "flex", alignItems: "center", gap: 4 }}>
                                <Ic n={isQuiz ? "clipboard" : lIsVid ? "video" : "file"} size={11} />
                                {isQuiz ? (quizLocked ? "Đề trắc nghiệm (chưa xuất bản)" : "Đề trắc nghiệm") : lIsVid ? "Video" : "Tài liệu"}
                              </div>
                            </div>
                            {isAct && (
                              <div style={{ width: 8, height: 8, borderRadius: 999,
                                background: "#10b981", flexShrink: 0 }} />
                            )}
                          </div>
                        );
                      })}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* ─── AI Chatbot ─── */}
        {chat && (
          <div style={{ position: "fixed", right: 24, bottom: 96, width: 380,
            maxWidth: "calc(100vw - 32px)", height: 520, maxHeight: "70vh",
            background: "rgba(255,255,255,.86)", backdropFilter: "blur(18px)",
            border: "1px solid rgba(255,255,255,.6)", borderRadius: 20,
            boxShadow: "0 8px 40px rgba(0,0,0,.15)", display: "flex",
            flexDirection: "column", zIndex: 70, animation: "popIn .25s" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12,
              padding: 16, borderBottom: "1px solid var(--border)" }}>
              <div className="sb-logo" style={{ width: 40, height: 40,
                background: "linear-gradient(150deg,#7c3aed,#2563eb)" }}>
                <Ic n="sparkles" size={20} />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 700 }}>Trợ lý AI Rikkei</div>
                <div className="t-xs" style={{ color: "var(--success)" }}>Trực tuyến 24/7</div>
              </div>
              <button className="icon-btn" style={{ width: 34, height: 34 }}
                onClick={() => setChat(false)}>
                <Ic n="x" size={17} />
              </button>
            </div>
            <div style={{ flex: 1, overflowY: "auto", padding: 16,
              display: "flex", flexDirection: "column", gap: 12 }}>
              {msgs.map((m, i) => (
                <div key={i} style={{ alignSelf: m.me ? "flex-end" : "flex-start", maxWidth: "85%" }}>
                  <div style={{ padding: "10px 14px", borderRadius: 14, fontSize: 13.5,
                    lineHeight: 1.55,
                    background: m.me ? "var(--primary)" : "#fff",
                    color: m.me ? "#fff" : "var(--text)",
                    border: m.me ? "none" : "1px solid var(--border)" }}>
                    {m.t}
                  </div>
                  {!m.me && i > 0 && (
                    <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
                      <button className="icon-btn" style={{ width: 28, height: 28, color: "var(--text-3)" }}>
                        <Ic n="thumbs_up" size={14} />
                      </button>
                      <button className="icon-btn" style={{ width: 28, height: 28, color: "var(--text-3)" }}>
                        <Ic n="thumbs_down" size={14} />
                      </button>
                    </div>
                  )}
                </div>
              ))}
              {sending && (
                <div style={{ alignSelf: "flex-start", maxWidth: "85%" }}>
                  <div style={{ padding: "10px 14px", borderRadius: 14, fontSize: 13.5, background: "#fff", color: "var(--text-3)", border: "1px solid var(--border)", borderBottomLeftRadius: 4 }}>Đang trả lời...</div>
                </div>
              )}
              <div ref={endRef} />
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8,
              padding: 14, borderTop: "1px solid var(--border)" }}>
              <input className="input" placeholder="Nhập câu hỏi của bạn..."
                value={input} disabled={sending} onChange={e => setInput(e.target.value)}
                onKeyDown={e => e.key === "Enter" && send()} />
              <button className="btn btn-primary btn-icon" disabled={sending} onClick={send}>
                <Ic n="send" size={17} />
              </button>

            </div>
          </div>
        )}

        <button onClick={() => setChat(!chat)}
          style={{ position: "fixed", right: 24, bottom: 24,
            width: 60, height: 60, borderRadius: 999, border: "none",
            background: "linear-gradient(150deg,#7c3aed,#2563eb)",
            color: "#fff", display: "grid", placeItems: "center",
            cursor: "pointer", boxShadow: "0 10px 30px rgba(124,58,237,.4)",
            zIndex: 71, transition: ".2s" }}>
          <Ic n={chat ? "x" : "sparkles"} size={26} />
        </button>
      </div>
    );
  }

  Object.assign(window, { LecturePlayer });
})();
