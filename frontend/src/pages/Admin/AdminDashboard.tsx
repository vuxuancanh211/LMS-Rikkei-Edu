// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Tổng quan hệ thống (Separated Granular APIs with Retry)
   ============================================================ */
(function () {
  const { useState, useEffect, useCallback, useMemo } = React;
  const Ic = window.Icon;
  const { Avatar, Status, Progress, StatCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  function getDynamicMonthLabels(count = 6) {
    const labels = [];
    const now = new Date();
    const enMonths = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    for (let i = count - 1; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      labels.push(enMonths[d.getMonth()]);
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

  /* ---------------- Admin Dashboard ---------------- */
  function AdminDashboard({ nav }) {
    const [stats, setStats] = useState({ data: null, loading: true, error: false });
    const [traffic, setTraffic] = useState({ data: null, loading: true, error: false });
    const [coursesChart, setCoursesChart] = useState({ data: null, loading: true, error: false });
    const [usersChart, setUsersChart] = useState({ data: null, loading: true, error: false });
    const [enrollmentsChart, setEnrollmentsChart] = useState({ data: null, loading: true, error: false });
    const [timeRange, setTimeRange] = useState("180");

    const loadStats = useCallback(async (force = false) => {
      setStats(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getAdminStats) {
          res = await window.__dashboardService.getAdminStats(force === true);
        } else if (window.httpClient) {
          const r = await window.httpClient.get('/admin/dashboard/stats');
          res = r.data;
        }
        setStats({ data: res || {}, loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load admin stats:", err);
        setStats(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadTraffic = useCallback(async (force = false) => {
      setTraffic(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getAdminTrafficChart) {
          res = await window.__dashboardService.getAdminTrafficChart(force === true);
        } else if (window.httpClient) {
          const r = await window.httpClient.get('/admin/dashboard/traffic');
          res = r.data;
        }
        setTraffic({ data: res || {}, loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load admin traffic chart:", err);
        setTraffic(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadCoursesChart = useCallback(async (force = false) => {
      setCoursesChart(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getAdminCoursesChart) {
          res = await window.__dashboardService.getAdminCoursesChart(force === true);
        } else if (window.httpClient) {
          const r = await window.httpClient.get('/admin/dashboard/courses-chart');
          res = r.data;
        }
        setCoursesChart({ data: res || {}, loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load admin courses chart:", err);
        setCoursesChart(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadUsersChart = useCallback(async (force = false) => {
      setUsersChart(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getAdminUsersChart) {
          res = await window.__dashboardService.getAdminUsersChart(force === true);
        } else if (window.httpClient) {
          const r = await window.httpClient.get('/admin/dashboard/users-chart');
          res = r.data;
        }
        setUsersChart({ data: res || {}, loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load admin users chart:", err);
        setUsersChart(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadEnrollmentsChart = useCallback(async (force = false) => {
      setEnrollmentsChart(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getAdminEnrollmentsChart) {
          res = await window.__dashboardService.getAdminEnrollmentsChart(force === true);
        } else if (window.httpClient) {
          const r = await window.httpClient.get('/admin/dashboard/enrollments-chart');
          res = r.data;
        }
        setEnrollmentsChart({ data: res || {}, loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load admin enrollments chart:", err);
        setEnrollmentsChart(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    useEffect(() => {
      loadStats();
      loadTraffic();
      loadCoursesChart();
      loadUsersChart();
      loadEnrollmentsChart();
    }, [loadStats, loadTraffic, loadCoursesChart, loadUsersChart, loadEnrollmentsChart]);

    const { trafficData, trafficLabels, courseData, courseLabels, usersData, usersLabels, enrollmentsData, enrollmentsLabels } = useMemo(() => {
      let td, tl, cd, cl, ud, ul, ed, el;
      const tfData = traffic.data || {};
      const cChartData = coursesChart.data || {};
      const uChartData = usersChart.data || {};
      const eChartData = enrollmentsChart.data || {};

      if (timeRange === "7") {
        td = (tfData.weeklyTrafficData && tfData.weeklyTrafficData.length > 0) ? tfData.weeklyTrafficData : [0, 0, 0, 0, 0, 0, 0];
        tl = (tfData.weeklyTrafficLabels && tfData.weeklyTrafficLabels.length > 0) ? tfData.weeklyTrafficLabels : ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
        cd = (cChartData.weeklyCoursesData && cChartData.weeklyCoursesData.length > 0) ? cChartData.weeklyCoursesData : [0, 0, 0, 0, 0, 0, 0];
        cl = (cChartData.weeklyCoursesLabels && cChartData.weeklyCoursesLabels.length > 0) ? cChartData.weeklyCoursesLabels : ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
        ud = (uChartData.weeklyUsersData && uChartData.weeklyUsersData.length > 0) ? uChartData.weeklyUsersData : [0, 0, 0, 0, 0, 0, 0];
        ul = (uChartData.weeklyUsersLabels && uChartData.weeklyUsersLabels.length > 0) ? uChartData.weeklyUsersLabels : ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
        ed = (eChartData.weeklyEnrollmentsData && eChartData.weeklyEnrollmentsData.length > 0) ? eChartData.weeklyEnrollmentsData : [0, 0, 0, 0, 0, 0, 0];
        el = (eChartData.weeklyEnrollmentsLabels && eChartData.weeklyEnrollmentsLabels.length > 0) ? eChartData.weeklyEnrollmentsLabels : ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
      } else {
        const dynamicLabels = getDynamicMonthLabels(6);
        const allTd = (tfData.trafficData && tfData.trafficData.length > 0) ? tfData.trafficData : [0, 0, 0, 0, 0, 0];
        const allTl = (tfData.trafficLabels && tfData.trafficLabels.length > 0) ? tfData.trafficLabels : dynamicLabels;
        const allCd = (cChartData.newCoursesData && cChartData.newCoursesData.length > 0) ? cChartData.newCoursesData : [0, 0, 0, 0, 0, 0];
        const allCl = (cChartData.newCoursesLabels && cChartData.newCoursesLabels.length > 0) ? cChartData.newCoursesLabels : dynamicLabels;
        const allUd = (uChartData.newUsersData && uChartData.newUsersData.length > 0) ? uChartData.newUsersData : [0, 0, 0, 0, 0, 0];
        const allUl = (uChartData.newUsersLabels && uChartData.newUsersLabels.length > 0) ? uChartData.newUsersLabels : dynamicLabels;
        const allEd = (eChartData.enrollmentsData && eChartData.enrollmentsData.length > 0) ? eChartData.enrollmentsData : [0, 0, 0, 0, 0, 0];
        const allEl = (eChartData.enrollmentsLabels && eChartData.enrollmentsLabels.length > 0) ? eChartData.enrollmentsLabels : dynamicLabels;
        const count = timeRange === "90" ? 3 : 6;
        td = allTd.slice(-count);
        tl = allTl.slice(-count);
        cd = allCd.slice(-count);
        cl = allCl.slice(-count);
        ud = allUd.slice(-count);
        ul = allUl.slice(-count);
        ed = allEd.slice(-count);
        el = allEl.slice(-count);
      }

      return { trafficData: td, trafficLabels: tl, courseData: cd, courseLabels: cl, usersData: ud, usersLabels: ul, enrollmentsData: ed, enrollmentsLabels: el };
    }, [traffic.data, coursesChart.data, usersChart.data, enrollmentsChart.data, timeRange]);

    const handleExport = () => {
      const sData = stats.data || {};
      const stuCount = sData.totalStudentsCount ?? 0;
      const insCount = sData.totalInstructorsCount ?? 0;
      const courseCount = sData.activeCoursesCount ?? 0;
      const avgRate = sData.averageCompletionRate ?? 0;
      
      const timeLabel = timeRange === "7" ? "7 ngày qua" : timeRange === "90" ? "3 tháng qua" : "6 tháng qua";
      const nowStr = new Date().toLocaleString('vi-VN');

      let rowsHtml = "";
      let sumTraffic = 0, sumCourses = 0, sumUsers = 0, sumEnrollments = 0;
      for (let i = 0; i < trafficLabels.length; i++) {
        const tVal = Number(trafficData[i] || 0);
        const cVal = Number(courseData[i] || 0);
        const uVal = Number(usersData[i] || 0);
        const eVal = Number(enrollmentsData[i] || 0);
        sumTraffic += tVal;
        sumCourses += cVal;
        sumUsers += uVal;
        sumEnrollments += eVal;

        rowsHtml += `
          <tr>
            <td style="border: 1px solid #cbd5e1; padding: 8px; text-align: center;">${trafficLabels[i] || ""}</td>
            <td style="border: 1px solid #cbd5e1; padding: 8px; text-align: right; mso-number-format:'\\@';">${tVal}</td>
            <td style="border: 1px solid #cbd5e1; padding: 8px; text-align: right; mso-number-format:'\\@';">${cVal}</td>
            <td style="border: 1px solid #cbd5e1; padding: 8px; text-align: right; mso-number-format:'\\@';">${uVal}</td>
            <td style="border: 1px solid #cbd5e1; padding: 8px; text-align: right; mso-number-format:'\\@';">${eVal}</td>
          </tr>
        `;
      }

      const excelHtml = `<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
<head>
<meta charset="utf-8">
<!--[if gte mso 9]><xml><x:ExcelWorkbook><x:ExcelWorksheets><x:ExcelWorksheet><x:Name>BaoCaoAdmin</x:Name><x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions></x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml><![endif]-->
<style>
  table { border-collapse: collapse; font-family: 'Segoe UI', Arial, sans-serif; font-size: 13px; }
  th { background-color: #1e3a8a; color: #ffffff; font-weight: bold; border: 1px solid #cbd5e1; padding: 10px; text-align: center; }
  td { border: 1px solid #cbd5e1; padding: 8px; }
  .title { font-size: 18px; font-weight: bold; color: #1e3a8a; text-align: center; }
  .meta { font-size: 12px; font-style: italic; color: #475569; }
  .section-title { font-size: 14px; font-weight: bold; background-color: #e2e8f0; color: #0f172a; padding: 8px; }
  .total-row { font-weight: bold; background-color: #f1f5f9; }
</style>
</head>
<body>
  <table>
    <tr><td colspan="5" class="title" style="border: none; font-size: 18px; font-weight: bold; color: #1e3a8a; padding: 12px 0;">BÁO CÁO TỔNG QUAN HỆ THỐNG RIKKEI EDU LMS</td></tr>
    <tr><td colspan="5" class="meta" style="border: none; font-style: italic; color: #475569;">Khoảng thời gian thống kê: ${timeLabel}</td></tr>
    <tr><td colspan="5" class="meta" style="border: none; font-style: italic; color: #475569;">Thời gian xuất báo cáo: ${nowStr}</td></tr>
    <tr><td colspan="5" style="border: none; height: 10px;"></td></tr>

    <tr><td colspan="5" class="section-title" style="background-color: #e2e8f0; font-weight: bold; padding: 8px;">1. CHỈ SỐ TỔNG QUAN HỆ THỐNG</td></tr>
    <tr>
      <th style="background-color: #1e3a8a; color: #ffffff; padding: 10px;">Chỉ tiêu thống kê</th>
      <th style="background-color: #1e3a8a; color: #ffffff; padding: 10px;">Giá trị hiện tại</th>
      <th style="background-color: #1e3a8a; color: #ffffff; padding: 10px;">Đơn vị tính</th>
      <th colspan="2" style="background-color: #1e3a8a; color: #ffffff; padding: 10px;">Ghi chú</th>
    </tr>
    <tr>
      <td>Tổng học viên đang học</td>
      <td style="text-align: right; font-weight: bold;">${stuCount}</td>
      <td style="text-align: center;">Học viên</td>
      <td colspan="2">Số tài khoản học viên đang kích hoạt trên hệ thống</td>
    </tr>
    <tr>
      <td>Tổng giảng viên giảng dạy</td>
      <td style="text-align: right; font-weight: bold;">${insCount}</td>
      <td style="text-align: center;">Giảng viên</td>
      <td colspan="2">Số tài khoản giảng viên đang quản lý và đào tạo</td>
    </tr>
    <tr>
      <td>Khóa học đang hoạt động</td>
      <td style="text-align: right; font-weight: bold;">${courseCount}</td>
      <td style="text-align: center;">Khóa học</td>
      <td colspan="2">Các khóa học đã phát hành (PUBLISHED)</td>
    </tr>
    <tr>
      <td>Tỷ lệ hoàn thành trung bình</td>
      <td style="text-align: right; font-weight: bold;">${avgRate}%</td>
      <td style="text-align: center;">%</td>
      <td colspan="2">Tỷ lệ hoàn thành tiến độ học tập trung bình toàn hệ thống</td>
    </tr>
    <tr><td colspan="5" style="border: none; height: 15px;"></td></tr>

    <tr><td colspan="5" class="section-title" style="background-color: #e2e8f0; font-weight: bold; padding: 8px;">2. CHI TIẾT BIẾN ĐỘNG THEO THỜI GIAN (${timeLabel.toUpperCase()})</td></tr>
    <tr>
      <th style="background-color: #1e3a8a; color: #ffffff; padding: 10px;">Mốc thời gian</th>
      <th style="background-color: #1e3a8a; color: #ffffff; padding: 10px;">Lượt học tập (Giờ)</th>
      <th style="background-color: #1e3a8a; color: #ffffff; padding: 10px;">Khóa học mới (Khóa)</th>
      <th style="background-color: #1e3a8a; color: #ffffff; padding: 10px;">Người dùng mới (Học viên)</th>
      <th style="background-color: #1e3a8a; color: #ffffff; padding: 10px;">Lượt ghi danh mới (Lượt)</th>
    </tr>
    ${rowsHtml}
    <tr class="total-row" style="background-color: #f1f5f9; font-weight: bold;">
      <td style="text-align: center; font-weight: bold;">TỔNG CỘNG</td>
      <td style="text-align: right; font-weight: bold;">${sumTraffic}</td>
      <td style="text-align: right; font-weight: bold;">${sumCourses}</td>
      <td style="text-align: right; font-weight: bold;">${sumUsers}</td>
      <td style="text-align: right; font-weight: bold;">${sumEnrollments}</td>
    </tr>
  </table>
</body>
</html>`;

      const blob = new Blob([excelHtml], { type: "application/vnd.ms-excel;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.setAttribute("href", url);
      link.setAttribute("download", `bao_cao_tong_quan_rikkei_${timeRange}ngay_${Date.now()}.xls`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    };

    const statsData = stats.data || {};

    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div><h1 className="t-display">Tổng quan hệ thống</h1><p>Theo dõi và phân tích hoạt động trên nền tảng Rikkei Edu LMS.</p></div>
          <div className="row gap-10">
            <Select value={timeRange} onChange={setTimeRange} options={[{v:"180",label:"6 tháng qua"},{v:"90",label:"3 tháng qua"},{v:"7",label:"7 ngày qua"}]} style={{width:150}} />
            <button className="btn btn-primary" onClick={handleExport}><Ic n="download" size={16} />Xuất báo cáo</button>
          </div>
        </div>

        {stats.error ? (
          <SectionRetryBox onRetry={loadStats} text="Lỗi khi tải thông số tổng quan" />
        ) : (
          <div className="grid grid-stats" style={{ marginBottom: 22 }}>
            <StatCard icon="users" iconBg="#eaf1ff" iconColor="#2563eb" value={stats.loading ? "..." : String(statsData.totalStudentsCount ?? 0)} label="Tổng học viên" sub="đang học" />
            <StatCard icon="cap" iconBg="#f3edff" iconColor="#7c3aed" value={stats.loading ? "..." : String(statsData.totalInstructorsCount ?? 0)} label="Tổng giảng viên" sub="đang giảng dạy" />
            <StatCard icon="book" iconBg="#fef5e6" iconColor="#d97706" value={stats.loading ? "..." : String(statsData.activeCoursesCount ?? 0)} label="Khóa học hoạt động" sub="đã xuất bản" />
            <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={stats.loading ? "..." : `${Math.round(statsData.averageCompletionRate ?? 0)}%`} label="Tỷ lệ hoàn thành TB" sub="toàn hệ thống" />
          </div>
        )}

        <div className="grid grid-2" style={{ marginBottom: 22 }}>
          <Section 
            title={timeRange === "7" ? "Hoạt động học tập (7 ngày qua)" : timeRange === "90" ? "Hoạt động học tập (3 tháng qua)" : "Hoạt động học tập (6 tháng qua)"} 
            sub={timeRange === "7" ? "Lượt học viên truy cập bài giảng theo từng ngày" : "Lượt học viên truy cập bài giảng theo từng tháng"}
          >
            {traffic.error ? (
              <SectionRetryBox onRetry={loadTraffic} text="Lỗi khi tải biểu đồ truy cập" />
            ) : traffic.loading ? (
              <div style={{ height: 250, display: "grid", placeItems: "center" }}><span className="t-sm dim">Đang tải biểu đồ...</span></div>
            ) : (
              <LineChart data={trafficData} labels={trafficLabels} color="#2563eb" height={250} unit="lượt" />
            )}
          </Section>
          <Section 
            title={timeRange === "7" ? "Khóa học mới (7 ngày qua)" : timeRange === "90" ? "Khóa học mới (3 tháng qua)" : "Khóa học mới (6 tháng qua)"} 
            sub={timeRange === "7" ? "Số lượng khóa học được tạo mới theo từng ngày" : "Số lượng khóa học được tạo mới theo từng tháng"}
          >
            {coursesChart.error ? (
              <SectionRetryBox onRetry={loadCoursesChart} text="Lỗi khi tải biểu đồ khóa học" />
            ) : coursesChart.loading ? (
              <div style={{ height: 250, display: "grid", placeItems: "center" }}><span className="t-sm dim">Đang tải biểu đồ...</span></div>
            ) : (
              <BarChart data={courseData} labels={courseLabels} color="#0f172a" height={250} unit="khóa học" />
            )}
          </Section>
        </div>

        <div className="grid grid-2" style={{ marginBottom: 22 }}>
          <Section 
            title={timeRange === "7" ? "Người dùng mới (7 ngày qua)" : timeRange === "90" ? "Người dùng mới (3 tháng qua)" : "Người dùng mới (6 tháng qua)"} 
            sub={timeRange === "7" ? "Số lượng tài khoản đăng ký mới theo từng ngày" : "Số lượng tài khoản đăng ký mới theo từng tháng"}
          >
            {usersChart.error ? (
              <SectionRetryBox onRetry={loadUsersChart} text="Lỗi khi tải biểu đồ người dùng" />
            ) : usersChart.loading ? (
              <div style={{ height: 250, display: "grid", placeItems: "center" }}><span className="t-sm dim">Đang tải biểu đồ...</span></div>
            ) : (
              <BarChart data={usersData} labels={usersLabels} color="#10b981" height={250} unit="người" />
            )}
          </Section>
          <Section 
            title={timeRange === "7" ? "Lượt ghi danh (7 ngày qua)" : timeRange === "90" ? "Lượt ghi danh (3 tháng qua)" : "Lượt ghi danh (6 tháng qua)"} 
            sub={timeRange === "7" ? "Số lượt đăng ký tham gia khóa học theo từng ngày" : "Số lượt đăng ký tham gia khóa học theo từng tháng"}
          >
            {enrollmentsChart.error ? (
              <SectionRetryBox onRetry={loadEnrollmentsChart} text="Lỗi khi tải biểu đồ lượt ghi danh" />
            ) : enrollmentsChart.loading ? (
              <div style={{ height: 250, display: "grid", placeItems: "center" }}><span className="t-sm dim">Đang tải biểu đồ...</span></div>
            ) : (
              <LineChart data={enrollmentsData} labels={enrollmentsLabels} color="#7c3aed" height={250} unit="lượt" />
            )}
          </Section>
        </div>
      </div>
    );
  }

  Object.assign(window, { AdminDashboard });
})();
