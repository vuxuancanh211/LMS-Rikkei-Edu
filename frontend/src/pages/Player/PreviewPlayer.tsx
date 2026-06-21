// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Xem trước khóa học (Admin / Giảng viên)
   Layout tham khảo: OneConnect e-learning player
   ============================================================ */
(function () {
  const { useState, useEffect, useRef, useMemo } = React;
  const Ic = window.Icon;
  const api = window.httpClient;

  /* ── Colors / meta ─────────────────────────────────────── */
  const RS = {
    PDF:   { bg: "#fdecec", color: "#dc2626", icon: "file"      },
    DOC:   { bg: "#eaf1ff", color: "#2563eb", icon: "file-text" },
    SLIDE: { bg: "#fef5e6", color: "#d97706", icon: "layers"    },
    IMAGE: { bg: "#f0fdf4", color: "#16a34a", icon: "image"     },
    VIDEO: { bg: "#eaf1ff", color: "#2563eb", icon: "video"     },
    OTHER: { bg: "#f1f5f9", color: "#475569", icon: "file"      },
  };

  const C = {
    bg:       "#f8fafc",
    sidebar:  "#ffffff",
    border:   "#e2e8f0",
    text:     "#0f172a",
    text2:    "#475569",
    text3:    "#94a3b8",
    accent:   "#2563eb",
    accentBg: "#eff6ff",
    surface:  "#f1f5f9",
  };

  function getYoutubeId(url) {
    try {
      const u = new URL(url);
      if (u.hostname === "youtu.be") return u.pathname.slice(1).split("?")[0];
      if (u.hostname.includes("youtube.com")) return u.searchParams.get("v");
    } catch {}
    return null;
  }

  function fmtDur(s) {
    if (!s) return null;
    return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
  }
  function fmtSize(b) {
    if (!b) return "";
    return b < 1048576 ? (b / 1024).toFixed(0) + " KB" : (b / 1048576).toFixed(1) + " MB";
  }

  /* ── Video player ─────────────────────────────────────── */
  function VideoArea({ lesson }) {
    const ref = useRef();
    useEffect(() => { ref.current?.load(); }, [lesson?.id]);

    if (!lesson?.hlsManifestUrl) return (
      <div style={{ aspectRatio: "16/9", background: "#0f172a", borderRadius: 16, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 12, marginBottom: 24 }}>
        <div style={{ width: 56, height: 56, borderRadius: 14, background: "rgba(255,255,255,.06)", display: "grid", placeItems: "center", color: "#334155" }}>
          <Ic n="video" size={26} />
        </div>
        <div style={{ color: "#475569", fontSize: 14 }}>
          {lesson?.videoStatus === "PROCESSING" ? "Video đang xử lý..." : "Video chưa sẵn sàng"}
        </div>
      </div>
    );

    return (
      <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", aspectRatio: "16/9", background: "#0f172a", marginBottom: 24, boxShadow: "0 4px 24px rgba(0,0,0,.12)" }}>
        <video ref={ref} controls style={{ width: "100%", height: "100%", display: "block" }} src={lesson.hlsManifestUrl} />
        <span style={{ position: "absolute", right: 12, top: 12, background: "rgba(15,23,42,.75)", color: "#fef9c3", borderRadius: 6, padding: "3px 10px", fontSize: 12, display: "flex", alignItems: "center", gap: 5, pointerEvents: "none" }}>
          <Ic n="eye" size={12} />Xem trước
        </span>
      </div>
    );
  }

  /* ── Main ─────────────────────────────────────────────── */
  function PreviewPlayer({ onBack }) {
    const { courseId, role } = window.__previewCourse || {};

    const [course,   setCourse]   = useState(null);
    const [chapters, setChapters] = useState([]);
    const [active,   setActive]   = useState(null);
    const [openCh,   setOpenCh]   = useState({});
    const [loading,  setLoading]  = useState(true);
    const [resUrls,    setResUrls]    = useState({});  // resourceId -> { url, loading, error }
    const [expandedRes, setExpandedRes] = useState(null); // resourceId đang mở
    const [sidebarOpen, setSidebarOpen] = useState(true);

    const allLessons = useMemo(() => chapters.flatMap(ch => ch.lessons || []), [chapters]);
    const activeIdx  = useMemo(() => allLessons.findIndex(l => l.id === active?.id), [allLessons, active]);
    const activeCh   = useMemo(() => chapters.find(c => c.lessons?.some(l => l.id === active?.id)), [chapters, active]);
    const totalLessons = useMemo(() => allLessons.length, [allLessons]);

    /* Reset expanded khi đổi bài */
    useEffect(() => { setExpandedRes(null); }, [active?.id]);

    /* Fetch URL khi mở resource */
    function fetchResUrl(r) {
      if (resUrls[r.id]?.url || resUrls[r.id]?.loading) return;
      if (r.externalUrl) { setResUrls(m => ({ ...m, [r.id]: { url: r.externalUrl, loading: false } })); return; }
      setResUrls(m => ({ ...m, [r.id]: { loading: true } }));
      const ep = role === "admin"
        ? `/admin/courses/resources/${r.id}/download-url`
        : `/instructor/courses/${courseId}/lessons/${active.id}/resources/${r.id}/download-url`;
      api.get(ep)
        .then(res => setResUrls(m => ({ ...m, [r.id]: { url: res.data.url, loading: false } })))
        .catch(() => setResUrls(m => ({ ...m, [r.id]: { loading: false, error: true } })));
    }

    function toggleRes(r) {
      if (expandedRes === r.id) { setExpandedRes(null); return; }
      setExpandedRes(r.id);
      fetchResUrl(r);
    }


    useEffect(() => {
      if (!courseId) { setLoading(false); return; }
      const ep = role === "admin"
        ? `/admin/courses/${courseId}`
        : `/instructor/courses/${courseId}`;
      api.get(ep)
        .then(r => {
          const data = r.data;
          setCourse(data);
          const chs = data.chapters || [];
          setChapters(chs);
          if (chs.length > 0) {
            const opened = {};
            chs.forEach(ch => { opened[ch.id] = true; });
            setOpenCh(opened);
            if (chs[0].lessons?.length > 0) setActive(chs[0].lessons[0]);
          }
        })
        .catch(() => {})
        .finally(() => setLoading(false));
    }, [courseId, role]);

    function goLesson(lesson) {
      setActive(lesson);
      const ch = chapters.find(c => c.lessons?.some(l => l.id === lesson.id));
      if (ch) setOpenCh(prev => ({ ...prev, [ch.id]: true }));
      /* scroll content to top */
      document.getElementById("pp-content")?.scrollTo({ top: 0, behavior: "smooth" });
    }

    /* ── Overlay wrapper (fullscreen modal) ──────────────── */
    const Overlay = ({ children }) => (
      <div style={{ position: "fixed", inset: 0, zIndex: 200, background: C.bg, display: "flex", flexDirection: "column" }}>
        {children}
      </div>
    );

    /* ── Guards ───────────────────────────────────────────── */
    if (loading) return (
      <Overlay>
        <div style={{ flex: 1, display: "grid", placeItems: "center", color: C.text3 }}>
          <div style={{ textAlign: "center" }}>
            <div style={{ width: 48, height: 48, borderRadius: 14, background: C.surface, display: "grid", placeItems: "center", margin: "0 auto 14px", color: C.accent }}>
              <Ic n="book" size={24} />
            </div>
            Đang tải khóa học...
          </div>
        </div>
      </Overlay>
    );

    if (!courseId || !course) return (
      <Overlay>
        <div style={{ flex: 1, display: "grid", placeItems: "center" }}>
          <div style={{ textAlign: "center" }}>
            <div style={{ color: C.text3, marginBottom: 16 }}>Không tìm thấy khóa học.</div>
            <button className="btn btn-ghost" onClick={onBack}><Ic n="arrow_left" size={16} />Quay lại</button>
          </div>
        </div>
      </Overlay>
    );

    const isVideo = active?.lessonType === "VIDEO";
    const isText  = active?.lessonType === "TEXT";
    const prevLesson = activeIdx > 0 ? allLessons[activeIdx - 1] : null;
    const nextLesson = activeIdx < allLessons.length - 1 ? allLessons[activeIdx + 1] : null;
    const prevCh = prevLesson ? chapters.find(c => c.lessons?.some(l => l.id === prevLesson.id)) : null;
    const nextCh = nextLesson ? chapters.find(c => c.lessons?.some(l => l.id === nextLesson.id)) : null;

    return (
      <Overlay>
      <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>

        {/* ── Topbar ─────────────────────────────────────── */}
        <div style={{ height: 56, flexShrink: 0, background: "#fff", borderBottom: `1px solid ${C.border}`, display: "flex", alignItems: "center", gap: 12, padding: "0 20px", zIndex: 10 }}>
          <button onClick={onBack} style={{ width: 34, height: 34, borderRadius: 9, border: `1px solid ${C.border}`, background: "transparent", color: C.text2, display: "grid", placeItems: "center", cursor: "pointer" }}>
            <Ic n="arrow_left" size={17} />
          </button>
          {/* Logo */}
          <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
            <div style={{ width: 28, height: 28, borderRadius: 7, background: "linear-gradient(135deg,#1e40af,#3b82f6)", display: "grid", placeItems: "center" }}>
              <Ic n="book" size={14} style={{ color: "#fff" }} />
            </div>
            <span style={{ fontWeight: 700, fontSize: 14, color: C.text }}>Rikkei Edu</span>
          </div>
          <div style={{ width: 1, height: 20, background: C.border, margin: "0 4px" }} />
          {/* Breadcrumb */}
          <div style={{ display: "flex", alignItems: "center", gap: 6, flex: 1, minWidth: 0, fontSize: 13 }}>
            <span style={{ color: C.text3, flexShrink: 0 }} className="truncate">{course.title}</span>
            {activeCh && <>
              <Ic n="chevron_right" size={13} style={{ color: C.text3, flexShrink: 0 }} />
              <span style={{ color: C.text2, fontWeight: 500, flexShrink: 0 }} className="truncate">{activeCh.title}</span>
            </>}
            {active && <>
              <Ic n="chevron_right" size={13} style={{ color: C.text3, flexShrink: 0 }} />
              <span style={{ color: C.text, fontWeight: 600, flexShrink: 0 }} className="truncate">{active.title}</span>
            </>}
          </div>
          <span style={{ background: "#fef9c3", color: "#92400e", borderRadius: 20, padding: "3px 11px", fontSize: 12, fontWeight: 600, display: "flex", alignItems: "center", gap: 5, flexShrink: 0 }}>
            <Ic n="eye" size={12} />Xem trước
          </span>
          {/* <button onClick={onBack} style={{ border: `1px solid ${C.border}`, background: "transparent", color: C.text2, borderRadius: 8, padding: "5px 13px", cursor: "pointer", fontSize: 13, display: "flex", alignItems: "center", gap: 6, flexShrink: 0 }}>
            <Ic n="x" size={14} />Đóng
          </button> */}
        </div>

        {/* ── Body ─────────────────────────────────────────── */}
        <div style={{ flex: 1, display: "flex", overflow: "hidden" }}>

          {/* ── Content ──────────────────────────────────── */}
          <div id="pp-content" style={{ flex: 1, minWidth: 0, overflowY: "auto", padding: 20 }}>
            <div style={{ maxWidth: 860, margin: "0 auto" }}>
              {active ? (
                <div style={{ background: "#fff", borderRadius: 20, padding: "28px 32px", boxShadow: "0 1px 4px rgba(0,0,0,.06)", border: `1px solid ${C.border}` }}>
                  {/* Video */}
                  {isVideo && <VideoArea lesson={active} />}

                  {/* Text content */}
                  {isText && (
                    <div style={{ background: C.surface, borderRadius: 14, padding: "22px 26px", marginBottom: 24, lineHeight: 1.85, fontSize: 15, color: C.text }}>
                      {active.contentText
                        ? <div style={{ whiteSpace: "pre-wrap" }} dangerouslySetInnerHTML={{ __html: active.contentText }} />
                        : <div style={{ color: C.text3, fontStyle: "italic" }}>Bài giảng này chưa có nội dung văn bản.</div>
                      }
                    </div>
                  )}

                  {/* Description */}
                  {active.description && (
                    <div style={{ marginBottom: 24, padding: "14px 18px", background: C.accentBg, border: `1px solid #bfdbfe`, borderRadius: 12 }}>
                      <div style={{ fontSize: 11.5, fontWeight: 700, color: C.accent, textTransform: "uppercase", letterSpacing: ".5px", marginBottom: 6 }}>Mô tả bài học</div>
                      <div style={{ fontSize: 14, lineHeight: 1.7, color: C.text2, whiteSpace: "pre-wrap" }}>{active.description}</div>
                    </div>
                  )}

                  {/* Resources — accordion */}
                  {(active.resources || []).length > 0 && (
                    <div style={{ marginBottom: 28 }}>
                      <div style={{ fontSize: 13.5, fontWeight: 700, color: C.text, marginBottom: 10, display: "flex", alignItems: "center", gap: 8 }}>
                        <Ic n="paperclip" size={15} style={{ color: C.text2 }} />
                        Tài liệu đính kèm
                        <span style={{ fontWeight: 500, color: C.text3, fontSize: 12, background: C.surface, borderRadius: 20, padding: "1px 8px" }}>{active.resources.length}</span>
                      </div>
                      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                        {active.resources.map(r => {
                          const rs       = RS[r.resourceType] || RS.OTHER;
                          const isOpen   = expandedRes === r.id;
                          const state    = resUrls[r.id] || {};
                          const { url, loading: rLoading, error: rError } = state;
                          const isImg    = r.resourceType === "IMAGE";
                          const isVid    = r.resourceType === "VIDEO";
                          const isEmbed  = ["PDF","DOC","SLIDE"].includes(r.resourceType);
                          const ytId     = url && isVid ? getYoutubeId(url) : null;
                          const ytEmbed  = ytId ? `https://www.youtube-nocookie.com/embed/${ytId}?rel=0` : null;
                          const gdUrl    = url && isEmbed
                            ? `https://docs.google.com/viewer?url=${encodeURIComponent(url)}&embedded=true`
                            : null;

                          return (
                            <div key={r.id} style={{ border: `1px solid ${isOpen ? C.accent : C.border}`, borderRadius: 12, overflow: "hidden", background: "#fff", transition: "border-color .15s" }}>
                              {/* Header — click để toggle */}
                              <div onClick={() => toggleRes(r)}
                                style={{ display: "flex", alignItems: "center", gap: 10, padding: "10px 14px", cursor: "pointer", background: isOpen ? C.accentBg : "#fff", transition: "background .15s" }}>
                                <div style={{ width: 32, height: 32, borderRadius: 8, flexShrink: 0, background: rs.bg, color: rs.color, display: "grid", placeItems: "center" }}>
                                  <Ic n={rs.icon} size={15} />
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                  <div style={{ fontWeight: 600, fontSize: 13.5, color: isOpen ? C.accent : C.text }} className="truncate">{r.displayName || r.originalFilename}</div>
                                  <div style={{ fontSize: 11.5, color: C.text3 }}>{r.resourceType}{r.fileSizeBytes ? " · " + fmtSize(r.fileSizeBytes) : ""}</div>
                                </div>
                                {/* Download / YouTube link — stop propagation */}
                                {url && !ytId && (
                                  <a href={url} target="_blank" rel="noreferrer" onClick={e => e.stopPropagation()}
                                    style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12, color: C.accent, textDecoration: "none", border: `1px solid #bfdbfe`, borderRadius: 7, padding: "3px 9px", flexShrink: 0, background: "#fff" }}>
                                    <Ic n="download" size={12} />Tải
                                  </a>
                                )}
                                {url && ytId && (
                                  <a href={url} target="_blank" rel="noreferrer" onClick={e => e.stopPropagation()}
                                    style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12, color: "#dc2626", textDecoration: "none", border: "1px solid #fecaca", borderRadius: 7, padding: "3px 9px", flexShrink: 0, background: "#fff" }}>
                                    <Ic n="external_link" size={12} />YT
                                  </a>
                                )}
                                <Ic n="chevron_down" size={15} style={{ color: isOpen ? C.accent : C.text3, flexShrink: 0, transform: isOpen ? "rotate(180deg)" : "none", transition: "transform .2s" }} />
                              </div>

                              {/* Content — chỉ render khi mở */}
                              {isOpen && (
                                <div style={{ borderTop: `1px solid ${C.border}` }}>
                                  {rLoading && (
                                    <div style={{ padding: "32px 0", textAlign: "center", color: C.text3, fontSize: 13 }}>Đang tải...</div>
                                  )}
                                  {rError && (
                                    <div style={{ padding: "20px 0", textAlign: "center", color: "#dc2626", fontSize: 13 }}>Không thể tải tài liệu</div>
                                  )}
                                  {url && isImg && (
                                    <div style={{ background: C.surface, display: "flex", justifyContent: "center", padding: 16 }}>
                                      <img src={url} alt={r.displayName || r.originalFilename}
                                        style={{ maxWidth: "100%", maxHeight: 520, borderRadius: 10, objectFit: "contain", display: "block" }} />
                                    </div>
                                  )}
                                  {url && isVid && ytEmbed && (
                                    <div style={{ position: "relative", paddingBottom: "56.25%", background: "#000" }}>
                                      <iframe src={ytEmbed} title={r.displayName || "Video"}
                                        style={{ position: "absolute", inset: 0, width: "100%", height: "100%", border: "none" }}
                                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                                        allowFullScreen />
                                    </div>
                                  )}
                                  {url && isVid && !ytEmbed && (
                                    <video controls src={url} style={{ width: "100%", display: "block", background: "#000", maxHeight: 420 }} />
                                  )}
                                  {url && gdUrl && (
                                    <iframe src={gdUrl} title={r.displayName || r.originalFilename}
                                      style={{ width: "100%", height: 540, border: "none", display: "block" }}
                                      sandbox="allow-scripts allow-same-origin allow-popups" />
                                  )}
                                  {url && !isImg && !isVid && !isEmbed && (
                                    <div style={{ padding: "18px 16px", background: C.surface, textAlign: "center", color: C.text3, fontSize: 13 }}>
                                      Định dạng này không hỗ trợ xem trực tiếp — hãy tải xuống để mở.
                                    </div>
                                  )}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* Prev / Next — style như mẫu OneConnect */}
                  <div style={{ display: "flex", gap: 10, paddingTop: 20, borderTop: `1px solid ${C.border}` }}>
                    <button disabled={!prevLesson} onClick={() => prevLesson && goLesson(prevLesson)}
                      style={{ flex: 1, height: 56, display: "flex", alignItems: "center", gap: 10, padding: "0 16px", border: `1px solid ${C.border}`, borderRadius: 12, background: prevLesson ? "#fff" : C.surface, cursor: prevLesson ? "pointer" : "default", opacity: prevLesson ? 1 : 0.45, transition: ".15s" }}
                      onMouseEnter={e => { if(prevLesson) e.currentTarget.style.borderColor = C.accent; }}
                      onMouseLeave={e => { e.currentTarget.style.borderColor = C.border; }}>
                      <Ic n="arrow_left" size={16} style={{ color: C.text3, flexShrink: 0 }} />
                      <div style={{ textAlign: "left", minWidth: 0 }}>
                        <div style={{ fontSize: 11, color: C.text3, fontWeight: 500 }}>Bài trước</div>
                        {prevLesson && <div style={{ fontSize: 13, fontWeight: 600, color: C.text, lineHeight: 1.3 }} className="truncate">{prevLesson.title}</div>}
                        {prevCh && <div style={{ fontSize: 11, color: C.text3 }} className="truncate">{prevCh.title}</div>}
                      </div>
                    </button>
                    <button disabled={!nextLesson} onClick={() => nextLesson && goLesson(nextLesson)}
                      style={{ flex: 1, height: 56, display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 10, padding: "0 16px", border: `1px solid ${nextLesson ? C.accent : C.border}`, borderRadius: 12, background: nextLesson ? C.accentBg : C.surface, cursor: nextLesson ? "pointer" : "default", opacity: nextLesson ? 1 : 0.45, transition: ".15s" }}
                      onMouseEnter={e => { if(nextLesson) e.currentTarget.style.background = "#dbeafe"; }}
                      onMouseLeave={e => { e.currentTarget.style.background = nextLesson ? C.accentBg : C.surface; }}>
                      <div style={{ textAlign: "right", minWidth: 0 }}>
                        <div style={{ fontSize: 11, color: C.accent, fontWeight: 500 }}>Bài tiếp theo</div>
                        {nextLesson && <div style={{ fontSize: 13, fontWeight: 600, color: C.text, lineHeight: 1.3 }} className="truncate">{nextLesson.title}</div>}
                        {nextCh && <div style={{ fontSize: 11, color: C.text2 }} className="truncate">{nextCh.title}</div>}
                      </div>
                      <Ic n="arrow_right" size={16} style={{ color: C.accent, flexShrink: 0 }} />
                    </button>
                  </div>
                </div>
              ) : (
                <div style={{ paddingTop: 80, textAlign: "center" }}>
                  <div style={{ width: 72, height: 72, borderRadius: 18, background: "#fff", border: `1px solid ${C.border}`, color: C.text3, display: "grid", placeItems: "center", margin: "0 auto 16px", boxShadow: "0 1px 4px rgba(0,0,0,.06)" }}>
                    <Ic n="book" size={32} />
                  </div>
                  <div style={{ color: C.text3, fontSize: 15 }}>Chọn một bài giảng từ danh sách bên phải để bắt đầu</div>
                </div>
              )}
            </div>
          </div>

          {/* ── Curriculum sidebar (right) ─────────────── */}
          <div style={{ width: sidebarOpen ? 340 : 40, flexShrink: 0, background: C.sidebar, borderLeft: `1px solid ${C.border}`, display: "flex", flexDirection: "column", transition: "width .25s ease", overflow: "hidden", position: "relative" }}>

            {/* Toggle button */}
            <button onClick={() => setSidebarOpen(o => !o)}
              style={{ position: "absolute", top: 14, left: sidebarOpen ? 12 : 4, zIndex: 10, width: 28, height: 28, borderRadius: 8, border: `1px solid ${C.border}`, background: "#fff", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", color: C.text2, boxShadow: "0 1px 3px rgba(0,0,0,.08)", transition: "left .25s ease", flexShrink: 0 }}
              title={sidebarOpen ? "Thu gọn" : "Mở rộng"}>
              <Ic n={sidebarOpen ? "chevron-right" : "chevron-left"} size={14} />
            </button>

            {/* Sidebar content — ẩn khi thu gọn */}
            <div style={{ opacity: sidebarOpen ? 1 : 0, transition: "opacity .15s", pointerEvents: sidebarOpen ? "auto" : "none", display: "flex", flexDirection: "column", flex: 1, minHeight: 0, paddingTop: 0 }}>

            {/* Course header */}
            <div style={{ padding: "16px 16px 14px", borderBottom: `1px solid ${C.border}`, flexShrink: 0, paddingTop: 52 }}>
              {course.thumbnailUrl && (
                <div style={{ borderRadius: 12, overflow: "hidden", marginBottom: 12, aspectRatio: "16/9", background: C.surface }}>
                  <img src={course.thumbnailUrl} alt="" style={{ width: "100%", height: "100%", objectFit: "cover", display: "block" }} />
                </div>
              )}
              <div style={{ fontSize: 14, fontWeight: 700, color: C.text, lineHeight: 1.4, marginBottom: 8 }}>{course.title}</div>
              <div style={{ display: "flex", gap: 14, fontSize: 12, color: C.text3 }}>
                <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Ic n="layers" size={12} />{chapters.length} chương</span>
                <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Ic n="book" size={12} />{totalLessons} bài</span>
              </div>
              {/* Progress bar */}
              <div style={{ marginTop: 10 }}>
                <div style={{ height: 5, borderRadius: 999, background: C.surface, overflow: "hidden" }}>
                  <div style={{ width: activeIdx >= 0 ? `${((activeIdx + 1) / totalLessons) * 100}%` : "0%", height: "100%", borderRadius: 999, background: C.accent, transition: "width .3s" }} />
                </div>
                <div style={{ fontSize: 11, color: C.text3, marginTop: 4 }}>
                  Bài {activeIdx >= 0 ? activeIdx + 1 : 0} / {totalLessons}
                </div>
              </div>
            </div>

            {/* Chapters accordion */}
            <div style={{ flex: 1, overflowY: "auto", padding: "8px 8px 24px" }}>
              {chapters.map((ch, ci) => {
                const isOpen = openCh[ch.id];
                return (
                  <div key={ch.id} style={{ marginBottom: 4 }}>
                    {/* Chapter header */}
                    <div onClick={() => setOpenCh(prev => ({ ...prev, [ch.id]: !prev[ch.id] }))}
                      style={{ display: "flex", alignItems: "center", gap: 10, padding: "10px 12px", borderRadius: 10, cursor: "pointer", background: isOpen ? C.accentBg : "transparent", transition: ".15s" }}
                      onMouseEnter={e => { if(!isOpen) e.currentTarget.style.background = C.surface; }}
                      onMouseLeave={e => { if(!isOpen) e.currentTarget.style.background = "transparent"; }}>
                      <div style={{ width: 22, height: 22, borderRadius: 6, background: isOpen ? C.accent : C.surface, color: isOpen ? "#fff" : C.text3, display: "grid", placeItems: "center", flexShrink: 0, fontSize: 11, fontWeight: 700 }}>
                        {ci + 1}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 13, fontWeight: 600, color: isOpen ? C.accent : C.text, lineHeight: 1.3 }} className="truncate">{ch.title}</div>
                        <div style={{ fontSize: 11, color: C.text3, marginTop: 1 }}>{ch.lessons?.length || 0} bài giảng</div>
                      </div>
                      <Ic n="chevron_down" size={14} style={{ color: isOpen ? C.accent : C.text3, flexShrink: 0, transform: isOpen ? "rotate(180deg)" : "none", transition: ".2s" }} />
                    </div>

                    {/* Lessons */}
                    {isOpen && (ch.lessons || []).map((l, li) => {
                      const isAct = active?.id === l.id;
                      const lIsVideo = l.lessonType === "VIDEO";
                      return (
                        <div key={l.id} onClick={() => goLesson(l)}
                          style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 12px 9px 20px", margin: "1px 0 1px 6px", borderRadius: 9, cursor: "pointer", background: isAct ? C.accentBg : "transparent", borderLeft: `2px solid ${isAct ? C.accent : "transparent"}`, transition: ".12s" }}
                          onMouseEnter={e => { if(!isAct) e.currentTarget.style.background = C.surface; }}
                          onMouseLeave={e => { if(!isAct) e.currentTarget.style.background = "transparent"; }}>
                          {/* Icon */}
                          <div style={{ width: 28, height: 28, borderRadius: 7, flexShrink: 0, display: "grid", placeItems: "center", background: isAct ? "#dbeafe" : C.surface, color: isAct ? C.accent : C.text3 }}>
                            <Ic n={lIsVideo ? "video" : "file-text"} size={13} />
                          </div>
                          {/* Info */}
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontSize: 13, fontWeight: isAct ? 600 : 400, color: isAct ? C.text : C.text2, lineHeight: 1.35 }} className="truncate">{l.title}</div>
                            <div style={{ fontSize: 11, color: C.text3, marginTop: 1, display: "flex", alignItems: "center", gap: 4 }}>
                              <span>{lIsVideo ? "Video" : "Văn bản"}</span>
                              {l.durationSeconds ? <><span>·</span><span>{fmtDur(l.durationSeconds)}</span></> : null}
                              {l.resources?.length > 0 ? <><span>·</span><Ic n="paperclip" size={10} /><span>{l.resources.length}</span></> : null}
                            </div>
                          </div>
                          {/* Status dot */}
                          <div style={{ width: 7, height: 7, borderRadius: 999, flexShrink: 0, background: isAct ? C.accent : C.border }} />
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
            </div>{/* end sidebar content */}
          </div>
        </div>

      </div>
      </Overlay>
    );
  }

  Object.assign(window, { PreviewPlayer });
})();
