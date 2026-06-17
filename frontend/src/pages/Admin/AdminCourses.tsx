// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Quản lý Khóa học
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  function AdminCourses() {
    const [tab, setTab] = useState("all");
    const [q, setQ] = useState("");
    let list = D.courses;
    if (tab !== "all") list = D.courses.filter(c => c.pubStatus === tab);
    if (q) list = list.filter(c => c.title.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 10);
    return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Quản lý Khóa học</h1><p>Toàn bộ khóa học trên hệ thống, theo dõi trạng thái xuất bản và hiệu suất.</p></div>
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="book" iconBg="#eaf1ff" iconColor="#2563eb" value="1,200" label="Tổng khóa học" trend={5} />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value="1,043" label="Đã xuất bản" />
          <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value="5" label="Chờ duyệt" />
          <StatCard icon="file" iconBg="#eef2f7" iconColor="#64748b" value="152" label="Bản nháp" />
        </div>
        <div className="toolbar">
          <Tabs items={[{v:"all",label:"Tất cả"},{v:"published",label:"Đã xuất bản"},{v:"pending",label:"Chờ duyệt"},{v:"draft",label:"Bản nháp"}]} value={tab} onChange={setTab} />
          <div className="grow" /><Search placeholder="Tìm khóa học..." value={q} onChange={setQ} style={{ width: 260, flex: "none" }} />
        </div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Khóa học</th><th>Giảng viên</th><th>Danh mục</th><th>Học viên</th><th>Đánh giá</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>{pg.slice.map(c => (
              <tr key={c.id}>
                <td><div className="row gap-11"><div style={{ width: 52, height: 36, borderRadius: 8, flex: "none", backgroundImage: `url(${c.thumb})`, backgroundSize: "cover", backgroundPosition: "center" }} /><b style={{ fontSize: 13.5, maxWidth: 220 }} className="truncate">{c.title}</b></div></td>
                <td className="muted t-sm">{c.instructor}</td>
                <td><span className="chip chip-neutral">{c.cat}</span></td>
                <td><b>{c.students.toLocaleString()}</b></td>
                <td>{c.rating > 0 ? <span className="row gap-5" style={{ color: "var(--warning)", fontWeight: 700 }}><Ic n="star" size={14} fill="currentColor" />{c.rating}</span> : <span className="dim">—</span>}</td>
                <td><Status s={c.pubStatus} /></td>
                <td><button className="icon-btn" style={{ width: 34, height: 34 }}><Ic n="dots" size={18} /></button></td>
              </tr>
            ))}</tbody>
          </table></div>
        </Section>
        <window.PageBar pg={pg} unit="khóa học" />
      </div>
    );
  }

  Object.assign(window, { AdminCourses });
})();
