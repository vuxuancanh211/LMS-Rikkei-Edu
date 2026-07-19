// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Phê duyệt Khóa học (+ popup chi tiết / xem trước / từ chối)
   ============================================================ */
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Avatar, Status, StatCard, Search, Tabs, Select, Section, Modal, ModalHead, AlertModal } = window;
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
    const [imgFull, setImgFull]   = useState(null);
    const [view, setView]         = useState("overview");
    const [diffData, setDiffData] = useState(null);
    const [diffLoading, setDiffLoading] = useState(false);
    const isPendUpd = isUpdate(detail);
    const isPending = mapStatus(detail.status) === "pending";

    const allLessons   = (detail.chapters || []).flatMap(ch => ch.lessons || []);
    const allResources = allLessons.flatMap(l => l.resources || []);
    const liveChapters = (detail.chapters || []).filter(ch => !ch.isDraft && !ch.pendingDelete);
    const liveLessons  = allLessons.filter(l => !l.isDraft && !l.pendingDelete);

    const typeIc    = { VIDEO: "video", IMAGE: "image", PDF: "file", DOC: "file", SLIDE: "layers", OTHER: "file" };
    const typeColor = { PDF: "#ef4444", DOC: "#2563eb", SLIDE: "#f59e0b", IMAGE: "#10b981", VIDEO: "#8b5cf6" };
    const typeBg    = { PDF: "#fdecec", DOC: "#eaf1ff", SLIDE: "#fef5e6", IMAGE: "#e7f8f0", VIDEO: "#f3f0ff" };

    const draftChanges = isPendUpd ? [
      detail.draftTitle        && detail.draftTitle        !== detail.title        && { label: "Tên khóa học",  old: detail.title,        next: detail.draftTitle },
      detail.draftLevel        && detail.draftLevel        !== detail.level        && { label: "Cấp độ",        old: detail.level,        next: detail.draftLevel },
      detail.draftDescription  && detail.draftDescription  !== detail.description  && { label: "Mô tả",         old: detail.description,  next: detail.draftDescription },
      detail.draftThumbnailUrl && detail.draftThumbnailUrl !== detail.thumbnailUrl && { label: "Thumbnail",     old: detail.thumbnailUrl, next: detail.draftThumbnailUrl, isImg: true },
    ].filter(Boolean) : [];

    const newChapters           = isPendUpd ? (detail.chapters || []).filter(ch => ch.isDraft)                              : [];
    const delChapters           = isPendUpd ? (detail.chapters || []).filter(ch => ch.pendingDelete)                         : [];
    const newLessons            = isPendUpd ? allLessons.filter(l => l.isDraft)                                              : [];
    const delLessons            = isPendUpd ? allLessons.filter(l => l.pendingDelete)                                        : [];
    const renamedLessons        = isPendUpd ? allLessons.filter(l => !l.isDraft && l.draftTitle)                             : [];
    const contentChangedLessons = isPendUpd ? allLessons.filter(l => !l.isDraft && !l.draftTitle && l.draftContentText)      : [];
    const thumbnailDiff         = draftChanges.find(d => d.isImg) || null;

    // Option B: dùng flag từ backend thay vì so sánh timestamp
    const newResources = isPendUpd ? allResources.filter(r => r.isNewInUpdate)   : [];
    const delResources = isPendUpd ? allResources.filter(r => r.pendingDelete)    : [];

    const totalDiff = [draftChanges.length, newChapters.length + delChapters.length,
      newLessons.length + delLessons.length, renamedLessons.length + contentChangedLessons.length,
      newResources.length + delResources.length].reduce((a, b) => a + b, 0);
    const hasDiff = totalDiff > 0;

    const bannerUrl = (isPendUpd && detail.draftThumbnailUrl) ? detail.draftThumbnailUrl : (detail.thumbnailUrl || "");

    // ── Section divider helper ────────────────────────────────────────────────
    function SectionLabel({ children }) {
      return (
        <div style={{ display: "flex", alignItems: "center", gap: 8, margin: "18px 0 10px" }}>
          <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
          <span style={{ fontSize: 10.5, fontWeight: 700, color: "var(--text-3)", textTransform: "uppercase", letterSpacing: "0.07em", whiteSpace: "nowrap" }}>{children}</span>
          <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
        </div>
      );
    }

    // ── Diff card helper ──────────────────────────────────────────────────────
    function DiffCard({ label, old: oldVal, next: newVal, accent = "#0369a1", accentBg = "#f0f9ff" }) {
      return (
        <div style={{ marginBottom: 8, borderRadius: 10, overflow: "hidden", border: `1px solid ${accentBg === "#f0f9ff" ? "#bae6fd" : "var(--border)"}` }}>
          <div style={{ padding: "5px 12px", background: accentBg, fontSize: 10.5, fontWeight: 700, color: accent, textTransform: "uppercase", letterSpacing: "0.06em" }}>{label}</div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr auto 1fr" }}>
            <div style={{ padding: "9px 12px", background: "#fff5f5", borderRight: "1px solid #fecaca" }}>
              <div style={{ fontSize: 10, color: "#94a3b8", fontWeight: 600, marginBottom: 3 }}>TRƯỚC</div>
              <div style={{ fontSize: 13, color: "#dc2626", textDecoration: "line-through", wordBreak: "break-word" }}>{oldVal || <em style={{ color: "#cbd5e1" }}>trống</em>}</div>
            </div>
            <div style={{ display: "flex", alignItems: "center", padding: "0 8px", color: "#94a3b8" }}>
              <Ic n="chevron_right" size={16} />
            </div>
            <div style={{ padding: "9px 12px", background: "#f0fdf4" }}>
              <div style={{ fontSize: 10, color: "#94a3b8", fontWeight: 600, marginBottom: 3 }}>SAU</div>
              <div style={{ fontSize: 13, color: "#16a34a", fontWeight: 600, wordBreak: "break-word" }}>{newVal}</div>
            </div>
          </div>
        </div>
      );
    }

    return <>
      {/* ── Banner ── */}
      <div style={{ height: 200, background: "#0f172a", borderRadius: "18px 18px 0 0", position: "relative", overflow: "hidden" }}>
        {/* Thumbnail / fallback */}
        {(() => {
          const url = bannerUrl;
          const ext = url.split(".").pop()?.toLowerCase().split("?")[0] || "";
          if (["jpg","jpeg","png","gif","webp","svg"].includes(ext))
            return <img src={url} alt="" onClick={() => setImgFull(url)} style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover", opacity: 0.7, cursor: "zoom-in" }} />;
          if (["mp4","webm","mov","avi"].includes(ext))
            return <video src={url} muted style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover", opacity: 0.55 }} />;
          return null;
        })()}
        {/* Gradient overlay */}
        <div style={{ position: "absolute", inset: 0, background: "linear-gradient(to top, rgba(10,15,30,.9) 40%, rgba(10,15,30,.3) 100%)" }} />
        {/* Close */}
        <button className="icon-btn" onClick={onClose}
          style={{ position: "absolute", right: 14, top: 14, width: 34, height: 34, background: "rgba(255,255,255,.12)", color: "#fff", border: "none", zIndex: 3 }}>
          <Ic n="x" size={17} />
        </button>
        {/* Title overlay */}
        <div style={{ position: "absolute", left: 18, right: 52, bottom: 16, zIndex: 2 }}>
          <div style={{ display: "flex", gap: 6, marginBottom: 6, flexWrap: "wrap" }}>
            {detail.category?.name && (
              <span className="chip" style={{ background: "rgba(255,255,255,.15)", color: "#e2e8f0", fontSize: 11, backdropFilter: "blur(4px)" }}>{detail.category.name}</span>
            )}
            {(detail.draftLevel || detail.level) && (
              <span className="chip" style={{ background: "rgba(255,255,255,.1)", color: "#cbd5e1", fontSize: 11 }}>{detail.draftLevel || detail.level}</span>
            )}
            {isPendUpd && <span className="chip" style={{ background: "#0369a1", color: "#fff", fontSize: 10.5 }}>Yêu cầu cập nhật</span>}
          </div>
          <div style={{ color: "#fff", fontWeight: 700, fontSize: 16, lineHeight: 1.3, textShadow: "0 1px 4px rgba(0,0,0,.5)" }} className="truncate">
            {detail.draftTitle || detail.title}
          </div>
        </div>
        {/* Preview button */}
        <button onClick={onPreview}
          style={{ position: "absolute", right: 16, bottom: 16, background: "rgba(255,255,255,.14)", color: "#fff", border: "1px solid rgba(255,255,255,.25)", borderRadius: 999, padding: "5px 12px", fontSize: 12, display: "flex", alignItems: "center", gap: 5, cursor: "pointer", zIndex: 3, backdropFilter: "blur(4px)" }}>
          <Ic n="play" size={12} />Xem trước
        </button>
      </div>

      {/* ── Body ── */}
      <div className="modal-body" style={{ overflowY: "auto", flex: 1 }}>
        {/* Instructor row */}
        <div className="row gap-10" style={{ marginBottom: 16, paddingBottom: 14, borderBottom: "1px solid var(--border)" }}>
          <Avatar name={detail.instructorName || "?"} size={38} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontWeight: 600, fontSize: 14 }}>{detail.instructorName || "—"}</div>
            <div className="t-xs muted">Nộp lúc {fmtDate(detail.submittedAt || detail.createdAt)}</div>
          </div>
          {detail.changeSummary && (
            <div style={{ maxWidth: 200, padding: "6px 10px", borderRadius: 8, background: "#f0f9ff", border: "1px solid #bae6fd", fontSize: 12, color: "#0369a1", lineHeight: 1.4 }} title={detail.changeSummary}>
              <Ic n="message_square" size={12} style={{ marginRight: 4 }} />
              <span className="truncate" style={{ display: "inline" }}>{detail.changeSummary}</span>
            </div>
          )}
        </div>

        {/* Tabs — chỉ khi PENDING_UPDATE */}
        {isPendUpd && (
          <div className="tabs" style={{ marginBottom: 16, width: "fit-content" }}>
            <button className={view === "overview" ? "on" : ""} onClick={() => setView("overview")}>Tổng quan</button>
            <button className={view === "diff"     ? "on" : ""} onClick={() => {
              setView("diff");
              if (!diffData && !diffLoading) {
                setDiffLoading(true);
                http.get(`/admin/courses/${detail.id}/versions/diff`)
                  .then(r => setDiffData(r.data))
                  .catch(() => setDiffData(null))
                  .finally(() => setDiffLoading(false));
              }
            }}>So sánh (snapshot)</button>
            <button className={view === "changes"  ? "on" : ""} onClick={() => setView("changes")}>
              Thay đổi{hasDiff ? <span style={{ marginLeft: 5, background: "#ef4444", color: "#fff", borderRadius: 999, fontSize: 10, padding: "0 5px", fontWeight: 700 }}>{totalDiff}</span> : ""}
            </button>
          </div>
        )}

        {/* ════ VIEW: TỔNG QUAN ════ */}
        {(view === "overview" || !isPendUpd) && <>
          {/* Stats row */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8, marginBottom: 18 }}>
            {[
              { l: "Chương",     v: isPendUpd ? `${liveChapters.length}${newChapters.length ? ` +${newChapters.length}` : ""}` : (detail.chapters?.length || 0), ic: "layers",    c: "#7c3aed", bg: "#f5f3ff" },
              { l: "Bài giảng", v: isPendUpd ? `${liveLessons.length}${newLessons.length ? ` +${newLessons.length}` : ""}` : allLessons.length,                   ic: "book",      c: "#2563eb", bg: "#eaf1ff" },
              { l: "Tài liệu",  v: `${allResources.filter(r => !r.pendingDelete).length}${newResources.length ? ` (+${newResources.length})` : ""}${delResources.length ? ` (-${delResources.length})` : ""} tệp`, ic: "paperclip", c: "#d97706", bg: "#fef5e6" },
              { l: "Danh mục",  v: detail.category?.name || "—",                                                                                                    ic: "folder",    c: "#059669", bg: "#e7f8f0" },
            ].map((s, i) => (
              <div key={i} style={{ padding: "10px 12px", borderRadius: 11, background: "var(--surface-2)", textAlign: "center" }}>
                <div style={{ width: 34, height: 34, borderRadius: 9, background: s.bg, color: s.c, display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 6px" }}>
                  <Ic n={s.ic} size={16} />
                </div>
                <div style={{ fontWeight: 700, fontSize: 13.5, color: "var(--text)" }}>{s.v}</div>
                <div style={{ fontSize: 10.5, color: "var(--text-3)", marginTop: 1 }}>{s.l}</div>
              </div>
            ))}
          </div>

          {/* Description */}
          {(detail.description || detail.draftDescription) && (
            <div style={{ marginBottom: 16, padding: "10px 14px", borderRadius: 10, background: "var(--surface-2)", fontSize: 13, color: "var(--text-2)", lineHeight: 1.65 }}>
              {isPendUpd && detail.draftDescription
                ? <><span style={{ textDecoration: "line-through", color: "#94a3b8" }}>{detail.description}</span><br /><span style={{ color: "#16a34a", fontWeight: 500 }}>{detail.draftDescription}</span></>
                : (detail.description || detail.draftDescription)}
            </div>
          )}

          {/* Chapters & Lessons */}
          <SectionLabel>Nội dung khóa học</SectionLabel>
          {(detail.chapters || []).map((ch, ci) => {
            const chIsNew = !!ch.isDraft;
            const chIsDel = !!ch.pendingDelete;
            return (
              <div key={ch.id || ci} style={{
                marginBottom: 12, borderRadius: 11, overflow: "hidden",
                border: chIsNew ? "2px solid #86efac" : chIsDel ? "2px dashed #fca5a5" : "1px solid var(--border)",
                opacity: chIsDel ? 0.65 : 1,
              }}>
                {/* Chapter header */}
                <div style={{ padding: "9px 14px", background: chIsNew ? "#f0fdf4" : chIsDel ? "#fff5f5" : "var(--surface-2)", display: "flex", alignItems: "center", gap: 8 }}>
                  <Ic n="layers" size={14} style={{ color: "var(--text-3)", flex: "none" }} />
                  <span style={{ fontWeight: 700, fontSize: 13, flex: 1, textDecoration: chIsDel ? "line-through" : "none" }}>
                    {ci + 1}. {ch.title}
                  </span>
                  {chIsNew && <span className="chip" style={{ background: "#dcfce7", color: "#16a34a", fontSize: 10 }}>Mới</span>}
                  {chIsDel && <span className="chip" style={{ background: "#fee2e2", color: "#dc2626", fontSize: 10 }}>Sẽ xóa</span>}
                  <span className="t-xs muted">{ch.lessons?.length || 0} bài</span>
                </div>
                {/* Lessons */}
                <div style={{ display: "flex", flexDirection: "column", gap: 1, background: "var(--border)" }}>
                  {(ch.lessons || []).map((l, li) => {
                    const isVideo  = l.lessonType === "VIDEO";
                    const lIsNew   = !!l.isDraft;
                    const lIsDel   = !!l.pendingDelete;
                    const lRenamed = !l.isDraft && !!l.draftTitle;
                    const lChanged = !l.isDraft && !l.draftTitle && !!l.draftContentText;
                    const bg = lIsNew ? "#f0fdf4" : lIsDel ? "#fff5f5" : lRenamed || lChanged ? "#eff6ff" : "var(--surface)";
                    return (
                      <div key={l.id || li} style={{ padding: "8px 14px", background: bg, display: "flex", alignItems: "center", gap: 10, opacity: lIsDel ? 0.6 : 1 }}>
                        <div style={{ width: 30, height: 22, borderRadius: 5, background: isVideo ? "#1e293b" : "var(--surface-2)", display: "grid", placeItems: "center", flex: "none" }}>
                          <Ic n={isVideo ? "play" : "file_text"} size={11} style={{ color: isVideo ? "#fff" : "var(--text-3)" }} />
                        </div>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: 13, fontWeight: 500 }} className="truncate">
                            {lIsDel    ? <span style={{ textDecoration: "line-through", color: "#dc2626" }}>{l.title}</span>
                            : lRenamed ? <><span style={{ textDecoration: "line-through", color: "#94a3b8", fontSize: 12 }}>{l.title}</span>{" "}<Ic n="chevron_right" size={11} style={{ color: "#94a3b8" }} />{" "}<span style={{ color: "#16a34a" }}>{l.draftTitle}</span></>
                            : l.title}
                          </div>
                          <div style={{ fontSize: 11, color: "var(--text-3)", marginTop: 1, display: "flex", gap: 4, alignItems: "center" }}>
                            {isVideo ? "Video" : "Văn bản"}
                            {l.resources?.length > 0 && <><span>·</span><Ic n="paperclip" size={10} />{l.resources.length}</>}
                            {lIsNew    && <><span>·</span><span style={{ color: "#16a34a", fontWeight: 700 }}>MỚI</span></>}
                            {lChanged  && <><span>·</span><span style={{ color: "#2563eb", fontWeight: 700 }}>ĐỔI ND</span></>}
                          </div>
                        </div>
                        {lIsDel && <span className="chip" style={{ background: "#fee2e2", color: "#dc2626", fontSize: 10 }}>Sẽ xóa</span>}
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}

          {/* Resources */}
          {allResources.length > 0 && <>
            <SectionLabel>Tài liệu đính kèm</SectionLabel>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 7, marginBottom: 8 }}>
              {allResources.map((r, i) => {
                const isNew = !!r.isNewInUpdate;
                const isDel = !!r.pendingDelete;
                const border = isNew ? "2px solid #93c5fd" : isDel ? "2px dashed #fca5a5" : "1px solid var(--border)";
                const bg     = isNew ? "#eff6ff" : isDel ? "#fff5f5" : "var(--surface-2)";
                return (
                  <div key={r.id || i} style={{ padding: "8px 10px", border, borderRadius: 10, background: bg, display: "flex", alignItems: "center", gap: 9, opacity: isDel ? 0.7 : 1 }}>
                    <div style={{ width: 32, height: 32, borderRadius: 8, background: typeBg[r.resourceType] || "var(--surface-2)", color: typeColor[r.resourceType] || "#64748b", display: "flex", alignItems: "center", justifyContent: "center", flex: "none" }}>
                      <Ic n={typeIc[r.resourceType] || "file"} size={15} />
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 12.5, fontWeight: 600, textDecoration: isDel ? "line-through" : "none", color: isDel ? "#dc2626" : "inherit" }} className="truncate">{r.displayName || r.originalFilename}</div>
                      <div style={{ fontSize: 10.5, color: "var(--text-3)" }}>{r.resourceType}{r.fileSizeBytes ? ` · ${(r.fileSizeBytes / 1024 / 1024).toFixed(1)}MB` : ""}</div>
                    </div>
                    {isNew && <span className="chip" style={{ background: "#2563eb", color: "#fff", fontSize: 9.5 }}>Mới</span>}
                    {isDel && <span className="chip" style={{ background: "#fee2e2", color: "#dc2626", fontSize: 9.5 }}>Sẽ xóa</span>}
                  </div>
                );
              })}
            </div>
          </>}
        </>}

        {/* ════ VIEW: SO SÁNH ════ */}
        {view === "diff" && isPendUpd && (() => {
          const C = {
            add:   { border: "#86efac", bg: "#f0fdf4", text: "#166534", chip: "#dcfce7", chipFg: "#16a34a" },
            del:   { border: "#fca5a5", bg: "#fff5f5", text: "#991b1b", chip: "#fee2e2", chipFg: "#dc2626" },
            mod:   { border: "#93c5fd", bg: "#eff6ff", text: "#1e40af", chip: "#dbeafe", chipFg: "#2563eb" },
            plain: { border: "var(--border)", bg: "var(--surface-2)", text: "var(--text)" },
          };
          const actionColor = (a) => a === "ADDED" ? C.add : a === "REMOVED" ? C.del : a === "MODIFIED" ? C.mod : C.plain;

          if (diffLoading) return (
            <div style={{ textAlign: "center", padding: "40px 0", color: "var(--text-3)" }}>
              <div style={{ fontSize: 13 }}>Đang tải so sánh snapshot...</div>
            </div>
          );

          if (!diffData) return (
            <div style={{ textAlign: "center", padding: "40px 0", color: "var(--text-3)" }}>
              <Ic n="warn" size={24} style={{ marginBottom: 8 }} />
              <div style={{ fontSize: 13 }}>Không thể tải dữ liệu so sánh. Chưa có version được duyệt trước đó?</div>
            </div>
          );

          const meta = diffData.metadata || {};
          const metaFields = [
            { label: "Tên khóa học", f: meta.title },
            { label: "Mô tả",        f: meta.description },
            { label: "Cấp độ",       f: meta.level },
            { label: "Thumbnail",    f: meta.thumbnailUrl, isImg: true },
          ].filter(x => x.f?.changed);

          const chapters = diffData.chapters || [];
          const resources = diffData.resources || [];
          const addedRes = resources.filter(r => r.action === "ADDED");
          const removedRes = resources.filter(r => r.action === "REMOVED");

          return (
            <div>
              {/* Version badge */}
              <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 14, padding: "8px 12px", borderRadius: 9, background: "var(--surface-2)", fontSize: 11.5, color: "var(--text-2)" }}>
                <Ic n="clock" size={13} />
                So sánh
                <span style={{ fontWeight: 700, color: "#64748b" }}>
                  v{diffData.approvedVersionNumber ?? "?"} (đang live)
                </span>
                <Ic n="chevron_right" size={13} />
                <span style={{ fontWeight: 700, color: "#0369a1" }}>
                  v{diffData.pendingVersionNumber ?? "?"} (đang chờ duyệt)
                </span>
                <div style={{ flex: 1 }} />
                {/* Legend */}
                {[{ color: "#16a34a", bg: "#dcfce7", label: "Thêm" }, { color: "#dc2626", bg: "#fee2e2", label: "Xóa" }, { color: "#2563eb", bg: "#dbeafe", label: "Sửa" }].map(({ color, bg, label }) => (
                  <div key={label} style={{ display: "flex", alignItems: "center", gap: 4 }}>
                    <div style={{ width: 9, height: 9, borderRadius: 2, background: bg, border: `1.5px solid ${color}` }} />
                    <span style={{ fontSize: 10.5 }}>{label}</span>
                  </div>
                ))}
              </div>

              {/* Metadata changes */}
              {metaFields.length > 0 && (
                <div style={{ marginBottom: 16 }}>
                  <SectionLabel>Thông tin cơ bản</SectionLabel>
                  {metaFields.filter(x => !x.isImg).map((x, i) => (
                    <DiffCard key={i} label={x.label} old={x.f.oldValue} next={x.f.newValue} />
                  ))}
                  {metaFields.filter(x => x.isImg).map((x, i) => (
                    <div key={i} style={{ display: "flex", gap: 12, alignItems: "center", padding: 12, background: "var(--surface-2)", borderRadius: 10, marginBottom: 8 }}>
                      <div style={{ flex: 1, textAlign: "center" }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color: "var(--text-3)", marginBottom: 5 }}>ẢNH BÌA HIỆN TẠI</div>
                        {x.f.oldValue
                          ? <img src={x.f.oldValue} onClick={() => setImgFull(x.f.oldValue)} style={{ width: "100%", maxWidth: 160, height: 90, objectFit: "cover", borderRadius: 8, cursor: "zoom-in", border: "1px solid var(--border)" }} />
                          : <div style={{ width: 160, height: 90, borderRadius: 8, background: "#f1f5f9", display: "grid", placeItems: "center", margin: "0 auto" }}><Ic n="image" size={24} style={{ color: "#94a3b8" }} /></div>
                        }
                      </div>
                      <Ic n="chevron_right" size={20} style={{ color: "#94a3b8" }} />
                      <div style={{ flex: 1, textAlign: "center" }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color: "#16a34a", marginBottom: 5 }}>ẢNH BÌA MỚI</div>
                        <img src={x.f.newValue} onClick={() => setImgFull(x.f.newValue)} style={{ width: "100%", maxWidth: 160, height: 90, objectFit: "cover", borderRadius: 8, cursor: "zoom-in", border: "2px solid #86efac" }} />
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Chapter / Lesson diff */}
              {chapters.length > 0 && (
                <div style={{ marginBottom: 16 }}>
                  <SectionLabel>Nội dung khóa học</SectionLabel>
                  {chapters.map((ch, ci) => {
                    const cc = actionColor(ch.action);
                    const changed = ch.action !== "UNCHANGED";
                    return (
                      <div key={ci} style={{ marginBottom: 10, borderRadius: 10, overflow: "hidden", border: `2px solid ${cc.border}`, opacity: ch.action === "REMOVED" ? 0.75 : 1 }}>
                        <div style={{ padding: "8px 12px", background: cc.bg, display: "flex", alignItems: "center", gap: 7 }}>
                          <Ic n="layers" size={13} style={{ color: changed ? cc.chipFg : "var(--text-3)", flex: "none" }} />
                          <span style={{ fontWeight: 700, fontSize: 12.5, flex: 1, color: cc.text }}>
                            {ch.action === "REMOVED" ? <s>{ch.title}</s> : ch.title}
                          </span>
                          {ch.action === "ADDED"    && <span style={{ fontSize: 9.5, fontWeight: 700, padding: "1px 7px", borderRadius: 999, background: C.add.chip, color: C.add.chipFg }}>MỚI</span>}
                          {ch.action === "REMOVED"  && <span style={{ fontSize: 9.5, fontWeight: 700, padding: "1px 7px", borderRadius: 999, background: C.del.chip, color: C.del.chipFg }}>XÓA</span>}
                          {ch.action === "MODIFIED" && <span style={{ fontSize: 9.5, fontWeight: 700, padding: "1px 7px", borderRadius: 999, background: C.mod.chip, color: C.mod.chipFg }}>SỬA</span>}
                          <span style={{ fontSize: 10.5, color: "var(--text-3)" }}>{(ch.lessons || []).length} bài</span>
                        </div>
                        <div style={{ padding: "6px 8px" }}>
                          {(ch.lessons || []).length === 0
                            ? <div style={{ fontSize: 11.5, color: "var(--text-3)", padding: "6px 4px", fontStyle: "italic" }}>Không có bài giảng</div>
                            : (ch.lessons || []).map((l, li) => {
                              const lc = actionColor(l.action);
                              const isVid = l.lessonType === "VIDEO";
                              return (
                                <div key={li} style={{ display: "flex", alignItems: "center", gap: 8, padding: "6px 12px", background: lc.bg, borderLeft: `3px solid ${lc.border}`, marginBottom: 2, borderRadius: "0 6px 6px 0" }}>
                                  <div style={{ width: 24, height: 18, borderRadius: 4, background: isVid ? "#1e293b" : "var(--surface-2)", display: "grid", placeItems: "center", flex: "none" }}>
                                    <Ic n={isVid ? "play" : "file_text"} size={10} style={{ color: isVid ? "#fff" : "var(--text-3)" }} />
                                  </div>
                                  <div style={{ flex: 1, minWidth: 0 }}>
                                    <div style={{ fontSize: 12.5, fontWeight: 500, color: lc.text }} className="truncate">
                                      {l.action === "REMOVED" ? <s>{l.title}</s> : (l.newTitle || l.title)}
                                    </div>
                                    {l.action === "MODIFIED" && l.newTitle && (
                                      <div style={{ fontSize: 10.5, color: "#94a3b8", textDecoration: "line-through" }} className="truncate">{l.title}</div>
                                    )}
                                  </div>
                                  {l.action === "ADDED"    && <span style={{ fontSize: 9.5, fontWeight: 700, padding: "1px 6px", borderRadius: 999, background: C.add.chip, color: C.add.chipFg }}>MỚI</span>}
                                  {l.action === "REMOVED"  && <span style={{ fontSize: 9.5, fontWeight: 700, padding: "1px 6px", borderRadius: 999, background: C.del.chip, color: C.del.chipFg }}>XÓA</span>}
                                  {l.action === "MODIFIED" && <span style={{ fontSize: 9.5, fontWeight: 700, padding: "1px 6px", borderRadius: 999, background: C.mod.chip, color: C.mod.chipFg }}>SỬA</span>}
                                </div>
                              );
                            })
                          }
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Resource diff */}
              {(addedRes.length > 0 || removedRes.length > 0) && (
                <div style={{ marginBottom: 16 }}>
                  <SectionLabel>Tài liệu ({addedRes.length > 0 ? `+${addedRes.length}` : ""}{removedRes.length > 0 ? ` -${removedRes.length}` : ""})</SectionLabel>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                    <div>
                      <div style={{ fontSize: 10.5, fontWeight: 700, color: "#dc2626", marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.06em" }}>Xóa ({removedRes.length})</div>
                      {removedRes.length === 0
                        ? <div style={{ fontSize: 12, color: "var(--text-3)", fontStyle: "italic" }}>Không có</div>
                        : removedRes.map((r, i) => (
                          <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 8, padding: "7px 10px", borderRadius: 8, marginBottom: 5, border: "2px dashed #fca5a5", background: "#fff5f5" }}>
                            <Ic n={typeIc[r.resourceType] || "file"} size={13} style={{ color: typeColor[r.resourceType] || "#64748b", flex: "none", marginTop: 1 }} />
                            <div style={{ flex: 1, minWidth: 0 }}>
                              <div style={{ fontSize: 12, fontWeight: 500, color: "#991b1b", textDecoration: "line-through" }} className="truncate">{r.displayName}</div>
                              <div style={{ fontSize: 10.5, color: "#94a3b8", marginTop: 1 }}>{r.chapterTitle} › {r.lessonTitle}</div>
                              <div style={{ fontSize: 10.5, color: "#94a3b8" }}>{r.resourceType}{r.fileSizeBytes ? ` · ${(r.fileSizeBytes / 1024 / 1024).toFixed(1)}MB` : ""}</div>
                            </div>
                          </div>
                        ))
                      }
                    </div>
                    <div>
                      <div style={{ fontSize: 10.5, fontWeight: 700, color: "#16a34a", marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.06em" }}>Thêm mới ({addedRes.length})</div>
                      {addedRes.length === 0
                        ? <div style={{ fontSize: 12, color: "var(--text-3)", fontStyle: "italic" }}>Không có</div>
                        : addedRes.map((r, i) => (
                          <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 8, padding: "7px 10px", borderRadius: 8, marginBottom: 5, border: "2px solid #86efac", background: "#f0fdf4" }}>
                            <Ic n={typeIc[r.resourceType] || "file"} size={13} style={{ color: typeColor[r.resourceType] || "#64748b", flex: "none", marginTop: 1 }} />
                            <div style={{ flex: 1, minWidth: 0 }}>
                              <div style={{ fontSize: 12, fontWeight: 500, color: "#166534" }} className="truncate">{r.displayName}</div>
                              <div style={{ fontSize: 10.5, color: "#94a3b8", marginTop: 1 }}>{r.chapterTitle} › {r.lessonTitle}</div>
                              <div style={{ fontSize: 10.5, color: "#94a3b8" }}>{r.resourceType}{r.fileSizeBytes ? ` · ${(r.fileSizeBytes / 1024 / 1024).toFixed(1)}MB` : ""}</div>
                            </div>
                          </div>
                        ))
                      }
                    </div>
                  </div>
                </div>
              )}

              {/* No changes at all */}
              {metaFields.length === 0 && chapters.filter(c => c.action !== "UNCHANGED").length === 0 && resources.length === 0 && (
                <div style={{ textAlign: "center", padding: "24px 0", color: "var(--text-3)", fontSize: 13 }}>
                  <Ic n="check_circle" size={22} style={{ marginBottom: 6, color: "#16a34a" }} />
                  <div>Không phát hiện thay đổi nào so với version trước</div>
                </div>
              )}
            </div>
          );
        })()}

        {/* ════ VIEW: THAY ĐỔI ════ */}
        {view === "changes" && isPendUpd && (
          <div>
            {detail.changeSummary && (
              <div style={{ marginBottom: 14, padding: "10px 14px", borderRadius: 10, background: "#f0f9ff", border: "1px solid #bae6fd", display: "flex", gap: 10, alignItems: "flex-start" }}>
                <Ic n="message_square" size={15} style={{ color: "#0284c7", flex: "none", marginTop: 1 }} />
                <div>
                  <div style={{ fontSize: 10.5, fontWeight: 700, color: "#0369a1", marginBottom: 3 }}>GHI CHÚ GIẢNG VIÊN</div>
                  <div style={{ fontSize: 13, color: "#0369a1", lineHeight: 1.5 }}>{detail.changeSummary}</div>
                </div>
              </div>
            )}

            {!hasDiff && (
              <div style={{ padding: "32px 0", textAlign: "center" }}>
                <div style={{ width: 52, height: 52, borderRadius: "50%", background: "#f0fdf4", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 12px" }}>
                  <Ic n="check" size={24} style={{ color: "#16a34a" }} />
                </div>
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>Không có thay đổi nội dung</div>
                <div className="t-xs muted">Có thể có thay đổi tài nguyên bên trong bài giảng.</div>
              </div>
            )}

            {/* Metadata changes */}
            {draftChanges.filter(d => !d.isImg).length > 0 && <>
              <SectionLabel>Thông tin khóa học</SectionLabel>
              {draftChanges.filter(d => !d.isImg).map((d, i) => (
                <DiffCard key={i} label={d.label} old={d.old} next={d.next} />
              ))}
            </>}

            {/* Thumbnail */}
            {thumbnailDiff && <>
              <SectionLabel>Hình thumbnail</SectionLabel>
              <div style={{ display: "flex", gap: 14, alignItems: "center", padding: 14, background: "var(--surface-2)", borderRadius: 12, marginBottom: 12 }}>
                <div style={{ flex: 1, textAlign: "center" }}>
                  <div style={{ fontSize: 10.5, fontWeight: 700, color: "var(--text-3)", marginBottom: 6 }}>TRƯỚC</div>
                  {thumbnailDiff.old
                    ? <img src={thumbnailDiff.old} alt="" onClick={() => setImgFull(thumbnailDiff.old)} style={{ width: "100%", maxWidth: 170, height: 96, objectFit: "cover", borderRadius: 9, border: "1px solid var(--border)", cursor: "zoom-in" }} />
                    : <div style={{ width: 170, height: 96, borderRadius: 9, background: "#f1f5f9", display: "grid", placeItems: "center", margin: "0 auto" }}><Ic n="image" size={28} style={{ color: "#94a3b8" }} /></div>}
                </div>
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 4, color: "#94a3b8" }}>
                  <Ic n="chevron_right" size={22} />
                </div>
                <div style={{ flex: 1, textAlign: "center" }}>
                  <div style={{ fontSize: 10.5, fontWeight: 700, color: "#16a34a", marginBottom: 6 }}>SAU</div>
                  <img src={thumbnailDiff.next} alt="" onClick={() => setImgFull(thumbnailDiff.next)} style={{ width: "100%", maxWidth: 170, height: 96, objectFit: "cover", borderRadius: 9, border: "2px solid #86efac", cursor: "zoom-in" }} />
                </div>
              </div>
            </>}

            {/* Structure changes */}
            {(newChapters.length || delChapters.length || newLessons.length || delLessons.length) > 0 && <>
              <SectionLabel>Cấu trúc khóa học</SectionLabel>
              <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 8 }}>
                {newChapters.map(ch => (
                  <div key={ch.id} className="row gap-9" style={{ padding: "7px 12px", borderRadius: 9, background: "#f0fdf4", border: "1px solid #bbf7d0" }}>
                    <span style={{ color: "#16a34a", fontWeight: 800, fontSize: 14, lineHeight: 1 }}>+</span>
                    <Ic n="layers" size={13} style={{ color: "#16a34a" }} />
                    <span style={{ fontSize: 13, color: "#166534" }}>Chương: {ch.title}</span>
                  </div>
                ))}
                {delChapters.map(ch => (
                  <div key={ch.id} className="row gap-9" style={{ padding: "7px 12px", borderRadius: 9, background: "#fff5f5", border: "1px solid #fecaca" }}>
                    <span style={{ color: "#dc2626", fontWeight: 800, fontSize: 14, lineHeight: 1 }}>−</span>
                    <Ic n="layers" size={13} style={{ color: "#dc2626" }} />
                    <span style={{ fontSize: 13, color: "#991b1b", textDecoration: "line-through" }}>Chương: {ch.title}</span>
                  </div>
                ))}
                {newLessons.map(l => (
                  <div key={l.id} className="row gap-9" style={{ padding: "7px 12px", borderRadius: 9, background: "#f0fdf4", border: "1px solid #bbf7d0" }}>
                    <span style={{ color: "#16a34a", fontWeight: 800, fontSize: 14, lineHeight: 1 }}>+</span>
                    <Ic n="book" size={13} style={{ color: "#16a34a" }} />
                    <span style={{ fontSize: 13, color: "#166534" }}>Bài: {l.title}</span>
                  </div>
                ))}
                {delLessons.map(l => (
                  <div key={l.id} className="row gap-9" style={{ padding: "7px 12px", borderRadius: 9, background: "#fff5f5", border: "1px solid #fecaca" }}>
                    <span style={{ color: "#dc2626", fontWeight: 800, fontSize: 14, lineHeight: 1 }}>−</span>
                    <Ic n="book" size={13} style={{ color: "#dc2626" }} />
                    <span style={{ fontSize: 13, color: "#991b1b", textDecoration: "line-through" }}>Bài: {l.title}</span>
                  </div>
                ))}
              </div>
            </>}

            {/* Renamed lessons */}
            {renamedLessons.length > 0 && <>
              <SectionLabel>Đổi tên bài giảng</SectionLabel>
              {renamedLessons.map(l => (
                <div key={l.id} style={{ marginBottom: 8 }}>
                  <DiffCard label={`Bài: ${l.title}`} old={l.title} next={l.draftTitle} accent="#0369a1" accentBg="#e0f2fe" />
                  {l.draftContentText && (
                    <div style={{ marginTop: -4, padding: "9px 12px", borderRadius: "0 0 10px 10px", background: "#f0fdf4", border: "1px solid #bbf7d0", borderTop: "none" }}>
                      <div style={{ fontSize: 10.5, fontWeight: 700, color: "#16a34a", marginBottom: 4 }}>NỘI DUNG MỚI</div>
                      <div style={{ fontSize: 12, color: "#166534", maxHeight: 100, overflowY: "auto", whiteSpace: "pre-wrap", lineHeight: 1.6 }}>{l.draftContentText}</div>
                    </div>
                  )}
                </div>
              ))}
            </>}

            {/* Content changed lessons */}
            {contentChangedLessons.length > 0 && <>
              <SectionLabel>Thay đổi nội dung bài</SectionLabel>
              {contentChangedLessons.map(l => (
                <div key={l.id} style={{ marginBottom: 8, borderRadius: 10, overflow: "hidden", border: "1px solid #fde68a" }}>
                  <div style={{ padding: "5px 12px", background: "#fef9c3", fontSize: 10.5, fontWeight: 700, color: "#854d0e", textTransform: "uppercase", letterSpacing: "0.06em" }}>{l.title}</div>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr auto 1fr" }}>
                    <div style={{ padding: "9px 12px", background: "#fff5f5", borderRight: "1px solid #fde68a" }}>
                      <div style={{ fontSize: 10, color: "#94a3b8", fontWeight: 600, marginBottom: 3 }}>TRƯỚC</div>
                      <div style={{ fontSize: 12, color: "#dc2626", maxHeight: 100, overflowY: "auto", whiteSpace: "pre-wrap", lineHeight: 1.6 }}>{l.contentText || <em style={{ color: "#cbd5e1" }}>trống</em>}</div>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", padding: "0 8px", color: "#94a3b8" }}>
                      <Ic n="chevron_right" size={16} />
                    </div>
                    <div style={{ padding: "9px 12px", background: "#f0fdf4" }}>
                      <div style={{ fontSize: 10, color: "#94a3b8", fontWeight: 600, marginBottom: 3 }}>SAU</div>
                      <div style={{ fontSize: 12, color: "#166534", maxHeight: 100, overflowY: "auto", whiteSpace: "pre-wrap", lineHeight: 1.6 }}>{l.draftContentText}</div>
                    </div>
                  </div>
                </div>
              ))}
            </>}

            {/* New resources (Option B) */}
            {newResources.length > 0 && <>
              <SectionLabel>Tài liệu mới thêm</SectionLabel>
              <div style={{ display: "flex", flexDirection: "column", gap: 7, marginBottom: 8 }}>
                {newResources.map(r => (
                  <div key={r.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 12px", borderRadius: 10, border: "2px solid #93c5fd", background: "#eff6ff" }}>
                    <div style={{ width: 34, height: 34, borderRadius: 9, background: typeBg[r.resourceType] || "var(--surface-2)", color: typeColor[r.resourceType] || "#475569", display: "flex", alignItems: "center", justifyContent: "center", flex: "none" }}>
                      <Ic n={typeIc[r.resourceType] || "file"} size={16} />
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 13 }} className="truncate">{r.displayName || r.originalFilename}</div>
                      <div style={{ fontSize: 11, color: "var(--text-3)" }}>{r.resourceType}{r.fileSizeBytes ? ` · ${(r.fileSizeBytes / 1024 / 1024).toFixed(1)} MB` : ""}</div>
                    </div>
                    <span className="chip" style={{ background: "#2563eb", color: "#fff", fontSize: 10 }}>Mới</span>
                  </div>
                ))}
              </div>
            </>}

            {/* Deleted resources (Option B) */}
            {delResources.length > 0 && <>
              <SectionLabel>Tài liệu sẽ xóa</SectionLabel>
              <div style={{ display: "flex", flexDirection: "column", gap: 7, marginBottom: 8 }}>
                {delResources.map(r => (
                  <div key={r.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 12px", borderRadius: 10, border: "2px dashed #fca5a5", background: "#fff5f5", opacity: 0.85 }}>
                    <div style={{ width: 34, height: 34, borderRadius: 9, background: typeBg[r.resourceType] || "var(--surface-2)", color: typeColor[r.resourceType] || "#475569", display: "flex", alignItems: "center", justifyContent: "center", flex: "none" }}>
                      <Ic n={typeIc[r.resourceType] || "file"} size={16} />
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 13, textDecoration: "line-through", color: "#dc2626" }} className="truncate">{r.displayName || r.originalFilename}</div>
                      <div style={{ fontSize: 11, color: "var(--text-3)" }}>{r.resourceType}{r.fileSizeBytes ? ` · ${(r.fileSizeBytes / 1024 / 1024).toFixed(1)} MB` : ""}</div>
                    </div>
                    <span className="chip" style={{ background: "#fee2e2", color: "#dc2626", fontSize: 10 }}>Sẽ xóa</span>
                  </div>
                ))}
              </div>
            </>}
          </div>
        )}
      </div>

      {/* ── Footer ── */}
      {isPending && (
        <div className="modal-foot" style={{ gap: 10 }}>
          <button className="btn btn-danger" style={{ flex: 1 }} onClick={onReject}>
            <Ic n="x" size={16} />{isPendUpd ? "Từ chối cập nhật" : "Từ chối"}
          </button>
          <button className="btn btn-success" style={{ flex: 2 }} onClick={onApprove}>
            <Ic n="check" size={16} />{isPendUpd ? "Áp dụng cập nhật" : "Phê duyệt & Xuất bản"}
          </button>
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
  function AdminApproval({ nav, approvalId: propApprovalId }: { nav?: any; approvalId?: string } = {}) {
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
    const [previewStage, setPreviewStage] = useState(null); // null | "overview" | "player"
    const [alertState, setAlertState] = useState(null);
    const showAlert = (message, opts?) => setAlertState({ message, ...opts });

    const approvalId = propApprovalId || new URLSearchParams(window.location.search).get("approvalId") || window.__selectedApprovalId || null;
    useEffect(() => {
      if (approvalId && !detail) {
        const item = list.find(c => c.id === approvalId) || window.__selectedApprovalCourse || { id: approvalId };
        if (item && item.id) openDetail(item);
      }
    }, [approvalId, list]);

    useEffect(() => { loadList(); }, [tab, page]);

    async function loadList() {
      setLoading(true);
      try {
        let url;
        if (tab === "pending") {
          url = `/admin/courses/pending?page=${page}&size=10`;
        } else if (tab === "approved") {
          // Lọc theo status ngay ở backend (query DB) — KHÔNG lọc lại ở FE sau khi đã
          // phân trang, vì mỗi trang chỉ có 10 bản ghi thô, số khớp filter mỗi trang
          // không đều (có trang 0 kết quả dù totalPages vẫn tính trên tập chưa lọc).
          url = `/admin/courses?page=${page}&size=10&status=PUBLISHED`;
        } else if (tab === "rejected") {
          url = `/admin/courses?page=${page}&size=10&status=REJECTED`;
        } else {
          url = `/admin/courses?page=${page}&size=10`;
        }
        const res = await http.get(url);
        const data = res.data;
        const items = data.content || data || [];

        setList(items);
        setTotalPages(data.totalPages || 1);
        if (tab === "pending")  setStats(st => ({ ...st, pending:  data.totalElements ?? items.length }));
        if (tab === "approved") setStats(st => ({ ...st, approved: data.totalElements ?? items.length }));
        if (tab === "rejected") setStats(st => ({ ...st, rejected: data.totalElements ?? items.length }));
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
        showAlert(e?.response?.data?.message || e.message, { title: "Lỗi khi phê duyệt" });
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
        showAlert(e?.response?.data?.message || e.message, { title: "Lỗi khi từ chối" });
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
                onPreview={() => { setDetail(null); window.__previewCourse = { courseId: detail.id, role: "admin" }; setPreviewStage("overview"); }}
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
        {previewStage === "overview" && (
          <div style={{ position: "fixed", inset: 0, zIndex: 200, background: "var(--bg)", overflowY: "auto" }}>
            {React.createElement(window.StuCourseDetail, {
              courseId: window.__previewCourse?.courseId,
              previewMode: true, previewRole: "admin",
              onBack: () => setPreviewStage(null),
              onEnterContent: () => setPreviewStage("player"),
            })}
          </div>
        )}
        {previewStage === "player" && React.createElement(window.PreviewPlayer, { onBack: () => setPreviewStage("overview") })}
        <AlertModal open={!!alertState} onClose={() => setAlertState(null)} title={alertState?.title} message={alertState?.message} type={alertState?.type} />
      </div>
    );
  }

  Object.assign(window, { AdminApproval });
})();
