// @ts-nocheck
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Empty } = window;

  function StuGroups({ nav }) {
    const [groups, setGroups] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
      setLoading(true);
      const fetchGroups = window.__groupService && window.__groupService.getStudentGroups
        ? window.__groupService.getStudentGroups
        : () => window.httpClient ? window.httpClient.get('/student/groups').then(r => r.data) : Promise.resolve([]);

      fetchGroups()
        .then(setGroups)
        .catch(() => setGroups([]))
        .finally(() => setLoading(false));
    }, []);

    const statusLabel = { UPCOMING: "Sắp diễn ra", ACTIVE: "Đang hoạt động", COMPLETED: "Đã kết thúc" };
    const statusColor = { UPCOMING: "warning", ACTIVE: "success", COMPLETED: "muted" };

    return (
      <div className="page fade-in">
        <div className="page-head">
          <div><h1 className="t-h1">Nhóm học của tôi</h1><p>Các nhóm bạn đang tham gia.</p></div>
        </div>
        {loading ? <div className="t-center muted" style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 300 }}>Đang tải...</div> : (
          <>
            {groups.length === 0 ? (
              <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 300 }}>
                <Empty text="Bạn chưa tham gia nhóm nào" />
              </div>
            ) : (
              <div className="grid grid-cards">
                {groups.map(g => (
                  <div key={g.id} className="card card-pad fade-in" style={{ cursor: "pointer" }} onClick={() => nav("groupDetail", { groupId: g.id })}>
                    <div className="between" style={{ marginBottom: 14 }}>
                      <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 12, background: "#eaf1ff", color: "#2563eb" }}><Ic n="layers" size={22} /></div>
                    </div>
                    <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700 }}>{g.name}</h3>
                    <div className="t-sm muted truncate">{g.courseTitle}</div>
                    <div className="row gap-8" style={{ margin: "14px 0" }}>
                      <span className={"chip chip-" + statusColor[g.status]}>{statusLabel[g.status]}</span>
                      <span className="t-sm muted">{g.memberCount}/{g.maxCapacity || '∞'} thành viên</span>
                    </div>
                    <div className="t-xs dim row gap-6" style={{ marginTop: 12 }}><Ic n="calendar" size={13} />{g.startDate}{g.endDate ? ` \u2013 ${g.endDate}` : ''}</div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    );
  }

  Object.assign(window, { StuGroups });
})();
