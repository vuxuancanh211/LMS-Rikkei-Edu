// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Quản lý Khóa học (+ popup tạo khóa học)
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

  /* ---------------- Create Course popup ---------------- */
  function CreateCourseModal({ open, onClose, onCreated }) {
    const [step, setStep] = useState(1);
    const [cat, setCat] = useState(CATS[0]);
    const [level, setLevel] = useState(LEVELS[1]);
    const close = () => { setStep(1); onClose(); };
    return (
      <Modal open={open} onClose={close} max={620}>
        <ModalHead title="Tạo khóa học mới" sub={"Bước " + step + "/2 · " + (step === 1 ? "Thông tin cơ bản" : "Ảnh bìa & xuất bản")} icon="book" iconBg="#eaf1ff" iconColor="#2563eb" onClose={close} />
        {/* progress */}
        <div style={{ padding: "0 24px" }}>
          <div className="row gap-8" style={{ marginTop: 4 }}>
            {[1, 2].map(n => <div key={n} style={{ flex: 1, height: 4, borderRadius: 999, background: n <= step ? "var(--accent)" : "var(--border)" }} />)}
          </div>
        </div>
        <div className="modal-body" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
          {step === 1 ? <>
            <Field label="Tên khóa học" full><input className="input" placeholder="VD: Lập trình ReactJS Nâng cao & Redux" /></Field>
            <Field label="Danh mục"><Select value={cat} onChange={setCat} options={CATS} /></Field>
            <Field label="Cấp độ"><Select value={level} onChange={setLevel} options={LEVELS} /></Field>
            <Field label="Giảng viên phụ trách" full>
              <div className="row gap-10" style={{ padding: "9px 12px", border: "1px solid var(--border-input)", borderRadius: 8 }}>
                <Avatar name="Nguyễn Văn An" size={30} /><div><div style={{ fontWeight: 600, fontSize: 13.5 }}>Nguyễn Văn An</div><div className="t-xs muted">an.nguyen@rikkei.edu</div></div>
              </div>
            </Field>
            <Field label="Mô tả khóa học" full><textarea className="input" style={{ height: 96, padding: 12, resize: "none" }} placeholder="Giới thiệu ngắn gọn nội dung, đối tượng và mục tiêu của khóa học..." /></Field>
          </> : <>
            <div style={{ gridColumn: "1 / -1" }}><Dropzone icon="upload" title="Tải ảnh bìa khóa học" hint="PNG, JPG tối đa 4MB · tỉ lệ 16:9" h={36} /></div>
            <Field label="Học phí (VND)"><input className="input" placeholder="0 = miễn phí" /></Field>
            <Field label="Thời lượng dự kiến"><input className="input" placeholder="VD: 32 giờ" /></Field>
            <Field label="Yêu cầu đầu vào" full><input className="input" placeholder="VD: Đã biết JavaScript cơ bản" /></Field>
            <label className="row gap-10" style={{ gridColumn: "1 / -1", padding: "12px 14px", background: "var(--chip-info-bg)", borderRadius: 11, cursor: "pointer" }}>
              <input type="checkbox" style={{ width: 18, height: 18 }} defaultChecked />
              <div><div style={{ fontWeight: 600, fontSize: 13.5, color: "var(--chip-info-fg)" }}>Gửi duyệt ngay sau khi tạo</div><div className="t-xs" style={{ color: "var(--chip-info-fg)", opacity: .8 }}>Khóa học sẽ chuyển tới Quản trị viên để phê duyệt</div></div>
            </label>
          </>}
        </div>
        <div className="modal-foot">
          {step === 2 && <button className="btn btn-ghost" onClick={() => setStep(1)} style={{ marginRight: "auto" }}><Ic n="chevron_left" size={16} />Quay lại</button>}
          <button className="btn btn-ghost" onClick={close}>Hủy</button>
          {step === 1
            ? <button className="btn btn-primary" onClick={() => setStep(2)}>Tiếp theo<Ic n="chevron_right" size={16} /></button>
            : <button className="btn btn-success" onClick={() => { close(); onCreated && onCreated(); }}><Ic n="check" size={16} />Tạo & vào chỉnh sửa</button>}
        </div>
      </Modal>
    );
  }


  /* ---------------- Instructor Courses ---------------- */
  function InsCourses({ nav, demo }) {
    const [q, setQ] = useState("");
    const [create, setCreate] = useState(demo === "create");
    let list = myCourses.filter(c => !q || c.title.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 8);
    return (
      <div className="page fade-in">
        <div className="page-head between"><div><h1 className="t-h1">Quản lý Khóa học</h1><p>Tạo, chỉnh sửa và theo dõi các khóa học của bạn.</p></div><button className="btn btn-primary" onClick={() => setCreate(true)}><Ic n="plus" size={17} />Tạo khóa học</button></div>
        <window.CreateCourseModal open={create} onClose={() => setCreate(false)} onCreated={() => nav("courseDetail")} />
        <div className="toolbar"><Search placeholder="Tìm khóa học..." value={q} onChange={setQ} /><Select value="all" onChange={()=>{}} options={[{v:"all",label:"Tất cả trạng thái"},{v:"pub",label:"Đã xuất bản"},{v:"pend",label:"Chờ duyệt"}]} style={{width:180,flex:"none"}} /></div>
        <div className="grid grid-cards">{pg.slice.map(c => (
          <div key={c.id} className="card course-card fade-in">
            <div className="course-thumb" style={{ backgroundImage: `url(${c.thumb})` }}>
              <span className="tl"><span className="chip" style={{ background: "rgba(15,23,42,.72)", color: "#fff" }}>{c.cat}</span></span>
              <span className="tr"><Status s={c.pubStatus} /></span>
            </div>
            <div className="course-body">
              <h3 className="clamp-2">{c.title}</h3>
              <div className="row gap-16 wrap">
                <span className="meta-row"><Ic n="users" size={15} /> {c.students} học viên</span>
                <span className="meta-row"><Ic n="layers" size={15} /> {c.chapters} chương</span>
              </div>
              <div className="row gap-10" style={{ marginTop: "auto", paddingTop: 6 }}>
                <button className="btn btn-ghost btn-sm grow" onClick={() => nav("player")}><Ic n="eye" size={15} />Xem</button>
                <button className="btn btn-primary btn-sm grow" onClick={() => nav("courseDetail")}><Ic n="edit" size={15} />Sửa</button>
              </div>
            </div>
          </div>
        ))}</div>
        <window.PageBar pg={pg} unit="khóa học" />
      </div>
    );
  }


  Object.assign(window, { InsCourses, CreateCourseModal });
})();
