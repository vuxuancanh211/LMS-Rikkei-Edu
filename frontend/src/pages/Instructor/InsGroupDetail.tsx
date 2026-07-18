// @ts-nocheck
(function () {
  const { useState, useEffect, useMemo } = React;
  const Ic = window.Icon;
  const { Avatar, StatCard, Search, Section, Modal, ModalHead, Empty } = window;
  const { getGroupDetail, addGroupMembers, removeGroupMember } = window.__groupService;

  function InsGroupDetail({ nav, groupId: propGroupId }: { nav?: any; groupId?: string } = {}) {
    const [urlGroupId, setUrlGroupId] = useState(() => new URLSearchParams(window.location.search).get("groupId"));
    useEffect(() => {
      const checkUrl = () => setUrlGroupId(new URLSearchParams(window.location.search).get("groupId"));
      window.addEventListener("popstate", checkUrl);
      return () => window.removeEventListener("popstate", checkUrl);
    }, []);
    const groupId = propGroupId || urlGroupId || window.__selectedGroupId || sessionStorage.getItem("selectedGroupId") || null;

    const [q, setQ] = useState("");
    const [group, setGroup] = useState(null);
    const [loading, setLoading] = useState(true);
    const [removeTarget, setRemoveTarget] = useState(null);
    const [removing, setRemoving] = useState(false);

    useEffect(() => {
      if (!groupId) { setLoading(false); return; }
      setLoading(true);
      getGroupDetail(groupId)
        .then(setGroup)
        .catch(() => setGroup(null))
        .finally(() => setLoading(false));
    }, [groupId]);

    const members = group?.members || [];
    const avgProgress = useMemo(() => {
      const withProgress = members.filter(m => m.progress != null);
      if (!withProgress.length) return null;
      const sum = withProgress.reduce((a, m) => a + m.progress, 0);
      return Math.round(sum / withProgress.length);
    }, [members]);
    let list = members.filter(s => !q || s.studentName.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 10);

    const statusLabel = { UPCOMING: "Sắp diễn ra", ACTIVE: "Đang hoạt động", COMPLETED: "Đã kết thúc" };
    const statusColor = { UPCOMING: "warning", ACTIVE: "success", COMPLETED: "muted" };

    const handleRemoveConfirm = () => {
      if (!removeTarget) return;
      setRemoving(true);
      removeGroupMember(groupId, removeTarget.studentId)
        .then(() => {
          setRemoveTarget(null);
          getGroupDetail(groupId).then(setGroup);
        })
        .catch(() => {})
        .finally(() => setRemoving(false));
    };

    if (loading) return (
      <div className="page fade-in">
        <div className="row gap-10" style={{ marginBottom: 16, cursor: "pointer", color: "var(--text-2)" }} onClick={() => nav("groups")}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách nhóm</span></div>
        <div className="t-center muted" style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 300 }}>Đang tải...</div>
      </div>
    );

    return (
      <div className="page fade-in">
        <div className="row gap-10" style={{ marginBottom: 16, cursor: "pointer", color: "var(--text-2)" }} onClick={() => nav("groups")}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách nhóm</span></div>
        <div className="page-head between">
          <div>
            <h1 className="t-h1">{group?.name}</h1>
            <p className="row gap-8">{group?.courseTitle}<span className={"chip chip-" + statusColor[group?.status]}>{statusLabel[group?.status]}</span>• {group?.memberCount}/{group?.maxCapacity || '∞'} thành viên • {group?.startDate}{group?.endDate ? ` \u2013 ${group?.endDate}` : ''}</p>
          </div>
          <div className="row gap-10">
          </div>
        </div>
        <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns: "repeat(2,1fr)" }}>
          <StatCard icon="users" iconBg="#eaf1ff" iconColor="#2563eb" value={String(group?.memberCount || 0)} label="Thành viên" />
          <StatCard icon="trending" iconBg="#e7f8f0" iconColor="#059669" value={avgProgress != null ? avgProgress + '%' : '—'} label="Tiến độ TB" />
        </div>
        <div className="toolbar"><Search placeholder="Tìm học viên..." value={q} onChange={setQ} /></div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Học viên</th><th>Email</th><th>Tiến độ</th><th>Ngày tham gia</th><th></th></tr></thead>
            <tbody>{pg.slice.map(m => (
              <tr key={m.id}>
                <td><div className="row gap-10"><Avatar name={m.studentName} size={36} src={m.avatarUrl} /><b style={{ fontSize: 14 }}>{m.studentName}</b></div></td>
                <td className="muted">{m.studentEmail}</td>
                <td>{m.progress != null ? <div className="row gap-6"><div className="progress-bar" style={{ width: 80 }}><div style={{ width: m.progress + '%' }} /></div><span className="t-sm">{m.progress}%</span></div> : <span className="muted">—</span>}</td>
                <td className="muted">{new Date(m.joinedAt).toLocaleDateString("vi-VN")}</td>
                <td><button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => setRemoveTarget(m)}><Ic n="x" size={18} /></button></td>
              </tr>
            ))}</tbody>
          </table></div>
        </Section>
        {pg.total === 0 && !loading && <Empty text="Chưa có học viên nào trong nhóm" />}
        <window.PageBar pg={pg} unit="học viên" />

        <Modal open={!!removeTarget} onClose={() => { if (!removing) setRemoveTarget(null); }}>
          <ModalHead title="Xoá học viên" sub={removeTarget ? `Học viên sẽ bị xoá khỏi nhóm "${group?.name}"` : ""} icon="x" iconBg="#fef2f2" iconColor="#dc2626" onClose={() => { if (!removing) setRemoveTarget(null); }} />
          <div className="modal-body">
            {removeTarget && (
              <div className="row gap-12" style={{ alignItems: "center", padding: "12px 14px", background: "var(--bg-2)", borderRadius: 10 }}>
                <Avatar name={removeTarget.studentName} size={40} src={removeTarget.avatarUrl} />
                <div>
                  <div style={{ fontWeight: 600, fontSize: 15 }}>{removeTarget.studentName}</div>
                  <div className="t-sm muted">{removeTarget.studentEmail}</div>
                </div>
              </div>
            )}
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => setRemoveTarget(null)} disabled={removing}>Hủy</button>
            <button className="btn" style={{ background: "#dc2626", color: "#fff" }} disabled={removing} onClick={handleRemoveConfirm}>
              {removing ? "Đang xoá..." : "Xác nhận xoá"}
            </button>
          </div>
        </Modal>
      </div>
    );
  }

  Object.assign(window, { InsGroupDetail });
})();
