// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Reusable floating AI Chatbot widget
   Mounted on student & instructor shell pages (not quiz/result/player).
   ============================================================ */
(function () {
  const { useState, useRef, useEffect } = React;
  const Ic = window.Icon;

  const SUGGEST = [
    "Tóm tắt nội dung bài học này",
    "Giải thích khái niệm Redux Toolkit",
    "Hạn nộp bài tập sắp tới của tôi?",
  ];

  function AIChatbot({ contained }) {
    const [open, setOpen] = useState(false);
    const [input, setInput] = useState("");
    const [sending, setSending] = useState(false);
    const [conversationId, setConversationId] = useState(null);
    const pos = contained ? "absolute" : "fixed";
    const [msgs, setMsgs] = useState([
      { me: false, t: "Xin chào! Mình là trợ lý AI của Rikkei Edu. Mình có thể giúp bạn về bài giảng, bài tập, deadline hay cách dùng nền tảng. Bạn cần hỗ trợ gì? 🤖" },
    ]);
    const endRef = useRef();
    useEffect(() => { endRef.current && endRef.current.scrollIntoView({ block: "nearest" }); }, [msgs, open, sending]);

    const send = async (text) => {
      const q = (text != null ? text : input).trim();
      if (!q || sending) return;
      setMsgs(m => [...m, { me: true, t: q }]);
      setInput("");
      setSending(true);
      try {
        const courseId = window.__selectedCourseId || sessionStorage.getItem("selectedCourseId") || null;
        const res = await window.__aiService.sendChatMessage({ message: q, courseId, conversationId });
        setConversationId(res.conversationId);
        setMsgs(m => [...m, { me: false, t: res.answer }]);
      } catch (e) {
        setMsgs(m => [...m, { me: false, t: "Xin lỗi, mình đang gặp sự cố khi trả lời. Bạn thử lại sau nhé.", error: true }]);
      } finally {
        setSending(false);
      }
    };

    return (
      <>
        {open && (
          <div className="ai-panel" style={{ position: pos, right: 24, bottom: 96, width: 380, maxWidth: "calc(100vw - 32px)", height: 520, maxHeight: "70vh", background: "rgba(255,255,255,.9)", backdropFilter: "blur(18px)", border: "1px solid rgba(255,255,255,.6)", borderRadius: 20, boxShadow: "var(--sh-lg)", display: "flex", flexDirection: "column", zIndex: 70, animation: "popIn .25s" }}>
            <div className="row gap-12" style={{ padding: 16, borderBottom: "1px solid var(--border)" }}>
              <div className="sb-logo" style={{ width: 40, height: 40, background: "linear-gradient(150deg,#7c3aed,#2563eb)" }}><Ic n="sparkles" size={20} /></div>
              <div className="grow"><div style={{ fontWeight: 700 }}>Trợ lý AI Rikkei</div><div className="t-xs" style={{ color: "var(--success)" }}>● Trực tuyến 24/7</div></div>
              <button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => setOpen(false)}><Ic n="x" size={17} /></button>
            </div>
            <div style={{ flex: 1, overflowY: "auto", padding: 16, display: "flex", flexDirection: "column", gap: 12 }}>
              {msgs.map((m, i) => (
                <div key={i} style={{ alignSelf: m.me ? "flex-end" : "flex-start", maxWidth: "85%" }}>
                  <div style={{ padding: "10px 14px", borderRadius: 14, fontSize: 13.5, lineHeight: 1.55, background: m.me ? "var(--primary)" : "#fff", color: m.me ? "#fff" : "var(--text)", border: m.me ? "none" : "1px solid var(--border)", borderBottomRightRadius: m.me ? 4 : 14, borderBottomLeftRadius: m.me ? 14 : 4 }}>{m.t}</div>
                  {!m.me && i > 0 && <div className="row gap-6" style={{ marginTop: 6 }}><button className="icon-btn" style={{ width: 28, height: 28, color: "var(--text-3)" }}><Ic n="thumbs_up" size={14} /></button><button className="icon-btn" style={{ width: 28, height: 28, color: "var(--text-3)" }}><Ic n="thumbs_down" size={14} /></button></div>}
                </div>
              ))}
              {sending && (
                <div style={{ alignSelf: "flex-start", maxWidth: "85%" }}>
                  <div style={{ padding: "10px 14px", borderRadius: 14, fontSize: 13.5, background: "#fff", color: "var(--text-3)", border: "1px solid var(--border)", borderBottomLeftRadius: 4 }}>Đang trả lời...</div>
                </div>
              )}
              {msgs.length <= 1 && !sending && (
                <div style={{ marginTop: "auto", display: "flex", flexDirection: "column", gap: 8 }}>
                  <div className="t-xs dim" style={{ paddingLeft: 2 }}>Gợi ý câu hỏi</div>
                  {SUGGEST.map((s, i) => (
                    <button key={i} className="row gap-8" onClick={() => send(s)} style={{ textAlign: "left", padding: "10px 12px", borderRadius: 11, border: "1px solid var(--border)", background: "#fff", cursor: "pointer", fontSize: 13, color: "var(--text)", fontFamily: "var(--font)" }}>
                      <Ic n="sparkles" size={14} style={{ color: "#7c3aed", flex: "none" }} />{s}
                    </button>
                  ))}
                </div>
              )}
              <div ref={endRef} />
            </div>
            <div className="row gap-8" style={{ padding: 14, borderTop: "1px solid var(--border)" }}>
              <input className="input" placeholder="Nhập câu hỏi của bạn..." value={input} disabled={sending} onChange={e => setInput(e.target.value)} onKeyDown={e => e.key === "Enter" && send()} />
              <button className="btn btn-primary btn-icon" disabled={sending} onClick={() => send()}><Ic n="send" size={17} /></button>
            </div>
          </div>
        )}
        <button className="ai-fab" onClick={() => setOpen(!open)} aria-label="Trợ lý AI"
          style={{ position: pos, right: 24, bottom: 24, width: 60, height: 60, borderRadius: 999, border: "none", background: "linear-gradient(150deg,#7c3aed,#2563eb)", color: "#fff", display: "grid", placeItems: "center", cursor: "pointer", boxShadow: "0 10px 30px rgba(124,58,237,.4)", zIndex: 71 }}>
          <Ic n={open ? "x" : "sparkles"} size={26} />
        </button>
      </>
    );
  }
  window.AIChatbot = AIChatbot;
})();
