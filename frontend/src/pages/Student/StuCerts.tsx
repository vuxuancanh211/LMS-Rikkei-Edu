/* ============================================================
   RIKKEI EDU — StuCerts
   ============================================================ */
(function () {
const { useState: uS } = React;
// @ts-nocheck
const Ic = window.Icon;
const D = window.DATA;
const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

/* ---------------- Certificates ---------------- */
function StuCerts() {
  const [q, setQ] = uS("");
  let list = D.certificates.filter(c => !q || c.course.toLowerCase().includes(q.toLowerCase()));
  const pg = window.usePaged(list, 8);
  return (
    <div className="page fade-in">
      <div className="page-head"><h1 className="t-h1">Chứng chỉ của tôi</h1><p>Chúc mừng bạn! Đây là thành quả cho những nỗ lực học tập của bạn.</p></div>
      <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns:"repeat(3,1fr)" }}>
        <SC icon="award" iconBg="#eaf1ff" iconColor="#2563eb" value="12" label="Tổng số chứng chỉ" />
        <SC icon="trending" iconBg="#e7f8f0" iconColor="#059669" value="2" label="Hoàn thành trong tháng" />
        <SC icon="star" iconBg="#fef5e6" iconColor="#d97706" value="Top 5%" label="Xếp hạng học viên" />
      </div>
      <div className="toolbar">
        <Se placeholder="Tìm chứng chỉ theo tên khóa học..." value={q} onChange={setQ} />
        <Sl value="all" onChange={()=>{}} options={[{v:"all",label:"Tất cả xếp loại"},{v:"xs",label:"Xuất sắc"},{v:"g",label:"Giỏi"},{v:"k",label:"Khá"}]} style={{width:180,flex:"none"}} />
        <Sl value="new" onChange={()=>{}} options={[{v:"new",label:"Mới nhất"},{v:"az",label:"Tên A-Z"}]} style={{width:150,flex:"none"}} />
      </div>
      <div className="grid grid-cards">
        {pg.slice.map(ct => (
          <div key={ct.id} className="card fade-in" style={{ overflow: "hidden" }}>
            <div style={{ padding: 18, background: "linear-gradient(135deg,#0f172a,#1e293b)", position: "relative" }}>
              <div style={{ border: "1.5px solid rgba(255,255,255,.18)", borderRadius: 12, padding: "22px 18px", textAlign: "center", position: "relative" }}>
                <div style={{ width: 46, height: 46, borderRadius: 12, background: "#fff", display: "grid", placeItems: "center", margin: "0 auto 12px", fontWeight: 800, fontSize: 22, color: "#0f172a" }}>R</div>
                <div style={{ height: 5, width: "70%", background: "rgba(255,255,255,.22)", borderRadius: 9, margin: "0 auto 7px" }} />
                <div style={{ height: 5, width: "45%", background: "rgba(255,255,255,.14)", borderRadius: 9, margin: "0 auto" }} />
                <div style={{ position: "absolute", right: 12, bottom: 10, width: 30, height: 30, borderRadius: 999, background: "var(--warning)", display: "grid", placeItems: "center", color: "#fff" }}><Ic n="award" size={16} /></div>
              </div>
            </div>
            <div style={{ padding: 18 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }} className="clamp-2">{ct.course}</h3>
              <div className="t-sm muted row gap-6" style={{ marginTop: 10 }}><Ic n="calendar" size={14} />Cấp ngày: {ct.date}</div>
              <div className="t-sm muted row gap-6" style={{ marginTop: 5 }}><Ic n="finger" size={14} />ID: <span className="mono" style={{ color: "var(--text)", fontWeight: 600 }}>{ct.id}</span></div>
              <div className="row gap-10" style={{ marginTop: 16 }}>
                <button className="btn btn-ghost btn-sm grow">Xem chi tiết</button>
                <button className="btn btn-primary btn-sm grow"><Ic n="download" size={15} />Tải PDF</button>
              </div>
            </div>
          </div>
        ))}
      </div>
      <window.PageBar pg={pg} unit="chứng chỉ" />
    </div>
  );
}

window.StuCerts = StuCerts;
})();
