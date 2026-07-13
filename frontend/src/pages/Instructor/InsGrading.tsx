// @ts-nocheck
(function () {
  const { useState, useEffect, useRef } = React;
  const Ic = window.Icon;
  const { Avatar, Status, StatCard, Search, Tabs, Section, Pager, Modal, ModalHead, Empty } = window;
  const api = window.httpClient;

  function fmtTime(dt) {
    if (!dt) return "—";
    return new Date(dt).toLocaleDateString("vi-VN", {
      day: "2-digit", month: "2-digit", year: "numeric",
      hour: "2-digit", minute: "2-digit",
    });
  }

  function InsGrading() {
    const [grade, setGrade] = useState(null);
    const [tab, setTab] = useState("all");
    const [q, setQ] = useState("");

    const [showFilter, setShowFilter] = useState(false);
    const [courses, setCourses] = useState([]);
    const [allAssignments, setAllAssignments] = useState([]);
    const [filterCourseId, setFilterCourseId] = useState("");
    const [filterAssignmentId, setFilterAssignmentId] = useState("");
    const [filterGroup, setFilterGroup] = useState("all");

    const [submissions, setSubmissions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [selected, setSelected] = useState(new Set());
    const [releasing, setReleasing] = useState(false);
    const [previewFiles, setPreviewFiles] = useState(null);

    const filterRef = useRef(null);

    const filteredAssignments = allAssignments.filter(a =>
      !filterCourseId || a.courseId === filterCourseId
    );
    const filteredAssignmentsOptions = filteredAssignments.map(a => ({ v: a.id, label: a.title }));

    const groups = [...new Set(submissions.map(s => s.groupName).filter(Boolean))];

    let list = submissions;
    if (tab !== "all") {
      if (tab === "late") list = list.filter(s => s.isLate);
      else list = list.filter(s => s.status === tab);
    }
    if (filterGroup !== "all") list = list.filter(s => s.groupName === filterGroup);
    if (q) {
      const lq = q.toLowerCase();
      list = list.filter(s =>
        (s.studentName && s.studentName.toLowerCase().includes(lq)) ||
        (s.assignmentTitle && s.assignmentTitle.toLowerCase().includes(lq))
      );
    }

    const pg = window.usePaged(list, 10);

    const totalCount = submissions.length;
    const submittedCount = submissions.filter(s => s.status === "submitted").length;
    const gradedCount = submissions.filter(s => s.status === "graded").length;
    const lateCount = submissions.filter(s => s.isLate).length;
    const notSubmittedCount = submissions.filter(s => s.status === "not_submitted").length;
    const scores = submissions.filter(s => s.score != null).map(s => s.score);
    const avgScore = scores.length ? (scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1) : "—";

    useEffect(() => {
      api.get("/instructor/assignments").then(res => {
        setAllAssignments(res.data || []);
      });
      if (window.__courseService?.getMyCourses) {
        window.__courseService.getMyCourses()
          .then(res => setCourses(res.content || []))
          .catch(() => {});
      }
    }, []);

    useEffect(() => {
      setLoading(true);
      const params = { status: "ALL" };
      if (filterCourseId) params.courseId = filterCourseId;
      if (filterAssignmentId) params.assignmentId = filterAssignmentId;
      api.get("/instructor/submissions", { params })
        .then(res => { setSubmissions((res.data || []).map(s => ({ ...s, status: s.status?.toLowerCase() }))); setSelected(new Set()); })
        .catch(() => setSubmissions([]))
        .finally(() => setLoading(false));
    }, [filterCourseId, filterAssignmentId]);

    useEffect(() => {
      const handler = e => { if (filterRef.current && !filterRef.current.contains(e.target)) setShowFilter(false); };
      document.addEventListener("mousedown", handler);
      return () => document.removeEventListener("mousedown", handler);
    }, []);

    const handleGrade = async (sub, data) => {
      const res = await api.patch("/instructor/submissions/grade", {
        submissionId: sub.id, score: data.score, feedback: data.feedback
      });
      setSubmissions(prev => prev.map(s => s.id === sub.id ? { ...s, ...res.data, status: (res.data.status || "").toLowerCase() } : s));
      setGrade(null);
    };

    const handleRelease = async () => {
      setReleasing(true);
      try {
        await api.patch("/instructor/submissions/batch/release", {
          submissionIds: [...selected]
        });
        setSubmissions(prev => prev.map(s =>
          selected.has(s.id) ? { ...s, scorePublishedAt: new Date().toISOString() } : s
        ));
        setSelected(new Set());
      } finally { setReleasing(false); }
    };

    const hasSelection = selected.size > 0;
    const selectedForRelease = submissions.filter(s => selected.has(s.id) && s.status === "graded" && !s.scorePublishedAt);

    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div>
            <h1 className="t-h1">Chấm điểm bài tập</h1>
            <p>Xem và chấm điểm các bài nộp của học viên.</p>
          </div>
        </div>

        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value={submittedCount} label="Chờ chấm" />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={gradedCount} label="Đã chấm" />
          <StatCard icon="warn" iconBg="#fdecec" iconColor="#dc2626" value={lateCount} label="Nộp trễ" />
          <StatCard icon="clock" iconBg="#f1f5f9" iconColor="#64748b" value={notSubmittedCount} label="Chưa nộp" />
          <StatCard icon="target" iconBg="#eaf1ff" iconColor="#2563eb" value={avgScore} label="Điểm TB" />
        </div>

        <div className="toolbar" ref={filterRef}>
          <div className="row gap-8" style={{ position: "relative" }}>
            <button className="btn btn-soft btn-sm" onClick={() => setShowFilter(!showFilter)}>
              <Ic n="filter" size={15} /> Lọc
              {(filterCourseId || filterAssignmentId) && <span className="chip chip-primary" style={{ marginLeft: 6, padding: "0 6px", fontSize: 11 }}>!</span>}
            </button>
            {showFilter && (
              <div style={{
                position: "absolute", top: "100%", left: 0, zIndex: 100,
                background: "var(--surface)", border: "1px solid var(--border)",
                borderRadius: 12, padding: 16, width: 320, marginTop: 6,
                boxShadow: "0 8px 24px rgba(0,0,0,.08)", display: "flex", flexDirection: "column", gap: 12
              }}>
                <div>
                  <label className="t-label" style={{ display: "block", marginBottom: 6 }}>Khóa học</label>
                  <select className="input" value={filterCourseId} onChange={e => { setFilterCourseId(e.target.value); setFilterAssignmentId(""); setFilterGroup("all"); }}
                    style={{ width: "100%" }}>
                    <option value="">Tất cả khóa học</option>
                    {courses.map(c => <option key={c.id} value={c.id}>{c.title}</option>)}
                  </select>
                </div>
                <div>
                  <label className="t-label" style={{ display: "block", marginBottom: 6 }}>Bài tập</label>
                  <select className="input" value={filterAssignmentId} onChange={e => { setFilterAssignmentId(e.target.value); setFilterGroup("all"); }}
                    style={{ width: "100%" }}>
                    <option value="">Tất cả bài tập</option>
                    {filteredAssignmentsOptions.map(o => <option key={o.v} value={o.v}>{o.label}</option>)}
                  </select>
                </div>
                <div>
                  <label className="t-label" style={{ display: "block", marginBottom: 6 }}>Nhóm</label>
                  <select className="input" value={filterGroup} onChange={e => setFilterGroup(e.target.value)}
                    style={{ width: "100%" }}>
                    <option value="all">Tất cả nhóm</option>
                    {groups.map(g => <option key={g} value={g}>{g}</option>)}
                  </select>
                </div>
                <div className="row gap-8" style={{ justifyContent: "flex-end", marginTop: 4 }}>
                  <button className="btn btn-ghost btn-sm" onClick={() => {
                    setFilterCourseId(""); setFilterAssignmentId(""); setFilterGroup("all");
                    setShowFilter(false);
                  }}>Đặt lại</button>
                  <button className="btn btn-primary btn-sm" onClick={() => setShowFilter(false)}>Áp dụng</button>
                </div>
              </div>
            )}
          </div>
          <Tabs items={[
            { v: "all", label: "Tất cả" },
            { v: "submitted", label: "Đã nộp" },
            { v: "graded", label: "Đã chấm" },
            { v: "late", label: "Trễ hạn" },
            { v: "not_submitted", label: "Chưa nộp" },
            { v: "returned", label: "Trả lại" }
          ]} value={tab} onChange={setTab} />
          <Search placeholder="Tìm học viên, bài tập..." value={q} onChange={setQ} style={{ width: 220, flex: "none" }} />
        </div>

        <Section pad={false}>
          {allAssignments.length === 0 ? (
            <Empty icon="clipboard" text="Chưa có bài tập nào để chấm điểm" />
          ) : loading ? (
            <div className="t-center muted" style={{ padding: "40px 0" }}>Đang tải...</div>
          ) : (<>
            <div className="row" style={{ padding: "10px 16px", borderBottom: "1px solid var(--border)", alignItems: "center", justifyContent: "space-between" }}>
              <div className="t-sm muted">Đã nộp <b>{totalCount - notSubmittedCount}</b> / <b>{totalCount}</b> học viên</div>
              {hasSelection && selectedForRelease.length > 0 && (
                <button className="btn btn-primary btn-sm" onClick={handleRelease} disabled={releasing}>
                  <Ic n="check" size={15} /> Công bố điểm ({selectedForRelease.length})
                </button>
              )}
            </div>
            <div style={{ overflowX: "auto" }}>
              <table className="tbl">
                <thead>
                  <tr>
                    <th style={{ width: 36, textAlign: "center" }}>#</th>
                    <th style={{ width: 36 }}>
                      <input type="checkbox" checked={selected.size > 0 && selected.size === list.length}
                        onChange={e => {
                          if (e.target.checked) setSelected(new Set(list.filter(s => s.status === "graded" && !s.scorePublishedAt).map(s => s.id)));
                          else setSelected(new Set());
                        }} />
                    </th>
                    <th>Học viên</th>
                    <th>Nhóm</th>
                    <th>Khóa học</th>
                    <th>Bài tập</th>
                    <th style={{ width: 80 }}>Lần nộp</th>
                    <th>File nộp</th>
                    <th style={{ width: 130 }}>Thời gian</th>
                    <th>Trạng thái</th>
                    <th style={{ width: 80 }}>Điểm</th>
                    <th style={{ width: 70 }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pg.slice.map((s, i) => (
                    <tr key={s.id}>
                      <td className="t-sm muted" style={{ textAlign: "center" }}>{pg.from + i}</td>
                      <td>
                        {s.status === "graded" && !s.scorePublishedAt ? (
                          <input type="checkbox" checked={selected.has(s.id)}
                            onChange={e => {
                              const next = new Set(selected);
                              e.target.checked ? next.add(s.id) : next.delete(s.id);
                              setSelected(next);
                            }} />
                        ) : null}
                      </td>
                      <td><div className="row gap-10"><Avatar name={s.studentName} size={34} /><b style={{ fontSize: 13.5 }}>{s.studentName}</b></div></td>
                      <td className="muted" style={{ wordBreak: "break-word", whiteSpace: "normal", maxWidth: 160 }}>{s.groupName}</td>
                      <td style={{ wordBreak: "break-word", whiteSpace: "normal", maxWidth: 160 }}>{s.courseTitle}</td>
                      <td style={{ wordBreak: "break-word", whiteSpace: "normal", maxWidth: 160 }}>{s.assignmentTitle}</td>
                      <td>{s.submissionNumber}{s.assignmentMaxSubmissions ? "/" + s.assignmentMaxSubmissions : ""}</td>
                      <td>
                        {s.files && s.files.length > 0 ? (
                          <span className="row gap-6 mono" style={{ fontSize: 12.5, color: "var(--accent)", cursor: "pointer" }}
                            onClick={() => setPreviewFiles({
                              files: s.files.map(f => ({ name: f.originalFilename, url: f.url, mimeType: f.mimeType, size: f.fileSizeBytes })),
                              idx: 0
                            })}>
                            <Ic n="file" size={14} />{s.files[0].originalFilename}
                          </span>
                        ) : <span className="muted">—</span>}
                      </td>
                      <td>
                        <div style={{ display:"flex", flexDirection:"column", gap:4 }}>
                          <span className="muted t-sm">{fmtTime(s.submittedAt)}</span>
                          {s.status === "late" && <span className="chip chip-error" style={{ fontSize:11, alignSelf:"flex-start" }}>Nộp trễ</span>}
                        </div>
                      </td>
                      <td><Status s={s.status === "late" ? "submitted" : s.status} /></td>
                      <td style={{ fontWeight: 700 }}>
                        {s.score != null ? s.score + (s.assignmentMaxScore ? "/" + s.assignmentMaxScore : "") : "—"}
                        {s.scorePublishedAt && <Ic n="check_circle" size={13} style={{ color: "var(--success)", marginLeft: 4 }} />}
                      </td>
                      <td>
                        {s.status === "not_submitted" ? (
                          <span className="muted t-sm">—</span>
                        ) : s.status === "graded" ? (
                          <button className="btn btn-ghost btn-sm" onClick={() => setGrade(s)}><Ic n="eye" size={14} />Xem</button>
                        ) : (
                          <button className="btn btn-primary btn-sm" onClick={() => setGrade(s)}>Chấm</button>
                        )}
                      </td>
                    </tr>
                  ))}
                  {pg.slice.length === 0 && (
                    <tr><td colSpan={12} className="t-center muted" style={{ padding: 24 }}>Không có bài nộp nào</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </>)}
        </Section>
        <window.PageBar pg={pg} unit="bài nộp" />
        <GradeModal sub={grade} onClose={() => setGrade(null)} onGrade={handleGrade} maxScore={grade?.assignmentMaxScore} onPreview={(files, idx) => setPreviewFiles({ files, idx })} />
        {previewFiles && React.createElement(window.FilePreview, {
          files: previewFiles.files,
          initialIdx: previewFiles.idx,
          onClose: () => setPreviewFiles(null),
        })}
      </div>
    );
  }

  function GradeModal({ sub, onClose, onGrade, maxScore, onPreview }) {
    const graded = sub && sub.status === "graded";
    const [score, setScore] = useState("");
    const [fb, setFb] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    React.useEffect(() => {
      if (!sub) return;
      if (sub.status === "graded" && sub.score != null) {
        setScore(String(sub.score));
        setFb(sub.feedback || "");
      } else { setScore(""); setFb(""); }
      setError("");
    }, [sub && sub.id]);

    if (!sub) return null;
    const num = parseFloat(score);
    const hasScore = score !== "" && !isNaN(num);
    const max = maxScore || 10;
    const passed = hasScore && num >= max * 0.5;

    const handleSave = async () => {
      if (!hasScore) { setError("Vui lòng nhập điểm"); return; }
      if (num < 0 || num > max) { setError("Điểm phải từ 0 đến " + max); return; }
      setSubmitting(true);
      setError("");
      try {
        await onGrade(sub, { score: num, feedback: fb });
      } catch (e) {
        setError("Lưu điểm thất bại");
      } finally { setSubmitting(false); }
    };

    return (
      <Modal open={!!sub} onClose={onClose} max={560}>
        <ModalHead title={graded ? "Xem & sửa điểm" : "Chấm điểm bài nộp"}
          sub={(sub.studentName || "") + " • " + (sub.assignmentTitle || "")}
          icon="edit" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          <div>
            <div className="t-label" style={{ marginBottom: 8 }}>Bài nộp của học viên</div>
            {sub.files && sub.files.map(f => (
              <div key={f.id} className="row gap-12" style={{ padding: 13, background: "var(--surface-2)", borderRadius: 12, marginBottom: 6 }}>
                <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 11, background: "var(--chip-info-bg)", color: "var(--accent)", flex: "none" }}>
                  <Ic n="file" size={20} />
                </div>
                <div className="grow" style={{ minWidth: 0 }}>
                  <div className="mono truncate" style={{ fontWeight: 600, fontSize: 13.5 }}>{f.originalFilename}</div>
                  <div className="t-xs muted">
                    {sub.submittedAt ? "Nộp lúc " + fmtTime(sub.submittedAt) : ""}
                    {sub.isLate && <span style={{ color: "var(--error)", fontWeight: 600 }}> · Nộp trễ</span>}
                  </div>
                </div>
                {f.url && <button className="btn btn-soft btn-sm" onClick={() => onPreview(
                  sub.files.map(f => ({ name: f.originalFilename, url: f.url, mimeType: f.mimeType, size: f.fileSizeBytes })),
                  sub.files.indexOf(f)
                )}><Ic n="eye" size={14} />Xem trước</button>}
                {f.url && <a className="btn btn-ghost btn-sm" href={f.url} download><Ic n="download" size={14} />Tải</a>}
              </div>
            ))}
          </div>

          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 8 }}>Điểm số (thang {max})</label>
            <div className="row gap-12">
              <input className="input" type="number" max={max} min={0} step={0.5} value={score}
                placeholder={"Nhập điểm 0 – " + max} onChange={e => setScore(e.target.value)} style={{ flex: 1 }} />
              {hasScore && <span className={"chip " + (passed ? "chip-success" : "chip-error")}
                style={{ flex: "none", fontSize: 13, padding: "8px 14px" }}>{passed ? "Đạt" : "Chưa đạt"}</span>}
            </div>
          </div>

          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 8 }}>Nhận xét cho học viên</label>
            <textarea className="input" style={{ height: 120, padding: 12, resize: "none" }} value={fb}
              onChange={e => setFb(e.target.value)} placeholder="Phản hồi chi tiết giúp học viên cải thiện..." />
          </div>
          {error && <div className="t-sm" style={{ color: "var(--error)" }}>{error}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
          {!graded && (
            <button className="btn btn-soft" onClick={handleSave} disabled={submitting}>
              <Ic n="check" size={16} />Lưu điểm
            </button>
          )}
          {graded && (
            <button className="btn btn-success" onClick={handleSave} disabled={submitting}>
              <Ic n="check" size={16} />Cập nhật điểm
            </button>
          )}
        </div>
      </Modal>
    );
  }

  Object.assign(window, { InsGrading });
})();
