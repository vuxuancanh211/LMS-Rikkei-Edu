// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Quản lý Người dùng (+ popup thêm người dùng)
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  /* ---------------- Users management ---------------- */
  function AdminUsers() {
    const [tab, setTab] = useState("all");
    const [q, setQ] = useState("");
    const [add, setAdd] = useState(false);
    let list = D.users;
    if (tab !== "all") list = D.users.filter(u => u.role === tab);
    if (q) list = list.filter(u => u.name.toLowerCase().includes(q.toLowerCase()) || u.email.includes(q.toLowerCase()));
    const pg = window.usePaged(list, 10);
    const roleChip = { "Giảng viên":"info", "Học viên":"neutral", "Quản trị viên":"warning" };
    return (
      <div className="page fade-in">
        <div className="page-head between"><div><h1 className="t-h1">Quản lý Người dùng</h1><p>Quản lý tài khoản, phân quyền và trạng thái hoạt động của toàn hệ thống.</p></div><button className="btn btn-primary" onClick={() => setAdd(true)}><Ic n="plus" size={17} />Thêm người dùng</button></div>
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="users" iconBg="#eaf1ff" iconColor="#2563eb" value="12,450" label="Tổng người dùng" trend={12} />
          <StatCard icon="cap" iconBg="#f3edff" iconColor="#7c3aed" value="450" label="Giảng viên" />
          <StatCard icon="user" iconBg="#e7f8f0" iconColor="#059669" value="11,988" label="Học viên" trend={8} />
          <StatCard icon="lock" iconBg="#fdecec" iconColor="#dc2626" value="12" label="Tài khoản bị khóa" />
        </div>
        <div className="toolbar">
          <Tabs items={[{v:"all",label:"Tất cả"},{v:"Học viên",label:"Học viên"},{v:"Giảng viên",label:"Giảng viên"},{v:"Quản trị viên",label:"Quản trị"}]} value={tab} onChange={setTab} />
          <div className="grow" />
          <Search placeholder="Tìm theo tên, email..." value={q} onChange={setQ} style={{ width: 280, flex: "none" }} />
          <button className="btn btn-ghost btn-icon"><Ic n="filter" size={18} /></button>
        </div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Người dùng</th><th>Vai trò</th><th>Ngày tham gia</th><th>Khóa học</th><th>Hoạt động cuối</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>{pg.slice.map(u => (
              <tr key={u.id}>
                <td><div className="row gap-11"><Avatar name={u.name} size={38} /><div><div style={{ fontWeight: 700, fontSize: 14 }}>{u.name}</div><div className="t-xs muted">{u.email}</div></div></div></td>
                <td><span className={"chip chip-" + roleChip[u.role]}>{u.role}</span></td>
                <td className="muted">{u.joined}</td>
                <td><b>{u.courses}</b></td>
                <td className="muted t-sm">{u.lastActive}</td>
                <td><Status s={u.status} /></td>
                <td><div className="row gap-6"><button className="icon-btn" style={{ width: 34, height: 34 }}><Ic n="edit" size={16} /></button><button className="icon-btn" style={{ width: 34, height: 34, color: u.status === "active" ? "var(--error)" : "var(--success)" }}><Ic n="lock" size={16} /></button></div></td>
              </tr>
            ))}</tbody>
          </table></div>
        </Section>
        <window.PageBar pg={pg} unit="người dùng" />
        <Modal open={add} onClose={() => setAdd(false)}>
          <ModalHead title="Thêm người dùng mới" sub="Tạo tài khoản cho nhân sự hoặc học viên" icon="users" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => setAdd(false)} />
          <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Họ và tên</label><input className="input" placeholder="Nguyễn Văn A" /></div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Email</label><input className="input" placeholder="email@rikkei.edu" /></div>
            </div>
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Vai trò</label><Select value="hv" onChange={()=>{}} options={[{v:"hv",label:"Học viên"},{v:"gv",label:"Giảng viên"},{v:"ad",label:"Quản trị viên"}]} /></div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Số điện thoại</label><input className="input" placeholder="09xx xxx xxx" /></div>
            </div>
            <div style={{ background: "var(--chip-info-bg)", borderRadius: 11, padding: "12px 14px", fontSize: 13, color: "var(--chip-info-fg)", display: "flex", gap: 10 }}><Ic n="mail" size={18} style={{ flex: "none" }} />Mật khẩu khởi tạo sẽ được gửi tới email của người dùng.</div>
          </div>
          <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setAdd(false)}>Hủy</button><button className="btn btn-primary" onClick={() => setAdd(false)}>Tạo tài khoản</button></div>
        </Modal>
      </div>
    );
  }


  Object.assign(window, { AdminUsers });
})();
