// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Phê duyệt Khóa học (+ popup chi tiết / xem trước / từ chối)
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  /* ---------------- Course approval ---------------- */
  function AdminApproval({ demo }) {
    const [tab, setTab] = useState("pending");
    const [detail, setDetail] = useState((demo === "detail" || demo === "preview") ? D.approvals.find(a => a.status === "pending") : null);
    const [reject, setReject] = useState(null);
    const [preview, setPreview] = useState(demo === "preview" ? { kind: "video", name: "Video giới thiệu khóa học" } : null); // {kind:'video'|'doc', name, ...}
    const [qSearch, setQSearch] = useState("");
    // sample curriculum/material for the course under review
    const reviewLessons = [
      { t: "Bài 1: Giới thiệu tổng quan khóa học", dur: "08:24", kind: "video" },
      { t: "Bài 2: Cài đặt môi trường & công cụ", dur: "12:40", kind: "video" },
      { t: "Bài 3: Thực hành dự án đầu tiên", dur: "21:15", kind: "video" },
    ];
    const reviewDocs = [
      { n: "Giáo trình chi tiết - Chương 1.pdf", s: "2.4 MB", type: "pdf", color: "#ef4444", bg: "#fdecec" },
      { n: "Slide bài giảng buổi 1.pptx", s: "5.1 MB", type: "slide", color: "#f59e0b", bg: "#fef5e6" },
      { n: "Bài tập thực hành.docx", s: "640 KB", type: "doc", color: "#2563eb", bg: "#eaf1ff" },
    ];
    let list = D.approvals;
    if (tab !== "all") list = D.approvals.filter(a => a.status === tab);
    if (qSearch) list = list.filter(a => a.course.toLowerCase().includes(qSearch.toLowerCase()) || a.instructor.toLowerCase().includes(qSearch.toLowerCase()));
    const pg = window.usePaged(list, 10);
    return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Phê duyệt Khóa học</h1><p>Kiểm duyệt nội dung khóa học do giảng viên gửi trước khi xuất bản.</p></div>
        <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns: "repeat(3,1fr)" }}>
          <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value="5" label="Đang chờ duyệt" />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value="142" label="Đã duyệt tháng này" trend={18} />
          <StatCard icon="x" iconBg="#fdecec" iconColor="#dc2626" value="8" label="Đã từ chối" />
        </div>
        <div className="toolbar"><Tabs items={[{v:"pending",label:"Chờ duyệt",count:5},{v:"approved",label:"Đã duyệt"},{v:"rejected",label:"Từ chối"},{v:"all",label:"Tất cả"}]} value={tab} onChange={setTab} /><div className="grow" /><Search placeholder="Tìm khóa học, giảng viên..." value={qSearch} onChange={setQSearch} style={{ width: 280, flex: "none" }} /></div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Khóa học</th><th>Giảng viên</th><th>Danh mục</th><th>Số bài</th><th>Ngày gửi</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>{pg.slice.map(a => (
              <tr key={a.id}>
                <td><b style={{ fontSize: 14, maxWidth: 240, display: "block" }} className="truncate">{a.course}</b></td>
                <td><div className="row gap-9"><Avatar name={a.instructor} size={30} /><span className="t-sm">{a.instructor}</span></div></td>
                <td><span className="chip chip-neutral">{a.cat}</span></td>
                <td><b>{a.lessons}</b></td>
                <td className="muted">{a.date}</td>
                <td><Status s={a.status} /></td>
                <td>{a.status === "pending" ? <div className="row gap-6"><button className="btn btn-soft btn-sm" onClick={() => setDetail(a)}>Xem</button><button className="btn btn-success btn-sm" onClick={() => setDetail(a)}><Ic n="check" size={15} /></button><button className="btn btn-danger btn-sm" onClick={() => setReject(a)}><Ic n="x" size={15} /></button></div> : <button className="btn btn-ghost btn-sm" onClick={() => setDetail(a)}>Chi tiết</button>}</td>
              </tr>
            ))}</tbody>
          </table></div>
        </Section>
        <window.PageBar pg={pg} unit="khóa học" />

        <Modal open={!!detail} onClose={() => setDetail(null)} max={620}>
          {detail && <>
            <div style={{ height: 190, background: `#0f172a url(${D.T.lesson}) center/cover`, borderRadius: "18px 18px 0 0", position: "relative" }}>
              <button className="icon-btn" style={{ position: "absolute", right: 14, top: 14, width: 36, height: 36, background: "rgba(15,23,42,.6)", color: "#fff", border: "none" }} onClick={() => setDetail(null)}><Ic n="x" size={18} /></button>
              <span style={{ position: "absolute", left: 16, bottom: 14 }}><span className="chip" style={{ background: "rgba(15,23,42,.72)", color: "#fff" }}>{detail.cat}</span></span>
              <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center" }}>
                <button onClick={() => setPreview({ kind: "video", name: "Video giới thiệu khóa học", lesson: reviewLessons[0] })}
                  style={{ width: 64, height: 64, borderRadius: 999, border: "none", background: "rgba(255,255,255,.92)", color: "var(--primary)", display: "grid", placeItems: "center", cursor: "pointer", boxShadow: "0 8px 24px rgba(0,0,0,.3)" }}>
                  <Ic n="play" size={26} fill="currentColor" />
                </button>
              </div>
              <span className="chip" style={{ position: "absolute", right: 16, bottom: 14, background: "rgba(15,23,42,.72)", color: "#fff" }}><Ic n="video" size={13} />Xem trước video</span>
            </div>
            <div className="modal-body">
              <h2 className="t-h2" style={{ margin: "0 0 8px" }}>{detail.course}</h2>
              <div className="row gap-10" style={{ marginBottom: 16 }}><Avatar name={detail.instructor} size={34} /><div><div style={{ fontWeight: 600, fontSize: 14 }}>{detail.instructor}</div><div className="t-xs muted">Gửi ngày {detail.date}</div></div></div>
              <div className="grid grid-2" style={{ gap: 12, marginBottom: 18 }}>
                {[{l:"Số bài giảng",v:detail.lessons,ic:"book"},{l:"Thời lượng",v:"~32 giờ",ic:"clock"},{l:"Danh mục",v:detail.cat,ic:"folder"},{l:"Cấp độ",v:"Nâng cao",ic:"trending"}].map((s,i)=>(
                  <div key={i} className="row gap-10" style={{ padding: 12, background: "var(--surface-2)", borderRadius: 11 }}><div className="stat-ic" style={{ width: 38, height: 38, borderRadius: 10, background: "#fff", color: "var(--accent)" }}><Ic n={s.ic} size={18} /></div><div><div className="t-xs muted">{s.l}</div><div style={{ fontWeight: 700, fontSize: 14 }}>{s.v}</div></div></div>
                ))}
              </div>

              <div className="between" style={{ marginBottom: 9 }}><div className="t-label" style={{ margin: 0 }}>Bài giảng (video)</div><span className="t-xs dim">{reviewLessons.length} bài mẫu</span></div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 18 }}>
                {reviewLessons.map((l, i) => (
                  <div key={i} className="row gap-11" style={{ padding: 10, border: "1px solid var(--border)", borderRadius: 11 }}>
                    <div style={{ width: 56, height: 38, borderRadius: 8, flex: "none", background: `#0f172a url(${D.T.lesson}) center/cover`, position: "relative" }}><span style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center", color: "#fff" }}><Ic n="play" size={15} fill="#fff" /></span></div>
                    <div className="grow" style={{ minWidth: 0 }}><div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">{l.t}</div><div className="t-xs muted row gap-5" style={{ marginTop: 2 }}><Ic n="clock" size={12} />{l.dur}</div></div>
                    <button className="btn btn-soft btn-sm" style={{ flex: "none" }} onClick={() => setPreview({ kind: "video", name: l.t, lesson: l })}><Ic n="eye" size={14} />Xem</button>
                  </div>
                ))}
              </div>

              <div className="between" style={{ marginBottom: 9 }}><div className="t-label" style={{ margin: 0 }}>Tài liệu đính kèm</div><span className="t-xs dim">{reviewDocs.length} tệp</span></div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 18 }}>
                {reviewDocs.map((d, i) => (
                  <div key={i} className="row gap-11" style={{ padding: 10, border: "1px solid var(--border)", borderRadius: 11 }}>
                    <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 10, flex: "none", background: d.bg, color: d.color }}><Ic n="file" size={19} /></div>
                    <div className="grow" style={{ minWidth: 0 }}><div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">{d.n}</div><div className="t-xs muted">{d.s} • {d.type.toUpperCase()}</div></div>
                    <button className="btn btn-soft btn-sm" style={{ flex: "none" }} onClick={() => setPreview({ kind: "doc", name: d.n, doc: d })}><Ic n="eye" size={14} />Xem trước</button>
                  </div>
                ))}
              </div>

              <div className="t-label" style={{ marginBottom: 7 }}>Mô tả khóa học</div>
              <p className="muted t-sm" style={{ margin: 0, lineHeight: 1.6 }}>Khóa học cung cấp kiến thức toàn diện, đi từ cơ bản đến nâng cao với các bài tập thực hành và dự án thực tế. Nội dung được biên soạn kỹ lưỡng, phù hợp với học viên muốn nâng cao kỹ năng chuyên môn.</p>
            </div>
            {detail.status === "pending" && <div className="modal-foot"><button className="btn btn-danger" onClick={() => { setReject(detail); setDetail(null); }}><Ic n="x" size={16} />Từ chối</button><button className="btn btn-success" onClick={() => setDetail(null)}><Ic n="check" size={16} />Phê duyệt & Xuất bản</button></div>}
          </>}
        </Modal>

        {/* Content preview lightbox — video player / document viewer */}
        <Modal open={!!preview} onClose={() => setPreview(null)} max={preview && preview.kind === "video" ? 860 : 720}>
          {preview && <>
            <div className="between" style={{ padding: "16px 20px", borderBottom: "1px solid var(--border)" }}>
              <div className="row gap-11">
                <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 11, background: preview.kind === "video" ? "var(--chip-info-bg)" : "var(--surface-3)", color: preview.kind === "video" ? "var(--accent)" : "var(--text-2)" }}><Ic n={preview.kind === "video" ? "video" : "file"} size={20} /></div>
                <div style={{ minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 15 }} className="truncate">{preview.name}</div><div className="t-xs muted">{preview.kind === "video" ? "Xem trước nội dung video" : "Xem trước tài liệu"} • Chế độ kiểm duyệt</div></div>
              </div>
              <button className="icon-btn" style={{ width: 36, height: 36 }} onClick={() => setPreview(null)}><Ic n="x" size={18} /></button>
            </div>

            {preview.kind === "video" ? (
              <div style={{ padding: 16 }}>
                <div style={{ position: "relative", borderRadius: 14, overflow: "hidden", aspectRatio: "16/9", background: `#0a0f1c url(${D.T.lesson}) center/cover` }}>
                  <div style={{ position: "absolute", inset: 0, background: "rgba(8,12,24,.42)", display: "grid", placeItems: "center" }}>
                    <button style={{ width: 78, height: 78, borderRadius: 999, border: "none", background: "rgba(255,255,255,.94)", color: "var(--primary)", display: "grid", placeItems: "center", cursor: "pointer", boxShadow: "0 10px 30px rgba(0,0,0,.4)" }}><Ic n="play" size={32} fill="currentColor" /></button>
                  </div>
                  <span className="chip" style={{ position: "absolute", left: 14, top: 14, background: "rgba(15,23,42,.72)", color: "#fff" }}><Ic n="lock" size={12} />Nội dung bảo mật</span>
                  {/* control bar */}
                  <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, padding: "14px 16px 12px", background: "linear-gradient(transparent, rgba(8,12,24,.85))" }}>
                    <div style={{ height: 5, borderRadius: 999, background: "rgba(255,255,255,.25)", marginBottom: 11 }}><div style={{ width: "34%", height: "100%", borderRadius: 999, background: "var(--success)" }} /></div>
                    <div className="between">
                      <div className="row gap-12" style={{ color: "#fff" }}><Ic n="play" size={18} fill="#fff" /><span className="t-xs mono" style={{ color: "#dbe3f1" }}>02:51 / {preview.lesson ? preview.lesson.dur : "08:24"}</span></div>
                      <div className="row gap-12" style={{ color: "#dbe3f1" }}><span className="t-xs">1080p</span><Ic n="settings" size={16} /></div>
                    </div>
                  </div>
                </div>
                <div className="row gap-10 wrap" style={{ marginTop: 14 }}>
                  <span className="chip chip-info"><Ic n="video" size={13} />MP4 • Streaming bảo mật</span>
                  <span className="chip chip-neutral"><Ic n="clock" size={13} />{preview.lesson ? preview.lesson.dur : "08:24"}</span>
                  <div className="grow" />
                  <button className="btn btn-ghost btn-sm" onClick={() => setPreview(null)}>Đóng</button>
                </div>
              </div>
            ) : (
              <div style={{ padding: 16 }}>
                <div style={{ background: "var(--surface-3)", borderRadius: 14, padding: 22, maxHeight: 420, overflowY: "auto" }}>
                  {/* mock document pages */}
                  {[0, 1].map(p => (
                    <div key={p} style={{ background: "#fff", borderRadius: 8, boxShadow: "var(--sh-sm)", padding: "26px 30px", marginBottom: 16, minHeight: 240 }}>
                      <div style={{ height: 13, width: "55%", borderRadius: 5, background: "#cbd5e1", marginBottom: 16 }} />
                      {[100, 96, 92, 98, 70].map((w, i) => <div key={i} style={{ height: 8, width: w + "%", borderRadius: 4, background: "#e6ecf3", marginBottom: 10 }} />)}
                      <div style={{ height: 90, borderRadius: 8, background: "#eef3fa", margin: "16px 0", display: "grid", placeItems: "center", color: "var(--text-3)" }}><Ic n={preview.doc && preview.doc.type === "slide" ? "layers" : "file"} size={26} /></div>
                      {[94, 88, 60].map((w, i) => <div key={i} style={{ height: 8, width: w + "%", borderRadius: 4, background: "#e6ecf3", marginBottom: 10 }} />)}
                      <div className="t-xs dim" style={{ textAlign: "right", marginTop: 10 }}>Trang {p + 1} / 12</div>
                    </div>
                  ))}
                </div>
                <div className="row gap-10 wrap" style={{ marginTop: 14 }}>
                  <span className="chip chip-neutral"><Ic n="file" size={13} />{preview.doc ? preview.doc.type.toUpperCase() : "PDF"} • {preview.doc ? preview.doc.s : ""}</span>
                  <div className="grow" />
                  <button className="btn btn-ghost btn-sm"><Ic n="download" size={14} />Tải tệp</button>
                  <button className="btn btn-primary btn-sm" onClick={() => setPreview(null)}>Đóng</button>
                </div>
              </div>
            )}
          </>}
        </Modal>

        <Modal open={!!reject} onClose={() => setReject(null)} max={480}>
          {reject && <>
            <ModalHead title="Từ chối khóa học" sub={reject.course} icon="warn" iconBg="#fdecec" iconColor="#dc2626" onClose={() => setReject(null)} />
            <div className="modal-body">
              <div className="t-label" style={{ marginBottom: 7 }}>Lý do từ chối</div>
              <Select value="r1" onChange={()=>{}} options={[{v:"r1",label:"Nội dung chưa đầy đủ"},{v:"r2",label:"Vi phạm bản quyền"},{v:"r3",label:"Chất lượng video kém"},{v:"r4",label:"Lý do khác"}]} />
              <div className="t-label" style={{ margin: "16px 0 7px" }}>Ghi chú cho giảng viên</div>
              <textarea className="input" style={{ height: 100, padding: 12, resize: "none" }} placeholder="Mô tả chi tiết để giảng viên chỉnh sửa..." />
            </div>
            <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setReject(null)}>Hủy</button><button className="btn btn-primary" style={{ background: "var(--error)" }} onClick={() => setReject(null)}>Xác nhận từ chối</button></div>
          </>}
        </Modal>
      </div>
    );
  }


  Object.assign(window, { AdminApproval });
})();
