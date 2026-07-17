// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Báo cáo & Thống kê
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  /* ---------------- Reports ---------------- */
  function AdminReports() {
    const [q, setQ] = useState("");
    const [period, setPeriod] = useState("30");
    const teachers = [
      {n:"Lê Văn Cường",c:4,s:967,r:88,star:4.9,rev:"680 tr"},{n:"Trần Thị Bình",c:2,s:1204,r:81,star:4.7,rev:"540 tr"},
      {n:"Nguyễn Văn An",c:3,s:842,r:85,star:4.8,rev:"420 tr"},{n:"Phạm Thị Dung",c:2,s:740,r:79,star:4.7,rev:"360 tr"},
      {n:"Vũ Đức Hải",c:2,s:521,r:74,star:4.9,rev:"290 tr"},{n:"Đỗ Minh Quân",c:1,s:1089,r:72,star:4.8,rev:"260 tr"},
      {n:"Hoàng Thu Trang",c:3,s:688,r:83,star:4.8,rev:"310 tr"},{n:"Mai Văn Khánh",c:2,s:430,r:69,star:4.5,rev:"180 tr"},
      {n:"Lý Thị Ngọc",c:1,s:312,r:77,star:4.6,rev:"140 tr"},{n:"Phan Đức Tài",c:2,s:560,r:71,star:4.7,rev:"210 tr"},
    ];
    let list = teachers.filter(t => !q || t.n.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 8);
    return (
      <div className="page fade-in">
        <div className="page-head between"><div><h1 className="t-h1">Báo cáo & Thống kê</h1><p>Phân tích hiệu suất đào tạo và doanh thu toàn hệ thống.</p></div><div className="row gap-10"><Select value={period} onChange={setPeriod} options={[{v:"30",label:"30 ngày qua"},{v:"quy",label:"Quý này"},{v:"nam",label:"Năm nay"}]} style={{width:150}} /><button className="btn btn-primary"><Ic n="download" size={16} />Xuất Excel</button></div></div>
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="money" iconBg="#e7f8f0" iconColor="#059669" value="2.4 tỷ" label="Doanh thu tháng" trend={15} />
          <StatCard icon="cap" iconBg="#eaf1ff" iconColor="#2563eb" value="3,210" label="Chứng chỉ đã cấp" trend={9} />
          <StatCard icon="target" iconBg="#f3edff" iconColor="#7c3aed" value="82%" label="Tỷ lệ hoàn thành" trend={4} />
          <StatCard icon="trending" iconBg="#fef5e6" iconColor="#d97706" value="4.7/5" label="Đánh giá TB giảng viên" />
        </div>
        <div className="grid grid-2" style={{ marginBottom: 22 }}>
          <Section title="Doanh thu theo tháng" sub="8 tháng gần nhất (triệu đồng)"><BarChart data={D.charts.revenue} labels={["Th1","Th2","Th3","Th4","Th5","Th6","Th7","Th8"]} color="#10b981" height={250} unit="tr.đ" /></Section>
          <Section title="Tỷ lệ hoàn thành khóa học" sub="Xu hướng 6 tháng"><LineChart data={D.charts.completion} labels={D.charts.courseLabels} color="#2563eb" height={250} unit="%" /></Section>
        </div>
        <Section title="Hiệu suất giảng viên" sub="Top giảng viên theo số học viên & đánh giá" pad={false}
          action={<div style={{ width: 240 }}><Search placeholder="Tìm giảng viên..." value={q} onChange={setQ} /></div>}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Giảng viên</th><th>Số khóa học</th><th>Tổng học viên</th><th>Tỷ lệ hoàn thành</th><th>Đánh giá</th><th>Doanh thu</th></tr></thead>
            <tbody>{pg.slice.map((t,i)=>(
              <tr key={i}><td><div className="row gap-10"><Avatar name={t.n} size={36} /><b style={{ fontSize: 14 }}>{t.n}</b></div></td><td><b>{t.c}</b></td><td>{t.s.toLocaleString()}</td><td style={{ minWidth: 150 }}><Progress value={t.r} /></td><td><span className="row gap-5" style={{ color: "var(--warning)", fontWeight: 700 }}><Ic n="star" size={15} fill="currentColor" />{t.star}</span></td><td style={{ fontWeight: 700 }}>{t.rev}</td></tr>
            ))}</tbody>
          </table></div>
        </Section>
        <window.PageBar pg={pg} unit="giảng viên" />
      </div>
    );
  }


  Object.assign(window, { AdminReports });
})();
