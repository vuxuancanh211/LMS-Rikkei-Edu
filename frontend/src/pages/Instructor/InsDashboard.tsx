// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Tổng quan
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const myCourses = D.courses.filter(c => ["Nguyễn Văn An", "Trần Thị Bình", "Lê Văn Cường", "Phạm Thị Dung"].includes(c.instructor)).slice(0, 9);

  /* ---------------- Instructor Dashboard ---------------- */
  function InsDashboard({ nav }) {
    const [create, setCreate] = useState(false);
    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div><h1 className="t-display">Bảng điều khiển Giảng viên</h1><p>Tổng quan hoạt động giảng dạy của bạn trên Rikkei Edu.</p></div>
          <button className="btn btn-primary" onClick={() => setCreate(true)}><Ic n="plus" size={17} />Tạo khóa học mới</button>
        </div>
        <window.CreateCourseModal open={create} onClose={() => setCreate(false)} onCreated={() => nav("courseDetail")} />
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="book" iconBg="#eaf1ff" iconColor="#2563eb" value="6" label="Khóa học đang dạy" sub="2 chờ phê duyệt" />
          <StatCard icon="users" iconBg="#e7f8f0" iconColor="#059669" value="1,248" label="Tổng học viên" trend={8} />
          <StatCard icon="layers" iconBg="#f3edff" iconColor="#7c3aed" value="8" label="Nhóm đang quản lý" />
          <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value="32" label="Bài tập cần chấm" sub="Ưu tiên xử lý" />
        </div>
        <div className="grid grid-3-2" style={{ marginBottom: 22 }}>
          <Section title="Tiến độ học viên theo tháng" sub="Tỷ lệ hoàn thành trung bình các khóa học">
            <LineChart data={D.charts.completion} labels={D.charts.courseLabels} color="#10b981" height={250} />
          </Section>
          <Section title="Phân bố học viên" sub="Theo từng khóa học">
            <div style={{ display: "grid", placeItems: "center", paddingBottom: 8 }}><Donut value={78} label="Hoàn thành TB" /></div>
            <div style={{ display: "flex", flexDirection: "column", gap: 10, marginTop: 8 }}>
              {[{t:"ReactJS Nâng cao",n:842,c:"#2563eb"},{t:"NodeJS & Express",n:1204,c:"#10b981"},{t:"Spring Boot",n:967,c:"#f59e0b"}].map((r,i)=>(
                <div key={i} className="between"><span className="row gap-8"><span style={{width:9,height:9,borderRadius:999,background:r.c}}/><span className="t-sm">{r.t}</span></span><b className="t-sm">{r.n}</b></div>
              ))}
            </div>
          </Section>
        </div>
        <Section title="Bài tập chờ chấm điểm" sub="Các bài nộp gần đây cần xử lý" action={<span className="link" onClick={() => nav("grading")}>Xem tất cả</span>} pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Học viên</th><th>Bài tập</th><th>Nhóm</th><th>Thời gian nộp</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>{D.submissions.slice(0, 5).map(s => (
              <tr key={s.id}><td><div className="row gap-10"><Avatar name={s.student} size={34} /><b style={{ fontSize: 13.5 }}>{s.student}</b></div></td>
              <td className="truncate" style={{ maxWidth: 200 }}>{s.assignment}</td><td className="muted">{s.group}</td><td className="muted">{s.submitted}</td><td><Status s={s.status} /></td>
              <td><button className="btn btn-soft btn-sm" onClick={() => nav("grading")}>Chấm điểm</button></td></tr>
            ))}</tbody>
          </table></div>
        </Section>
      </div>
    );
  }


  Object.assign(window, { InsDashboard });
})();
