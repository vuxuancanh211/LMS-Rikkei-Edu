// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Tổng quan
   ============================================================ */
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Avatar, Status, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;
  /* ---------------- Instructor Dashboard ---------------- */
  function InsDashboard({ nav }) {
    const [create, setCreate] = useState(false);
    const [dashboard, setDashboard] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
      let active = true;
      async function load() {
        setLoading(true);
        try {
          let data;
          if (window.__dashboardService?.getInstructorDashboard) {
            data = await window.__dashboardService.getInstructorDashboard();
          } else if (window.httpClient) {
            const res = await window.httpClient.get('/instructor/dashboard');
            data = res.data;
          }
          if (active && data) {
            setDashboard(data);
          }
        } catch (err) {
          console.debug("Failed to load instructor dashboard:", err);
        } finally {
          if (active) setLoading(false);
        }
      }
      load();
      return () => { active = false; };
    }, []);

    const { monthlyData, monthlyLabels, distList, subList } = React.useMemo(() => {
      const md = (dashboard?.monthlyCompletionRates && dashboard.monthlyCompletionRates.length > 0)
        ? dashboard.monthlyCompletionRates : [0, 0, 0, 0, 0, 0];
      const ml = (dashboard?.monthlyLabels && dashboard.monthlyLabels.length > 0)
        ? dashboard.monthlyLabels : ["Th1", "Th2", "Th3", "Th4", "Th5", "Th6"];
      const dl = (dashboard?.courseDistributions || []).slice(0, 5);
      const sl = (dashboard?.pendingSubmissions || []).slice(0, 5);
      return { monthlyData: md, monthlyLabels: ml, distList: dl, subList: sl };
    }, [dashboard]);

    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div><h1 className="t-display">Tổng quan Giảng dạy</h1><p>Tổng quan hoạt động giảng dạy của bạn trên Rikkei Edu.</p></div>
          <button className="btn btn-primary" onClick={() => setCreate(true)}><Ic n="plus" size={17} />Tạo khóa học mới</button>
        </div>
        <window.CreateCourseModal open={create} onClose={() => setCreate(false)} onCreated={() => nav("courseDetail")} />
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="book" iconBg="#eaf1ff" iconColor="#2563eb" value={loading ? "..." : (dashboard?.activeCoursesCount ?? 0)} label="Khóa học đang dạy" sub={dashboard?.pendingCoursesCount > 0 ? `${dashboard.pendingCoursesCount} chờ phê duyệt` : "Đang hoạt động"} />
          <StatCard icon="users" iconBg="#e7f8f0" iconColor="#059669" value={loading ? "..." : (dashboard?.totalStudentsCount ?? 0)} label="Tổng học viên" />
          <StatCard icon="layers" iconBg="#f3edff" iconColor="#7c3aed" value={loading ? "..." : (dashboard?.totalGroupsCount ?? 0)} label="Nhóm đang quản lý" />
          <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value={loading ? "..." : (dashboard?.pendingSubmissionsCount ?? 0)} label="Bài tập cần chấm" sub={(dashboard?.pendingSubmissionsCount ?? 0) > 0 ? "Ưu tiên xử lý" : "Đã hoàn thành"} />
        </div>
        <div className="grid grid-3-2" style={{ marginBottom: 22 }}>
          <Section title="Tiến độ học viên theo tháng" sub="Tỷ lệ hoàn thành trung bình các khóa học">
            {loading ? (
              <div style={{ height: 250, display: "grid", placeItems: "center" }}><span className="t-sm muted">Đang tải biểu đồ...</span></div>
            ) : (
              <LineChart data={monthlyData} labels={monthlyLabels} color="#10b981" height={250} />
            )}
          </Section>
          <Section title="Phân bố học viên" sub="Theo từng khóa học">
            <div style={{ display: "grid", placeItems: "center", paddingBottom: 8 }}><Donut value={loading ? 0 : Math.round(dashboard?.averageCompletionRate ?? 0)} label="Hoàn thành TB" /></div>
            <div style={{ display: "flex", flexDirection: "column", gap: 10, marginTop: 8 }}>
              {loading ? (
                <div className="t-sm muted" style={{ textAlign: "center", padding: 12 }}>Đang tải...</div>
              ) : (!distList || distList.length === 0) ? (
                <div className="t-sm muted" style={{ textAlign: "center", padding: 12 }}>Chưa có phân bố học viên.</div>
              ) : (
                distList.map((r, i) => (
                  <div key={i} className="between"><span className="row gap-8"><span style={{width:9,height:9,borderRadius:999,background:r.color || "#2563eb"}}/><span className="t-sm truncate" style={{maxWidth: 160}}>{r.title}</span></span><b className="t-sm">{r.studentCount}</b></div>
                ))
              )}
            </div>
          </Section>
        </div>
        <Section title="Bài tập chờ chấm điểm" sub="Các bài nộp gần đây cần xử lý" action={<span className="link" onClick={() => nav("grading")}>Xem tất cả</span>} pad={false}>
          {loading ? (
            <div style={{ padding: 24, textAlign: "center" }} className="t-sm muted">Đang tải bài tập...</div>
          ) : (!subList || subList.length === 0) ? (
            <div style={{ padding: 36, textAlign: "center" }} className="t-sm muted">Chưa có bài tập nào chờ chấm điểm.</div>
          ) : (
            <div style={{ overflowX: "auto" }}><table className="tbl">
              <thead><tr><th>Học viên</th><th>Bài tập</th><th>Nhóm</th><th>Thời gian nộp</th><th>Trạng thái</th><th></th></tr></thead>
              <tbody>{subList.map(s => (
                <tr key={s.id}><td><div className="row gap-10"><Avatar name={s.studentName || "Học viên"} size={34} /><b style={{ fontSize: 13.5 }}>{s.studentName || "Học viên"}</b></div></td>
                <td className="truncate" style={{ maxWidth: 200 }}>{s.assignmentTitle || "Bài tập"}</td><td className="muted">{s.groupName || "Tự do"}</td><td className="muted">{s.submittedAt || ""}</td><td><Status s={s.status || "SUBMITTED"} /></td>
                <td><button className="btn btn-soft btn-sm" onClick={() => nav("grading")}>Chấm điểm</button></td></tr>
              ))}</tbody>
            </table></div>
          )}
        </Section>
      </div>
    );
  }


  Object.assign(window, { InsDashboard });
})();
