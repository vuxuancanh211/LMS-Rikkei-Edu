// @ts-nocheck
/* ============================================================
   RIKKEI EDU — StuDashboard
   ============================================================ */
(function () {
const { useState: uS } = React;
const Ic = window.Icon;
const D = window.DATA;
const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

/* ---------------- Student Dashboard ---------------- */
function StuDashboard({ nav }) {
  const inProgress = D.courses.filter(c => c.sStatus === "learning").slice(0, 3);
  const due = D.assignments.filter(a => a.status === "pending" || a.status === "late").slice(0, 4);
  return (
    <div className="page fade-in">
      <div className="page-head">
        <h1 className="t-display">Chào mừng trở lại, Văn Em 👋</h1>
        <p>Hôm nay là ngày tốt để tiếp tục hành trình học tập của bạn.</p>
      </div>
      <div className="grid grid-stats" style={{ marginBottom: 22 }}>
        <SC icon="cap" iconBg="#eaf1ff" iconColor="#2563eb" value="5" label="Khóa học đang học" sub="2 sắp hoàn thành" trend={null} />
        <SC icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value="6" label="Bài tập cần làm" sub="2 sắp đến hạn" />
        <SC icon="award" iconBg="#e7f8f0" iconColor="#059669" value="12" label="Chứng chỉ đạt được" trend={null} />
        <SC icon="clock" iconBg="#f3edff" iconColor="#7c3aed" value="18.5h" label="Giờ học tuần này" trend={12} />
      </div>

      <div className="grid grid-3-2" style={{ marginBottom: 22 }}>
        <Sn title="Tiếp tục học" sub="Các khóa học bạn đang theo dõi" action={<span className="link" onClick={() => nav("courses")}>Xem tất cả</span>} pad={false}>
          <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 12 }}>
            {inProgress.map(c => (
              <div key={c.id} className="row gap-16" style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 14, cursor: "pointer" }} onClick={() => nav("player")}>
                <div style={{ width: 92, height: 64, borderRadius: 10, backgroundImage: `url(${c.thumb})`, backgroundSize: "cover", backgroundPosition: "center", flex: "none" }} />
                <div className="grow">
                  <div className="t-xs" style={{ color: "var(--accent)", fontWeight: 700, marginBottom: 4 }}>{c.cat}</div>
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
            {due.map(a => (
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
          <LC data={[2.5,3.2,1.8,4.1,2.9,3.5,2.4]} labels={["T2","T3","T4","T5","T6","T7","CN"]} color="#7c3aed" height={210} />
        </Sn>
        <Sn title="Lộ trình học tập" sub="Cá nhân hóa theo lĩnh vực Backend Development">
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            {[{t:"Nền tảng lập trình",p:100},{t:"Cơ sở dữ liệu & SQL",p:100},{t:"Backend với NodeJS",p:62},{t:"Microservices & DevOps",p:18},{t:"Bảo mật & Deploy",p:0}].map((s,i)=>(
              <div key={i} className="row gap-12">
                <div style={{ width: 28, height: 28, borderRadius: 999, flex:"none", display:"grid", placeItems:"center", fontWeight:800, fontSize:12.5, background: s.p===100?"var(--success)":s.p>0?"var(--accent)":"var(--surface-3)", color: s.p>0?"#fff":"var(--text-3)" }}>{s.p===100?"✓":i+1}</div>
                <div className="grow"><div className="between" style={{marginBottom:6}}><span style={{fontWeight:600,fontSize:13.5}}>{s.t}</span><span className="t-xs muted">{s.p}%</span></div><div className="bar"><span style={{width:s.p+"%",background:s.p===100?"var(--success)":"var(--accent)"}}/></div></div>
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
