// @ts-nocheck
(function () {
  const { useState, useEffect, useRef } = React;
  const Ic  = window.Icon;
  const { Modal, ModalHead, Select } = window;
  const api = window.httpClient;

  const RES_TYPE = {
    VIDEO: { ic: "video",  bg: "#eaf1ff", fg: "#2563eb" },
    PDF:   { ic: "file",   bg: "#fdecec", fg: "#dc2626" },
    DOC:   { ic: "file",   bg: "#eaf1ff", fg: "#2563eb" },
    SLIDE: { ic: "layers", bg: "#fef5e6", fg: "#d97706" },
    IMAGE: { ic: "image",  bg: "#f0fdf4", fg: "#16a34a" },
    OTHER: { ic: "file",   bg: "#f1f5f9", fg: "#475569" },
  };

  /* Xác định loại tài nguyên thực tế từ mimeType + phần mở rộng file — dùng chung cho cả
     AddResourceModal và EditResourceModal để chặn nhầm file (VD kéo thả PDF vào khung Video,
     hoặc video vào khung Tài liệu): input `accept` chỉ là gợi ý cho hộp thoại chọn file của
     trình duyệt, KHÔNG có tác dụng khi kéo thả (drag-and-drop bỏ qua `accept` hoàn toàn), nên
     bắt buộc phải tự kiểm tra lại loại file thật sau khi đã có File object. */
  function detectResourceType(file: any) {
    const ext = file.name.split(".").pop()?.toLowerCase() || "";
    if (file.type.startsWith("video/") || ["mp4","mov","webm"].includes(ext)) return "VIDEO";
    if (file.type === "application/pdf" || ext === "pdf") return "PDF";
    if (ext === "ppt" || ext === "pptx") return "SLIDE";
    if (ext === "doc" || ext === "docx") return "DOC";
    if (file.type.startsWith("image/") || ["png","jpg","jpeg","gif","webp"].includes(ext)) return "IMAGE";
    return "OTHER";
  }

  /* Upload thẳng lên presigned URL bằng XHR để lấy progress — XHR mặc định KHÔNG có
     timeout, nên nếu kết nối treo (mất mạng, presigned host không phản hồi...) mà
     không có byte nào được gửi tiếp, promise sẽ không bao giờ resolve/reject: UI kẹt ở
     "Đang upload... 0%" mãi mãi, chỉ thoát được bằng cách F5 lại trang. Đồng hồ "stall"
     dưới đây tự reset mỗi khi có tiến độ mới nên không giết oan các upload lớn/chậm
     nhưng vẫn đang chạy — chỉ hủy khi thực sự không còn tiến triển gì. */
  function uploadWithProgress(url: string, file: any, contentType: string, onProgress: (pct: number) => void) {
    const STALL_TIMEOUT_MS = 30000;
    return new Promise((res, rej) => {
      const xhr = new XMLHttpRequest();
      xhr.open("PUT", url);
      xhr.setRequestHeader("Content-Type", contentType);
      let stallTimer: any;
      const resetStallTimer = () => {
        clearTimeout(stallTimer);
        stallTimer = setTimeout(() => {
          xhr.abort();
          rej(new Error("Upload bị treo do mất kết nối — vui lòng thử lại."));
        }, STALL_TIMEOUT_MS);
      };
      resetStallTimer();
      xhr.upload.onprogress = (e: any) => {
        resetStallTimer();
        if (e.lengthComputable) onProgress(Math.round(e.loaded / e.total * 100));
      };
      xhr.onload = () => { clearTimeout(stallTimer); xhr.status < 300 ? res(null) : rej(new Error("Upload thất bại")); };
      xhr.onerror = () => { clearTimeout(stallTimer); rej(new Error("Mất kết nối")); };
      xhr.send(file);
    });
  }

  /* ── UI primitives (local copies) ── */
  function Field({ label, children, full }: any) {
    return (
      <div style={{ gridColumn: full ? "1 / -1" : undefined }}>
        <label className="t-label" style={{ display: "block", marginBottom: 7 }}>{label}</label>
        {children}
      </div>
    );
  }

  function Dropzone({ icon, title, hint, h, file, onClick, onDrop }: any) {
    const [over, setOver] = useState(false);
    return (
      <div
        onDragOver={e => { e.preventDefault(); setOver(true); }}
        onDragLeave={() => setOver(false)}
        onDrop={e => { e.preventDefault(); setOver(false); onDrop(e.dataTransfer.files[0]); }}
        onClick={onClick}
        style={{
          minHeight: h ? h * 8 : 120, borderRadius: 10, border: `2px dashed ${over ? "var(--accent)" : "var(--border)"}`,
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

  function DocViewer({ url, type, height = 300 }: any) {
    if (!url) return null;
    if (type === "PDF") return (
      <div style={{ position: "relative", width: "100%", height }}>
        <iframe src={`${url}#toolbar=0&navpanes=0&scrollbar=0`} style={{ width: "100%", height, border: "none", display: "block" }} title="preview" />
        <div style={{ position: "absolute", top: 0, right: 0, width: 180, height: 50, zIndex: 10, background: "transparent" }}
          onClick={(e) => { e.preventDefault(); e.stopPropagation(); }} />
      </div>
    );
    if (type === "SLIDE" || type === "DOC") {
      const isLocal = url.includes("localhost") || url.includes("127.0.0.1") || url.startsWith("/");
      if (isLocal) {
        return (
          <div style={{ height, background: "var(--surface-2)", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: 20, textAlign: "center", gap: 10 }}>
            <Ic n="book" size={28} style={{ color: "var(--muted)" }} />
            <div style={{ fontSize: 13, fontWeight: 600, color: "var(--fg)" }}>Tài liệu {type === "SLIDE" ? "Slide PowerPoint" : "Word / Docx"}</div>
            <div style={{ fontSize: 12, color: "var(--muted)", maxWidth: 380, lineHeight: 1.5 }}>Trình xem trực tuyến không kết nối được Localhost. Tài liệu sẽ hiển thị trực tiếp khi chạy trên máy chủ chính thức.</div>
          </div>
        );
      }
      return (
        <div style={{ position: "relative", width: "100%", height }}>
          <iframe src={`https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(url)}&wdDownloadButton=False&wdPrint=0`}
            style={{ width: "100%", height, border: "none", display: "block" }} title="preview" />
          <div style={{ position: "absolute", top: 0, right: 0, width: 160, height: 48, zIndex: 10, background: "transparent" }}
            onClick={(e) => { e.preventDefault(); e.stopPropagation(); }} />
          <div style={{ position: "absolute", bottom: 0, right: 0, width: 200, height: 44, zIndex: 10, background: "transparent" }}
            onClick={(e) => { e.preventDefault(); e.stopPropagation(); }} />
        </div>
      );
    }
    return null;
  }

  /* ── AddChapterModal ── */
  function AddChapterModal({ open, onClose, courseId, onAdded }: any) {
    const [title, setTitle]   = useState("");
    const [saving, setSaving] = useState(false);
    const [err, setErr]       = useState(null);
    const close = () => { setTitle(""); setErr(null); onClose(); };
    async function handleAdd() {
      if (!title.trim()) { setErr("Vui lòng nhập tên chương"); return; }
      setSaving(true); setErr(null);
      try { await api.post(`/instructor/courses/${courseId}/chapters`, { title: title.trim() }); close(); onAdded?.(); }
      catch (e: any) { setErr(e?.response?.data?.message || "Thêm chương thất bại"); }
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

  /* ── Form tạo/sửa quiz dùng chung (AddLessonModal + ChangeQuizModal) — y hệt form ở InsAssess ── */
  function defaultQuizMeta() {
    return {
      title: "", quizType: "STATIC", duration: 20, maxAttempts: 3, unlimited: false,
      cooldown: 20, passScore: 70, shuffleQuestions: false, shuffleOptions: false, proctoring: false,
    };
  }

  function toQuizMetadataPayload(m: any) {
    return {
      title: m.title.trim(),
      quizType: m.quizType,
      durationMinutes: Number(m.duration),
      maxAttempts: m.unlimited ? null : Number(m.maxAttempts),
      cooldownMinutes: Number(m.cooldown),
      passScore: Number(m.passScore),
      shuffleQuestions: m.shuffleQuestions,
      shuffleOptions: m.shuffleOptions,
      proctoringEnabled: m.proctoring,
    };
  }

  function QuizMetadataForm({ value, onChange }: any) {
    const set = (k: string, v: any) => onChange({ ...value, [k]: v });
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <input className="input" value={value.title} onChange={e => set("title", e.target.value)} placeholder="Tên đề trắc nghiệm" autoFocus />
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 5 }}>Loại quiz</label>
            <Select value={value.quizType} onChange={(v: string) => set("quizType", v)} options={[
              { v: "STATIC", label: "Tự chọn câu hỏi" },
              { v: "RANDOM_DRAW", label: "Ngẫu nhiên (từ ngân hàng)" },
            ]} />
          </div>
          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 5 }}>Thời gian (phút)</label>
            <input className="input" type="number" min={1} value={value.duration} onChange={e => set("duration", e.target.value)} />
          </div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 5 }}>Số lần làm tối đa</label>
            <input className="input" type="number" min={1} value={value.maxAttempts} disabled={value.unlimited}
              onChange={e => set("maxAttempts", e.target.value)} />
            <label className="row gap-6" style={{ marginTop: 6, cursor: "pointer", alignItems: "center" }}>
              <input type="checkbox" checked={value.unlimited} onChange={e => set("unlimited", e.target.checked)} style={{ width: 15, height: 15 }} />
              <span className="t-xs muted">Không giới hạn số lần</span>
            </label>
          </div>
          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 5 }}>Điểm đạt (%)</label>
            <input className="input" type="number" min={0} max={100} value={value.passScore} onChange={e => set("passScore", e.target.value)} />
          </div>
        </div>
        <div>
          <label className="t-label" style={{ display: "block", marginBottom: 5 }}>Khoảng cách giữa các lần làm (phút)</label>
          <input className="input" type="number" min={0} value={value.cooldown} onChange={e => set("cooldown", e.target.value)} />
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <label className="row gap-8" style={{ padding: "9px 12px", background: "var(--surface-2)", borderRadius: 8, cursor: "pointer" }}>
            <input type="checkbox" checked={value.shuffleQuestions} onChange={e => set("shuffleQuestions", e.target.checked)} style={{ width: 15, height: 15 }} />
            <span className="t-xs">Xáo thứ tự câu hỏi</span>
          </label>
          <label className="row gap-8" style={{ padding: "9px 12px", background: "var(--surface-2)", borderRadius: 8, cursor: "pointer" }}>
            <input type="checkbox" checked={value.shuffleOptions} onChange={e => set("shuffleOptions", e.target.checked)} style={{ width: 15, height: 15 }} />
            <span className="t-xs">Xáo thứ tự đáp án</span>
          </label>
        </div>
        <label className="row gap-8" style={{ padding: "9px 12px", background: "var(--chip-error-bg)", borderRadius: 8, cursor: "pointer" }}>
          <input type="checkbox" checked={value.proctoring} onChange={e => set("proctoring", e.target.checked)} style={{ width: 15, height: 15 }} />
          <span className="t-xs" style={{ color: "var(--chip-error-fg)" }}>Bật chế độ giám sát</span>
        </label>
        <div className="t-xs muted">Đề tạo ở trạng thái Nháp — soạn câu hỏi &amp; xuất bản sau ở tab "Đề trắc nghiệm".</div>
      </div>
    );
  }

  /* ── AddLessonModal ── */
  function AddLessonModal({ open, onClose, courseId, chapterId, onAdded }: any) {
    const [title, setTitle]     = useState("");
    // "LECTURE" = bài giảng thường (video/tài liệu thêm sau qua "Thêm nội dung"), "QUIZ" = đề trắc nghiệm
    const [type, setType]       = useState("LECTURE");
    const [quizMode, setQuizMode] = useState("existing"); // "existing" | "new"
    const [quizzes, setQuizzes] = useState([]);
    const [quizzesLoading, setQuizzesLoading] = useState(false);
    const [quizId, setQuizId]   = useState("");
    const [newQuiz, setNewQuiz] = useState(defaultQuizMeta());
    const [saving, setSaving]   = useState(false);
    const [err, setErr]         = useState(null);

    const close = () => {
      setTitle(""); setType("LECTURE"); setQuizMode("existing"); setQuizId(""); setNewQuiz(defaultQuizMeta());
      setErr(null); onClose();
    };

    useEffect(() => {
      if (!open || type !== "QUIZ" || quizMode !== "existing") return;
      setQuizzesLoading(true);
      api.get(`/courses/${courseId}/quizzes`, { params: { size: 100, sort: "createdAt,desc" } })
        .then(r => setQuizzes(r.data?.content || []))
        .catch(() => setQuizzes([]))
        .finally(() => setQuizzesLoading(false));
    }, [open, type, quizMode, courseId]);

    async function handleAdd() {
      if (!title.trim()) { setErr("Vui lòng nhập tên bài giảng"); return; }
      if (title.trim().length < 3) { setErr("Tên phải có ít nhất 3 ký tự"); return; }
      const payload: any = { title: title.trim(), type: type === "QUIZ" ? "QUIZ" : "TEXT" };
      if (type === "QUIZ") {
        if (quizMode === "existing") {
          if (!quizId) { setErr("Vui lòng chọn 1 đề trắc nghiệm có sẵn"); return; }
          payload.quizId = quizId;
        } else {
          if (!newQuiz.title.trim()) { setErr("Vui lòng nhập tên đề trắc nghiệm"); return; }
          payload.newQuiz = toQuizMetadataPayload(newQuiz);
        }
      }
      setSaving(true); setErr(null);
      try {
        const { data } = await api.post(`/instructor/courses/${courseId}/chapters/${chapterId}/lessons`, payload);
        close(); onAdded?.(data);
      } catch (e: any) { setErr(e?.response?.data?.message || "Thêm bài giảng thất bại"); }
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
            onKeyDown={e => e.key === "Enter" && type !== "QUIZ" && handleAdd()} />
          <label className="t-label" style={{ display: "block", marginBottom: 7, marginTop: 14 }}>Loại bài giảng</label>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            {[
              { id: "LECTURE", label: "Bài giảng", ic: "book" },
              { id: "QUIZ", label: "Đề trắc nghiệm", ic: "clipboard" },
            ].map(t => (
              <div key={t.id} onClick={() => setType(t.id)}
                style={{ padding: "10px 12px", borderRadius: 8, border: `1.5px solid ${type === t.id ? "var(--accent)" : "var(--border)"}`,
                  background: type === t.id ? "var(--surface-2)" : "transparent", cursor: "pointer", display: "flex", alignItems: "center", gap: 8, transition: "all .15s" }}>
                <Ic n={t.ic} size={16} style={{ color: type === t.id ? "var(--accent)" : "var(--text-3)" }} />
                <span style={{ fontSize: 13, fontWeight: type === t.id ? 600 : 400, color: type === t.id ? "var(--text-1)" : "var(--text-2)" }}>{t.label}</span>
              </div>
            ))}
          </div>
          {type === "LECTURE" && (
            <div className="t-xs muted" style={{ marginTop: 8 }}>Sau khi tạo, bạn sẽ được hỏi thêm video/tài liệu ngay — hoặc có thể bỏ qua và thêm sau.</div>
          )}

          {type === "QUIZ" && (
            <div style={{ marginTop: 14, padding: 12, borderRadius: 10, border: "1px solid var(--border)", background: "var(--surface-2)" }}>
              <div className="tabs" style={{ width: "fit-content", marginBottom: 12 }}>
                <button className={quizMode === "existing" ? "on" : ""} onClick={() => setQuizMode("existing")}>Gắn đề có sẵn</button>
                <button className={quizMode === "new" ? "on" : ""} onClick={() => setQuizMode("new")}>Tạo đề mới</button>
              </div>

              {quizMode === "existing" ? (
                quizzesLoading ? (
                  <div className="t-xs muted">Đang tải danh sách đề...</div>
                ) : quizzes.length === 0 ? (
                  <div className="t-xs muted">Khóa học chưa có đề trắc nghiệm nào — chọn "Tạo đề mới" ở trên.</div>
                ) : (
                  <select className="input" value={quizId} onChange={e => setQuizId(e.target.value)}>
                    <option value="">— Chọn đề trắc nghiệm —</option>
                    {quizzes.map((q: any) => (
                      <option key={q.id} value={q.id}>{q.title} ({q.status === "DRAFT" ? "Nháp" : q.status === "PUBLISHED" ? "Đã xuất bản" : "Lưu trữ"})</option>
                    ))}
                  </select>
                )
              ) : (
                <QuizMetadataForm value={newQuiz} onChange={setNewQuiz} />
              )}
            </div>
          )}

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

  /* ── ChangeQuizModal — đổi đề trắc nghiệm gắn với 1 lesson QUIZ đã tồn tại ── */
  function ChangeQuizModal({ open, onClose, courseId, chapterId, lessonId, currentQuizId, onChanged }: any) {
    const [quizMode, setQuizMode] = useState("existing"); // "existing" | "new"
    const [quizzes, setQuizzes]   = useState([]);
    const [quizzesLoading, setQuizzesLoading] = useState(false);
    const [quizId, setQuizId]     = useState("");
    const [newQuiz, setNewQuiz]   = useState(defaultQuizMeta());
    const [resetProgress, setResetProgress] = useState(false);
    const [saving, setSaving]     = useState(false);
    const [err, setErr]           = useState(null);

    const close = () => {
      setQuizMode("existing"); setQuizId(""); setNewQuiz(defaultQuizMeta());
      setResetProgress(false); setErr(null);
      onClose();
    };

    useEffect(() => {
      if (!open || quizMode !== "existing") return;
      setQuizzesLoading(true);
      api.get(`/courses/${courseId}/quizzes`, { params: { size: 100, sort: "createdAt,desc" } })
        .then(r => setQuizzes((r.data?.content || []).filter((q: any) => q.id !== currentQuizId)))
        .catch(() => setQuizzes([]))
        .finally(() => setQuizzesLoading(false));
    }, [open, quizMode, courseId, currentQuizId]);

    async function handleSave() {
      const payload: any = { resetProgressForInProgressStudents: resetProgress };
      if (quizMode === "existing") {
        if (!quizId) { setErr("Vui lòng chọn 1 đề trắc nghiệm khác"); return; }
        payload.quizId = quizId;
      } else {
        if (!newQuiz.title.trim()) { setErr("Vui lòng nhập tên đề trắc nghiệm"); return; }
        payload.newQuiz = toQuizMetadataPayload(newQuiz);
      }
      setSaving(true); setErr(null);
      try {
        await api.put(`/instructor/courses/${courseId}/chapters/${chapterId}/lessons/${lessonId}`, payload);
        close(); onChanged?.();
      } catch (e: any) { setErr(e?.response?.data?.message || "Đổi đề thất bại"); }
      finally { setSaving(false); }
    }

    if (!open) return null;
    return (
      <Modal open={open} onClose={close} max={460}>
        <ModalHead title="Đổi đề trắc nghiệm" icon="rotate_ccw" iconBg="#eaf1ff" iconColor="#2563eb" onClose={close} />
        <div className="modal-body">
          <div className="tabs" style={{ width: "fit-content", marginBottom: 12 }}>
            <button className={quizMode === "existing" ? "on" : ""} onClick={() => setQuizMode("existing")}>Gắn đề có sẵn</button>
            <button className={quizMode === "new" ? "on" : ""} onClick={() => setQuizMode("new")}>Tạo đề mới</button>
          </div>

          {quizMode === "existing" ? (
            quizzesLoading ? (
              <div className="t-xs muted">Đang tải danh sách đề...</div>
            ) : quizzes.length === 0 ? (
              <div className="t-xs muted">Không còn đề nào khác trong khóa học — chọn "Tạo đề mới" ở trên.</div>
            ) : (
              <select className="input" value={quizId} onChange={e => setQuizId(e.target.value)}>
                <option value="">— Chọn đề trắc nghiệm —</option>
                {quizzes.map((q: any) => (
                  <option key={q.id} value={q.id}>{q.title} ({q.status === "DRAFT" ? "Nháp" : q.status === "PUBLISHED" ? "Đã xuất bản" : "Lưu trữ"})</option>
                ))}
              </select>
            )
          ) : (
            <QuizMetadataForm value={newQuiz} onChange={setNewQuiz} />
          )}

          <label className="row gap-10" style={{ marginTop: 14, padding: "10px 12px", background: "var(--surface-2)", borderRadius: 8, cursor: "pointer" }}>
            <input type="checkbox" checked={resetProgress} onChange={e => setResetProgress(e.target.checked)} style={{ width: 16, height: 16 }} />
            <div>
              <div style={{ fontWeight: 600, fontSize: 13 }}>Reset tiến độ học viên đang học</div>
              <div className="t-xs muted">Chỉ áp dụng cho học viên đang học dở khóa — học viên đã hoàn thành cả khóa không bị ảnh hưởng.</div>
            </div>
          </label>

          {err && <div style={{ color: "var(--error)", fontSize: 13, marginTop: 8 }}>{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={close}>Hủy</button>
          <button className="btn btn-primary" disabled={saving} onClick={handleSave}>
            <Ic n="check" size={16} />{saving ? "Đang lưu..." : "Đổi đề"}
          </button>
        </div>
      </Modal>
    );
  }

  /* ── AddResourceModal ── */
  function AddResourceModal({ open, onClose, courseId, lessonId, lessonTitle, onAdded }: any) {
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
    const videoFileRef = useRef<any>();
    const docFileRef   = useRef<any>();
    const nameRef      = useRef<any>();

    const close = () => {
      setTab("video"); setVideoMode("upload");
      setVideoFile(null); setVideoName(""); setVideoUrl("");
      setDocFile(null); setDocName("");
      setSaving(false); setProgress(0); setErr(null); onClose();
    };

    function onVideoFilePick(f: any) {
      if (!f) return;
      // input `accept` chỉ lọc hộp thoại chọn file, không chặn được kéo-thả — tự kiểm tra lại
      // loại file thật để không nhận nhầm PDF/DOCX... vào tab Video.
      if (detectResourceType(f) !== "VIDEO") { setErr("Vui lòng chọn file video (MP4, MOV, WebM)"); return; }
      if (f.size > 2 * 1024 * 1024 * 1024) { setErr("File vượt quá 2GB"); return; }
      setVideoFile(f);
      setVideoName(prev => prev || f.name.replace(/\.[^.]+$/, ""));
      setErr(null);
      setTimeout(() => { nameRef.current?.focus(); nameRef.current?.select(); }, 50);
    }
    function onDocFilePick(f: any) {
      if (!f) return;
      // Tương tự onVideoFilePick — chặn video bị kéo thả nhầm vào tab Tài liệu.
      if (detectResourceType(f) === "VIDEO") { setErr("Vui lòng chọn file tài liệu (PDF, DOCX, PPTX, ảnh...), không phải video"); return; }
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
            await uploadWithProgress(p.presignedUrl, videoFile, videoFile.type || "video/mp4", setProgress);
            await api.post(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/confirm-upload`,
              { s3Key: p.s3Key, originalFilename: videoFile.name, resourceType: "VIDEO", displayName });
          } else {
            await api.post(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/confirm-upload`,
              { externalUrl: videoUrl.trim(), resourceType: "VIDEO", displayName: videoName.trim() || "Video" });
          }
        } else {
          if (!docFile) { setErr("Vui lòng chọn file tài liệu"); setSaving(false); return; }
          if (!docName.trim()) { setErr("Vui lòng nhập tên tài liệu"); setSaving(false); return; }
          const resourceType = detectResourceType(docFile);
          const { data: p } = await api.post(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/presign-upload`,
            { originalFilename: docFile.name, mimeType: docFile.type || "application/octet-stream", fileSizeBytes: docFile.size, resourceType, displayName: docName.trim() });
          await uploadWithProgress(p.presignedUrl, docFile, docFile.type || "application/octet-stream", setProgress);
          await api.post(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/confirm-upload`,
            { s3Key: p.s3Key, originalFilename: docFile.name, resourceType, displayName: docName.trim() });
        }
        close(); onAdded?.();
      } catch (e: any) { setErr(e?.response?.data?.message || e?.message || "Thao tác thất bại"); }
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

  /* ── EditResourceModal ── */
  function EditResourceModal({ s, rm, isVideo, isImage, isYT, ytId, onClose, onSave, courseId, onReplaced }: any) {
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
    const fileRef = useRef<any>();

    const canEmbed = s.externalUrl && s.externalUrl.match(/\.(mp4|webm|ogg)(\?.*)?$/i);

    useEffect(() => {
      if (s.externalUrl || !s.resourceId) return;
      setViewLoading(true);
      api.get(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/${s.resourceId}/download-url`)
        .then(r => setViewUrl(r.data?.url || null))
        .catch(() => {})
        .finally(() => setViewLoading(false));
    }, [s.resourceId]);

    useEffect(() => {
      if (!newFile) { setNewFileUrl(null); return; }
      const url = URL.createObjectURL(newFile);
      setNewFileUrl(url);
      return () => URL.revokeObjectURL(url);
    }, [newFile]);

    function onReplacePick(f: any) {
      if (!f) return;
      const isVid = detectResourceType(f) === "VIDEO";
      // input `accept` chỉ lọc hộp thoại chọn file, không chặn được kéo-thả — tự kiểm tra lại
      // loại file thật khớp với loại tài nguyên đang thay thế (video chỉ nhận video, tài liệu
      // không nhận video và ngược lại).
      if (isVideo && !isVid) { setErr("Vui lòng chọn file video (MP4, MOV, WebM)"); return; }
      if (!isVideo && isVid) { setErr("Vui lòng chọn file tài liệu (PDF, DOCX, PPTX, ảnh...), không phải video"); return; }
      if (f.size > (isVid ? 2 * 1024 * 1024 * 1024 : 200 * 1024 * 1024)) { setErr(`File vượt quá ${isVid ? "2GB" : "200MB"}`); return; }
      setNewFile(f); setErr(null);
    }

    function fmtBytes(b: number) {
      if (!b) return "";
      return b < 1024 * 1024 ? (b / 1024).toFixed(1) + " KB" : (b / (1024 * 1024)).toFixed(1) + " MB";
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
            <span style={{ fontSize: 12, fontWeight: 600, color: "var(--chip-info-fg)" }}>Xem trước file mới · {detectedType}</span>
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
            await api.post(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/confirm-upload`,
              { externalUrl: newUrl.trim(), resourceType: s.resourceType, displayName: name.trim() });
            await api.delete(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/${s.resourceId}`);
          } else {
            const detectedType = detectResourceType(newFile);
            const { data: p } = await api.post(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/presign-upload`,
              { originalFilename: newFile.name, mimeType: newFile.type || "application/octet-stream", fileSizeBytes: newFile.size, resourceType: detectedType, displayName: name.trim() });
            await uploadWithProgress(p.presignedUrl, newFile, newFile.type || "application/octet-stream", setProgress);
            await api.post(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/confirm-upload`,
              { s3Key: p.s3Key, originalFilename: newFile.name, resourceType: detectedType, displayName: name.trim() });
            await api.delete(`/instructor/courses/${courseId}/lessons/${s.lessonId}/resources/${s.resourceId}`);
          }
          onReplaced?.();
        } else {
          onSave(name.trim());
        }
      } catch (e: any) { setErr(e?.response?.data?.message || e?.message || "Thao tác thất bại"); }
      finally { setSaving(false); }
    }

    return (
      <Modal open onClose={onClose} max={580}>
        <ModalHead title="Chỉnh sửa tài liệu" icon="edit" iconBg={rm.bg} iconColor={rm.fg} onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* Preview hiện tại */}
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
                {viewUrl && <a href={viewUrl} target="_blank" rel="noreferrer" className="btn btn-ghost btn-sm"><Ic n="external_link" size={14} />Mở file</a>}
              </div>
            )}
          </div>

          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên hiển thị</label>
            <input className="input" value={name} onChange={e => setName(e.target.value)} autoFocus
              onKeyDown={e => { if (e.key === "Enter" && !replaceOpen) handleSave(); }} />
          </div>

          <button className="btn btn-ghost btn-sm" style={{ alignSelf: "flex-start", gap: 6 }}
            onClick={() => { setReplaceOpen(v => !v); setErr(null); setNewFile(null); }}>
            <Ic n={replaceOpen ? "chevron_down" : "chevron_right"} size={14} />
            {replaceOpen ? "Ẩn thay thế nội dung" : "Thay thế nội dung"}
          </button>

          {replaceOpen && (
            <div style={{ padding: 14, borderRadius: 10, border: "1px solid var(--border)", display: "flex", flexDirection: "column", gap: 12, background: "var(--surface-2)" }}>
              {isVideo && (
                <div className="tabs" style={{ width: "fit-content" }}>
                  <button className={!urlMode ? "on" : ""} onClick={() => setUrlMode(false)}>Tải file lên</button>
                  <button className={urlMode ? "on" : ""} onClick={() => { setUrlMode(true); setNewFile(null); }}>Nhúng URL</button>
                </div>
              )}
              {isVideo && urlMode ? (
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

  /* ── ResourcePreviewModal ── */
  function ResourcePreviewModal({ resource, courseId, lessonId, onClose }: any) {
    const { resourceId, title, resourceType, externalUrl } = resource;
    const rm      = RES_TYPE[resourceType] || RES_TYPE.OTHER;
    const isYT    = !!(externalUrl && externalUrl.match(/youtube\.com|youtu\.be/));
    const ytId    = isYT ? (externalUrl.match(/(?:v=|youtu\.be\/)([^&?/]+)/)?.[1] || null) : null;
    const isVideo = resourceType === "VIDEO";
    const isImage = resourceType === "IMAGE";
    const isPdf   = resourceType === "PDF";
    const isDoc   = resourceType === "DOC";
    const isSlide = resourceType === "SLIDE";

    const isLandscape = isVideo || isYT || isSlide;
    const isPortrait  = isPdf || isDoc;
    const modalMax    = isLandscape ? 960 : isPortrait ? 720 : 680;
    const modalHeight = isLandscape ? "calc(100vh - 40px)" : "calc(100vh - 48px)";
    const iframeH     = isLandscape || isPortrait ? "calc(100vh - 130px)" : 440;

    const [viewUrl, setViewUrl]   = useState(externalUrl || null);
    const [loading, setLoading]   = useState(false);

    useEffect(() => {
      if (externalUrl || !resourceId) return;
      setLoading(true);
      api.get(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/${resourceId}/view-url`)
        .then(r => setViewUrl(r.data?.url || null))
        .catch(() => {})
        .finally(() => setLoading(false));
    }, [resourceId]);

    const loadingNode = <div style={{ padding: "60px 0", textAlign: "center", color: "var(--text-3)", fontSize: 13 }}>Đang tải xem trước...</div>;
    const errorNode   = <div style={{ padding: "60px 0", textAlign: "center", color: "var(--text-3)", fontSize: 13 }}>Không thể tải xem trước</div>;

    return (
      <Modal open onClose={onClose} max={modalMax} maxHeight={modalHeight}>
        <ModalHead title={title || "Xem tài liệu"} icon={rm.ic} iconBg={rm.bg} iconColor={rm.fg} onClose={onClose} />
        <div className="modal-body" style={{ padding: 0, overflow: "hidden" }}>
          {(isYT && ytId) ? (
            <div style={{ position: "relative", paddingBottom: "56.25%", height: 0, background: "#000" }}>
              <iframe src={`https://www.youtube.com/embed/${ytId}`}
                style={{ position: "absolute", inset: 0, width: "100%", height: "100%", border: "none" }} allowFullScreen />
            </div>
          ) : isVideo ? (
            viewUrl ? <video src={viewUrl} controls style={{ width: "100%", height: iframeH, display: "block", background: "#000", objectFit: "contain" }} />
                    : loading ? loadingNode : errorNode
          ) : isImage ? (
            viewUrl ? <img src={viewUrl} style={{ width: "100%", maxHeight: "70vh", objectFit: "contain", display: "block", background: "var(--surface-2)" }} />
                    : loading ? loadingNode : errorNode
          ) : (isSlide || isPdf || isDoc) ? (
            loading ? loadingNode : viewUrl
              ? <DocViewer url={viewUrl} type={resourceType} height={iframeH} />
              : errorNode
          ) : (
            <div className="row gap-12" style={{ padding: "28px 20px" }}>
              <div className="stat-ic" style={{ width: 48, height: 48, borderRadius: 12, background: rm.bg, color: rm.fg, flex: "none" }}>
                <Ic n={rm.ic} size={22} />
              </div>
              <div className="grow" style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: 15 }} className="truncate">{title}</div>
                <div className="muted t-xs" style={{ marginTop: 3 }}>{(resourceType || "").toUpperCase()} · Không hỗ trợ xem trực tiếp</div>
              </div>
              {viewUrl && <a href={viewUrl} target="_blank" rel="noreferrer" className="btn btn-ghost btn-sm"><Ic n="external_link" size={14} />Mở file</a>}
            </div>
          )}
        </div>
      </Modal>
    );
  }

  Object.assign(window, { AddChapterModal, AddLessonModal, ChangeQuizModal, AddResourceModal, EditResourceModal, ResourcePreviewModal });
})();
