// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Trình xem trước nội dung khóa học (Giảng viên / Admin)
   Đồng bộ giao diện + trình xem tài liệu gốc (docx-preview, react-pdf)
   với LecturePlayer.tsx (học viên) — nhưng KHÔNG ghi nhận tiến trình
   học tập (không gọi progress API, không có trạng thái "đã hoàn thành",
   không có chatbot AI/thông báo vốn chỉ dành cho tài khoản học viên).
   ============================================================ */
import { createPortal } from 'react-dom';
(function () {
  const { useState, useEffect, useRef, useMemo } = React;
  const Ic  = window.Icon, api = window.httpClient;
  const { getQuizDetail } = window.__quizService;

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

  function fmtBytes(bytes) {
    if (!bytes) return "";
    const units = ["B","KB","MB","GB"];
    let i = 0;
    let v = bytes;
    while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
    return v.toFixed(i > 0 ? 1 : 0) + " " + units[i];
  }

  /* ─── Quiz preview card (chỉ hiển thị thông tin đề, không cho làm bài) ─── */
  function QuizPreviewDetail({ lesson, courseId }) {
    const [detail, setDetail] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
      if (!lesson?.quizId) return;
      setLoading(true);
      setError("");
      getQuizDetail(courseId, lesson.quizId)
        .then(setDetail)
        .catch(err => setError(err?.response?.data?.message || "Không thể tải thông tin đề trắc nghiệm."))
        .finally(() => setLoading(false));
    }, [lesson?.quizId, courseId]);

    if (!lesson) return null;

    return (
      <div className="card" style={{ maxWidth: 800, margin: "0 auto", overflow: "hidden" }}>
        <div style={{ padding: "20px 24px", borderBottom: "1px solid var(--border)", display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 44, height: 44, borderRadius: 12, background: "#eaf1ff", color: "#2563eb", display: "grid", placeItems: "center", flexShrink: 0 }}>
            <Ic n="clipboard" size={22} />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0f172a" }}>Thông tin bài trắc nghiệm</h3>
            <div className="t-sm muted truncate" style={{ marginTop: 2 }}>{lesson.title}</div>
          </div>
          {detail && detail.status !== "PUBLISHED" && (
            <span className="chip chip-neutral" style={{ fontSize: 11, flexShrink: 0 }}>Chưa xuất bản</span>
          )}
        </div>
        <div style={{ padding: "24px 24px 32px" }}>
          {loading ? (
            <div style={{ padding: 40, textAlign: "center", color: "var(--text-2)" }}>Đang tải thông tin...</div>
          ) : error ? (
            <div style={{ padding: 40, textAlign: "center", color: "var(--error)" }}>{error}</div>
          ) : (
            <>
              <div className="grid" style={{ gridTemplateColumns: "repeat(3, 1fr)", gap: 12 }}>
                <div style={{ padding: "16px 14px", textAlign: "center", background: "#f8fafc", borderRadius: 12, border: "1px solid var(--border)" }}>
                  <div className="t-xs muted" style={{ fontWeight: 600 }}>SỐ CÂU HỎI</div>
                  <div style={{ fontSize: 22, fontWeight: 800, marginTop: 6, color: "#0f172a" }}>{detail?.questionCount ?? "—"}</div>
                </div>
                <div style={{ padding: "16px 14px", textAlign: "center", background: "#f8fafc", borderRadius: 12, border: "1px solid var(--border)" }}>
                  <div className="t-xs muted" style={{ fontWeight: 600 }}>THỜI GIAN LÀM BÀI</div>
                  <div style={{ fontSize: 22, fontWeight: 800, marginTop: 6, color: "#0f172a" }}>{detail?.durationMinutes ? `${detail.durationMinutes} phút` : "—"}</div>
                </div>
                <div style={{ padding: "16px 14px", textAlign: "center", background: "#f8fafc", borderRadius: 12, border: "1px solid var(--border)" }}>
                  <div className="t-xs muted" style={{ fontWeight: 600 }}>ĐIỂM CẦN ĐẠT</div>
                  <div style={{ fontSize: 22, fontWeight: 800, marginTop: 6, color: "#0f172a" }}>{detail?.passScore != null ? `${Number(detail.passScore).toFixed(1)}%` : "—"}</div>
                </div>
              </div>

              <div style={{ marginTop: 20, display: "flex", flexWrap: "wrap", gap: 8 }}>
                <span className="chip" style={{ background: "#f1f5f9", color: "#475569", fontSize: 12 }}>
                  Số lần làm tối đa: {detail?.maxAttempts ?? "Không giới hạn"}
                </span>
                {detail?.proctoringEnabled && (
                  <span className="chip" style={{ background: "#fef9c3", color: "#92400e", fontSize: 12 }}>Có giám sát thi</span>
                )}
              </div>

              <div className="t-sm muted" style={{ marginTop: 28, textAlign: "center" }}>
                Đây là bản xem trước — không thể làm bài hoặc ghi nhận kết quả tại đây.
              </div>
            </>
          )}
        </div>
      </div>
    );
  }

  /* ── Resource card (không có trạng thái hoàn thành) ──────── */
  function ResourceCard({ title, subtitle, icon, iconBg, iconColor, isActive, onClick }) {
    return (
      <div onClick={onClick}
        style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 14px",
          border: "1.5px solid", borderRadius: 12, cursor: "pointer",
          background: isActive ? "#eff6ff" : "#fff", transition: "all .15s ease",
          borderColor: isActive ? "#2563eb" : "var(--border)" }}
        onMouseEnter={e => { if (!isActive) { e.currentTarget.style.borderColor = "#cbd5e1"; e.currentTarget.style.background = "#f8fafc"; } }}
        onMouseLeave={e => { if (!isActive) { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.background = "#fff"; } }}>
        <div style={{ width: 42, height: 42, borderRadius: 11, background: isActive ? "#dbeafe" : iconBg || "#f1f5f9",
          color: isActive ? "#2563eb" : iconColor || "#64748b", display: "grid", placeItems: "center", flexShrink: 0 }}>
          <Ic n={icon} size={20} />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 13.5, color: isActive ? "#1d4ed8" : "#0f172a" }} className="truncate">
            {title}
          </div>
          {subtitle && <div className="t-xs muted truncate" style={{ marginTop: 2, color: isActive ? "#2563eb" : "#64748b" }}>{subtitle}</div>}
        </div>
        {isActive && <div style={{ width: 8, height: 8, borderRadius: 999, background: "#2563eb", flexShrink: 0 }} />}
      </div>
    );
  }

  /* ── Docx Native Viewer (`docx-preview`) ─────────────────── */
  function DocxNativeViewer({ url, res, docHeaderBar }: any) {
    const containerRef = useRef<HTMLDivElement>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useEffect(() => {
      let active = true;
      if (!url) { setLoading(false); setError(true); return; }
      setLoading(true);
      setError(false);

      fetch(url)
        .then(r => {
          if (!r.ok) throw new Error("Network response was not ok");
          return r.blob();
        })
        .then(async (blob) => {
          if (!active || !containerRef.current) return;
          try {
            const { renderAsync } = await import('docx-preview');
            await renderAsync(blob, containerRef.current, undefined, {
              className: "docx-viewer-inner",
              inWrapper: false,
              ignoreWidth: false,
              ignoreHeight: false,
              breakPages: true,
            });
            if (active) setLoading(false);
          } catch (e) {
            console.error("docx-preview render error:", e);
            if (active) { setError(true); setLoading(false); }
          }
        })
        .catch(e => {
          console.error("Fetch docx error:", e);
          if (active) { setError(true); setLoading(false); }
        });

      return () => { active = false; };
    }, [url]);

    if (error) {
      return (
        <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", background: "#fff", border: "1px solid var(--border)", display: "flex", flexDirection: "column" }}
          onContextMenu={(e: any) => e.preventDefault()}>
          {docHeaderBar}
          <div style={{ position: "relative", height: 600 }}>
            <iframe src={`https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(url)}&wdDownloadButton=False&wdPrint=0`}
              title={res?.displayName} style={{ width: "100%", height: "100%", border: "none", display: "block" }} />
            <div style={{ position: "absolute", top: 0, right: 0, width: 180, height: 56, zIndex: 10, background: "transparent" }}
              onClick={(e: any) => { e.preventDefault(); e.stopPropagation(); }} />
            <div style={{ position: "absolute", bottom: 0, right: 0, width: 240, height: 52, zIndex: 10, background: "transparent" }}
              onClick={(e: any) => { e.preventDefault(); e.stopPropagation(); }} />
          </div>
        </div>
      );
    }

    return (
      <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", background: "#fff", border: "1px solid var(--border)", display: "flex", flexDirection: "column" }}
        onContextMenu={(e: any) => e.preventDefault()}>
        {docHeaderBar}
        <div style={{ position: "relative", height: 600, overflowY: "auto", background: "#f8fafc", padding: "24px 16px" }}>
          {loading && (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: 400, gap: 12, color: "#64748b" }}>
              <div className="spinner" style={{ width: 32, height: 32, border: "3px solid #e2e8f0", borderTopColor: "#2563eb", borderRadius: "50%" }} />
              <div style={{ fontSize: 13.5, fontWeight: 500 }}>Đang chuẩn bị hiển thị tài liệu Word...</div>
            </div>
          )}
          <div
            ref={containerRef}
            style={{
              maxWidth: 820,
              margin: "0 auto",
              background: "#fff",
              boxShadow: "0 4px 6px -1px rgba(0,0,0,0.05), 0 2px 4px -1px rgba(0,0,0,0.03)",
              borderRadius: 8,
              padding: loading ? 0 : "32px 40px",
              minHeight: loading ? 0 : 500,
              display: loading ? "none" : "block",
              color: "#0f172a",
            }}
          />
        </div>
      </div>
    );
  }

  /* ── PDF Native Viewer (`react-pdf`) ─────────────────────── */
  function PdfNativeViewer({ url, res, docHeaderBar }: any) {
    const wrapperRef = useRef<HTMLDivElement>(null);
    const [numPages, setNumPages] = useState<number | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<boolean>(false);
    const [containerWidth, setContainerWidth] = useState<number>(760);
    const [pdfComponents, setPdfComponents] = useState<any>(null);

    useEffect(() => {
      let active = true;
      if (!url) { setLoading(false); setError(true); return; }
      setLoading(true);
      setError(false);
      setNumPages(null);

      import('react-pdf').then(mod => {
        if (!active) return;
        const { Document, Page, pdfjs } = mod;
        if (pdfjs && pdfjs.version) {
          pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;
        }
        setPdfComponents({ Document, Page });
      }).catch(e => {
        console.error("import react-pdf error:", e);
        if (active) { setError(true); setLoading(false); }
      });

      return () => { active = false; };
    }, [url]);

    useEffect(() => {
      const updateWidth = () => {
        if (wrapperRef.current) {
          const w = wrapperRef.current.clientWidth - 48;
          if (w > 200) setContainerWidth(Math.min(w, 860));
        }
      };
      updateWidth();
      window.addEventListener('resize', updateWidth);
      return () => window.removeEventListener('resize', updateWidth);
    }, [pdfComponents]);

    if (error || !pdfComponents) {
      return (
        <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", background: "#fff", border: "1px solid var(--border)", display: "flex", flexDirection: "column" }}
          onContextMenu={(e: any) => e.preventDefault()}>
          {docHeaderBar}
          <div style={{ position: "relative", height: 600 }}>
            <iframe src={`${url}#toolbar=0&navpanes=0&scrollbar=0`} title={res?.displayName}
              style={{ width: "100%", height: "100%", border: "none", display: "block" }} />
          </div>
        </div>
      );
    }

    const { Document, Page } = pdfComponents;

    return (
      <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", background: "#fff", border: "1px solid var(--border)", display: "flex", flexDirection: "column" }}
        onContextMenu={(e: any) => e.preventDefault()}>
        {docHeaderBar}
        <div ref={wrapperRef} style={{ position: "relative", height: 600, overflowY: "auto", background: "#525659", padding: "24px 16px" }}>
          {loading && (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: 400, gap: 12, color: "#e2e8f0" }}>
              <div className="spinner" style={{ width: 32, height: 32, border: "3px solid #64748b", borderTopColor: "#38bdf8", borderRadius: "50%" }} />
              <div style={{ fontSize: 13.5, fontWeight: 500 }}>Đang chuẩn bị hiển thị trang PDF...</div>
            </div>
          )}
          <div style={{ maxWidth: 880, margin: "0 auto", display: loading ? "none" : "block" }}>
            <Document
              file={url}
              onLoadSuccess={({ numPages }: any) => { setNumPages(numPages); setLoading(false); }}
              onLoadError={(e: any) => { console.error("react-pdf load error:", e); setError(true); setLoading(false); }}
              loading={null}
            >
              {!loading && numPages && Array.from(new Array(numPages), (el, index) => (
                <div key={`page_${index + 1}`} style={{ marginBottom: 16, boxShadow: "0 4px 6px -1px rgba(0,0,0,0.3)", borderRadius: 4, overflow: "hidden", background: "#fff" }}>
                  <Page pageNumber={index + 1} width={containerWidth} renderTextLayer={false} renderAnnotationLayer={false} />
                </div>
              ))}
            </Document>
          </div>
        </div>
      </div>
    );
  }

  /* ── Viewer ─────────────────────────────────────────────── */
  function Viewer({ res, url, loading, error, onRetry }: any) {
    const videoRef = useRef(null);
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
          <iframe src={`https://www.youtube-nocookie.com/embed/${ytId}?rel=0`}
            style={{ width: "100%", height: "100%", border: "none", display: "block" }}
            allowFullScreen />
        </div>
      );
      return (
        <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", aspectRatio: "16/9", background: "#000" }}
          onContextMenu={(e: any) => e.preventDefault()}>
          <video ref={videoRef} key={url || res?.id} controls controlsList="nodownload" src={url}
            style={{ width: "100%", height: "100%", display: "block", outline: "none" }}
            onContextMenu={(e: any) => e.preventDefault()} />
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

    const docHeaderBar = (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 16px", background: "#f8fafc", borderBottom: "1px solid var(--border)", borderRadius: "16px 16px 0 0" }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: "#475569", display: "flex", alignItems: "center", gap: 6 }}>
          <Ic n="file-text" size={16} /> Trình xem tài liệu ({t})
        </span>
        <span style={{ fontSize: 12, color: "#92400e", fontWeight: 600, display: "flex", alignItems: "center", gap: 4 }}>
          <Ic n="eye" size={12} />Xem trước — không tính tiến trình
        </span>
      </div>
    );

    if (t === "PDF") return <PdfNativeViewer url={url} res={res} docHeaderBar={docHeaderBar} />;
    if (t === "DOC") return <DocxNativeViewer url={url} res={res} docHeaderBar={docHeaderBar} />;

    if (t === "SLIDE") {
      const isLocal = url.includes("localhost") || url.includes("127.0.0.1") || url.startsWith("/");
      if (isLocal) {
        return (
          <div style={{ borderRadius: 16, background: "#f8fafc", border: "1px solid var(--border)", padding: "40px 24px", textAlign: "center", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 12, minHeight: 320 }}
            onContextMenu={(e: any) => e.preventDefault()}>
            <div style={{ width: 56, height: 56, borderRadius: 16, background: "#fff7ed", color: "#ea580c", display: "grid", placeItems: "center" }}>
              <Ic n="book" size={28} />
            </div>
            <div style={{ fontWeight: 600, fontSize: 14.5, color: "#0f172a" }}>
              {res.displayName || res.originalFilename || "Slide bài giảng PowerPoint"}
            </div>
            <div style={{ fontSize: 13, color: "#64748b", maxWidth: 440, lineHeight: 1.5 }}>
              Trình xem trực tuyến không thể kết nối tới đường dẫn nội bộ (Localhost). Slide sẽ hiển thị trực tiếp trong khung khi hệ thống chạy trên máy chủ chính thức (Production).
            </div>
          </div>
        );
      }
      return (
        <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", background: "#fff", border: "1px solid var(--border)", display: "flex", flexDirection: "column" }}
          onContextMenu={(e: any) => e.preventDefault()}>
          {docHeaderBar}
          <div style={{ position: "relative", height: 600 }}>
            <iframe src={`https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(url)}&wdDownloadButton=False&wdPrint=0`}
              title={res.displayName} style={{ width: "100%", height: "100%", border: "none", display: "block" }} />
            <div style={{ position: "absolute", top: 0, right: 0, width: 180, height: 56, zIndex: 10, background: "transparent" }}
              onClick={(e: any) => { e.preventDefault(); e.stopPropagation(); }} />
            <div style={{ position: "absolute", bottom: 0, right: 0, width: 240, height: 52, zIndex: 10, background: "transparent" }}
              onClick={(e: any) => { e.preventDefault(); e.stopPropagation(); }} />
          </div>
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
  function PreviewPlayer({ onBack }) {
    const { courseId, role } = window.__previewCourse || {};

    const [course,   setCourse]   = useState(null);
    const [chapters, setChapters] = useState([]);
    const [loading,  setLoading]  = useState(!!courseId);
    const [active,   setActive]   = useState(null);
    const [viewRes,  setViewRes]  = useState(null);
    const [resUrls,  setResUrls]  = useState({});
    const [openCh,   setOpenCh]   = useState({});
    const [activeVideoIdx, setActiveVideoIdx] = useState(0);

    const [sidebarTab,    setSidebarTab]    = useState("lessons");
    const [assignments,   setAssignments]   = useState([]);
    const [assignLoading, setAssignLoading] = useState(false);

    const allLessons   = useMemo(() => chapters.flatMap(c => c.lessons || []), [chapters]);
    const activeIdx    = useMemo(() => allLessons.findIndex(l => l.id === active?.id), [allLessons, active]);
    const activeCh     = useMemo(() => chapters.find(c => c.lessons?.some(l => l.id === active?.id)), [chapters, active]);
    const prevLesson   = activeIdx > 0 ? allLessons[activeIdx - 1] : null;
    const nextLesson   = activeIdx < allLessons.length - 1 ? allLessons[activeIdx + 1] : null;
    const totalLessons = allLessons.length;

    const activeResources = useMemo(() => (active?.resources || []).filter((r: any) => !r.pendingDelete), [active]);
    const videoResources   = useMemo(() => activeResources.filter((r: any) => r.resourceType === "VIDEO"), [activeResources]);
    const docResources     = useMemo(() => activeResources.filter((r: any) => r.resourceType !== "VIDEO"), [activeResources]);
    const activeVideoRes   = videoResources[activeVideoIdx] || videoResources[0] || null;
    const isVideoActive    = videoResources.length > 0;
    const videoUrl         = activeVideoRes ? (resUrls[activeVideoRes.id]?.url || activeVideoRes?.externalUrl) : null;

    /* Fetch course */
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

    /* Fetch assignments when switching to the "Bài tập" tab */
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

    /* Khi đổi bài học: tự tải video đầu tiên và mở sẵn tài liệu đầu nếu không có video */
    useEffect(() => {
      if (!active) { setViewRes(null); return; }
      setActiveVideoIdx(0);
      setResUrls({});
      const docs = (active.resources || []).filter((r: any) => !r.pendingDelete && r.resourceType !== "VIDEO");
      const videos = (active.resources || []).filter((r: any) => !r.pendingDelete && r.resourceType === "VIDEO");
      const hasRealVideo = videos.length > 0;
      setViewRes(!hasRealVideo ? (docs[0] || null) : null);
      videos.forEach(v => { if (!v.externalUrl) fetchResUrl(v, active.id); });
      if (docs[0] && !docs[0].externalUrl) fetchResUrl(docs[0], active.id);
    }, [active?.id]);

    function fetchResUrl(r, lessonId) {
      if (!r) return;
      if (resUrls[r.id]?.url || resUrls[r.id]?.loading) return;
      if (r.externalUrl) { setResUrls(m => ({ ...m, [r.id]: { url: r.externalUrl } })); return; }
      setResUrls(m => ({ ...m, [r.id]: { loading: true } }));
      const ep = role === "admin"
        ? `/admin/courses/resources/${r.id}/download-url`
        : `/instructor/courses/${courseId}/lessons/${lessonId}/resources/${r.id}/view-url`;
      api.get(ep)
        .then(res => setResUrls(m => ({ ...m, [r.id]: { url: res.data?.url } })))
        .catch(() => setResUrls(m => ({ ...m, [r.id]: { error: true } })));
    }

    function handleViewRes(r) {
      setViewRes(r);
      if (r && !r.externalUrl) fetchResUrl(r, active.id);
    }

    function getViewerUrl(res) {
      if (!res) return null;
      if (res.externalUrl) return res.externalUrl;
      return resUrls[res.id]?.url || null;
    }

    function goLesson(l) {
      setActive(l);
      const ch = chapters.find(c => c.lessons?.some(x => x.id === l.id));
      if (ch) setOpenCh(p => ({ ...p, [ch.id]: true }));
    }

    /* `position: fixed` bên trong wrapper chuyển trang `.page.fade-in` (có transform) sẽ bị
       ăn theo containing block của transform đó thay vì viewport thật — cả kích thước lẫn vị
       trí đều sai (overlay bị kéo dài thừa xuống dưới, và lệch theo scroll của trang bên dưới).
       Portal thẳng ra `document.body` để overlay luôn neo đúng theo viewport thật, bất kể nó
       được gọi từ đâu trong cây component (giống pattern `createPortal` đã dùng ở Modal). */
    const Overlay = ({ children }) => createPortal(
      <div style={{ position: "fixed", inset: 0, zIndex: 200,
        background: "#fff", display: "flex", flexDirection: "column", overflow: "hidden" }}>
        {children}
      </div>,
      document.body
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

    return (
      <Overlay>
        {/* ─── Top bar ─────────────────────────────────────── */}
        <div style={{ height: 64, flexShrink: 0, borderBottom: "1px solid rgba(255,255,255,.08)",
          display: "flex", alignItems: "center", gap: 10, padding: "0 20px", background: "#0f172a" }}>
          <button onClick={onBack} title="Quay lại"
            style={{ width: 30, height: 30, borderRadius: 8, border: "1px solid rgba(255,255,255,.12)",
              background: "rgba(255,255,255,.06)", color: "#cbd5e1", display: "grid",
              placeItems: "center", cursor: "pointer", flexShrink: 0 }}>
            <Ic n="arrow_left" size={15} />
          </button>
          <div style={{ display: "flex", alignItems: "center", gap: 6, flexShrink: 0 }}>
            <div style={{ width: 28, height: 28, borderRadius: 8,
              background: "linear-gradient(135deg,#1e40af,#3b82f6)", display: "grid", placeItems: "center" }}>
              <Ic n="book" size={14} style={{ color: "#fff" }} />
            </div>
            <span style={{ fontWeight: 700, fontSize: 14, color: "#e2e8f0" }}>Rikkei Edu</span>
          </div>
          <div style={{ width: 1, height: 16, background: "rgba(255,255,255,.15)" }} />
          <div style={{ flex: 1, minWidth: 0, display: "flex", alignItems: "center", gap: 5, fontSize: 12.5 }}>
            <span style={{ color: "#94a3b8" }} className="truncate">{course.title}</span>
            {activeCh && (
              <><Ic n="chevron_right" size={11} style={{ color: "#64748b", flexShrink: 0 }} />
                <span style={{ color: "#cbd5e1", flexShrink: 0 }} className="truncate">{activeCh.title}</span></>
            )}
            {active && (
              <><Ic n="chevron_right" size={11} style={{ color: "#64748b", flexShrink: 0 }} />
                <span style={{ color: "#fff", fontWeight: 600, flexShrink: 0 }} className="truncate">{active.title}</span></>
            )}
          </div>
          <span style={{ background: "#fef9c3", color: "#92400e", borderRadius: 20, padding: "4px 12px",
            fontSize: 11.5, fontWeight: 600, display: "flex", alignItems: "center", gap: 4, flexShrink: 0 }}>
            <Ic n="eye" size={12} />Xem trước
          </span>
        </div>

        {/* ─── Body ────────────────────────────────────────── */}
        <div style={{ flex: 1, minHeight: 0, display: "flex", overflow: "hidden" }}>

          {/* ── Left: main content ─────────────────────────── */}
          <div style={{ flex: 1, minWidth: 0, minHeight: 0, overflowY: "auto",
            padding: "24px 24px 32px", background: "#f8fafc" }}>
            {!active ? (
              <div style={{ textAlign: "center", padding: 60, color: "#94a3b8" }}>
                <Ic n="book" size={32} style={{ opacity: 0.3, marginBottom: 12 }} />
                <div>Chọn một bài giảng từ danh sách</div>
              </div>
            ) : (
              <>
                <div className="t-sm muted" style={{ marginBottom: 6, display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap" }}>
                  <span>{course.title}</span>
                  {activeCh && <><Ic n="chevron_right" size={11} /><span>{activeCh.title}</span></>}
                  <Ic n="chevron_right" size={11} /><span style={{ color: "#0f172a", fontWeight: 600 }}>{active.title}</span>
                </div>
                <h1 className="t-h1" style={{ marginBottom: 20, fontSize: 22 }}>{active.title}</h1>

                {active.type === 'QUIZ' ? (
                  <QuizPreviewDetail lesson={active} courseId={courseId} />
                ) : (
                  <>
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
                              icon="video" iconBg="#eaf1ff" iconColor="#2563eb"
                              isActive={i === activeVideoIdx}
                              onClick={() => setActiveVideoIdx(i)} />
                          ))}
                        </div>
                        {isVideoActive && (
                          <Viewer key={activeVideoRes?.id || videoUrl || activeVideoIdx} res={activeVideoRes} url={videoUrl} />
                        )}
                      </div>
                    )}

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
                                subtitle={sub} icon={rs.icon} iconBg={rs.bg} iconColor={rs.color}
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
                  </>
                )}

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
                      cursor: nextLesson ? "pointer" : "default", opacity: nextLesson ? 1 : 0.35 }}
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

          {/* ── Right: dark curriculum sidebar ─────────────── */}
          <div className="dark-scroll" style={{ width: 380, flex: "none", background: "#0f172a", color: "#fff",
            padding: "22px 20px", overflowY: "auto" }}>
            <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700, lineHeight: 1.4 }}>
              {course.title}
            </h3>
            <div style={{ fontSize: 12.5, color: "#94a3b8", marginBottom: 16, display: "flex", gap: 16 }}>
              <span>{chapters.length} chương</span>
              <span>•</span>
              <span>{totalLessons} bài giảng</span>
            </div>

            {/* Sidebar tab bar */}
            <div style={{ flexShrink: 0, display: "flex", borderBottom: "1px solid rgba(255,255,255,.1)", marginBottom: 12 }}>
              {["lessons", "assignments"].map(tab => {
                const isAct = sidebarTab === tab;
                return (
                  <button key={tab} onClick={() => setSidebarTab(tab)}
                    style={{ flex: 1, height: 36, border: "none", background: "transparent",
                      cursor: "pointer", fontSize: 12.5, fontWeight: 600,
                      color: isAct ? "#60a5fa" : "#64748b",
                      borderBottom: `2px solid ${isAct ? "#3b82f6" : "transparent"}`,
                      transition: ".13s", display: "flex", alignItems: "center",
                      justifyContent: "center", gap: 5 }}>
                    <Ic n={tab === "lessons" ? "book" : "clipboard"} size={13} />
                    {tab === "lessons" ? "Bài học" : "Bài tập"}
                  </button>
                );
              })}
            </div>

            {sidebarTab === "lessons" ? (
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                {chapters.length === 0 ? (
                  <div style={{ textAlign: "center", color: "#64748b", fontSize: 12, padding: "40px 16px" }}>
                    Chưa có chương nào trong khóa học
                  </div>
                ) : (
                  chapters.map((ch, ci) => {
                    const isOpen = openCh[ch.id];
                    return (
                      <div key={ch.id}
                        style={{ background: "rgba(255,255,255,.04)", border: "1px solid rgba(255,255,255,.07)",
                          borderRadius: 13, overflow: "hidden" }}>
                        <div onClick={() => setOpenCh(p => ({ ...p, [ch.id]: !p[ch.id] }))}
                          style={{ display: "flex", alignItems: "center", gap: 10, padding: "13px 14px", cursor: "pointer" }}>
                          <div style={{ width: 22, height: 22, borderRadius: 999, background: "#1e293b", color: "#94a3b8",
                            display: "grid", placeItems: "center", flexShrink: 0, fontSize: 11, fontWeight: 700 }}>
                            {ci + 1}
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontSize: 13.5, fontWeight: 600 }} className="truncate">{ch.title}</div>
                            <div style={{ fontSize: 11, color: "#64748b", marginTop: 1 }}>{ch.lessons?.length || 0} bài</div>
                          </div>
                          <Ic n="chevron_down" size={16}
                            style={{ transform: isOpen ? "rotate(180deg)" : "none", transition: ".2s", color: "#64748b", flexShrink: 0 }} />
                        </div>

                        {isOpen && (ch.lessons || []).map(l => {
                          const isAct = active?.id === l.id;
                          const lHasVid = (l.resources || []).some((r: any) => !r.pendingDelete && r.resourceType === "VIDEO");
                          const lIsVid  = lHasVid || l.type === "VIDEO";
                          const isQuiz     = l.type === "QUIZ";
                          const quizLocked = isQuiz && l.quizStatus !== "PUBLISHED";
                          return (
                            <div key={l.id}
                              onClick={() => { if (!quizLocked) goLesson(l); }}
                              title={quizLocked ? "Giảng viên chưa xuất bản đề này" : undefined}
                              style={{ display: "flex", alignItems: "center", gap: 10,
                                padding: "10px 10px 10px 48px", cursor: quizLocked ? "not-allowed" : "pointer",
                                opacity: quizLocked ? 0.5 : 1,
                                background: isAct ? "rgba(59,130,246,.16)" : "transparent",
                                transition: ".12s" }}
                              onMouseEnter={e => { if (!isAct && !quizLocked) e.currentTarget.style.background = "rgba(255,255,255,.03)"; }}
                              onMouseLeave={e => { if (!isAct) e.currentTarget.style.background = "transparent"; }}>
                              <Ic n={isQuiz ? "clipboard" : lIsVid ? "video" : "file"} size={13}
                                style={{ color: isAct ? "#60a5fa" : "#64748b", flexShrink: 0 }} />
                              <div style={{ flex: 1, minWidth: 0 }}>
                                <div style={{ fontSize: 13, fontWeight: isAct ? 700 : 500,
                                  color: isAct ? "#93c5fd" : "#cbd5e1" }} className="truncate">
                                  {l.title}
                                </div>
                                <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>
                                  {isQuiz ? (quizLocked ? "Đề trắc nghiệm (chưa xuất bản)" : "Đề trắc nghiệm") : lIsVid ? "Video" : "Tài liệu"}
                                </div>
                              </div>
                              {isAct && <div style={{ width: 6, height: 6, borderRadius: 999, background: "#3b82f6", flexShrink: 0 }} />}
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
                <div style={{ flexShrink: 0, display: "flex", alignItems: "center", gap: 6, padding: "0 0 8px" }}>
                  <span style={{ fontSize: 12, fontWeight: 600, color: "#e2e8f0", flex: 1 }}>
                    Bài tập
                    {assignments.length > 0 && (
                      <span style={{ fontWeight: 400, color: "#94a3b8", marginLeft: 4 }}>({assignments.length})</span>
                    )}
                  </span>
                </div>

                <div>
                  {assignLoading ? (
                    <div style={{ textAlign: "center", color: "#64748b", fontSize: 12, padding: "40px 16px" }}>
                      Đang tải bài tập...
                    </div>
                  ) : assignments.length === 0 ? (
                    <div style={{ textAlign: "center", color: "#64748b", fontSize: 12, padding: "40px 16px" }}>
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
                            day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit",
                          })
                        : null;
                      return (
                        <div key={a.id}
                          style={{ display: "flex", alignItems: "flex-start", gap: 8,
                            padding: "8px 10px", margin: "2px 0", borderRadius: 8 }}>
                          <div style={{ width: 26, height: 26, borderRadius: 7, flexShrink: 0,
                            background: "#fef3c7", color: "#d97706", display: "grid", placeItems: "center" }}>
                            <Ic n="clipboard" size={12} />
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontSize: 12.5, fontWeight: 600, color: "#e2e8f0",
                              lineHeight: 1.35, marginBottom: 3 }} className="truncate">{a.title}</div>
                            <div style={{ display: "flex", flexWrap: "wrap", gap: 4, marginBottom: 3 }}>
                              <span style={{ fontSize: 10, fontWeight: 600, borderRadius: 4, padding: "1px 6px", background: sc.bg, color: sc.color }}>
                                {sc.label}
                              </span>
                              {a.maxScore != null && (
                                <span style={{ fontSize: 10, fontWeight: 600, borderRadius: 4, padding: "1px 6px", background: "#f0fdf4", color: "#16a34a" }}>
                                  {a.maxScore} điểm
                                </span>
                              )}
                              {a.scope === "SPECIFIC_GROUPS" && (
                                <span style={{ fontSize: 10, fontWeight: 600, borderRadius: 4, padding: "1px 6px", background: "#eff6ff", color: "#2563eb" }}>
                                  Theo nhóm
                                </span>
                              )}
                            </div>
                            {deadline && (
                              <div style={{ fontSize: 10.5, color: "#94a3b8", display: "flex", alignItems: "center", gap: 3 }}>
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
      </Overlay>
    );
  }

  Object.assign(window, { PreviewPlayer });
})();
