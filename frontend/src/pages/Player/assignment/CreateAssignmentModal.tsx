// @ts-nocheck
import { createPortal } from 'react-dom';
(function () {
  const { useState, useEffect, useRef } = React;
  const Ic = window.Icon;
  const api = window.httpClient;

  const ALLOWED_MIME_TYPES = [
    { key: "image/jpeg", label: "JPEG" },
    { key: "image/png", label: "PNG" },
    { key: "application/pdf", label: "PDF" },
    { key: "application/zip", label: "ZIP" },
    { key: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", label: "DOCX" },
    { key: "application/vnd.openxmlformats-officedocument.presentationml.presentation", label: "PPTX" },
  ];

  function CreateAssignmentModal({ courseId, role, onClose, assignment }) {
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
    const [coursesLoading, setCoursesLoading] = useState(role === 'instructor' || !courseId);
    const [selectedGroupIds, setSelectedGroupIds] = useState([]);
    const [groups, setGroups] = useState([]);
    const [groupsLoading, setGroupsLoading] = useState(false);
    const isEdit = !!assignment;
    const [existingAttachments, setExistingAttachments] = useState(assignment?.attachments || []);
    const deletedAttachmentIds = useRef([]);

    useEffect(() => {
      if (role !== 'instructor' && courseId) return;
      api.get("/instructor/courses?size=100").then(res => {
        const data = res.data || res;
        const list = data.content || data;
        setCourses(list);
        setCoursesLoading(false);
      }).catch(() => setCoursesLoading(false));
    }, []);

    const effectiveCourseId = selectedCourseId || courseId;

    useEffect(() => {
      if (!effectiveCourseId || scope !== "SPECIFIC_GROUPS") {
        setGroups([]);
        return;
      }
      setGroupsLoading(true);
      api.get("/instructor/groups", { params: { courseId: effectiveCourseId, size: 100 } }).then(res => {
        const data = res.data || res;
        setGroups(data.content || data);
        setGroupsLoading(false);
      }).catch(err => {
        console.error("Lỗi tải nhóm:", err?.response?.status, err?.response?.data || err);
        setGroupsLoading(false);
      });
    }, [effectiveCourseId, scope, assignment]);

    function toggleGroup(id) {
      setSelectedGroupIds(prev =>
        prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
      );
    }

    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    const [previewItemIdx, setPreviewItemIdx] = useState(null);
    const [previewItems, setPreviewItems] = useState([]);

    const fileRef = useRef(null);
    const fileUrls = useRef(new Map());

    function padNum(n) { return String(n).padStart(2, '0'); }
    function toLocalDateStr(d) {
      return `${d.getFullYear()}-${padNum(d.getMonth()+1)}-${padNum(d.getDate())}`;
    }
    function toLocalTimeStr(d) {
      return `${padNum(d.getHours())}:${padNum(d.getMinutes())}`;
    }
    function toLocalISOStr(dateStr, timeStr, fallback) {
      if (!dateStr) return null;
      const [y, m, d] = dateStr.split("-").map(Number);
      const [hh, mi] = (timeStr || fallback).split(":").map(Number);
      return new Date(y, m - 1, d, hh, mi).toISOString();
    }

    useEffect(() => {
      if (!assignment) return;
      setTitle(assignment.title || "");
      setDescription(assignment.description || "");
      setMaxScore(assignment.maxScore ?? 100);
      setScope(assignment.scope || "ALL_GROUPS");
      setMaxFileSize(assignment.maxFileSizeMb ?? 50);
      setAllowedTypes(assignment.allowedFileTypes || []);
      setMaxSubmissions(assignment.maxSubmissions ?? 3);
      setSelectedGroupIds(assignment.groupIds || []);
      setAllowLate(assignment.allowLateSubmission || false);
      setLatePenalty(assignment.latePenaltyPercent ?? 10);
      setExistingAttachments(assignment.attachments || []);
      deletedAttachmentIds.current = [];

      if (assignment.startDate) {
        setHasStartDate(true);
        const d = new Date(assignment.startDate);
        setStartDateDate(toLocalDateStr(d));
        setStartDateTime(toLocalTimeStr(d));
      }
      if (assignment.deadline) {
        setHasDeadline(true);
        const d = new Date(assignment.deadline);
        setDeadlineDate(toLocalDateStr(d));
        setDeadlineTime(toLocalTimeStr(d));
      }
    }, [assignment]);

    function toggleType(mime) {
      setAllowedTypes(prev =>
        prev.includes(mime) ? prev.filter(t => t !== mime) : [...prev, mime]
      );
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

    function buildPreviewItems() {
      const items = [];
      for (const att of existingAttachments) {
        if (att.url) {
          items.push({
            name: att.displayName || att.originalFilename,
            url: att.url,
            size: att.fileSizeBytes,
            mimeType: att.mimeType,
          });
        }
      }
      for (const file of files) {
        items.push({
          name: file.name,
          file: file,
          size: file.size,
          mimeType: file.type,
        });
      }
      return items;
    }

    function openPreviewExisting(att) {
      const items = buildPreviewItems();
      const idx = existingAttachments.findIndex(a => a.id === att.id);
      if (idx >= 0) {
        setPreviewItems(items);
        setPreviewItemIdx(idx);
      }
    }

    function openPreviewNewFile(fileIdx) {
      const items = buildPreviewItems();
      const localStart = existingAttachments.filter(a => a.url).length;
      setPreviewItems(items);
      setPreviewItemIdx(localStart + fileIdx);
    }

    function closePreview() {
      setPreviewItemIdx(null);
      setPreviewItems([]);
    }

    async function removeExistingAttachment(att) {
      setError("");
      try {
        const ep = role === "admin"
          ? `/admin/courses/${effectiveCourseId}/assignments/${assignment.id}/attachments/${att.id}`
          : `/instructor/courses/${effectiveCourseId}/assignments/${assignment.id}/attachments/${att.id}`;
        await api.delete(ep);
        setExistingAttachments(prev => prev.filter(a => a.id !== att.id));
      } catch (err) {
        const msg = err?.response?.data?.message || err?.message || "Xoá file thất bại";
        setError(msg);
      }
    }

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
      if (previewItemIdx !== null) {
        const localStart = existingAttachments.filter(a => a.url).length;
        if (previewItemIdx === localStart + i) {
          setPreviewItemIdx(null);
        }
      }
    }

    async function uploadWithLimit(files, urlPrefix) {
      const total = files.length;
      if (total === 0) return;
      let idx = 0;
      const workers = Array.from({ length: Math.min(3, total) }, async () => {
        while (idx < total) {
          const i = idx++;
          const formData = new FormData();
          formData.append("file", files[i]);
          await api.post(urlPrefix, formData, {
            headers: { "Content-Type": "multipart/form-data" },
          });
        }
      });
      await Promise.all(workers);
    }

    async function handleSubmit(publishAfter) {
      setError("");
      if (!title.trim()) { setError("Vui lòng nhập tiêu đề"); return; }
      if (publishAfter && title.trim().length < 5) { setError("Tiêu đề phải có ít nhất 5 ký tự"); return; }
      if (allowedTypes.length === 0) { setError("Vui lòng chọn ít nhất một loại file được phép"); return; }

      const startDate = hasStartDate ? toLocalISOStr(startDateDate, startDateTime, "00:00") : null;
      const deadline = hasDeadline ? toLocalISOStr(deadlineDate, deadlineTime, "23:59") : null;

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
        groupIds: scope === "SPECIFIC_GROUPS" ? selectedGroupIds : null,
      };

      if (!effectiveCourseId) { setError("Vui lòng chọn khóa học"); setSubmitting(false); return; }

      setSubmitting(true);
      try {
        const baseEp = role === "admin"
          ? `/admin/courses/${effectiveCourseId}/assignments`
          : `/instructor/courses/${effectiveCourseId}/assignments`;

        if (isEdit) {
          const ep = `${baseEp}/${assignment.id}`;
          await api.put(ep, payload);
          await uploadWithLimit(files, `${ep}/attachments`);
          if (publishAfter) {
            await api.put(`${ep}/publish`);
          }
          onClose(true);
        } else {
          const res = await api.post(baseEp, payload);
          const created = res.data || res;

          await uploadWithLimit(files, `${baseEp}/${created.id}/attachments`);

          if (publishAfter) {
            await api.put(`${baseEp}/${created.id}/publish`);
          }

          onClose(true);
        }
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
              {isEdit ? "Sửa bài tập" : "Tạo bài tập"}
            </span>
            {isEdit ? (
              <div style={{ flex: 1, fontSize: 12, color: "#64748b", textAlign: "right" }}>
                <Ic n="book" size={12} style={{ marginRight: 4 }} />
                {assignment.courseTitle || "Đang tải..."}
              </div>
            ) : (role === 'instructor') && (
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
                {isEdit && existingAttachments.length > 0 && (
                  <div style={{ marginBottom: 6, display: "flex", flexDirection: "column", gap: 4 }}>
                    {existingAttachments.map(att => (
                      <div key={att.id}
                        style={{ display: "flex", alignItems: "center", gap: 6,
                          padding: "4px 8px", borderRadius: 6, background: "#eef2ff" }}>
                        <Ic n="file" size={11} style={{ color: "#6366f1", flexShrink: 0 }} />
                        <span onClick={() => openPreviewExisting(att)}
                          style={{ flex: 1, fontSize: 11.5, color: "#2563eb", cursor: "pointer",
                            textDecoration: "underline", textDecorationColor: "#bfdbfe",
                            textUnderlineOffset: 2 }}
                          className="truncate">{att.displayName || att.originalFilename}</span>
                        <span style={{ fontSize: 10, color: "#94a3b8", flexShrink: 0 }}>
                          {att.fileSizeBytes ? (att.fileSizeBytes / 1024).toFixed(0) + " KB" : ""}
                        </span>
                        <button onClick={() => removeExistingAttachment(att)}
                          style={{ border: "none", background: "transparent", color: "#ef4444",
                            cursor: "pointer", padding: 0, display: "grid", placeItems: "center",
                            flexShrink: 0 }}>
                          <Ic n="x" size={11} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
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
                        <span onClick={() => openPreviewNewFile(i)}
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
                <select value={scope} onChange={e => { setScope(e.target.value); setSelectedGroupIds([]); }}
                  style={{ width: "100%", height: 36, borderRadius: 8, border: "1px solid #e2e8f0",
                    padding: "0 10px", fontSize: 13, outline: "none", background: "#fff",
                    boxSizing: "border-box" }}>
                  <option value="ALL_GROUPS">Tất cả nhóm</option>
                  <option value="SPECIFIC_GROUPS">Nhóm cụ thể</option>
                </select>
                {scope === "SPECIFIC_GROUPS" && (
                  <div style={{ marginTop: 8, border: "1px solid #e2e8f0", borderRadius: 8,
                    maxHeight: 160, overflowY: "auto", background: "#f8fafc" }}>
                    {groupsLoading ? (
                      <div style={{ padding: "10px 12px", fontSize: 12, color: "#94a3b8" }}>
                        Đang tải nhóm...
                      </div>
                    ) : groups.length === 0 ? (
                      <div style={{ padding: "10px 12px", fontSize: 12, color: "#94a3b8" }}>
                        Không có nhóm nào
                      </div>
                    ) : (
                      groups.map(g => {
                        const sel = selectedGroupIds.includes(g.id);
                        return (
                          <label key={g.id}
                            style={{ display: "flex", alignItems: "center", gap: 8,
                              padding: "7px 12px", cursor: "pointer", fontSize: 12.5,
                              color: "#0f172a", transition: ".1s",
                              background: sel ? "#eff6ff" : "transparent",
                              borderBottom: "1px solid #f1f5f9" }}
                            onMouseEnter={e => { if (!sel) e.currentTarget.style.background = "#f8fafc"; }}
                            onMouseLeave={e => { if (!sel) e.currentTarget.style.background = "transparent"; }}>
                            <input type="checkbox" checked={sel}
                              onChange={() => toggleGroup(g.id)}
                              style={{ accentColor: "#2563eb" }} />
                            <span style={{ flex: 1 }}>{g.name}</span>
                            <span style={{ fontSize: 11, color: "#94a3b8" }}>
                              {g.memberCount ?? 0} HV
                            </span>
                          </label>
                        );
                      })
                    )}
                    {selectedGroupIds.length > 0 && (
                      <div style={{ padding: "6px 12px", fontSize: 11, color: "#64748b",
                        borderTop: "1px solid #e2e8f0", background: "#fff" }}>
                        Đã chọn {selectedGroupIds.length} nhóm
                      </div>
                    )}
                  </div>
                )}
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
            {isEdit && assignment.status === 'DRAFT' ? (
              <>
                <button onClick={() => handleSubmit(false)} disabled={submitting}
                  style={{ height: 36, padding: "0 14px", borderRadius: 8, fontSize: 13,
                    border: "1.5px solid #e2e8f0", background: "#f8fafc", color: "#475569",
                    cursor: "pointer", fontWeight: 600, display: "flex", alignItems: "center", gap: 5 }}>
                  <Ic n="save" size={12} />
                  {submitting ? "Đang lưu..." : "Lưu thay đổi"}
                </button>
                <button onClick={() => handleSubmit(true)} disabled={submitting}
                  style={{ height: 36, padding: "0 14px", borderRadius: 8, fontSize: 13,
                    border: "none", background: "#2563eb", color: "#fff",
                    cursor: "pointer", fontWeight: 600, display: "flex", alignItems: "center", gap: 5 }}>
                  <Ic n="send" size={12} />
                  {submitting ? "Đang đăng..." : "Đăng"}
                </button>
              </>
            ) : isEdit ? (
              <button onClick={() => handleSubmit(false)} disabled={submitting}
                style={{ height: 36, padding: "0 16px", borderRadius: 8, fontSize: 13,
                  border: "none", background: "#2563eb", color: "#fff",
                  cursor: "pointer", fontWeight: 600, display: "flex", alignItems: "center", gap: 5 }}>
                <Ic n="save" size={12} />
                {submitting ? "Đang lưu..." : "Lưu thay đổi"}
              </button>
            ) : (
              <>
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
              </>
            )}
          </div>
        </div>
      </div>

      {/* File preview overlay */}
      {previewItemIdx !== null && previewItems[previewItemIdx] && (
        React.createElement(window.FilePreview, {
          files: previewItems,
          initialIdx: previewItemIdx,
          onClose: closePreview,
        })
      )}
      </>,
  document.body
    );
  }

  Object.assign(window, { CreateAssignmentModal });
})();
