// @ts-nocheck
import { createPortal } from 'react-dom';
(function () {
  const { useState, useEffect, useRef, useCallback } = React;
  const Ic = window.Icon;
  const api = window.httpClient;
  const mammoth = window.mammoth;

  const ALLOWED_MIME_TYPES = [
    { key: "image/jpeg", label: "JPEG" },
    { key: "image/png", label: "PNG" },
    { key: "application/pdf", label: "PDF" },
    { key: "application/zip", label: "ZIP" },
    { key: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", label: "DOCX" },
    { key: "application/vnd.openxmlformats-officedocument.presentationml.presentation", label: "PPTX" },
  ];

  function CreateAssignmentModal({ courseId, role, onClose }) {
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [maxScore, setMaxScore] = useState(100);
    const [hasStartDate, setHasStartDate] = useState(false);
    const [startDateDate, setStartDateDate] = useState("");
    const [startDateTime, setStartDateTime] = useState("00:00");
    const [hasDeadline, setHasDeadline] = useState(false);
    const [deadlineDate, setDeadlineDate] = useState("");
    const [deadlineTime, setDeadlineTime] = useState("23:59");
    const [allowLate, setAllowLate] = useState(false);
    const [latePenalty, setLatePenalty] = useState(10);
    const [scope, setScope] = useState("ALL_GROUPS");
    const [maxFileSize, setMaxFileSize] = useState(50);
    const [allowedTypes, setAllowedTypes] = useState([]);
    const [maxSubmissions, setMaxSubmissions] = useState(3);
    const [files, setFiles] = useState([]);
    const [selectedCourseId, setSelectedCourseId] = useState(courseId || "");
    const [courses, setCourses] = useState([]);
    const [coursesLoading, setCoursesLoading] = useState(!courseId);

    useEffect(() => {
      if (courseId) return;
      api.get("/instructor/courses?size=100").then(res => {
        const data = res.data || res;
        const list = data.content || data;
        setCourses(list);
        setCoursesLoading(false);
      }).catch(() => setCoursesLoading(false));
    }, []);

    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    const [previewIdx, setPreviewIdx] = useState(null);
    const [docxHtml, setDocxHtml] = useState({});
    const [previewLoading, setPreviewLoading] = useState(false);
    const [previewError, setPreviewError] = useState("");

    const fileRef = useRef(null);
    const fileUrls = useRef(new Map());
    const docxCache = useRef({});

    function toggleType(mime) {
      setAllowedTypes(prev =>
        prev.includes(mime) ? prev.filter(t => t !== mime) : [...prev, mime]
      );
    }

    function getFileUrl(file) {
      if (!fileUrls.current.has(file)) {
        fileUrls.current.set(file, URL.createObjectURL(file));
      }
      return fileUrls.current.get(file);
    }

    useEffect(() => {
      const currentFiles = new Set(files);
      for (const [file, url] of fileUrls.current) {
        if (!currentFiles.has(file)) {
          URL.revokeObjectURL(url);
          fileUrls.current.delete(file);
        }
      }
    }, [files]);

    useEffect(() => {
      return () => {
        for (const url of fileUrls.current.values()) {
          URL.revokeObjectURL(url);
        }
        fileUrls.current.clear();
      };
    }, []);

    function isDocx(file) {
      return file.name.toLowerCase().endsWith(".docx")
        || file.type === "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    function isImage(file) { return file.type.startsWith("image/"); }
    function isPdf(file) { return file.type === "application/pdf"; }

    useEffect(() => {
      if (previewIdx === null || !files[previewIdx]) {
        setPreviewLoading(false);
        setPreviewError("");
        return;
      }
      const file = files[previewIdx];
      if (!isDocx(file)) {
        setPreviewLoading(false);
        setPreviewError("");
        return;
      }
      if (docxCache.current[file.name]) {
        setPreviewLoading(false);
        setPreviewError("");
        return;
      }
      setPreviewLoading(true);
      setPreviewError("");
      file.arrayBuffer().then(buf => {
        mammoth.convertToHtml({ arrayBuffer: buf }).then(result => {
          docxCache.current[file.name] = result.value;
          setDocxHtml(prev => ({ ...prev, [file.name]: result.value }));
          setPreviewLoading(false);
        }).catch(() => {
          setPreviewError("Không thể đọc file DOCX");
          setPreviewLoading(false);
        });
      }).catch(() => {
        setPreviewError("Không thể đọc file");
        setPreviewLoading(false);
      });
    }, [previewIdx]);

    useEffect(() => {
      if (previewIdx === null) return;
      function handleKey(e) {
        if (e.key === "Escape") { setPreviewIdx(null); return; }
        if (e.key === "ArrowLeft" && previewIdx > 0) { e.preventDefault(); setPreviewIdx(previewIdx - 1); }
        if (e.key === "ArrowRight" && previewIdx < files.length - 1) { e.preventDefault(); setPreviewIdx(previewIdx + 1); }
      }
      window.addEventListener("keydown", handleKey);
      return () => window.removeEventListener("keydown", handleKey);
    }, [previewIdx, files.length]);

    function handleFileDrop(e) {
      e.preventDefault();
      const dropped = Array.from(e.dataTransfer.files);
      setFiles(prev => [...prev, ...dropped]);
    }

    function handleFileSelect(e) {
      const selected = Array.from(e.target.files);
      setFiles(prev => [...prev, ...selected]);
      e.target.value = "";
    }

    function removeFile(i) {
      const removed = files[i];
      setFiles(prev => prev.filter((_, idx) => idx !== i));
      if (removed && fileUrls.current.has(removed)) {
        URL.revokeObjectURL(fileUrls.current.get(removed));
        fileUrls.current.delete(removed);
      }
      if (previewIdx !== null) {
        if (previewIdx === i) {
          setPreviewIdx(null);
        } else if (previewIdx > i) {
          setPreviewIdx(previewIdx - 1);
        }
      }
    }

    async function handleSubmit(publishAfter) {
      setError("");
      if (!title.trim()) { setError("Vui lòng nhập tiêu đề"); return; }
      if (publishAfter && title.trim().length < 5) { setError("Tiêu đề phải có ít nhất 5 ký tự"); return; }

      let startDate = null;
      if (hasStartDate && startDateDate) {
        startDate = new Date(`${startDateDate}T${startDateTime || "00:00"}:00+07:00`).toISOString();
      }
      let deadline = null;
      if (hasDeadline && deadlineDate) {
        deadline = new Date(`${deadlineDate}T${deadlineTime || "23:59"}:00+07:00`).toISOString();
      }

      const payload = {
        title: title.trim(),
        description: description.trim() || null,
        maxScore: maxScore != null ? Number(maxScore) : null,
        startDate,
        deadline,
        allowLateSubmission: hasDeadline ? allowLate : null,
        latePenaltyPercent: allowLate ? Number(latePenalty) : null,
        scope,
        maxFileSizeMb: maxFileSize != null ? Number(maxFileSize) : null,
        allowedFileTypes: allowedTypes.length > 0 ? allowedTypes : null,
        maxSubmissions: maxSubmissions != null ? Number(maxSubmissions) : null,
      };

      const effectiveCourseId = courseId || selectedCourseId;
      if (!effectiveCourseId) { setError("Vui lòng chọn khóa học"); setSubmitting(false); return; }

      setSubmitting(true);
      try {
        const ep = role === "admin"
          ? `/admin/courses/${effectiveCourseId}/assignments`
          : `/instructor/courses/${effectiveCourseId}/assignments`;
        const res = await api.post(ep, payload);
        const assignment = res.data || res;

        // Upload files if any
        for (const file of files) {
          const formData = new FormData();
          formData.append("file", file);
          await api.post(`${ep}/${assignment.id}/attachments`, formData, {
            headers: { "Content-Type": "multipart/form-data" },
          });
        }

        // Publish if requested
        if (publishAfter) {
          await api.put(`${ep}/${assignment.id}/publish`);
        }

        onClose(true);
      } catch (err) {
        const msg = err?.response?.data?.message || err?.message || "Có lỗi xảy ra";
        setError(msg);
      } finally {
        setSubmitting(false);
      }
    }

    return createPortal(
      <>
      <div style={{ position: "fixed", top: 0, left: 0, right: 0, bottom: 0, zIndex: 300,
        background: "rgba(0,0,0,.35)", display: "flex", alignItems: "center",
        justifyContent: "center", overflowY: "auto", padding: 20 }}
        onClick={e => { if (e.target === e.currentTarget) onClose(false); }}>
        <div style={{ width: 720, maxWidth: "100%", margin: "auto", background: "#fff",
          borderRadius: 14, maxHeight: "90vh", overflowY: "auto",
          boxShadow: "0 16px 48px rgba(0,0,0,.15)" }}>

          {/* Header */}
          <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "12px 18px",
            borderBottom: "1px solid #e2e8f0", position: "sticky", top: 0, background: "#fff",
            zIndex: 1 }}>
            <div style={{ width: 28, height: 28, borderRadius: 7,
              background: "linear-gradient(135deg,#1e40af,#3b82f6)",
              display: "grid", placeItems: "center" }}>
              <Ic n="clipboard" size={14} style={{ color: "#fff" }} />
            </div>
            <span style={{ fontSize: 15, fontWeight: 700, color: "#0f172a", flexShrink: 0 }}>
              Tạo bài tập
            </span>
            {!courseId && (
              <div style={{ flex: 1, maxWidth: 280 }}>
                <select value={selectedCourseId}
                  onChange={e => setSelectedCourseId(e.target.value)}
                  disabled={coursesLoading}
                  style={{ width: "100%", height: 32, borderRadius: 7, border: "1px solid #e2e8f0",
                    padding: "0 8px", fontSize: 12.5, outline: "none", background: "#f8fafc",
                    color: selectedCourseId ? "#0f172a" : "#94a3b8", cursor: "pointer" }}>
                  <option value="">{coursesLoading ? "Đang tải..." : "Chọn khóa học *"}</option>
                  {courses.map(c => (
                    <option key={c.id} value={c.id}>{c.title}</option>
                  ))}
                </select>
              </div>
            )}
          </div>

          {/* Error */}
          {error && (
            <div style={{ margin: "10px 18px 0", padding: "8px 12px", borderRadius: 8,
              background: "#fef2f2", color: "#dc2626", fontSize: 12.5,
              display: "flex", alignItems: "center", gap: 6 }}>
              <Ic n="alert_circle" size={14} />
              {error}
            </div>
          )}

          {/* Body: 2-column */}
          <div style={{ padding: "16px 18px 8px",
            display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 20px" }}>

            {/* ── LEFT COLUMN ── */}
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>

              {/* Title */}
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                  display: "block", marginBottom: 4 }}>
                  Tiêu đề <span style={{ color: "#dc2626" }}>*</span>
                </label>
                <input value={title} onChange={e => setTitle(e.target.value)}
                  placeholder="Nhập tiêu đề bài tập"
                  style={{ width: "100%", height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                    padding: "0 10px", fontSize: 13, outline: "none", boxSizing: "border-box" }} />
              </div>

              {/* Description */}
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                  display: "block", marginBottom: 4 }}>Mô tả</label>
                <textarea value={description} onChange={e => setDescription(e.target.value)}
                  placeholder="Nhập mô tả bài tập (không bắt buộc)"
                  rows={5}
                  style={{ width: "100%", borderRadius: 8, border: "1px solid #e2e8f0",
                    padding: "8px 10px", fontSize: 13, outline: "none", resize: "vertical",
                    boxSizing: "border-box", fontFamily: "inherit", lineHeight: 1.5 }} />
              </div>

              {/* Attachments */}
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                  display: "block", marginBottom: 4 }}>File đính kèm</label>
                <div onDragOver={e => e.preventDefault()} onDrop={handleFileDrop}
                  style={{ border: "1.5px dashed #cbd5e1", borderRadius: 8,
                    padding: "14px 12px", textAlign: "center", cursor: "pointer",
                    background: "#f8fafc", transition: ".12s" }}
                  onClick={() => fileRef.current?.click()}
                  onMouseEnter={e => e.currentTarget.style.background = "#f1f5f9"}
                  onMouseLeave={e => e.currentTarget.style.background = "#f8fafc"}>
                  <Ic n="upload" size={16} style={{ color: "#94a3b8", marginBottom: 4 }} />
                  <div style={{ fontSize: 12, color: "#94a3b8" }}>
                    Kéo thả file hoặc <span style={{ color: "#2563eb", fontWeight: 600 }}>chọn file</span>
                  </div>
                  <input ref={fileRef} type="file" multiple onChange={handleFileSelect}
                    style={{ display: "none" }} />
                </div>
                {files.length > 0 && (
                  <div style={{ marginTop: 6, display: "flex", flexDirection: "column", gap: 4 }}>
                    {files.map((f, i) => (
                      <div key={i}
                        style={{ display: "flex", alignItems: "center", gap: 6,
                          padding: "4px 8px", borderRadius: 6, background: "#f8fafc" }}>
                        <Ic n="file" size={11} style={{ color: "#94a3b8", flexShrink: 0 }} />
                        <span onClick={() => setPreviewIdx(i)}
                          style={{ flex: 1, fontSize: 11.5, color: "#2563eb", cursor: "pointer",
                            textDecoration: "underline", textDecorationColor: "#bfdbfe",
                            textUnderlineOffset: 2 }}
                          className="truncate">{f.name}</span>
                        <span style={{ fontSize: 10, color: "#94a3b8", flexShrink: 0 }}>
                          {(f.size / 1024).toFixed(0)} KB
                        </span>
                        <button onClick={() => removeFile(i)}
                          style={{ border: "none", background: "transparent", color: "#94a3b8",
                            cursor: "pointer", padding: 0, display: "grid", placeItems: "center",
                            flexShrink: 0 }}>
                          <Ic n="x" size={11} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* ── RIGHT COLUMN ── */}
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>

              {/* Max score */}
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                  display: "block", marginBottom: 4 }}>Điểm tối đa</label>
                <input type="number" min={0} value={maxScore}
                  onChange={e => setMaxScore(e.target.value)}
                  style={{ width: "100%", height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                    padding: "0 10px", fontSize: 13, outline: "none", boxSizing: "border-box" }} />
              </div>

              {/* Start date toggle */}
              <div>
                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                  <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                    cursor: "pointer", display: "flex", alignItems: "center", gap: 6 }}>
                    <input type="checkbox" checked={hasStartDate}
                      onChange={e => setHasStartDate(e.target.checked)}
                      style={{ accentColor: "#2563eb" }} />
                    Ngày bắt đầu
                  </label>
                </div>
                {hasStartDate && (
                  <div style={{ display: "flex", gap: 6 }}>
                    <input type="date" value={startDateDate}
                      onChange={e => setStartDateDate(e.target.value)}
                      style={{ flex: 1, height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                        padding: "0 10px", fontSize: 13, outline: "none", boxSizing: "border-box" }} />
                    <input type="time" value={startDateTime}
                      onChange={e => setStartDateTime(e.target.value)}
                      style={{ width: 90, height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                        padding: "0 8px", fontSize: 13, outline: "none", boxSizing: "border-box" }} />
                  </div>
                )}
              </div>

              {/* Deadline toggle */}
              <div>
                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                  <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                    cursor: "pointer", display: "flex", alignItems: "center", gap: 6 }}>
                    <input type="checkbox" checked={hasDeadline}
                      onChange={e => setHasDeadline(e.target.checked)}
                      style={{ accentColor: "#2563eb" }} />
                    Có hạn nộp
                  </label>
                </div>
                {hasDeadline && (
                  <div style={{ display: "flex", gap: 6 }}>
                    <input type="date" value={deadlineDate}
                      onChange={e => setDeadlineDate(e.target.value)}
                      style={{ flex: 1, height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                        padding: "0 10px", fontSize: 13, outline: "none", boxSizing: "border-box" }} />
                    <input type="time" value={deadlineTime}
                      onChange={e => setDeadlineTime(e.target.value)}
                      style={{ width: 90, height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                        padding: "0 8px", fontSize: 13, outline: "none", boxSizing: "border-box" }} />
                  </div>
                )}
              </div>

              {/* Late submission toggle */}
              {hasDeadline && (
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                      cursor: "pointer", display: "flex", alignItems: "center", gap: 6 }}>
                      <input type="checkbox" checked={allowLate}
                        onChange={e => setAllowLate(e.target.checked)}
                        style={{ accentColor: "#2563eb" }} />
                      Cho phép nộp muộn
                    </label>
                  </div>
                  {allowLate && (
                    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                      <input type="number" min={0} max={100} value={latePenalty}
                        onChange={e => setLatePenalty(e.target.value)}
                        style={{ width: 80, height: 36, borderRadius: 8,
                          border: "1px solid #e2e8f0", padding: "0 8px",
                          fontSize: 13, outline: "none", boxSizing: "border-box" }} />
                      <span style={{ fontSize: 12, color: "#64748b" }}>% trừ điểm</span>
                    </div>
                  )}
                </div>
              )}

              {/* Scope */}
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                  display: "block", marginBottom: 4 }}>Phạm vi</label>
                <select value={scope} onChange={e => setScope(e.target.value)}
                  style={{ width: "100%", height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                    padding: "0 10px", fontSize: 13, outline: "none", background: "#fff",
                    boxSizing: "border-box" }}>
                  <option value="ALL_GROUPS">Tất cả nhóm</option>
                  <option value="SPECIFIC_GROUPS" disabled>Nhóm cụ thể (sắp ra mắt)</option>
                </select>
              </div>

              {/* Max file size */}
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                  display: "block", marginBottom: 4 }}>Kích thước file tối đa (MB)</label>
                <input type="number" min={1} value={maxFileSize}
                  onChange={e => setMaxFileSize(e.target.value)}
                  style={{ width: "100%", height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                    padding: "0 10px", fontSize: 13, outline: "none", boxSizing: "border-box" }} />
              </div>

              {/* Allowed file types */}
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                  display: "block", marginBottom: 4 }}>Loại file được phép</label>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                  {ALLOWED_MIME_TYPES.map(m => {
                    const sel = allowedTypes.includes(m.key);
                    return (
                      <button key={m.key} onClick={() => toggleType(m.key)}
                        style={{ fontSize: 11, fontWeight: 600, padding: "4px 9px",
                          borderRadius: 6, border: `1.5px solid ${sel ? "#2563eb" : "#e2e8f0"}`,
                          background: sel ? "#eff6ff" : "#f8fafc",
                          color: sel ? "#2563eb" : "#64748b",
                          cursor: "pointer", transition: ".1s" }}>
                        {m.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Max submissions */}
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
                  display: "block", marginBottom: 4 }}>Số lần nộp tối đa</label>
                <input type="number" min={1} value={maxSubmissions}
                  onChange={e => setMaxSubmissions(e.target.value)}
                  style={{ width: "100%", height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                    padding: "0 10px", fontSize: 13, outline: "none", boxSizing: "border-box" }} />
              </div>
            </div>
          </div>

          {/* Footer */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "flex-end",
            gap: 8, padding: "12px 18px", borderTop: "1px solid #e2e8f0",
            position: "sticky", bottom: 0, background: "#fff", zIndex: 1 }}>
            <button onClick={() => onClose(false)} disabled={submitting}
              style={{ height: 36, padding: "0 14px", borderRadius: 8, fontSize: 13,
                border: "1px solid #e2e8f0", background: "#fff", color: "#475569",
                cursor: "pointer", fontWeight: 600 }}>
              Huỷ
            </button>
            <button onClick={() => handleSubmit(false)} disabled={submitting}
              style={{ height: 36, padding: "0 14px", borderRadius: 8, fontSize: 13,
                border: "1.5px solid #e2e8f0", background: "#f8fafc", color: "#475569",
                cursor: "pointer", fontWeight: 600, display: "flex", alignItems: "center", gap: 5 }}>
              <Ic n="save" size={12} />
              {submitting ? "Đang lưu..." : "Lưu nháp"}
            </button>
            <button onClick={() => handleSubmit(true)} disabled={submitting}
              style={{ height: 36, padding: "0 14px", borderRadius: 8, fontSize: 13,
                border: "none", background: "#2563eb", color: "#fff",
                cursor: "pointer", fontWeight: 600, display: "flex", alignItems: "center", gap: 5 }}>
              <Ic n="send" size={12} />
              {submitting ? "Đang đăng..." : "Đăng"}
            </button>
          </div>
        </div>
      </div>

      {/* File preview overlay */}
      {previewIdx !== null && files[previewIdx] && (() => {
        const file = files[previewIdx];
        const hasPrev = previewIdx > 0;
        const hasNext = previewIdx < files.length - 1;
        const fileSize = (file.size / 1024).toFixed(0);

        let content;
        if (isImage(file)) {
          content = <img src={getFileUrl(file)}
            style={{ maxWidth: "100%", maxHeight: "100%", minWidth: 400, minHeight: 300,
              objectFit: "contain", borderRadius: 4 }} />;
        } else if (isPdf(file)) {
          content = <iframe src={getFileUrl(file)}
            style={{ width: "100%", height: "100%", border: "none", borderRadius: 4,
              minHeight: "60vh" }} />;
        } else if (isDocx(file)) {
          if (previewLoading) {
            content = <div style={{ color: "#94a3b8", fontSize: 13, textAlign: "center" }}>
              <div style={{ fontSize: 11, marginBottom: 8 }}>⏳</div>
              Đang tải nội dung...
            </div>;
          } else if (previewError) {
            content = <div style={{ color: "#f87171", fontSize: 13, textAlign: "center" }}>
              <Ic n="alert_circle" size={20} style={{ marginBottom: 6 }} /><br />
              {previewError}
            </div>;
          } else if (docxHtml[file.name]) {
            content = <div dangerouslySetInnerHTML={{ __html: docxHtml[file.name] }}
              style={{ width: "100%", height: "100%", overflow: "auto", background: "#fff",
                padding: 32, borderRadius: 6, color: "#0f172a", fontSize: 14,
                lineHeight: 1.7, boxSizing: "border-box" }} />;
          } else {
            content = <div style={{ color: "#94a3b8", fontSize: 13, textAlign: "center" }}>
              Không thể xem trước
            </div>;
          }
        } else {
          content = <div style={{ color: "#94a3b8", fontSize: 13, textAlign: "center" }}>
            <Ic n="file" size={28} style={{ marginBottom: 6 }} /><br />
            Không thể xem trước file này
          </div>;
        }

        return (
          <div style={{ position: "fixed", top: 0, left: 0, right: 0, bottom: 0, zIndex: 400, background: "rgba(0,0,0,.88)",
            display: "flex", flexDirection: "column" }}>
            {/* Top bar */}
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between",
              padding: "10px 16px", flexShrink: 0 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <button onClick={() => setPreviewIdx(null)}
                  style={{ border: "none", background: "rgba(255,255,255,.1)", color: "#fff",
                    width: 32, height: 32, borderRadius: 8, cursor: "pointer", fontSize: 14,
                    display: "grid", placeItems: "center" }}>
                  ✕
                </button>
                <Ic n="file" size={13} style={{ color: "#94a3b8" }} />
                <span style={{ color: "#f1f5f9", fontSize: 13, fontWeight: 500 }}>{file.name}</span>
                <span style={{ color: "#64748b", fontSize: 11 }}>({fileSize} KB)</span>
              </div>
              <span style={{ color: "#94a3b8", fontSize: 12 }}>
                {previewIdx + 1} / {files.length}
              </span>
            </div>

            {/* Content area */}
            <div style={{ flex: 1, position: "relative", overflow: "hidden" }}>
              {hasPrev && (
                <button onClick={() => setPreviewIdx(previewIdx - 1)}
                  style={{ position: "absolute", left: 16, top: "50%", translate: "0 -50%",
                    zIndex: 10, border: "none", background: "rgba(255,255,255,.1)",
                    color: "#fff", width: 44, height: 44, borderRadius: "50%",
                    cursor: "pointer", fontSize: 20, display: "grid", placeItems: "center" }}>
                  ‹
                </button>
              )}
              <div style={{ width: "100%", height: "100%", display: "flex",
                alignItems: "center", justifyContent: "center", padding: "16px 64px",
                boxSizing: "border-box" }}>
                {content}
              </div>
              {hasNext && (
                <button onClick={() => setPreviewIdx(previewIdx + 1)}
                  style={{ position: "absolute", right: 16, top: "50%", translate: "0 -50%",
                    zIndex: 10, border: "none", background: "rgba(255,255,255,.1)",
                    color: "#fff", width: 44, height: 44, borderRadius: "50%",
                    cursor: "pointer", fontSize: 20, display: "grid", placeItems: "center" }}>
                  ›
                </button>
              )}
            </div>
          </div>
        );
      })()}
      </>,
  document.body
    );
  }

  Object.assign(window, { CreateAssignmentModal });
})();
