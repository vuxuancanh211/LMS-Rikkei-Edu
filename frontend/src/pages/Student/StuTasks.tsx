// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Học viên · Bài tập & Quiz (+ popup nộp bài)
   ============================================================ */
(function () {
const { useState: uS } = React;
const Ic = window.Icon;
const D = window.DATA;
const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

/* ---------------- Tasks (Assignments & Quizzes) ---------------- */
function StuTasks({ nav }) {
  const [tab, setTab] = uS("all");
  const [q, setQ] = uS("");
  const [open, setOpen] = uS(null); // assignment being viewed/submitted
  let list = D.assignments;
  if (tab === "pending") list = D.assignments.filter(a => a.status === "pending" || a.status === "late");
  else if (tab === "done") list = D.assignments.filter(a => a.status === "graded" || a.status === "submitted");
  else if (tab === "quiz") list = D.assignments.filter(a => a.type === "quiz");
  if (q) list = list.filter(a => a.title.toLowerCase().includes(q.toLowerCase()) || a.course.toLowerCase().includes(q.toLowerCase()));
  const pg = window.usePaged(list, 10);
  return (
    <div className="page fade-in">
      <div className="page-head"><h1 className="t-h1">Bài tập & Bài kiểm tra</h1><p>Theo dõi deadline, trạng thái nộp bài và làm bài trắc nghiệm trực tuyến.</p></div>
      <div className="grid grid-stats" style={{ marginBottom: 22 }}>
        <SC icon="clipboard" iconBg="#eaf1ff" iconColor="#2563eb" value="10" label="Tổng số bài" />
        <SC icon="warn" iconBg="#fef5e6" iconColor="#d97706" value="6" label="Chưa hoàn thành" />
        <SC icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value="3" label="Đã chấm điểm" />
        <SC icon="target" iconBg="#f3edff" iconColor="#7c3aed" value="8.8" label="Điểm trung bình" />
      </div>
      <div className="toolbar"><Tb items={[{v:"all",label:"Tất cả"},{v:"pending",label:"Cần làm"},{v:"done",label:"Đã nộp"},{v:"quiz",label:"Trắc nghiệm"}]} value={tab} onChange={setTab} /><div className="grow" /><Se placeholder="Tìm bài tập, khóa học..." value={q} onChange={setQ} style={{ width: 280, flex: "none" }} /></div>
      <Sn pad={false}>
        <div style={{ overflowX: "auto" }}>
          <table className="tbl">
            <thead><tr><th>Tên bài</th><th>Khóa học</th><th>Loại</th><th>Hạn nộp</th><th>Trạng thái</th><th>Điểm</th><th></th></tr></thead>
            <tbody>
              {pg.slice.map(a => (
                <tr key={a.id}>
                  <td><div className="row gap-10"><div className="stat-ic" style={{width:36,height:36,borderRadius:10,background:"var(--surface-3)",color:"var(--text-2)"}}><Ic n={a.type==="quiz"?"clipboard":"file"} size={17}/></div><div style={{fontWeight:600,maxWidth:240}} className="truncate">{a.title}{a.proctored&&<span className="chip chip-error" style={{marginLeft:8,fontSize:11,padding:"2px 8px"}}><Ic n="shield" size={11}/>Giám sát</span>}</div></div></td>
                  <td className="muted truncate" style={{maxWidth:160}}>{a.course}</td>
                  <td><span className="chip chip-neutral">{a.type==="quiz"?"Trắc nghiệm":"Tự luận"}</span></td>
                  <td className="muted">{a.deadline}</td>
                  <td><St s={a.status}/></td>
                  <td style={{fontWeight:700}}>{a.score||"—"}</td>
                  <td>{(a.status==="pending"||a.status==="late")?<button className="btn btn-primary btn-sm" onClick={()=> a.type==="quiz" ? nav("quiz") : setOpen(a)}>{a.type==="quiz"?"Làm bài":"Nộp bài"}</button>:<button className="btn btn-ghost btn-sm" onClick={()=> a.type!=="quiz" ? setOpen(a) : (a.score? nav("result"):setOpen(a))}>Xem</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Sn>
      <window.PageBar pg={pg} unit="bài" />
      <AssignmentModal a={open} onClose={() => setOpen(null)} />
    </div>
  );
}

/* ---------------- Assignment detail + submit popup ---------------- */
function AssignmentModal({ a, onClose }) {
  const [file, setFile] = uS(null);
  if (!a) return null;
  const submitted = a.status === "submitted" || a.status === "graded";
  const graded = a.status === "graded";
  const docs = [
    { n: "Đề bài chi tiết.pdf", s: "1.2 MB", color: "#ef4444", bg: "#fdecec" },
    { n: "File mẫu khởi tạo.zip", s: "320 KB", color: "#2563eb", bg: "#eaf1ff" },
  ];
  const requirement = a.type === "quiz"
    ? "Bài kiểm tra trắc nghiệm trực tuyến. Hãy đọc kỹ từng câu hỏi và chọn đáp án đúng nhất trong thời gian quy định."
    : "Hoàn thành các yêu cầu trong đề bài đính kèm. Nộp mã nguồn dưới dạng file .zip kèm hướng dẫn chạy (README). Bài làm cần đúng yêu cầu chức năng, code rõ ràng, có comment.";
  return (
    <Md open={!!a} onClose={onClose} max={600}>
      <MH title={a.title} sub={a.course} icon={a.type === "quiz" ? "clipboard" : "file"} iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
      <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 18 }}>
        <div className="row gap-8 wrap">
          <span className="chip chip-neutral">{a.type === "quiz" ? "Trắc nghiệm" : "Tự luận"}</span>
          <St s={a.status} />
          {a.proctored && <span className="chip chip-error"><Ic n="shield" size={12} />Có giám sát</span>}
        </div>

        {/* meta grid — info giảng viên đã điền */}
        <div className="grid grid-2" style={{ gap: 10 }}>
          {[{l:"Hạn nộp",v:a.deadline,ic:"calendar"},{l:"Nhóm áp dụng",v:"Nhóm A1 - ReactJS K15",ic:"layers"},{l:"Thang điểm",v:"10 điểm",ic:"target"},{l:"Số lần nộp",v:"Tối đa 3 lần",ic:"upload"}].map((m,i)=>(
            <div key={i} className="row gap-10" style={{ padding: 11, background: "var(--surface-2)", borderRadius: 11 }}>
              <div className="stat-ic" style={{ width: 36, height: 36, borderRadius: 10, background: "#fff", color: "var(--accent)", flex: "none" }}><Ic n={m.ic} size={17} /></div>
              <div style={{ minWidth: 0 }}><div className="t-xs muted">{m.l}</div><div style={{ fontWeight: 700, fontSize: 13.5 }} className="truncate">{m.v}</div></div>
            </div>
          ))}
        </div>

        <div>
          <div className="t-label" style={{ marginBottom: 7 }}>Yêu cầu bài tập</div>
          <p className="muted t-sm" style={{ margin: 0, lineHeight: 1.6 }}>{requirement}</p>
        </div>

        <div>
          <div className="t-label" style={{ marginBottom: 9 }}>Tài liệu đính kèm</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {docs.map((d, i) => (
              <div key={i} className="row gap-11" style={{ padding: 10, border: "1px solid var(--border)", borderRadius: 11 }}>
                <div className="stat-ic" style={{ width: 38, height: 38, borderRadius: 10, background: d.bg, color: d.color, flex: "none" }}><Ic n="file" size={18} /></div>
                <div className="grow" style={{ minWidth: 0 }}><div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">{d.n}</div><div className="t-xs muted">{d.s}</div></div>
                <button className="btn btn-soft btn-sm"><Ic n="download" size={14} />Tải</button>
              </div>
            ))}
          </div>
        </div>

        {/* graded feedback */}
        {graded && (
          <div style={{ background: "var(--chip-success-bg)", borderRadius: 12, padding: 16 }}>
            <div className="between" style={{ marginBottom: 8 }}><span className="t-label" style={{ color: "var(--chip-success-fg)", margin: 0 }}>Kết quả chấm điểm</span><span style={{ fontWeight: 800, fontSize: 22, color: "var(--success)" }}>{a.score}</span></div>
            <p className="t-sm" style={{ margin: 0, color: "var(--chip-success-fg)", lineHeight: 1.55 }}>Bài làm tốt, đúng yêu cầu chức năng. Cần bổ sung thêm xử lý lỗi và comment cho code. Tiếp tục phát huy nhé!</p>
          </div>
        )}

        {/* submit zone */}
        {a.type !== "quiz" && (
          <div>
            <div className="t-label" style={{ marginBottom: 9 }}>{submitted ? "Bài đã nộp" : "Nộp bài làm"}</div>
            {submitted ? (
              <div className="row gap-11" style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 11, background: "var(--surface-2)" }}>
                <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 10, background: "#eaf1ff", color: "var(--accent)", flex: "none" }}><Ic n="file" size={19} /></div>
                <div className="grow" style={{ minWidth: 0 }}><div className="mono truncate" style={{ fontWeight: 600, fontSize: 13.5 }}>baitap_vanem.zip</div><div className="t-xs muted">Đã nộp lúc 30/05/2026 14:20</div></div>
                <span className="chip chip-success"><Ic n="check" size={12} />Đã nộp</span>
              </div>
            ) : (
              <>
                <label htmlFor="subfile" style={{ display: "block", border: "2px dashed var(--border-strong)", borderRadius: 12, padding: 22, textAlign: "center", color: "var(--text-3)", cursor: "pointer", background: file ? "var(--accent-soft)" : "var(--surface-2)" }}>
                  <div className="stat-ic" style={{ width: 46, height: 46, borderRadius: 12, background: "#fff", color: "var(--accent)", margin: "0 auto 10px" }}><Ic n={file ? "check_circle" : "upload"} size={22} /></div>
                  <div style={{ fontWeight: 600, fontSize: 14, color: "var(--text)" }}>{file ? file : "Kéo thả hoặc bấm để chọn file"}</div>
                  <div className="t-xs" style={{ marginTop: 4 }}>ZIP, PDF, DOCX · tối đa 50MB</div>
                  <input id="subfile" type="file" style={{ display: "none" }} onChange={e => setFile(e.target.files[0] ? e.target.files[0].name : null)} />
                </label>
                <textarea className="input" style={{ height: 78, padding: 12, resize: "none", marginTop: 12 }} placeholder="Ghi chú cho giảng viên (không bắt buộc)..." />
              </>
            )}
          </div>
        )}
      </div>
      <div className="modal-foot">
        <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
        {a.type === "quiz"
          ? <button className="btn btn-primary"><Ic n="play" size={16} fill="#fff" />Vào làm bài</button>
          : submitted
            ? <button className="btn btn-ghost" disabled>Đã nộp bài</button>
            : <button className="btn btn-success" onClick={onClose}><Ic n="upload" size={16} />Nộp bài</button>}
      </div>
    </Md>
  );
}

window.StuTasks = StuTasks;
})();
