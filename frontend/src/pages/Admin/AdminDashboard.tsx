// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Tổng quan hệ thống
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon;
  const { Avatar, Status, Progress, StatCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  /* ---------------- Admin Dashboard ---------------- */
  function AdminDashboard({ nav }) {
    const [dashboard, setDashboard] = useState(null);
    const [loading, setLoading] = useState(true);
    const [timeRange, setTimeRange] = useState("180");

    React.useEffect(() => {
      let active = true;
      async function load() {
        setLoading(true);
        try {
          let data;
          if (window.__dashboardService?.getAdminDashboard) {
            data = await window.__dashboardService.getAdminDashboard();
          } else if (window.httpClient) {
            const res = await window.httpClient.get('/admin/dashboard');
            data = res.data;
          }
          if (active && data) {
            setDashboard(data);
          }
        } catch (err) {
          console.debug("Failed to load admin dashboard:", err);
        } finally {
          if (active) setLoading(false);
        }
      }
      load();
      return () => { active = false; };
    }, []);

    const { trafficData, trafficLabels, courseData, courseLabels, approvalsList, activitiesList } = React.useMemo(() => {
      let td, tl, cd, cl;
      if (timeRange === "7") {
        td = (dashboard?.weeklyTrafficData && dashboard.weeklyTrafficData.length > 0) ? dashboard.weeklyTrafficData : [0, 0, 0, 0, 0, 0, 0];
        tl = (dashboard?.weeklyTrafficLabels && dashboard.weeklyTrafficLabels.length > 0) ? dashboard.weeklyTrafficLabels : ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];
        cd = (dashboard?.weeklyCoursesData && dashboard.weeklyCoursesData.length > 0) ? dashboard.weeklyCoursesData : [0, 0, 0, 0, 0, 0, 0];
        cl = (dashboard?.weeklyCoursesLabels && dashboard.weeklyCoursesLabels.length > 0) ? dashboard.weeklyCoursesLabels : ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];
      } else {
        const allTd = (dashboard?.trafficData && dashboard.trafficData.length > 0) ? dashboard.trafficData : [0, 0, 0, 0, 0, 0];
        const allTl = (dashboard?.trafficLabels && dashboard.trafficLabels.length > 0) ? dashboard.trafficLabels : ["Th2", "Th3", "Th4", "Th5", "Th6", "Th7"];
        const allCd = (dashboard?.newCoursesData && dashboard.newCoursesData.length > 0) ? dashboard.newCoursesData : [0, 0, 0, 0, 0, 0];
        const allCl = (dashboard?.newCoursesLabels && dashboard.newCoursesLabels.length > 0) ? dashboard.newCoursesLabels : ["Th2", "Th3", "Th4", "Th5", "Th6", "Th7"];
        const count = timeRange === "90" ? 3 : 6;
        td = allTd.slice(-count);
        tl = allTl.slice(-count);
        cd = allCd.slice(-count);
        cl = allCl.slice(-count);
      }

      const apprs = (dashboard?.pendingApprovals || []).slice(0, 5);
      const acts = (dashboard?.recentActivities || []).slice(0, 6);
      return { trafficData: td, trafficLabels: tl, courseData: cd, courseLabels: cl, approvalsList: apprs, activitiesList: acts };
    }, [dashboard, timeRange]);

    const handleExport = () => {
      const stuCount = dashboard?.totalStudentsCount ?? 0;
      const insCount = dashboard?.totalInstructorsCount ?? 0;
      const courseCount = dashboard?.activeCoursesCount ?? 0;
      const avgRate = dashboard?.averageCompletionRate ?? 0;
      
      const csvContent = "data:text/csv;charset=utf-8,\uFEFF"
        + "Chi tieu,Gia tri\n"
        + `Tong hoc vien,${stuCount}\n`
        + `Tong giang vien,${insCount}\n`
        + `Khoa hoc hoat dong,${courseCount}\n`
        + `Ty le hoan thanh trung binh,${avgRate}%\n`
        + `Thoi gian xuat,${new Date().toLocaleString('vi-VN')}\n`;
        
      const encodedUri = encodeURI(csvContent);
      const link = document.createElement("a");
      link.setAttribute("href", encodedUri);
      link.setAttribute("download", `bao_cao_tong_quan_rikkei_${Date.now()}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    };

    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div><h1 className="t-display">Tổng quan hệ thống</h1><p>Theo dõi và phân tích hoạt động trên nền tảng Rikkei Edu LMS.</p></div>
          <div className="row gap-10">
            <Select value={timeRange} onChange={setTimeRange} options={[{v:"180",label:"6 tháng qua"},{v:"90",label:"3 tháng qua"},{v:"7",label:"7 ngày qua"}]} style={{width:150}} />
            <button className="btn btn-primary" onClick={handleExport}><Ic n="download" size={16} />Xuất báo cáo</button>
          </div>
        </div>
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="users" iconBg="#eaf1ff" iconColor="#2563eb" value={loading ? "..." : String(dashboard?.totalStudentsCount ?? 0)} label="Tổng học viên" sub="đang học" />
          <StatCard icon="cap" iconBg="#f3edff" iconColor="#7c3aed" value={loading ? "..." : String(dashboard?.totalInstructorsCount ?? 0)} label="Tổng giảng viên" sub="đang giảng dạy" />
          <StatCard icon="book" iconBg="#fef5e6" iconColor="#d97706" value={loading ? "..." : String(dashboard?.activeCoursesCount ?? 0)} label="Khóa học hoạt động" sub="đã xuất bản" />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={loading ? "..." : `${Math.round(dashboard?.averageCompletionRate ?? 0)}%`} label="Tỷ lệ hoàn thành TB" sub="toàn hệ thống" />
        </div>
        <div className="grid grid-2" style={{ marginBottom: 22 }}>
          <Section title="Hoạt động học tập theo tháng" sub="Lượt học viên truy cập bài giảng">
            {loading ? (
              <div style={{ height: 250, display: "grid", placeItems: "center" }}><span className="t-sm dim">Đang tải biểu đồ...</span></div>
            ) : (
              <LineChart data={trafficData} labels={trafficLabels} color="#2563eb" height={250} />
            )}
          </Section>
          <Section title="Khóa học mới theo tháng" sub="Số lượng khóa học được tạo mới">
            {loading ? (
              <div style={{ height: 250, display: "grid", placeItems: "center" }}><span className="t-sm dim">Đang tải biểu đồ...</span></div>
            ) : (
              <BarChart data={courseData} labels={courseLabels} color="#0f172a" height={250} />
            )}
          </Section>
        </div>
        <div className="grid grid-3-2">
          <Section title="Phê duyệt khóa học" sub="Cần xử lý" action={<span className="link" onClick={() => nav("approval")}>Xem tất cả</span>} pad={false}>
            {loading ? (
              <div style={{ padding: 24, textAlign: "center" }} className="t-sm dim">Đang tải danh sách...</div>
            ) : (!approvalsList || approvalsList.length === 0) ? (
              <div style={{ padding: 36, textAlign: "center" }} className="t-sm dim">Chưa có khóa học nào chờ phê duyệt.</div>
            ) : (
              <div style={{ overflowX: "auto" }}><table className="tbl">
                <thead><tr><th>Tên khóa học</th><th>Giảng viên</th><th>Ngày gửi</th><th>Trạng thái</th></tr></thead>
                <tbody>{approvalsList.map(a => (
                  <tr key={a.id} style={{ cursor: "pointer" }} onClick={() => nav("approval")}><td><b className="t-sm truncate" style={{ maxWidth: 220, display: "block" }}>{a.courseName || a.course || "Khóa học"}</b></td><td className="dim t-sm">{a.instructorName || a.instructor || "Giảng viên"}</td><td className="dim t-sm">{a.submittedDate || a.date || ""}</td><td><Status s={a.status || "PENDING"} /></td></tr>
                ))}</tbody>
              </table></div>
            )}
          </Section>
          <Section title="Nhật ký hệ thống" sub="Hoạt động gần nhất" pad={false}>
            <div style={{ padding: 12, maxHeight: 320, overflowY: "auto" }}>
              {loading ? (
                <div style={{ padding: 24, textAlign: "center" }} className="t-sm dim">Đang tải nhật ký...</div>
              ) : (!activitiesList || activitiesList.length === 0) ? (
                <div style={{ padding: 36, textAlign: "center" }} className="t-sm dim">Chưa có hoạt động hệ thống nào.</div>
              ) : (
                activitiesList.map((a, i) => (
                  <div key={i} className="row gap-11" style={{ padding: "9px 8px" }}>
                    <div style={{ width: 30, height: 30, borderRadius: 999, flex: "none", display: "grid", placeItems: "center", background: (actColor[a.type] || "#2563eb") + "18", color: actColor[a.type] || "#2563eb" }}><Ic n={actIcon[a.type] || "check_circle"} size={15} /></div>
                    <div className="grow" style={{ minWidth: 0 }}><div className="t-sm" style={{ lineHeight: 1.4 }}><b>{a.who}</b> {a.act}</div><div className="t-xs dim" style={{ marginTop: 2 }}>{a.time}</div></div>
                  </div>
                ))
              )}
            </div>
          </Section>
        </div>
      </div>
    );
  }


  Object.assign(window, { AdminDashboard });
})();
