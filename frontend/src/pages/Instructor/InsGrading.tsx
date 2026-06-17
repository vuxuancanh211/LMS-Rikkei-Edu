// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Chấm điểm (+ popup chấm điểm & nhận xét)
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;
  const { Avatar, Status, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  const myCourses = D.courses.filter(c => ["Nguyễn Văn An", "Trần Thị Bình", "Lê Văn Cường", "Phạm Thị Dung"].includes(c.instructor)).slice(0, 9);

  /* ---------------- Grading ---------------- */
  function InsGrading() {
    const [grade, setGrade] = useState(null);
    const [tab, setTab] = useState("all");
    const [group, setGroup] = useState("all");
    const [q, setQ] = useState("");
    const groups = [...new Set(D.submissions.map(s => s.group))];
    let list = D.submissions;
    if (tab !== "all") list = list.filter(s => s.status === tab);
    if (group !== "all") list = list.filter(s => s.group === group);
    if (q) list = list.filter(s => s.student.toLowerCase().includes(q.toLowerCase()) || s.assignment.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 10);
    const dlLabel = group === "all" ? "tất cả nhóm" : group;
    return (
      <div className="page fade-in">
        <div className="page-head between"><div><h1 className="t-h1">Chấm điểm bài tập</h1><p>Xem và chấm điểm các bài nộp của học viên.</p></div>
          <button className="btn btn-primary" onClick={() => alert("Đang tải " + list.length + " file bài nộp (" + dlLabel + ") dưới dạng .zip...")}><Ic n="download" size={16} />Tải tất cả file ({list.length})</button>
        </div>
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value="7" label="Chờ chấm" />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value="3" label="Đã chấm" />
          <StatCard icon="warn" iconBg="#fdecec" iconColor="#dc2626" value="1" label="Nộp trễ" />
          <StatCard icon="target" iconBg="#eaf1ff" iconColor="#2563eb" value="8.6" label="Điểm TB" />
        </div>
        <div className="toolbar">
          <Tabs items={[{v:"all",label:"Tất cả"},{v:"pending",label:"Chờ chấm"},{v:"graded",label:"Đã chấm"},{v:"late",label:"Nộp trễ"}]} value={tab} onChange={setTab} />
          <div className="grow" />
          <Select value={group} onChange={setGroup} options={[{v:"all",label:"Tất cả nhóm"}, ...groups.map(g => ({v:g,label:g}))]} style={{ width: 190, flex: "none" }} />
          <Search placeholder="Tìm học viên, bài tập..." value={q} onChange={setQ} style={{ width: 260, flex: "none" }} />
        </div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Học viên</th><th>Bài tập</th><th>Nhóm</th><th>File nộp</th><th>Thời gian</th><th>Trạng thái</th><th>Điểm</th><th></th></tr></thead>
            <tbody>{pg.slice.map(s => (
              <tr key={s.id}>
                <td><div className="row gap-10"><Avatar name={s.student} size={34} /><b style={{ fontSize: 13.5 }}>{s.student}</b></div></td>
                <td className="truncate" style={{ maxWidth: 180 }}>{s.assignment}</td>
                <td className="muted">{s.group}</td>
                <td><span className="row gap-6 mono" style={{ fontSize: 12.5, color: "var(--accent)" }}><Ic n="file" size={14} />{s.file}</span></td>
                <td className="muted t-sm">{s.submitted}</td>
                <td><Status s={s.status} /></td>
                <td style={{ fontWeight: 700 }}>{s.score ? s.score + "/10" : "—"}</td>
                <td>{s.status === "graded" ? <button className="btn btn-ghost btn-sm" onClick={() => setGrade(s)}><Ic n="eye" size={14} />Xem</button> : <button className="btn btn-primary btn-sm" onClick={() => setGrade(s)}>Chấm</button>}</td>
              </tr>
            ))}</tbody>
          </table></div>
        </Section>
        <window.PageBar pg={pg} unit="bài nộp" />
        <GradeModal sub={grade} onClose={() => setGrade(null)} />
      </div>
    );
  }

  /* ---------------- Grade submission modal (score + feedback) ---------------- */
  function GradeModal({ sub, onClose }) {
    const graded = sub && sub.status === "graded";
    const [score, setScore] = useState("");
    const [fb, setFb] = useState("");
    React.useEffect(() => {
      if (!sub) return;
      if (sub.status === "graded") {
        setScore(String(parseFloat(sub.score) || 8));
        setFb("Bài làm tốt, đúng yêu cầu chức năng. Cần bổ sung thêm xử lý lỗi và comment cho code. Tiếp tục phát huy nhé!");
      } else { setScore(""); setFb(""); }
    }, [sub && sub.id]);
    if (!sub) return null;
    const num = parseFloat(score);
    const hasScore = score !== "" && !isNaN(num);
    const passed = hasScore && num >= 5;

    return (
      <Modal open={!!sub} onClose={onClose} max={560}>
        <ModalHead title={graded ? "Xem & sửa điểm" : "Chấm điểm bài nộp"} sub={sub.student + " • " + sub.assignment} icon="edit" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          {/* submission file */}
          <div>
            <div className="t-label" style={{ marginBottom: 8 }}>Bài nộp của học viên</div>
            <div className="row gap-12" style={{ padding: 13, background: "var(--surface-2)", borderRadius: 12 }}>
              <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 11, background: "var(--chip-info-bg)", color: "var(--accent)", flex: "none" }}><Ic n="file" size={20} /></div>
              <div className="grow" style={{ minWidth: 0 }}><div className="mono truncate" style={{ fontWeight: 600, fontSize: 13.5 }}>{sub.file}</div><div className="t-xs muted">Nộp lúc {sub.submitted} {sub.status === "late" && <span style={{ color: "var(--error)", fontWeight: 600 }}>· Nộp trễ</span>}</div></div>
              <button className="btn btn-soft btn-sm"><Ic n="eye" size={14} />Xem trước</button>
              <button className="btn btn-ghost btn-sm"><Ic n="download" size={14} />Tải</button>
            </div>
          </div>

          {/* score */}
          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 8 }}>Điểm số (thang 10)</label>
            <div className="row gap-12">
              <input className="input" type="number" max={10} min={0} step={0.5} value={score} placeholder="Nhập điểm 0 – 10" onChange={e => setScore(e.target.value)} style={{ flex: 1 }} />
              {hasScore && <span className={"chip " + (passed ? "chip-success" : "chip-error")} style={{ flex: "none", fontSize: 13, padding: "8px 14px" }}>{passed ? "Đạt" : "Chưa đạt"}</span>}
            </div>
          </div>

          {/* feedback */}
          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 8 }}>Nhận xét cho học viên</label>
            <textarea className="input" style={{ height: 120, padding: 12, resize: "none" }} value={fb} onChange={e => setFb(e.target.value)} placeholder="Phản hồi chi tiết giúp học viên cải thiện..." />
          </div>
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
          <button className="btn btn-success" onClick={onClose}><Ic n="check" size={16} />{graded ? "Cập nhật điểm" : "Lưu & gửi học viên"}</button>
        </div>
      </Modal>
    );
  }


  Object.assign(window, { InsGrading });
})();
