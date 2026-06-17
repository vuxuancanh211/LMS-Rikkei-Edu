// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Toàn màn hình · Kết quả bài kiểm tra
   ============================================================ */
(function () {
  const { useState, useEffect, useRef } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, Modal, Section } = window;

  /* shared top bar for fullscreen views */
  function PlayerTop({ title, onBack }) {
    return (
      <div className="topbar" style={{ paddingLeft: 20, paddingRight: 24 }}>
        <button className="icon-btn" onClick={onBack}><Ic n="arrow_left" size={20} /></button>
        <div className="row gap-10"><div className="sb-logo" style={{ width: 34, height: 34, borderRadius: 9 }}><Ic n="cap" size={18} /></div><b style={{ fontSize: 16 }}>Rikkei Edu</b></div>
        <div className="grow" />
        <button className="icon-btn"><Ic n="bell" size={19} /><span className="badge-dot" /></button>
        <button className="icon-btn"><Ic n="help" size={19} /></button>
        <Avatar name="Học viên Rikkei" size={40} />
      </div>
    );
  }

  /* ---------------- Quiz Result ---------------- */
  function QuizResult({ onBack }) {
    const data = [
      { q: "Câu 1: Agile Manifesto có bao nhiêu giá trị cốt lõi?", ok: true },
      { q: "Câu 2: Scrum Master có vai trò gì?", ok: true },
      { q: "Câu 3: Sprint thường kéo dài bao lâu?", ok: false },
      { q: "Câu 4: Product Backlog do ai quản lý?", ok: true },
      { q: "Câu 5: Đặc điểm của mô hình Agile?", ok: true },
    ];
    return (
      <div className="main" style={{ minHeight: "100vh" }}>
        <PlayerTop onBack={onBack} />
        <div className="page" style={{ maxWidth: 820 }}>
          <div className="card fade-in" style={{ overflow: "hidden", marginBottom: 22 }}>
            <div style={{ background: "linear-gradient(135deg,#059669,#10b981)", padding: "34px 28px", textAlign: "center", color: "#fff" }}>
              <div style={{ width: 80, height: 80, borderRadius: 999, background: "rgba(255,255,255,.2)", display: "grid", placeItems: "center", margin: "0 auto 16px" }}><Ic n="check_circle" size={44} /></div>
              <div style={{ fontSize: 15, opacity: .9 }}>Chúc mừng! Bạn đã hoàn thành</div>
              <h1 style={{ margin: "6px 0 0", fontSize: 26 }}>Bài kiểm tra cuối khóa: Agile & Scrum</h1>
            </div>
            <div className="grid" style={{ gridTemplateColumns: "repeat(4,1fr)", padding: 24, gap: 0 }}>
              {[{l:"Điểm số",v:"9.0",c:"var(--success)"},{l:"Câu đúng",v:"36/40",c:"var(--text)"},{l:"Thời gian",v:"22:15",c:"var(--text)"},{l:"Kết quả",v:"Đạt",c:"var(--success)"}].map((s,i)=>(
                <div key={i} style={{ textAlign: "center", padding: "0 12px", borderRight: i < 3 ? "1px solid var(--border)" : "none" }}><div style={{ fontSize: 30, fontWeight: 800, color: s.c }}>{s.v}</div><div className="t-sm muted" style={{ marginTop: 4 }}>{s.l}</div></div>
              ))}
            </div>
          </div>
          <Section title="Chi tiết đáp án" sub="Xem lại các câu trả lời của bạn" pad={false}>
            <div style={{ padding: 12 }}>
              {data.map((d, i) => (
                <div key={i} className="row gap-12" style={{ padding: 14, borderRadius: 11 }}>
                  <div style={{ width: 30, height: 30, borderRadius: 999, flex: "none", display: "grid", placeItems: "center", background: d.ok ? "var(--chip-success-bg)" : "var(--chip-error-bg)", color: d.ok ? "var(--success)" : "var(--error)" }}><Ic n={d.ok ? "check" : "x"} size={17} /></div>
                  <div className="grow" style={{ fontWeight: 600, fontSize: 14 }}>{d.q}</div>
                  <span className={"chip chip-" + (d.ok ? "success" : "error")}>{d.ok ? "Đúng" : "Sai"}</span>
                </div>
              ))}
            </div>
          </Section>
          <div className="row gap-12" style={{ marginTop: 22, justifyContent: "center" }}>
            <button className="btn btn-ghost" onClick={onBack}>Về trang Bài tập</button>
            <button className="btn btn-primary"><Ic n="download" size={16} />Tải kết quả PDF</button>
          </div>
        </div>
      </div>
    );
  }

  Object.assign(window, { QuizResult });
})();
