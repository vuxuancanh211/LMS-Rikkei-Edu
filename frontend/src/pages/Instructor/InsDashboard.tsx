// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Tổng quan (Separated Granular APIs with Retry)
   ============================================================ */
(function () {
  const { useState, useEffect, useCallback, useMemo } = React;
  const Ic = window.Icon;
  const { Avatar, Status, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  function getDynamicMonthLabels(count = 6) {
    const labels = [];
    const now = new Date();
    for (let i = count - 1; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      labels.push("Th" + (d.getMonth() + 1));
    }
    return labels;
  }

  function SectionRetryBox({ onRetry, text = "Không thể tải dữ liệu phần này" }) {
    return (
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "28px 16px", background: "var(--surface-2, #f8fafc)", borderRadius: 12, border: "1px dashed var(--border, #cbd5e1)", margin: "8px 0" }}>
        <div style={{ color: "var(--error, #ef4444)", marginBottom: 10, display: "flex", alignItems: "center", gap: 6, fontWeight: 600, fontSize: 14 }}>
          <Ic n="alert_circle" size={18} /> {text}
        </div>
        <button className="btn btn-soft btn-sm" onClick={onRetry} style={{ border: "1px solid var(--border, #cbd5e1)", display: "flex", alignItems: "center", gap: 6, cursor: "pointer" }}>
          <Ic n="refresh" size={14} /> Thử lại (Retry)
        </button>
      </div>
    );
  }

  /* ---------------- Instructor Dashboard ---------------- */
  function InsDashboard({ nav }) {
    const [create, setCreate] = useState(false);
    const [stats, setStats] = useState({ data: null, loading: true, error: false });
    const [chart, setChart] = useState({ data: null, loading: true, error: false });
    const [dist, setDist] = useState({ data: null, loading: true, error: false });
    const [sub, setSub] = useState({ data: null, loading: true, error: false });

    const loadStats = useCallback(async (force = false) => {
      setStats(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getInstructorStats) {
          res = await window.__dashboardService.getInstructorStats(force === true);
        } else if (window.httpClient) {
          const r = await window.httpClient.get('/instructor/dashboard/stats');
          res = r.data;
        }
        setStats({ data: res || {}, loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load instructor stats:", err);
        setStats(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadChart = useCallback(async (force = false) => {
      setChart(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getInstructorCompletionChart) {
          res = await window.__dashboardService.getInstructorCompletionChart(force === true);
        } else if (window.httpClient) {
          const r = await window.httpClient.get('/instructor/dashboard/completion-chart');
          res = r.data;
        }
        setChart({ data: res || {}, loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load instructor chart:", err);
        setChart(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadDist = useCallback(async (force = false) => {
      setDist(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getInstructorCourseDistributions) {
          res = await window.__dashboardService.getInstructorCourseDistributions(force === true);
        } else if (window.httpClient) {
          const r = await window.httpClient.get('/instructor/dashboard/course-distributions');
          res = r.data;
        }
        setDist({ data: res || {}, loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load instructor course distributions:", err);
        setDist(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadSub = useCallback(async (force = false) => {
      setSub(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getInstructorPendingSubmissions) {
          res = await window.__dashboardService.getInstructorPendingSubmissions(force === true);
        } else if (window.httpClient) {
          const r = await window.httpClient.get('/instructor/dashboard/pending-submissions');
          res = r.data;
        }
        setSub({ data: Array.isArray(res) ? res : [], loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load instructor pending submissions:", err);
        setSub(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    useEffect(() => {
      loadStats();
      loadChart();
      loadDist();
      loadSub();
    }, [loadStats, loadChart, loadDist, loadSub]);

    const statsData = stats.data || {};
    const chartData = chart.data || {};
    const distData = dist.data || {};
    const subList = sub.data || [];

    const { monthlyData, monthlyLabels, distList } = useMemo(() => {
      const md = (chartData.monthlyCompletionRates && chartData.monthlyCompletionRates.length > 0)
        ? chartData.monthlyCompletionRates : [0, 0, 0, 0, 0, 0];
      const ml = (chartData.monthlyLabels && chartData.monthlyLabels.length > 0)
        ? chartData.monthlyLabels : getDynamicMonthLabels(6);
      const dl = (distData.courseDistributions || []).slice(0, 5);
      return { monthlyData: md, monthlyLabels: ml, distList: dl };
    }, [chartData, distData]);

    const avgRate = distData.averageCompletionRate ?? statsData.averageCompletionRate ?? 0;

    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div><h1 className="t-display">Tổng quan Giảng dạy</h1><p>Tổng quan hoạt động giảng dạy của bạn trên Rikkei Edu.</p></div>
          <button className="btn btn-primary" onClick={() => setCreate(true)}><Ic n="plus" size={17} />Tạo khóa học mới</button>
        </div>
        <window.CreateCourseModal open={create} onClose={() => setCreate(false)} onCreated={() => nav("courseDetail", { courseId: window.__selectedCourseId || sessionStorage.getItem("selectedCourseId") })} />

        {stats.error ? (
          <SectionRetryBox onRetry={loadStats} text="Lỗi khi tải thống kê tổng quan" />
        ) : (
          <div className="grid grid-stats" style={{ marginBottom: 22 }}>
            <StatCard icon="book" iconBg="#eaf1ff" iconColor="#2563eb" value={stats.loading ? "..." : (statsData.activeCoursesCount ?? 0)} label="Khóa học đang dạy" sub={statsData.pendingCoursesCount > 0 ? `${statsData.pendingCoursesCount} chờ phê duyệt` : "Đang hoạt động"} />
            <StatCard icon="users" iconBg="#e7f8f0" iconColor="#059669" value={stats.loading ? "..." : (statsData.totalStudentsCount ?? 0)} label="Tổng học viên" />
            <StatCard icon="layers" iconBg="#f3edff" iconColor="#7c3aed" value={stats.loading ? "..." : (statsData.totalGroupsCount ?? 0)} label="Nhóm đang quản lý" />
            <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value={stats.loading ? "..." : (statsData.pendingSubmissionsCount ?? 0)} label="Bài tập cần chấm" sub={(statsData.pendingSubmissionsCount ?? 0) > 0 ? "Ưu tiên xử lý" : "Đã hoàn thành"} />
          </div>
        )}

        <div className="grid grid-3-2" style={{ marginBottom: 22 }}>
          <Section title="Tiến độ học viên theo tháng" sub="Tỷ lệ hoàn thành trung bình các khóa học">
            {chart.error ? (
              <SectionRetryBox onRetry={loadChart} text="Lỗi khi tải biểu đồ tiến độ" />
            ) : chart.loading ? (
              <div style={{ height: 250, display: "grid", placeItems: "center" }}><span className="t-sm muted">Đang tải biểu đồ...</span></div>
            ) : (
              <LineChart data={monthlyData} labels={monthlyLabels} color="#10b981" height={250} unit="%" />
            )}
          </Section>
          <Section title="Phân bố học viên" sub="Theo từng khóa học">
            {dist.error ? (
              <SectionRetryBox onRetry={loadDist} text="Lỗi khi tải phân bố học viên" />
            ) : dist.loading ? (
              <div className="t-sm muted" style={{ textAlign: "center", padding: 36 }}>Đang tải...</div>
            ) : (
              <>
                <div style={{ display: "grid", placeItems: "center", paddingBottom: 8 }}><Donut value={Math.round(avgRate)} label="Hoàn thành TB" /></div>
                <div style={{ display: "flex", flexDirection: "column", gap: 10, marginTop: 8 }}>
                  {(!distList || distList.length === 0) ? (
                    <div className="t-sm muted" style={{ textAlign: "center", padding: 12 }}>Chưa có phân bố học viên.</div>
                  ) : (
                    distList.slice(0, 5).map((r, i) => (
                      <div key={i} className="between"><span className="row gap-8"><span style={{ width: 9, height: 9, borderRadius: 999, background: r.color || "#2563eb" }} /><span className="t-sm truncate" style={{ maxWidth: 160 }}>{r.title}</span></span><b className="t-sm">{r.studentCount}</b></div>
                    ))
                  )}
                </div>
              </>
            )}
          </Section>
        </div>

        <Section title="Bài tập chờ chấm điểm" sub="Các bài nộp gần đây cần xử lý" action={<span className="link" onClick={() => nav("grading")}>Xem tất cả</span>} pad={false}>
          {sub.error ? (
            <div style={{ padding: 12 }}><SectionRetryBox onRetry={loadSub} text="Lỗi khi tải danh sách bài tập chờ chấm điểm" /></div>
          ) : sub.loading ? (
            <div style={{ height: 340, display: "grid", placeItems: "center" }} className="t-sm muted">Đang tải bài tập...</div>
          ) : (!subList || subList.length === 0) ? (
            <div style={{ height: 340, display: "grid", placeItems: "center" }} className="t-sm muted">Chưa có bài tập nào chờ chấm điểm.</div>
          ) : (
            <div style={{ height: 340, overflowX: "auto", overflowY: "auto" }}><table className="tbl">
              <thead><tr><th>Học viên</th><th>Bài tập</th><th>Nhóm</th><th>Thời gian nộp</th><th>Trạng thái</th><th></th></tr></thead>
              <tbody>{subList.slice(0, 5).map(s => (
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
