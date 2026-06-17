// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Chi tiết Khóa học (+ popup thêm video / tài liệu)
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const myCourses = D.courses.filter(c => ["Nguyễn Văn An", "Trần Thị Bình", "Lê Văn Cường", "Phạm Thị Dung"].includes(c.instructor)).slice(0, 9);

  const CATS = ["Frontend", "Backend", "DevOps", "Database", "AI/ML", "Mobile", "Testing", "Design", "Security", "Quy trình"];
  const LEVELS = ["Cơ bản", "Trung cấp", "Nâng cao"];

  function Field({ label, children, hint, full }) {
    return (
      <div style={{ gridColumn: full ? "1 / -1" : "auto" }}>
        <label className="t-label" style={{ display: "block", marginBottom: 7 }}>{label}</label>
        {children}
        {hint && <div className="t-xs muted" style={{ marginTop: 6 }}>{hint}</div>}
      </div>
    );
  }
  function Dropzone({ icon, title, hint, h }) {
    return (
      <div style={{ border: "2px dashed var(--border-strong)", borderRadius: 12, padding: h || 26, textAlign: "center", color: "var(--text-3)", cursor: "pointer", background: "var(--surface-2)", transition: ".15s" }}
        onMouseEnter={e => { e.currentTarget.style.borderColor = "var(--accent)"; e.currentTarget.style.background = "var(--accent-soft)"; }}
        onMouseLeave={e => { e.currentTarget.style.borderColor = "var(--border-strong)"; e.currentTarget.style.background = "var(--surface-2)"; }}>
        <div className="stat-ic" style={{ width: 46, height: 46, borderRadius: 12, background: "#fff", color: "var(--accent)", margin: "0 auto 10px" }}><Ic n={icon} size={22} /></div>
        <div style={{ fontWeight: 600, fontSize: 14, color: "var(--text)" }}>{title}</div>
        <div className="t-xs" style={{ marginTop: 4 }}>{hint}</div>
      </div>
    );
  }

  /* ---------------- Add Video / Add Document modals ---------------- */
  function AddVideoModal({ open, onClose, onAdd }) {
    const [mode, setMode] = useState("upload");
    return (
      <Modal open={open} onClose={onClose} max={560}>
        <ModalHead title="Thêm bài giảng (Video)" sub="Tải video hoặc nhúng từ đường dẫn" icon="video" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <Field label="Tiêu đề bài giảng"><input className="input" id="vtitle" placeholder="VD: Giới thiệu về React Hooks" /></Field>
          <div className="tabs" style={{ width: "fit-content" }}>
            <button className={mode === "upload" ? "on" : ""} onClick={() => setMode("upload")}>Tải lên</button>
            <button className={mode === "url" ? "on" : ""} onClick={() => setMode("url")}>Nhúng URL</button>
          </div>
          {mode === "upload"
            ? <Dropzone icon="video" title="Kéo thả file video vào đây" hint="MP4, MOV, WebM · tối đa 2GB · Streaming bảo mật" h={32} />
            : <Field label="Đường dẫn video"><input className="input" placeholder="https://..." /></Field>}
          <div className="grid grid-2" style={{ gap: 14 }}>
            <Field label="Thời lượng"><input className="input" placeholder="VD: 12:40" /></Field>
            <Field label="Chương"><Select value="1" onChange={()=>{}} options={[{v:"1",label:"Session 01"},{v:"2",label:"Session 02"},{v:"3",label:"Session 03"}]} /></Field>
          </div>
          <label className="row gap-10" style={{ padding: "11px 14px", background: "var(--surface-2)", borderRadius: 11, cursor: "pointer" }}>
            <input type="checkbox" style={{ width: 18, height: 18 }} defaultChecked /><span style={{ fontSize: 13.5, fontWeight: 500 }}>Cho phép xem thử (preview miễn phí)</span>
          </label>
        </div>
        <div className="modal-foot"><button className="btn btn-ghost" onClick={onClose}>Hủy</button><button className="btn btn-primary" onClick={() => { const t = (document.getElementById("vtitle") || {}).value; onAdd("video", t || "Bài giảng mới"); onClose(); }}><Ic n="plus" size={16} />Thêm bài giảng</button></div>
      </Modal>
    );
  }
  function AddDocModal({ open, onClose, onAdd }) {
    return (
      <Modal open={open} onClose={onClose} max={520}>
        <ModalHead title="Thêm tài liệu" sub="PDF, Slide, Word đính kèm cho bài giảng" icon="file" iconBg="#fef5e6" iconColor="#d97706" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <Field label="Tên tài liệu"><input className="input" id="dtitle" placeholder="VD: Giáo trình chương 1" /></Field>
          <div className="grid grid-2" style={{ gap: 14 }}>
            <Field label="Loại tài liệu"><Select value="pdf" onChange={()=>{}} options={[{v:"pdf",label:"PDF"},{v:"slide",label:"Slide (PPTX)"},{v:"doc",label:"Word (DOCX)"}]} /></Field>
            <Field label="Chương"><Select value="1" onChange={()=>{}} options={[{v:"1",label:"Session 01"},{v:"2",label:"Session 02"}]} /></Field>
          </div>
          <Dropzone icon="upload" title="Kéo thả tài liệu vào đây" hint="PDF, PPTX, DOCX · tối đa 50MB" h={32} />
        </div>
        <div className="modal-foot"><button className="btn btn-ghost" onClick={onClose}>Hủy</button><button className="btn btn-primary" onClick={() => { const t = (document.getElementById("dtitle") || {}).value; onAdd("doc", t || "Tài liệu mới"); onClose(); }}><Ic n="plus" size={16} />Thêm tài liệu</button></div>
      </Modal>
    );
  }


  /* ---------------- Course Detail (curriculum builder) ---------------- */
  function InsCourseDetail({ nav }) {
    const [tab, setTab] = useState("content");
    const [open, setOpen] = useState(0);
    const [vid, setVid] = useState(false);
    const [doc, setDoc] = useState(false);
    const [targetCh, setTargetCh] = useState(0);
    // editable curriculum state
    const [chapters, setChapters] = useState([
      { id: 1, name: "Session 01 — Tổng quan & Cài đặt", items: [
        { t: "video", title: "Giới thiệu khóa học & lộ trình", dur: "08:24" },
        { t: "video", title: "Cài đặt môi trường phát triển", dur: "12:40" },
        { t: "doc", title: "Giáo trình chi tiết - Chương 1.pdf", kind: "pdf" },
      ]},
      { id: 2, name: "Session 02 — Components & Props", items: [
        { t: "video", title: "Functional Components", dur: "15:10" },
        { t: "video", title: "Props & State cơ bản", dur: "18:05" },
        { t: "doc", title: "Slide bài giảng buổi 2.pptx", kind: "slide" },
        { t: "quiz", title: "Quiz chương 2: Components", q: 10 },
      ]},
      { id: 3, name: "Session 03 — Hooks nâng cao", items: [
        { t: "video", title: "useState & useEffect", dur: "20:30" },
        { t: "doc", title: "Bài tập thực hành.docx", kind: "doc" },
      ]},
    ]);
    const counts = chapters.reduce((a, c) => { c.items.forEach(i => { a[i.t] = (a[i.t] || 0) + 1; }); return a; }, {});
    const addChapter = () => setChapters(cs => [...cs, { id: Date.now(), name: "Session " + String(cs.length + 1).padStart(2, "0") + " — Chương mới", items: [] }]);
    const addItem = (type, title) => setChapters(cs => cs.map((c, i) => i === targetCh ? { ...c, items: [...c.items, type === "video" ? { t: "video", title, dur: "00:00" } : { t: "doc", title, kind: "pdf" }] } : c));
    const removeItem = (ci, ii) => setChapters(cs => cs.map((c, i) => i === ci ? { ...c, items: c.items.filter((_, j) => j !== ii) } : c));

    const ITEM_META = {
      video: { ic: "play", bg: "var(--chip-info-bg)", fg: "var(--accent)", lbl: "Video" },
      doc: { ic: "file", bg: "#fef5e6", fg: "#d97706", lbl: "Tài liệu" },
      quiz: { ic: "clipboard", bg: "#f3edff", fg: "#7c3aed", lbl: "Trắc nghiệm" },
    };

    return (
      <div className="page fade-in">
        <div className="row gap-10" style={{ marginBottom: 16, cursor: "pointer", color: "var(--text-2)" }} onClick={() => nav("courses")}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách khóa học</span></div>

        {/* hero */}
        <div className="card" style={{ overflow: "hidden", marginBottom: 22 }}>
          <div style={{ height: 150, background: `#0f172a url(${D.T.react}) center/cover`, position: "relative" }}>
            <button className="btn btn-ghost btn-sm" style={{ position: "absolute", right: 16, top: 16, background: "rgba(255,255,255,.92)" }}><Ic n="upload" size={15} />Đổi ảnh bìa</button>
          </div>
          <div className="card-pad between wrap" style={{ gap: 16 }}>
            <div style={{ minWidth: 0 }}>
              <div className="row gap-10" style={{ marginBottom: 8 }}><span className="chip chip-info">Frontend</span><Status s="published" /><span className="chip chip-neutral">Nâng cao</span></div>
              <h1 className="t-h2" style={{ margin: 0 }}>Lập trình ReactJS Nâng cao & Redux Toolkit</h1>
              <div className="row gap-16 wrap" style={{ marginTop: 10, color: "var(--text-2)" }}>
                <span className="meta-row"><Ic n="users" size={15} /> 842 học viên</span>
                <span className="meta-row"><Ic n="layers" size={15} /> {chapters.length} chương</span>
                <span className="meta-row"><Ic n="video" size={15} /> {counts.video || 0} bài giảng</span>
                <span className="meta-row" style={{ color: "var(--warning)" }}><Ic n="star" size={15} fill="currentColor" /> <b style={{ color: "var(--text)" }}>4.8</b></span>
              </div>
            </div>
            <div className="row gap-10">
              <button className="btn btn-ghost" onClick={() => nav("player")}><Ic n="eye" size={16} />Xem trước</button>
              <button className="btn btn-primary"><Ic n="check" size={16} />Lưu & Gửi duyệt</button>
            </div>
          </div>
        </div>

        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="video" iconBg="#eaf1ff" iconColor="#2563eb" value={counts.video || 0} label="Bài giảng video" />
          <StatCard icon="file" iconBg="#fef5e6" iconColor="#d97706" value={counts.doc || 0} label="Tài liệu" />
          <StatCard icon="clipboard" iconBg="#f3edff" iconColor="#7c3aed" value={counts.quiz || 0} label="Bài trắc nghiệm" />
          <StatCard icon="clock" iconBg="#e7f8f0" iconColor="#059669" value="~32h" label="Tổng thời lượng" />
        </div>

        <div className="toolbar"><Tabs items={[{v:"content",label:"Nội dung khóa học"},{v:"info",label:"Thông tin"},{v:"price",label:"Học phí & Cài đặt"}]} value={tab} onChange={setTab} /></div>

        {tab === "content" ? (
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            {chapters.map((ch, ci) => (
              <div key={ch.id} className="card" style={{ overflow: "hidden" }}>
                <div className="row gap-12" style={{ padding: "16px 20px", borderBottom: open === ci ? "1px solid var(--border)" : "none", cursor: "pointer", background: "var(--surface-2)" }} onClick={() => setOpen(open === ci ? -1 : ci)}>
                  <Ic n="chevron_down" size={18} style={{ transform: open === ci ? "none" : "rotate(-90deg)", transition: ".2s", color: "var(--text-3)" }} />
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 700, fontSize: 15 }} className="truncate">{ch.name}</div>
                    <div className="t-xs muted" style={{ marginTop: 2 }}>{ch.items.length} mục · {ch.items.filter(i => i.t === "video").length} video · {ch.items.filter(i => i.t === "doc").length} tài liệu</div>
                  </div>
                  <button className="icon-btn" style={{ width: 34, height: 34 }} onClick={e => e.stopPropagation()}><Ic n="edit" size={16} /></button>
                  <button className="icon-btn" style={{ width: 34, height: 34, color: "var(--error)" }} onClick={e => { e.stopPropagation(); setChapters(cs => cs.filter((_, i) => i !== ci)); }}><Ic n="x" size={16} /></button>
                </div>
                {open === ci && (
                  <div style={{ padding: 12 }}>
                    {ch.items.length === 0 && <div className="t-sm muted" style={{ textAlign: "center", padding: "18px 0" }}>Chưa có nội dung. Thêm bài giảng hoặc tài liệu bên dưới.</div>}
                    {ch.items.map((it, ii) => {
                      const m = ITEM_META[it.t];
                      return (
                        <div key={ii} className="row gap-12" style={{ padding: "11px 12px", borderRadius: 11, border: "1px solid var(--border)", marginBottom: 8 }}>
                          <Ic n="dots" size={16} style={{ color: "var(--text-3)", cursor: "grab" }} />
                          <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 10, background: m.bg, color: m.fg, flex: "none" }}><Ic n={m.ic} size={18} fill={it.t === "video" ? m.fg : "none"} /></div>
                          <div className="grow" style={{ minWidth: 0 }}>
                            <div style={{ fontWeight: 600, fontSize: 14 }} className="truncate">{it.title}</div>
                            <div className="t-xs muted row gap-7" style={{ marginTop: 2 }}>
                              <span className={"chip chip-" + (it.t === "video" ? "info" : it.t === "doc" ? "warning" : "neutral")} style={{ padding: "1px 8px", fontSize: 11 }}>{m.lbl}</span>
                              {it.dur && <span className="row gap-4"><Ic n="clock" size={12} />{it.dur}</span>}
                              {it.kind && <span>{it.kind.toUpperCase()}</span>}
                              {it.q && <span>{it.q} câu hỏi</span>}
                            </div>
                          </div>
                          <button className="icon-btn" style={{ width: 32, height: 32 }}><Ic n="edit" size={15} /></button>
                          <button className="icon-btn" style={{ width: 32, height: 32, color: "var(--error)" }} onClick={() => removeItem(ci, ii)}><Ic n="x" size={15} /></button>
                        </div>
                      );
                    })}
                    <div className="row gap-10 wrap" style={{ marginTop: 6 }}>
                      <button className="btn btn-soft btn-sm" onClick={() => { setTargetCh(ci); setVid(true); }}><Ic n="video" size={15} />Thêm video</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => { setTargetCh(ci); setDoc(true); }}><Ic n="file" size={15} />Thêm tài liệu</button>
                      <button className="btn btn-ghost btn-sm"><Ic n="clipboard" size={15} />Thêm trắc nghiệm</button>
                    </div>
                  </div>
                )}
              </div>
            ))}
            <button className="btn btn-ghost btn-block" style={{ borderStyle: "dashed", height: 52 }} onClick={addChapter}><Ic n="plus" size={17} />Thêm chương mới</button>
          </div>
        ) : tab === "info" ? (
          <Section>
            <div className="grid grid-2" style={{ gap: 16 }}>
              <Field label="Tên khóa học" full><input className="input" defaultValue="Lập trình ReactJS Nâng cao & Redux Toolkit" /></Field>
              <Field label="Danh mục"><Select value="Frontend" onChange={()=>{}} options={CATS} /></Field>
              <Field label="Cấp độ"><Select value="Nâng cao" onChange={()=>{}} options={LEVELS} /></Field>
              <Field label="Mô tả" full><textarea className="input" style={{ height: 110, padding: 12, resize: "none" }} defaultValue="Khóa học chuyên sâu về ReactJS và Redux Toolkit, đi từ component nâng cao đến quản lý state phức tạp trong ứng dụng thực tế." /></Field>
              <Field label="Bạn sẽ học được gì" full><textarea className="input" style={{ height: 90, padding: 12, resize: "none" }} defaultValue={"• Thành thạo React Hooks\n• Quản lý state với Redux Toolkit\n• Tối ưu hiệu năng ứng dụng"} /></Field>
            </div>
            <div className="row gap-10" style={{ marginTop: 20, justifyContent: "flex-end" }}><button className="btn btn-ghost">Hủy</button><button className="btn btn-primary">Lưu thay đổi</button></div>
          </Section>
        ) : (
          <Section>
            <div className="grid grid-2" style={{ gap: 16, maxWidth: 560 }}>
              <Field label="Học phí (VND)"><input className="input" defaultValue="2,400,000" /></Field>
              <Field label="Giảm giá (%)"><input className="input" defaultValue="20" /></Field>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 4, marginTop: 18, maxWidth: 560 }}>
              {[["Cho phép tải tài liệu", true], ["Cấp chứng chỉ khi hoàn thành", true], ["Bật bình luận / diễn đàn", true], ["Giới hạn thời gian truy cập", false]].map((s, i) => (
                <label key={i} className="between" style={{ padding: "14px 4px", borderBottom: "1px solid var(--border)", cursor: "pointer" }}><span style={{ fontWeight: 500, fontSize: 14.5 }}>{s[0]}</span><span className="toggle" data-on={s[1]} /></label>
              ))}
            </div>
          </Section>
        )}

        <AddVideoModal open={vid} onClose={() => setVid(false)} onAdd={addItem} />
        <AddDocModal open={doc} onClose={() => setDoc(false)} onAdd={addItem} />
      </div>
    );
  }

  Object.assign(window, { InsCourseDetail });
})();
