// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Toàn màn hình · Trình phát bài giảng (+ AI Chatbot)
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

  /* ---------------- Lecture Player + AI Chatbot ---------------- */
  function LecturePlayer({ onBack }) {
    const [open, setOpen] = useState(0);
    const [chat, setChat] = useState(false);
    const [msgs, setMsgs] = useState([
      { me: false, t: "Xin chào! Mình là trợ lý AI của Rikkei Edu. Mình có thể giúp bạn giải đáp thắc mắc về bài giảng này. Bạn cần hỗ trợ gì? 🤖" },
    ]);
    const [input, setInput] = useState("");
    const endRef = useRef();
    useEffect(() => { endRef.current && endRef.current.scrollIntoView({ block: "nearest" }); }, [msgs, chat]);
    const send = () => {
      if (!input.trim()) return;
      const q = input.trim();
      setMsgs(m => [...m, { me: true, t: q }]);
      setInput("");
      setTimeout(() => setMsgs(m => [...m, { me: false, t: "Agile là phương pháp phát triển phần mềm linh hoạt, tập trung vào việc phát triển lặp đi lặp lại (iterative) và tăng dần (incremental). Thay vì hoàn thành toàn bộ rồi mới giao, Agile chia nhỏ công việc thành các Sprint ngắn để liên tục nhận phản hồi. Bạn có muốn mình giải thích thêm về Scrum không?" }]), 600);
    };

    return (
      <div className="main" style={{ minHeight: "100vh" }}>
        <PlayerTop onBack={onBack} />
        <div className="lecture-wrap" style={{ display: "flex", gap: 0, flex: 1, alignItems: "stretch" }}>
          <div className="grow" style={{ minWidth: 0, padding: "26px 28px", maxWidth: 980, margin: "0 auto", width: "100%" }}>
            <div className="t-sm muted" style={{ marginBottom: 6 }}>Session 01 — Tổng quan về Agile & Scrum</div>
            <h1 className="t-h1" style={{ marginBottom: 20 }}>Lesson 1: Tổng quan về quy trình phát triển phần mềm</h1>
            <div style={{ position: "relative", borderRadius: 16, overflow: "hidden", aspectRatio: "16/9", background: `#0a0f1c url(${D.T.lesson}) center/cover` }}>
              <div style={{ position: "absolute", inset: 0, background: "rgba(8,12,24,.34)", display: "grid", placeItems: "center" }}>
                <button style={{ width: 76, height: 76, borderRadius: 999, border: "none", background: "rgba(15,23,42,.78)", color: "#fff", display: "grid", placeItems: "center", cursor: "pointer", backdropFilter: "blur(4px)" }}><Ic n="play" size={30} fill="#fff" /></button>
              </div>
              <div style={{ position: "absolute", right: 14, top: 14 }}><span className="chip" style={{ background: "rgba(15,23,42,.7)", color: "#fff" }}><Ic n="lock" size={12} />Nội dung bảo mật</span></div>
            </div>
            <Section title="Tài liệu học tập" sub="Tài liệu tham khảo cho bài giảng này" pad={false}>
              <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 12 }}>
                {[{n:"Quy trình phát triển phần mềm - Chi tiết.pdf",s:"2.4 MB • PDF Document",ic:"#ef4444",bg:"#fdecec"},{n:"Tóm tắt các mô hình phát triển.docx",s:"850 KB • Word Document",ic:"#2563eb",bg:"#eaf1ff"},{n:"Slide bài giảng Session 01.pptx",s:"4.1 MB • Slide",ic:"#f59e0b",bg:"#fef5e6"}].map((f,i)=>(
                  <div key={i} className="row gap-14" style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 12 }}>
                    <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 11, background: f.bg, color: f.ic }}><Ic n="file" size={21} /></div>
                    <div className="grow" style={{ minWidth: 0 }}><div style={{ fontWeight: 600, fontSize: 14 }} className="truncate">{f.n}</div><div className="t-xs muted">{f.s}</div></div>
                    <button className="btn btn-soft btn-sm">Xem chi tiết</button>
                    <button className="btn btn-primary btn-icon btn-sm" style={{ width: 36, height: 36 }}><Ic n="download" size={16} /></button>
                  </div>
                ))}
              </div>
            </Section>
          </div>

          {/* curriculum rail */}
          <div className="dark-scroll lecture-rail" style={{ width: 380, flex: "none", background: "var(--sidebar-bg)", color: "#fff", padding: 22, overflowY: "auto", maxHeight: "calc(100vh - var(--header-h))", position: "sticky", top: "var(--header-h)" }}>
            <h3 style={{ margin: "0 0 6px", fontSize: 17 }}>Phát triển phần mềm với Agile / Scrum</h3>
            <div style={{ fontSize: 12.5, color: "#94a3b8", marginBottom: 18 }}>3 sessions • 11 bài giảng • 38 giờ</div>
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {D.curriculum.map((s, si) => (
                <div key={si} style={{ background: "rgba(255,255,255,.04)", border: "1px solid rgba(255,255,255,.07)", borderRadius: 13, overflow: "hidden" }}>
                  <div className="row gap-10" style={{ padding: "13px 14px", cursor: "pointer" }} onClick={() => setOpen(open === si ? -1 : si)}>
                    <div style={{ width: 22, height: 22, borderRadius: 999, background: "var(--success)", display: "grid", placeItems: "center", flex: "none" }}><Ic n="check" size={13} /></div>
                    <b style={{ flex: 1, fontSize: 13.5 }} className="truncate">{s.session}</b>
                    <Ic n="chevron_down" size={17} style={{ transform: open === si ? "rotate(180deg)" : "none", transition: ".2s", color: "#94a3b8" }} />
                  </div>
                  {open === si && (
                    <div style={{ padding: "0 8px 8px" }}>
                      {s.lessons.map((l, li) => (
                        <div key={li} className="row gap-11" style={{ padding: "10px 10px", borderRadius: 10, cursor: "pointer", background: l.active ? "rgba(16,185,129,.13)" : "transparent" }}>
                          <div style={{ width: 20, height: 20, borderRadius: 999, flex: "none", display: "grid", placeItems: "center", background: l.done ? "var(--success)" : "transparent", border: l.done ? "none" : "1.5px solid #475569" }}>{l.done && <Ic n="check" size={12} />}</div>
                          <div className="grow" style={{ minWidth: 0 }}>
                            <div style={{ fontSize: 13, fontWeight: l.active ? 700 : 500, color: l.active ? "#6ff5c0" : "#cbd5e1" }} className="truncate">{l.t}</div>
                            <div className="row gap-5" style={{ fontSize: 11.5, color: "#64748b", marginTop: 2 }}><Ic n="video" size={12} />Video • {l.dur}</div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* AI Chatbot floating */}
        {chat && (
          <div className="ai-panel" style={{ position: "fixed", right: 24, bottom: 96, width: 380, maxWidth: "calc(100vw - 32px)", height: 520, maxHeight: "70vh", background: "rgba(255,255,255,.86)", backdropFilter: "blur(18px)", border: "1px solid rgba(255,255,255,.6)", borderRadius: 20, boxShadow: "var(--sh-lg)", display: "flex", flexDirection: "column", zIndex: 70, animation: "popIn .25s" }}>
            <div className="row gap-12" style={{ padding: 16, borderBottom: "1px solid var(--border)" }}>
              <div className="sb-logo" style={{ width: 40, height: 40, background: "linear-gradient(150deg,#7c3aed,#2563eb)" }}><Ic n="sparkles" size={20} /></div>
              <div className="grow"><div style={{ fontWeight: 700 }}>Trợ lý AI Rikkei</div><div className="t-xs" style={{ color: "var(--success)" }}>● Trực tuyến 24/7</div></div>
              <button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => setChat(false)}><Ic n="x" size={17} /></button>
            </div>
            <div style={{ flex: 1, overflowY: "auto", padding: 16, display: "flex", flexDirection: "column", gap: 12 }}>
              {msgs.map((m, i) => (
                <div key={i} style={{ alignSelf: m.me ? "flex-end" : "flex-start", maxWidth: "85%" }}>
                  <div style={{ padding: "10px 14px", borderRadius: 14, fontSize: 13.5, lineHeight: 1.55, background: m.me ? "var(--primary)" : "#fff", color: m.me ? "#fff" : "var(--text)", border: m.me ? "none" : "1px solid var(--border)", borderBottomRightRadius: m.me ? 4 : 14, borderBottomLeftRadius: m.me ? 14 : 4 }}>{m.t}</div>
                  {!m.me && i > 0 && <div className="row gap-6" style={{ marginTop: 6 }}><button className="icon-btn" style={{ width: 28, height: 28, color: "var(--text-3)" }}><Ic n="thumbs_up" size={14} /></button><button className="icon-btn" style={{ width: 28, height: 28, color: "var(--text-3)" }}><Ic n="thumbs_down" size={14} /></button></div>}
                </div>
              ))}
              <div ref={endRef} />
            </div>
            <div className="row gap-8" style={{ padding: 14, borderTop: "1px solid var(--border)" }}>
              <input className="input" placeholder="Nhập câu hỏi của bạn..." value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => e.key === "Enter" && send()} />
              <button className="btn btn-primary btn-icon" onClick={send}><Ic n="send" size={17} /></button>
            </div>
          </div>
        )}
        <button onClick={() => setChat(!chat)} style={{ position: "fixed", right: 24, bottom: 24, width: 60, height: 60, borderRadius: 999, border: "none", background: "linear-gradient(150deg,#7c3aed,#2563eb)", color: "#fff", display: "grid", placeItems: "center", cursor: "pointer", boxShadow: "0 10px 30px rgba(124,58,237,.4)", zIndex: 71 }}>
          <Ic n={chat ? "x" : "sparkles"} size={26} />
        </button>
      </div>
    );
  }


  Object.assign(window, { LecturePlayer });
})();
