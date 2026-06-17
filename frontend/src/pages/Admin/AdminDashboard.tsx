// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Tổng quan hệ thống
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  /* ---------------- Admin Dashboard ---------------- */
  function AdminDashboard({ nav }) {
    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div><h1 className="t-display">Tổng quan hệ thống</h1><p>Theo dõi và phân tích hoạt động trên nền tảng Rikkei Edu LMS.</p></div>
          <div className="row gap-10"><Select value="30" onChange={()=>{}} options={[{v:"30",label:"30 ngày qua"},{v:"7",label:"7 ngày qua"},{v:"90",label:"90 ngày qua"}]} style={{width:150}} /><button className="btn btn-primary"><Ic n="download" size={16} />Xuất báo cáo</button></div>
        </div>
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="users" iconBg="#eaf1ff" iconColor="#2563eb" value="12,450" label="Tổng học viên" sub="so với tháng trước" trend={12} />
          <StatCard icon="cap" iconBg="#f3edff" iconColor="#7c3aed" value="450" label="Tổng giảng viên" sub="đang hoạt động" />
          <StatCard icon="book" iconBg="#fef5e6" iconColor="#d97706" value="1,200" label="Khóa học hoạt động" sub="đã xuất bản" trend={5} />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value="78%" label="Tỷ lệ hoàn thành TB" sub="toàn hệ thống" />
        </div>
        <div className="grid grid-2" style={{ marginBottom: 22 }}>
          <Section title="Lượt truy cập hệ thống" action={<button className="icon-btn" style={{ width: 34, height: 34 }}><Ic n="dots" size={18} /></button>}>
            <LineChart data={D.charts.traffic} labels={D.charts.trafficLabels} color="#2563eb" height={250} />
          </Section>
          <Section title="Khóa học mới theo tháng" action={<button className="icon-btn" style={{ width: 34, height: 34 }}><Ic n="dots" size={18} /></button>}>
            <BarChart data={D.charts.newCourses} labels={D.charts.courseLabels} color="#0f172a" height={250} />
          </Section>
        </div>
        <div className="grid grid-3-2">
          <Section title="Phê duyệt khóa học" sub="Cần xử lý ngay" action={<span className="link" onClick={() => nav("approval")}>Xem tất cả</span>} pad={false}>
            <div style={{ overflowX: "auto" }}><table className="tbl">
              <thead><tr><th>Tên khóa học</th><th>Giảng viên</th><th>Ngày gửi</th><th>Trạng thái</th></tr></thead>
              <tbody>{D.approvals.filter(a => a.status === "pending").slice(0, 5).map(a => (
                <tr key={a.id} style={{ cursor: "pointer" }} onClick={() => nav("approval")}><td><b className="t-sm truncate" style={{ maxWidth: 220, display: "block" }}>{a.course}</b></td><td className="muted t-sm">{a.instructor}</td><td className="muted t-sm">{a.date}</td><td><Status s={a.status} /></td></tr>
              ))}</tbody>
            </table></div>
          </Section>
          <Section title="Nhật ký hệ thống" sub="Hoạt động gần nhất" pad={false}>
            <div style={{ padding: 12, maxHeight: 320, overflowY: "auto" }}>
              {D.activity.slice(0, 6).map((a, i) => (
                <div key={i} className="row gap-11" style={{ padding: "9px 8px" }}>
                  <div style={{ width: 30, height: 30, borderRadius: 999, flex: "none", display: "grid", placeItems: "center", background: actColor[a.type] + "18", color: actColor[a.type] }}><Ic n={actIcon[a.type]} size={15} /></div>
                  <div className="grow" style={{ minWidth: 0 }}><div className="t-sm" style={{ lineHeight: 1.4 }}><b>{a.who}</b> {a.act}</div><div className="t-xs dim" style={{ marginTop: 2 }}>{a.time}</div></div>
                </div>
              ))}
            </div>
          </Section>
        </div>
      </div>
    );
  }


  Object.assign(window, { AdminDashboard });
})();
