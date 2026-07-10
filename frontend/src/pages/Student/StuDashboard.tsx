// @ts-nocheck
/* ============================================================
   RIKKEI EDU — StuDashboard (100% Real Database API)
   ============================================================ */
(function () {
const { useState: uS, useEffect } = React;
const Ic = window.Icon;
const api = window.httpClient;
const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

/* ---------------- Student Dashboard ---------------- */
function StuDashboard({ nav }) {
  const [dashboardData, setDashboardData] = uS(null);
  const [loading, setLoading] = uS(true);

  useEffect(() => {
    setLoading(true);
    const fetchDashboard = window.__dashboardService
      ? window.__dashboardService.getStudentDashboard
      : () => api.get("/student/dashboard").then(r => r.data);

    fetchDashboard()
      .then(data => setDashboardData(data))
      .catch(() => setDashboardData(null))
      .finally(() => setLoading(false));
  }, []);

  const studentName = dashboardData?.studentName || "Học viên";
  const { stats, inProgress, due, weeklyHoursData, roadmap } = React.useMemo(() => {
    const st = dashboardData?.stats || {
      activeCoursesCount: 0,
      nearCompletionCoursesCount: 0,
      pendingTasksCount: 0,
      dueSoonTasksCount: 0,
      certificatesCount: 0,
      weeklyHours: 0,
      weeklyHoursTrend: null
    };
    const ip = (dashboardData?.inProgressCourses || []).slice(0, 4);
    const da = (dashboardData?.dueAssignments || []).slice(0, 6);
    const wh = (dashboardData?.weeklyStudyHours && dashboardData.weeklyStudyHours.length > 0)
      ? dashboardData.weeklyStudyHours : [0,0,0,0,0,0,0];
    const rm = (dashboardData?.skillProgress || []).slice(0, 6);
    return { stats: st, inProgress: ip, due: da, weeklyHoursData: wh, roadmap: rm };
  }, [dashboardData]);

  return (
    <div className="page fade-in">
      <div className="page-head">
        <h1 className="t-display">Chào mừng trở lại, {studentName} 👋</h1>
        <p>Hôm nay là ngày tốt để tiếp tục hành trình học tập của bạn.</p>
      </div>
      <div className="grid grid-stats" style={{ marginBottom: 22 }}>
        <SC icon="cap" iconBg="#eaf1ff" iconColor="#2563eb" value={String(stats.activeCoursesCount)} label="Khóa học đang học" sub={stats.nearCompletionCoursesCount > 0 ? `${stats.nearCompletionCoursesCount} sắp hoàn thành` : "Đang tiến hành"} trend={null} />
        <SC icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value={String(stats.pendingTasksCount)} label="Bài tập cần làm" sub={stats.dueSoonTasksCount > 0 ? `${stats.dueSoonTasksCount} sắp đến hạn` : "Chưa hoàn thành"} />
        <SC icon="award" iconBg="#e7f8f0" iconColor="#059669" value={String(stats.certificatesCount)} label="Chứng chỉ đạt được" trend={null} />
        <SC icon="clock" iconBg="#f3edff" iconColor="#7c3aed" value={`${stats.weeklyHours}h`} label="Giờ học tuần này" trend={stats.weeklyHoursTrend || null} />
      </div>

      <div className="grid grid-3-2" style={{ marginBottom: 22 }}>
        <Sn title="Tiếp tục học" sub="Các khóa học bạn đang theo dõi" action={<span className="link" onClick={() => nav("courses")}>Xem tất cả</span>} pad={false}>
          <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 12 }}>
            {loading && <div className="t-sm muted" style={{ padding: 24, textAlign: "center" }}>Đang tải khóa học...</div>}
            {!loading && inProgress.length === 0 && (
              <div className="t-sm muted" style={{ padding: 24, textAlign: "center" }}>
                Bạn chưa tham gia khóa học nào đang tiến hành. <span className="link" onClick={() => nav("courses")}>Khám phá ngay</span>
              </div>
            )}
            {!loading && inProgress.map(c => (
              <div key={c.id} className="row gap-16" style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 14, cursor: "pointer" }} onClick={() => nav("player", { courseId: c.id })}>
                <div style={{ width: 92, height: 64, borderRadius: 10, backgroundImage: `url(${c.thumbnailUrl || "assets/courses/placeholder.png"})`, backgroundSize: "cover", backgroundPosition: "center", flex: "none" }} />
                <div className="grow">
                  <div className="t-xs" style={{ color: "var(--accent)", fontWeight: 700, marginBottom: 4 }}>{c.category}</div>
                  <div style={{ fontWeight: 700, fontSize: 15 }} className="clamp-1 truncate">{c.title}</div>
                  <div style={{ marginTop: 8 }}><Pg value={c.progress} /></div>
                </div>
                <button className="btn btn-primary btn-sm" style={{ flex: "none" }}><Ic n="play" size={15} fill="#fff" />Học</button>
              </div>
            ))}
          </div>
        </Sn>
        <Sn title="Bài tập sắp đến hạn" action={<span className="link" onClick={() => nav("tasks")}>Tất cả</span>} pad={false}>
          <div style={{ padding: 10 }}>
            {loading && <div className="t-sm muted" style={{ padding: 24, textAlign: "center" }}>Đang tải bài tập...</div>}
            {!loading && due.length === 0 && (
              <div className="t-sm muted" style={{ padding: 24, textAlign: "center" }}>
                Tuyệt vời! Bạn không có bài tập nào cần nộp gấp.
              </div>
            )}
            {!loading && due.map(a => (
              <div key={a.id} className="row gap-12" style={{ padding: 12, borderRadius: 11, cursor: "pointer" }} onClick={() => nav("tasks")}>
                <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 11, background: a.status === "late" ? "var(--chip-error-bg)" : "var(--chip-warning-bg)", color: a.status === "late" ? "var(--error)" : "var(--warning)" }}>
                  <Ic n={a.type === "quiz" ? "clipboard" : "file"} size={19} />
                </div>
                <div className="grow" style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">{a.title}</div>
                  <div className="t-xs muted row gap-6" style={{ marginTop: 3 }}><Ic n="calendar" size={13} />Hạn: {a.deadline}</div>
                </div>
                <St s={a.status} />
              </div>
            ))}
          </div>
        </Sn>
      </div>

      <div className="grid grid-2">
        <Sn title="Hoạt động học tập" sub="Số giờ học 7 ngày qua">
          <LC data={weeklyHoursData} labels={["T2","T3","T4","T5","T6","T7","CN"]} color="#7c3aed" height={210} />
        </Sn>
        <Sn title="Lộ trình học tập" sub="Cá nhân hóa theo danh mục kỹ năng khóa học">
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            {roadmap.length === 0 && !loading && (
              <div className="t-sm muted" style={{ padding: 20, textAlign: "center" }}>Chưa có đủ dữ liệu lộ trình kỹ năng.</div>
            )}
            {roadmap.map((s, i) => (
              <div key={i} className="row gap-12">
                <div style={{ width: 28, height: 28, borderRadius: 999, flex:"none", display:"grid", placeItems:"center", fontWeight:800, fontSize:12.5, background: s.progress===100?"var(--success)":s.progress>0?"var(--accent)":"var(--surface-3)", color: s.progress>0?"#fff":"var(--text-3)" }}>{s.progress===100?"✓":i+1}</div>
                <div className="grow"><div className="between" style={{marginBottom:6}}><span style={{fontWeight:600,fontSize:13.5}}>{s.title}</span><span className="t-xs muted">{s.progress}%</span></div><div className="bar"><span style={{width:s.progress+"%",background:s.progress===100?"var(--success)":"var(--accent)"}}/></div></div>
              </div>
            ))}
          </div>
        </Sn>
      </div>
    </div>
  );
}

window.StuDashboard = StuDashboard;
})();
