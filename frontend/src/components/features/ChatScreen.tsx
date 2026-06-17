// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Shared Forum & Group Chat (Giảng viên + Học viên)
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar } = window;

  const convos = [
    { id:"g1", name:"Nhóm A1 - ReactJS K15", last:"Thầy ơi em hỏi về phần Redux ạ", time:"09:42", unread:3, online:14, group:true },
    { id:"g2", name:"Nhóm B1 - NodeJS K12", last:"Mọi người nộp bài chưa?", time:"08:15", unread:0, online:9, group:true },
    { id:"d1", name:"Nguyễn Văn An (GV)", last:"Em xem lại lesson 4 nhé", time:"Hôm qua", unread:0, online:1, group:false },
    { id:"d2", name:"Vũ Thị Phương", last:"Cảm ơn bạn nhiều!", time:"Hôm qua", unread:0, online:1, group:false },
    { id:"g3", name:"Nhóm C1 - Spring Boot K9", last:"Link tài liệu đây mọi người", time:"T2", unread:0, online:18, group:true },
    { id:"d3", name:"Ngô Văn Khoa", last:"Ok để mình check", time:"T2", unread:0, online:1, group:false },
  ];
  const thread = [
    { me:false, who:"Nguyễn Văn An", role:"GV", t:"Chào cả nhóm, hôm nay chúng ta sẽ thảo luận về Redux Toolkit nhé.", time:"09:30" },
    { me:false, who:"Vũ Thị Phương", t:"Dạ thầy, em vẫn chưa hiểu rõ phần createAsyncThunk ạ.", time:"09:35" },
    { me:false, who:"Nguyễn Văn An", role:"GV", t:"createAsyncThunk giúp xử lý các tác vụ bất đồng bộ. Nó tự sinh ra 3 action: pending, fulfilled, rejected.", time:"09:38" },
    { me:true, who:"Hoàng Văn Em", t:"Thầy ơi em hỏi về phần Redux ạ, khi nào thì nên dùng useSelector ạ?", time:"09:42" },
    { me:false, who:"Nguyễn Văn An", role:"GV", t:"useSelector dùng để đọc state từ store. Em nên đặt nó ở component cần dữ liệu đó thôi để tối ưu re-render nhé.", time:"09:44" },
  ];

  function ChatScreen() {
    const [active, setActive] = useState("g1");
    const [msg, setMsg] = useState("");
    const cur = convos.find(c => c.id === active) || convos[0];
    return (
      <div className="page fade-in" style={{ paddingBottom: 0 }}>
        <div className="page-head"><h1 className="t-h1">Chat nhóm</h1><p>Trao đổi trực tiếp (real-time) giữa Giảng viên và Học viên trong cùng đợt học.</p></div>
        <div className="card chat-shell" style={{ overflow: "hidden", display: "grid", gridTemplateColumns: "320px 1fr", height: 620 }}>
          {/* conversation list */}
          <div className="chat-list" style={{ borderRight: "1px solid var(--border)", display: "flex", flexDirection: "column", minHeight: 0 }}>
            <div style={{ padding: 14, borderBottom: "1px solid var(--border)" }}>
              <div className="field-icon"><Ic n="search" /><input className="input" placeholder="Tìm cuộc trò chuyện..." /></div>
            </div>
            <div style={{ overflowY: "auto", flex: 1 }}>
              {convos.map(c => (
                <div key={c.id} className="row gap-12" style={{ padding: "13px 16px", cursor: "pointer", background: active === c.id ? "var(--accent-soft)" : "transparent", borderLeft: "3px solid " + (active === c.id ? "var(--accent)" : "transparent") }} onClick={() => setActive(c.id)}>
                  <div style={{ position: "relative", flex: "none" }}>
                    {c.group ? <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 12, background: "#f3edff", color: "#7c3aed" }}><Ic n="layers" size={21} /></div> : <Avatar name={c.name} size={44} />}
                    {!c.group && <span style={{ position: "absolute", right: 0, bottom: 0, width: 12, height: 12, borderRadius: 999, background: "var(--success)", border: "2px solid #fff" }} />}
                  </div>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div className="between"><b style={{ fontSize: 14 }} className="truncate">{c.name}</b><span className="t-xs dim" style={{ flex: "none" }}>{c.time}</span></div>
                    <div className="between" style={{ marginTop: 3 }}><span className="t-sm muted truncate">{c.last}</span>{c.unread > 0 && <span style={{ flex: "none", background: "var(--accent)", color: "#fff", fontSize: 11, fontWeight: 700, minWidth: 20, height: 20, borderRadius: 999, display: "grid", placeItems: "center", padding: "0 6px" }}>{c.unread}</span>}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
          {/* thread */}
          <div className="chat-thread" style={{ display: "flex", flexDirection: "column", minHeight: 0, background: "var(--surface-2)" }}>
            <div className="between" style={{ padding: "14px 20px", borderBottom: "1px solid var(--border)", background: "#fff" }}>
              <div className="row gap-12">
                {cur.group ? <div className="stat-ic" style={{ width: 42, height: 42, borderRadius: 11, background: "#f3edff", color: "#7c3aed" }}><Ic n="layers" size={20} /></div> : <Avatar name={cur.name} size={42} />}
                <div><div style={{ fontWeight: 700, fontSize: 15 }}>{cur.name}</div><div className="t-xs" style={{ color: "var(--success)" }}>● {cur.group ? cur.online + " thành viên đang hoạt động" : "Đang hoạt động"}</div></div>
              </div>
              <div className="row gap-8"><button className="icon-btn" style={{ width: 38, height: 38 }}><Ic n="search" size={18} /></button><button className="icon-btn" style={{ width: 38, height: 38 }}><Ic n="dots" size={18} /></button></div>
            </div>
            <div style={{ flex: 1, overflowY: "auto", padding: 20, display: "flex", flexDirection: "column", gap: 14 }}>
              <div style={{ textAlign: "center" }}><span className="chip chip-neutral">Hôm nay</span></div>
              {thread.map((m, i) => (
                <div key={i} style={{ display: "flex", gap: 10, flexDirection: m.me ? "row-reverse" : "row", alignItems: "flex-end" }}>
                  {!m.me && <Avatar name={m.who} size={32} />}
                  <div style={{ maxWidth: "62%" }}>
                    {!m.me && <div className="row gap-6" style={{ marginBottom: 4, marginLeft: 2 }}><span className="t-xs" style={{ fontWeight: 700 }}>{m.who}</span>{m.role === "GV" && <span className="chip chip-info" style={{ fontSize: 10, padding: "1px 7px" }}>Giảng viên</span>}</div>}
                    <div style={{ padding: "11px 15px", borderRadius: 16, fontSize: 14, lineHeight: 1.5, background: m.me ? "var(--primary)" : "#fff", color: m.me ? "#fff" : "var(--text)", border: m.me ? "none" : "1px solid var(--border)", borderBottomRightRadius: m.me ? 4 : 16, borderBottomLeftRadius: m.me ? 16 : 4 }}>{m.t}</div>
                    <div className="t-xs dim" style={{ marginTop: 4, textAlign: m.me ? "right" : "left", padding: "0 4px" }}>{m.time}</div>
                  </div>
                </div>
              ))}
            </div>
            <div className="row gap-10" style={{ padding: 16, borderTop: "1px solid var(--border)", background: "#fff" }}>
              <button className="icon-btn" style={{ width: 44, height: 44 }}><Ic n="plus" size={20} /></button>
              <input className="input" placeholder="Nhập tin nhắn..." value={msg} onChange={e => setMsg(e.target.value)} />
              <button className="btn btn-primary btn-icon" style={{ width: 44, height: 44 }}><Ic n="send" size={18} /></button>
            </div>
          </div>
        </div>
      </div>
    );
  }
  window.ChatScreen = ChatScreen;
})();
