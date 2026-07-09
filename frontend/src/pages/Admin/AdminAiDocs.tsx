// @ts-nocheck
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Section, Empty, Modal, ModalHead, Search, Tabs, Select } = window;
  const api = window.httpClient;

  const STATUS_CFG = {
    PENDING:    { label: "Đang chờ xử lý", color: "#64748b", bg: "#f1f5f9" },
    PROCESSING: { label: "Đang xử lý",     color: "#0284c7", bg: "#e0f2fe" },
    INDEXED:    { label: "Đã index",       color: "#16a34a", bg: "#dcfce7" },
    FAILED:     { label: "Lỗi",            color: "#dc2626", bg: "#fee2e2" },
  };

  function scopeOf(s) { return s.courseId ? "course" : "system"; }

  function fmtDT(iso) {
    if (!iso) return "—";
    return new Date(iso).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" });
  }

  function Field({ label, children }) {
    return (
      <div>
        <label className="t-label" style={{ display: "block", marginBottom: 7 }}>{label}</label>
        {children}
      </div>
    );
  }

  /* Cùng pattern Dropzone dùng ở AddResourceModal (CourseModals.tsx) / AiDocsTab.tsx — bản copy cục bộ. */
  function Dropzone({ icon, title, hint, file, onClick, onDrop }) {
    const [over, setOver] = useState(false);
    return (
      <div
        onDragOver={e => { e.preventDefault(); setOver(true); }}
        onDragLeave={() => setOver(false)}
        onDrop={e => { e.preventDefault(); setOver(false); onDrop(e.dataTransfer.files[0]); }}
        onClick={onClick}
        style={{
          minHeight: 140, borderRadius: 10, border: `2px dashed ${over ? "var(--accent)" : "var(--border)"}`,
          display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 8,
          cursor: "pointer", background: over ? "var(--surface-2)" : "transparent", transition: "all .15s", padding: "16px 0",
        }}>
        {file
          ? <><Ic n="check" size={22} style={{ color: "var(--accent)" }} /><span style={{ fontSize: 13, color: "var(--text-2)", fontWeight: 500 }}>{file.name}</span></>
          : <><Ic n={icon} size={22} style={{ color: "var(--text-3)" }} /><span style={{ fontSize: 13, color: "var(--text-3)", fontWeight: 500 }}>{title}</span><span style={{ fontSize: 11.5, color: "var(--text-3)" }}>{hint}</span></>
        }
      </div>
    );
  }

  function AddAdminDocModal({ onClose, onAdded }) {
    const [scope, setScope] = useState("system");
    const [courses, setCourses] = useState([]);
    const [courseId, setCourseId] = useState("");
    const [file, setFile] = useState(null);
    const [name, setName] = useState("");
    const [progress, setProgress] = useState(0);
    const [saving, setSaving] = useState(false);
    const [err, setErr] = useState("");
    const fileRef = React.useRef();

    useEffect(() => {
      api.get('/admin/courses', { params: { page: 0, size: 200 } })
        .then(r => setCourses(r.data?.content || []))
        .catch(() => {});
    }, []);

    function onFilePick(f) {
      if (!f) return;
      const ext = f.name.split(".").pop()?.toLowerCase() || "";
      if (ext !== "pdf" && ext !== "doc" && ext !== "docx") { setErr("Chỉ hỗ trợ file .pdf, .doc, .docx"); return; }
      setFile(f);
      setName(prev => prev || f.name.replace(/\.[^.]+$/, ""));
      setErr("");
    }

    async function submit() {
      if (scope === "course" && !courseId) { setErr("Vui lòng chọn khóa học"); return; }
      if (!file) { setErr("Vui lòng chọn file tài liệu"); return; }
      const ext = file.name.split(".").pop()?.toLowerCase() || "";
      const sourceType = ext === "pdf" ? "PDF" : "DOC";
      const targetCourseId = scope === "course" ? courseId : null;

      setSaving(true); setErr(""); setProgress(0);
      try {
        const { uploadUrl, s3Key } = await window.__aiService.presignAiSourceUpload({
          courseId: targetCourseId, originalFilename: file.name, mimeType: file.type || "application/octet-stream",
        });
        await new Promise((res, rej) => {
          const xhr = new XMLHttpRequest();
          xhr.open("PUT", uploadUrl);
          xhr.setRequestHeader("Content-Type", file.type || "application/octet-stream");
          xhr.upload.onprogress = (e) => { if (e.lengthComputable) setProgress(Math.round(e.loaded / e.total * 100)); };
          xhr.onload = () => xhr.status < 300 ? res(null) : rej(new Error("Upload thất bại"));
          xhr.onerror = () => rej(new Error("Mất kết nối"));
          xhr.send(file);
        });
        await window.__aiService.createAiSource({
          courseId: targetCourseId, sourceType, sourceName: name.trim() || file.name, metadata: { s3Key },
        });
        onAdded();
        onClose();
      } catch (e) {
        setErr(e?.response?.data?.message || e?.message || "Thao tác thất bại");
      } finally {
        setSaving(false);
      }
    }

    return (
      <Modal open onClose={onClose} max={560}>
        <ModalHead title="Thêm tài liệu AI" icon="sparkles" iconBg="#f5f0ff" iconColor="#7c3aed" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div className="row gap-12" style={{ flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 220 }}>
              <Field label="Loại tài liệu">
                <Select value={scope} onChange={setScope} options={[
                  { v: "system", label: "Tài liệu hệ thống (không gắn khóa học)" },
                  { v: "course", label: "Gắn với khóa học cụ thể" },
                ]} />
              </Field>
            </div>
            {scope === "course" && (
              <div style={{ flex: 1, minWidth: 220 }}>
                <Field label="Khóa học">
                  <Select value={courseId} onChange={setCourseId} options={[
                    { v: "", label: "Chọn khóa học..." },
                    ...courses.map(c => ({ v: c.id, label: c.title })),
                  ]} />
                </Field>
              </div>
            )}
          </div>
          <input ref={fileRef} type="file" accept=".pdf,.doc,.docx" style={{ display: "none" }}
            onChange={e => onFilePick(e.target.files?.[0])} />
          <Dropzone icon="upload" title="Kéo thả tài liệu vào đây" hint="PDF, DOCX · tối đa 200MB"
            file={file} onClick={() => fileRef.current?.click()} onDrop={onFilePick} />
          <Field label="Tên hiển thị">
            <input className="input" value={name} onChange={e => setName(e.target.value)}
              placeholder="VD: Quy chế học vụ" autoFocus={!file} />
          </Field>
          {saving && (
            <div>
              <div style={{ height: 5, borderRadius: 999, background: "var(--border)", overflow: "hidden" }}>
                <div style={{ width: progress + "%", height: "100%", background: "var(--accent)", transition: "width .2s" }} />
              </div>
              <div className="t-xs muted" style={{ marginTop: 4, textAlign: "center" }}>Đang xử lý... {progress}%</div>
            </div>
          )}
          {err && <div className="t-xs" style={{ color: "var(--error)" }}>{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Hủy</button>
          <button className="btn btn-primary" onClick={submit} disabled={saving}>
            <Ic n="plus" size={16} />{saving ? "Đang xử lý..." : "Thêm"}
          </button>
        </div>
      </Modal>
    );
  }

  function FilterModal({ instructorFilter, courseFilter, onApply, onClose }) {
    const [instructors, setInstructors] = useState([]);
    const [courses, setCourses] = useState([]);
    const [instructorId, setInstructorId] = useState(instructorFilter || "");
    const [courseId, setCourseId] = useState(courseFilter || "");

    useEffect(() => {
      api.get('/admin/users', { params: { role: 'INSTRUCTOR', page: 1, size: 200 } })
        .then(r => setInstructors(r.data?.items || []))
        .catch(() => {});
      api.get('/admin/courses', { params: { page: 0, size: 200 } })
        .then(r => setCourses(r.data?.content || []))
        .catch(() => {});
    }, []);

    const courseOptions = instructorId ? courses.filter(c => c.instructorId === instructorId) : courses;

    function apply() {
      onApply({ instructorId: instructorId || null, courseId: courseId || null });
      onClose();
    }
    function clearAll() {
      onApply({ instructorId: null, courseId: null });
      onClose();
    }

    return (
      <Modal open onClose={onClose} max={460}>
        <ModalHead title="Bộ lọc tài liệu" icon="filter" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <Field label="Giảng viên">
            <Select value={instructorId} onChange={v => { setInstructorId(v); setCourseId(""); }} options={[
              { v: "", label: "Tất cả giảng viên" },
              ...instructors.map(i => ({ v: i.id, label: i.fullName })),
            ]} />
          </Field>
          <Field label="Khóa học">
            <Select value={courseId} onChange={setCourseId} options={[
              { v: "", label: "Tất cả khóa học" },
              ...courseOptions.map(c => ({ v: c.id, label: c.title })),
            ]} />
          </Field>
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={clearAll}>Xóa lọc</button>
          <button className="btn btn-primary" onClick={apply}>Áp dụng</button>
        </div>
      </Modal>
    );
  }

  /* Popup xem trước tài liệu AI — 2 tab: file gốc và nội dung AI đã đọc (chunks). */
  function AiDocPreviewModal({ source, onClose }) {
    const [tab, setTab] = useState("file");
    const canViewFile = source.sourceType === "PDF" || source.sourceType === "DOC";
    const [viewUrl, setViewUrl] = useState(null);
    const [viewLoading, setViewLoading] = useState(false);
    const [viewErr, setViewErr] = useState("");
    const [chunks, setChunks] = useState(null);
    const [chunksErr, setChunksErr] = useState("");

    useEffect(() => {
      if (tab !== "file" || !canViewFile || viewUrl) return;
      setViewLoading(true); setViewErr("");
      window.__aiService.getAiSourceViewUrl(source.id)
        .then(r => setViewUrl(r.url))
        .catch(e => setViewErr(e?.response?.data?.message || "Không thể tải file gốc"))
        .finally(() => setViewLoading(false));
    }, [tab]);

    useEffect(() => {
      if (tab !== "chunks" || chunks !== null) return;
      setChunksErr("");
      window.__aiService.getAiSourceChunks(source.id)
        .then(setChunks)
        .catch(e => { setChunksErr(e?.response?.data?.message || "Không thể tải nội dung"); setChunks([]); });
    }, [tab]);

    const loadingNode = <div style={{ padding: "60px 0", textAlign: "center", color: "var(--text-3)", fontSize: 13 }}>Đang tải...</div>;
    const iframeH = "calc(100vh - 260px)";

    return (
      <Modal open onClose={onClose} max={720} maxHeight="calc(100vh - 48px)">
        <ModalHead title={source.sourceName} icon="file" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div style={{ padding: "0 20px", borderBottom: "1px solid var(--border)" }}>
          <Tabs value={tab} onChange={setTab} items={[
            { v: "file", label: "File gốc" },
            { v: "chunks", label: "Nội dung AI đã đọc" },
          ]} />
        </div>
        <div className="modal-body" style={{ padding: 0, overflow: "auto" }}>
          {tab === "file" && (
            !canViewFile ? (
              <Empty icon="file" title="Không có file gốc" sub="Tài liệu dạng văn bản/URL không có file gốc để xem." />
            ) : viewLoading ? loadingNode
              : viewErr ? <div style={{ padding: "60px 0", textAlign: "center", color: "var(--text-3)", fontSize: 13 }}>{viewErr}</div>
              : viewUrl ? (
                source.sourceType === "PDF"
                  ? <iframe src={viewUrl} style={{ width: "100%", height: iframeH, border: "none", display: "block" }} title="preview" />
                  : <iframe src={`https://docs.google.com/viewer?url=${encodeURIComponent(viewUrl)}&embedded=true`}
                      style={{ width: "100%", height: iframeH, border: "none", display: "block" }} title="preview" />
              ) : null
          )}
          {tab === "chunks" && (
            chunks === null ? loadingNode
              : chunksErr ? <div style={{ padding: "60px 0", textAlign: "center", color: "var(--text-3)", fontSize: 13 }}>{chunksErr}</div>
              : chunks.length === 0 ? <Empty icon="file" title="Chưa có nội dung" sub="Tài liệu chưa được xử lý hoặc không trích xuất được nội dung." />
              : (
                <div style={{ padding: "16px 20px", display: "flex", flexDirection: "column", gap: 14 }}>
                  {chunks.map((c, i) => (
                    <div key={i} style={{ paddingBottom: 14, borderBottom: i < chunks.length - 1 ? "1px solid var(--border)" : "none" }}>
                      {c.sectionTitle && <div style={{ fontWeight: 700, fontSize: 12.5, marginBottom: 6, color: "var(--text-2)" }}>{c.sectionTitle}</div>}
                      <div style={{ fontSize: 13, lineHeight: 1.6, whiteSpace: "pre-wrap", color: "var(--text-2)" }}>{c.chunkText}</div>
                    </div>
                  ))}
                </div>
              )
          )}
        </div>
      </Modal>
    );
  }

  function ConfirmDeleteModal({ source, onClose, onConfirm, busy }) {
    return (
      <Modal open onClose={onClose} max={460}>
        <ModalHead title="Xác nhận xóa" sub="Tài liệu sẽ bị gỡ khỏi kho tri thức AI, trợ lý sẽ không còn dùng nội dung này để trả lời."
          icon="warn" iconBg="#fff7ed" iconColor="#f97316" onClose={onClose} />
        <div className="modal-body">
          <div style={{ fontWeight: 600, fontSize: 14 }} className="truncate">{source.sourceName}</div>
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>Hủy</button>
          <button className="btn btn-danger" onClick={onConfirm} disabled={busy}>{busy ? "Đang xóa..." : "Xóa"}</button>
        </div>
      </Modal>
    );
  }

  /**
   * Trang Admin "Quản lý Tài liệu AI" — xem/quản lý TOÀN BỘ tài liệu AI trong hệ thống
   * (mọi khóa học + tài liệu hệ thống không gắn khóa học), và đăng tài liệu hệ thống mới.
   */
  function AdminAiDocs() {
    const [sources, setSources] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showAdd, setShowAdd] = useState(false);
    const [busyId, setBusyId] = useState(null);
    const [search, setSearch] = useState("");
    const [scopeFilter, setScopeFilter] = useState("all");
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [showFilter, setShowFilter] = useState(false);
    const [instructorFilter, setInstructorFilter] = useState(null);
    const [courseFilter, setCourseFilter] = useState(null);
    const [previewTarget, setPreviewTarget] = useState(null);

    const load = async (opts = {}) => {
      if (!opts.silent) setLoading(true);
      try {
        const data = await window.__aiService.listAiSources();
        setSources(data);
      } catch (e) {
        console.error("Failed to load AI sources", e);
      } finally {
        if (!opts.silent) setLoading(false);
      }
    };

    useEffect(() => { load(); }, []);

    // Ingest chạy nền (async) — poll lặng lẽ trong khi còn tài liệu PENDING/PROCESSING,
    // tự dừng khi tất cả đã INDEXED/FAILED hoặc sau ~2 phút an toàn.
    const hasPending = sources.some(s => s.ingestStatus === "PENDING" || s.ingestStatus === "PROCESSING");
    useEffect(() => {
      if (!hasPending) return;
      const start = Date.now();
      const interval = setInterval(() => {
        if (Date.now() - start > 120_000) { clearInterval(interval); return; }
        load({ silent: true });
      }, 3000);
      return () => clearInterval(interval);
    }, [hasPending]);

    async function handleReingest(id) {
      setBusyId(id);
      try { await window.__aiService.reingestAiSource(id); await load(); }
      catch (e) { console.error(e); }
      finally { setBusyId(null); }
    }

    async function confirmDelete() {
      if (!deleteTarget) return;
      setBusyId(deleteTarget.id);
      try { await window.__aiService.deleteAiSource(deleteTarget.id); await load(); }
      catch (e) { console.error(e); }
      finally { setBusyId(null); setDeleteTarget(null); }
    }

    const filteredSources = sources.filter(s => {
      if (scopeFilter !== "all" && scopeOf(s) !== scopeFilter) return false;
      if (instructorFilter && s.instructorId !== instructorFilter) return false;
      if (courseFilter && s.courseId !== courseFilter) return false;
      if (search.trim() && !s.sourceName.toLowerCase().includes(search.trim().toLowerCase())) return false;
      return true;
    });
    const systemCount = sources.filter(s => scopeOf(s) === "system").length;
    const activeFilterCount = [instructorFilter, courseFilter].filter(Boolean).length;

    return (
      <div className="page fade-in">
        <div className="page-head">
          <h1 className="t-h1">Quản lý Tài liệu AI</h1>
          <p>Xem và quản lý toàn bộ tài liệu AI trong hệ thống — mọi khóa học và tài liệu hệ thống dùng chung.</p>
        </div>

        <Section>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 4 }}>
            <h2 className="t-h2">Tài liệu AI</h2>
            <button className="btn btn-primary btn-sm" style={{ gap: 6 }} onClick={() => setShowAdd(true)}>
              <Ic n="plus" size={14} />Thêm tài liệu
            </button>
          </div>
          <p className="muted" style={{ fontSize: 12.5, marginBottom: 16 }}>
            Tài liệu hệ thống được trợ lý AI dùng chung cho mọi cuộc hội thoại, bất kể khóa học nào.
          </p>

          {!loading && sources.length > 0 && (
            <div className="row gap-12" style={{ marginBottom: 16, flexWrap: "wrap" }}>
              <Search placeholder="Tìm theo tên tài liệu..." value={search} onChange={setSearch} style={{ maxWidth: 280 }} />
              <Tabs
                value={scopeFilter}
                onChange={setScopeFilter}
                items={[
                  { v: "all", label: "Tất cả", count: sources.length },
                  { v: "system", label: "Tài liệu hệ thống", count: systemCount },
                ]}
              />
              <button className="btn btn-ghost btn-sm" style={{ gap: 6 }} onClick={() => setShowFilter(true)}>
                <Ic n="filter" size={14} />Bộ lọc{activeFilterCount > 0 ? ` (${activeFilterCount})` : ""}
              </button>
              {activeFilterCount > 0 && (
                <button className="btn btn-ghost btn-sm" style={{ color: "var(--text-3)" }}
                  onClick={() => { setInstructorFilter(null); setCourseFilter(null); }}>
                  <Ic n="x" size={13} />Xóa lọc
                </button>
              )}
            </div>
          )}

          {loading && <div className="muted" style={{ fontSize: 13.5 }}>Đang tải...</div>}

          {!loading && sources.length === 0 && (
            <Empty icon="file" title="Chưa có tài liệu nào" sub="Thêm tài liệu hệ thống hoặc tài liệu riêng cho một khóa học." />
          )}

          {!loading && sources.length > 0 && filteredSources.length === 0 && (
            <Empty icon="search" title="Không tìm thấy tài liệu phù hợp" sub="Thử từ khóa khác hoặc chọn lại bộ lọc." />
          )}

          {!loading && filteredSources.length > 0 && (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {filteredSources.map(s => {
                const cfg = STATUS_CFG[s.ingestStatus] || STATUS_CFG.PENDING;
                const isSystem = scopeOf(s) === "system";
                return (
                  <div key={s.id} className="row gap-12" style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 12 }}>
                    <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 11, background: cfg.bg, color: cfg.color }}>
                      <Ic n="file" size={21} />
                    </div>
                    <div className="grow" style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 14 }} className="truncate">{s.sourceName}</div>
                      <div className="row gap-6" style={{ marginTop: 4, flexWrap: "wrap" }}>
                        <span className="t-xs muted">
                          {s.sourceType} · {fmtDT(s.createdAt)}
                          {s.ingestStatus === "INDEXED" && s.chunkCount != null ? ` · ${s.chunkCount} đoạn` : ""}
                        </span>
                        <span className="row gap-4" style={{
                          padding: "2px 8px", borderRadius: 999, fontSize: 10.5, fontWeight: 600, flex: "none",
                          background: isSystem ? "#f1f5f9" : "#eaf1ff", color: isSystem ? "#475569" : "#185fa5",
                        }}>
                          <Ic n={isSystem ? "shield" : "book"} size={11} />
                          {isSystem ? "Tài liệu hệ thống" : (s.courseName || "Không rõ khóa học")}
                        </span>
                      </div>
                      {s.ingestStatus === "FAILED" && s.errorMessage && (
                        <div className="t-xs" style={{ color: "var(--error)", marginTop: 3 }}>{s.errorMessage}</div>
                      )}
                    </div>
                    <span style={{ padding: "3px 10px", borderRadius: 999, fontSize: 11.5, fontWeight: 700, background: cfg.color, color: "#fff", flex: "none" }}>
                      {cfg.label}
                    </span>
                    <button className="btn btn-ghost btn-icon btn-sm" style={{ width: 32, height: 32 }} title="Xem trước"
                      onClick={() => setPreviewTarget(s)}>
                      <Ic n="eye" size={14} />
                    </button>
                    <button className="btn btn-ghost btn-icon btn-sm" style={{ width: 32, height: 32 }} title="Xử lý lại"
                      disabled={busyId === s.id} onClick={() => handleReingest(s.id)}>
                      <Ic n="rotate_ccw" size={14} />
                    </button>
                    <button className="btn btn-ghost btn-icon btn-sm" style={{ width: 32, height: 32, color: "var(--error)" }} title="Xóa"
                      disabled={busyId === s.id} onClick={() => setDeleteTarget(s)}>
                      <Ic n="x" size={14} />
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          {showAdd && (
            <AddAdminDocModal onClose={() => setShowAdd(false)} onAdded={load} />
          )}
          {showFilter && (
            <FilterModal
              instructorFilter={instructorFilter}
              courseFilter={courseFilter}
              onApply={({ instructorId, courseId }) => { setInstructorFilter(instructorId); setCourseFilter(courseId); }}
              onClose={() => setShowFilter(false)}
            />
          )}
          {deleteTarget && (
            <ConfirmDeleteModal source={deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={confirmDelete} busy={busyId === deleteTarget.id} />
          )}
          {previewTarget && (
            <AiDocPreviewModal source={previewTarget} onClose={() => setPreviewTarget(null)} />
          )}
        </Section>
      </div>
    );
  }

  window.AdminAiDocs = AdminAiDocs;
})();
