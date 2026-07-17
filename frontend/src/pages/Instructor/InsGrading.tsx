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
    const [draftCourseId, setDraftCourseId] = useState("");
    const [draftAssignmentId, setDraftAssignmentId] = useState("");
    const [draftGroup, setDraftGroup] = useState("all");
    const [courseGroupsMap, setCourseGroupsMap] = useState({});

    const [submissions, setSubmissions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [selected, setSelected] = useState(new Set());
    const [releasing, setReleasing] = useState(false);
    const [previewFiles, setPreviewFiles] = useState(null);
    const [viewAssignment, setViewAssignment] = useState(null);

    const filterRef = useRef(null);

    const filteredAssignments = allAssignments.filter(a => {
      if (draftCourseId && a.courseId !== draftCourseId) return false;
      if (!draftGroup || draftGroup === "all") return a.scope === "ALL_GROUPS";
      if (a.scope === "ALL_GROUPS") return true;
      return a.groupNames?.includes(draftGroup);
    });
    const filteredAssignmentsOptions = filteredAssignments.map(a => ({ v: a.id, label: a.title }));

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
    const releasableInList = list.filter(s => s.status === "graded" && !s.scorePublishedAt);

    const totalCount = submissions.length;
    const submittedCount = submissions.filter(s => s.status === "submitted").length;
    const gradedCount = submissions.filter(s => s.status === "graded").length;
    const lateCount = submissions.filter(s => s.isLate).length;
    const notSubmittedCount = submissions.filter(s => s.status === "not_submitted").length;

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
        .then(res => { setSubmissions((res.data || []).map(s => ({ ...s, status: s.status ? s.status.toLowerCase() : "not_submitted" }))); setSelected(new Set()); })
        .catch(() => setSubmissions([]))
        .finally(() => setLoading(false));
    }, [filterCourseId, filterAssignmentId]);

    useEffect(() => {
      if (!draftCourseId || courseGroupsMap[draftCourseId]) return;
      api.get("/instructor/submissions", { params: { courseId: draftCourseId, status: "ALL" } })
        .then(res => {
          const gs = [...new Set((res.data || []).map(s => s.groupName).filter(Boolean))];
          setCourseGroupsMap(prev => ({ ...prev, [draftCourseId]: gs }));
        })
        .catch(() => {});
    }, [draftCourseId]);

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

    const handleGradeAndPublish = async (sub, data) => {
      const res = await api.patch("/instructor/submissions/grade", {
        submissionId: sub.id, score: data.score, feedback: data.feedback
      });
      await api.patch("/instructor/submissions/batch/release", {
        submissionIds: [sub.id]
      });
      setSubmissions(prev => prev.map(s =>
        s.id === sub.id ? { ...s, ...res.data, status: "graded", scorePublishedAt: new Date().toISOString() } : s
      ));
      setGrade(null);
    };

    const handlePublishOne = async (sub) => {
      await api.patch("/instructor/submissions/batch/release", {
        submissionIds: [sub.id]
      });
      setSubmissions(prev => prev.map(s =>
        s.id === sub.id ? { ...s, scorePublishedAt: new Date().toISOString() } : s
      ));
      setGrade(null);
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
        </div>

        <div className="toolbar" ref={filterRef}>
          <div className="row gap-8" style={{ position: "relative" }}>
            <button className="btn btn-soft btn-sm" onClick={() => { if (!showFilter) { setDraftCourseId(filterCourseId); setDraftAssignmentId(filterAssignmentId); setDraftGroup(filterGroup); } setShowFilter(!showFilter); }}>
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
                  <select className="input" value={draftCourseId} onChange={e => { setDraftCourseId(e.target.value); setDraftAssignmentId(""); setDraftGroup("all"); }}
                    style={{ width: "100%" }}>
                    <option value="">Tất cả khóa học</option>
                    {courses.map(c => <option key={c.id} value={c.id}>{c.title}</option>)}
                  </select>
                </div>
                <div>
                  <label className="t-label" style={{ display: "block", marginBottom: 6 }}>Nhóm</label>
                  <select className="input" value={draftGroup} disabled={!draftCourseId} onChange={e => { setDraftGroup(e.target.value); setDraftAssignmentId(""); }}
                    style={{ width: "100%" }}>
                    <option value="all">{draftCourseId ? "Tất cả nhóm" : "Chọn khóa học trước"}</option>
                    {(courseGroupsMap[draftCourseId] || []).map(g => <option key={g} value={g}>{g}</option>)}
                  </select>
                </div>
                <div>
                  <label className="t-label" style={{ display: "block", marginBottom: 6 }}>Bài tập</label>
                  <select className="input" value={draftAssignmentId} disabled={!draftCourseId} onChange={e => { setDraftAssignmentId(e.target.value); }}
                    style={{ width: "100%" }}>
                    <option value="">{draftCourseId ? "Tất cả bài tập" : "Chọn khóa học trước"}</option>
                    {filteredAssignmentsOptions.map(o => <option key={o.v} value={o.v}>{o.label}</option>)}
                  </select>
                </div>
                <div className="row gap-8" style={{ justifyContent: "flex-end", marginTop: 4 }}>
                  <button className="btn btn-ghost btn-sm" onClick={() => {
                    setDraftCourseId(""); setDraftAssignmentId(""); setDraftGroup("all");
                    setFilterCourseId(""); setFilterAssignmentId(""); setFilterGroup("all");
                    setShowFilter(false);
                  }}>Đặt lại</button>
                  <button className="btn btn-primary btn-sm" onClick={() => {
                    setFilterCourseId(draftCourseId); setFilterAssignmentId(draftAssignmentId); setFilterGroup(draftGroup);
                    setShowFilter(false);
                  }}>Áp dụng</button>
                </div>
              </div>
            )}
          </div>
          <Tabs items={[
            { v: "all", label: "Tất cả" },
            { v: "submitted", label: "Đã nộp" },
            { v: "graded", label: "Đã chấm" },
            { v: "late", label: "Trễ hạn" },
            { v: "not_submitted", label: "Chưa nộp" }
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
                  <tr style={{ fontSize: "10.5" }}>
                    <th style={{ width: 26, textAlign: "center" }}>#</th>
                    <th style={{ width: 22 }}>
                      {releasableInList.length > 0 ? (
                        <input type="checkbox" checked={selected.size > 0 && selected.size === releasableInList.length}
                          onChange={e => {
                            if (e.target.checked) setSelected(new Set(list.filter(s => s.status === "graded" && !s.scorePublishedAt).map(s => s.id)));
                            else setSelected(new Set());
                          }} style={{ accentColor: "#2563eb", cursor: "pointer", width: 11, height: 11, margin: 0 }} />
                      ) : null}
                    </th>
                    <th style={{ minWidth: 110 }}>Học viên</th>
                    <th style={{ minWidth: 100, wordBreak: "break-word" }}>Khóa học</th>
                    <th style={{ minWidth: 100, wordBreak: "break-word" }}>Nhóm</th>
                    <th style={{ minWidth: 140 }}>Bài tập</th>
                    <th style={{ minWidth: 100 }}>File nộp</th>
                    <th style={{ width: 100 }}>Thời gian</th>
                    <th style={{ width: 70 }}>Trạng thái</th>
                    <th style={{ width: 60, textAlign: "center" }}>Điểm</th>
                  </tr>
                </thead>
                <tbody>
                    {pg.slice.map((s, i) => (
                    <tr key={s.studentId + '-' + s.assignmentId}>
                      <td className="t-sm muted" style={{ textAlign: "center" }}>{pg.from + i}</td>
                      <td style={{ textAlign: "center", padding: "4px 2px" }}>
                        {s.status === "graded" && !s.scorePublishedAt ? (
                          <input type="checkbox" checked={selected.has(s.id)}
                            onChange={e => {
                              const next = new Set(selected);
                              e.target.checked ? next.add(s.id) : next.delete(s.id);
                              setSelected(next);
                            }} style={{ accentColor: "#2563eb", cursor: "pointer", width: 11, height: 11, margin: 0 }} />
                        ) : null}
                      </td>
                      <td style={{ fontSize: 12 }}>{s.studentName}</td>
                      <td style={{ wordBreak: "break-word", whiteSpace: "normal", fontSize: 12, maxWidth: 140 }}>{s.courseTitle}</td>
                      <td className="muted" style={{ wordBreak: "break-word", whiteSpace: "normal", fontSize: 12, maxWidth: 140 }}>{s.groupName}</td>
                      <td style={{ maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", cursor: "pointer", color: "var(--accent)", fontSize: 12 }}
                        title={s.assignmentTitle}
                        onClick={() => setViewAssignment({ id: s.assignmentId, courseId: s.courseId })}>
                        {s.assignmentTitle}
                      </td>
                      <td>
                        {s.files && s.files.length > 0 ? (
                          <span className="row gap-4 mono" style={{ fontSize: 12, color: "var(--accent)", cursor: "pointer", maxWidth: 150, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", display: "inline-flex", alignItems: "center" }}
                            title={s.files.map(f => f.originalFilename).join(", ")}
                            onClick={() => setPreviewFiles({
                              files: s.files.map(f => ({ name: f.originalFilename, url: f.url, mimeType: f.mimeType, size: f.fileSizeBytes })),
                              idx: 0
                            })}>
                            <Ic n="file" size={12} style={{ flexShrink: 0 }} /><span className="truncate">{s.files[0].originalFilename}</span>
                          </span>
                        ) : <span className="muted" style={{ fontSize: 12 }}>—</span>}
                      </td>
                      <td>
                        <div style={{ display:"flex", flexDirection:"column", gap: 2, fontSize: 12 }}>
                          <span className="muted">{fmtTime(s.submittedAt)}</span>
                          {s.isLate && <span className="chip chip-error" style={{ fontSize: 10, alignSelf:"flex-start", padding: "1px 5px" }}>Nộp trễ</span>}
                        </div>
                      </td>
                      <td style={{ fontSize: 12, whiteSpace: "nowrap", textAlign: "center" }}>
                        {s.status === "not_submitted" ? <span className="chip" style={{ fontSize: 11, padding: "1px 6px", background: "#f1f5f9", color: "#64748b" }}>Chưa nộp</span>
                         : s.status === "graded" ? <span className="chip" style={{ fontSize: 11, padding: "1px 6px", background: "#e7f8f0", color: "#059669" }}>Đã chấm</span>
                         : <span className="chip" style={{ fontSize: 11, padding: "1px 6px", background: "#eaf1ff", color: "#2563eb" }}>Đã nộp</span>}
                      </td>
                      <td style={{ textAlign: "center", verticalAlign: "middle" }}>
                        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 3 }}>
                          <span style={{ fontWeight: 600, fontSize: 12 }}>
                            {s.score != null ? s.score + (s.assignmentMaxScore ? "/" + s.assignmentMaxScore : "") : <span className="muted" style={{ fontSize: 12 }}>—</span>}
                            {s.scorePublishedAt && <Ic n="check_circle" size={10} style={{ color: "var(--success)", marginLeft: 2 }} />}
                          </span>
                          {s.status === "not_submitted" ? (
                            <span className="muted" style={{ fontSize: 12 }}>—</span>
                          ) : s.status === "graded" ? (
                            <button className="btn btn-ghost btn-sm" style={{ fontSize: 12, height: 24, padding: "0 6px" }} onClick={() => setGrade(s)}>Xem</button>
                          ) : (
                            <button className="btn btn-primary btn-sm" style={{ fontSize: 12, height: 24, padding: "0 6px" }} onClick={() => setGrade(s)}>Chấm</button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                    {pg.slice.length === 0 && (
                    <tr><td colSpan={10} className="t-center muted" style={{ padding: 24 }}>Không có bài nộp nào</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </>)}
        </Section>
        <window.PageBar pg={pg} unit="bài nộp" />
        <GradeModal sub={grade} onClose={() => setGrade(null)} onGrade={handleGrade} onGradeAndPublish={handleGradeAndPublish} onPublish={handlePublishOne} maxScore={grade?.assignmentMaxScore} assignmentPassScore={grade?.assignmentPassScore} onPreview={(files, idx) => setPreviewFiles({ files, idx })} published={!!grade?.scorePublishedAt} />
        {previewFiles && React.createElement(window.FilePreview, {
          files: previewFiles.files,
          initialIdx: previewFiles.idx,
          onClose: () => setPreviewFiles(null),
        })}
        {viewAssignment && (
          <div style={{ position: "fixed", inset: 0, zIndex: 1000, background: "#fff",
            display: "flex", flexDirection: "column" }}>
            {React.createElement(window.AssignmentDetail, {
              assignmentId: viewAssignment.id,
              courseId: viewAssignment.courseId,
              role: "instructor",
              onBack: () => setViewAssignment(null),
            })}
          </div>
        )}
      </div>
    );
  }

  function GradeModal({ sub, onClose, onGrade, onGradeAndPublish, onPublish, maxScore, assignmentPassScore, onPreview, published }) {
    const graded = sub && sub.status === "graded";
    const readonly = published;
    const [score, setScore] = useState("");
    const [fb, setFb] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [scoreError, setScoreError] = useState("");

    const initScore = React.useMemo(() => {
      if (!sub) return "";
      if (sub.status === "graded" && sub.score != null) return String(sub.score);
      return "";
    }, [sub?.id, sub?.status, sub?.score]);
    const initFb = React.useMemo(() => {
      if (!sub) return "";
      if (sub.status === "graded") return sub.feedback || "";
      return "";
    }, [sub?.id, sub?.status, sub?.feedback]);
    const dirty = score !== initScore || fb !== initFb;

    React.useEffect(() => {
      if (!sub) return;
      if (sub.status === "graded" && sub.score != null) {
        setScore(String(sub.score));
        setFb(sub.feedback || "");
      } else { setScore(""); setFb(""); }
      setError("");
      setScoreError("");
    }, [sub && sub.id]);

    if (!sub) return null;
    const num = parseFloat(score);
    const hasScore = score !== "" && !isNaN(num);
    const max = maxScore || 10;
    const passThreshold = assignmentPassScore != null ? Number(assignmentPassScore) : max * 0.5;
    const passed = hasScore && num >= passThreshold;

    function validateScore(v) {
      const n = parseFloat(v);
      if (v === "" || isNaN(n)) return "";
      if (n < 0) return "Điểm không được nhỏ hơn 0";
      const limit = Math.min(max, 100);
      if (n > limit) return "Điểm không được vượt quá " + limit;
      return "";
    }

    const scoreErr = validateScore(score);

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

    const handleSaveAndPublish = async () => {
      if (!hasScore) { setError("Vui lòng nhập điểm"); return; }
      if (num < 0 || num > max) { setError("Điểm phải từ 0 đến " + max); return; }
      setSubmitting(true);
      setError("");
      try {
        await onGradeAndPublish(sub, { score: num, feedback: fb });
      } catch (e) {
        setError("Lưu & công bố thất bại");
      } finally { setSubmitting(false); }
    };

    const handlePublish = async () => {
      setSubmitting(true);
      setError("");
      try {
        await onPublish(sub);
      } catch (e) {
        setError("Công bố thất bại");
      } finally { setSubmitting(false); }
    };

    return (
      <Modal open={!!sub} onClose={onClose} max={560}>
        <ModalHead title={readonly ? "Điểm đã công bố" : graded ? "Xem & sửa điểm" : "Chấm điểm bài nộp"}
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
            <div className="row gap-12" style={{ flexDirection: "column", gap: 4 }}>
              <div className="row gap-12">
                <input className="input" type="number" max={max} min={0} step={0.5} value={score}
                  placeholder={"Nhập điểm 0 – " + max}
                  onChange={e => { setScore(e.target.value); setScoreError(validateScore(e.target.value)); }}
                  style={{ flex: 1, borderColor: scoreError ? "#dc2626" : undefined }} disabled={readonly} />
                {hasScore && <span className={"chip " + (passed ? "chip-success" : "chip-error")}
                  style={{ flex: "none", fontSize: 13, padding: "8px 14px" }}>{passed ? "Đạt" : "Chưa đạt"}</span>}
              </div>
              {scoreError && <span style={{ color: "#dc2626", fontSize: 11 }}>{scoreError}</span>}
            </div>
          </div>

          <div>
            <label className="t-label" style={{ display: "block", marginBottom: 8 }}>Nhận xét cho học viên</label>
            <textarea className="input" style={{ height: 120, padding: 12, resize: "none" }} value={fb}
              onChange={e => setFb(e.target.value)} placeholder="Phản hồi chi tiết giúp học viên cải thiện..." disabled={readonly} />
          </div>
          {error && <div className="t-sm" style={{ color: "var(--error)" }}>{error}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{readonly ? "Đóng" : "Hủy"}</button>
          {!readonly && !graded && (
            <>
              <button className="btn btn-soft" onClick={handleSave} disabled={submitting || !!scoreError}>
                <Ic n="check" size={16} />Lưu điểm
              </button>
              <button className="btn btn-success" onClick={handleSaveAndPublish} disabled={submitting || !!scoreError}>
                <Ic n="check" size={16} />Lưu & Công bố
              </button>
            </>
          )}
          {!readonly && graded && dirty && (
            <>
              <button className="btn btn-soft" onClick={handleSave} disabled={submitting || !!scoreError}>
                <Ic n="check" size={16} />Cập nhật điểm
              </button>
              <button className="btn btn-success" onClick={handleSaveAndPublish} disabled={submitting || !!scoreError}>
                <Ic n="check" size={16} />Cập nhật & Công bố
              </button>
            </>
          )}
          {!readonly && graded && !dirty && (
            <button className="btn btn-success" onClick={handlePublish} disabled={submitting}>
              <Ic n="check" size={16} />Công bố điểm
            </button>
          )}
        </div>
      </Modal>
    );
  }

  Object.assign(window, { InsGrading });
})();
