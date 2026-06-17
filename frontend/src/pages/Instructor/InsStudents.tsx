// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Học viên
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const myCourses = D.courses.filter(c => ["Nguyễn Văn An", "Trần Thị Bình", "Lê Văn Cường", "Phạm Thị Dung"].includes(c.instructor)).slice(0, 9);

  function InsStudents() {
    const [q, setQ] = useState("");
    const studs = D.users.filter(u => u.role === "Học viên").concat(D.groupStudents.map((s, i) => ({ id: "gs" + i, name: s.name, email: s.name.toLowerCase().replace(/\s/g, ".") + "@gmail.com", role: "Học viên", status: "active", courses: Math.floor(Math.random() * 4) + 1, lastActive: "gần đây", joined: s.joined, progress: s.progress })));
    let list = studs.filter(s => !q || s.name.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 10);
    return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Học viên của tôi</h1><p>Toàn bộ học viên trong các khóa học bạn đang giảng dạy.</p></div>
        <div className="toolbar"><Search placeholder="Tìm học viên theo tên hoặc email..." value={q} onChange={setQ} /><Select value="all" onChange={()=>{}} options={[{v:"all",label:"Tất cả khóa học"}]} style={{width:200,flex:"none"}} /></div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Học viên</th><th>Email</th><th>Số khóa học</th><th>Tiến độ</th><th>Hoạt động cuối</th><th></th></tr></thead>
            <tbody>{pg.slice.map(s => (
              <tr key={s.id}>
                <td><div className="row gap-10"><Avatar name={s.name} size={36} /><b style={{ fontSize: 14 }}>{s.name}</b></div></td>
                <td className="muted">{s.email}</td>
                <td><b>{s.courses}</b></td>
                <td style={{ minWidth: 150 }}><Progress value={s.progress || 60} /></td>
                <td className="muted t-sm">{s.lastActive}</td>
                <td><button className="btn btn-ghost btn-sm"><Ic n="message" size={15} />Nhắn tin</button></td>
              </tr>
            ))}</tbody>
          </table></div>
        </Section>
        <window.PageBar pg={pg} unit="học viên" />
      </div>
    );
  }

  Object.assign(window, { InsStudents });
})();
