// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Quản lý Nhóm (+ popup tạo nhóm)
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const myCourses = D.courses.filter(c => ["Nguyễn Văn An", "Trần Thị Bình", "Lê Văn Cường", "Phạm Thị Dung"].includes(c.instructor)).slice(0, 9);

  /* ---------------- Groups ---------------- */
  function InsGroups({ nav }) {
    const [add, setAdd] = useState(false);
    const [q, setQ] = useState("");
    let list = D.groups.filter(g => !q || g.name.toLowerCase().includes(q.toLowerCase()) || g.course.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 8);
    return (
      <div className="page fade-in">
        <div className="page-head between"><div><h1 className="t-h1">Quản lý Nhóm</h1><p>Tạo nhóm, phân bổ học viên và theo dõi tiến độ từng đợt học.</p></div><button className="btn btn-primary" onClick={() => setAdd(true)}><Ic n="plus" size={17} />Tạo nhóm mới</button></div>
        <div className="toolbar">
          <Search placeholder="Tìm nhóm theo tên hoặc khóa học..." value={q} onChange={setQ} />
          <Select value="all" onChange={()=>{}} options={[{v:"all",label:"Tất cả khóa học"},{v:"react",label:"ReactJS Nâng cao"},{v:"node",label:"NodeJS & Express"}]} style={{width:200,flex:"none"}} />
        </div>
        <div className="grid grid-cards">
          {pg.slice.map(g => (
            <div key={g.id} className="card card-pad fade-in" style={{ cursor: "pointer" }} onClick={() => nav("groupDetail")}>
              <div className="between" style={{ marginBottom: 14 }}>
                <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 12, background: "#f3edff", color: "#7c3aed" }}><Ic n="layers" size={22} /></div>
                <button className="icon-btn" style={{ width: 34, height: 34 }} onClick={(e)=>e.stopPropagation()}><Ic n="dots" size={18} /></button>
              </div>
              <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700 }}>{g.name}</h3>
              <div className="t-sm muted truncate">{g.course}</div>
              <div className="row gap-8" style={{ margin: "14px 0" }}>
                <div style={{ display: "flex" }}>{["A","B","C","D"].map((x,i)=><span key={i} className="avatar" style={{ width: 28, height: 28, fontSize: 11, marginLeft: i?-8:0, border: "2px solid #fff", background: window.UI.AV[i] }}>{x}</span>)}</div>
                <span className="t-sm muted">{g.members}/{g.max} thành viên</span>
              </div>
              <div className="between" style={{ marginBottom: 7 }}><span className="t-xs muted">Tiến độ trung bình</span><b className="t-xs">{g.progress}%</b></div>
              <div className="bar"><span style={{ width: g.progress + "%" }} /></div>
              <div className="t-xs dim row gap-6" style={{ marginTop: 12 }}><Ic n="calendar" size={13} />{g.start} – {g.end}</div>
            </div>
          ))}
        </div>
        <window.PageBar pg={pg} unit="nhóm" />
        <Modal open={add} onClose={() => setAdd(false)}>
          <ModalHead title="Tạo nhóm mới" sub="Thêm một đợt học mới vào khóa học" icon="layers" iconBg="#f3edff" iconColor="#7c3aed" onClose={() => setAdd(false)} />
          <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên nhóm</label><input className="input" placeholder="VD: Nhóm A1 - ReactJS K16" /></div>
            <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Khóa học</label><Select value="c1" onChange={()=>{}} options={myCourses.map(c=>({v:c.id,label:c.title}))} /></div>
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Số thành viên tối đa</label><input className="input" type="number" defaultValue={20} /></div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Ngày bắt đầu</label><input className="input" type="date" /></div>
            </div>
            <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Mô tả</label><textarea className="input" style={{ height: 84, padding: 12, resize: "none" }} placeholder="Mô tả ngắn về nhóm..." /></div>
          </div>
          <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setAdd(false)}>Hủy</button><button className="btn btn-primary" onClick={() => setAdd(false)}>Tạo nhóm</button></div>
        </Modal>
      </div>
    );
  }


  Object.assign(window, { InsGroups });
})();
