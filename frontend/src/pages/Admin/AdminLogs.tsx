// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Nhật ký hệ thống
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  /* ---------------- System logs ---------------- */
  function AdminLogs() {
    const [q, setQ] = useState("");
    let list = D.activity.filter(a => !q || a.who.toLowerCase().includes(q.toLowerCase()) || a.act.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 10);
    return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Nhật ký hệ thống</h1><p>Theo dõi toàn bộ hoạt động và sự kiện trên nền tảng.</p></div>
        <div className="toolbar"><Search placeholder="Tìm trong nhật ký..." value={q} onChange={setQ} /><Select value="all" onChange={()=>{}} options={[{v:"all",label:"Tất cả loại"},{v:"warn",label:"Cảnh báo"},{v:"approve",label:"Phê duyệt"}]} style={{width:170,flex:"none"}} /></div>
        <Section pad={false}>
          <div style={{ padding: 8 }}>
            {pg.slice.map((a, i) => (
              <div key={i} className="row gap-13" style={{ padding: 15, borderRadius: 12, borderBottom: i < pg.slice.length - 1 ? "1px solid var(--border)" : "none" }}>
                <div style={{ width: 38, height: 38, borderRadius: 11, flex: "none", display: "grid", placeItems: "center", background: actColor[a.type] + "18", color: actColor[a.type] }}><Ic n={actIcon[a.type]} size={18} /></div>
                <div className="grow"><div style={{ fontSize: 14, lineHeight: 1.45 }}><b>{a.who}</b> {a.act}</div><div className="t-xs dim" style={{ marginTop: 3 }}>{a.time}</div></div>
                {a.type === "warn" && <span className="chip chip-error">Cảnh báo</span>}
              </div>
            ))}
          </div>
        </Section>
        <window.PageBar pg={pg} unit="sự kiện" />
      </div>
    );
  }

  Object.assign(window, { AdminLogs });
})();
