// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Trình phát bài giảng cho học viên (+ AI Chatbot)
   ============================================================ */
(function () {
  const { useState, useEffect, useRef, useMemo, useCallback } = React;
  const Ic  = window.Icon, api = window.httpClient;
  const Av  = window.Avatar;
  const Md  = window.Modal, MH = window.ModalHead;
  const { getStudentCourseProgress, getMyAttempts, getQuizDetail } = window.__quizService;

  function fmtCountdown(ms) {
    const total = Math.max(0, Math.ceil(ms / 1000));
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    return h > 0
      ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
      : `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  }

  /* ─── Popup xác nhận trước khi vào làm quiz ───────────────
     Hiện lịch sử làm bài, số lần còn lại, điểm cao nhất so với điểm cần pass.
     Nếu đang trong thời gian cooldown (sau lần nộp gần nhất), nút bắt đầu bị
     khóa và thay bằng đồng hồ đếm ngược tới lúc được làm lại. */
  function QuizConfirmModal({ lesson, courseId, onClose, onStart }) {
    const [detail, setDetail] = useState(null);
    const [progress, setProgress] = useState(null);
    const [attempts, setAttempts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [now, setNow] = useState(Date.now());

    useEffect(() => {
      if (!lesson) return;
      setLoading(true);
      setError("");
      Promise.all([
        getQuizDetail(courseId, lesson.quizId),
        getStudentCourseProgress(courseId),
        getMyAttempts(courseId, lesson.quizId),
      ])
        .then(([quizDetail, progressList, attemptList]) => {
          setDetail(quizDetail);
          setProgress((progressList || []).find(p => p.quizId === lesson.quizId) || null);
          setAttempts([...(attemptList || [])].sort((a, b) => b.attemptNumber - a.attemptNumber));
        })
        .catch(err => setError(err?.response?.data?.message || "Không thể tải thông tin đề trắc nghiệm."))
        .finally(() => setLoading(false));
    }, [lesson, courseId]);

    // Đếm ngược cooldown — tick mỗi giây, chỉ chạy khi thực sự đang khóa.
    const latestSubmitted = attempts.find(a => a.submittedAt)?.submittedAt;
    const cooldownMinutes = detail?.cooldownMinutes != null ? detail.cooldownMinutes : 20;
    const retryAt = latestSubmitted ? new Date(latestSubmitted).getTime() + cooldownMinutes * 60000 : null;
    const inCooldown = !!(retryAt && now < retryAt);

    useEffect(() => {
      if (!inCooldown) return;
      const t = setInterval(() => setNow(Date.now()), 1000);
      return () => clearInterval(t);
    }, [inCooldown]);

    if (!lesson) return null;

    const hasInProgress = attempts.some(a => a.status === "IN_PROGRESS");
    const remaining = detail?.maxAttempts != null ? Math.max(0, detail.maxAttempts - attempts.length) : null;
    const outOfAttempts = remaining != null && remaining <= 0 && !hasInProgress;
    const canStart = !loading && !error && !inCooldown && !outOfAttempts;
    const bestPct = progress?.bestScorePercentage;

    return (
      <Md open={!!lesson} onClose={onClose} max={640}>
        <MH title={lesson.title} sub="Xác nhận làm bài" icon="clipboard" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body">
          {loading ? (
            <div style={{ padding: 40, textAlign: "center", color: "var(--text-2)" }}>Đang tải...</div>
          ) : error ? (
            <div style={{ padding: 40, textAlign: "center", color: "var(--error)" }}>{error}</div>
          ) : (
            <>
              <div className="grid" style={{ gridTemplateColumns: "repeat(3, 1fr)", gap: 12, marginBottom: 18 }}>
                <div className="card" style={{ padding: 14, textAlign: "center" }}>
                  <div className="t-xs muted">Số lần còn lại</div>
                  <div style={{ fontSize: 20, fontWeight: 700, marginTop: 4 }}>
                    {remaining == null ? "Không giới hạn" : `${remaining} / ${detail.maxAttempts}`}
                  </div>
                </div>
                <div className="card" style={{ padding: 14, textAlign: "center" }}>
                  <div className="t-xs muted">Điểm cao nhất</div>
                  <div style={{ fontSize: 20, fontWeight: 700, marginTop: 4,
                    color: progress?.passed ? "var(--success)" : "var(--text)" }}>
                    {bestPct != null ? `${Number(bestPct).toFixed(1)}%` : "—"}
                  </div>
                </div>
                <div className="card" style={{ padding: 14, textAlign: "center" }}>
                  <div className="t-xs muted">Điểm cần đạt</div>
                  <div style={{ fontSize: 20, fontWeight: 700, marginTop: 4 }}>
                    {detail?.passScore != null ? `${Number(detail.passScore).toFixed(1)}%` : "—"}
                  </div>
                </div>
              </div>

              <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 8 }}>Lịch sử làm bài</div>
              {attempts.length === 0 ? (
                <div style={{ padding: 24, textAlign: "center", color: "var(--text-2)", background: "var(--bg-2, #f8fafc)", borderRadius: 10 }}>
                  Bạn chưa làm bài này lần nào.
                </div>
              ) : (
                <div style={{ overflowX: "auto" }}>
                  <table className="tbl">
                    <thead>
                      <tr><th>Lần</th><th>Ngày nộp</th><th>Điểm</th><th>Kết quả</th></tr>
                    </thead>
                    <tbody>
                      {attempts.map(a => (
                        <tr key={a.attemptId}>
                          <td style={{ fontWeight: 700 }}>#{a.attemptNumber}</td>
                          <td className="muted">{a.submittedAt ? new Date(a.submittedAt).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "—"}</td>
                          <td style={{ fontWeight: 700 }}>
                            {a.scorePercentage != null
                              ? <span style={{ color: a.isPassed ? "var(--success)" : "var(--error)" }}>{Number(a.scorePercentage).toFixed(1)}%</span>
                              : <span className="muted">—</span>}
                          </td>
                          <td>
                            {a.status === "IN_PROGRESS" ? (
                              <span className="chip chip-neutral" style={{ fontSize: 11 }}>Đang làm</span>
                            ) : a.isPassed ? (
                              <span className="chip chip-success" style={{ fontSize: 11 }}>Đạt</span>
                            ) : (
                              <span className="chip chip-error" style={{ fontSize: 11 }}>Chưa đạt</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {outOfAttempts && (
                <div className="t-sm" style={{ color: "var(--error)", marginTop: 14 }}>
                  Bạn đã sử dụng hết số lần làm bài cho đề này.
                </div>
              )}
            </>
          )}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
          {inCooldown ? (
            <button className="btn btn-primary" disabled style={{ opacity: .7, cursor: "not-allowed" }}>
              Còn lại {fmtCountdown(retryAt - now)}
            </button>
          ) : (
            <button className="btn btn-primary" disabled={!canStart} onClick={onStart}>
              {hasInProgress ? "Tiếp tục làm bài" : attempts.length > 0 ? "Làm lại" : "Bắt đầu làm bài"}
            </button>
          )}
        </div>
      </Md>
    );
  }

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
  function ResourceCard({ title, subtitle, icon, iconBg, iconColor, isActive, isCompleted, onClick }) {
    return (
      <div onClick={onClick}
        style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 14px",
          border: "1.5px solid", borderRadius: 12, cursor: "pointer",
          background: isActive ? "#f5f3ff" : "#fff", transition: "all .15s ease",
          borderColor: isActive ? "#8b5cf6" : isCompleted ? "#16a34a" : "var(--border)",
          boxShadow: isActive ? "0 2px 8px rgba(139,92,246,.12)" : "none" }}
        onMouseEnter={e => { if (!isActive) { e.currentTarget.style.borderColor = isCompleted ? "#16a34a" : "#cbd5e1"; e.currentTarget.style.background = "#f8fafc"; } }}
        onMouseLeave={e => { if (!isActive) { e.currentTarget.style.borderColor = isCompleted ? "#16a34a" : "var(--border)"; e.currentTarget.style.background = "#fff"; } }}>
        <div style={{ width: 42, height: 42, borderRadius: 11, background: isActive ? "#ede9fe" : isCompleted ? "#dcfce7" : iconBg || "#f1f5f9",
          color: isActive ? "#7c3aed" : isCompleted ? "#16a34a" : iconColor || "#64748b", display: "grid", placeItems: "center", flexShrink: 0 }}>
          {isCompleted && !isActive ? <Ic n="check" size={20} /> : <Ic n={icon} size={20} />}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 13.5, color: isActive ? "#5b21b6" : "#0f172a" }} className="truncate">
            {title}
          </div>
          {subtitle && <div className="t-xs muted truncate" style={{ marginTop: 2, color: isActive ? "#7c3aed" : "#64748b" }}>{subtitle}</div>}
        </div>
        {isCompleted && (
          <span className="chip chip-success" style={{ fontSize: 10, padding: "2px 6px", borderRadius: 6, fontWeight: 600 }}>Xong</span>
        )}
        {isActive && !isCompleted && (
          <div style={{ width: 8, height: 8, borderRadius: 999, background: "#8b5cf6", flexShrink: 0 }} />
        )}
      </div>
    );
  }

  /* ── Docx Native Viewer (`docx-preview`) ─────────────────── */
  function DocxNativeViewer({ url, res, isCompleted, onMarkCompleted, docHeaderBar }: any) {
    const containerRef = useRef<HTMLDivElement>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);
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

    const handleScroll = () => {
      if (isCompleted || !wrapperRef.current) return;
      const el = wrapperRef.current;
      if (el.scrollHeight > el.clientHeight + 20) {
        if (el.scrollTop + el.clientHeight >= el.scrollHeight - 50 && el.scrollTop > 10) {
          if (onMarkCompleted) onMarkCompleted();
        }
      }
    };

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
        <div
          ref={wrapperRef}
          onScroll={handleScroll}
          style={{
            position: "relative",
            height: 600,
            overflowY: "auto",
            background: "#f8fafc",
            padding: "24px 16px",
          }}
        >
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
  function PdfNativeViewer({ url, res, isCompleted, onMarkCompleted, docHeaderBar }: any) {
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

    const handleScroll = () => {
      if (isCompleted || !wrapperRef.current) return;
      const el = wrapperRef.current;
      if (el.scrollHeight > el.clientHeight + 20) {
        if (el.scrollTop + el.clientHeight >= el.scrollHeight - 60 && el.scrollTop > 10) {
          if (onMarkCompleted) onMarkCompleted();
        }
      }
    };

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
        <div
          ref={wrapperRef}
          onScroll={handleScroll}
          style={{
            position: "relative",
            height: 600,
            overflowY: "auto",
            background: "#525659",
            padding: "24px 16px",
          }}
        >
          {loading && (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: 400, gap: 12, color: "#e2e8f0" }}>
              <div className="spinner" style={{ width: 32, height: 32, border: "3px solid #64748b", borderTopColor: "#38bdf8", borderRadius: "50%" }} />
              <div style={{ fontSize: 13.5, fontWeight: 500 }}>Đang chuẩn bị hiển thị trang PDF...</div>
            </div>
          )}
          <div style={{ maxWidth: 880, margin: "0 auto", display: loading ? "none" : "block" }}>
            <Document
              file={url}
              onLoadSuccess={({ numPages }: any) => {
                setNumPages(numPages);
                setLoading(false);
              }}
              onLoadError={(e: any) => {
                console.error("react-pdf load error:", e);
                setError(true);
                setLoading(false);
              }}
              loading={null}
            >
              {!loading && numPages && Array.from(new Array(numPages), (el, index) => (
                <div key={`page_${index + 1}`} style={{ marginBottom: 16, boxShadow: "0 4px 6px -1px rgba(0,0,0,0.3)", borderRadius: 4, overflow: "hidden", background: "#fff" }}>
                  <Page
                    pageNumber={index + 1}
                    width={containerWidth}
                    renderTextLayer={false}
                    renderAnnotationLayer={false}
                    onRenderSuccess={handleScroll}
                  />
                </div>
              ))}
            </Document>
          </div>
        </div>
      </div>
    );
  }

  /* ── Viewer ─────────────────────────────────────────────── */
  function Viewer({ res, url, onVideoTimeUpdate, loading, error, onRetry, onMarkCompleted, isCompleted }: any) {
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
    useEffect(() => {
      const el = videoRef.current;
      if (!el || !onVideoTimeUpdate) return;
      const handler = (e: any) => onVideoTimeUpdate(e);
      el.addEventListener('seeked', handler);
      return () => el.removeEventListener('seeked', handler);
    }, [onVideoTimeUpdate]);

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
          <iframe src={`https://www.youtube-nocookie.com/embed/${ytId}?rel=0&autoplay=1&enablejsapi=1`}
            style={{ width: "100%", height: "100%", border: "none", display: "block" }}
            allowFullScreen allow="autoplay" />
        </div>
      );
      return (
        <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", aspectRatio: "16/9", background: "#000" }}
          onContextMenu={(e: any) => e.preventDefault()}>
          <video ref={videoRef} key={url || res?.id} controls controlsList="nodownload" autoPlay src={url}
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

    const docHeaderBar = (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 16px", background: "#f8fafc", borderBottom: "1px solid var(--border)", borderRadius: "16px 16px 0 0" }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: "#475569", display: "flex", alignItems: "center", gap: 6 }}>
          <Ic n="file-text" size={16} /> Trình xem tài liệu ({t})
        </span>
        {isCompleted ? (
          <span className="chip chip-success" style={{ fontSize: 12, fontWeight: 600, padding: "4px 10px" }}>Đã xem xong ✓</span>
        ) : (
          <span style={{ fontSize: 12, color: "#64748b", fontStyle: "italic" }}>Đang tự động ghi nhận...</span>
        )}
      </div>
    );

    if (t === "PDF") {
      return (
        <PdfNativeViewer
          url={url}
          res={res}
          isCompleted={isCompleted}
          onMarkCompleted={onMarkCompleted}
          docHeaderBar={docHeaderBar}
        />
      );
    }

    if (t === "DOC") {
      return (
        <DocxNativeViewer
          url={url}
          res={res}
          isCompleted={isCompleted}
          onMarkCompleted={onMarkCompleted}
          docHeaderBar={docHeaderBar}
        />
      );
    }

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
            {isCompleted ? (
              <span className="chip chip-success" style={{ fontWeight: 600 }}>Đã hoàn thành ✓</span>
            ) : (
              <span style={{ fontSize: 13, color: "#64748b", fontStyle: "italic", marginTop: 4 }}>Đang ghi nhận tiến độ...</span>
            )}
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
    const [confirmQuiz, setConfirmQuiz] = useState(null); // lesson QUIZ đang chờ xác nhận trước khi vào làm bài
    
    /* State từ dev: quản lý gửi tin nhắn AI Chatbot thật */
    const [sending, setSending] = useState(false);
    const [conversationId, setConversationId] = useState(null);

    /* Assignment sidebar */
    const [sidebarTab, setSidebarTab] = useState("lessons");
    const [assignments, setAssignments] = useState([]);
    const [assignLoading, setAssignLoading] = useState(false);
    const [activeView, setActiveView] = useState("lesson");
    const [selectedAssignmentId, setSelectedAssignmentId] = useState(null);
    const [resProgressMap, setResProgressMap] = useState<Record<string, { pct?: number; completed?: boolean }>>({});

    useEffect(() => {
      api.get('/profile').then(r => { if (r.data?.fullName) setUserName(r.data.fullName); }).catch(() => { /* ignore */ });
    }, []);

    function notifMeta(type) {
      return (window.NotificationTypeMetadata && window.NotificationTypeMetadata[type]) || { icon: 'bell', color: '#2563eb', label: 'Hệ thống', category: 'Hệ thống' };
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

    const assignIdx      = useMemo(() => assignments.findIndex(a => a.id === selectedAssignmentId), [assignments, selectedAssignmentId]);
    const prevAssignment = assignIdx > 0 ? assignments[assignIdx - 1] : null;
    const nextAssignment = assignIdx < assignments.length - 1 ? assignments[assignIdx + 1] : null;

    /* Progress tracking */
    const progRef = useRef<any>({});
    const maxWatchedPctRef = useRef<Record<string, number>>({});
    const cumDocSecRef = useRef<Record<string, number>>({});
    const ytElapsedRef = useRef<Record<string, number>>({});
    const ytStateRef = useRef<Record<string, number>>({});
    const completedResRef = useRef<Record<string, boolean>>({});
    const resourceWatchedPctRef = useRef<Record<string, number>>({});
    const lastSentKeyRef = useRef<string>("");
    const lastSentTimeRef = useRef<number>(0);
    const updateProgress = useCallback(async (watchedPct, position, docSeconds, isCompleted) => {
      if (!courseId || !activeL?.id) return;
      const vids = (activeL.resources || []).filter((r: any) => !r.pendingDelete && r.resourceType === "VIDEO");
      const docs = (activeL.resources || []).filter((r: any) => !r.pendingDelete && r.resourceType !== "VIDEO" && r.resourceType !== "IMAGE");
      const imgs = (activeL.resources || []).filter((r: any) => !r.pendingDelete && r.resourceType === "IMAGE");
      const allVidsDone = vids.length === 0 || vids.every((v: any) => completedResRef.current[v.id] || (resourceWatchedPctRef.current[v.id] || 0) >= 90 || (ytElapsedRef.current[v.id] || 0) >= ((v.fileSizeBytes ? 60 : activeL.durationSeconds || 60) * 0.9));
      const allDocsDone = docs.length === 0 || docs.every((d: any) => completedResRef.current[d.id]);
      const allImgsDone = imgs.length === 0 || imgs.every((i: any) => completedResRef.current[i.id]);

      const autoComp = allVidsDone && allDocsDone && allImgsDone && (vids.length > 0 || docs.length > 0 || imgs.length > 0);
      const targetStatus = isCompleted || progRef.current[activeL.id] === "COMPLETED" || autoComp ? "COMPLETED" : "IN_PROGRESS";
      if (targetStatus === "COMPLETED") {
        progRef.current[activeL.id] = "COMPLETED";
      } else if (progRef.current[activeL.id] !== "COMPLETED") {
        progRef.current[activeL.id] = targetStatus;
      }
      setActiveL(prev => prev && prev.id === activeL.id ? { ...prev, progress: targetStatus } : prev);
      setChapters(prev => prev.map(ch => ({
        ...ch,
        lessons: (ch.lessons || []).map(l =>
          l.id === activeL.id
            ? { ...l, progress: l.progress === "COMPLETED" ? "COMPLETED" : targetStatus }
            : l
        ),
      })));
      try {
        const cached = JSON.parse(sessionStorage.getItem('lp_' + courseId) || '{}');
        cached[activeL.id] = targetStatus;
        sessionStorage.setItem('lp_' + courseId, JSON.stringify(cached));
      } catch { /* ignore */ }
      try {
        const isComp = isCompleted || targetStatus === "COMPLETED";
        const payloadKey = `${activeL.id}_${isComp}_${Math.floor((watchedPct || 0) / 10)}`;
        const now = Date.now();
        if (!isComp && lastSentKeyRef.current === payloadKey && now - lastSentTimeRef.current < 20000) {
          return;
        }
        lastSentKeyRef.current = payloadKey;
        lastSentTimeRef.current = now;

        await api.post(`/student/courses/${courseId}/lessons/${activeL.id}/progress`, {
          watchedPercentage: watchedPct,
          lastPlaybackPosition: position,
          documentViewSeconds: docSeconds,
          completed: isComp,
        });
      } catch (e) { console.error("updateProgress error", e); }
    }, [courseId, activeL?.id, videoResources.length, docResources.length]);

    const checkLessonCompletion = useCallback((currentResId?: string, forceCompleteRes?: boolean) => {
      if (!activeL?.id) return;
      if (currentResId && forceCompleteRes) {
        completedResRef.current[currentResId] = true;
        setResProgressMap(m => ({ ...m, [currentResId]: { completed: true, pct: 100 } }));
      }
      const vids = (activeL.resources || []).filter((r: any) => !r.pendingDelete && r.resourceType === "VIDEO");
      const docs = (activeL.resources || []).filter((r: any) => !r.pendingDelete && r.resourceType !== "VIDEO" && r.resourceType !== "IMAGE");
      const imgs = (activeL.resources || []).filter((r: any) => !r.pendingDelete && r.resourceType === "IMAGE");

      const allVidsDone = vids.length === 0 || vids.every((v: any) => 
        completedResRef.current[v.id] || (resourceWatchedPctRef.current[v.id] || 0) >= 90 || (ytElapsedRef.current[v.id] || 0) >= ((v.fileSizeBytes ? 60 : activeL.durationSeconds || 60) * 0.9)
      );
      const allDocsDone = docs.length === 0 || docs.every((d: any) => completedResRef.current[d.id]);
      const allImgsDone = imgs.length === 0 || imgs.every((i: any) => completedResRef.current[i.id]);

      if (allVidsDone && allDocsDone && allImgsDone) {
        if (progRef.current[activeL.id] !== "COMPLETED") {
          progRef.current[activeL.id] = "COMPLETED";
          updateProgress(100, null, cumDocSecRef.current[activeL.id] || 10, true);
        }
      } else if (
        vids.some((v: any) => (resourceWatchedPctRef.current[v.id] || 0) >= 1 || completedResRef.current[v.id]) ||
        docs.some((d: any) => completedResRef.current[d.id] || (cumDocSecRef.current[activeL.id] || 0) >= 1) ||
        imgs.some((i: any) => completedResRef.current[i.id])
      ) {
        if (progRef.current[activeL.id] !== "IN_PROGRESS" && progRef.current[activeL.id] !== "COMPLETED") {
          progRef.current[activeL.id] = "IN_PROGRESS";
          updateProgress(null, null, cumDocSecRef.current[activeL.id] || 1, false);
        }
      }
    }, [activeL, updateProgress]);

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
          let chs = r.data.chapters || [];
          /* Merge sessionStorage cache to survive reload if server progress lags */
          try {
            const cached = JSON.parse(sessionStorage.getItem('lp_' + courseId) || '{}');
            if (Object.keys(cached).length > 0) {
              chs = chs.map(ch => ({
                ...ch,
                lessons: (ch.lessons || []).map(l => ({
                  ...l,
                  progress: cached[l.id] || l.progress || 'NOT_STARTED',
                })),
              }));
            }
          } catch { /* ignore */ }
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
      } catch (e) { console.error("updateProgress error", e); }
            const ch = chs.find(c => c.lessons?.some(x => x.id === target.id));
            if (ch) opened[ch.id] = true;
          }
        })
        .catch(e => setError(e.response?.data?.message || "Không thể tải khóa học"))
        .finally(() => setLoading(false));
    }, [courseId]);

    /* Fetch assignments when switching to the "Bài tập" tab */
    useEffect(() => {
      if (sidebarTab !== "assignments" || !courseId) return;
      setAssignLoading(true);
      api.get(`/student/courses/${courseId}/assignments`)
        .then(r => setAssignments(r.data || []))
        .catch(() => setAssignments([]))
        .finally(() => setAssignLoading(false));
    }, [sidebarTab, courseId]);

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
      if (activeL && progRef.current[activeL.id] !== "COMPLETED") {
        if (progRef.current[activeL.id] !== "IN_PROGRESS") {
          progRef.current[activeL.id] = "IN_PROGRESS";
        }
        updateProgress(null, null, cumDocSecRef.current[activeL.id] || 1, false);
      }
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
    const videoThrottleRef = useRef(0);
    const handleVideoTimeUpdate = useCallback((e: any) => {
      const video = e.target;
      if (!video || !activeL?.id || !videoUrl) return;
      const rawDur = video.duration || 0;
      const cur = video.currentTime || 0;
      const isFiniteDur = rawDur > 0 && isFinite(rawDur);
      const dur = isFiniteDur 
        ? rawDur 
        : ((activeVideoRes?.fileSizeBytes && activeVideoRes.fileSizeBytes > 0 ? 60 : activeL.durationSeconds && activeL.durationSeconds > 0 ? activeL.durationSeconds : 60));

      const pct = dur > 0 ? Math.min(100, Math.round((cur / dur) * 100)) : 100;
      if (activeVideoRes?.id) {
        const prevPct = resourceWatchedPctRef.current[activeVideoRes.id] || 0;
        const newPct = Math.max(prevPct, pct);
        resourceWatchedPctRef.current[activeVideoRes.id] = newPct;
        setResProgressMap(m => ({ ...m, [activeVideoRes.id]: { pct: newPct, completed: newPct >= 90 || completedResRef.current[activeVideoRes.id] } }));
      }
      maxWatchedPctRef.current[activeL.id] = Math.max(
        maxWatchedPctRef.current[activeL.id] || 0,
        pct
      );
      const effectivePct = activeVideoRes?.id ? (resourceWatchedPctRef.current[activeVideoRes.id] || 0) : maxWatchedPctRef.current[activeL.id];
      const curProg = progRef.current[activeL.id];
      const now = Date.now();
      const isEndedOrNearEnd = e.type === "ended" || effectivePct >= 90 || (isFiniteDur && rawDur - cur <= 3) || (!isFiniteDur && cur >= dur * 0.9);

      if (isEndedOrNearEnd) {
        if (activeVideoRes?.id) {
          completedResRef.current[activeVideoRes.id] = true;
          resourceWatchedPctRef.current[activeVideoRes.id] = 100;
          setResProgressMap(m => ({ ...m, [activeVideoRes.id]: { pct: 100, completed: true } }));
        }
        checkLessonCompletion(activeVideoRes?.id, true);
        if (now - videoThrottleRef.current >= 15000) {
          videoThrottleRef.current = now;
          updateProgress(100, Math.floor(cur), cumDocSecRef.current[activeL.id] || 0, progRef.current[activeL.id] === "COMPLETED");
        }
      } else if (effectivePct >= 1 || cur >= 1) {
        if (curProg !== "IN_PROGRESS" && curProg !== "COMPLETED") {
          progRef.current[activeL.id] = "IN_PROGRESS";
        }
        checkLessonCompletion(activeVideoRes?.id, false);
        if (now - videoThrottleRef.current >= 30000) {
          videoThrottleRef.current = now;
          updateProgress(effectivePct, Math.floor(cur), cumDocSecRef.current[activeL.id] || 0, false);
        }
      } else if (now - videoThrottleRef.current >= 30000) {
        videoThrottleRef.current = now;
        updateProgress(effectivePct, Math.floor(cur), cumDocSecRef.current[activeL.id] || 0, false);
      }
    }, [activeL?.id, activeL?.durationSeconds, updateProgress, activeVideoIdx, videoUrl, docResources.length, activeVideoRes?.id, checkLessonCompletion]);

    /* Reset progress ref when lesson changes (run first) */
    useEffect(() => {
      if (activeL?.id) {
        if (activeL.progress === "COMPLETED") {
          progRef.current[activeL.id] = "COMPLETED";
          (activeL.resources || []).forEach((r: any) => {
            if (r?.id) {
              completedResRef.current[r.id] = true;
              setResProgressMap(m => ({ ...m, [r.id]: { completed: true, pct: 100 } }));
            }
          });
        } else {
          if (!progRef.current[activeL.id] || progRef.current[activeL.id] === "NOT_STARTED") {
            progRef.current[activeL.id] = "IN_PROGRESS";
            updateProgress(0, 0, cumDocSecRef.current[activeL.id] || 1, false);
          } else {
            progRef.current[activeL.id] = activeL.progress || "IN_PROGRESS";
          }
        }
      }
    }, [activeL?.id, activeL?.progress]);

    /* Cache progress to sessionStorage so reload doesn't lose checkmarks */
    useEffect(() => {
      if (!courseId) return;
      const cache = {};
      chapters.forEach(ch => (ch.lessons || []).forEach(l => { if (l.progress) cache[l.id] = l.progress; }));
      try { sessionStorage.setItem('lp_' + courseId, JSON.stringify(cache)); } catch { /* ignore */ }
    }, [courseId, chapters]);

    /* ── Progress: YouTube iframe tracking ── */
    useEffect(() => {
      if (!activeL?.id) return;
      if (!activeVideoRes || activeVideoRes.resourceType !== "VIDEO") return;
      const lid = activeL.id;
      const resId = activeVideoRes.id;
      const dur = (activeL.durationSeconds && activeL.durationSeconds > 0) ? activeL.durationSeconds : 60;
      const threshold = (activeL.durationSeconds && activeL.durationSeconds > 0) ? (dur * 90) / 100 : 30;

      let hasReceivedInfo = false;
      const handleYtMessage = (e: MessageEvent) => {
        try {
          if (typeof e.data !== "string") return;
          const data = JSON.parse(e.data);
          if (data?.event === "infoDelivery" && data?.info) {
            hasReceivedInfo = true;
            const state = data.info.playerState;
            if (typeof state === "number") ytStateRef.current[resId] = state;
            const cur = data.info.currentTime;
            const ytDur = data.info.duration || dur;
            if (cur && ytDur) {
              const pct = Math.min(100, Math.round((cur / ytDur) * 100));
              resourceWatchedPctRef.current[resId] = Math.max(resourceWatchedPctRef.current[resId] || 0, pct);
              setResProgressMap(m => ({ ...m, [resId]: { pct: resourceWatchedPctRef.current[resId], completed: resourceWatchedPctRef.current[resId] >= 90 || completedResRef.current[resId] } }));
            }
            if (state === 0 || (cur && ytDur && (cur / ytDur >= 0.9 || ytDur - cur <= 3))) {
              if (resId) {
                completedResRef.current[resId] = true;
                resourceWatchedPctRef.current[resId] = 100;
                setResProgressMap(m => ({ ...m, [resId]: { pct: 100, completed: true } }));
              }
              checkLessonCompletion(resId, true);
            }
          }
        } catch { /* ignore */ }
      };
      window.addEventListener("message", handleYtMessage);
      const pingYt = () => {
        document.querySelectorAll("iframe").forEach(ifr => {
          try { ifr.contentWindow?.postMessage(JSON.stringify({ event: "listening", id: resId, channel: "widget" }), "*"); } catch {}
        });
      };
      pingYt();

      let elapsed = ytElapsedRef.current[resId] || 0;
      if (elapsed >= threshold || completedResRef.current[resId] || (resourceWatchedPctRef.current[resId] || 0) >= 90) {
        if (resId) {
          completedResRef.current[resId] = true;
          resourceWatchedPctRef.current[resId] = 100;
          setResProgressMap(m => ({ ...m, [resId]: { pct: 100, completed: true } }));
        }
        checkLessonCompletion(resId, true);
        return () => window.removeEventListener("message", handleYtMessage);
      }
      let pingTicks = 0;
      const t = setInterval(() => {
        if (document.hidden) return;
        pingTicks += 1;
        if (!hasReceivedInfo || pingTicks % 15 === 0) {
          pingYt();
        }
        const state = ytStateRef.current[resId];
        if (state === 2) return;

        elapsed += 1;
        ytElapsedRef.current[resId] = elapsed;
        const pct = Math.min(100, Math.round((elapsed / dur) * 100));
        if (resId) {
          resourceWatchedPctRef.current[resId] = Math.max(resourceWatchedPctRef.current[resId] || 0, pct);
          setResProgressMap(m => ({ ...m, [resId]: { pct: resourceWatchedPctRef.current[resId], completed: resourceWatchedPctRef.current[resId] >= 90 || completedResRef.current[resId] } }));
        }
        if (elapsed >= threshold || (resourceWatchedPctRef.current[resId] || 0) >= 90) {
          clearInterval(t);
          if (resId) {
            completedResRef.current[resId] = true;
            resourceWatchedPctRef.current[resId] = 100;
            setResProgressMap(m => ({ ...m, [resId]: { pct: 100, completed: true } }));
          }
          checkLessonCompletion(resId, true);
        } else if (elapsed >= 1) {
          if (progRef.current[lid] !== "IN_PROGRESS" && progRef.current[lid] !== "COMPLETED") {
            progRef.current[lid] = "IN_PROGRESS";
          }
          checkLessonCompletion(resId, false);
        }
        if (elapsed > 0 && elapsed % 30 === 0) {
          updateProgress(resourceWatchedPctRef.current[resId] || pct, Math.floor(elapsed), cumDocSecRef.current[lid] || 0, false);
        }
      }, 1000);
      return () => {
        window.removeEventListener("message", handleYtMessage);
        clearInterval(t);
      };
    }, [activeL?.id, activeL?.durationSeconds, activeVideoRes?.id, activeVideoIdx, updateProgress, checkLessonCompletion]);

    /* ── Progress: Document & Image tracking (timer + scroll + completion) ── */
    useEffect(() => {
      if (!activeL || !viewRes || viewRes.resourceType === "VIDEO") return;
      const resId = viewRes.id;
      const lid = activeL.id;
      if (!resId) return;

      if (progRef.current[lid] !== "COMPLETED" && progRef.current[lid] !== "IN_PROGRESS") {
        progRef.current[lid] = "IN_PROGRESS";
        updateProgress(null, null, cumDocSecRef.current[lid] || 1, false);
      }

      if (completedResRef.current[resId]) {
        setResProgressMap(m => ({ ...m, [resId]: { completed: true, pct: 100 } }));
        checkLessonCompletion(resId, true);
        return;
      }

      if (viewRes.resourceType === "IMAGE") {
        completedResRef.current[resId] = true;
        setResProgressMap(m => ({ ...m, [resId]: { completed: true, pct: 100 } }));
        checkLessonCompletion(resId, true);
        return;
      }

      if (viewRes.resourceType === "DOC" || viewRes.resourceType === "PDF") {
        return;
      }

      const mainEl = document.querySelector('.lecture-wrap > div') || document.querySelector('.main > div');
      const mountedTime = Date.now();
      let hasScrolledOnce = false;
      let wheelTicks = 0;

      const checkScroll = (e?: any) => {
        if (!resId || completedResRef.current[resId]) return;
        const timeSinceMount = Date.now() - mountedTime;
        if (timeSinceMount < 2500 && !e) return;

        let reachedBottom = false;

        if (e && (e.type === 'wheel' || e.type === 'touchmove' || e.type === 'keydown')) {
          wheelTicks += 1;
        }

        if (mainEl && mainEl.scrollHeight > mainEl.clientHeight + 30) {
          if (mainEl.scrollTop > 20) hasScrolledOnce = true;
          if (mainEl.scrollTop + mainEl.clientHeight >= mainEl.scrollHeight - 60 && hasScrolledOnce) {
            reachedBottom = true;
          }
        }
        if (window.innerHeight < document.documentElement.scrollHeight - 30) {
          if (window.scrollY > 20) hasScrolledOnce = true;
          if (window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 60 && hasScrolledOnce) {
            reachedBottom = true;
          }
        }
        document.querySelectorAll('iframe').forEach(ifr => {
          try {
            const cw = ifr.contentWindow;
            if (cw && cw.document && cw.document.documentElement) {
              const d = cw.document.documentElement;
              if (d.scrollHeight > cw.innerHeight + 30) {
                if ((d.scrollTop || cw.scrollY) > 10) hasScrolledOnce = true;
                if ((d.scrollTop || cw.scrollY) + cw.innerHeight >= d.scrollHeight - 60 && hasScrolledOnce) {
                  reachedBottom = true;
                }
              }
            }
          } catch {
            if (timeSinceMount >= 3000 && wheelTicks >= 12) {
              reachedBottom = true;
            }
          }
        });
        if (timeSinceMount >= 3000 && wheelTicks >= 12) {
          if (mainEl && mainEl.scrollHeight <= mainEl.clientHeight + 30 && window.innerHeight >= document.documentElement.scrollHeight - 30) {
            reachedBottom = true;
          }
        }
        if (reachedBottom) {
          completedResRef.current[resId] = true;
          setResProgressMap(m => ({ ...m, [resId]: { completed: true, pct: 100 } }));
          checkLessonCompletion(resId, true);
        }
      };

      if (mainEl) {
        checkScroll();
        mainEl.addEventListener('scroll', checkScroll, { passive: true });
        mainEl.addEventListener('wheel', checkScroll, { passive: true });
        mainEl.addEventListener('touchmove', checkScroll, { passive: true });
      }
      window.addEventListener('scroll', checkScroll, { passive: true });
      window.addEventListener('resize', checkScroll);
      window.addEventListener('wheel', checkScroll, { passive: true });
      window.addEventListener('keydown', checkScroll, { passive: true });

      return () => {
        if (mainEl) {
          mainEl.removeEventListener('scroll', checkScroll);
          mainEl.removeEventListener('wheel', checkScroll);
          mainEl.removeEventListener('touchmove', checkScroll);
        }
        window.removeEventListener('scroll', checkScroll);
        window.removeEventListener('resize', checkScroll);
        window.removeEventListener('wheel', checkScroll);
        window.removeEventListener('keydown', checkScroll);
      };
    }, [activeL?.id, viewRes?.id, checkLessonCompletion, updateProgress]);

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
                      <div key={n.id} className="row gap-12" style={{ padding: "13px 16px", background: n.read ? "#fff" : "var(--accent-soft)", borderBottom: "1px solid var(--border)", cursor: "pointer" }} onClick={() => {
                         if (!n.read) markRead(n.id);
                         setNotifOpen(false);
                         const role = window.useAuthStore?.getState().role || 'student';
                         const targetUrl = window.getNotificationTargetUrl ? window.getNotificationTargetUrl(n, role) : '';
                         if (window.AppShell && typeof window.AppShell.go === 'function') {
                           const { routeKey, params } = window.parseNotificationUrl ? window.parseNotificationUrl(targetUrl) : { routeKey: '', params: {} };
                           window.AppShell.go(routeKey, Object.keys(params).length > 0 ? params : undefined);
                         } else {
                           window.location.href = targetUrl || '/notifications';
                         }
                        }}>
                        <div className="stat-ic" style={{ width: 38, height: 38, borderRadius: 10, background: meta.color + "1a", color: meta.color, flex: "none" }}><Ic n={meta.icon} size={18} /></div>
                        <div className="grow" style={{ minWidth: 0 }}>
                          <div className="row gap-6" style={{ marginBottom: 3 }}>
                            <span className="chip" style={{ background: meta.color + "1a", color: meta.color, borderColor: meta.color + "33", fontSize: 10, padding: "1px 5px" }}>{meta.label || 'Hệ thống'}</span>
                          </div>
                          <div className="t-sm" style={{ lineHeight: 1.4, fontWeight: n.read ? 400 : 600 }}>{n.title}</div>
                          {n.body && <div className="t-xs muted" style={{ marginTop: 3, lineHeight: 1.3, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{n.body}</div>}
                          <div className="t-xs dim" style={{ marginTop: n.body ? 3 : 4 }}>{timeAgo(n.createdAt)}</div>
                        </div>
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
              {activeView === "assignment" && selectedAssignmentId ? (
                window.AssignmentDetail && React.createElement(window.AssignmentDetail, {
                  assignmentId: selectedAssignmentId,
                  courseId,
                  role: "student",
                  onBack: () => {
                    setActiveView("lesson");
                    setSelectedAssignmentId(null);
                    setSidebarTab("lessons");
                  },
                })
              ) : !activeL ? (
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
                            isCompleted={resProgressMap[v.id]?.completed || completedResRef.current[v.id] || (resourceWatchedPctRef.current[v.id] || 0) >= 90}
                            onClick={() => {
                              setActiveVideoIdx(i);
                              if (activeL && progRef.current[activeL.id] !== "COMPLETED") {
                                if (progRef.current[activeL.id] !== "IN_PROGRESS") {
                                  progRef.current[activeL.id] = "IN_PROGRESS";
                                }
                                updateProgress(resourceWatchedPctRef.current[v.id] || null, null, cumDocSecRef.current[activeL.id] || 1, false);
                              }
                            }} />
                        ))}
                      </div>
                      {isVideoActive && (
                        <Viewer key={activeVideoRes?.id || videoUrl || activeVideoIdx}
                          res={activeVideoRes} url={videoUrl} onVideoTimeUpdate={handleVideoTimeUpdate}
                          isCompleted={activeVideoRes && (resProgressMap[activeVideoRes.id]?.completed || completedResRef.current[activeVideoRes.id] || (resourceWatchedPctRef.current[activeVideoRes.id] || 0) >= 90)}
                          onMarkCompleted={() => activeVideoRes && checkLessonCompletion(activeVideoRes.id, true)} />
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
                              isCompleted={resProgressMap[r.id]?.completed || completedResRef.current[r.id]}
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
                      <Viewer key={viewRes?.id || getViewerUrl(viewRes)}
                        res={viewRes} url={getViewerUrl(viewRes)}
                        isCompleted={resProgressMap[viewRes.id]?.completed || completedResRef.current[viewRes.id]}
                        onMarkCompleted={() => checkLessonCompletion(viewRes.id, true)} />
                    )}
                  </div>

                  {/* Prev / Next */}
                  {(activeView !== "assignment" && activeL) || (activeView === "assignment" && selectedAssignmentId) ? (
                  <div style={{ marginTop: 28, display: "flex", gap: 12 }}>
                    {activeView === "assignment" ? (
                      <>
                    <button disabled={!prevAssignment}
                      onClick={() => prevAssignment && setSelectedAssignmentId(prevAssignment.id)}
                      style={{ flex: 1, height: 48, display: "flex", alignItems: "center", gap: 10,
                        padding: "0 16px", border: "1px solid var(--border)", borderRadius: 12,
                        background: "#fff", cursor: prevAssignment ? "pointer" : "default",
                        opacity: prevAssignment ? 1 : 0.35 }}
                      onMouseEnter={e => { if (prevAssignment) e.currentTarget.style.borderColor = "#2563eb"; }}
                      onMouseLeave={e => { e.currentTarget.style.borderColor = "var(--border)"; }}>
                      <Ic n="arrow_left" size={15} style={{ color: "#94a3b8", flexShrink: 0 }} />
                      <div style={{ textAlign: "left", minWidth: 0 }}>
                        <div style={{ fontSize: 10.5, color: "#94a3b8", fontWeight: 500 }}>Bài tập trước</div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "#0f172a" }} className="truncate">
                          {prevAssignment ? prevAssignment.title : "—"}
                        </div>
                      </div>
                    </button>
                    <button disabled={!nextAssignment}
                      onClick={() => nextAssignment && setSelectedAssignmentId(nextAssignment.id)}
                      style={{ flex: 1, height: 48, display: "flex", alignItems: "center",
                        justifyContent: "flex-end", gap: 10, padding: "0 16px",
                        border: `1px solid ${nextAssignment ? "#2563eb" : "var(--border)"}`,
                        borderRadius: 12, background: nextAssignment ? "#eff6ff" : "#fff",
                        cursor: nextAssignment ? "pointer" : "default",
                        opacity: nextAssignment ? 1 : 0.35 }}
                      onMouseEnter={e => { if (nextAssignment) e.currentTarget.style.background = "#dbeafe"; }}
                      onMouseLeave={e => { e.currentTarget.style.background = nextAssignment ? "#eff6ff" : "#fff"; }}>
                      <div style={{ textAlign: "right", minWidth: 0 }}>
                        <div style={{ fontSize: 10.5, color: "#2563eb", fontWeight: 500 }}>Bài tập tiếp theo</div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "#0f172a" }} className="truncate">
                          {nextAssignment ? nextAssignment.title : "—"}
                        </div>
                      </div>
                      <Ic n="arrow_right" size={15} style={{ color: "#2563eb", flexShrink: 0 }} />
                    </button>
                      </>
                    ) : (
                      <>
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
                      </>
                    )}
                  </div>
                  ) : null}
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

              {/* Sidebar tab bar */}
              <div style={{ flexShrink: 0, display: "flex", borderBottom: "1px solid rgba(255,255,255,.1)", marginBottom: 12 }}>
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
                        color: isAct ? "#10b981" : "#64748b",
                        borderBottom: `2px solid ${isAct ? "#10b981" : "transparent"}`,
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
                minHeight: 0, overflow: "hidden" }}>

                {sidebarTab === "lessons" ? (
                  <div style={{ flex: 1, overflowY: "auto", minHeight: 0 }}>
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
                                    setConfirmQuiz(l);
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

                ) : (
                  <>
                  <div style={{ flexShrink: 0, display: "flex", alignItems: "center",
                    gap: 6, padding: "0 0 8px" }}>
                    <span style={{ fontSize: 12, fontWeight: 600, color: "#e2e8f0", flex: 1 }}>
                      Bài tập
                      {assignments.length > 0 && (
                        <span style={{ fontWeight: 400, color: "#94a3b8", marginLeft: 4 }}>
                          ({assignments.length})
                        </span>
                      )}
                    </span>
                  </div>

                  <div style={{ flex: 1, minHeight: 0, overflowY: "auto" }}>
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
                              background: selectedAssignmentId === a.id ? "rgba(16,185,129,.13)" : "transparent" }}
                            onMouseEnter={e => { e.currentTarget.style.background = "rgba(255,255,255,.03)"; }}
                            onMouseLeave={e => { e.currentTarget.style.background = selectedAssignmentId === a.id ? "rgba(16,185,129,.13)" : "transparent"; }}>
                            <div style={{ width: 26, height: 26, borderRadius: 7, flexShrink: 0,
                              background: "#fef3c7", color: "#d97706",
                              display: "grid", placeItems: "center" }}>
                              <Ic n="clipboard" size={12} />
                            </div>
                            <div style={{ flex: 1, minWidth: 0 }}>
                              <div style={{ fontSize: 12.5, fontWeight: 600, color: "#e2e8f0",
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

        <QuizConfirmModal
          lesson={confirmQuiz}
          courseId={courseId}
          onClose={() => setConfirmQuiz(null)}
          onStart={() => {
            const url = `/player/quiz?courseId=${courseId}&quizId=${confirmQuiz.quizId}&from=lecture&lessonId=${confirmQuiz.id}`;
            setConfirmQuiz(null);
            if (navigate) navigate(url); else window.location.href = url;
          }}
        />
      </div>
    );
  }

  Object.assign(window, { LecturePlayer });
})();