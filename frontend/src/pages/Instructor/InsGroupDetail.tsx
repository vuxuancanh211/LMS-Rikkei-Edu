// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Chi tiết Nhóm
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const myCourses = D.courses.filter(c => ["Nguyễn Văn An", "Trần Thị Bình", "Lê Văn Cường", "Phạm Thị Dung"].includes(c.instructor)).slice(0, 9);

  /* ---------------- Group Detail (student list) ---------------- */
  function InsGroupDetail({ nav }) {
    const [q, setQ] = useState("");
    let list = D.groupStudents.filter(s => !q || s.name.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 10);
    return (
      <div className="page fade-in">
        <div className="row gap-10" style={{ marginBottom: 16, cursor: "pointer", color: "var(--text-2)" }} onClick={() => nav("groups")}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách nhóm</span></div>
        <div className="page-head between">
          <div><h1 className="t-h1">Nhóm A1 - ReactJS K15</h1><p>Lập trình ReactJS Nâng cao • 18/20 thành viên • 01/03/2026 – 30/06/2026</p></div>
          <div className="row gap-10"><button className="btn btn-ghost"><Ic n="upload" size={16} />Import .xlsx</button><button className="btn btn-primary"><Ic n="plus" size={17} />Thêm học viên</button></div>
        </div>
        <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns: "repeat(4,1fr)" }}>
          <StatCard icon="users" iconBg="#eaf1ff" iconColor="#2563eb" value="18" label="Thành viên" />
          <StatCard icon="trending" iconBg="#e7f8f0" iconColor="#059669" value="72%" label="Tiến độ TB" />
          <StatCard icon="check_circle" iconBg="#f3edff" iconColor="#7c3aed" value="14" label="Đã nộp bài" />
          <StatCard icon="warn" iconBg="#fef5e6" iconColor="#d97706" value="4" label="Chưa nộp / trễ" />
        </div>
        <div className="toolbar"><Search placeholder="Tìm học viên..." value={q} onChange={setQ} /><Select value="all" onChange={()=>{}} options={[{v:"all",label:"Tất cả trạng thái"},{v:"sub",label:"Đã nộp"},{v:"no",label:"Chưa nộp"}]} style={{width:180,flex:"none"}} /></div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Học viên</th><th>Ngày tham gia</th><th>Tiến độ</th><th>Bài tập</th><th>Điểm TB</th><th></th></tr></thead>
            <tbody>{pg.slice.map((s, i) => (
              <tr key={i}>
                <td><div className="row gap-10"><Avatar name={s.name} size={36} /><b style={{ fontSize: 14 }}>{s.name}</b></div></td>
                <td className="muted">{s.joined}</td>
                <td style={{ minWidth: 160 }}><Progress value={s.progress} /></td>
                <td><span className={"chip chip-" + (s.submit === "Đã nộp" ? "success" : s.submit === "Trễ hạn" ? "error" : "warning")}>{s.submit}</span></td>
                <td style={{ fontWeight: 700 }}>{s.grade}</td>
                <td><button className="icon-btn" style={{ width: 34, height: 34 }}><Ic n="dots" size={18} /></button></td>
              </tr>
            ))}</tbody>
          </table></div>
        </Section>
        <window.PageBar pg={pg} unit="học viên" />
      </div>
    );
  }


  Object.assign(window, { InsGroupDetail });
})();
