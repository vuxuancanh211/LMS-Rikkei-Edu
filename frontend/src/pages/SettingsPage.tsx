// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Shared Settings / Profile screen
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon;
  const { Avatar, Section } = window;

  function Settings({ persona }) {
    const [tab, setTab] = useState("profile");
    return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Cài đặt tài khoản</h1><p>Quản lý hồ sơ cá nhân, bảo mật và tùy chọn thông báo.</p></div>
        <div className="grid settings-grid" style={{ gridTemplateColumns: "240px 1fr", gap: 22, alignItems: "start" }}>
          <div className="card settings-nav" style={{ padding: 10 }}>
            {[{v:"profile",l:"Hồ sơ cá nhân",ic:"user"},{v:"security",l:"Bảo mật",ic:"lock"},{v:"notif",l:"Thông báo",ic:"bell"},{v:"pref",l:"Tùy chọn",ic:"sliders"}].map(t => (
              <div key={t.v} className="row gap-11" style={{ padding: "11px 13px", borderRadius: 10, cursor: "pointer", fontWeight: 600, fontSize: 14, color: tab === t.v ? "var(--text)" : "var(--text-2)", background: tab === t.v ? "var(--surface-3)" : "transparent" }} onClick={() => setTab(t.v)}>
                <Ic n={t.ic} size={18} />{t.l}
              </div>
            ))}
          </div>
          <div className="card card-pad">
            {tab === "profile" && <div>
              <div className="row gap-16" style={{ marginBottom: 24 }}>
                <Avatar name={persona.name} size={76} />
                <div><div className="t-h3">{persona.name}</div><div className="muted">{persona.email}</div><button className="btn btn-ghost btn-sm" style={{ marginTop: 10 }}><Ic n="upload" size={15} />Đổi ảnh đại diện</button></div>
              </div>
              <div className="grid grid-2" style={{ gap: 16 }}>
                <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Họ và tên</label><input className="input" defaultValue={persona.name} /></div>
                <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Email</label><input className="input" defaultValue={persona.email} /></div>
                <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Số điện thoại</label><input className="input" defaultValue="0912 345 678" /></div>
                <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Vai trò</label><input className="input" defaultValue={persona.role} disabled /></div>
                <div style={{ gridColumn: "1 / -1" }}><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Giới thiệu (Bio)</label><textarea className="input" style={{ height: 90, padding: 12, resize: "none" }} defaultValue="Đam mê công nghệ và học tập suốt đời." /></div>
              </div>
              <div className="row gap-10" style={{ marginTop: 22, justifyContent: "flex-end" }}><button className="btn btn-ghost">Hủy</button><button className="btn btn-primary">Lưu thay đổi</button></div>
            </div>}
            {tab === "security" && <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 460 }}>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Mật khẩu hiện tại</label><input className="input" type="password" placeholder="••••••••" /></div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Mật khẩu mới</label><input className="input" type="password" placeholder="••••••••" /></div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Xác nhận mật khẩu mới</label><input className="input" type="password" placeholder="••••••••" /></div>
              <label className="row gap-10" style={{ padding: "14px 16px", background: "var(--surface-2)", borderRadius: 12, cursor: "pointer" }}><input type="checkbox" style={{ width: 18, height: 18 }} defaultChecked /><div><div style={{ fontWeight: 600, fontSize: 14 }}>Xác thực 2 lớp (2FA)</div><div className="t-xs muted">Tăng cường bảo mật cho tài khoản của bạn</div></div></label>
              <button className="btn btn-primary" style={{ alignSelf: "flex-start" }}>Cập nhật mật khẩu</button>
            </div>}
            {tab === "notif" && <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {[["Email khi có bài kiểm tra / quiz mới",true],["Thông báo in-app realtime",true],["Email khi được cấp chứng chỉ",true],["Thông báo trả lời trên diễn đàn",false],["Bản tin hàng tuần",false]].map((n,i)=>(
                <label key={i} className="between" style={{ padding: "14px 4px", borderBottom: "1px solid var(--border)", cursor: "pointer" }}><span style={{ fontWeight: 500, fontSize: 14.5 }}>{n[0]}</span><span className="toggle" data-on={n[1]} /></label>
              ))}
            </div>}
            {tab === "pref" && <div style={{ maxWidth: 420, display: "flex", flexDirection: "column", gap: 16 }}>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Ngôn ngữ</label><div className="row gap-8">{["Tiếng Việt","English"].map((l,i)=><button key={i} className={"btn btn-sm " + (i===0?"btn-primary":"btn-ghost")}>{l}</button>)}</div></div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Lĩnh vực quan tâm</label><div className="row gap-8 wrap">{["Frontend","Backend","DevOps","AI/ML","Design"].map((l,i)=><span key={i} className={"chip " + (i<2?"chip-info":"chip-neutral")} style={{ cursor: "pointer", padding: "7px 14px" }}>{l}</span>)}</div></div>
            </div>}
          </div>
        </div>
      </div>
    );
  }
  window.Settings = Settings;
})();
