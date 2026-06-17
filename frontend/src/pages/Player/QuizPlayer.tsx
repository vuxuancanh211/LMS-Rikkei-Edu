// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Toàn màn hình · Làm Quiz (+ Giám sát Proctoring)
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

  /* ---------------- Quiz Player + Proctoring ---------------- */
  function QuizPlayer({ onBack, onSubmit }) {
    const q = D.quiz;
    const [sel, setSel] = useState(1);
    const [warn, setWarn] = useState(false);
    const [secs, setSecs] = useState(25 * 60 + 40);
    useEffect(() => { const t = setInterval(() => setSecs(s => Math.max(0, s - 1)), 1000); return () => clearInterval(t); }, []);
    const mm = String(Math.floor(secs / 60)).padStart(2, "0"), ss = String(secs % 60).padStart(2, "0");
    return (
      <div className="main" style={{ minHeight: "100vh", background: "#eef2f7" }}>
        <PlayerTop onBack={onBack} title="Quiz" />
        <div className="quiz-wrap" style={{ display: "flex", gap: 0, flex: 1, alignItems: "stretch" }}>
          <div className="grow" style={{ minWidth: 0, padding: "32px 28px", maxWidth: 860, margin: "0 auto", width: "100%" }}>
            <span className="chip chip-info" style={{ fontSize: 13, padding: "6px 14px" }}>Câu {q.question.no} / {q.total}</span>
            <h1 className="t-h1" style={{ margin: "18px 0 8px" }}>{q.question.text}</h1>
            <p className="muted" style={{ margin: "0 0 24px" }}>{q.question.hint}</p>
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {q.question.options.map((o, i) => (
                <label key={i} className="row gap-14" style={{ padding: "16px 18px", borderRadius: 13, cursor: "pointer", background: sel === i ? "var(--accent-soft)" : "#fff", border: "1.5px solid " + (sel === i ? "var(--accent)" : "var(--border)"), transition: ".15s" }} onClick={() => setSel(i)}>
                  <span style={{ width: 22, height: 22, borderRadius: 999, flex: "none", border: "2px solid " + (sel === i ? "var(--accent)" : "var(--border-input)"), display: "grid", placeItems: "center" }}>{sel === i && <span style={{ width: 11, height: 11, borderRadius: 999, background: "var(--accent)" }} />}</span>
                  <span style={{ fontSize: 15, fontWeight: sel === i ? 600 : 500, color: sel === i ? "var(--accent)" : "var(--text)" }}>{o}</span>
                </label>
              ))}
            </div>
            <div className="between" style={{ marginTop: 28 }}>
              <button className="btn btn-ghost"><Ic n="chevron_left" size={17} />Quay lại</button>
              <button className="btn btn-primary" onClick={() => setWarn(true)}>Câu tiếp theo<Ic n="chevron_right" size={17} /></button>
            </div>
          </div>

          {/* proctoring rail */}
          <div className="dark-scroll quiz-rail" style={{ width: 340, flex: "none", background: "var(--sidebar-bg)", color: "#fff", padding: 22, overflowY: "auto" }}>
            <div style={{ background: "rgba(255,255,255,.05)", borderRadius: 14, padding: 18, textAlign: "center", marginBottom: 18 }}>
              <div className="row gap-7" style={{ justifyContent: "center", color: "#fbbf24", marginBottom: 8 }}><Ic n="clock" size={17} /><span style={{ fontSize: 13, fontWeight: 600 }}>Thời gian còn lại</span></div>
              <div className="mono" style={{ fontSize: 40, fontWeight: 700, letterSpacing: "0.02em" }}>{mm}:{ss}</div>
            </div>
            <div style={{ background: "rgba(16,185,129,.1)", border: "1px solid rgba(16,185,129,.25)", borderRadius: 12, padding: 13, marginBottom: 18, display: "flex", gap: 10, alignItems: "center" }}>
              <Ic n="shield" size={18} style={{ color: "var(--success)", flex: "none" }} /><div style={{ fontSize: 12, color: "#a7f3d0" }}>Chế độ giám sát đang bật. Không chuyển tab.</div>
            </div>
            <button className="btn btn-ghost btn-block" style={{ background: "rgba(255,255,255,.06)", color: "#fff", border: "1px solid rgba(255,255,255,.12)", marginBottom: 18 }}><Ic n="flag" size={16} />Đánh dấu câu hỏi</button>
            <div className="row gap-8" style={{ color: "var(--success)", marginBottom: 14, fontWeight: 700 }}><Ic n="grid" size={18} />Danh sách câu hỏi</div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(5,1fr)", gap: 8 }}>
              {Array.from({ length: 40 }, (_, i) => i + 1).map(n => {
                const answered = q.answered.includes(n), cur = n === q.question.no, flag = q.flagged.includes(n);
                return <div key={n} style={{ aspectRatio: "1", borderRadius: 9, display: "grid", placeItems: "center", fontSize: 13, fontWeight: 700, cursor: "pointer", position: "relative",
                  background: cur ? "transparent" : answered ? "rgba(37,99,235,.85)" : "rgba(255,255,255,.05)",
                  border: cur ? "2px solid var(--success)" : "1px solid rgba(255,255,255,.08)",
                  color: cur ? "var(--success)" : answered ? "#fff" : "#94a3b8" }}>{n}{flag && <span style={{ position: "absolute", top: 2, right: 3, width: 6, height: 6, borderRadius: 999, background: "var(--warning)" }} />}</div>;
              })}
            </div>
            <button className="btn btn-success btn-block btn-lg" style={{ marginTop: 22 }} onClick={onSubmit}><Ic n="send" size={17} />Nộp bài</button>
          </div>
        </div>

        <Modal open={warn} onClose={() => setWarn(false)} max={460}>
          <div style={{ padding: "30px 28px 24px", textAlign: "center" }}>
            <div style={{ width: 72, height: 72, borderRadius: 999, background: "var(--chip-error-bg)", display: "grid", placeItems: "center", margin: "0 auto 18px" }}><Ic n="warn" size={36} style={{ color: "var(--error)" }} /></div>
            <h2 className="t-h2" style={{ color: "var(--error)", margin: "0 0 10px" }}>Cảnh báo vi phạm!</h2>
            <p className="muted" style={{ margin: "0 0 6px", lineHeight: 1.6 }}>Hệ thống phát hiện bạn đã rời khỏi trình duyệt. Đây là lần vi phạm thứ <b style={{ color: "var(--text)" }}>1/2</b>.</p>
            <div style={{ background: "var(--chip-error-bg)", borderRadius: 11, padding: "12px 14px", margin: "14px 0 0", fontSize: 13, color: "var(--chip-error-fg)" }}>Vi phạm lần thứ 3 sẽ tự động nộp bài và kết thúc bài thi của bạn ngay lập tức.</div>
            <button className="btn btn-primary btn-block btn-lg" style={{ marginTop: 20 }} onClick={() => setWarn(false)}>Tôi đã hiểu và cam kết không tái phạm</button>
          </div>
        </Modal>
      </div>
    );
  }


  Object.assign(window, { QuizPlayer });
})();
