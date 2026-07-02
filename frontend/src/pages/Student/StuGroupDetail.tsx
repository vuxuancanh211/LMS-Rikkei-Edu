// @ts-nocheck
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Avatar, StatCard, Search, Section, Empty } = window;
  const { getStudentGroupDetail } = window.__groupService;

  function StuGroupDetail({ nav, groupId }) {
    const [q, setQ] = useState("");
    const [group, setGroup] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
      if (!groupId) { setLoading(false); return; }
      setLoading(true);
      getStudentGroupDetail(groupId)
        .then(setGroup)
        .catch(() => setGroup(null))
        .finally(() => setLoading(false));
    }, [groupId]);

    const members = group?.members || [];
    let list = members.filter(s => !q || s.studentName.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 10);

    const statusLabel = { UPCOMING: "Sắp diễn ra", ACTIVE: "Đang hoạt động", COMPLETED: "Đã kết thúc" };
    if (loading) return (
      <div className="page fade-in">
        <div className="t-center muted" style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 300 }}>Đang tải...</div>
      </div>
    );

    if (!group) return (
      <div className="page fade-in">
        <div className="row gap-10" style={{ marginBottom: 16, cursor: "pointer", color: "var(--text-2)" }} onClick={() => nav("groups")}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách nhóm</span></div>
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 300 }}>
          <Empty text="Không tìm thấy nhóm" />
        </div>
      </div>
    );

    return (
      <div className="page fade-in">
        <div className="between" style={{ marginBottom: 16 }}>
          <div className="row gap-10" style={{ cursor: "pointer", color: "var(--text-2)" }} onClick={() => nav("groups")}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách nhóm</span></div>
          <button className="btn btn-primary" onClick={() => nav("chat", { groupId: group.id })} style={{ flex: "none" }}><Ic n="chat" size={17} />Chat nhóm</button>
        </div>
        <div className="card card-pad" style={{ marginBottom: 22 }}>
          <div className="row gap-16" style={{ alignItems: "center" }}>
            <div className="stat-ic" style={{ width: 50, height: 50, borderRadius: 12, background: "#eaf1ff", color: "#2563eb", flex: "none" }}><Ic n="layers" size={24} /></div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="row gap-10" style={{ alignItems: "baseline", flexWrap: "wrap" }}>
                <h1 className="t-h1" style={{ margin: 0, fontSize: 22 }}>{group.name}</h1>
                <span style={{ padding: "2px 10px", borderRadius: 999, background: "var(--bg-2)", color: "var(--text-3)", fontSize: 12, fontWeight: 600, whiteSpace: "nowrap" }}>{group.courseTitle}</span>
              </div>
            </div>
          </div>
          {group.description && <div style={{ marginTop: 12, fontSize: 14, color: "var(--text-2)", lineHeight: 1.6, whiteSpace: "pre-wrap" }}>{group.description}</div>}
        </div>
        <style>{`.stu-stat-sm .stat-val { font-size: 22px; } .stu-stat-sm .stat-lbl { font-size: 12px; }`}</style>
        <div className="grid grid-stats stu-stat-sm" style={{ marginBottom: 22, gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))" }}>
          <StatCard icon="users" iconBg="#eaf1ff" iconColor="#2563eb" value={String(group.memberCount || 0)} label="Thành viên" />
          <StatCard icon="calendar" iconBg="#f3edff" iconColor="#7c3aed" value={group.startDate} label="Ngày bắt đầu" />
          {group.endDate && <StatCard icon="calendar" iconBg={group.status === "COMPLETED" ? "#e7f8f0" : "#fef5e6"} iconColor={group.status === "COMPLETED" ? "#059669" : "#d97706"} value={group.endDate} label="Ngày kết thúc" />}
          <StatCard icon={group.status === "UPCOMING" ? "clock" : group.status === "ACTIVE" ? "check_circle" : "check_circle"} iconBg={group.status === "UPCOMING" ? "#fef5e6" : group.status === "ACTIVE" ? "#e7f8f0" : "#f3f4f6"} iconColor={group.status === "UPCOMING" ? "#d97706" : group.status === "ACTIVE" ? "#059669" : "#6b7280"} value={statusLabel[group.status]} label="Trạng thái" />
        </div>
        <div className="toolbar">
          <Search placeholder="Tìm học viên..." value={q} onChange={setQ} />
        </div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Học viên</th><th>Email</th><th>Ngày tham gia</th></tr></thead>
            <tbody>{pg.slice.map(m => (
              <tr key={m.id}>
                <td><div className="row gap-10"><Avatar name={m.studentName} size={36} img={m.avatarUrl} /><b style={{ fontSize: 14 }}>{m.studentName}</b></div></td>
                <td className="muted">{m.studentEmail}</td>
                <td className="muted">{new Date(m.joinedAt).toLocaleDateString("vi-VN")}</td>
              </tr>
            ))}</tbody>
          </table></div>
        </Section>
        {pg.total === 0 && !loading && <Empty text="Không tìm thấy học viên nào" />}
        <window.PageBar pg={pg} unit="học viên" />
      </div>
    );
  }

  Object.assign(window, { StuGroupDetail });
})();
