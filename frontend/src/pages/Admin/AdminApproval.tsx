// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Phê duyệt Khóa học (+ popup chi tiết / xem trước / từ chối)
   ============================================================ */
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Avatar, Status, StatCard, Search, Tabs, Select, Section, Modal, ModalHead } = window;
  const http = window.httpClient;

  function fmtDate(d) {
    if (!d) return "-";
    return new Date(d).toLocaleDateString("vi-VN");
  }

  function mapStatus(s) {
    if (!s) return "pending";
    if (s === "PENDING" || s === "PENDING_UPDATE") return "pending";
    if (s === "PUBLISHED" || s === "APPROVED") return "approved";
    if (s === "REJECTED") return "rejected";
    return "pending";
  }

  function isUpdate(course) {
    return course?.status === "PENDING_UPDATE";
  }

  function countLessons(course) {
    if (!course?.chapters) return course?.lessonCount || 0;
    return course.chapters.reduce((s, ch) => s + (ch.lessons?.length || 0), 0);
  }

  /* ---------------- Detail modal content ---------------- */
  function DetailModal({ detail, onClose, onApprove, onReject, onPreview }) {
    const [imgFull, setImgFull] = useState(null);
    const [view, setView]       = useState("overview"); // "overview" | "changes"
    const isPendUpd = isUpdate(detail);
    const isPending = mapStatus(detail.status) === "pending";

    const allLessons   = (detail.chapters || []).flatMap(ch => ch.lessons || []);
    const allResources = allLessons.flatMap(l => l.resources || []);

    // Live counts (không tính draft/pendingDelete)
    const liveChapters = (detail.chapters || []).filter(ch => !ch.isDraft && !ch.pendingDelete);
    const liveLessons  = allLessons.filter(l => !l.isDraft && !l.pendingDelete);

    const typeColor = { PDF: "#ef4444", DOC: "#2563eb", SLIDE: "#f59e0b", IMAGE: "#10b981" };
    const typeBg   = { PDF: "#fdecec", DOC: "#eaf1ff", SLIDE: "#fef5e6", IMAGE: "#e7f8f0" };

    // Diff tổng hợp
    const draftChanges = isPendUpd ? [
      detail.draftTitle        && { label: "Tên mới",     old: detail.title,          next: detail.draftTitle },
      detail.draftLevel        && { label: "Cấp độ mới",  old: detail.level,          next: detail.draftLevel },
      detail.draftDescription  && { label: "Mô tả mới",   old: detail.description,    next: detail.draftDescription },
      detail.draftThumbnailUrl && { label: "Thumbnail",   old: detail.thumbnailUrl,   next: detail.draftThumbnailUrl, isImg: true },
    ].filter(Boolean) : [];

    const newChapters      = isPendUpd ? (detail.chapters || []).filter(ch => ch.isDraft)                              : [];
    const delChapters      = isPendUpd ? (detail.chapters || []).filter(ch => ch.pendingDelete)                         : [];
    const newLessons       = isPendUpd ? allLessons.filter(l => l.isDraft)                                              : [];
    const delLessons       = isPendUpd ? allLessons.filter(l => l.pendingDelete)                                        : [];
    const renamedLessons   = isPendUpd ? allLessons.filter(l => !l.isDraft && l.draftTitle)                             : [];
    const contentChangedLessons = isPendUpd ? allLessons.filter(l => !l.isDraft && !l.draftTitle && l.draftContentText) : [];
    const thumbnailDiff = draftChanges.find(d => d.isImg) || null;
    const hasDiff = draftChanges.length || newChapters.length || delChapters.length ||
                    newLessons.length || delLessons.length || renamedLessons.length || contentChangedLessons.length;

    // Banner thumbnail: dùng draft nếu có
    const bannerUrl = (isPendUpd && detail.draftThumbnailUrl) ? detail.draftThumbnailUrl : (detail.thumbnailUrl || "");

    return <>
      {/* Banner */}
      <div style={{ height: 190, background: "#0f172a", borderRadius: "18px 18px 0 0", position: "relative" }}>
        <button className="icon-btn" style={{ position: "absolute", right: 14, top: 14, width: 36, height: 36, background: "rgba(15,23,42,.6)", color: "#fff", border: "none", zIndex: 2 }} onClick={onClose}>
          <Ic n="x" size={18} />
        </button>
        <span style={{ position: "absolute", left: 16, bottom: 14, zIndex: 2 }}>
          <span className="chip" style={{ background: "rgba(15,23,42,.72)", color: "#fff" }}>{detail.category?.name || "-"}</span>
          {isPendUpd && detail.draftThumbnailUrl && (
            <span className="chip" style={{ background: "#0369a1", color: "#fff", marginLeft: 6, fontSize: 10.5 }}>Thumbnail mới</span>
          )}
        </span>
        {(() => {
          const url = bannerUrl;
          const ext = url.split(".").pop()?.toLowerCase().split("?")[0] || "";
          const isImg   = ["jpg","jpeg","png","gif","webp","svg"].includes(ext);
          const isVideo = ["mp4","webm","mov","avi"].includes(ext);
          const isSlide = ["ppt","pptx"].includes(ext);

          if (isImg) return (
            <img src={url} alt="" onClick={() => setImgFull(url)}
              style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover", opacity: 0.8, cursor: "zoom-in" }} />
          );
          if (isVideo) return (
            <video src={url} muted style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover", opacity: 0.7 }} />
          );
          if (isSlide) return (
            <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 6 }}>
              <div style={{ width: 52, height: 52, borderRadius: 12, background: "#fef5e6", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <Ic n="layers" size={26} style={{ color: "#f59e0b" }} />
              </div>
              <span style={{ color: "#94a3b8", fontSize: 12 }}>Trình chiếu</span>
            </div>
          );
          return (
            <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center" }}>
              <button onClick={onPreview}
                style={{ width: 64, height: 64, borderRadius: 999, border: "none", background: "rgba(255,255,255,.92)", color: "var(--primary)", display: "grid", placeItems: "center", cursor: "pointer", boxShadow: "0 8px 24px rgba(0,0,0,.3)" }}>
                <Ic n="play" size={26} fill="currentColor" />
              </button>
            </div>
          );
        })()}
        <button onClick={onPreview}
          style={{ position: "absolute", right: 16, bottom: 14, background: "rgba(15,23,42,.72)", color: "#fff", border: "none", borderRadius: 999, padding: "4px 10px", fontSize: 12, display: "flex", alignItems: "center", gap: 5, cursor: "pointer", zIndex: 2 }}>
          <Ic n="eye" size={13} />Xem trước
        </button>
      </div>

      {/* Body */}
      <div className="modal-body" style={{ overflowY: "auto", flex: 1 }}>
        <h2 className="t-h2" style={{ margin: "0 0 8px" }}>
          {isPendUpd && detail.draftTitle
            ? <><span style={{ textDecoration: "line-through", color: "#94a3b8", fontSize: "0.85em" }}>{detail.title}</span>{" "}<span style={{ color: "#16a34a" }}>{detail.draftTitle}</span></>
            : detail.title}
        </h2>
        <div className="row gap-10" style={{ marginBottom: 14 }}>
          <Avatar name={detail.instructorName || "?"} size={34} />
          <div>
            <div style={{ fontWeight: 600, fontSize: 14 }}>{detail.instructorName}</div>
            <div className="t-xs muted">Gửi ngày {fmtDate(detail.submittedAt || detail.createdAt)}</div>
          </div>
        </div>

        {/* Tabs chỉ hiện khi PENDING_UPDATE */}
        {isPendUpd && (
          <div className="tabs" style={{ marginBottom: 16, width: "fit-content" }}>
            <button className={view === "overview" ? "on" : ""} onClick={() => setView("overview")}>Tổng quan</button>
            <button className={view === "changes" ? "on" : ""} onClick={() => setView("changes")}>
              Thay đổi{hasDiff ? ` (${[draftChanges.length, newChapters.length + delChapters.length, newLessons.length + delLessons.length, renamedLessons.length + contentChangedLessons.length].reduce((a,b)=>a+b,0)})` : ""}
            </button>
          </div>
        )}

        {/* ── VIEW: TỔNG QUAN ── */}
        {(view === "overview" || !isPendUpd) && <>
          {/* Stats */}
          <div className="grid grid-2" style={{ gap: 12, marginBottom: 18 }}>
            {[
              { l: "Số bài giảng", v: isPendUpd ? `${liveLessons.length}${newLessons.length ? ` (+${newLessons.length})` : ""}${delLessons.length ? ` (−${delLessons.length})` : ""}` : allLessons.length, ic: "book" },
              { l: "Số chương",    v: isPendUpd ? `${liveChapters.length}${newChapters.length ? ` (+${newChapters.length})` : ""}${delChapters.length ? ` (−${delChapters.length})` : ""}` : (detail.chapters?.length || 0), ic: "layers" },
              { l: "Danh mục",     v: detail.category?.name || "-", ic: "folder" },
              { l: "Tài liệu",     v: allResources.length + " tệp", ic: "paperclip" },
            ].map((s, i) => (
              <div key={i} className="row gap-10" style={{ padding: 12, background: "var(--surface-2)", borderRadius: 11 }}>
                <div className="stat-ic" style={{ width: 38, height: 38, borderRadius: 10, background: "#fff", color: "var(--accent)" }}>
                  <Ic n={s.ic} size={18} />
                </div>
                <div>
                  <div className="t-xs muted">{s.l}</div>
                  <div style={{ fontWeight: 700, fontSize: 14 }}>{s.v}</div>
                </div>
              </div>
            ))}
          </div>

          {/* Chapters & Lessons */}
          {(detail.chapters || []).map((ch, ci) => {
            const chIsNew = !!ch.isDraft;
            const chIsDel = !!ch.pendingDelete;
            return (
              <div key={ch.id || ci} style={{
                marginBottom: 18,
                ...(chIsNew && { padding: "10px 12px", borderRadius: 10, border: "2px solid #86efac", background: "#f0fdf4" }),
                ...(chIsDel && { padding: "10px 12px", borderRadius: 10, border: "2px dashed #fca5a5", background: "#fff5f5", opacity: 0.7 }),
              }}>
                <div className="between" style={{ marginBottom: 9 }}>
                  <div className="row gap-7" style={{ alignItems: "center" }}>
                    {chIsNew && <span className="chip" style={{ background: "#dcfce7", color: "#16a34a", fontSize: 10, padding: "1px 7px" }}>MỚI</span>}
                    {chIsDel && <span className="chip" style={{ background: "#fee2e2", color: "#dc2626", fontSize: 10, padding: "1px 7px" }}>CHỜ XÓA</span>}
                    <div className="t-label" style={{ margin: 0, textDecoration: chIsDel ? "line-through" : "none" }}>
                      Chương {ci + 1}: {ch.title}
                    </div>
                  </div>
                  <span className="t-xs dim">{ch.lessons?.length || 0} bài</span>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {(ch.lessons || []).map((l, li) => {
                    const isVideo  = l.lessonType === "VIDEO";
                    const lIsNew   = !!l.isDraft;
                    const lIsDel   = !!l.pendingDelete;
                    const lRenamed = !l.isDraft && !!l.draftTitle;
                    const lChanged = !l.isDraft && !l.draftTitle && !!l.draftContentText;
                    return (
                      <div key={l.id || li} className="row gap-11" style={{
                        padding: 10, borderRadius: 11,
                        border: lIsNew ? "1px solid #86efac" : lIsDel ? "1px dashed #fca5a5" : lRenamed || lChanged ? "1px solid #93c5fd" : "1px solid var(--border)",
                        background: lIsNew ? "#f0fdf4" : lIsDel ? "#fff5f5" : lRenamed || lChanged ? "#eff6ff" : undefined,
                        opacity: lIsDel ? 0.6 : 1,
                      }}>
                        <div style={{ width: 56, height: 38, borderRadius: 8, flex: "none", background: "#0f172a", display: "grid", placeItems: "center" }}>
                          <Ic n={isVideo ? "play" : "file_text"} size={15} fill={isVideo ? "#fff" : undefined} style={{ color: "#fff" }} />
                        </div>
                        <div className="grow" style={{ minWidth: 0 }}>
                          <div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">
                            {lIsDel    ? <span style={{ textDecoration: "line-through" }}>{l.title}</span>
                            : lRenamed ? <><span style={{ textDecoration: "line-through", color: "#94a3b8" }}>{l.title}</span>{" → "}<span style={{ color: "#16a34a" }}>{l.draftTitle}</span></>
                            : l.title}
                          </div>
                          <div className="t-xs muted row gap-5" style={{ marginTop: 2 }}>
                            {lIsNew   && <span style={{ color: "#16a34a", fontWeight: 600 }}>MỚI • </span>}
                            {lIsDel   && <span style={{ color: "#dc2626", fontWeight: 600 }}>CHỜ XÓA • </span>}
                            {lChanged && <span style={{ color: "#2563eb", fontWeight: 600 }}>ĐỔI NỘI DUNG • </span>}
                            <Ic n={isVideo ? "video" : "file_text"} size={12} />
                            {isVideo ? "Video" : "Văn bản"}
                            {l.resources?.length > 0 && <> • <Ic n="paperclip" size={12} />{l.resources.length} tài liệu</>}
                          </div>
                        </div>
                        <button className="btn btn-soft btn-sm" style={{ flex: "none" }} onClick={onPreview}>
                          <Ic n="eye" size={14} />Xem
                        </button>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}

          {/* Resources */}
          {allResources.length > 0 && <>
            <div className="between" style={{ marginBottom: 9 }}>
              <div className="t-label" style={{ margin: 0 }}>Tài liệu đính kèm</div>
              <span className="t-xs dim">{allResources.length} tệp</span>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 18 }}>
              {allResources.map((r, i) => (
                <div key={r.id || i} className="row gap-11" style={{ padding: 10, border: "1px solid var(--border)", borderRadius: 11 }}>
                  <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 10, flex: "none", background: typeBg[r.resourceType] || "var(--surface-2)", color: typeColor[r.resourceType] || "#64748b" }}>
                    <Ic n="file" size={19} />
                  </div>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">{r.displayName || r.originalFilename}</div>
                    <div className="t-xs muted">{r.resourceType}</div>
                  </div>
                </div>
              ))}
            </div>
          </>}

          {/* Description */}
          {detail.description && <>
            <div className="t-label" style={{ marginBottom: 7 }}>Mô tả khóa học</div>
            <p className="muted t-sm" style={{ margin: 0, lineHeight: 1.6 }}>
              {isPendUpd && detail.draftDescription
                ? <><span style={{ textDecoration: "line-through", color: "#94a3b8" }}>{detail.description}</span><br /><span style={{ color: "#16a34a", fontWeight: 500 }}>{detail.draftDescription}</span></>
                : detail.description}
            </p>
          </>}
        </>}

        {/* ── VIEW: THAY ĐỔI ── */}
        {view === "changes" && isPendUpd && (
          <div>
            {detail.changeSummary && (
              <div style={{ marginBottom: 14, padding: "10px 14px", borderRadius: 10, background: "#f0f9ff", border: "1px solid #bae6fd" }}>
                <div className="t-xs muted" style={{ marginBottom: 3 }}>Ghi chú từ giảng viên</div>
                <div style={{ fontSize: 13, color: "#0369a1" }}>{detail.changeSummary}</div>
              </div>
            )}

            {!hasDiff && (
              <div style={{ padding: "24px 0", textAlign: "center", color: "var(--text-3)" }}>
                <Ic n="check_circle" size={32} style={{ color: "#10b981", marginBottom: 8 }} />
                <div className="t-sm">Không có thay đổi về nội dung</div>
                <div className="t-xs muted">Có thể có thay đổi về tài nguyên bên trong bài giảng.</div>
              </div>
            )}

            {/* Metadata */}
            {draftChanges.filter(d => !d.isImg).map((d, i) => (
              <div key={i} style={{ marginBottom: 10, borderRadius: 10, overflow: "hidden", border: "1px solid #e2e8f0" }}>
                <div style={{ padding: "6px 12px", background: "var(--surface-2)", fontSize: 11, fontWeight: 600, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>{d.label}</div>
                <div style={{ padding: "10px 12px", background: "#fff5f5", borderBottom: "1px solid #e2e8f0" }}>
                  <div className="t-xs muted" style={{ marginBottom: 3 }}>Cũ</div>
                  <div style={{ color: "#dc2626", fontSize: 13, textDecoration: "line-through" }}>{d.old || <em style={{ color: "#94a3b8" }}>trống</em>}</div>
                </div>
                <div style={{ padding: "10px 12px", background: "#f0fdf4" }}>
                  <div className="t-xs muted" style={{ marginBottom: 3 }}>Mới</div>
                  <div style={{ color: "#16a34a", fontSize: 13, fontWeight: 600 }}>{d.next}</div>
                </div>
              </div>
            ))}

            {/* Thumbnail */}
            {thumbnailDiff && (
              <div style={{ marginBottom: 10, borderRadius: 10, overflow: "hidden", border: "1px solid #e2e8f0" }}>
                <div style={{ padding: "6px 12px", background: "var(--surface-2)", fontSize: 11, fontWeight: 600, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>Thumbnail</div>
                <div style={{ padding: 12, display: "flex", gap: 12, alignItems: "center", background: "#fff" }}>
                  <div style={{ flex: 1, textAlign: "center" }}>
                    <div className="t-xs muted" style={{ marginBottom: 6 }}>Cũ</div>
                    {thumbnailDiff.old
                      ? <img src={thumbnailDiff.old} alt="" style={{ width: "100%", maxWidth: 160, height: 90, objectFit: "cover", borderRadius: 8, border: "1px solid var(--border)" }} />
                      : <div style={{ width: 160, height: 90, borderRadius: 8, background: "#f1f5f9", display: "grid", placeItems: "center", margin: "0 auto" }}><Ic n="image" size={24} style={{ color: "#94a3b8" }} /></div>}
                  </div>
                  <Ic n="chevron_right" size={20} style={{ color: "#94a3b8", flex: "none" }} />
                  <div style={{ flex: 1, textAlign: "center" }}>
                    <div className="t-xs muted" style={{ marginBottom: 6 }}>Mới</div>
                    <img src={thumbnailDiff.next} alt="" style={{ width: "100%", maxWidth: 160, height: 90, objectFit: "cover", borderRadius: 8, border: "2px solid #86efac" }} />
                  </div>
                </div>
              </div>
            )}

            {/* Chapters added/removed */}
            {(newChapters.length > 0 || delChapters.length > 0) && (
              <div style={{ marginBottom: 10, borderRadius: 10, border: "1px solid #e2e8f0", overflow: "hidden" }}>
                <div style={{ padding: "6px 12px", background: "var(--surface-2)", fontSize: 11, fontWeight: 600, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>Chương</div>
                <div style={{ padding: "10px 12px", display: "flex", flexDirection: "column", gap: 6 }}>
                  {newChapters.map(ch => (
                    <div key={ch.id} className="row gap-8" style={{ padding: "6px 10px", borderRadius: 7, background: "#f0fdf4", border: "1px solid #bbf7d0" }}>
                      <span style={{ color: "#16a34a", fontWeight: 700, fontSize: 13 }}>+</span>
                      <span style={{ fontSize: 13, color: "#166534" }}>{ch.title}</span>
                    </div>
                  ))}
                  {delChapters.map(ch => (
                    <div key={ch.id} className="row gap-8" style={{ padding: "6px 10px", borderRadius: 7, background: "#fff5f5", border: "1px solid #fecaca" }}>
                      <span style={{ color: "#dc2626", fontWeight: 700, fontSize: 13 }}>−</span>
                      <span style={{ fontSize: 13, color: "#991b1b", textDecoration: "line-through" }}>{ch.title}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Lessons added/removed */}
            {(newLessons.length > 0 || delLessons.length > 0) && (
              <div style={{ marginBottom: 10, borderRadius: 10, border: "1px solid #e2e8f0", overflow: "hidden" }}>
                <div style={{ padding: "6px 12px", background: "var(--surface-2)", fontSize: 11, fontWeight: 600, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>Bài giảng thêm / xóa</div>
                <div style={{ padding: "10px 12px", display: "flex", flexDirection: "column", gap: 6 }}>
                  {newLessons.map(l => (
                    <div key={l.id} className="row gap-8" style={{ padding: "6px 10px", borderRadius: 7, background: "#f0fdf4", border: "1px solid #bbf7d0" }}>
                      <span style={{ color: "#16a34a", fontWeight: 700, fontSize: 13 }}>+</span>
                      <span style={{ fontSize: 13, color: "#166534" }}>{l.title}</span>
                    </div>
                  ))}
                  {delLessons.map(l => (
                    <div key={l.id} className="row gap-8" style={{ padding: "6px 10px", borderRadius: 7, background: "#fff5f5", border: "1px solid #fecaca" }}>
                      <span style={{ color: "#dc2626", fontWeight: 700, fontSize: 13 }}>−</span>
                      <span style={{ fontSize: 13, color: "#991b1b", textDecoration: "line-through" }}>{l.title}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Renamed lessons */}
            {renamedLessons.map(l => (
              <div key={l.id} style={{ marginBottom: 10, borderRadius: 10, overflow: "hidden", border: "1px solid #bae6fd" }}>
                <div style={{ padding: "6px 12px", background: "#e0f2fe", fontSize: 11, fontWeight: 600, color: "#0369a1", textTransform: "uppercase", letterSpacing: "0.05em" }}>Đổi tên bài: {l.title}</div>
                <div style={{ padding: "8px 12px", background: "#fff5f5", borderBottom: "1px solid #e2e8f0" }}>
                  <div className="t-xs muted" style={{ marginBottom: 2 }}>Tên cũ</div>
                  <div style={{ color: "#dc2626", fontSize: 13, textDecoration: "line-through" }}>{l.title}</div>
                </div>
                <div style={{ padding: "8px 12px", background: "#f0fdf4" }}>
                  <div className="t-xs muted" style={{ marginBottom: 2 }}>Tên mới</div>
                  <div style={{ color: "#16a34a", fontSize: 13, fontWeight: 600 }}>{l.draftTitle}</div>
                </div>
                {l.draftContentText && (
                  <div style={{ padding: "8px 12px", background: "#f0fdf4", borderTop: "1px solid #bbf7d0" }}>
                    <div className="t-xs muted" style={{ marginBottom: 4 }}>Nội dung mới</div>
                    <div style={{ fontSize: 12, color: "#166534", maxHeight: 120, overflowY: "auto", whiteSpace: "pre-wrap", lineHeight: 1.6 }}>{l.draftContentText}</div>
                  </div>
                )}
              </div>
            ))}

            {/* Content changed lessons */}
            {contentChangedLessons.map(l => (
              <div key={l.id} style={{ marginBottom: 10, borderRadius: 10, overflow: "hidden", border: "1px solid #fde68a" }}>
                <div style={{ padding: "6px 12px", background: "#fef9c3", fontSize: 11, fontWeight: 600, color: "#854d0e", textTransform: "uppercase", letterSpacing: "0.05em" }}>Đổi nội dung bài: {l.title}</div>
                <div style={{ padding: "10px 12px", background: "#fff5f5", borderBottom: "1px solid #e2e8f0" }}>
                  <div className="t-xs muted" style={{ marginBottom: 4 }}>Nội dung cũ</div>
                  <div style={{ fontSize: 12, color: "#dc2626", maxHeight: 120, overflowY: "auto", whiteSpace: "pre-wrap", lineHeight: 1.6 }}>
                    {l.contentText || <em style={{ color: "#94a3b8" }}>trống</em>}
                  </div>
                </div>
                <div style={{ padding: "10px 12px", background: "#f0fdf4" }}>
                  <div className="t-xs muted" style={{ marginBottom: 4 }}>Nội dung mới</div>
                  <div style={{ fontSize: 12, color: "#166534", maxHeight: 120, overflowY: "auto", whiteSpace: "pre-wrap", lineHeight: 1.6 }}>{l.draftContentText}</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Footer */}
      {isPending && !isPendUpd && (
        <div className="modal-foot">
          <button className="btn btn-danger" onClick={onReject}><Ic n="x" size={16} />Từ chối</button>
          <button className="btn btn-success" onClick={onApprove}><Ic n="check" size={16} />Phê duyệt & Xuất bản</button>
        </div>
      )}
      {isPendUpd && (
        <div className="modal-foot">
          <button className="btn btn-danger" onClick={onReject}><Ic n="x" size={16} />Từ chối cập nhật</button>
          <button className="btn btn-success" onClick={onApprove}><Ic n="check" size={16} />Áp dụng cập nhật</button>
        </div>
      )}

      {/* Lightbox */}
      {imgFull && (
        <div onClick={() => setImgFull(null)}
          style={{ position: "fixed", inset: 0, zIndex: 400, background: "rgba(0,0,0,.88)", display: "flex", alignItems: "center", justifyContent: "center", cursor: "zoom-out" }}>
          <img src={imgFull} alt="" style={{ maxWidth: "90vw", maxHeight: "90vh", borderRadius: 12, boxShadow: "0 24px 64px rgba(0,0,0,.6)", objectFit: "contain" }} />
          <button onClick={() => setImgFull(null)}
            style={{ position: "absolute", top: 16, right: 16, width: 40, height: 40, borderRadius: 999, border: "none", background: "rgba(255,255,255,.15)", color: "#fff", cursor: "pointer", display: "grid", placeItems: "center" }}>
            <Ic n="x" size={20} />
          </button>
        </div>
      )}
    </>;
  }

  /* ---------------- Course approval ---------------- */
  function AdminApproval({ nav }) {
    const [tab, setTab] = useState("pending");
    const [list, setList] = useState([]);
    const [loading, setLoading] = useState(false);
    const [stats, setStats] = useState({ pending: 0, approved: 0, rejected: 0 });
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [qSearch, setQSearch] = useState("");

    const [detail, setDetail] = useState(null);

    const [reject, setReject] = useState(null);
    const [rejectSelect, setRejectSelect] = useState("Nội dung chưa đầy đủ");
    const [rejectNote, setRejectNote] = useState("");
    const [rejectLoading, setRejectLoading] = useState(false);
    const [showPreview, setShowPreview] = useState(false);

    useEffect(() => { loadList(); }, [tab, page]);

    async function loadList() {
      setLoading(true);
      try {
        let url;
        if (tab === "pending") {
          url = `/admin/courses/pending?page=${page}&size=10`;
        } else {
          url = `/admin/courses?page=${page}&size=10`;
        }
        const res = await http.get(url);
        const data = res.data;
        let items = data.content || data || [];

        // Filter theo tab trên frontend (backend trả tất cả)
        if (tab === "approved") items = items.filter(c => c.status === "PUBLISHED");
        if (tab === "rejected") items = items.filter(c => c.status === "REJECTED");

        setList(items);
        setTotalPages(data.totalPages || 1);
        if (tab === "pending")  setStats(st => ({ ...st, pending:  data.totalElements || items.length }));
        if (tab === "approved") setStats(st => ({ ...st, approved: items.length }));
        if (tab === "rejected") setStats(st => ({ ...st, rejected: items.length }));
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    }

    async function openDetail(item) {
      setDetail({ ...item, _loading: true });
      try {
        const res = await http.get(`/admin/courses/${item.id}`);
        setDetail(res.data);
      } catch (e) {
        console.error(e);
        setDetail(null);
      }
    }

    async function handleApprove(id, courseStatus) {
      const endpoint = courseStatus === "PENDING_UPDATE"
        ? `/admin/courses/${id}/approve-update`
        : `/admin/courses/${id}/approve`;
      try {
        await http.post(endpoint);
        setList(l => l.filter(c => c.id !== id));
        setStats(st => ({ ...st, pending: Math.max(0, st.pending - 1), approved: st.approved + 1 }));
        setDetail(null);
      } catch (e) {
        alert("Lỗi khi phê duyệt: " + (e?.response?.data?.message || e.message));
      }
    }

    async function handleReject() {
      const reason = rejectNote.trim() || rejectSelect;
      if (!reason) return;
      setRejectLoading(true);
      const endpoint = isUpdate(reject)
        ? `/admin/courses/${reject.id}/reject-update`
        : `/admin/courses/${reject.id}/reject`;
      try {
        await http.post(endpoint, { reason });
        setList(l => l.filter(c => c.id !== reject.id));
        setStats(st => ({ ...st, pending: Math.max(0, st.pending - 1), rejected: st.rejected + 1 }));
        setReject(null);
        setRejectNote("");
      } catch (e) {
        alert("Lỗi khi từ chối: " + (e?.response?.data?.message || e.message));
      } finally {
        setRejectLoading(false);
      }
    }

    let filtered = list;
    if (qSearch) filtered = list.filter(a =>
      (a.title || "").toLowerCase().includes(qSearch.toLowerCase()) ||
      (a.instructorName || "").toLowerCase().includes(qSearch.toLowerCase())
    );

    return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Phê duyệt Khóa học</h1><p>Kiểm duyệt nội dung khóa học do giảng viên gửi trước khi xuất bản.</p></div>
        <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns: "repeat(3,1fr)" }}>
          <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value={String(stats.pending)} label="Đang chờ duyệt" />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={String(stats.approved)} label="Đã duyệt" />
          <StatCard icon="x" iconBg="#fdecec" iconColor="#dc2626" value={String(stats.rejected)} label="Đã từ chối" />
        </div>
        <div className="toolbar">
          <Tabs items={[{v:"pending",label:"Chờ duyệt"},{v:"approved",label:"Đã duyệt"},{v:"rejected",label:"Từ chối"},{v:"all",label:"Tất cả"}]} value={tab} onChange={v => { setTab(v); setPage(0); }} />
          <div className="grow" />
          <Search placeholder="Tìm khóa học, giảng viên..." value={qSearch} onChange={setQSearch} style={{ width: 280, flex: "none" }} />
        </div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Khóa học</th><th>Giảng viên</th><th>Danh mục</th><th>Số bài</th><th>Ngày gửi</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>{loading ? (
              <tr><td colSpan={7} style={{ textAlign: "center", padding: 32, color: "var(--text-3)" }}>Đang tải...</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={7} style={{ textAlign: "center", padding: 32, color: "var(--text-3)" }}>Không có dữ liệu</td></tr>
            ) : filtered.map(a => {
              const st = mapStatus(a.status);
              return (
                <tr key={a.id}>
                  <td><b style={{ fontSize: 14, maxWidth: 240, display: "block" }} className="truncate">{a.title}</b></td>
                  <td><div className="row gap-9"><Avatar name={a.instructorName || "?"} size={30} /><span className="t-sm">{a.instructorName}</span></div></td>
                  <td><span className="chip chip-neutral">{a.category?.name || "-"}</span></td>
                  <td><b>{a.lessonCount ?? countLessons(a)}</b></td>
                  <td className="muted">{fmtDate(a.submittedAt || a.createdAt)}</td>
                  <td>
                    <div className="row gap-6 wrap">
                      <Status s={st} />
                      {a.status === "PENDING_UPDATE" && <span className="chip" style={{ background: "#e0f2fe", color: "#0284c7", fontSize: 10.5 }}>Cập nhật</span>}
                    </div>
                  </td>
                  <td>{st === "pending"
                    ? <div className="row gap-6">
                        <button className="btn btn-soft btn-sm" onClick={() => openDetail(a)}>Xem</button>
                        <button className="btn btn-success btn-sm" onClick={() => handleApprove(a.id, a.status)}><Ic n="check" size={15} /></button>
                        <button className="btn btn-danger btn-sm" onClick={() => { setReject(a); setRejectNote(""); setRejectSelect("Nội dung chưa đầy đủ"); }}><Ic n="x" size={15} /></button>
                      </div>
                    : <button className="btn btn-ghost btn-sm" onClick={() => openDetail(a)}>Chi tiết</button>
                  }</td>
                </tr>
              );
            })}</tbody>
          </table></div>
        </Section>
        {totalPages > 1 && (
          <div className="row gap-8" style={{ justifyContent: "center", marginTop: 16 }}>
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>‹ Trước</button>
            <span className="t-sm muted">Trang {page + 1} / {totalPages}</span>
            <button className="btn btn-ghost btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Sau ›</button>
          </div>
        )}

        {/* Detail modal */}
        <Modal open={!!detail} onClose={() => setDetail(null)} max={620} maxHeight="calc(100vh - 48px)">
          {detail && (detail._loading
            ? <div style={{ padding: 64, textAlign: "center", color: "var(--text-3)" }}>Đang tải...</div>
            : <DetailModal
                detail={detail}
                onClose={() => setDetail(null)}
                onApprove={() => handleApprove(detail.id, detail.status)}
                onReject={() => { setReject(detail); setDetail(null); setRejectNote(""); setRejectSelect("Nội dung chưa đầy đủ"); }}
                onPreview={() => { setDetail(null); window.__previewCourse = { courseId: detail.id, role: "admin" }; setShowPreview(true); }}
              />
          )}
        </Modal>

        {/* Reject modal */}
        <Modal open={!!reject} onClose={() => setReject(null)} max={480}>
          {reject && <>
            <ModalHead title={isUpdate(reject) ? "Từ chối cập nhật" : "Từ chối khóa học"} sub={reject.title || reject.course} icon="x" iconBg="#fdecec" iconColor="#dc2626" onClose={() => setReject(null)} />
            <div className="modal-body">
              <div className="t-label" style={{ marginBottom: 7 }}>Lý do từ chối</div>
              <Select value={rejectSelect} onChange={setRejectSelect} options={[
                { v: "Nội dung chưa đầy đủ", label: "Nội dung chưa đầy đủ" },
                { v: "Vi phạm bản quyền", label: "Vi phạm bản quyền" },
                { v: "Chất lượng video kém", label: "Chất lượng video kém" },
                { v: "Lý do khác", label: "Lý do khác" },
              ]} />
              <div className="t-label" style={{ margin: "16px 0 7px" }}>Ghi chú cho giảng viên</div>
              <textarea className="input" style={{ height: 100, padding: 12, resize: "none" }}
                placeholder="Mô tả chi tiết để giảng viên chỉnh sửa..."
                value={rejectNote}
                onChange={e => setRejectNote(e.target.value)}
              />
            </div>
            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={() => setReject(null)}>Hủy</button>
              <button className="btn btn-primary" style={{ background: "var(--error)" }} disabled={rejectLoading} onClick={handleReject}>
                {rejectLoading ? "Đang gửi..." : "Xác nhận từ chối"}
              </button>
            </div>
          </>}
        </Modal>
        {showPreview && React.createElement(window.PreviewPlayer, { onBack: () => setShowPreview(false) })}
      </div>
    );
  }

  Object.assign(window, { AdminApproval });
})();
