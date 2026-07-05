// @ts-nocheck
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Section, Empty, Modal, ModalHead, Search, Tabs } = window;

  const STATUS_CFG = {
    PENDING:    { label: "Đang chờ xử lý", color: "#64748b", bg: "#f1f5f9" },
    PROCESSING: { label: "Đang xử lý",     color: "#0284c7", bg: "#e0f2fe" },
    INDEXED:    { label: "Đã index",       color: "#16a34a", bg: "#dcfce7" },
    FAILED:     { label: "Lỗi",            color: "#dc2626", bg: "#fee2e2" },
  };

  const ORIGIN_CFG = {
    lesson:     { label: "Từ bài giảng", icon: "folder", color: "#2563eb", bg: "#eaf1ff" },
    standalone: { label: "Tài liệu riêng", icon: "upload", color: "#7c3aed", bg: "#f5f0ff" },
  };
  function originOf(s) { return s.resourceId ? "lesson" : "standalone"; }

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

  /* Cùng pattern Dropzone dùng ở AddResourceModal (CourseModals.tsx) — bản copy cục bộ. */
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

  function AddAiDocModal({ courseId, onClose, onAdded }) {
    const [file, setFile] = useState(null);
    const [name, setName] = useState("");
    const [progress, setProgress] = useState(0);
    const [saving, setSaving] = useState(false);
    const [err, setErr] = useState("");
    const fileRef = React.useRef();

    function onFilePick(f) {
      if (!f) return;
      const ext = f.name.split(".").pop()?.toLowerCase() || "";
      if (ext !== "pdf" && ext !== "doc" && ext !== "docx") { setErr("Chỉ hỗ trợ file .pdf, .doc, .docx"); return; }
      setFile(f);
      setName(prev => prev || f.name.replace(/\.[^.]+$/, ""));
      setErr("");
    }

    async function submit() {
      if (!file) { setErr("Vui lòng chọn file tài liệu"); return; }
      const ext = file.name.split(".").pop()?.toLowerCase() || "";
      const sourceType = ext === "pdf" ? "PDF" : "DOC";

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
      <Modal open onClose={onClose} max={560}>
        <ModalHead title="Thêm tài liệu AI" icon="sparkles" iconBg="#f5f0ff" iconColor="#7c3aed" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <input ref={fileRef} type="file" accept=".pdf,.doc,.docx" style={{ display: "none" }}
            onChange={e => onFilePick(e.target.files?.[0])} />
          <Dropzone icon="upload" title="Kéo thả tài liệu vào đây" hint="PDF, DOCX · tối đa 200MB"
            file={file} onClick={() => fileRef.current?.click()} onDrop={onFilePick} />
          <Field label="Tên hiển thị">
            <input className="input" value={name} onChange={e => setName(e.target.value)}
              placeholder="VD: Giáo trình chương 1" autoFocus={!file} />
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

  function PickLessonResourcesModal({ courseId, onClose, onAdded }) {
    const [resources, setResources] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selected, setSelected] = useState({});
    const [saving, setSaving] = useState(false);
    const [err, setErr] = useState("");

    useEffect(() => {
      (async () => {
        setLoading(true);
        try {
          const data = await window.__aiService.listAvailableResources(courseId);
          setResources(data);
        } catch (e) {
          setErr(e?.response?.data?.message || "Không tải được danh sách tài liệu");
        } finally {
          setLoading(false);
        }
      })();
    }, [courseId]);

    function toggle(resourceId) {
      setSelected(prev => ({ ...prev, [resourceId]: !prev[resourceId] }));
    }

    async function submit() {
      const resourceIds = Object.keys(selected).filter(id => selected[id]);
      if (resourceIds.length === 0) { setErr("Chọn ít nhất 1 tài liệu"); return; }
      setSaving(true); setErr("");
      try {
        await window.__aiService.addResourcesToAi(courseId, resourceIds);
        onAdded();
        onClose();
      } catch (e) {
        setErr(e?.response?.data?.message || e?.message || "Thao tác thất bại");
      } finally {
        setSaving(false);
      }
    }

    // Nhóm theo chapterTitle → lessonTitle để hiển thị cây, giữ đúng thứ tự trả về từ API.
    const chapterGroups = [];
    const chapterIndex = new Map();
    resources.forEach(r => {
      let chapter = chapterIndex.get(r.chapterTitle);
      if (!chapter) {
        chapter = { chapterTitle: r.chapterTitle, lessons: [] };
        chapterIndex.set(r.chapterTitle, chapter);
        chapterGroups.push(chapter);
      }
      let lesson = chapter.lessons.find(l => l.lessonTitle === r.lessonTitle);
      if (!lesson) {
        lesson = { lessonTitle: r.lessonTitle, resources: [] };
        chapter.lessons.push(lesson);
      }
      lesson.resources.push(r);
    });

    const selectedCount = Object.values(selected).filter(Boolean).length;

    return (
      <Modal open onClose={onClose} max={560}>
        <ModalHead title="Chọn từ tài liệu bài giảng" icon="folder" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <p className="muted" style={{ fontSize: 12.5 }}>
            Chọn tài liệu PDF/DOCX đã có sẵn trong bài giảng để đưa vào kho tri thức AI, không cần tải lên lại.
          </p>
          {loading && <div className="muted" style={{ fontSize: 13.5 }}>Đang tải...</div>}
          {!loading && resources.length === 0 && (
            <Empty icon="file" title="Không có tài liệu phù hợp" sub="Chỉ hỗ trợ file PDF/DOCX đính kèm trong bài giảng." />
          )}
          {!loading && resources.length > 0 && (
            <div style={{ maxHeight: 360, overflowY: "auto", display: "flex", flexDirection: "column", gap: 16 }}>
              {chapterGroups.map(ch => (
                <div key={ch.chapterTitle}>
                  <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6 }}>{ch.chapterTitle}</div>
                  {ch.lessons.map(l => (
                    <div key={l.lessonTitle} style={{ marginLeft: 10, marginBottom: 8 }}>
                      <div className="t-xs muted" style={{ marginBottom: 4 }}>{l.lessonTitle}</div>
                      {l.resources.map(r => (
                        <label key={r.resourceId} className="row gap-8"
                          style={{ padding: "6px 8px", borderRadius: 8, cursor: r.alreadyAdded ? "default" : "pointer", opacity: r.alreadyAdded ? 0.6 : 1 }}>
                          <input type="checkbox" checked={r.alreadyAdded || !!selected[r.resourceId]} disabled={r.alreadyAdded}
                            onChange={() => toggle(r.resourceId)} />
                          <Ic n="file" size={14} />
                          <span className="grow truncate" style={{ fontSize: 13 }}>{r.displayName}</span>
                          {r.alreadyAdded && (
                            <span style={{ padding: "2px 8px", borderRadius: 999, fontSize: 10.5, fontWeight: 700, background: "#dcfce7", color: "#16a34a", flex: "none" }}>
                              Đã thêm
                            </span>
                          )}
                        </label>
                      ))}
                    </div>
                  ))}
                </div>
              ))}
            </div>
          )}
          {err && <div className="t-xs" style={{ color: "var(--error)" }}>{err}</div>}
          <div className="row gap-8" style={{ justifyContent: "flex-end" }}>
            <button className="btn btn-ghost btn-sm" onClick={onClose} disabled={saving}>Hủy</button>
            <button className="btn btn-primary btn-sm" onClick={submit} disabled={saving || selectedCount === 0}>
              {saving ? "Đang thêm..." : `Thêm vào AI${selectedCount ? ` (${selectedCount})` : ""}`}
            </button>
          </div>
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
   * Tab "Tài liệu AI" — instructor quản lý kho tri thức RAG của khóa học.
   * Props: courseId
   */
  function AiDocsTab({ courseId }) {
    const [sources, setSources] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showAdd, setShowAdd] = useState(false);
    const [showPick, setShowPick] = useState(false);
    const [busyId, setBusyId] = useState(null);
    const [search, setSearch] = useState("");
    const [originFilter, setOriginFilter] = useState("all");
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [previewTarget, setPreviewTarget] = useState(null);

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

    async function confirmDelete() {
      if (!deleteTarget) return;
      setBusyId(deleteTarget.id);
      try { await window.__aiService.deleteAiSource(deleteTarget.id); await load(); }
      catch (e) { console.error(e); }
      finally { setBusyId(null); setDeleteTarget(null); }
    }

    const filteredSources = sources.filter(s => {
      if (originFilter !== "all" && originOf(s) !== originFilter) return false;
      if (search.trim() && !s.sourceName.toLowerCase().includes(search.trim().toLowerCase())) return false;
      return true;
    });
    const lessonCount = sources.filter(s => originOf(s) === "lesson").length;
    const standaloneCount = sources.length - lessonCount;

    return (
      <Section>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 4 }}>
          <h2 className="t-h2">Tài liệu AI</h2>
          <div className="row gap-8">
            <button className="btn btn-ghost btn-sm" style={{ gap: 6 }} onClick={() => setShowPick(true)}>
              <Ic n="folder" size={14} />Chọn từ bài giảng
            </button>
            <button className="btn btn-primary btn-sm" style={{ gap: 6 }} onClick={() => setShowAdd(true)}>
              <Ic n="plus" size={14} />Thêm tài liệu
            </button>
          </div>
        </div>
        <p className="muted" style={{ fontSize: 12.5, marginBottom: 16 }}>
          Tài liệu PDF/DOCX đưa vào đây sẽ được trợ lý AI dùng để trả lời câu hỏi của học viên trong khóa học này.
        </p>

        {!loading && sources.length > 0 && (
          <div className="row gap-12" style={{ marginBottom: 16, flexWrap: "wrap" }}>
            <Search placeholder="Tìm theo tên tài liệu..." value={search} onChange={setSearch} style={{ maxWidth: 280 }} />
            <Tabs
              value={originFilter}
              onChange={setOriginFilter}
              items={[
                { v: "all", label: "Tất cả", count: sources.length },
                { v: "lesson", label: "Từ bài giảng", count: lessonCount },
                { v: "standalone", label: "Tài liệu riêng", count: standaloneCount },
              ]}
            />
          </div>
        )}

        {loading && <div className="muted" style={{ fontSize: 13.5 }}>Đang tải...</div>}

        {!loading && sources.length === 0 && (
          <Empty icon="file" title="Chưa có tài liệu nào" sub="Thêm PDF hoặc DOCX để trợ lý AI có thể trả lời dựa trên nội dung khóa học." />
        )}

        {!loading && sources.length > 0 && filteredSources.length === 0 && (
          <Empty icon="search" title="Không tìm thấy tài liệu phù hợp" sub="Thử từ khóa khác hoặc chọn lại bộ lọc." />
        )}

        {!loading && filteredSources.length > 0 && (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {filteredSources.map(s => {
              const cfg = STATUS_CFG[s.ingestStatus] || STATUS_CFG.PENDING;
              const origin = ORIGIN_CFG[originOf(s)];
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
                      <span className="row gap-4" style={{ padding: "2px 8px", borderRadius: 999, fontSize: 10.5, fontWeight: 600, background: origin.bg, color: origin.color, flex: "none" }}>
                        <Ic n={origin.icon} size={11} />{origin.label}
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
          <AddAiDocModal courseId={courseId} onClose={() => setShowAdd(false)} onAdded={load} />
        )}
        {showPick && (
          <PickLessonResourcesModal courseId={courseId} onClose={() => setShowPick(false)} onAdded={load} />
        )}
        {deleteTarget && (
          <ConfirmDeleteModal source={deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={confirmDelete} busy={busyId === deleteTarget.id} />
        )}
        {previewTarget && (
          <AiDocPreviewModal source={previewTarget} onClose={() => setPreviewTarget(null)} />
        )}
      </Section>
    );
  }

  window.AiDocsTab = AiDocsTab;
})();
