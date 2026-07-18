// @ts-nocheck
/* ============================================================
     RIKKEI EDU – Học viên · Tổng quan khóa học
     Trang trung gian giữa "Khóa học của tôi" và trình học (LecturePlayer) —
     chỉ dành cho khóa đã ghi danh, không có luồng mua/đăng ký công khai.
   ============================================================ */
(function () {
  const { useState, useEffect, useMemo } = React;
  const Ic = window.Icon;
  const { Empty } = window;
  const api = window.httpClient;

  const LEVEL_LABEL = { BEGINNER: "Cơ bản", INTERMEDIATE: "Trung cấp", ADVANCED: "Nâng cao" };

  function fmtDuration(totalSeconds) {
    if (!totalSeconds) return "0 phút";
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.round((totalSeconds % 3600) / 60);
    if (h > 0) return `${h} giờ${m ? " " + m + " phút" : ""}`;
    return `${m} phút`;
  }
  function fmtLessonDuration(s) {
    if (!s) return null;
    return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
  }

  function Card({ title, children }) {
    return (
      <div className="card card-pad" style={{ marginBottom: 20 }}>
        {title && <h2 className="t-h3" style={{ marginTop: 0, marginBottom: 16 }}>{title}</h2>}
        {children}
      </div>
    );
  }

  /**
   * previewMode: true khi giảng viên bấm "Xem trước" — dùng lại đúng giao diện học viên thấy
   * nhưng đọc dữ liệu qua endpoint /instructor (không cần ghi danh) và ẩn phần tiến độ cá nhân
   * (giảng viên không "học" khóa của mình). onBack/onEnterContent chỉ dùng khi previewMode
   * (đóng overlay / mở trình xem bài giảng).
   */
  function StuCourseDetail({ nav, courseId, previewMode, previewRole, onBack, onEnterContent }) {
    const isPreview = !!previewMode;
    const role = previewRole || "instructor"; // "instructor" | "admin" — chỉ có ý nghĩa khi isPreview
    const [course, setCourse]     = useState(null);
    const [loading, setLoading]   = useState(true);
    const [err, setErr]           = useState(null);
    const [openCh, setOpenCh]     = useState<Record<string, boolean>>({});
    const [descExpanded, setDescExpanded] = useState(false);

    useEffect(() => {
      if (!courseId) { setLoading(false); return; }
      setLoading(true); setErr(null);
      const base = !isPreview ? `/student/courses/${courseId}`
        : role === "admin" ? `/admin/courses/${courseId}`
        : `/instructor/courses/${courseId}`;
      api.get(base).then(r => {
        setCourse(r.data);
        const opened = {};
        (r.data?.chapters || []).forEach((ch, i) => { opened[ch.id] = i === 0; });
        setOpenCh(opened);
      }).catch(e => setErr(e?.response?.data?.message || "Không thể tải khóa học"))
        .finally(() => setLoading(false));
    }, [courseId, isPreview, role]);

    const chapters = course?.chapters || [];
    const allLessons = useMemo(() => chapters.flatMap(ch => ch.lessons || []), [chapters]);
    const totalLessons = allLessons.length;
    const completedCount = allLessons.filter(l => l.progress === "COMPLETED").length;
    const progressPct = totalLessons > 0 ? Math.round((completedCount / totalLessons) * 100) : 0;
    const totalDurationSeconds = allLessons.reduce((sum, l) => sum + (l.durationSeconds || 0), 0);

    const goBack = () => (isPreview ? onBack?.() : nav("courses"));

    if (!courseId) return (
      <div className="page fade-in">
        <Empty icon="book" title="Chưa chọn khóa học" sub="Quay lại danh sách và chọn một khóa học." />
        <button className="btn btn-ghost" style={{ marginTop: 24 }} onClick={goBack}><Ic n="arrow_left" size={16} />Quay lại</button>
      </div>
    );
    if (loading) return <div className="page fade-in"><div className="muted" style={{ textAlign: "center", padding: 80 }}>Đang tải...</div></div>;
    if (err || !course) return (
      <div className="page fade-in">
        <div style={{ color: "var(--error)", padding: 24 }}>{err || "Không tìm thấy khóa học"}</div>
        <button className="btn btn-ghost" onClick={goBack}><Ic n="arrow_left" size={16} />Quay lại</button>
      </div>
    );

    const levelLabel = LEVEL_LABEL[course.level] || course.level;
    const plainDesc = (course.description || "").replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
    const outcomes = course.learningOutcomes || [];
    const requirements = course.requirements || [];

    return (
      <div className="page fade-in" style={{ minHeight: "100%" }}>
        {/* Hero — lấn ra ngoài padding của .page bằng margin âm trên chính div này (không phải
            trên .page) để tránh margin-collapsing với <main> (mép ngoài không có padding/border
            sẽ "rò" margin âm ra ngoài, kéo lệch cả layout cha) — margin âm chỉ an toàn khi phần
            tử cha (.page) có padding khác 0 chặn collapse lại. */}
        <div style={{ margin: "calc(var(--pad-page) * -1) calc(var(--pad-page) * -1) 0", padding: "32px 32px 90px", background: "linear-gradient(135deg,#1e3a5f,#2563eb)", color: "#fff" }}>
          <div className="row between wrap" style={{ marginBottom: 16 }}>
            <button className="btn btn-ghost btn-sm" style={{ background: "rgba(255,255,255,.12)", border: "none", color: "#fff" }}
              onClick={goBack}><Ic n="arrow_left" size={15} />{!isPreview ? "Khóa học của tôi" : role === "admin" ? "Quay lại phê duyệt" : "Quay lại"}</button>
            {isPreview && (
              <span className="chip" style={{ background: "#fef9c3", color: "#92400e", fontWeight: 700 }}>
                <Ic n="eye" size={12} />Xem trước — giao diện học viên
              </span>
            )}
          </div>
          <div className="row gap-10 wrap" style={{ marginBottom: 14 }}>
            {course.category?.name && <span className="chip" style={{ background: "rgba(255,255,255,.18)", color: "#fff" }}>{course.category.name}</span>}
            {levelLabel && <span className="chip" style={{ background: "rgba(255,255,255,.18)", color: "#fff" }}>{levelLabel}</span>}
          </div>
          <h1 style={{ fontSize: 30, fontWeight: 800, margin: "0 0 14px", maxWidth: 720, lineHeight: 1.25 }}>{course.title}</h1>
          <div className="row gap-16 wrap" style={{ color: "rgba(255,255,255,.85)", fontSize: 13.5 }}>
            <span className="row gap-6"><Ic n="users" size={14} />{course.studentCount || 0} học viên</span>
            {course.instructorName && <span className="row gap-6"><Ic n="user" size={14} />Giảng viên {course.instructorName}</span>}
          </div>
        </div>

        {/* Body */}
        <div style={{ maxWidth: 1280, margin: "-64px auto 0", padding: "0 32px 60px" }}>
          <div className="row gap-24" style={{ alignItems: "flex-start", flexWrap: "wrap" }}>

            {/* Left column */}
            <div style={{ flex: "1 1 620px", minWidth: 460, marginTop: 64 }}>

              {outcomes.length > 0 && (
                <Card title="Bạn sẽ học được gì">
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px 20px" }}>
                    {outcomes.map((o, i) => (
                      <div key={i} className="row gap-8" style={{ alignItems: "flex-start" }}>
                        <span style={{ color: "#10b981", flex: "none", marginTop: 1 }}><Ic n="check" size={14} /></span>
                        <span className="t-sm">{o}</span>
                      </div>
                    ))}
                  </div>
                </Card>
              )}

              <Card title="Nội dung khóa học">
                <div className="row between" style={{ marginBottom: 12, color: "var(--text-3)" }}>
                  <span className="t-xs">{chapters.length} chương · {totalLessons} bài · {fmtDuration(totalDurationSeconds)}</span>
                </div>
                {chapters.map((ch, ci) => {
                  const isOpen = openCh[ch.id];
                  const lessons = ch.lessons || [];
                  return (
                    <div key={ch.id} style={{ border: "1px solid var(--border)", borderRadius: 10, marginBottom: 10, overflow: "hidden" }}>
                      <div onClick={() => setOpenCh(p => ({ ...p, [ch.id]: !p[ch.id] }))}
                        className="row between" style={{ padding: "12px 16px", background: "var(--surface-2)", cursor: "pointer" }}>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: 14 }}>{ci + 1}. {ch.title}</div>
                          <div className="t-xs muted" style={{ marginTop: 3 }}>{lessons.length} bài</div>
                        </div>
                        <Ic n="chevron_down" size={15} style={{ color: "var(--text-3)", transform: isOpen ? "none" : "rotate(-90deg)", transition: ".2s" }} />
                      </div>
                      {isOpen && lessons.map((l, li) => (
                        <div key={l.id} className="row gap-10" style={{ padding: "9px 16px", borderTop: "1px solid var(--border)" }}>
                          <span style={{ color: "var(--text-3)", flex: "none" }}>
                            <Ic n={l.type === "VIDEO" ? "play" : l.type === "QUIZ" ? "clipboard" : "file_text"} size={13} />
                          </span>
                          <span className="t-sm" style={{ flex: 1 }}>{li + 1}. {l.title}</span>
                          {l.progress === "COMPLETED" && <Ic n="check" size={13} style={{ color: "#10b981" }} />}
                          {fmtLessonDuration(l.durationSeconds) && <span className="t-xs muted">{fmtLessonDuration(l.durationSeconds)}</span>}
                        </div>
                      ))}
                    </div>
                  );
                })}
              </Card>

              {requirements.length > 0 && (
                <Card title="Yêu cầu">
                  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    {requirements.map((r, i) => (
                      <div key={i} className="row gap-8" style={{ alignItems: "flex-start" }}>
                        <span style={{ width: 5, height: 5, borderRadius: 999, background: "var(--text-3)", flex: "none", marginTop: 7 }} />
                        <span className="t-sm">{r}</span>
                      </div>
                    ))}
                  </div>
                </Card>
              )}

              <Card title="Mô tả chi tiết">
                {plainDesc ? (
                  <>
                    <div className="t-sm" style={{ lineHeight: 1.7,
                        maxHeight: descExpanded ? "none" : 90, overflow: "hidden" }}
                      dangerouslySetInnerHTML={{ __html: course.description }} />
                    {plainDesc.length > 160 && (
                      <button className="btn-link" style={{ marginTop: 8, background: "none", border: "none", color: "var(--accent)", fontWeight: 600, cursor: "pointer", padding: 0 }}
                        onClick={() => setDescExpanded(v => !v)}>{descExpanded ? "Thu gọn" : "Xem thêm"}</button>
                    )}
                  </>
                ) : <span className="muted t-sm">Chưa có mô tả cho khóa học này.</span>}
              </Card>

              <Card title="Giảng viên">
                <div className="row gap-16" style={{ alignItems: "flex-start" }}>
                  <div className="avatar" style={{ width: 56, height: 56, fontSize: 20, flex: "none" }}>
                    {(course.instructorName || "?").split(" ").slice(-2).map(w => w[0]).join("").toUpperCase()}
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 700, fontSize: 15 }}>{course.instructorName}</div>
                    {course.instructorCourseCount != null && (
                      <div className="t-xs muted" style={{ marginTop: 2 }}>{course.instructorCourseCount} khóa học đã xuất bản</div>
                    )}
                    {course.instructorBio && <p className="t-sm" style={{ marginTop: 10, lineHeight: 1.6 }}>{course.instructorBio}</p>}
                  </div>
                </div>
              </Card>
            </div>

            {/* Right column: sticky CTA */}
            <div style={{ flex: "0 0 340px", minWidth: 300, marginTop: 24, position: "sticky", top: "calc(var(--header-h) + 20px)" }}>
              <div className="card" style={{ overflow: "hidden" }}>
                <div style={{ aspectRatio: "16/9", position: "relative",
                  background: course.thumbnailUrl ? `#0f172a url(${course.thumbnailUrl}) center/cover` : "linear-gradient(135deg,#1e3a5f,#2563eb)" }} />
                <div className="card-pad">
                  <div className="row gap-8" style={{ padding: "10px 14px", borderRadius: 8, background: "var(--accent-soft)", marginBottom: 16 }}>
                    <Ic n="check_circle" size={16} style={{ color: "var(--accent)", flex: "none" }} />
                    <span className="t-sm" style={{ fontWeight: 600, color: "var(--accent)" }}>Học mọi lúc, mọi nơi — không giới hạn thời gian</span>
                  </div>

                  {!isPreview && totalLessons > 0 && (
                    <div style={{ marginBottom: 14 }}>
                      <div className="row between t-xs muted" style={{ marginBottom: 6 }}>
                        <span>Tiến độ của bạn</span><span style={{ fontWeight: 700 }}>{progressPct}%</span>
                      </div>
                      <div style={{ height: 8, borderRadius: 999, background: "var(--surface-2)", overflow: "hidden" }}>
                        <div style={{ width: `${progressPct}%`, height: "100%", background: "#10b981", borderRadius: 999 }} />
                      </div>
                    </div>
                  )}

                  <button className="btn btn-primary btn-block"
                    onClick={() => (isPreview ? onEnterContent?.() : nav("player", { courseId }))}>
                    {isPreview ? "Xem nội dung khóa học" : (progressPct > 0 ? `Tiếp tục học (${progressPct}%)` : "Vào học")}
                  </button>

                  <div style={{ marginTop: 20, paddingTop: 18, borderTop: "1px solid var(--border)", display: "flex", flexDirection: "column", gap: 12 }}>
                    <div className="row gap-10 t-sm"><Ic n="book" size={15} style={{ color: "var(--text-3)" }} />{totalLessons} bài giảng</div>
                    <div className="row gap-10 t-sm"><Ic n="clock" size={15} style={{ color: "var(--text-3)" }} />{fmtDuration(totalDurationSeconds)} tổng thời lượng</div>
                    {levelLabel && <div className="row gap-10 t-sm"><Ic n="clipboard" size={15} style={{ color: "var(--text-3)" }} />Cấp độ {levelLabel}</div>}
                    <div className="row gap-10 t-sm"><Ic n="award" size={15} style={{ color: "var(--text-3)" }} />Có chứng chỉ hoàn thành</div>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
      </div>
    );
  }

  Object.assign(window, { StuCourseDetail });
})();
