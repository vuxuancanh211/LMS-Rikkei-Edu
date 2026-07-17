// @ts-nocheck
(function () {
  const { useState, useEffect, useMemo } = React;
  const Ic = window.Icon;
  const { Empty, Search, Select, PageBar } = window;

  function hasSearchText(value) {
    return /[\p{L}\p{N}]/u.test(String(value || ''));
  }

  function groupMatches(g, q) {
    const keywords = (q || "").toLowerCase().match(/[\p{L}\p{N}]+/gu) || [];
    if (keywords.length === 0) return true;
    const haystack = `${g.name || ''} ${g.courseTitle || ''} ${g.description || ''}`.toLowerCase();
    return keywords.some(keyword => haystack.includes(keyword));
  }

  function StuGroups({ nav }) {
    const [groups, setGroups] = useState([]);
    const [loading, setLoading] = useState(true);
    const [q, setQ] = useState("");
    const [courseFilter, setCourseFilter] = useState("all");

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

    const courseOpts = useMemo(() => {
      const map = new Map();
      groups.forEach(g => {
        if (g.courseId && g.courseTitle) {
          map.set(g.courseId, g.courseTitle);
        }
      });
      return Array.from(map.entries()).map(([v, label]) => ({ v, label }));
    }, [groups]);

    let list = groups.filter(g => groupMatches(g, q));
    if (courseFilter !== 'all') list = list.filter(g => g.courseId === courseFilter);
    const pg = window.usePaged(list, 8);

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
              <>
                <div className="toolbar" style={{ marginBottom: 20 }}>
                  <Search placeholder="Tìm theo tên nhóm hoặc khóa học..." value={q} onChange={(value) => { if (!value || hasSearchText(value)) setQ(value); }} />
                  <Select value={courseFilter} onChange={(v) => { setCourseFilter(v); pg.setPage(1); }} options={[{v:"all", label:"Tất cả khóa học"}, ...courseOpts]} style={{ width: 220, flex: "none" }} />
                </div>
                {pg.total === 0 ? (
                  <Empty icon="layers" title="Không tìm thấy nhóm học" text="Không có nhóm học nào phù hợp với từ khóa và bộ lọc hiện tại." />
                ) : (
                  <>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 14 }}>
                      {pg.slice.map(g => (
                        <div key={g.id} className="card fade-in" style={{ padding: "14px 16px", cursor: "pointer", display: "flex", flexDirection: "column", justifyContent: "space-between" }} onClick={() => nav("groupDetail", { groupId: g.id })}>
                          <div>
                            <div className="between" style={{ marginBottom: 10 }}>
                              <div className="stat-ic" style={{ width: 36, height: 36, borderRadius: 10, background: "#eaf1ff", color: "#2563eb", display: "flex", alignItems: "center", justifyContent: "center" }}><Ic n="layers" size={18} /></div>
                              <span className={"chip chip-" + statusColor[g.status]} style={{ fontSize: 11, padding: "2px 8px" }}>{statusLabel[g.status]}</span>
                            </div>
                            <h3 style={{ margin: "0 0 4px", fontSize: 14.5, fontWeight: 700, lineHeight: "1.3" }} className="truncate">{g.name}</h3>
                            <div className="t-xs muted truncate" style={{ marginBottom: 10 }}>{g.courseTitle}</div>
                          </div>
                          <div>
                            <div className="row gap-6" style={{ margin: "8px 0", fontSize: 12, color: "var(--text-2)" }}>
                              <Ic n="users" size={14} style={{ color: "var(--text-3)" }} />
                              <span>{g.memberCount}/{g.maxCapacity || '∞'} thành viên</span>
                            </div>
                            <div className="t-xs dim row gap-5" style={{ marginTop: 8, paddingTop: 8, borderTop: "1px solid var(--border)", fontSize: 11 }}><Ic n="calendar" size={12} />{g.startDate}{g.endDate ? ` \u2013 ${g.endDate}` : ''}</div>
                          </div>
                        </div>
                      ))}
                    </div>
                    <PageBar pg={pg} unit="nhóm học" />
                  </>
                )}
              </>
            )}
          </>
        )}
      </div>
    );
  }

  Object.assign(window, { StuGroups });
})();
