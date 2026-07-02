// @ts-nocheck
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Section, Empty, Modal, ModalHead } = window;

  const STATUS_CFG = {
    PENDING:    { label: "Đang chờ xử lý", color: "#64748b", bg: "#f1f5f9" },
    PROCESSING: { label: "Đang xử lý",     color: "#0284c7", bg: "#e0f2fe" },
    INDEXED:    { label: "Đã index",       color: "#16a34a", bg: "#dcfce7" },
    FAILED:     { label: "Lỗi",            color: "#dc2626", bg: "#fee2e2" },
  };

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

  function AddAiDocModal({ courseId, onClose, onAdded }) {
    const [file, setFile] = useState(null);
    const [name, setName] = useState("");
    const [progress, setProgress] = useState(0);
    const [saving, setSaving] = useState(false);
    const [err, setErr] = useState("");

    function onFilePick(f) {
      if (!f) return;
      setFile(f);
      setName(prev => prev || f.name.replace(/\.[^.]+$/, ""));
      setErr("");
    }

    async function submit() {
      if (!file) { setErr("Vui lòng chọn file tài liệu"); return; }
      const ext = file.name.split(".").pop()?.toLowerCase() || "";
      const sourceType = ext === "pdf" ? "PDF" : (ext === "doc" || ext === "docx") ? "DOC" : null;
      if (!sourceType) { setErr("Chỉ hỗ trợ file .pdf, .doc, .docx"); return; }

      setSaving(true); setErr(""); setProgress(0);
      try {
        const { uploadUrl, s3Key } = await window.__aiService.presignAiSourceUpload({
          courseId, originalFilename: file.name, mimeType: file.type || "application/octet-stream",
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
          courseId, sourceType, sourceName: name.trim() || file.name, metadata: { s3Key },
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
      <Modal open onClose={onClose} max={480}>
        <ModalHead title="Thêm tài liệu AI" icon="sparkles" iconBg="#f5f0ff" iconColor="#7c3aed" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <input type="file" accept=".pdf,.doc,.docx" onChange={e => onFilePick(e.target.files?.[0])} />
          {file && (
            <Field label="Tên hiển thị">
              <input className="input" value={name} onChange={e => setName(e.target.value)} placeholder={file.name} />
            </Field>
          )}
          {saving && (
            <div>
              <div style={{ height: 5, borderRadius: 999, background: "var(--border)", overflow: "hidden" }}>
                <div style={{ width: progress + "%", height: "100%", background: "var(--accent)", transition: "width .2s" }} />
              </div>
              <div className="t-xs muted" style={{ marginTop: 4, textAlign: "center" }}>Đang xử lý... {progress}%</div>
            </div>
          )}
          {err && <div className="t-xs" style={{ color: "var(--error)" }}>{err}</div>}
          <div className="row gap-8" style={{ justifyContent: "flex-end" }}>
            <button className="btn btn-ghost btn-sm" onClick={onClose} disabled={saving}>Hủy</button>
            <button className="btn btn-primary btn-sm" onClick={submit} disabled={saving}>
              {saving ? "Đang xử lý..." : "Đưa vào AI"}
            </button>
          </div>
        </div>
      </Modal>
    );
  }

  /**
   * Tab "Tài liệu AI" — instructor quản lý kho tri thức RAG của khóa học.
   * Props: courseId
   */
  function AiDocsTab({ courseId }) {
    const [sources, setSources] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showAdd, setShowAdd] = useState(false);
    const [busyId, setBusyId] = useState(null);

    const load = async () => {
      if (!courseId) return;
      setLoading(true);
      try {
        const data = await window.__aiService.listAiSources(courseId);
        setSources(data);
      } catch (e) {
        console.error("Failed to load AI sources", e);
      } finally {
        setLoading(false);
      }
    };

    useEffect(() => { load(); }, [courseId]);

    async function handleReingest(id) {
      setBusyId(id);
      try { await window.__aiService.reingestAiSource(id); await load(); }
      catch (e) { console.error(e); }
      finally { setBusyId(null); }
    }

    async function handleDelete(id) {
      if (!window.confirm("Xóa tài liệu này khỏi kho tri thức AI?")) return;
      setBusyId(id);
      try { await window.__aiService.deleteAiSource(id); await load(); }
      catch (e) { console.error(e); }
      finally { setBusyId(null); }
    }

    return (
      <Section>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 4 }}>
          <h2 className="t-h2">Tài liệu AI</h2>
          <button className="btn btn-primary btn-sm" style={{ gap: 6 }} onClick={() => setShowAdd(true)}>
            <Ic n="plus" size={14} />Thêm tài liệu
          </button>
        </div>
        <p className="muted" style={{ fontSize: 12.5, marginBottom: 20 }}>
          Tài liệu PDF/DOCX đưa vào đây sẽ được trợ lý AI dùng để trả lời câu hỏi của học viên trong khóa học này.
        </p>

        {loading && <div className="muted" style={{ fontSize: 13.5 }}>Đang tải...</div>}

        {!loading && sources.length === 0 && (
          <Empty icon="file" title="Chưa có tài liệu nào" sub="Thêm PDF hoặc DOCX để trợ lý AI có thể trả lời dựa trên nội dung khóa học." />
        )}

        {!loading && sources.length > 0 && (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {sources.map(s => {
              const cfg = STATUS_CFG[s.ingestStatus] || STATUS_CFG.PENDING;
              return (
                <div key={s.id} className="row gap-14" style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 12 }}>
                  <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 11, background: cfg.bg, color: cfg.color }}>
                    <Ic n="file" size={21} />
                  </div>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 14 }} className="truncate">{s.sourceName}</div>
                    <div className="t-xs muted">
                      {s.sourceType} · {fmtDT(s.createdAt)}
                      {s.ingestStatus === "INDEXED" && s.chunkCount != null ? ` · ${s.chunkCount} đoạn` : ""}
                    </div>
                    {s.ingestStatus === "FAILED" && s.errorMessage && (
                      <div className="t-xs" style={{ color: "var(--error)", marginTop: 3 }}>{s.errorMessage}</div>
                    )}
                  </div>
                  <span style={{ padding: "3px 10px", borderRadius: 999, fontSize: 11.5, fontWeight: 700, background: cfg.color, color: "#fff", flex: "none" }}>
                    {cfg.label}
                  </span>
                  <button className="btn btn-ghost btn-icon btn-sm" style={{ width: 32, height: 32 }} title="Xử lý lại"
                    disabled={busyId === s.id} onClick={() => handleReingest(s.id)}>
                    <Ic n="rotate_ccw" size={14} />
                  </button>
                  <button className="btn btn-ghost btn-icon btn-sm" style={{ width: 32, height: 32, color: "var(--error)" }} title="Xóa"
                    disabled={busyId === s.id} onClick={() => handleDelete(s.id)}>
                    <Ic n="x" size={14} />
                  </button>
                </div>
              );
            })}
          </div>
        )}

        {showAdd && (
          <AddAiDocModal courseId={courseId} onClose={() => setShowAdd(false)} onAdded={load} />
        )}
      </Section>
    );
  }

  window.AiDocsTab = AiDocsTab;
})();
