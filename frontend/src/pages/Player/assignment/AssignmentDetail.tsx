// @ts-nocheck
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const api = window.httpClient;

  const statusColors = {
    DRAFT:     { bg: "#f1f5f9", color: "#64748b", label: "Bản nháp" },
    PUBLISHED: { bg: "#dcfce7", color: "#16a34a", label: "Đã xuất bản" },
    CLOSED:    { bg: "#fef2f2", color: "#dc2626", label: "Đã đóng" },
  };

  function fmtBytes(bytes) {
    if (!bytes) return "";
    if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + " MB";
    return (bytes / 1024).toFixed(0) + " KB";
  }

  function fmtDate(d) {
    if (!d) return null;
    return new Date(d).toLocaleDateString("vi-VN", {
      day: "2-digit", month: "2-digit", year: "numeric",
      hour: "2-digit", minute: "2-digit",
    });
  }

  function getThumbnail(att) {
    const m = (att.mimeType || "").toLowerCase();
    if (m.startsWith("image/")) return { type: "image", url: att.url };
    if (m === "application/pdf") return { type: "icon", label: "PDF", color: "#dc2626", bg: "#fef2f2" };
    if (m.includes("zip") || m.includes("rar") || m.includes("tar") || m.includes("7z") || m.includes("gz"))
      return { type: "icon", label: "ZIP", color: "#d97706", bg: "#fffbeb" };
    if (m.includes("word") || m.includes("doc") || att.displayName?.match(/\.docx?$/i) || att.originalFilename?.match(/\.docx?$/i))
      return { type: "icon", label: "DOC", color: "#2563eb", bg: "#eff6ff" };
    if (m.includes("presentation") || m.includes("ppt") || att.displayName?.match(/\.pptx?$/i) || att.originalFilename?.match(/\.pptx?$/i))
      return { type: "icon", label: "PPT", color: "#7c3aed", bg: "#f5f3ff" };
    return { type: "icon", label: "FILE", color: "#64748b", bg: "#f1f5f9" };
  }

  function getFileExt(name) {
    if (!name) return "FILE";
    const p = name.split(".");
    return p.length > 1 ? p.pop().toUpperCase() : "FILE";
  }

  function AssignmentDetail({ assignmentId, courseId, role, onBack }) {
    const [detail, setDetail] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [previewIdx, setPreviewIdx] = useState(null);
    const [previewFilesList, setPreviewFilesList] = useState([]);

    const [selectedFiles, setSelectedFiles] = useState([]);

    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);
    const [resubmitting, setResubmitting] = useState(false);
    const [keepFileIds, setKeepFileIds] = useState([]);

    const isStudent = role === "student";

    useEffect(() => {
      if (!assignmentId || !courseId) return;
      setLoading(true);
      setError("");
      const ep = isStudent
        ? `/student/courses/${courseId}/assignments/${assignmentId}`
        : `/instructor/courses/${courseId}/assignments/${assignmentId}`;
      api.get(ep)
        .then(r => { setDetail(r.data); setResubmitting(false); })
        .catch(() => setError("Không thể tải thông tin bài tập"))
        .finally(() => setLoading(false));
    }, [assignmentId, courseId, isStudent, refreshKey]);

    function openPreview(list, idx) {
      setPreviewFilesList(list);
      setPreviewIdx(idx);
    }

    function closePreview() {
      setPreviewIdx(null);
      setPreviewFilesList([]);
    }

    function handleFileSelect(e) {
      const newFiles = Array.from(e.target.files || []);
      const allowed = detail?.allowedFileTypes;
      const maxSize = detail?.maxFileSizeMb;
      const errors = [];
      setSelectedFiles(prev => {
        const map = new Map();
        prev.forEach(f => map.set(f.name + f.size + f.lastModified, f));
        newFiles.forEach(f => {
          const key = f.name + f.size + f.lastModified;
          if (map.has(key)) {
            errors.push(`"${f.name}" đã được chọn`);
            return;
          }
          map.set(key, f);
        });
        return Array.from(map.values());
      });
      if (errors.length > 0) {
        setSubmitError(errors.join('. '));
      } else {
        setSubmitError('');
      }
      e.target.value = '';
    }

    function removeFile(i) {
      setSelectedFiles(prev => prev.filter((_, idx) => idx !== i));
    }

    async function handleSubmit() {
      if (selectedFiles.length === 0 && keepFileIds.length === 0) return;
      const allowed = detail?.allowedFileTypes;
      const maxSize = detail?.maxFileSizeMb;
      const errors = [];
      let totalBytes = 0;
      selectedFiles.forEach(f => {
        if (allowed?.length > 0 && f.type && !allowed.some(t => t === f.type)) {
          errors.push(`"${f.name}" không đúng định dạng file`);
        }
        totalBytes += f.size;
      });
      if (maxSize != null) {
        const maxBytes = maxSize * 1024 * 1024;
        if (totalBytes > maxBytes) {
          errors.push(`Tổng kích thước các file vượt quá ${maxSize} MB`);
        }
      }
      if (errors.length > 0) {
        setSubmitError(errors.join('. '));
        return;
      }
      setSubmitting(true);
      setSubmitError("");
      try {
        const formData = new FormData();
        selectedFiles.forEach(f => formData.append("files", f));
        const params = {};
        if (keepFileIds.length > 0) {
          params.keepFileIds = keepFileIds;
        }
        await api.post(`/student/courses/${courseId}/assignments/${assignmentId}/submit`, formData, {
          headers: { "Content-Type": "multipart/form-data" },
          params,
        });
        setSelectedFiles([]);
        setKeepFileIds([]);
        setRefreshKey(k => k + 1);
      } catch (err) {
        setSubmitError(err.response?.data?.message || "Nộp bài thất bại");
      } finally {
        setSubmitting(false);
      }
    }

    if (loading) {
      return (
        <div style={{ flex: 1, display: "grid", placeItems: "center",
          background: "#f8fafc", color: "#94a3b8", fontSize: 13 }}>
          Đang tải bài tập...
        </div>
      );
    }

    if (error || !detail) {
      return (
        <div style={{ flex: 1, display: "grid", placeItems: "center",
          background: "#f8fafc" }}>
          <div style={{ textAlign: "center" }}>
            <div style={{ color: "#dc2626", marginBottom: 8, fontSize: 13 }}>{error || "Không tìm thấy bài tập"}</div>
            <button onClick={onBack}
              style={{ border: "1px solid #e2e8f0", borderRadius: 8, padding: "6px 14px",
                background: "#fff", color: "#475569", fontSize: 12.5, cursor: "pointer" }}>
              ← Quay lại
            </button>
          </div>
        </div>
      );
    }

    const sc = statusColors[detail.status] || statusColors.DRAFT;
    const attachments = detail.attachments || [];
    const assignPreviewFiles = attachments.map(a => ({
      name: a.displayName || a.originalFilename,
      url: a.url,
      size: a.fileSizeBytes,
      mimeType: a.mimeType,
    }));

    const sub = detail.studentSubmission;

    const canResubmit = isStudent && sub && !sub.scorePublishedAt && detail.status === "PUBLISHED";

    function FileCard({ file, onClick }) {
      const thumb = getThumbnail(file);
      const fname = file.displayName || file.originalFilename || file.name;
      return (
        <div onClick={onClick}
          style={{ width: 160, borderRadius: 10, border: "1px solid #e2e8f0",
            overflow: "hidden", cursor: "pointer", transition: "all .15s",
            background: "#fff", position: "relative" }}
          onMouseEnter={e => {
            e.currentTarget.style.boxShadow = "0 2px 8px rgba(0,0,0,.08)";
            e.currentTarget.style.transform = "translateY(-1px)";
          }}
          onMouseLeave={e => {
            e.currentTarget.style.boxShadow = "none";
            e.currentTarget.style.transform = "none";
          }}>
          <div style={{ width: "100%", height: 80, background: "#f8fafc",
            display: "flex", alignItems: "center", justifyContent: "center",
            borderBottom: "1px solid #e2e8f0" }}>
            {thumb.type === "image" ? (
              <img src={file.url} alt=""
                style={{ width: "100%", height: "100%", objectFit: "cover" }} />
            ) : (
              <div style={{ width: 36, height: 36, borderRadius: 8,
                background: thumb.bg, color: thumb.color,
                display: "grid", placeItems: "center",
                fontSize: 10, fontWeight: 800 }}>
                {thumb.label}
              </div>
            )}
          </div>
          <div style={{ padding: "8px 10px" }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: "#0f172a",
              lineHeight: 1.3, marginBottom: 2 }} className="truncate">{fname}</div>
            <div style={{ fontSize: 11, color: "#94a3b8",
              display: "flex", alignItems: "center", gap: 3,
              justifyContent: "space-between" }}>
              <span style={{ display: "flex", alignItems: "center", gap: 3 }}>
                <span>{getFileExt(fname)}</span>
                {file.fileSizeBytes != null && (
                  <><span>·</span><span>{fmtBytes(file.fileSizeBytes)}</span></>
                )}
              </span>
              <span onClick={e => { e.stopPropagation(); if (file.url) window.open(file.url, "_blank"); }}
                style={{ width: 24, height: 24, borderRadius: 6, display: "grid",
                  placeItems: "center", color: "#94a3b8", flexShrink: 0,
                  transition: ".1s", cursor: "pointer" }}
                onMouseEnter={e => e.currentTarget.style.color = "#2563eb"}
                onMouseLeave={e => e.currentTarget.style.color = "#94a3b8"}
                title="Tải xuống">
                <Ic n="download" size={13} />
              </span>
            </div>
          </div>
        </div>
      );
    }

    return (
      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column",
        minHeight: 0, background: "#f8fafc", overflow: "hidden" }}>

        {/* ── Header ─────────────────────────────────── */}
        <div style={{ flexShrink: 0, background: "#fff", borderBottom: "1px solid #e2e8f0",
          padding: "12px 16px", display: "flex", alignItems: "flex-start", gap: 10 }}>
          <button onClick={onBack}
            style={{ marginTop: 2, width: 28, height: 28, borderRadius: 7,
              border: "1px solid #e2e8f0", background: "#f8fafc", color: "#475569",
              display: "grid", placeItems: "center", cursor: "pointer", flexShrink: 0 }}>
            <Ic n="arrow_left" size={13} />
          </button>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
              <span style={{ fontSize: 15, fontWeight: 700, color: "#0f172a",
                lineHeight: 1.3 }} className="truncate">{detail.title}</span>
              <span style={{ fontSize: 10.5, fontWeight: 600, borderRadius: 5,
                padding: "2px 8px", background: sc.bg, color: sc.color, flexShrink: 0 }}>
                {sc.label}
              </span>
            </div>
            <div style={{ fontSize: 12, color: "#94a3b8", display: "flex", alignItems: "center", gap: 6 }}>
              <span style={{ display: "flex", alignItems: "center", gap: 3 }}>
                <Ic n="book" size={11} />{detail.courseTitle}
              </span>
              {!isStudent && (
                <><span>·</span>
                <span style={{ display: "flex", alignItems: "center", gap: 3 }}>
                  <Ic n="users" size={11} />
                  {detail.scope === "SPECIFIC_GROUPS" ? "Theo nhóm" : "Tất cả nhóm"}
                </span></>
              )}
            </div>
          </div>
        </div>

        {/* ── Scrollable body ──────────────────────────── */}
        <div style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: "16px 20px" }}>

          {/* Description */}
          {detail.description && (
            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: "#64748b", marginBottom: 6 }}>
                Mô tả
              </div>
              <div style={{ fontSize: 13, color: "#334155", lineHeight: 1.6,
                whiteSpace: "pre-wrap" }}>{detail.description}</div>
            </div>
          )}

          {/* Info grid */}
          <div style={{
            display: "grid", gridTemplateColumns: "1fr 1fr",
            gap: "8px 16px", marginBottom: 20,
            padding: 12, background: "#fff", borderRadius: 10,
            border: "1px solid #e2e8f0",
          }}>
            {(function() {
              const items = [
                { label: "Điểm tối đa", value: detail.maxScore != null ? `${detail.maxScore} điểm` : "—" },
                { label: "Ngày bắt đầu", value: fmtDate(detail.startDate) || "—" },
                { label: "Hạn nộp", value: fmtDate(detail.deadline) || "—" },
                ...(detail.deadline ? [{
                  label: "Nộp muộn",
                  value: detail.allowLateSubmission
                    ? `Cho phép (trừ ${detail.latePenaltyPercent ?? 0}%/ngày)`
                    : "Không cho phép"
                }] : []),
                {
                  label: "Loại file",
                  value: detail.allowedFileTypes?.length > 0
                    ? detail.allowedFileTypes.map(t => {
                        const map = {
                          "image/jpeg": "JPEG", "image/png": "PNG",
                          "application/pdf": "PDF", "application/zip": "ZIP",
                          "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "DOCX",
                          "application/vnd.openxmlformats-officedocument.presentationml.presentation": "PPTX",
                        };
                        return map[t] || t.split("/").pop()?.toUpperCase() || t;
                      }).join(", ")
                    : "Tất cả"
                },
                { label: "Kích thước tối đa", value: detail.maxFileSizeMb != null ? `${detail.maxFileSizeMb} MB` : "—" },
                { label: "Ngày tạo", value: fmtDate(detail.createdAt) || "—" },
              ];
              if (isStudent) {
                items.splice(6, 0, {
                  label: "Điểm đạt",
                  value: detail.passScore != null ? `${detail.passScore} / ${detail.maxScore ?? '?'}` : "—",
                });
              }
              if (isStudent && sub?.score != null) {
                items.splice(7, 0, {
                  label: "Điểm của bạn",
                  value: `${sub.score} / ${detail.maxScore ?? '?'}`,
                });
              }
              return items;
            })()
            .map(item => (
              <div key={item.label}>
                <div style={{ fontSize: 11, fontWeight: 500, color: "#94a3b8", marginBottom: 2 }}>
                  {item.label}
                </div>
                <div style={{ fontSize: 13, fontWeight: 600, color: "#0f172a" }}>{item.value}</div>
              </div>
            ))}
          </div>

          {/* Attachments — Google Classroom style */}
          {attachments.length > 0 && (
            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: "#64748b", marginBottom: 8,
                display: "flex", alignItems: "center", gap: 4 }}>
                <Ic n="paperclip" size={12} />
                File đính kèm ({attachments.length})
              </div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
                {attachments.map((att, i) => (
                  <FileCard key={att.id} file={att}
                    onClick={() => openPreview(assignPreviewFiles, i)} />
                ))}
              </div>
            </div>
          )}

          {/* ── Student submission section ── */}
          {isStudent && (
            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: "#64748b", marginBottom: 10,
                display: "flex", alignItems: "center", gap: 4 }}>
                <Ic n="upload" size={12} />
                Bài nộp của bạn
              </div>

              {sub && !resubmitting ? (
                /* ── Already submitted ── */
                <div style={{ background: "#fff", borderRadius: 10, border: "1px solid #e2e8f0",
                  overflow: "hidden" }}>
                  <div style={{ padding: "12px 14px", borderBottom: "1px solid #e2e8f0",
                    display: "flex", alignItems: "center", gap: 10 }}>
                    <span style={{ fontSize: 11.5, fontWeight: 600, borderRadius: 5,
                      padding: "2px 8px",
                      background: sub.status === "GRADED" ? "#dcfce7" : "#eaf1ff",
                      color: sub.status === "GRADED" ? "#16a34a" : "#2563eb" }}>
                      {sub.status === "GRADED" ? "Đã chấm" : "Đã nộp"}
                    </span>
                    <div style={{ display:"flex", flexDirection:"column", gap:2 }}>
                      <span style={{ fontSize: 12, color: "#94a3b8" }}>
                        {fmtDate(sub.submittedAt)}
                      </span>
                      {sub.isLate && <span style={{ fontSize: 11, color:"#dc2626", fontWeight:500 }}>Nộp trễ</span>}
                    </div>
                  </div>
                  {sub.files?.length > 0 && (
                    <div style={{ padding: "10px 14px", borderBottom: sub.score != null ? "1px solid #e2e8f0" : "none" }}>
                      <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
                        {sub.files.map(f => {
                          const subPreviewFiles = sub.files.map(sf => ({
                            name: sf.originalFilename,
                            url: sf.url,
                            size: sf.fileSizeBytes,
                            mimeType: sf.mimeType,
                          }));
                          return (
                            <FileCard key={f.id} file={f}
                              onClick={() => openPreview(subPreviewFiles, sub.files.indexOf(f))} />
                          );
                        })}
                      </div>
                    </div>
                  )}
                  {sub.score != null && (
                    <div style={{ padding: "12px 14px", display: "flex",
                      alignItems: "center", gap: 16 }}>
                      <div>
                        <div style={{ fontSize: 11, color: "#94a3b8", marginBottom: 2 }}>Điểm</div>
                        <div style={{ fontSize: 20, fontWeight: 700, color: "#0f172a" }}>
                          {sub.score}
                        </div>
                      </div>
                      {(() => {
                        const max = detail.maxScore ? Number(detail.maxScore) : 0;
                        const pass = detail.passScore ? Number(detail.passScore) : max * 0.5;
                        const p = Number(sub.score) >= pass;
                        return (
                          <span className={"chip " + (p ? "chip-success" : "chip-error")}
                            style={{ fontSize: 11, fontWeight: 600 }}>
                            {p ? "Đạt" : "Chưa đạt"}
                          </span>
                        );
                      })()}
                      {sub.feedback && (
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: 11, color: "#94a3b8", marginBottom: 2 }}>Nhận xét</div>
                          <div style={{ fontSize: 12.5, color: "#475569", whiteSpace: "pre-wrap" }}>
                            {sub.feedback}
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                  {sub.score == null && sub.status !== "GRADED" && (
                    <div style={{ padding: "12px 14px", fontSize: 12.5, color: "#64748b",
                      display: "flex", alignItems: "center", gap: 6 }}>
                      <Ic n="clock" size={13} />Đang chờ chấm điểm
                    </div>
                  )}
                  {canResubmit && (
                    <div style={{ padding: "8px 14px", borderTop: "1px solid #e2e8f0" }}>
                      <button className="btn btn-ghost btn-sm" onClick={() => { setResubmitting(true); setSelectedFiles([]); setKeepFileIds(sub.files?.map(f => f.id) || []); }}
                        style={{ fontSize: 12, color: "#2563eb" }}>
                        <Ic n="refresh" size={13} />Nộp lại
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                /* ── Not submitted yet ── */
                detail.status === "PUBLISHED" ? (
                  <div>
                    {resubmitting && sub?.files?.length > 0 && (
                      <div style={{ marginBottom: 16 }}>
                        <div style={{ fontSize: 12, fontWeight: 600, color: "#64748b", marginBottom: 8 }}>
                          File đã nộp trước đó (bỏ chọn để xoá)
                        </div>
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                          {sub.files.map(f => {
                            const checked = keepFileIds.includes(f.id);
                            return (
                              <label key={f.id}
                                style={{ display: "flex", alignItems: "center", gap: 10,
                                  padding: "8px 12px", background: checked ? "#eef2ff" : "#fff",
                                  borderRadius: 8, border: `1px solid ${checked ? "#2563eb" : "#e2e8f0"}`,
                                  cursor: "pointer", transition: ".13s" }}>
                                <input type="checkbox" checked={checked}
                                  onChange={() => {
                                    setKeepFileIds(prev =>
                                      prev.includes(f.id)
                                        ? prev.filter(id => id !== f.id)
                                        : [...prev, f.id]
                                    );
                                  }}
                                  style={{ accentColor: "#2563eb", width: 16, height: 16, flexShrink: 0 }} />
                                <Ic n="paperclip" size={14} style={{ color: "#64748b", flexShrink: 0 }} />
                                <span style={{ fontSize: 13, color: "#0f172a", flex: 1, minWidth: 0 }}
                                  className="truncate">{f.originalFilename}</span>
                                <span style={{ fontSize: 11, color: "#94a3b8", flexShrink: 0 }}>
                                  {fmtBytes(f.fileSizeBytes)}
                                </span>
                              </label>
                            );
                          })}
                        </div>
                      </div>
                    )}

                    <div style={{ marginBottom: 12 }}>
                      <label style={{ display: "block", marginBottom: 6, fontSize: 12,
                        fontWeight: 600, color: "#64748b" }}>
                        Chọn file để nộp
                      </label>
                      <div
                        style={{ border: "2px dashed #cbd5e1", borderRadius: 10, padding: 24,
                          textAlign: "center", cursor: "pointer", background: "#fff",
                          transition: ".15s" }}
                        onMouseEnter={e => e.currentTarget.style.borderColor = "#2563eb"}
                        onMouseLeave={e => e.currentTarget.style.borderColor = "#cbd5e1"}
                        onClick={() => document.getElementById("file-upload-input-" + assignmentId)?.click()}>
                        <Ic n="upload" size={24} style={{ color: "#94a3b8", marginBottom: 6 }} />
                        <div style={{ fontSize: 13, color: "#475569" }}>
                          Kéo thả file vào đây hoặc <span style={{ color: "#2563eb", fontWeight: 600 }}>chọn từ máy</span>
                        </div>
                        <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 4 }}>
                          {detail.allowedFileTypes?.length > 0
                            ? `Hỗ trợ: ${detail.allowedFileTypes.join(", ")}`
                            : "Tất cả các loại file"}
                          {detail.maxFileSizeMb != null && ` · Tổng tối đa ${detail.maxFileSizeMb} MB`}
                        </div>
                        <input id={"file-upload-input-" + assignmentId} type="file" multiple
                          style={{ display: "none" }} onChange={handleFileSelect} />
                      </div>
                    </div>

                    {selectedFiles.length > 0 && (
                      <div style={{ marginBottom: 12 }}>
                        <div style={{ fontSize: 11, color: "#94a3b8", marginBottom: 6 }}>
                          Đã chọn {selectedFiles.length} file
                        </div>
                        {selectedFiles.map((f, i) => (
                          <div key={i}
                            style={{ display: "flex", alignItems: "center", gap: 8,
                              padding: "8px 10px", background: "#fff", borderRadius: 8,
                              border: "1px solid #e2e8f0", marginBottom: 4 }}>
                            <div style={{ width: 28, height: 28, borderRadius: 6,
                              background: "#f1f5f9", display: "grid", placeItems: "center",
                              fontSize: 9, fontWeight: 800, color: "#64748b", flexShrink: 0 }}>
                              {getFileExt(f.name)}
                            </div>
                            <div style={{ flex: 1, minWidth: 0 }}>
                              <div style={{ fontSize: 12.5, fontWeight: 500, color: "#0f172a" }}
                                className="truncate">{f.name}</div>
                              <div style={{ fontSize: 11, color: "#94a3b8" }}>{fmtBytes(f.size)}</div>
                            </div>
                            <button onClick={() => removeFile(i)}
                              style={{ width: 24, height: 24, borderRadius: 6, border: "none",
                                background: "transparent", color: "#94a3b8", cursor: "pointer",
                                display: "grid", placeItems: "center" }}>
                              <Ic n="x" size={14} />
                            </button>
                          </div>
                        ))}
                      </div>
                    )}

                    {submitError && (
                      <div style={{ fontSize: 12.5, color: "#dc2626", marginBottom: 10 }}>
                        {submitError}
                      </div>
                    )}

                    <button disabled={(selectedFiles.length === 0 && keepFileIds.length === 0) || submitting}
                      onClick={handleSubmit}
                      style={{ width: "100%", height: 42, borderRadius: 10, border: "none",
                        background: (selectedFiles.length > 0 || keepFileIds.length > 0) && !submitting ? "#2563eb" : "#e2e8f0",
                        color: (selectedFiles.length > 0 || keepFileIds.length > 0) && !submitting ? "#fff" : "#94a3b8",
                        fontWeight: 600, fontSize: 13.5, cursor: (selectedFiles.length > 0 || keepFileIds.length > 0) && !submitting ? "pointer" : "default",
                        display: "flex", alignItems: "center", justifyContent: "center", gap: 6 }}>
                      {submitting ? "Đang nộp..." : <><Ic n="send" size={15} />Nộp bài</>}
                    </button>
                  </div>
                ) : (
                  <div style={{ padding: "16px", textAlign: "center", fontSize: 13,
                    color: "#94a3b8", background: "#fff", borderRadius: 10,
                    border: "1px solid #e2e8f0" }}>
                    {detail.status === "CLOSED" ? "Bài tập đã đóng" : "Bài tập chưa được xuất bản"}
                  </div>
                )
              )}
            </div>
          )}

          {/* Groups (instructor only) */}
          {!isStudent && detail.scope === "SPECIFIC_GROUPS" && detail.groupIds?.length > 0 && (
            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: "#64748b", marginBottom: 8,
                display: "flex", alignItems: "center", gap: 4 }}>
                <Ic n="users" size={12} />
                Nhóm được giao ({detail.groupIds.length})
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                {detail.groupIds.map(gid => (
                  <div key={gid}
                    style={{ padding: "8px 12px", background: "#fff", borderRadius: 8,
                      border: "1px solid #e2e8f0", fontSize: 12.5, color: "#475569" }}>
                    <Ic n="users" size={11} style={{ marginRight: 6, color: "#94a3b8" }} />
                    {gid}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* ── FilePreview overlay ─────────────────────── */}
        {previewIdx !== null && previewFilesList[previewIdx] && (
          React.createElement(window.FilePreview, {
            files: previewFilesList,
            initialIdx: previewIdx,
            onClose: closePreview,
          })
        )}
      </div>
    );
  }

  Object.assign(window, { AssignmentDetail });
})();
