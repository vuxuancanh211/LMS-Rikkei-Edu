// @ts-nocheck
(function () {
  const { useState, useEffect, useRef, useMemo } = React;
  const Ic  = window.Icon;
  const api = window.httpClient;

  /* ── Resource meta ─────────────────────────────────────── */
  const RS = {
    PDF:       { bg: "#fdecec", color: "#dc2626", icon: "file"      },
    DOC:       { bg: "#eaf1ff", color: "#2563eb", icon: "file-text" },
    SLIDE:     { bg: "#fef5e6", color: "#d97706", icon: "layers"    },
    IMAGE:     { bg: "#f0fdf4", color: "#16a34a", icon: "image"     },
    VIDEO:     { bg: "#f3edff", color: "#7c3aed", icon: "video"     },
    VIDEO_HLS: { bg: "#e0f2fe", color: "#0284c7", icon: "video"     },
    OTHER:     { bg: "#f1f5f9", color: "#475569", icon: "file"      },
  };

  function getYoutubeId(url) {
    try {
      const u = new URL(url);
      if (u.hostname === "youtu.be") return u.pathname.slice(1).split("?")[0];
      if (u.hostname.includes("youtube.com")) return u.searchParams.get("v");
    } catch (e) { console.error("Invalid URL", e); }
    return null;
  }
  function fmtDur(s) {
    if (!s) return null;
    return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
  }

  /* ── Single viewer panel ────────────────────────────────── */
  function Viewer({ res, url, loading, error, label, onClose }) {
    const isA = label === "A";
    const accentColor = isA ? "#2563eb" : "#7c3aed";
    const accentBg    = isA ? "#eff6ff"  : "#f5f3ff";
    const rs = res ? (RS[res.resourceType] || RS.OTHER) : null;

    useEffect(() => {
      const handleKeyDown = (e) => {
        if ((e.ctrlKey || e.metaKey) && (e.key === 's' || e.key === 'p' || e.key === 'S' || e.key === 'P' || e.key === 'u' || e.key === 'U')) {
          e.preventDefault();
        }
      };
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }, []);

    return (
      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", minHeight: 0 }}>
        {/* Header */}
        <div style={{ height: 36, flexShrink: 0, display: "flex", alignItems: "center", gap: 7,
          padding: "0 10px", background: "#fafafa", borderBottom: "1px solid #e2e8f0" }}>
          <span style={{ width: 18, height: 18, borderRadius: 4, background: accentBg, color: accentColor,
            display: "grid", placeItems: "center", fontSize: 9, fontWeight: 800, flexShrink: 0 }}>
            {label}
          </span>
          {rs && (
            <div style={{ width: 18, height: 18, borderRadius: 4, background: rs.bg, color: rs.color,
              display: "grid", placeItems: "center", flexShrink: 0 }}>
              <Ic n={rs.icon} size={9} />
            </div>
          )}
          <span style={{ flex: 1, minWidth: 0, fontSize: 12, fontWeight: res ? 600 : 400,
            color: res ? "#0f172a" : "#94a3b8", fontStyle: res ? "normal" : "italic" }} className="truncate">
            {res ? (res.displayName || res.originalFilename || res._label) : "Chưa có nội dung — chọn tài liệu bên trên"}
          </span>
          {onClose && (
            <button onClick={onClose}
              style={{ flexShrink: 0, width: 22, height: 22, borderRadius: 5, border: "1px solid #e2e8f0",
                background: "transparent", color: "#94a3b8", display: "grid",
                placeItems: "center", cursor: "pointer" }} title="Đóng split view">
              <Ic n="x" size={11} />
            </button>
          )}
        </div>

        {/* Content area */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", minHeight: 0,
          background: "#0f172a", position: "relative" }}
          onContextMenu={(e) => e.preventDefault()}>
          {!res && (
            <div style={{ flex: 1, display: "grid", placeItems: "center",
              color: "#475569", fontSize: 13, background: "#f8fafc" }}>
              ← Chọn bài/tài liệu ở cột trái để phát
            </div>
          )}
          {res && loading && (
            <div style={{ flex: 1, display: "grid", placeItems: "center",
              color: "#64748b", fontSize: 13, background: "#f8fafc" }}>
              <span>Đang tải... {res.displayName}</span>
            </div>
          )}
          {res && error && (
            <div style={{ flex: 1, display: "grid", placeItems: "center",
              color: "#dc2626", fontSize: 13, background: "#f8fafc" }}>
              Không thể tải tài liệu
            </div>
          )}
          {res && !loading && !error && (() => {
            const t = res.resourceType;
            // HLS video
            if (t === "VIDEO_HLS") return (
              <video controls controlsList="nodownload" src={res._hlsUrl}
                onContextMenu={(e) => e.preventDefault()}
                style={{ flex: 1, width: "100%", height: "100%", display: "block", minHeight: 0 }} />
            );
            if (!url) return null;
            // Image
            if (t === "IMAGE") return (
              <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center",
                background: "#1e293b", padding: 16, minHeight: 0, overflow: "auto" }}
                onContextMenu={(e) => e.preventDefault()}>
                <img src={url} alt={res.displayName}
                  style={{ maxWidth: "100%", maxHeight: "100%", objectFit: "contain",
                    borderRadius: 6, display: "block", pointerEvents: "none" }} />
              </div>
            );
            // External video
            if (t === "VIDEO") {
              const ytId = getYoutubeId(url);
              if (ytId) return (
                <iframe src={`https://www.youtube-nocookie.com/embed/${ytId}?rel=0`}
                  style={{ flex: 1, width: "100%", height: "100%", border: "none", display: "block", minHeight: 0 }}
                  allowFullScreen />
              );
              return (
                <video controls controlsList="nodownload" src={url}
                  onContextMenu={(e) => e.preventDefault()}
                  style={{ flex: 1, width: "100%", height: "100%", display: "block", minHeight: 0 }} />
              );
            }
            // PDF direct
            if (t === "PDF") return (
              <div style={{ flex: 1, position: "relative", width: "100%", height: "100%", display: "flex" }}>
                <iframe src={`${url}#toolbar=0&navpanes=0&scrollbar=0`} title={res.displayName}
                  style={{ flex: 1, width: "100%", height: "100%", border: "none", display: "block", minHeight: 0 }} />
                <div style={{ position: "absolute", top: 0, right: 0, width: 200, height: 55, zIndex: 10, background: "transparent" }}
                  onClick={(e) => { e.preventDefault(); e.stopPropagation(); }} />
              </div>
            );
            // DOC / SLIDE
            if (t === "DOC" || t === "SLIDE") {
              const isLocal = url.includes("localhost") || url.includes("127.0.0.1") || url.startsWith("/");
              if (isLocal) {
                return (
                  <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", background: "#f8fafc", padding: 20, textAlign: "center", gap: 12 }}>
                    <div style={{ width: 48, height: 48, borderRadius: 12, background: "#f1f5f9", color: "#64748b", display: "grid", placeItems: "center" }}>
                      <Ic n="book" size={24} />
                    </div>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "#0f172a" }}>{res.displayName || res.originalFilename || "Tài liệu " + t}</div>
                    <div style={{ fontSize: 12.5, color: "#64748b", maxWidth: 400, lineHeight: 1.5 }}>Trình xem trực tuyến không hỗ trợ kết nối tới đường dẫn nội bộ (Localhost). Tài liệu sẽ hiển thị trực tiếp trong khung khi hệ thống chạy trên máy chủ chính thức.</div>
                  </div>
                );
              }
              return (
                <div style={{ flex: 1, position: "relative", width: "100%", height: "100%", display: "flex" }}>
                  <iframe src={`https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(url)}&wdDownloadButton=False&wdPrint=0`}
                    title={res.displayName}
                    style={{ flex: 1, width: "100%", height: "100%", border: "none", display: "block", minHeight: 0 }} />
                  <div style={{ position: "absolute", top: 0, right: 0, width: 170, height: 50, zIndex: 10, background: "transparent" }}
                    onClick={(e) => { e.preventDefault(); e.stopPropagation(); }} />
                  <div style={{ position: "absolute", bottom: 0, right: 0, width: 220, height: 48, zIndex: 10, background: "transparent" }}
                    onClick={(e) => { e.preventDefault(); e.stopPropagation(); }} />
                </div>
              );
            }
            return (
              <div style={{ flex: 1, display: "grid", placeItems: "center",
                background: "#f8fafc", color: "#64748b", fontSize: 13 }}>
                <span>Định dạng tài liệu không hỗ trợ xem trực tiếp trên trình duyệt</span>
              </div>
            );
          })()}
        </div>
      </div>
    );
  }

  /* ── Drag divider ───────────────────────────────────────── */
  function DragDivider({ onDrag }) {
    const ref = useRef();
    function onMouseDown(e) {
      e.preventDefault();
      const move = ev => onDrag(ev.clientX);
      const up   = () => { window.removeEventListener("mousemove", move); window.removeEventListener("mouseup", up); };
      window.addEventListener("mousemove", move);
      window.addEventListener("mouseup", up);
    }
    return (
      <div ref={ref} onMouseDown={onMouseDown}
        style={{ width: 5, flexShrink: 0, background: "#e2e8f0", cursor: "col-resize",
          display: "flex", alignItems: "center", justifyContent: "center",
          transition: "background .15s", userSelect: "none" }}
        onMouseEnter={e => e.currentTarget.style.background = "#bfdbfe"}
        onMouseLeave={e => e.currentTarget.style.background = "#e2e8f0"}>
        <div style={{ width: 2, height: 24, borderRadius: 2, background: "#94a3b8" }} />
      </div>
    );
  }

  /* ── Resource chip ──────────────────────────────────────── */
  function ResChip({ res, activeA, activeB, onA, onB, splitActive }) {
    const [hov, setHov] = useState(false);
    const rs  = RS[res.resourceType] || RS.OTHER;
    const isA = activeA?.id === res.id;
    const isB = activeB?.id === res.id;
    const showBBtn = splitActive && (hov || isB);

    return (
      <div onMouseEnter={() => setHov(true)} onMouseLeave={() => setHov(false)}
        style={{ display: "inline-flex", alignItems: "center", borderRadius: 8, flexShrink: 0,
          border: `1.5px solid ${isA ? "#2563eb" : isB ? "#7c3aed" : "#e2e8f0"}`,
          background: isA ? "#eff6ff" : isB ? "#f5f3ff" : "#f8fafc",
          overflow: "hidden", transition: "border-color .12s, background .12s" }}>
        <button onClick={onA}
          style={{ display: "inline-flex", alignItems: "center", gap: 6, padding: "5px 10px",
            background: "transparent", border: "none", cursor: "pointer",
            color: isA ? "#2563eb" : isB ? "#7c3aed" : "#475569" }}>
          <div style={{ width: 18, height: 18, borderRadius: 4, background: rs.bg, color: rs.color,
            display: "grid", placeItems: "center", flexShrink: 0 }}>
            <Ic n={rs.icon} size={10} />
          </div>
          <span style={{ fontSize: 12.5, fontWeight: isA || isB ? 600 : 400, whiteSpace: "nowrap",
            maxWidth: 150, overflow: "hidden", textOverflow: "ellipsis" }}>
            {res.displayName || res.originalFilename || res._label}
          </span>
          {isA && <span style={{ fontSize: 9, fontWeight: 800, color: "#2563eb",
            background: "#dbeafe", borderRadius: 3, padding: "1px 4px" }}>A</span>}
          {isB && <span style={{ fontSize: 9, fontWeight: 800, color: "#7c3aed",
            background: "#ede9fe", borderRadius: 3, padding: "1px 4px" }}>B</span>}
        </button>
        {showBBtn && (
          <button onClick={e => { e.stopPropagation(); onB(); }}
            title="Mở ở viewer B"
            style={{ padding: "5px 8px 5px 2px", background: "transparent",
              border: "none", borderLeft: "1px solid #e2e8f0", cursor: "pointer",
              display: "flex", alignItems: "center" }}>
            <span style={{ fontSize: 9, fontWeight: 800, borderRadius: 3, padding: "1px 5px",
              background: isB ? "#ede9fe" : "#f1f5f9", color: isB ? "#7c3aed" : "#94a3b8" }}>B</span>
          </button>
        )}
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

    const [viewerA,     setViewerA]     = useState(null);
    const [viewerB,     setViewerB]     = useState(null);
    const [splitActive, setSplitActive] = useState(false);
    const [splitPct,    setSplitPct]    = useState(50);
    const [resUrls,     setResUrls]     = useState({});

    const [sidebarTab,    setSidebarTab]    = useState("lessons");
    const [assignments,   setAssignments]   = useState([]);
    const [assignLoading, setAssignLoading] = useState(false);
    const [activeView,        setActiveView]        = useState("lesson");
    const [selectedAssignmentId, setSelectedAssignmentId] = useState(null);

    const contentRef = useRef(null);

    const allLessons   = useMemo(() => chapters.flatMap(ch => ch.lessons || []), [chapters]);
    const activeIdx    = useMemo(() => allLessons.findIndex(l => l.id === active?.id), [allLessons, active]);
    const activeCh     = useMemo(() => chapters.find(c => c.lessons?.some(l => l.id === active?.id)), [chapters, active]);
    const totalLessons = useMemo(() => allLessons.length, [allLessons]);
    const prevLesson   = activeIdx > 0 ? allLessons[activeIdx - 1] : null;
    const nextLesson   = activeIdx < allLessons.length - 1 ? allLessons[activeIdx + 1] : null;

    const assignIdx      = useMemo(() => assignments.findIndex(a => a.id === selectedAssignmentId), [assignments, selectedAssignmentId]);
    const prevAssignment = assignIdx > 0 ? assignments[assignIdx - 1] : null;
    const nextAssignment = assignIdx < assignments.length - 1 ? assignments[assignIdx + 1] : null;

    // Build chips: virtual video chip + lesson resources
    const allChips = useMemo(() => {
      if (!active) return [];
      const list = [];
      if (active.hlsManifestUrl) {
        list.push({ id: "__video__", resourceType: "VIDEO_HLS",
          _label: "Video bài giảng", _hlsUrl: active.hlsManifestUrl });
      }
      (active.resources || []).forEach(r => list.push(r));
      return list;
    }, [active]);

    useEffect(() => {
      if (!courseId) { setLoading(false); return; }
      const ep = role === "admin" ? `/admin/courses/${courseId}` : `/instructor/courses/${courseId}`;
      api.get(ep)
        .then(r => {
          setCourse(r.data);
          const chs = r.data.chapters || [];
          setChapters(chs);
          const opened = {};
          chs.forEach(ch => { opened[ch.id] = true; });
          setOpenCh(opened);
          if (chs[0]?.lessons?.length > 0) setActive(chs[0].lessons[0]);
        })
        .catch(() => {})
        .finally(() => setLoading(false));
    }, [courseId, role]);

    // Auto-load first chip into A when lesson changes
    useEffect(() => {
      setViewerB(null);
      setSplitActive(false);
      setSplitPct(50);
      if (!active) { setViewerA(null); return; }
      const chips = [];
      if (active.hlsManifestUrl) chips.push({ id: "__video__", resourceType: "VIDEO_HLS", _label: "Video bài giảng", _hlsUrl: active.hlsManifestUrl });
      (active.resources || []).forEach(r => chips.push(r));
      const first = chips[0] || null;
      setViewerA(first);
      if (first && first.resourceType !== "VIDEO_HLS") fetchResUrl(first, active.id);
    }, [active?.id]);

    // Fetch assignments when switching to the "Bài tập" tab
    useEffect(() => {
      if (sidebarTab !== "assignments" || !courseId) return;
      setAssignLoading(true);
      const ep = role === "admin"
        ? `/admin/courses/${courseId}/assignments`
        : `/instructor/courses/${courseId}/assignments`;
      api.get(ep)
        .then(r => setAssignments(r.data || []))
        .catch(() => setAssignments([]))
        .finally(() => setAssignLoading(false));
    }, [sidebarTab, courseId, role]);

    function fetchResUrl(r, lessonId) {
      if (!r || r.resourceType === "VIDEO_HLS") return;
      if (resUrls[r.id]?.url || resUrls[r.id]?.loading) return;
      if (r.externalUrl) {
        setResUrls(m => ({ ...m, [r.id]: { url: r.externalUrl } }));
        return;
      }
      setResUrls(m => ({ ...m, [r.id]: { loading: true } }));
      const ep = role === "admin"
        ? `/admin/courses/resources/${r.id}/download-url`
        : `/instructor/courses/${courseId}/lessons/${lessonId}/resources/${r.id}/view-url`;
      api.get(ep)
        .then(res => setResUrls(m => ({ ...m, [r.id]: { url: res.data?.url } })))
        .catch(() => setResUrls(m => ({ ...m, [r.id]: { error: true } })));
    }

    function loadToA(r) {
      setViewerA(r);
      fetchResUrl(r, active?.id);
    }
    function loadToB(r) {
      setViewerB(r);
      fetchResUrl(r, active?.id);
      if (!splitActive) setSplitActive(true);
    }
    function closeSplit() {
      setViewerB(null);
      setSplitActive(false);
      setSplitPct(50);
    }

    function goLesson(l) {
      setActive(l);
      const ch = chapters.find(c => c.lessons?.some(x => x.id === l.id));
      if (ch) setOpenCh(p => ({ ...p, [ch.id]: true }));
    }

    function handleDividerDrag(clientX) {
      if (!contentRef.current) return;
      const rect = contentRef.current.getBoundingClientRect();
      const pct  = Math.min(80, Math.max(20, ((clientX - rect.left) / rect.width) * 100));
      setSplitPct(pct);
    }

    function getViewerState(res) {
      if (!res) return {};
      if (res.resourceType === "VIDEO_HLS") return { url: "__hls__" };
      return resUrls[res.id] || {};
    }

    const Overlay = ({ children }) => (
      <div style={{ position: "fixed", inset: 0, zIndex: 200, background: "#f1f5f9",
        display: "flex", flexDirection: "column" }}>
        {children}
      </div>
    );

    if (loading) return (
      <Overlay>
        <div style={{ flex: 1, display: "grid", placeItems: "center", color: "#94a3b8", fontSize: 14 }}>
          Đang tải khóa học...
        </div>
      </Overlay>
    );

    if (!courseId || !course) return (
      <Overlay>
        <div style={{ flex: 1, display: "grid", placeItems: "center" }}>
          <div style={{ textAlign: "center" }}>
            <div style={{ color: "#94a3b8", marginBottom: 14 }}>Không tìm thấy khóa học.</div>
            <button className="btn btn-ghost" onClick={onBack}><Ic n="arrow_left" size={15} />Quay lại</button>
          </div>
        </div>
      </Overlay>
    );

    const stA = getViewerState(viewerA);
    const stB = getViewerState(viewerB);

    return (
      <Overlay>

        {/* ── Topbar ──────────────────────────────────────── */}
        <div style={{ height: 50, flexShrink: 0, background: "#fff",
          borderBottom: "1px solid #e2e8f0", display: "flex", alignItems: "center",
          gap: 10, padding: "0 16px", zIndex: 10 }}>
          <button onClick={onBack}
            style={{ width: 30, height: 30, borderRadius: 8, border: "1px solid #e2e8f0",
              background: "transparent", color: "#475569", display: "grid",
              placeItems: "center", cursor: "pointer", flexShrink: 0 }}>
            <Ic n="arrow_left" size={15} />
          </button>
          <div style={{ display: "flex", alignItems: "center", gap: 6, flexShrink: 0 }}>
            <div style={{ width: 24, height: 24, borderRadius: 6,
              background: "linear-gradient(135deg,#1e40af,#3b82f6)", display: "grid", placeItems: "center" }}>
              <Ic n="book" size={12} style={{ color: "#fff" }} />
            </div>
            <span style={{ fontWeight: 700, fontSize: 13, color: "#0f172a" }}>Rikkei Edu</span>
          </div>
          <div style={{ width: 1, height: 16, background: "#e2e8f0" }} />
          <div style={{ flex: 1, minWidth: 0, display: "flex", alignItems: "center", gap: 5, fontSize: 12.5 }}>
            <span style={{ color: "#94a3b8" }} className="truncate">{course.title}</span>
            {activeCh && (
              <><Ic n="chevron_right" size={11} style={{ color: "#94a3b8", flexShrink: 0 }} />
                <span style={{ color: "#475569", flexShrink: 0 }} className="truncate">{activeCh.title}</span></>
            )}
            {active && (
              <><Ic n="chevron_right" size={11} style={{ color: "#94a3b8", flexShrink: 0 }} />
                <span style={{ color: "#0f172a", fontWeight: 600, flexShrink: 0 }} className="truncate">{active.title}</span></>
            )}
          </div>
          <span style={{ background: "#fef9c3", color: "#92400e", borderRadius: 20, padding: "2px 10px",
            fontSize: 11.5, fontWeight: 600, display: "flex", alignItems: "center",
            gap: 4, flexShrink: 0 }}>
            <Ic n="eye" size={11} />Xem trước
          </span>
        </div>

        {/* ── Body ────────────────────────────────────────── */}
        <div style={{ flex: 1, minHeight: 0, display: "flex", overflow: "hidden" }}>

          {/* ── Left: content area ──────────────────────── */}
          <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", minHeight: 0 }}>

            {/* Resource chips bar */}
            <div style={{ flexShrink: 0, background: "#fff", borderBottom: "1px solid #e2e8f0",
              padding: "7px 14px", display: "flex", alignItems: "center", gap: 8, overflowX: "auto",
              minHeight: 46 }}>
              {active && allChips.length > 0 ? (
                <>
                  <span style={{ fontSize: 11, color: "#94a3b8", fontWeight: 600,
                    textTransform: "uppercase", letterSpacing: ".04em", flexShrink: 0 }}>
                    Tài liệu
                  </span>
                  {allChips.map(r => (
                    <ResChip key={r.id} res={r}
                      activeA={viewerA} activeB={viewerB}
                      onA={() => loadToA(r)}
                      onB={() => loadToB(r)}
                      splitActive={splitActive} />
                  ))}
                </>
              ) : active ? (
                <span style={{ fontSize: 12, color: "#cbd5e1", fontStyle: "italic" }}>
                  Bài này chưa có tài liệu
                </span>
              ) : null}
              <div style={{ flex: 1 }} />
              {/* Split toggle */}
              {active && allChips.length > 1 && (
                <button
                  onClick={() => splitActive ? closeSplit() : setSplitActive(true)}
                  style={{ flexShrink: 0, display: "flex", alignItems: "center", gap: 5,
                    padding: "5px 11px", borderRadius: 8, fontSize: 12, fontWeight: 600,
                    cursor: "pointer", transition: ".12s",
                    border: `1.5px solid ${splitActive ? "#7c3aed" : "#e2e8f0"}`,
                    background: splitActive ? "#f5f3ff" : "#f8fafc",
                    color: splitActive ? "#7c3aed" : "#475569" }}>
                  <Ic n="layout" size={13} />
                  {splitActive ? "Thoát split" : "Split view"}
                </button>
              )}
            </div>

            {/* Viewers / AssignmentDetail */}
            <div ref={contentRef}
              style={{ flex: 1, minHeight: 0, display: "flex", overflow: "hidden" }}>
              {activeView === "assignment" && selectedAssignmentId ? (
                window.AssignmentDetail && React.createElement(window.AssignmentDetail, {
                  assignmentId: selectedAssignmentId,
                  courseId,
                  role,
                  onBack: () => {
                    setActiveView("lesson");
                    setSelectedAssignmentId(null);
                    setSidebarTab("lessons");
                  },
                })
              ) : active ? (
                <>
                  {/* Viewer A */}
                  <div style={{ width: splitActive ? `${splitPct}%` : "100%",
                    minWidth: 0, display: "flex", flexDirection: "column", minHeight: 0 }}>
                    <Viewer res={viewerA} url={stA.url} loading={stA.loading} error={stA.error} label="A" />
                  </div>

                  {splitActive && <DragDivider onDrag={handleDividerDrag} />}

                  {splitActive && (
                    <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", minHeight: 0 }}>
                      <Viewer res={viewerB} url={stB.url} loading={stB.loading} error={stB.error}
                        label="B" onClose={closeSplit} />
                    </div>
                  )}
                </>
              ) : (
                <div style={{ flex: 1, display: "grid", placeItems: "center", color: "#94a3b8" }}>
                  <div style={{ textAlign: "center", display: "flex", flexDirection: "column",
                    alignItems: "center", gap: 12 }}>
                    <div style={{ width: 56, height: 56, borderRadius: 14, background: "#fff",
                      border: "1px solid #e2e8f0", display: "grid", placeItems: "center" }}>
                      <Ic n="book" size={26} style={{ color: "#cbd5e1" }} />
                    </div>
                    <span style={{ fontSize: 14 }}>Chọn một bài giảng từ danh sách</span>
                  </div>
                </div>
              )}
            </div>

            {/* Prev / Next (lessons or assignments) */}
            {(active && activeView !== "assignment") || (activeView === "assignment" && selectedAssignmentId) ? (
              <div style={{ flexShrink: 0, background: "#fff", borderTop: "1px solid #e2e8f0",
                padding: "8px 14px", display: "flex", gap: 10 }}>
                {activeView === "assignment" ? (
                  <>
                    <button disabled={!prevAssignment}
                      onClick={() => prevAssignment && setSelectedAssignmentId(prevAssignment.id)}
                      style={{ flex: 1, height: 46, display: "flex", alignItems: "center", gap: 8,
                        padding: "0 14px", border: "1px solid #e2e8f0", borderRadius: 10,
                        background: prevAssignment ? "#fff" : "#f8fafc",
                        cursor: prevAssignment ? "pointer" : "default", opacity: prevAssignment ? 1 : 0.4 }}
                      onMouseEnter={e => { if (prevAssignment) e.currentTarget.style.borderColor = "#2563eb"; }}
                      onMouseLeave={e => { e.currentTarget.style.borderColor = "#e2e8f0"; }}>
                      <Ic n="arrow_left" size={14} style={{ color: "#94a3b8", flexShrink: 0 }} />
                      <div style={{ textAlign: "left", minWidth: 0 }}>
                        <div style={{ fontSize: 10, color: "#94a3b8", fontWeight: 500 }}>Bài tập trước</div>
                        {prevAssignment && <div style={{ fontSize: 12.5, fontWeight: 600, color: "#0f172a" }}
                          className="truncate">{prevAssignment.title}</div>}
                      </div>
                    </button>
                    <button disabled={!nextAssignment}
                      onClick={() => nextAssignment && setSelectedAssignmentId(nextAssignment.id)}
                      style={{ flex: 1, height: 46, display: "flex", alignItems: "center",
                        justifyContent: "flex-end", gap: 8, padding: "0 14px",
                        border: `1px solid ${nextAssignment ? "#2563eb" : "#e2e8f0"}`,
                        borderRadius: 10, background: nextAssignment ? "#eff6ff" : "#f8fafc",
                        cursor: nextAssignment ? "pointer" : "default", opacity: nextAssignment ? 1 : 0.4 }}
                      onMouseEnter={e => { if (nextAssignment) e.currentTarget.style.background = "#dbeafe"; }}
                      onMouseLeave={e => { e.currentTarget.style.background = nextAssignment ? "#eff6ff" : "#f8fafc"; }}>
                      <div style={{ textAlign: "right", minWidth: 0 }}>
                        <div style={{ fontSize: 10, color: "#2563eb", fontWeight: 500 }}>Bài tập tiếp theo</div>
                        {nextAssignment && <div style={{ fontSize: 12.5, fontWeight: 600, color: "#0f172a" }}
                          className="truncate">{nextAssignment.title}</div>}
                      </div>
                      <Ic n="arrow_right" size={14} style={{ color: "#2563eb", flexShrink: 0 }} />
                    </button>
                  </>
                ) : (
                  <>
                    <button disabled={!prevLesson} onClick={() => prevLesson && goLesson(prevLesson)}
                      style={{ flex: 1, height: 46, display: "flex", alignItems: "center", gap: 8,
                        padding: "0 14px", border: "1px solid #e2e8f0", borderRadius: 10,
                        background: prevLesson ? "#fff" : "#f8fafc",
                        cursor: prevLesson ? "pointer" : "default", opacity: prevLesson ? 1 : 0.4 }}
                      onMouseEnter={e => { if (prevLesson) e.currentTarget.style.borderColor = "#2563eb"; }}
                      onMouseLeave={e => { e.currentTarget.style.borderColor = "#e2e8f0"; }}>
                      <Ic n="arrow_left" size={14} style={{ color: "#94a3b8", flexShrink: 0 }} />
                      <div style={{ textAlign: "left", minWidth: 0 }}>
                        <div style={{ fontSize: 10, color: "#94a3b8", fontWeight: 500 }}>Bài trước</div>
                        {prevLesson && <div style={{ fontSize: 12.5, fontWeight: 600, color: "#0f172a" }}
                          className="truncate">{prevLesson.title}</div>}
                      </div>
                    </button>
                    <button disabled={!nextLesson} onClick={() => nextLesson && goLesson(nextLesson)}
                      style={{ flex: 1, height: 46, display: "flex", alignItems: "center",
                        justifyContent: "flex-end", gap: 8, padding: "0 14px",
                        border: `1px solid ${nextLesson ? "#2563eb" : "#e2e8f0"}`,
                        borderRadius: 10, background: nextLesson ? "#eff6ff" : "#f8fafc",
                        cursor: nextLesson ? "pointer" : "default", opacity: nextLesson ? 1 : 0.4 }}
                      onMouseEnter={e => { if (nextLesson) e.currentTarget.style.background = "#dbeafe"; }}
                      onMouseLeave={e => { e.currentTarget.style.background = nextLesson ? "#eff6ff" : "#f8fafc"; }}>
                      <div style={{ textAlign: "right", minWidth: 0 }}>
                        <div style={{ fontSize: 10, color: "#2563eb", fontWeight: 500 }}>Bài tiếp theo</div>
                        {nextLesson && <div style={{ fontSize: 12.5, fontWeight: 600, color: "#0f172a" }}
                          className="truncate">{nextLesson.title}</div>}
                      </div>
                      <Ic n="arrow_right" size={14} style={{ color: "#2563eb", flexShrink: 0 }} />
                    </button>
                  </>
                )}
              </div>
            ) : null}
          </div>

          {/* ── Right: Curriculum sidebar ────────────────── */}
          <div style={{ width: 300, flexShrink: 0, background: "#fff",
            borderLeft: "1px solid #e2e8f0", display: "flex",
            flexDirection: "column", minHeight: 0 }}>

            {/* Thumbnail */}
            {course.thumbnailUrl ? (
              <div style={{ aspectRatio: "16/9", flexShrink: 0, overflow: "hidden", background: "#0f172a" }}>
                <img src={course.thumbnailUrl} alt=""
                  style={{ width: "100%", height: "100%", objectFit: "cover", display: "block" }} />
              </div>
            ) : (
              <div style={{ aspectRatio: "16/9", flexShrink: 0,
                background: "linear-gradient(135deg,#1e3a8a,#3b82f6)",
                display: "grid", placeItems: "center" }}>
                <Ic n="book" size={30} style={{ color: "rgba(255,255,255,.25)" }} />
              </div>
            )}

            {/* Course info + progress */}
            <div style={{ padding: "12px 14px 10px", borderBottom: "1px solid #e2e8f0", flexShrink: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#0f172a",
                lineHeight: 1.4, marginBottom: 5 }}>{course.title}</div>
              <div style={{ fontSize: 11.5, color: "#94a3b8", display: "flex", gap: 12, marginBottom: 8 }}>
                <span style={{ display: "flex", alignItems: "center", gap: 4 }}>
                  <Ic n="layers" size={11} />{chapters.length} chương
                </span>
                <span style={{ display: "flex", alignItems: "center", gap: 4 }}>
                  <Ic n="book" size={11} />{totalLessons} bài
                </span>
              </div>
              <div style={{ height: 4, borderRadius: 999, background: "#f1f5f9", overflow: "hidden" }}>
                <div style={{
                  width: activeIdx >= 0 ? `${((activeIdx + 1) / totalLessons) * 100}%` : "0%",
                  height: "100%", background: "#2563eb", borderRadius: 999, transition: "width .3s"
                }} />
              </div>
              <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 3 }}>
                Bài {activeIdx >= 0 ? activeIdx + 1 : 0} / {totalLessons}
              </div>
            </div>

            {/* Sidebar tab bar */}
            <div style={{ flexShrink: 0, display: "flex", borderBottom: "1px solid #e2e8f0" }}>
              {["lessons", "assignments"].map(tab => {
                const isAct = sidebarTab === tab;
                return (
                  <button key={tab} onClick={() => {
                    setSidebarTab(tab);
                    if (tab === "lessons") {
                      setActiveView("lesson");
                      setSelectedAssignmentId(null);
                    }
                  }}
                    style={{ flex: 1, height: 36, border: "none", background: "transparent",
                      cursor: "pointer", fontSize: 12.5, fontWeight: 600,
                      color: isAct ? "#2563eb" : "#94a3b8",
                      borderBottom: `2px solid ${isAct ? "#2563eb" : "transparent"}`,
                      transition: ".13s", display: "flex", alignItems: "center",
                      justifyContent: "center", gap: 5 }}>
                    <Ic n={tab === "lessons" ? "book" : "clipboard"} size={13} />
                    {tab === "lessons" ? "Bài học" : "Bài tập"}
                  </button>
                );
              })}
            </div>

            {/* Tab content */}
            <div style={{ flex: 1, display: "flex", flexDirection: "column",
              minHeight: 0, overflow: "hidden", padding: "6px 6px 20px" }}>

              {sidebarTab === "lessons" ? (
                <div style={{ flex: 1, overflowY: "auto", minHeight: 0 }}>
                {chapters.length === 0 ? (
                  <div style={{ textAlign: "center", color: "#94a3b8", fontSize: 12, padding: "40px 16px" }}>
                    Chưa có chương nào trong khóa học
                  </div>
                ) : (
                  chapters.map((ch, ci) => {
                    const isOpen = openCh[ch.id];
                    return (
                      <div key={ch.id} style={{ marginBottom: 2 }}>
                        <div onClick={() => setOpenCh(p => ({ ...p, [ch.id]: !p[ch.id] }))}
                          style={{ display: "flex", alignItems: "center", gap: 8, padding: "8px 10px",
                            borderRadius: 9, cursor: "pointer",
                            background: isOpen ? "#eff6ff" : "transparent", transition: ".13s" }}
                          onMouseEnter={e => { if (!isOpen) e.currentTarget.style.background = "#f8fafc"; }}
                          onMouseLeave={e => { if (!isOpen) e.currentTarget.style.background = "transparent"; }}>
                          <div style={{ width: 20, height: 20, borderRadius: 5, flexShrink: 0,
                            background: isOpen ? "#2563eb" : "#f1f5f9",
                            color: isOpen ? "#fff" : "#94a3b8",
                            display: "grid", placeItems: "center", fontSize: 10, fontWeight: 700 }}>
                            {ci + 1}
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontSize: 12.5, fontWeight: 600,
                              color: isOpen ? "#2563eb" : "#0f172a" }} className="truncate">{ch.title}</div>
                            <div style={{ fontSize: 10.5, color: "#94a3b8" }}>{ch.lessons?.length || 0} bài</div>
                          </div>
                          <Ic n="chevron_down" size={12} style={{
                            color: isOpen ? "#2563eb" : "#94a3b8", flexShrink: 0,
                            transform: isOpen ? "rotate(180deg)" : "none", transition: ".2s"
                          }} />
                        </div>

                        {isOpen && (ch.lessons || []).map(l => {
                          const isAct = active?.id === l.id;
                          return (
                            <div key={l.id} onClick={() => goLesson(l)}
                              style={{ display: "flex", alignItems: "center", gap: 8,
                                padding: "7px 10px 7px 18px", margin: "1px 0 1px 4px",
                                borderRadius: 8, cursor: "pointer",
                                background: isAct ? "#eff6ff" : "transparent",
                                borderLeft: `2px solid ${isAct ? "#2563eb" : "transparent"}`,
                                transition: ".12s" }}
                              onMouseEnter={e => { if (!isAct) e.currentTarget.style.background = "#f8fafc"; }}
                              onMouseLeave={e => { if (!isAct) e.currentTarget.style.background = "transparent"; }}>
                              <div style={{ width: 26, height: 26, borderRadius: 7, flexShrink: 0,
                                background: isAct ? "#dbeafe" : "#f1f5f9",
                                color: isAct ? "#2563eb" : "#94a3b8",
                                display: "grid", placeItems: "center" }}>
                                <Ic n={l.lessonType === "VIDEO" ? "video" : "file"} size={12} />
                              </div>
                              <div style={{ flex: 1, minWidth: 0 }}>
                                <div style={{ fontSize: 12.5, fontWeight: isAct ? 600 : 400,
                                  color: isAct ? "#0f172a" : "#475569", lineHeight: 1.35 }}
                                  className="truncate">{l.title}</div>
                                <div style={{ fontSize: 10.5, color: "#94a3b8",
                                  display: "flex", alignItems: "center", gap: 3, marginTop: 1 }}>
                                  <span>{l.lessonType === "VIDEO" ? "Video" : "Tài liệu"}</span>
                                  {l.durationSeconds && <><span>·</span><span>{fmtDur(l.durationSeconds)}</span></>}
                                  {l.resources?.length > 0 && (
                                    <><span>·</span><Ic n="paperclip" size={9} /><span>{l.resources.length}</span></>
                                  )}
                                </div>
                              </div>
                              <div style={{ width: 6, height: 6, borderRadius: 999, flexShrink: 0,
                                background: isAct ? "#2563eb" : "#e2e8f0" }} />
                            </div>
                          );
                        })}
                      </div>
                    );
                  })
                )}
                </div>

              ) : (
                <>
                <div style={{ flexShrink: 0, display: "flex", alignItems: "center",
                  gap: 6, padding: "8px 10px 4px" }}>
                  <span style={{ fontSize: 12, fontWeight: 600, color: "#0f172a", flex: 1 }}>
                    Bài tập
                    {assignments.length > 0 && (
                      <span style={{ fontWeight: 400, color: "#94a3b8", marginLeft: 4 }}>
                        ({assignments.length})
                      </span>
                    )}
                  </span>
                  {window.AssignmentDropdown && (
                    React.createElement(window.AssignmentDropdown, {
                      courseId,
                      role,
                    })
                  )}
                </div>

                <div style={{ flex: 1, minHeight: 0, overflowY: "auto" }}>
                  {assignLoading ? (
                    <div style={{ textAlign: "center", color: "#94a3b8", fontSize: 12, padding: "40px 16px" }}>
                      Đang tải bài tập...
                    </div>
                  ) : assignments.length === 0 ? (
                    <div style={{ textAlign: "center", color: "#94a3b8", fontSize: 12, padding: "40px 16px" }}>
                      Chưa có bài tập nào trong khóa học
                    </div>
                  ) : (
                    assignments.map(a => {
                      const statusColors = {
                        DRAFT:     { bg: "#f1f5f9", color: "#64748b", label: "Bản nháp" },
                        PUBLISHED: { bg: "#dcfce7", color: "#16a34a", label: "Đã xuất bản" },
                        CLOSED:    { bg: "#fef2f2", color: "#dc2626", label: "Đã đóng" },
                      };
                      const sc = statusColors[a.status] || statusColors.DRAFT;
                      const deadline = a.deadline
                        ? new Date(a.deadline).toLocaleDateString("vi-VN", {
                            day: "2-digit", month: "2-digit", year: "numeric",
                            hour: "2-digit", minute: "2-digit",
                          })
                        : null;
                      return (
                        <div key={a.id} onClick={() => {
                          setSelectedAssignmentId(a.id);
                          setActiveView("assignment");
                        }}
                          style={{ display: "flex", alignItems: "flex-start", gap: 8,
                            padding: "8px 10px", margin: "2px 0", borderRadius: 8,
                            cursor: "pointer", transition: ".12s",
                            background: "transparent" }}
                          onMouseEnter={e => { e.currentTarget.style.background = "#f8fafc"; }}
                          onMouseLeave={e => { e.currentTarget.style.background = "transparent"; }}>
                          <div style={{ width: 26, height: 26, borderRadius: 7, flexShrink: 0,
                            background: "#fef3c7", color: "#d97706",
                            display: "grid", placeItems: "center" }}>
                            <Ic n="clipboard" size={12} />
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontSize: 12.5, fontWeight: 600, color: "#0f172a",
                              lineHeight: 1.35, marginBottom: 3 }} className="truncate">{a.title}</div>
                            <div style={{ display: "flex", flexWrap: "wrap", gap: 4, marginBottom: 3 }}>
                              <span style={{ fontSize: 10, fontWeight: 600, borderRadius: 4,
                                padding: "1px 6px", background: sc.bg, color: sc.color }}>
                                {sc.label}
                              </span>
                              {a.maxScore != null && (
                                <span style={{ fontSize: 10, fontWeight: 600, borderRadius: 4,
                                  padding: "1px 6px", background: "#f0fdf4", color: "#16a34a" }}>
                                  {a.maxScore} điểm
                                </span>
                              )}
                              {a.scope === "SPECIFIC_GROUPS" && (
                                <span style={{ fontSize: 10, fontWeight: 600, borderRadius: 4,
                                  padding: "1px 6px", background: "#eff6ff", color: "#2563eb" }}>
                                  Theo nhóm
                                </span>
                              )}
                            </div>
                            {deadline && (
                              <div style={{ fontSize: 10.5, color: "#94a3b8",
                                display: "flex", alignItems: "center", gap: 3 }}>
                                <Ic n="clock" size={9} />
                                <span>Hạn: {deadline}</span>
                              </div>
                            )}
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </>
              )}
            </div>
          </div>
        </div>
      </Overlay>
    );
  }

  Object.assign(window, { PreviewPlayer });
})();
