// @ts-nocheck
/* ============================================================
     RIKKEI EDU – Giảng viên · Chi tiết Khóa học
   ============================================================ */
(function () {
  const { useState, useEffect, useRef } = React;
  const Ic = window.Icon;
  const { Status, StatCard, Tabs, Select, Section, Modal, ModalHead, Empty } = window;
  const api = window.httpClient;

  const CATS   = ["Frontend", "Backend", "DevOps", "Database", "AI/ML", "Mobile", "Testing", "Design", "Security", "Quy trình"];
  const LEVELS = [
    { label: "Cơ bản",    value: "BEGINNER"     },
    { label: "Trung cấp", value: "INTERMEDIATE" },
    { label: "Nâng cao",  value: "ADVANCED"     },
  ];

  const STATUS_LABEL = {
    DRAFT:          "Bản nháp",
    PENDING:        "Chờ duyệt",
    PUBLISHED:      "Đã xuất bản",
    REJECTED:       "Từ chối",
    PENDING_UPDATE: "Chờ cập nhật",
    ARCHIVED:       "Lưu trữ",
  };
  const STATUS_COLOR = {
    DRAFT:          { bg: "#f1f5f9", color: "#64748b" },
    PENDING:        { bg: "#fef9c3", color: "#a16207" },
    PUBLISHED:      { bg: "#dcfce7", color: "#16a34a" },
    REJECTED:       { bg: "#fee2e2", color: "#dc2626" },
    PENDING_UPDATE: { bg: "#e0f2fe", color: "#0284c7" },
    ARCHIVED:       { bg: "#f1f5f9", color: "#475569" },
  };

  const RES_TYPE = {
    VIDEO: { ic: "video",  bg: "#eaf1ff", fg: "#2563eb" },
    PDF:   { ic: "file",   bg: "#fdecec", fg: "#dc2626" },
    DOC:   { ic: "file",   bg: "#eaf1ff", fg: "#2563eb" },
    SLIDE: { ic: "layers", bg: "#fef5e6", fg: "#d97706" },
    IMAGE: { ic: "image",  bg: "#f0fdf4", fg: "#16a34a" },
    OTHER: { ic: "file",   bg: "#f1f5f9", fg: "#475569" },
  };

  /* ── helpers ──────────────────────────────────────────── */
  function fmtDur(s) {
    if (!s) return null;
    return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
  }

  function mapCourse(data) {
    return (data.chapters || []).map(ch => ({
      id:            ch.id,
      name:          ch.title,
      isDraft:       ch.isDraft       || false,
      pendingDelete: ch.pendingDelete || false,
      items: (ch.lessons || []).map(l => ({
        lessonId:         l.id,
        title:            l.title,
        lessonType:       l.lessonType,
        dur:              fmtDur(l.durationSeconds),
        isDraft:          l.isDraft          || false,
        pendingDelete:    l.pendingDelete    || false,
        draftTitle:       l.draftTitle       || null,
        draftContentText: l.draftContentText || null,
        resources:        (l.resources || []).map(r => ({
          resourceId:   r.id,
          title:        r.displayName || r.originalFilename,
          resourceType: r.resourceType,
          kind:         r.resourceType?.toLowerCase(),
          externalUrl:  r.externalUrl || null,
          mimeType:     r.mimeType || null,
        })),
      })),
    }));
  }

  function Field({ label, children, hint, full }) {
    return (
      <div style={{ gridColumn: full ? "1 / -1" : "auto" }}>
        <label className="t-label" style={{ display: "block", marginBottom: 7 }}>{label}</label>
        {children}
        {hint && <div className="t-xs muted" style={{ marginTop: 6 }}>{hint}</div>}
      </div>
    );
  }

  function Dropzone({ icon, title, hint, h, file, onClick, onDrop }) {
    return (
      <div style={{ border: "2px dashed var(--border-strong)", borderRadius: 12, padding: h || 26, textAlign: "center", color: "var(--text-3)", cursor: "pointer", background: "var(--surface-2)", transition: ".15s" }}
        onClick={onClick}
        onMouseEnter={e => { e.currentTarget.style.borderColor = "var(--accent)"; e.currentTarget.style.background = "var(--accent-soft)"; }}
        onMouseLeave={e => { e.currentTarget.style.borderColor = "var(--border-strong)"; e.currentTarget.style.background = "var(--surface-2)"; }}
        onDragOver={e => { e.preventDefault(); e.currentTarget.style.borderColor = "var(--accent)"; }}
        onDragLeave={e => { e.currentTarget.style.borderColor = "var(--border-strong)"; }}
        onDrop={e => { e.preventDefault(); e.currentTarget.style.borderColor = "var(--border-strong)"; onDrop?.(e.dataTransfer.files?.[0]); }}>
        {file
          ? <div style={{ fontWeight: 600, color: "var(--text)", fontSize: 14 }}>{file.name} <span className="muted t-xs">({(file.size / 1024 / 1024).toFixed(1)} MB)</span></div>
          : <>
              <div className="stat-ic" style={{ width: 46, height: 46, borderRadius: 12, background: "#fff", color: "var(--accent)", margin: "0 auto 10px" }}><Ic n={icon} size={22} /></div>
              <div style={{ fontWeight: 600, fontSize: 14, color: "var(--text)" }}>{title}</div>
              <div className="t-xs" style={{ marginTop: 4 }}>{hint}</div>
            </>}
      </div>
    );
  }

  /* Inline edit tên chương / bài giảng */
  function InlineEdit({ value, onSave, style, placeholder }) {
    const [editing, setEditing] = useState(false);
    const [val, setVal]         = useState(value);
    const ref = useRef();
    useEffect(() => { setVal(value); }, [value]);
    useEffect(() => { if (editing) ref.current?.select(); }, [editing]);
    function commit() {
      setEditing(false);
      const t = val.trim();
      if (t && t !== value) onSave(t); else setVal(value);
    }
    if (editing) return (
      <input ref={ref} value={val} onChange={e => setVal(e.target.value)}
        onBlur={commit}
        onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); commit(); } if (e.key === "Escape") { setEditing(false); setVal(value); } }}
        onClick={e => e.stopPropagation()}
        style={{ background: "var(--surface)", border: "1px solid var(--accent)", borderRadius: 6, padding: "3px 8px", fontSize: "inherit", fontWeight: "inherit", color: "inherit", outline: "none", width: "100%", ...style }} />
    );
    return (
      <span style={{ cursor: "text", ...style }} title="Nhấn để đổi tên" onClick={e => { e.stopPropagation(); setEditing(true); }}>
        {value || <span style={{ color: "var(--text-3)", fontStyle: "italic" }}>{placeholder}</span>}
      </span>
    );
  }

  /* ── Modal thêm chương ──────────────────────────────────── */
  function AddChapterModal({ open, onClose, courseId, onAdded }) {
    const [title, setTitle]   = useState("");
    const [saving, setSaving] = useState(false);
    const [err, setErr]       = useState(null);
    const close = () => { setTitle(""); setErr(null); onClose(); };
    async function handleAdd() {
      if (!title.trim()) { setErr("Vui lòng nhập tên chương"); return; }
      setSaving(true); setErr(null);
      try { await api.post(`/instructor/courses/${courseId}/chapters`, { title: title.trim() }); close(); onAdded?.(); }
      catch (e) { setErr(e?.response?.data?.message || "Thêm chương thất bại"); }
      finally { setSaving(false); }
    }
    if (!open) return null;
    return (
      <Modal open={open} onClose={close} max={440}>
        <ModalHead title="Thêm chương mới" icon="layers" iconBg="#eaf1ff" iconColor="#2563eb" onClose={close} />
        <div className="modal-body">
          <label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên chương</label>
          <input className="input" value={title} onChange={e => setTitle(e.target.value)}
            placeholder="VD: Session 01 – Tổng quan & Cài đặt" autoFocus
            onKeyDown={e => e.key === "Enter" && handleAdd()} />
          {err && <div style={{ color: "var(--error)", fontSize: 13, marginTop: 8 }}>{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={close}>Hủy</button>
          <button className="btn btn-primary" disabled={saving} onClick={handleAdd}>
            <Ic n="plus" size={16} />{saving ? "Đang thêm..." : "Thêm chương"}
          </button>
        </div>
      </Modal>
    );
  }

  /* ── Modal tạo bài giảng (chỉ title) ───────────────────── */
  function AddLessonModal({ open, onClose, courseId, chapterId, onAdded }) {
    const [title, setTitle]   = useState("");
    const [saving, setSaving] = useState(false);
    const [err, setErr]       = useState(null);
    const close = () => { setTitle(""); setErr(null); onClose(); };
    async function handleAdd() {
      if (!title.trim()) { setErr("Vui lòng nhập tên bài giảng"); return; }
      if (title.trim().length < 3) { setErr("Tên phải có ít nhất 3 ký tự"); return; }
      setSaving(true); setErr(null);
      try {
        await api.post(`/instructor/courses/${courseId}/chapters/${chapterId}/lessons`, { title: title.trim(), type: "TEXT" });
        close(); onAdded?.();
      } catch (e) { setErr(e?.response?.data?.message || "Thêm bài giảng thất bại"); }
      finally { setSaving(false); }
    }
    if (!open) return null;
    return (
      <Modal open={open} onClose={close} max={460}>
        <ModalHead title="Thêm bài giảng" icon="book" iconBg="#f0fdf4" iconColor="#16a34a" onClose={close} />
        <div className="modal-body">
          <label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên bài giảng</label>
          <input className="input" value={title} onChange={e => setTitle(e.target.value)}
            placeholder="VD: Giới thiệu về React Hooks" autoFocus
            onKeyDown={e => e.key === "Enter" && handleAdd()} />
          {err && <div style={{ color: "var(--error)", fontSize: 13, marginTop: 8 }}>{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={close}>Hủy</button>
          <button className="btn btn-primary" disabled={saving} onClick={handleAdd}>
            <Ic n="plus" size={16} />{saving ? "Đang thêm..." : "Thêm bài giảng"}
          </button>
        </div>
      </Modal>
    );
  }

  /* ── Modal thêm nội dung vào bài giảng (video hoặc tài liệu) ── */
  function AddResourceModal({ open, onClose, courseId, lessonId, lessonTitle, onAdded }) {
    const [tab, setTab]             = useState("video");
    const [videoMode, setVideoMode] = useState("upload");
    const [videoFile, setVideoFile] = useState(null);
    const [videoName, setVideoName] = useState("");
    const [videoUrl, setVideoUrl]   = useState("");
    const [docFile, setDocFile]     = useState(null);
    const [docName, setDocName]     = useState("");
    const [saving, setSaving]       = useState(false);
    const [progress, setProgress]   = useState(0);
    const [err, setErr]             = useState(null);
    const videoFileRef = useRef();
    const docFileRef   = useRef();
    const nameRef      = useRef();

    const close = () => {
      setTab("video"); setVideoMode("upload");
      setVideoFile(null); setVideoName(""); setVideoUrl("");
      setDocFile(null); setDocName("");
      setSaving(false); setProgress(0); setErr(null); onClose();
    };

    function onVideoFilePick(f) {
      if (!f) return;
      if (f.size > 2 * 1024 * 1024 * 1024) { setErr("File vượt quá 2GB"); return; }
      setVideoFile(f);
      setVideoName(prev => prev || f.name.replace(/\.[^.]+$/, ""));
      setErr(null);
      setTimeout(() => { nameRef.current?.focus(); nameRef.current?.select(); }, 50);
    }
    function onDocFilePick(f) {
      if (!f) return;
      if (f.size > 200 * 1024 * 1024) { setErr("File vượt quá 200MB"); return; }
      setDocFile(f);
      setDocName(prev => prev || f.name.replace(/\.[^.]+$/, ""));
      setErr(null);
      setTimeout(() => { nameRef.current?.focus(); nameRef.current?.select(); }, 50);
    }

    async function handleSave() {
      setSaving(true); setErr(null); setProgress(0);
      try {
        if (tab === "video") {
          if (!videoFile && !videoUrl.trim()) { setErr("Vui lòng chọn file hoặc nhập URL"); setSaving(false); return; }
          if (videoUrl.trim()) { try { new URL(videoUrl.trim()); } catch { setErr("URL không hợp lệ"); setSaving(false); return; } }
          if (videoFile) {
            const displayName = videoName.trim() || videoFile.name.replace(/\.[^.]+$/, "");
            const { data: p } = await api.post(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/presign-upload`,
              { originalFilename: videoFile.name, mimeType: videoFile.type || "video/mp4", fileSizeBytes: videoFile.size, resourceType: "VIDEO", displayName });
            await new Promise((res, rej) => {
              const xhr = new XMLHttpRequest();
              xhr.open("PUT", p.presignedUrl); xhr.setRequestHeader("Content-Type", videoFile.type || "video/mp4");
              xhr.upload.onprogress = e => { if (e.lengthComputable) setProgress(Math.round(e.loaded / e.total * 100)); };
              xhr.onload = () => xhr.status < 300 ? res() : rej(new Error("Upload thất bại"));
              xhr.onerror = () => rej(new Error("Mất kết nối")); xhr.send(videoFile);
            });
            await api.post(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/confirm-upload`,
              { s3Key: p.s3Key, originalFilename: videoFile.name, resourceType: "VIDEO", displayName });
          } else {
            await api.post(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/confirm-upload`,
              { externalUrl: videoUrl.trim(), resourceType: "VIDEO", displayName: videoName.trim() || "Video" });
          }
        } else {
          if (!docFile) { setErr("Vui lòng chọn file tài liệu"); setSaving(false); return; }
          if (!docName.trim()) { setErr("Vui lòng nhập tên tài liệu"); setSaving(false); return; }
          const ext = docFile.name.split(".").pop()?.toLowerCase() || "";
          const resourceType = ext === "pdf" ? "PDF" : (ext === "doc" || ext === "docx") ? "DOC"
            : (ext === "ppt" || ext === "pptx") ? "SLIDE" : (["png","jpg","jpeg","gif"].includes(ext)) ? "IMAGE" : "OTHER";
          const { data: p } = await api.post(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/presign-upload`,
            { originalFilename: docFile.name, mimeType: docFile.type || "application/octet-stream", fileSizeBytes: docFile.size, resourceType, displayName: docName.trim() });
          await new Promise((res, rej) => {
            const xhr = new XMLHttpRequest();
            xhr.open("PUT", p.presignedUrl); xhr.setRequestHeader("Content-Type", docFile.type || "application/octet-stream");
            xhr.upload.onprogress = e => { if (e.lengthComputable) setProgress(Math.round(e.loaded / e.total * 100)); };
            xhr.onload = () => xhr.status < 300 ? res() : rej(new Error("Upload thất bại"));
            xhr.onerror = () => rej(new Error("Mất kết nối")); xhr.send(docFile);
          });
          await api.post(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/confirm-upload`,
            { s3Key: p.s3Key, originalFilename: docFile.name, resourceType, displayName: docName.trim() });
        }
        close(); onAdded?.();
      } catch (e) { setErr(e?.response?.data?.message || e?.message || "Thao tác thất bại"); }
      finally { setSaving(false); }
    }

    if (!open) return null;
    return (
      <Modal open={open} onClose={close} max={560}>
        <ModalHead title="Thêm nội dung" sub={lessonTitle} icon="plus" iconBg="#f0fdf4" iconColor="#16a34a" onClose={close} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div className="tabs" style={{ width: "fit-content" }}>
            <button className={tab === "video" ? "on" : ""} onClick={() => { setTab("video"); setErr(null); }}>Video</button>
            <button className={tab === "doc" ? "on" : ""} onClick={() => { setTab("doc"); setErr(null); }}>Tài liệu</button>
          </div>

          {tab === "video" && (<>
            <div className="tabs" style={{ width: "fit-content" }}>
              <button className={videoMode === "upload" ? "on" : ""} onClick={() => setVideoMode("upload")}>Tải lên</button>
              <button className={videoMode === "url" ? "on" : ""} onClick={() => setVideoMode("url")}>Nhúng URL</button>
            </div>
            {videoMode === "upload" ? (<>
              <input ref={videoFileRef} type="file" accept="video/mp4,video/quicktime,video/webm" style={{ display: "none" }}
                onChange={e => onVideoFilePick(e.target.files?.[0])} />
              <Dropzone icon="video" title="Kéo thả file video vào đây" hint="MP4, MOV, WebM · tối đa 2GB"
                h={32} file={videoFile} onClick={() => videoFileRef.current?.click()} onDrop={onVideoFilePick} />
              {videoFile && (
                <Field label="Tên hiển thị video">
                  <input ref={nameRef} className="input" value={videoName} onChange={e => setVideoName(e.target.value)}
                    placeholder={videoFile.name.replace(/\.[^.]+$/, "")} />
                </Field>
              )}
              {saving && videoFile && (
                <div>
                  <div style={{ height: 5, borderRadius: 999, background: "var(--border)", overflow: "hidden" }}>
                    <div style={{ width: progress + "%", height: "100%", background: "var(--accent)", transition: "width .2s" }} />
                  </div>
                  <div className="t-xs muted" style={{ marginTop: 4, textAlign: "center" }}>Đang upload... {progress}%</div>
                </div>
              )}
            </>) : (<>
              <Field label="Đường dẫn video (YouTube, MP4 URL...)">
                <input className="input" value={videoUrl} onChange={e => setVideoUrl(e.target.value)} placeholder="https://..." autoFocus />
              </Field>
              <Field label="Tên hiển thị">
                <input ref={nameRef} className="input" value={videoName} onChange={e => setVideoName(e.target.value)} placeholder="VD: Bài giảng Chương 1" />
              </Field>
            </>)}
          </>)}

          {tab === "doc" && (<>
            <input ref={docFileRef} type="file" accept=".pdf,.pptx,.ppt,.docx,.doc,.xlsx,.xls,.png,.jpg,.jpeg" style={{ display: "none" }}
              onChange={e => onDocFilePick(e.target.files?.[0])} />
            <Dropzone icon="upload" title="Kéo thả tài liệu vào đây" hint="PDF, PPTX, DOCX, ảnh · tối đa 200MB"
              h={32} file={docFile} onClick={() => docFileRef.current?.click()} onDrop={onDocFilePick} />
            <Field label="Tên hiển thị">
              <input ref={nameRef} className="input" value={docName} onChange={e => setDocName(e.target.value)}
                placeholder="VD: Giáo trình chương 1" autoFocus={!docFile} />
            </Field>
            {saving && docFile && (
              <div>
                <div style={{ height: 5, borderRadius: 999, background: "var(--border)", overflow: "hidden" }}>
                  <div style={{ width: progress + "%", height: "100%", background: "var(--accent)", transition: "width .2s" }} />
                </div>
                <div className="t-xs muted" style={{ marginTop: 4, textAlign: "center" }}>Đang upload... {progress}%</div>
              </div>
            )}
          </>)}

          {err && <div style={{ color: "var(--error)", fontSize: 13 }}>{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={close}>Hủy</button>
          <button className="btn btn-primary" disabled={saving} onClick={handleSave}>
            <Ic n="plus" size={16} />{saving ? "Đang lưu..." : "Thêm"}
          </button>
        </div>
      </Modal>
    );
  }

  /* ── Modal xem & cập nhật tài liệu/video ─────────────────── */
  function EditResourceInner({ s, rm, isVideo, isImage, isYT, ytId, onClose, onSave, courseId, onReplaced }) {
    const [name, setName]               = useState(s.title);
    const [replaceOpen, setReplaceOpen] = useState(false);
    const [newFile, setNewFile]         = useState(null);
    const [newFileUrl, setNewFileUrl]   = useState(null);
    const [newUrl, setNewUrl]           = useState(s.externalUrl || "");
    const [urlMode, setUrlMode]         = useState(false);
    const [saving, setSaving]           = useState(false);
    const [progress, setProgress]       = useState(0);
    const [err, setErr]                 = useState(null);
    const [viewUrl, setViewUrl]         = useState(s.externalUrl || null);
    const [viewLoading, setViewLoading] = useState(false);
    const fileRef = useRef();

    const canEmbed = s.externalUrl && s.externalUrl.match(/\.(mp4|webm|ogg)(\?.*)?$/i);

    // Fetch presigned URL cho file S3 (khi không có externalUrl)
    useEffect(() => {
      if (s.externalUrl || !s.resourceId) return;
      setViewLoading(true);
      api.get(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/${s.resourceId}/download-url`)
        .then(r => setViewUrl(r.data?.url || null))
        .catch(() => {})
        .finally(() => setViewLoading(false));
    }, [s.resourceId]);

    // Object URL cho file mới chọn
    useEffect(() => {
      if (!newFile) { setNewFileUrl(null); return; }
      const url = URL.createObjectURL(newFile);
      setNewFileUrl(url);
      return () => URL.revokeObjectURL(url);
    }, [newFile]);

    function detectResourceType(file) {
      const ext = file.name.split(".").pop()?.toLowerCase() || "";
      if (file.type.startsWith("video/") || ext === "mp4" || ext === "mov" || ext === "webm") return "VIDEO";
      if (file.type === "application/pdf" || ext === "pdf") return "PDF";
      if (ext === "ppt" || ext === "pptx") return "SLIDE";
      if (ext === "doc" || ext === "docx") return "DOC";
      if (file.type.startsWith("image/") || ["png","jpg","jpeg","gif","webp"].includes(ext)) return "IMAGE";
      return "OTHER";
    }

    function onReplacePick(f) {
      if (!f) return;
      const detectedType = detectResourceType(f);
      const isVid = detectedType === "VIDEO";
      const limit = isVid ? 2 * 1024 * 1024 * 1024 : 200 * 1024 * 1024;
      const label = isVid ? "2GB" : "200MB";
      if (f.size > limit) { setErr(`File vượt quá ${label}`); return; }
      setNewFile(f); setErr(null);
    }

    function fmtBytes(b) {
      if (!b) return "";
      if (b < 1024 * 1024) return (b / 1024).toFixed(1) + " KB";
      return (b / (1024 * 1024)).toFixed(1) + " MB";
    }

    function DocViewer({ url, type, height = 300 }) {
      const isDoc = type === "SLIDE" || type === "DOC";
      const isPdf = type === "PDF";
      if (!url) return null;
      if (isPdf) return (
        <iframe src={url} style={{ width: "100%", height, border: "none", display: "block" }} title="preview" />
      );
      if (isDoc) return (
        <iframe src={`https://docs.google.com/viewer?url=${encodeURIComponent(url)}&embedded=true`}
          style={{ width: "100%", height, border: "none", display: "block" }} title="preview" />
      );
      return null;
    }

    function NewFilePreview() {
      if (!newFile || !newFileUrl) return null;
      const detectedType = detectResourceType(newFile);
      const newRm = RES_TYPE[detectedType] || RES_TYPE.OTHER;
      const isImg = detectedType === "IMAGE";
      const isVid = detectedType === "VIDEO";
      const isDoc = ["PDF","SLIDE","DOC"].includes(detectedType);
      return (
        <div style={{ borderRadius: 10, overflow: "hidden", border: "2px solid var(--accent)", background: "var(--surface-2)" }}>
          <div className="row gap-8" style={{ padding: "8px 12px", background: "var(--chip-info-bg)", justifyContent: "space-between" }}>
            <span style={{ fontSize: 12, fontWeight: 600, color: "var(--chip-info-fg)" }}>
              Xem trước file mới · {detectedType}
            </span>
            <button className="btn btn-ghost btn-sm" style={{ padding: "2px 8px", fontSize: 11 }}
              onClick={() => { setNewFile(null); fileRef.current?.click(); }}>Đổi file</button>
          </div>
          {isImg && <img src={newFileUrl} style={{ width: "100%", maxHeight: 240, objectFit: "contain", display: "block" }} />}
          {isVid && <video src={newFileUrl} controls style={{ width: "100%", maxHeight: 240, display: "block", background: "#000" }} />}
          {isDoc && <DocViewer url={newFileUrl} type={detectedType} height={280} />}
          {!isImg && !isVid && !isDoc && (
            <div className="row gap-12" style={{ padding: "14px 16px" }}>
              <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 9, background: newRm.bg, color: newRm.fg, flex: "none" }}>
                <Ic n={newRm.ic} size={18} />
              </div>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: 13 }} className="truncate">{newFile.name}</div>
                <div className="muted t-xs" style={{ marginTop: 2 }}>{fmtBytes(newFile.size)}</div>
              </div>
            </div>
          )}
        </div>
      );
    }

    async function handleSave() {
      if (!name.trim()) { setErr("Tên không được để trống"); return; }
      setSaving(true); setErr(null); setProgress(0);
      try {
        if (replaceOpen) {
          if (!newFile && !newUrl.trim()) { setErr("Vui lòng chọn file hoặc nhập URL"); return; }
          if (newUrl.trim()) {
            try { new URL(newUrl.trim()); } catch { setErr("URL không hợp lệ"); return; }
            // Tạo tài liệu mới trước, xóa cũ sau để tránh mất dữ liệu nếu có lỗi
            await api.post(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/confirm-upload`,
              { externalUrl: newUrl.trim(), resourceType: s.resourceType, displayName: name.trim() });
            await api.delete(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/${s.resourceId}`);
          } else {
            const detectedType = detectResourceType(newFile);
            const { data: p } = await api.post(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/presign-upload`,
              { originalFilename: newFile.name, mimeType: newFile.type || "application/octet-stream", fileSizeBytes: newFile.size, resourceType: detectedType, displayName: name.trim() });
            await new Promise((res, rej) => {
              const xhr = new XMLHttpRequest();
              xhr.open("PUT", p.presignedUrl);
              xhr.setRequestHeader("Content-Type", newFile.type || "application/octet-stream");
              xhr.upload.onprogress = e => { if (e.lengthComputable) setProgress(Math.round(e.loaded / e.total * 100)); };
              xhr.onload = () => xhr.status < 300 ? res() : rej(new Error("Upload thất bại"));
              xhr.onerror = () => rej(new Error("Mất kết nối"));
              xhr.send(newFile);
            });
            // Confirm upload mới thành công trước, sau đó mới xóa cũ
            await api.post(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/confirm-upload`,
              { s3Key: p.s3Key, originalFilename: newFile.name, resourceType: detectedType, displayName: name.trim() });
            await api.delete(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/${s.resourceId}`);
          }
          onReplaced?.();
        } else {
          onSave(name.trim());
        }
      } catch (e) { setErr(e?.response?.data?.message || e?.message || "Thao tác thất bại"); }
      finally { setSaving(false); }
    }

    return (
      <Modal open onClose={onClose} max={580}>
        <ModalHead title="Chỉnh sửa tài liệu" icon="edit" iconBg={rm.bg} iconColor={rm.fg} onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 14 }}>

          {/* Preview file hiện tại */}
          <div style={{ borderRadius: 10, overflow: "hidden", background: "var(--surface-2)", border: "1px solid var(--border)" }}>
            {isYT && ytId ? (
              <div style={{ position: "relative", paddingBottom: "56.25%", height: 0 }}>
                <iframe src={`https://www.youtube.com/embed/${ytId}`}
                  style={{ position: "absolute", inset: 0, width: "100%", height: "100%", border: "none" }} allowFullScreen />
              </div>
            ) : isVideo && (viewUrl || canEmbed) ? (
              <video src={viewUrl || s.externalUrl} controls style={{ width: "100%", maxHeight: 260, display: "block", background: "#000" }} />
            ) : isImage && viewUrl ? (
              <img src={viewUrl} style={{ width: "100%", maxHeight: 260, objectFit: "contain", display: "block" }} />
            ) : (s.resourceType === "PDF" || s.resourceType === "SLIDE" || s.resourceType === "DOC") ? (
              viewLoading
                ? <div style={{ padding: "28px 0", textAlign: "center", color: "var(--text-3)", fontSize: 13 }}>Đang tải xem trước...</div>
                : viewUrl
                  ? <DocViewer url={viewUrl} type={s.resourceType} height={340} />
                  : <div style={{ padding: "20px 0", textAlign: "center", color: "var(--text-3)", fontSize: 13 }}>Không thể tải xem trước</div>
            ) : (
              <div className="row gap-12" style={{ padding: "16px 20px" }}>
                <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 10, background: rm.bg, color: rm.fg, flex: "none" }}>
                  <Ic n={rm.ic} size={20} />
                </div>
                <div className="grow" style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: 14 }} className="truncate">{s.title}</div>
                  <div className="muted t-xs" style={{ marginTop: 2 }}>{(s.resourceType || "").toUpperCase()}</div>
                </div>
                {viewUrl && (
                  <a href={viewUrl} target="_blank" rel="noreferrer" className="btn btn-ghost btn-sm">
                    <Ic n="external_link" size={14} />Mở file
                  </a>
                )}
              </div>
            )}
          </div>

          {/* Tên hiển thị */}
          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên hiển thị</label>
            <input className="input" value={name} onChange={e => setName(e.target.value)} autoFocus
              onKeyDown={e => { if (e.key === "Enter" && !replaceOpen) handleSave(); }} />
          </div>

          {/* Toggle thay thế */}
          <button className="btn btn-ghost btn-sm" style={{ alignSelf: "flex-start", gap: 6 }}
            onClick={() => { setReplaceOpen(v => !v); setErr(null); setNewFile(null); }}>
            <Ic n={replaceOpen ? "chevron_down" : "chevron_right"} size={14} />
            {replaceOpen ? "Ẩn thay thế nội dung" : "Thay thế nội dung"}
          </button>

          {replaceOpen && (
            <div style={{ padding: 14, borderRadius: 10, border: "1px solid var(--border)", display: "flex", flexDirection: "column", gap: 12, background: "var(--surface-2)" }}>
              {isVideo && (
                <div className="tabs" style={{ width: "fit-content" }}>
                  <button className={!urlMode ? "on" : ""} onClick={() => { setUrlMode(false); }}>Tải file lên</button>
                  <button className={urlMode ? "on" : ""} onClick={() => { setUrlMode(true); setNewFile(null); }}>Nhúng URL</button>
                </div>
              )}
              {(isVideo && urlMode) ? (
                <div>
                  <label className="t-label" style={{ display: "block", marginBottom: 7 }}>URL mới</label>
                  <input className="input" value={newUrl} onChange={e => setNewUrl(e.target.value)} placeholder="https://..." autoFocus />
                </div>
              ) : (
                <>
                  <input ref={fileRef} type="file" style={{ display: "none" }}
                    accept={isVideo ? "video/mp4,video/quicktime,video/webm" : ".pdf,.pptx,.ppt,.docx,.doc,.xlsx,.xls,.png,.jpg,.jpeg"}
                    onChange={e => onReplacePick(e.target.files?.[0])} />
                  {newFile
                    ? <NewFilePreview />
                    : <Dropzone icon={isVideo ? "video" : "upload"}
                        title={isVideo ? "Chọn file video mới" : "Chọn file tài liệu mới"}
                        hint={isVideo ? "MP4, MOV, WebM · tối đa 2GB" : "PDF, PPTX, DOCX · tối đa 200MB"}
                        h={20} file={null} onClick={() => fileRef.current?.click()} onDrop={onReplacePick} />
                  }
                </>
              )}
              {saving && newFile && (
                <div>
                  <div style={{ height: 5, borderRadius: 999, background: "var(--border)", overflow: "hidden" }}>
                    <div style={{ width: progress + "%", height: "100%", background: "var(--accent)", transition: "width .2s" }} />
                  </div>
                  <div className="t-xs muted" style={{ textAlign: "center", marginTop: 4 }}>Đang upload... {progress}%</div>
                </div>
              )}
            </div>
          )}

          {err && <div style={{ color: "var(--error)", fontSize: 13 }}>{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
          <button className="btn btn-primary" disabled={saving} onClick={handleSave}>
            <Ic n="check" size={15} />{saving ? "Đang lưu..." : replaceOpen ? "Lưu & Thay thế" : "Lưu tên"}
          </button>
        </div>
      </Modal>
    );
  }

  /* ── Trang chi tiết khóa học ─────────────────────────────── */
  function InsCourseDetail({ nav }) {
    const courseId = window.__selectedCourseId || sessionStorage.getItem("selectedCourseId") || null;

    const [course, setCourse]     = useState(null);
    const [chapters, setChapters] = useState([]);
    const [loading, setLoading]   = useState(true);
    const [err, setErr]           = useState(null);
    const [tab, setTab]           = useState("content");
    const [open, setOpen]         = useState(0);
    const [submitting, setSubmitting] = useState(false);
    const [submitMsg, setSubmitMsg]   = useState(null);

    const [editTitle,   setEditTitle]   = useState("");
    const [editDesc,    setEditDesc]    = useState("");
    const [editLevel,   setEditLevel]   = useState(LEVELS[1].value);
    const [editCat,     setEditCat]     = useState(null);
    const [categories,  setCategories]  = useState([]);
    const [infoSaving,  setInfoSaving]  = useState(false);
    const [infoMsg,     setInfoMsg]     = useState(null);

    const [addChapterOpen,    setAddChapterOpen]    = useState(false);
    const [addLessonState,    setAddLessonState]    = useState(null);
    const [addResourceState,  setAddResourceState]  = useState(null);
    const [renameLessonState, setRenameLessonState] = useState(null);
    const [editResourceState, setEditResourceState] = useState(null);
    const [showPreview,       setShowPreview]       = useState(false);
    const [history,           setHistory]           = useState([]);
    const [historyLoading,    setHistoryLoading]    = useState(false);

    function loadCourse(silent = false) {
      if (!courseId) { setLoading(false); return; }
      if (!silent) { setLoading(true); setErr(null); }
      const scrollEl = document.querySelector(".page");
      const savedScroll = scrollEl?.scrollTop ?? 0;
      api.get(`/instructor/courses/${courseId}`)
        .then(r => {
          setCourse(r.data);
          setChapters(mapCourse(r.data));
          // Nếu đang live, hiện draft values trong form để instructor thấy và chỉnh tiếp
          const isLiveStatus = r.data.status === "PUBLISHED" || r.data.status === "PENDING_UPDATE";
          setEditTitle((isLiveStatus && r.data.draftTitle)       ? r.data.draftTitle       : r.data.title       || "");
          setEditDesc ((isLiveStatus && r.data.draftDescription) ? r.data.draftDescription : r.data.description || "");
          setEditLevel((isLiveStatus && r.data.draftLevel)       ? r.data.draftLevel       : r.data.level       || LEVELS[1].value);
          setEditCat(r.data.category?.id || null);
          if (silent) requestAnimationFrame(() => { scrollEl?.scrollTo({ top: savedScroll }); });
        })
        .catch(e => setErr(e?.response?.data?.message || "Không thể tải khóa học"))
        .finally(() => { if (!silent) setLoading(false); });
    }
    useEffect(() => { loadCourse(); }, [courseId]);
    useEffect(() => {
      api.get("/instructor/courses/categories").then(r => setCategories(r.data || [])).catch(() => {});
    }, []);

    async function handleSubmit() {
      setSubmitting(true); setSubmitMsg(null);
      try { await api.put(`/instructor/courses/${courseId}/submit`); setSubmitMsg("Đã gửi duyệt thành công!"); loadCourse(true); }
      catch (e) { setSubmitMsg(e?.response?.data?.message || "Gửi duyệt thất bại"); }
      finally { setSubmitting(false); }
    }

    async function handleWithdraw() {
      const isPendingUpdate = course?.status === "PENDING_UPDATE";
      const msg = isPendingUpdate
        ? "Hủy toàn bộ thay đổi đang chờ duyệt? Khóa học sẽ trở về trạng thái live gốc."
        : "Rút khỏi hàng chờ duyệt và chuyển về Bản nháp?";
      if (!confirm(msg)) return;
      setSubmitting(true); setSubmitMsg(null);
      try {
        await api.put(`/instructor/courses/${courseId}/withdraw`);
        setSubmitMsg(isPendingUpdate ? "Đã hủy cập nhật – khóa học trở về trạng thái đang xuất bản." : "Đã rút duyệt – khóa học trở về Bản nháp.");
        loadCourse(true);
      }
      catch (e) { setSubmitMsg(e?.response?.data?.message || "Thao tác thất bại"); }
      finally { setSubmitting(false); }
    }

    async function handleSaveInfo() {
      if (!editTitle.trim()) { setInfoMsg("Vui lòng nhập tên khóa học"); return; }
      setInfoSaving(true); setInfoMsg(null);
      try {
        await api.put(`/instructor/courses/${courseId}`, {
          title: editTitle.trim(),
          description: editDesc.trim() || null,
          level: editLevel,
          categoryId: editCat || null,
        });
        setInfoMsg("Đã lưu thay đổi!"); loadCourse(true);
      } catch (e) { setInfoMsg(e?.response?.data?.message || "Lưu thất bại"); }
      finally { setInfoSaving(false); }
    }

    async function handleRenameChapter(chapterId, newTitle) {
      try { await api.put(`/instructor/courses/${courseId}/chapters/${chapterId}`, { title: newTitle }); loadCourse(true); } catch { }
    }
    async function handleRenameLesson(chapterId, lessonId, newTitle) {
      try { await api.put(`/instructor/courses/${courseId}/chapters/${chapterId}/lessons/${lessonId}`, { title: newTitle }); loadCourse(true); } catch { }
    }
    async function handleDeleteChapter(chapterId) {
      if (!confirm("Xóa chương này và toàn bộ bài giảng bên trong?")) return;
      try { await api.delete(`/instructor/courses/${courseId}/chapters/${chapterId}`); loadCourse(true); }
      catch (e) { alert(e?.response?.data?.message || "Xóa thất bại"); }
    }
    async function handleDeleteLesson(chapterId, lessonId) {
      if (!confirm("Xóa bài giảng này?")) return;
      try { await api.delete(`/instructor/courses/${courseId}/chapters/${chapterId}/lessons/${lessonId}`); loadCourse(true); }
      catch (e) { alert(e?.response?.data?.message || "Xóa thất bại"); }
    }
    async function handleDeleteResource(lessonId, resourceId) {
      if (!confirm("Xóa tài liệu này?")) return;
      try { await api.delete(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/${resourceId}`); loadCourse(true); }
      catch (e) { alert(e?.response?.data?.message || "Xóa thất bại"); }
    }
    async function handleRenameResource(lessonId, resourceId, newTitle) {
      try { await api.patch(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/${resourceId}`, { displayName: newTitle }); loadCourse(true); }
      catch (e) { alert(e?.response?.data?.message || "Đổi tên thất bại"); }
    }

    /* guards */
    if (!courseId) return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Chi tiết Khóa học</h1></div>
        <Empty icon="book" title="Chưa chọn khóa học" sub="Quay lại danh sách và chọn một khóa học." />
        <button className="btn btn-ghost" style={{ marginTop: 24 }} onClick={() => nav("courses")}><Ic n="arrow_left" size={16} />Quay lại</button>
      </div>
    );
    if (loading) return <div className="page fade-in"><div className="muted" style={{ textAlign: "center", padding: 80 }}>Đang tải...</div></div>;
    if (err) return (
      <div className="page fade-in">
        <div style={{ color: "var(--error)", padding: 24 }}>{err}</div>
        <button className="btn btn-ghost" onClick={() => nav("courses")}><Ic n="arrow_left" size={16} />Quay lại</button>
      </div>
    );

    const canEdit = ["DRAFT", "REJECTED", "PUBLISHED", "PENDING_UPDATE"].includes(course?.status);
    const isLive  = course?.status === "PUBLISHED" || course?.status === "PENDING_UPDATE";
    const sc      = STATUS_COLOR[course?.status] || {};

    const allLessons   = chapters.flatMap(ch => ch.items);
    const allResources = allLessons.flatMap(l => l.resources || []);
    const counts = {
      lessons: allLessons.length,
      VIDEO:   allResources.filter(r => r.resourceType === "VIDEO").length,
      doc:     allResources.filter(r => r.resourceType !== "VIDEO").length,
    };

    return (
      <div className="page fade-in">
        {/* Header */}
        <div className="page-head between" style={{ marginBottom: 20 }}>
          <button className="btn btn-ghost btn-sm" onClick={() => nav("courses")} style={{ gap: 6 }}>
            <Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách khóa học</span>
          </button>
          <div className="row gap-10">
            <button className="btn btn-ghost btn-sm" onClick={() => setShowPreview(true)}><Ic n="eye" size={15} />Xem trước</button>
            {(course?.status === "DRAFT" || course?.status === "REJECTED") && (
              <button className="btn btn-success btn-sm" disabled={submitting} onClick={handleSubmit}><Ic n="send" size={15} />{submitting ? "Đang gửi..." : "Gửi duyệt"}</button>
            )}
            {course?.status === "PENDING_UPDATE" && course?.hasPendingDraft && (
              <button className="btn btn-success btn-sm" disabled={submitting} onClick={handleSubmit}><Ic n="send" size={15} />{submitting ? "Đang gửi..." : "Gửi cập nhật"}</button>
            )}
            {(course?.status === "PENDING" || course?.status === "PENDING_UPDATE") && (
              <button className="btn btn-ghost btn-sm" disabled={submitting} onClick={handleWithdraw}>
                {course?.status === "PENDING_UPDATE" ? "Hủy cập nhật" : "Rút duyệt"}
              </button>
            )}
          </div>
        </div>

        {/* Course title + status */}
        <div className="row gap-14" style={{ marginBottom: 16, alignItems: "flex-start" }}>
          <div className="grow">
            <h1 className="t-h1" style={{ marginBottom: 4 }}>{course?.title}</h1>
            <div className="row gap-10 wrap">
              <span className="chip" style={{ background: sc.bg, color: sc.color, fontWeight: 600 }}>{STATUS_LABEL[course?.status] || course?.status}</span>
              {course?.level && <span className="meta-row"><Ic n="layers" size={15} /> {course.level}</span>}
              <span className="meta-row"><Ic n="layers" size={15} /> {chapters.length} chương</span>
              <span className="meta-row"><Ic n="video" size={15} /> {counts.VIDEO} video</span>
              <span className="meta-row"><Ic n="file" size={15} /> {counts.doc} tài liệu</span>
            </div>
          </div>
        </div>

        {/* Banner: draft rejection reason (update bị từ chối) */}
        {course?.draftRejectionReason && (
          <div style={{ padding: "12px 16px", borderRadius: 10, marginBottom: 12, fontSize: 13.5, background: "#fee2e2", color: "#dc2626", display: "flex", gap: 10, alignItems: "center" }}>
            <Ic n="x" size={16} style={{ flex: "none" }} />
            <span><strong>Cập nhật bị từ chối:</strong> {course.draftRejectionReason}</span>
          </div>
        )}

        {/* Banner: có thay đổi đang chờ duyệt */}
        {course?.status === "PENDING_UPDATE" && (
          <div style={{ padding: "12px 16px", borderRadius: 10, marginBottom: 12, fontSize: 13.5, background: "#e0f2fe", color: "#0284c7", display: "flex", gap: 10, alignItems: "center" }}>
            <Ic n="eye" size={16} style={{ flex: "none" }} />
            <span>Có thay đổi đang chờ admin duyệt. Khóa học vẫn hiển thị cho học viên theo nội dung cũ. Bạn có thể tiếp tục chỉnh sửa hoặc <strong>Hủy cập nhật</strong> để hủy toàn bộ thay đổi.</span>
          </div>
        )}

        {/* Banner: đang published — chỉnh sửa sẽ tạo bản draft */}
        {course?.status === "PUBLISHED" && (
          <div style={{ padding: "12px 16px", borderRadius: 10, marginBottom: 12, fontSize: 13.5, background: "#f0fdf4", color: "#15803d", display: "flex", gap: 10, alignItems: "center" }}>
            <Ic n="book" size={16} style={{ flex: "none" }} />
            <span>Khóa học đang được xuất bản. Mọi thay đổi sẽ tạo bản nháp và gửi admin duyệt trước khi áp dụng.</span>
          </div>
        )}

        {submitMsg && (
          <div style={{ padding: "10px 16px", borderRadius: 10, marginBottom: 14, fontSize: 13.5,
            background: submitMsg.startsWith("Đã") ? "#dcfce7" : "#fee2e2",
            color: submitMsg.startsWith("Đã") ? "#16a34a" : "#dc2626" }}>{submitMsg}</div>
        )}

        {/* Stat cards */}
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="layers" iconBg="#e7f8f0" iconColor="#059669" value={chapters.length}  label="Số chương" />
          <StatCard icon="book"   iconBg="#f0fdf4" iconColor="#16a34a" value={counts.lessons}   label="Số bài giảng" />
          <StatCard icon="video"  iconBg="#eaf1ff" iconColor="#2563eb" value={counts.VIDEO}     label="Video" />
          <StatCard icon="file"   iconBg="#fef5e6" iconColor="#d97706" value={counts.doc}       label="Tài liệu" />
        </div>

        {/* Tabs */}
        <div className="toolbar">
          <Tabs items={[
            { v: "content", label: "Nội dung khóa học" },
            { v: "info",    label: "Thông tin" },
            { v: "history", label: "Lịch sử duyệt" },
          ]} value={tab} onChange={v => {
            setTab(v);
            if (v === "history" && courseId && history.length === 0) {
              setHistoryLoading(true);
              api.get(`/instructor/courses/${courseId}/history`)
                .then(r => setHistory(r.data || []))
                .catch(() => {})
                .finally(() => setHistoryLoading(false));
            }
          }} />
        </div>

        {/* ── Content tab ── */}
        {tab === "content" && (
          <Section>
            <div className="between" style={{ marginBottom: 16 }}>
              <h2 className="t-h2">Chương trình học</h2>
              {canEdit && (
                <button className="btn btn-ghost btn-sm" onClick={() => setAddChapterOpen(true)}>
                  <Ic n="plus" size={15} />Thêm chương
                </button>
              )}
            </div>

            {chapters.length === 0 && (
              <Empty icon="layers" title="Chưa có chương nào" sub="Nhấn 'Thêm chương' để bắt đầu xây dựng nội dung." />
            )}

            {chapters.map((ch, ci) => (
              <div key={ch.id} style={{ border: "1px solid var(--border)", borderRadius: 12, marginBottom: 10, overflow: "hidden",
                opacity: ch.pendingDelete ? 0.55 : 1,
                outline: ch.isDraft ? "2px solid #22c55e" : ch.pendingDelete ? "2px solid #ef4444" : "none" }}>
                <div className="row gap-12" style={{ padding: "12px 16px", background: "var(--surface-2)", cursor: "pointer", userSelect: "none" }}
                  onClick={() => setOpen(open === ci ? -1 : ci)}>
                  <Ic n="chevron_down" size={18} style={{ transform: open === ci ? "none" : "rotate(-90deg)", transition: ".2s", color: "var(--text-3)", flexShrink: 0 }} />
                  <div className="grow" style={{ fontWeight: 600, fontSize: 14.5, display: "flex", alignItems: "center", gap: 8 }}>
                    {canEdit
                      ? <InlineEdit value={ch.name} onSave={t => handleRenameChapter(ch.id, t)} placeholder="Tên chương..." />
                      : ch.name}
                    {ch.isDraft       && <span className="chip" style={{ fontSize: 10.5, padding: "1px 7px", background: "#dcfce7", color: "#16a34a", fontWeight: 700, flex: "none" }}>MỚI</span>}
                    {ch.pendingDelete && <span className="chip" style={{ fontSize: 10.5, padding: "1px 7px", background: "#fee2e2", color: "#dc2626", fontWeight: 700, flex: "none" }}>Chờ xóa</span>}
                  </div>
                  <span className="muted t-xs">{ch.items.length} bài giảng</span>
                  {canEdit && (
                    <button className="icon-btn" style={{ width: 34, height: 34, color: "var(--error)" }}
                      onClick={e => { e.stopPropagation(); handleDeleteChapter(ch.id); }}>
                      <Ic n="x" size={15} />
                    </button>
                  )}
                </div>

                {open === ci && (
                  <div style={{ padding: "8px 0" }}>
                    {ch.items.length === 0 && (
                      <div className="muted t-xs" style={{ padding: "12px 24px" }}>Chưa có bài giảng nào.</div>
                    )}
                    {ch.items.map(lesson => {
                      const videoCount = (lesson.resources || []).filter(r => r.resourceType === "VIDEO").length;
                      const docCount   = (lesson.resources || []).filter(r => r.resourceType !== "VIDEO").length;
                      return (
                        <div key={lesson.lessonId}>
                          <div className="row gap-12" style={{ padding: "10px 16px 10px 24px", borderBottom: "1px solid var(--border-soft)",
                            opacity: lesson.pendingDelete ? 0.5 : 1,
                            background: lesson.isDraft ? "rgba(34,197,94,.04)" : "transparent" }}>
                            <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 10, background: "#f0fdf4", color: "#16a34a", flex: "none" }}>
                              <Ic n="book" size={17} />
                            </div>
                            <div className="grow" style={{ minWidth: 0 }}>
                              <div style={{ fontWeight: 500, fontSize: 14, display: "flex", alignItems: "center", gap: 7 }} className="truncate">
                                {canEdit
                                  ? <InlineEdit value={lesson.title} onSave={t => handleRenameLesson(ch.id, lesson.lessonId, t)} />
                                  : <span style={{ textDecoration: lesson.pendingDelete ? "line-through" : "none" }}>{lesson.title}</span>}
                                {lesson.isDraft       && <span className="chip" style={{ fontSize: 10, padding: "1px 6px", background: "#dcfce7", color: "#16a34a", fontWeight: 700, flex: "none" }}>MỚI</span>}
                                {lesson.pendingDelete && <span className="chip" style={{ fontSize: 10, padding: "1px 6px", background: "#fee2e2", color: "#dc2626", fontWeight: 700, flex: "none" }}>Chờ xóa</span>}
                              </div>
                              {lesson.draftTitle && (
                                <div className="t-xs" style={{ marginTop: 2, color: "#0284c7" }}>
                                  Tên mới (chờ duyệt): <strong>{lesson.draftTitle}</strong>
                                </div>
                              )}
                              <div className="row gap-8 wrap" style={{ marginTop: 3 }}>
                                <span className="chip" style={{ background: "#f0fdf4", color: "#16a34a", fontSize: 11, padding: "1px 8px" }}>Bài giảng</span>
                                {videoCount > 0 && <span className="muted t-xs">{videoCount} video</span>}
                                {docCount > 0   && <span className="muted t-xs">{docCount} tài liệu</span>}
                                {lesson.dur     && <span className="muted t-xs">{lesson.dur}</span>}
                              </div>
                            </div>
                            {canEdit && <>
                              <button className="icon-btn" style={{ width: 32, height: 32 }} title="Đổi tên bài giảng"
                                onClick={() => setRenameLessonState({ chapterId: ch.id, lessonId: lesson.lessonId, title: lesson.title })}>
                                <Ic n="edit" size={15} />
                              </button>
                              <button className="icon-btn" style={{ width: 32, height: 32, color: "#16a34a" }} title="Thêm nội dung"
                                onClick={() => setAddResourceState({ lessonId: lesson.lessonId, lessonTitle: lesson.title })}>
                                <Ic n="plus" size={15} />
                              </button>
                              <button className="icon-btn" style={{ width: 32, height: 32, color: "var(--error)" }} title="Xóa bài giảng"
                                onClick={() => handleDeleteLesson(ch.id, lesson.lessonId)}>
                                <Ic n="x" size={15} />
                              </button>
                            </>}
                          </div>
                          {lesson.resources?.length > 0 && (
                            <div style={{ paddingLeft: 64, paddingBottom: 8 }}>
                              {lesson.resources.map(r => {
                                const rm = RES_TYPE[r.resourceType] || RES_TYPE.OTHER;
                                return (
                                  <div key={r.resourceId} className="row gap-12" style={{ padding: "8px 12px", borderRadius: 9, border: "1px solid var(--border)", marginBottom: 4, background: "var(--surface-2)" }}>
                                    <div className="stat-ic" style={{ width: 30, height: 30, borderRadius: 7, background: rm.bg, color: rm.fg, flex: "none" }}>
                                      <Ic n={rm.ic} size={14} />
                                    </div>
                                    <div className="grow" style={{ minWidth: 0 }}>
                                      <div style={{ fontSize: 13, fontWeight: 500 }} className="truncate">{r.title}</div>
                                      {r.kind && <span className="chip t-xs" style={{ padding: "1px 7px", fontSize: 10.5, background: rm.bg, color: rm.fg }}>{r.kind.toUpperCase()}</span>}
                                    </div>
                                    {canEdit && <>
                                      <button className="icon-btn" style={{ width: 28, height: 28 }} title="Đổi tên"
                                        onClick={() => setEditResourceState({ lessonId: lesson.lessonId, resourceId: r.resourceId, title: r.title, resourceType: r.resourceType, externalUrl: r.externalUrl, mimeType: r.mimeType })}>
                                        <Ic n="edit" size={13} />
                                      </button>
                                      <button className="icon-btn" style={{ width: 28, height: 28, color: "var(--error)" }} title="Xóa tài liệu"
                                        onClick={() => handleDeleteResource(lesson.lessonId, r.resourceId)}>
                                        <Ic n="x" size={13} />
                                      </button>
                                    </>}
                                  </div>
                                );
                              })}
                            </div>
                          )}
                        </div>
                      );
                    })}
                    {canEdit && (
                      <div style={{ padding: "10px 24px" }}>
                        <button className="btn btn-ghost btn-sm" onClick={() => setAddLessonState({ chapterId: ch.id })}>
                          <Ic n="plus" size={14} />Thêm bài giảng
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))}
          </Section>
        )}

        {/* ── Info tab ── */}
        {tab === "info" && (
          <Section>
            <div className="between" style={{ marginBottom: 20 }}>
              <h2 className="t-h2">Thông tin khóa học</h2>
              {isLive && <span className="chip" style={{ background: "#e0f2fe", color: "#0284c7", fontSize: 12 }}>Thay đổi sẽ tạo bản draft chờ duyệt</span>}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 18 }}>
              <Field label={isLive && course?.draftTitle ? `Tên khóa học (hiện tại: ${course.title})` : "Tên khóa học"} full>
                <input className="input" value={editTitle} onChange={e => setEditTitle(e.target.value)} disabled={!canEdit} />
              </Field>
              <Field label="Danh mục">
                <Select
                  value={editCat}
                  onChange={setEditCat}
                  options={[{ v: null, label: "— Chưa chọn —" }, ...categories.map(c => ({ v: c.id, label: c.name }))]}
                  disabled={!canEdit}
                />
              </Field>
              <Field label="Cấp độ">
                <Select value={editLevel} onChange={setEditLevel} options={LEVELS.map(l => ({ v: l.value, label: l.label }))} disabled={!canEdit} />
              </Field>
              <Field label="Mô tả" full>
                <textarea className="input" style={{ height: 100, padding: 12, resize: "none" }} value={editDesc}
                  onChange={e => setEditDesc(e.target.value)} disabled={!canEdit}
                  placeholder="Mô tả ngắn gọn về khóa học..." />
              </Field>
            </div>
            {infoMsg && (
              <div style={{ marginTop: 12, padding: "10px 14px", borderRadius: 10, fontSize: 13.5,
                background: infoMsg.startsWith("Đã") ? "#dcfce7" : "#fee2e2",
                color: infoMsg.startsWith("Đã") ? "#16a34a" : "#dc2626" }}>{infoMsg}
              </div>
            )}
            {canEdit && (
              <div className="row gap-10" style={{ marginTop: 20, justifyContent: "flex-end" }}>
                <button className="btn btn-ghost" onClick={() => {
                  setEditTitle(course?.title || "");
                  setEditDesc(course?.description || "");
                  setEditLevel(course?.level || LEVELS[1].value);
                  setEditCat(course?.category?.id || null);
                  setInfoMsg(null);
                }}>Hủy</button>
                <button className="btn btn-primary" disabled={infoSaving} onClick={handleSaveInfo}>{infoSaving ? "Đang lưu..." : "Lưu thay đổi"}</button>
              </div>
            )}
          </Section>
        )}

        {/* ── History tab ── */}
        {tab === "history" && (
          <Section>
            <h2 className="t-h2" style={{ marginBottom: 20 }}>Lịch sử duyệt</h2>
            {historyLoading && <div className="muted" style={{ fontSize: 13.5 }}>Đang tải...</div>}
            {!historyLoading && history.length === 0 && (
              <Empty icon="clock" title="Chưa có lịch sử" sub="Lịch sử duyệt sẽ xuất hiện sau khi bạn nộp khóa học." />
            )}
            {!historyLoading && history.length > 0 && (
              <div style={{ position: "relative", paddingLeft: 28 }}>
                {/* Vertical line */}
                <div style={{ position: "absolute", left: 9, top: 6, bottom: 6, width: 2, background: "var(--border)" }} />
                {history.map((entry, i) => {
                  const isInstructor = entry.actorType === "INSTRUCTOR";
                  const color = entry.action.startsWith("APPROVED") ? "#16a34a"
                    : entry.action.startsWith("REJECTED") ? "#dc2626"
                    : "#0284c7";
                  const bg = entry.action.startsWith("APPROVED") ? "#dcfce7"
                    : entry.action.startsWith("REJECTED") ? "#fee2e2"
                    : "#e0f2fe";
                  const label = {
                    SUBMITTED_FIRST:  "Nộp lần đầu",
                    SUBMITTED_UPDATE: "Nộp cập nhật",
                    APPROVED_FIRST:   "Duyệt xuất bản",
                    APPROVED_UPDATE:  "Duyệt cập nhật",
                    REJECTED:         "Từ chối xuất bản",
                    REJECTED_UPDATE:  "Từ chối cập nhật",
                    WITHDRAWN:        "Rút khỏi hàng chờ",
                    DISCARDED:        "Hủy thay đổi",
                  }[entry.action] || entry.action;
                  return (
                    <div key={entry.id || i} style={{ position: "relative", marginBottom: 20 }}>
                      {/* Dot */}
                      <div style={{ position: "absolute", left: -23, top: 4, width: 10, height: 10, borderRadius: "50%", background: color, border: "2px solid var(--surface)" }} />
                      <div style={{ padding: "10px 14px", borderRadius: 10, background: "var(--surface-2)", border: "1px solid var(--border)" }}>
                        <div className="row gap-10" style={{ marginBottom: entry.reason ? 6 : 0, flexWrap: "wrap" }}>
                          <span style={{ fontWeight: 600, fontSize: 13.5, color }}>{label}</span>
                          <span className="chip" style={{ background: bg, color, fontSize: 11.5 }}>
                            {isInstructor ? "Giảng viên" : "Quản trị viên"}
                          </span>
                          <span className="muted" style={{ fontSize: 12, marginLeft: "auto" }}>
                            {new Date(entry.createdAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })}
                          </span>
                        </div>
                        {entry.reason && (
                          <div style={{ fontSize: 13, color: "var(--text-2)", borderTop: "1px solid var(--border)", marginTop: 6, paddingTop: 6 }}>
                            <strong>Lý do:</strong> {entry.reason}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </Section>
        )}

        {/* Modals */}
        <AddChapterModal   open={addChapterOpen}     onClose={() => setAddChapterOpen(false)}   courseId={courseId} onAdded={() => loadCourse(true)} />
        <AddLessonModal    open={!!addLessonState}   onClose={() => setAddLessonState(null)}    courseId={courseId} chapterId={addLessonState?.chapterId} onAdded={() => loadCourse(true)} />
        <AddResourceModal  open={!!addResourceState} onClose={() => setAddResourceState(null)}  courseId={courseId} lessonId={addResourceState?.lessonId} lessonTitle={addResourceState?.lessonTitle} onAdded={() => loadCourse(true)} />

        {showPreview && React.createElement(window.PreviewPlayer, { onBack: () => setShowPreview(false) })}

        {renameLessonState && (() => {
          const s = renameLessonState;
          let name = s.title;
          return (
            <Modal open onClose={() => setRenameLessonState(null)} max={420}>
              <ModalHead title="Đổi tên bài giảng" icon="edit" iconBg="#f0fdf4" iconColor="#16a34a" onClose={() => setRenameLessonState(null)} />
              <div className="modal-body">
                <label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên bài giảng</label>
                <input className="input" defaultValue={s.title} autoFocus
                  onChange={e => { name = e.target.value; }}
                  onKeyDown={e => { if (e.key === "Enter") { handleRenameLesson(s.chapterId, s.lessonId, name); setRenameLessonState(null); } }} />
              </div>
              <div className="modal-foot">
                <button className="btn btn-ghost" onClick={() => setRenameLessonState(null)}>Hủy</button>
                <button className="btn btn-primary" onClick={() => { handleRenameLesson(s.chapterId, s.lessonId, name); setRenameLessonState(null); }}>
                  <Ic n="check" size={15} />Lưu
                </button>
              </div>
            </Modal>
          );
        })()}

        {editResourceState && (() => {
          const s   = editResourceState;
          const rm  = RES_TYPE[s.resourceType] || RES_TYPE.OTHER;
          const isVideo = s.resourceType === "VIDEO";
          const isImage = s.resourceType === "IMAGE";
          const isYT = !!(s.externalUrl && /youtube\.com|youtu\.be/.test(s.externalUrl));
          const ytId = isYT ? (s.externalUrl.match(/(?:v=|youtu\.be\/)([^&?/]+)/)?.[1] || "") : null;
          return (
            <EditResourceInner
              s={s} rm={rm} isVideo={isVideo} isImage={isImage} isYT={isYT} ytId={ytId}
              onClose={() => setEditResourceState(null)}
              onSave={newName => { handleRenameResource(s.lessonId, s.resourceId, newName); setEditResourceState(null); }}
              courseId={courseId}
              onReplaced={() => { loadCourse(true); setEditResourceState(null); }}
            />
          );
        })()}
      </div>
    );
  }

  Object.assign(window, { InsCourseDetail });
})();
