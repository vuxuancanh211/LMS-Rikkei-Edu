// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Quản lý Khóa học (+ popup tạo khóa học)
   ============================================================ */
(function () {
  const { useState, useEffect, useRef } = React;
  const Ic = window.Icon;
  const { Modal, ModalHead, Empty, Search, Select, StatCard, Pager } = window;
  const api = window.httpClient;

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

  function Field({ label, children, hint, full }) {
    return (
      <div style={{ gridColumn: full ? "1 / -1" : "auto" }}>
        <label className="t-label" style={{ display: "block", marginBottom: 7 }}>{label}</label>
        {children}
        {hint && <div className="t-xs muted" style={{ marginTop: 6 }}>{hint}</div>}
      </div>
    );
  }

  /* ── Modal tạo khóa học ─────────────────────────────────────────────── */
  function CreateCourseModal({ open, onClose, onCreated }) {
    const [step, setStep]                     = useState(1);
    const [title, setTitle]                   = useState("");
    const [desc, setDesc]                     = useState("");
    const [level, setLevel]                   = useState(LEVELS[1].value);
    const [categoryId, setCategoryId]         = useState(null);
    const [duration, setDuration]             = useState("");
    const [categories, setCategories]         = useState([]);
    const [thumbFile, setThumbFile]           = useState(null);
    const [thumbPreview, setThumbPreview]     = useState(null);
    const [thumbUploading, setThumbUploading] = useState(false);
    const [thumbProgress, setThumbProgress]   = useState(0);
    const [thumbUrl, setThumbUrl]             = useState(null);
    const [saving, setSaving]                 = useState(false);
    const [err, setErr]                       = useState(null);
    const fileRef = useRef();

    useEffect(() => {
      if (!open || categories.length > 0) return;
      api.get("/instructor/courses/categories")
        .then(r => setCategories(r.data || []))
        .catch(() => {});
    }, [open]);

    const reset = () => {
      setStep(1); setTitle(""); setDesc(""); setLevel(LEVELS[1].value);
      setCategoryId(null); setDuration(""); setErr(null);
      setThumbFile(null); setThumbPreview(null); setThumbUrl(null); setThumbProgress(0);
    };
    const close = () => { reset(); onClose(); };

    function onThumbPick(f) {
      if (!f) return;
      if (!f.type.startsWith("image/")) { setErr("Chỉ chấp nhận file ảnh PNG/JPG"); return; }
      if (f.size > 4 * 1024 * 1024)    { setErr("Ảnh bìa vượt quá 4MB"); return; }
      setThumbFile(f); setThumbPreview(URL.createObjectURL(f)); setThumbUrl(null); setErr(null);
    }

    async function uploadThumb() {
      if (!thumbFile || thumbUrl) return thumbUrl;
      setThumbUploading(true); setThumbProgress(0);
      try {
        const { data } = await api.post(`/instructor/courses/presign-thumbnail?mimeType=${encodeURIComponent(thumbFile.type)}`);
        await new Promise((resolve, reject) => {
          const xhr = new XMLHttpRequest();
          xhr.open("PUT", data.uploadUrl);
          xhr.setRequestHeader("Content-Type", thumbFile.type);
          xhr.upload.onprogress = e => { if (e.lengthComputable) setThumbProgress(Math.round(e.loaded / e.total * 100)); };
          xhr.onload  = () => xhr.status < 300 ? resolve() : reject(new Error("Upload thất bại: " + xhr.status));
          xhr.onerror = () => reject(new Error("Mất kết nối khi upload"));
          xhr.send(thumbFile);
        });
        setThumbUrl(data.viewUrl);
        return data.viewUrl;
      } finally { setThumbUploading(false); }
    }

    async function handleCreate() {
      if (!title.trim())           { setErr("Vui lòng nhập tên khóa học"); return; }
      if (title.trim().length < 5) { setErr("Tên khóa học phải có ít nhất 5 ký tự"); return; }
      setSaving(true); setErr(null);
      try {
        let finalThumb = thumbUrl;
        if (thumbFile && !thumbUrl) finalThumb = await uploadThumb();
        const { data } = await api.post("/instructor/courses", {
          title: title.trim(),
          description: desc.trim() || null,
          level,
          categoryId: categoryId || null,
          thumbnailUrl: finalThumb || null,
        });
        reset(); onCreated && onCreated(data.slug);
      } catch (e) {
        setErr(e?.response?.data?.message || e?.message || "Tạo khóa học thất bại");
      } finally { setSaving(false); }
    }

    const catOptions = categories.map(c => ({ v: c.id, label: c.name }));

    if (!open) return null;
    return (
      <Modal open={open} onClose={close} max={620}>
        <ModalHead title="Tạo khóa học mới"
          sub={`Bước ${step}/2 · ${step === 1 ? "Thông tin cơ bản" : "Ảnh bìa & chi tiết"}`}
          icon="book" iconBg="#eaf1ff" iconColor="#2563eb" onClose={close} />
        {/* Progress bar */}
        <div style={{ padding: "0 24px" }}>
          <div className="row gap-8" style={{ marginTop: 4 }}>
            {[1, 2].map(n => (
              <div key={n} style={{ flex: 1, height: 4, borderRadius: 999, background: n <= step ? "var(--accent)" : "var(--border)", transition: "background .2s" }} />
            ))}
          </div>
        </div>

        <div className="modal-body" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
          {step === 1 ? (<>
            <Field label="Tên khóa học" full>
              <input className="input" value={title} onChange={e => setTitle(e.target.value)}
                placeholder="VD: Lập trình ReactJS Nâng cao & Redux" autoFocus />
            </Field>
            <Field label="Danh mục">
              {catOptions.length > 0
                ? <Select value={categoryId} onChange={setCategoryId} options={[{ v: null, label: "— Chưa chọn —" }, ...catOptions]} />
                : <input className="input" disabled value="Đang tải..." />}
            </Field>
            <Field label="Cấp độ">
              <Select value={level} onChange={setLevel} options={LEVELS.map(l => ({ v: l.value, label: l.label }))} />
            </Field>
            <Field label="Mô tả khóa học" full>
              <textarea className="input" style={{ height: 96, padding: 12, resize: "none" }} value={desc}
                onChange={e => setDesc(e.target.value)}
                placeholder="Giới thiệu ngắn gọn nội dung, đối tượng và mục tiêu của khóa học..." />
            </Field>
            {err && <div style={{ gridColumn: "1/-1", color: "var(--error)", fontSize: 13 }}>{err}</div>}
          </>) : (<>
            {/* Thumbnail dropzone */}
            <input ref={fileRef} type="file" accept="image/png,image/jpeg" style={{ display: "none" }}
              onChange={e => onThumbPick(e.target.files?.[0])} />
            <div style={{ gridColumn: "1/-1", border: "2px dashed var(--border-strong)", borderRadius: 12, overflow: "hidden", cursor: "pointer", background: "var(--surface-2)", aspectRatio: "16/7", position: "relative" }}
              onClick={() => fileRef.current?.click()}
              onDragOver={e => { e.preventDefault(); e.currentTarget.style.borderColor = "var(--accent)"; }}
              onDragLeave={e => { e.currentTarget.style.borderColor = "var(--border-strong)"; }}
              onDrop={e => { e.preventDefault(); e.currentTarget.style.borderColor = "var(--border-strong)"; onThumbPick(e.dataTransfer.files?.[0]); }}>
              {thumbPreview
                ? <img src={thumbPreview} style={{ width: "100%", height: "100%", objectFit: "cover", display: "block" }} />
                : <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: "100%", color: "var(--text-3)", padding: 20 }}>
                    <div className="stat-ic" style={{ width: 46, height: 46, borderRadius: 12, background: "#fff", color: "var(--accent)", marginBottom: 10 }}>
                      <Ic n="upload" size={22} />
                    </div>
                    <div style={{ fontWeight: 600, fontSize: 14, color: "var(--text)" }}>Tải ảnh bìa khóa học</div>
                    <div className="t-xs" style={{ marginTop: 4 }}>PNG, JPG tối đa 4MB · tỉ lệ 16:9</div>
                  </div>}
              {thumbPreview && (
                <div style={{ position: "absolute", bottom: 8, right: 8, background: "rgba(0,0,0,.6)", color: "#fff", fontSize: 12, padding: "4px 10px", borderRadius: 6 }}>
                  Nhấn để đổi ảnh
                </div>
              )}
            </div>

            {thumbUploading && (
              <div style={{ gridColumn: "1/-1" }}>
                <div style={{ height: 5, borderRadius: 999, background: "var(--border)", overflow: "hidden" }}>
                  <div style={{ width: thumbProgress + "%", height: "100%", background: "var(--accent)", transition: "width .2s" }} />
                </div>
                <div className="t-xs muted" style={{ marginTop: 4, textAlign: "center" }}>Đang upload ảnh bìa... {thumbProgress}%</div>
              </div>
            )}

            <Field label="Thời lượng dự kiến">
              <div style={{ position: "relative" }}>
                <input
                  className="input"
                  style={{ paddingRight: 44 }}
                  value={duration}
                  onChange={e => setDuration(e.target.value.replace(/[^0-9]/g, ""))}
                  placeholder="VD: 32"
                  inputMode="numeric"
                />
                <span style={{ position: "absolute", right: 12, top: "50%", transform: "translateY(-50%)", color: "var(--text-3)", fontSize: 14, pointerEvents: "none", userSelect: "none" }}>giờ</span>
              </div>
            </Field>
            {err && <div style={{ gridColumn: "1/-1", color: "var(--error)", fontSize: 13 }}>{err}</div>}
          </>)}
        </div>

        <div className="modal-foot">
          {step === 2 && (
            <button className="btn btn-ghost" onClick={() => setStep(1)} style={{ marginRight: "auto" }}>
              <Ic n="chevron-left" size={16} />Quay lại
            </button>
          )}
          <button className="btn btn-ghost" onClick={close}>Hủy</button>
          {step === 1
            ? <button className="btn btn-primary" onClick={() => {
                if (!title.trim())           { setErr("Vui lòng nhập tên khóa học"); return; }
                if (title.trim().length < 5) { setErr("Tên khóa học phải có ít nhất 5 ký tự"); return; }
                setErr(null); setStep(2);
              }}>Tiếp theo<Ic n="chevron-right" size={16} /></button>
            : <button className="btn btn-success" disabled={saving || thumbUploading} onClick={handleCreate}>
                <Ic n="check" size={16} />
                {saving ? "Đang tạo..." : thumbUploading ? "Đang upload..." : "Tạo & vào chỉnh sửa"}
              </button>}
        </div>
      </Modal>
    );
  }

  /* ── Trang danh sách khóa học ───────────────────────────────────────── */
  function InsCourses({ nav }) {
    const size = 20;
    const [q, setQ]                     = useState("");
    const [debouncedQ, setDebouncedQ]   = useState("");
    const [filter, setFilter]           = useState("all");
    const [page, setPage]               = useState(1);
    const [courses, setCourses]         = useState([]);
    const [totalPages, setTotalPages]   = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading]         = useState(true);
    const [create, setCreate]           = useState(false);

    useEffect(() => {
      const t = setTimeout(() => { setDebouncedQ(q); setPage(1); }, 350);
      return () => clearTimeout(t);
    }, [q]);

    function loadCourses() {
      setLoading(true);
      const params = { page: page - 1, size, sort: "createdAt,desc" };
      if (debouncedQ) params.keyword = debouncedQ;
      api.get("/instructor/courses", { params })
        .then(r => {
          setCourses(r.data.content || []);
          setTotalPages(r.data.totalPages || 1);
          setTotalElements(r.data.totalElements || 0);
        })
        .catch(() => { setCourses([]); setTotalPages(1); setTotalElements(0); })
        .finally(() => setLoading(false));
    }
    useEffect(() => { loadCourses(); }, [page, debouncedQ]);

    const list = courses.filter(c => {
      if (filter === "pub"   && c.status !== "PUBLISHED") return false;
      if (filter === "pend"  && c.status !== "PENDING" && c.status !== "PENDING_UPDATE") return false;
      if (filter === "draft" && c.status !== "DRAFT") return false;
      return true;
    });

    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div>
            <h1 className="t-h1">Quản lý Khóa học</h1>
            <p className="muted">Tạo, chỉnh sửa và theo dõi các khóa học của bạn.</p>
          </div>
          <button className="btn btn-primary" onClick={() => setCreate(true)}><Ic n="plus" size={17} />Tạo khóa học</button>
        </div>

        <window.CreateCourseModal open={create} onClose={() => setCreate(false)} onCreated={(slug) => nav("courseDetail", { slug })} />

        <div className="toolbar">
          <Search placeholder="Tìm khóa học..." value={q} onChange={setQ} />
          <Select value={filter} onChange={setFilter}
            options={[
              { v: "all",   label: "Tất cả trạng thái" },
              { v: "pub",   label: "Đã xuất bản" },
              { v: "pend",  label: "Chờ duyệt" },
              { v: "draft", label: "Bản nháp" },
            ]}
            style={{ width: 180, flex: "none" }} />
        </div>

        {loading ? (
          <div className="muted" style={{ textAlign: "center", padding: 60 }}>Đang tải...</div>
        ) : list.length === 0 ? (
          <Empty icon="book" title="Chưa có khóa học nào" sub={debouncedQ ? "Không tìm thấy kết quả phù hợp." : "Nhấn 'Tạo khóa học' để bắt đầu."} />
        ) : (
          <>
            <div className="grid grid-cards grid-cards-fixed">
              {list.map(c => {
                const sc = STATUS_COLOR[c.status] || {};
                return (
                  <div key={c.id} className="card course-card fade-in">
                    <div className="course-thumb" style={{ backgroundImage: c.thumbnailUrl ? `url(${c.thumbnailUrl})` : "linear-gradient(135deg,#1e3a5f,#2563eb)" }}>
                      <span className="tl">
                        <span className="chip" style={{ background: "rgba(15,23,42,.72)", color: "#fff" }}>{c.category?.name || "—"}</span>
                      </span>
                      <span className="tr">
                        <span className="chip" style={{ background: sc.bg, color: sc.color, fontWeight: 600 }}>{STATUS_LABEL[c.status] || c.status}</span>
                      </span>
                    </div>
                    <div className="course-body">
                      <h3 className="clamp-2">{c.title}</h3>
                      <div className="row gap-16 wrap" style={{ marginBottom: 8 }}>
                        {c.level && <span className="meta-row"><Ic n="layers" size={15} /> {c.level}</span>}
                      </div>
                      <div className="row gap-10" style={{ marginTop: "auto", paddingTop: 6 }}>
                        <button className="btn btn-ghost btn-sm grow" onClick={() => {
                          window.__previewCourse = { courseId: c.id, role: "instructor" };
                          nav("preview");
                        }}><Ic n="eye" size={15} />Xem</button>
                        <button className="btn btn-primary btn-sm grow" onClick={() => nav("courseDetail", { slug: c.slug })}>
                          <Ic n="edit" size={15} />Sửa</button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
            {totalPages > 1 && (
              <div className="between wrap pagebar" style={{ gap: 12, marginTop: 16 }}>
                <span className="t-sm muted">Trang <b style={{ color: "var(--text)" }}>{page}</b> / <b style={{ color: "var(--text)" }}>{totalPages}</b> · tổng <b style={{ color: "var(--text)" }}>{totalElements}</b> khóa học</span>
                <Pager page={page} pages={totalPages} onPage={setPage} />
              </div>
            )}
          </>
        )}
      </div>
    );
  }

  Object.assign(window, { InsCourses, CreateCourseModal });
})();
