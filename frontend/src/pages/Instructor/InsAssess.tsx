// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Bài tập & Trắc nghiệm (+ popup thêm câu hỏi / tạo đề)
   ============================================================ */
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon, D = window.DATA, api = window.httpClient;
  const { Avatar, Status, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty, LineChart, BarChart, Donut } = window;

  /* ---------------- Assessments (Bài tập & Quiz) ---------------- */
  function InsAssess({ demo }) {
    const [tab, setTab] = useState((demo === "bank" || demo === "addq") ? "bank" : demo === "random" ? "quiz" : "assign");
    const [add, setAdd] = useState(false);     // create essay assignment
    const [bank, setBank] = useState(demo === "addq");   // add question to bank
    const [random, setRandom] = useState(demo === "random"); // create random quiz
    const [q, setQ] = useState("");
    const [bankCourse, setBankCourse] = useState("all");
    const [correct, setCorrect] = useState(0);

    const [assignments, setAssignments] = useState([]);
    const [loading, setLoading] = useState(false);
    const [editAssignment, setEditAssignment] = useState(null);

    useEffect(() => {
      setLoading(true);
      api.get("/instructor/assignments").then(res => {
        const data = res.data || res;
        setAssignments(Array.isArray(data) ? data : []);
      }).catch(() => {}).finally(() => setLoading(false));
    }, []);

    async function handleEdit(a) {
      try {
        const res = await api.get(`/instructor/courses/${a.courseId}/assignments/${a.id}`);
        setEditAssignment(res.data || res);
      } catch (err) {
        console.error("Lỗi tải chi tiết bài tập:", err);
      }
    }

    const DIFF = { easy:{label:"Dễ",c:"success"}, medium:{label:"Trung bình",c:"warning"}, hard:{label:"Khó",c:"error"} };
    const courseOpts = D.courses.map(c => ({ v: c.title, label: c.title }));

    // ----- ASSIGN tab list -----
    let assignList = assignments;
    if (q) assignList = assignList.filter(a => a.title.toLowerCase().includes(q.toLowerCase()));
    // ----- QUIZ tab = published quizzes -----
    const mockQuizzes = D.assignments.filter(a => a.type === "quiz").map((a, i) => ({ ...a, group: D.groups[i % D.groups.length].name, submitted: Math.floor(Math.random() * 18) + 2 }));
    let quizList = mockQuizzes;
    if (q) quizList = quizList.filter(a => a.title.toLowerCase().includes(q.toLowerCase()));
    // ----- BANK list -----
    let bankList = D.questionBank;
    if (bankCourse !== "all") bankList = bankList.filter(x => x.course === bankCourse);

    const pgAssign = window.usePaged(assignList, 10);
    const pgQuiz = window.usePaged(quizList, 10);
    const pgBank = window.usePaged(bankList, 10);

    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div><h1 className="t-h1">Bài tập & Trắc nghiệm</h1><p>Tạo bài tập tự luận, quản lý ngân hàng câu hỏi và tạo đề trắc nghiệm ngẫu nhiên.</p></div>
          <div className="row gap-10">
            {tab === "assign" && <button className="btn btn-primary" onClick={() => setAdd(true)}><Ic n="plus" size={17} />Tạo bài tập</button>}
            {tab === "quiz" && <button className="btn btn-primary" onClick={() => setRandom(true)}><Ic n="sparkles" size={17} />Tạo bài trắc nghiệm</button>}
            {tab === "bank" && <button className="btn btn-primary" onClick={() => { setCorrect(0); setBank(true); }}><Ic n="plus" size={17} />Thêm câu hỏi</button>}
          </div>
        </div>
        <div className="toolbar">
          <Tabs items={[{v:"assign",label:"Bài tập tự luận",count:assignList.length},{v:"quiz",label:"Đề trắc nghiệm",count:quizList.length},{v:"bank",label:"Ngân hàng câu hỏi",count:D.questionBank.length}]} value={tab} onChange={setTab} />
          <div className="grow" />
          {tab === "bank" && <Select value={bankCourse} onChange={v => { setBankCourse(v); }} options={[{v:"all",label:"Tất cả khóa học"}, ...courseOpts]} style={{ width: 240, flex: "none" }} />}
          {tab !== "bank" && <Search placeholder="Tìm theo tên..." value={q} onChange={setQ} style={{ width: 260, flex: "none" }} />}
        </div>

        {/* ASSIGN + QUIZ tables */}
        {tab !== "bank" ? (
          <>
            <Section pad={false}>
              <div style={{ overflowX: "auto" }}><table className="tbl">
                {tab === "assign" ? (
                  <>
                    <thead><tr><th>Tên bài tập</th><th>Khóa học</th><th>Nhóm áp dụng</th><th>Hạn nộp</th><th>Đã nộp</th><th>Trạng thái</th><th></th></tr></thead>
                    <tbody>{loading ? (
                      <tr><td colSpan={7} style={{ textAlign: "center", padding: 32, color: "#94a3b8", fontSize: 13 }}>Đang tải...</td></tr>
                    ) : pgAssign.slice.length === 0 ? (
                      <tr><td colSpan={7} style={{ textAlign: "center", padding: 32, color: "#94a3b8", fontSize: 13 }}>Chưa có bài tập nào</td></tr>
                    ) : pgAssign.slice.map(a => (
                      <tr key={a.id}>
                        <td><div className="row gap-10"><div className="stat-ic" style={{ width: 36, height: 36, borderRadius: 10, background: "var(--surface-3)", color: "var(--text-2)" }}><Ic n="file" size={17} /></div><b style={{ fontSize: 13.5, maxWidth: 180 }} className="truncate">{a.title}</b></div></td>
                        <td className="muted truncate" style={{ maxWidth: 150 }}>{a.courseTitle || a.courseId}</td>
                        <td className="muted truncate" style={{ maxWidth: 130 }}>{a.scope === "SPECIFIC_GROUPS" ? "Nhóm cụ thể" : "Tất cả"}</td>
                        <td className="muted">{a.deadline ? new Date(a.deadline).toLocaleDateString("vi-VN") : "—"}</td>
                        <td><b>—</b> <span className="muted">/ —</span></td>
                        <td><Status s={a.status === "PUBLISHED" ? "published" : a.status === "CLOSED" ? "closed" : "draft"} /></td>
                        <td><div className="row gap-6"><button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => handleEdit(a)}><Ic n="edit" size={16} /></button><button className="icon-btn" style={{ width: 34, height: 34 }}><Ic n="eye" size={16} /></button></div></td>
                      </tr>
                    ))}</tbody>
                  </>
                ) : (
                  <>
                    <thead><tr><th>Tên bài</th><th>Nhóm áp dụng</th><th>Hạn nộp</th><th>Đã nộp</th><th>Giám sát</th><th>Trạng thái</th><th></th></tr></thead>
                    <tbody>{pgQuiz.slice.map(a => (
                      <tr key={a.id}>
                        <td><div className="row gap-10"><div className="stat-ic" style={{ width: 36, height: 36, borderRadius: 10, background: "var(--surface-3)", color: "var(--text-2)" }}><Ic n="clipboard" size={17} /></div><b style={{ fontSize: 13.5, maxWidth: 220 }} className="truncate">{a.title}</b></div></td>
                        <td className="muted truncate" style={{ maxWidth: 150 }}>{a.group}</td>
                        <td className="muted">{a.deadline}</td>
                        <td><b>{a.submitted}</b> <span className="muted">/ 20</span></td>
                        <td>{a.proctored ? <span className="chip chip-error"><Ic n="shield" size={12} />Bật</span> : <span className="chip chip-neutral">Tắt</span>}</td>
                        <td><Status s={a.id.charCodeAt(2) % 2 ? "published" : "draft"} /></td>
                        <td><div className="row gap-6"><button className="icon-btn" style={{ width: 34, height: 34 }}><Ic n="edit" size={16} /></button><button className="icon-btn" style={{ width: 34, height: 34 }}><Ic n="eye" size={16} /></button></div></td>
                      </tr>
                    ))}</tbody>
                  </>
                )}
              </table></div>
            </Section>
            <window.PageBar pg={tab === "assign" ? pgAssign : pgQuiz} unit={tab === "assign" ? "bài tập" : "đề"} />
          </>
        ) : (
          /* BANK table */
          <>
            <Section pad={false}>
              <div style={{ overflowX: "auto" }}><table className="tbl">
                <thead><tr><th>Câu hỏi</th><th>Khóa học</th><th>Độ khó</th><th>Đáp án đúng</th><th></th></tr></thead>
                <tbody>{pgBank.slice.map(x => (
                  <tr key={x.id}>
                    <td><b style={{ fontSize: 13.5, maxWidth: 320, display: "block" }} className="truncate">{x.q}</b></td>
                    <td className="muted truncate" style={{ maxWidth: 200 }}>{x.course}</td>
                    <td><span className={"chip chip-" + DIFF[x.diff].c}>{DIFF[x.diff].label}</span></td>
                    <td><span className="row gap-7" style={{ color: "var(--success)", fontWeight: 600, fontSize: 13.5 }}><Ic n="check_circle" size={15} />{String.fromCharCode(65 + x.correct)}. {x.a[x.correct]}</span></td>
                    <td><div className="row gap-6"><button className="icon-btn" style={{ width: 34, height: 34 }}><Ic n="edit" size={16} /></button><button className="icon-btn" style={{ width: 34, height: 34, color: "var(--error)" }}><Ic n="x" size={16} /></button></div></td>
                  </tr>
                ))}</tbody>
              </table></div>
            </Section>
            <window.PageBar pg={pgBank} unit="câu hỏi" />
          </>
        )}

        {/* ---- Modal 1: create essay assignment ---- */}
        {add && window.CreateAssignmentModal && React.createElement(window.CreateAssignmentModal, {
          role: "instructor",
          onClose: (refreshed) => { setAdd(false); if (refreshed) { window.location.reload(); } },
        })}

        {/* ---- Modal 1b: edit essay assignment ---- */}
        {editAssignment && window.CreateAssignmentModal && React.createElement(window.CreateAssignmentModal, {
          courseId: editAssignment.courseId,
          assignment: editAssignment,
          role: "instructor",
          onClose: (refreshed) => { setEditAssignment(null); if (refreshed) { window.location.reload(); } },
        })}

        {/* ---- Modal 2: add question to bank ---- */}
        <Modal open={bank} onClose={() => setBank(false)} max={620}>
          <ModalHead title="Thêm câu hỏi vào ngân hàng" sub="Câu hỏi sẽ được lưu vào ngân hàng của khóa học đã chọn" icon="layers" iconBg="#f3edff" iconColor="#7c3aed" onClose={() => setBank(false)} />
          <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Khóa học</label><Select value={D.courses[0].title} onChange={()=>{}} options={courseOpts} /></div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Độ khó</label><Select value="easy" onChange={()=>{}} options={[{v:"easy",label:"Dễ"},{v:"medium",label:"Trung bình"},{v:"hard",label:"Khó"}]} /></div>
            </div>
            <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Nội dung câu hỏi</label><textarea className="input" style={{ height: 72, padding: 12, resize: "none" }} placeholder="VD: Hook nào dùng để ghi nhớ giá trị tính toán nặng?" /></div>
            <div>
              <label className="t-label" style={{ display: "block", marginBottom: 9 }}>Các đáp án — chọn ô tròn để đánh dấu đáp án đúng</label>
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                {[0,1,2,3].map(i => (
                  <div key={i} className="row gap-11" style={{ padding: "8px 12px", border: "1.5px solid " + (correct === i ? "var(--success)" : "var(--border)"), background: correct === i ? "var(--chip-success-bg)" : "#fff", borderRadius: 11 }}>
                    <span onClick={() => setCorrect(i)} title="Đánh dấu đáp án đúng" style={{ width: 22, height: 22, borderRadius: 999, flex: "none", cursor: "pointer", border: "2px solid " + (correct === i ? "var(--success)" : "var(--border-input)"), display: "grid", placeItems: "center" }}>{correct === i && <span style={{ width: 11, height: 11, borderRadius: 999, background: "var(--success)" }} />}</span>
                    <span style={{ fontWeight: 700, color: "var(--text-3)", width: 16 }}>{String.fromCharCode(65 + i)}</span>
                    <input className="input" style={{ height: 38, border: "none", background: "transparent", padding: 0 }} placeholder={"Đáp án " + String.fromCharCode(65 + i)} />
                    {correct === i && <span className="chip chip-success" style={{ flex: "none" }}>Đúng</span>}
                  </div>
                ))}
              </div>
            </div>
          </div>
          <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setBank(false)}>Hủy</button><button className="btn btn-success" onClick={() => setBank(false)}><Ic n="check" size={16} />Lưu vào ngân hàng</button></div>
        </Modal>

        {/* ---- Modal 3: create random quiz ---- */}
        <Modal open={random} onClose={() => setRandom(false)} max={620}>
          <ModalHead title="Tạo bài trắc nghiệm ngẫu nhiên" sub="Hệ thống tự bốc câu hỏi từ ngân hàng theo số lượng & độ khó" icon="sparkles" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => setRandom(false)} />
          <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên bài trắc nghiệm</label><input className="input" placeholder="VD: Kiểm tra giữa kỳ - ReactJS" /></div>
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Khóa học</label><Select value={D.courses[0].title} onChange={()=>{}} options={courseOpts} /></div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Nhóm áp dụng</label><Select value="g1" onChange={()=>{}} options={D.groups.map(g=>({v:g.id,label:g.name}))} /></div>
            </div>
            <div>
              <label className="t-label" style={{ display: "block", marginBottom: 9 }}>Số câu theo độ khó</label>
              <div className="grid" style={{ gridTemplateColumns: "repeat(3,1fr)", gap: 12 }}>
                {[{k:"Dễ",c:"success",d:8},{k:"Trung bình",c:"warning",d:6},{k:"Khó",c:"error",d:4}].map((x,i)=>(
                  <div key={i} style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 12, textAlign: "center" }}>
                    <span className={"chip chip-" + x.c} style={{ marginBottom: 10 }}>{x.k}</span>
                    <input className="input" type="number" defaultValue={x.d} min={0} style={{ textAlign: "center", fontWeight: 700, fontSize: 18, marginTop: 4 }} />
                  </div>
                ))}
              </div>
              <div className="t-xs muted row gap-6" style={{ marginTop: 8 }}><Ic n="sparkles" size={13} />Tổng cộng <b style={{ color: "var(--text)" }}>18 câu</b> sẽ được bốc ngẫu nhiên từ ngân hàng.</div>
            </div>
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Thời gian làm bài (phút)</label><div className="field-icon"><Ic n="clock" /><input className="input" type="number" defaultValue={45} style={{ paddingLeft: 42 }} /></div></div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Hạn chót (deadline)</label><input className="input" type="datetime-local" /></div>
            </div>
            <label className="row gap-10" style={{ padding: "12px 14px", background: "var(--chip-error-bg)", borderRadius: 11, cursor: "pointer" }}><input type="checkbox" style={{ width: 18, height: 18 }} /><div><div style={{ fontWeight: 600, fontSize: 13.5, color: "var(--chip-error-fg)" }}>Bật chế độ giám sát (Proctoring)</div><div className="t-xs" style={{ color: "var(--chip-error-fg)", opacity: .8 }}>Tự động phát hiện chuyển tab khi làm bài</div></div></label>
          </div>
          <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setRandom(false)}>Hủy</button><button className="btn btn-primary" onClick={() => setRandom(false)}><Ic n="sparkles" size={16} />Tạo đề & xuất bản</button></div>
        </Modal>
      </div>
    );
  }


  Object.assign(window, { InsAssess });
})();
