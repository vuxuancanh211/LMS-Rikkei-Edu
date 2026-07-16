// @ts-nocheck
/* ============================================================
   RIKKEI EDU — StuDashboard (Separated Granular APIs with Retry)
   ============================================================ */
(function () {
  const { useState, useEffect, useCallback, useMemo } = React;
  const Ic = window.Icon;
  const api = window.httpClient;
  const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

  function SectionRetryBox({ onRetry, text = "Không thể tải dữ liệu phần này" }) {
    return (
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "28px 16px", background: "var(--surface-2, #f8fafc)", borderRadius: 12, border: "1px dashed var(--border, #cbd5e1)", margin: "8px 0" }}>
        <div style={{ color: "var(--error, #ef4444)", marginBottom: 10, display: "flex", alignItems: "center", gap: 6, fontWeight: 600, fontSize: 14 }}>
          <Ic n="alert_circle" size={18} /> {text}
        </div>
        <button className="btn btn-soft btn-sm" onClick={onRetry} style={{ border: "1px solid var(--border, #cbd5e1)", display: "flex", alignItems: "center", gap: 6, cursor: "pointer" }}>
          <Ic n="refresh" size={14} /> Thử lại (Retry)
        </button>
      </div>
    );
  }

  /* ---------------- Student Dashboard ---------------- */
  function StuDashboard({ nav }) {
    const [statsState, setStatsState] = useState({ data: null, loading: true, error: false });
    const [inProgressState, setInProgressState] = useState({ data: null, loading: true, error: false });
    const [dueState, setDueState] = useState({ data: null, loading: true, error: false });
    const [weeklyHoursState, setWeeklyHoursState] = useState({ data: null, loading: true, error: false });
    const [quizzesState, setQuizzesState] = useState({ data: null, loading: true, error: false });

    const loadStats = useCallback(async (force = false) => {
      setStatsState(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getStudentStats) {
          res = await window.__dashboardService.getStudentStats(force === true);
        } else if (api) {
          const r = await api.get('/student/dashboard/stats');
          res = r.data;
        }
        setStatsState({ data: res || {}, loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load student stats:", err);
        setStatsState(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadInProgress = useCallback(async (force = false) => {
      setInProgressState(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getStudentInProgressCourses) {
          res = await window.__dashboardService.getStudentInProgressCourses(force === true);
        } else if (api) {
          const r = await api.get('/student/dashboard/in-progress-courses');
          res = r.data;
        }
        setInProgressState({ data: Array.isArray(res) ? res : [], loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load in progress courses:", err);
        setInProgressState(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadDue = useCallback(async (force = false) => {
      setDueState(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getStudentDueAssignments) {
          res = await window.__dashboardService.getStudentDueAssignments(force === true);
        } else if (api) {
          const r = await api.get('/student/dashboard/due-assignments');
          res = r.data;
        }
        setDueState({ data: Array.isArray(res) ? res : [], loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load due assignments:", err);
        setDueState(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadWeeklyHours = useCallback(async (force = false) => {
      setWeeklyHoursState(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getStudentWeeklyStudyHours) {
          res = await window.__dashboardService.getStudentWeeklyStudyHours(force === true);
        } else if (api) {
          const r = await api.get('/student/dashboard/weekly-study-hours');
          res = r.data;
        }
        setWeeklyHoursState({ data: Array.isArray(res) ? res : [0,0,0,0,0,0,0], loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load weekly study hours:", err);
        setWeeklyHoursState(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    const loadQuizzes = useCallback(async (force = false) => {
      setQuizzesState(prev => ({ ...prev, loading: true, error: false }));
      try {
        let res;
        if (window.__dashboardService?.getStudentDueQuizzes) {
          res = await window.__dashboardService.getStudentDueQuizzes(force === true);
        } else if (api) {
          const r = await api.get('/student/dashboard/due-quizzes');
          res = r.data;
        }
        setQuizzesState({ data: Array.isArray(res) ? res : [], loading: false, error: false });
      } catch (err) {
        console.debug("Failed to load student due quizzes:", err);
        setQuizzesState(prev => ({ ...prev, loading: false, error: true }));
      }
    }, []);

    useEffect(() => {
      loadStats();
      loadInProgress();
      loadDue();
      loadWeeklyHours();
      loadQuizzes();
    }, [loadStats, loadInProgress, loadDue, loadWeeklyHours, loadQuizzes]);

    const studentName = statsState.data?.studentName || "Học viên";
    const stats = statsState.data?.stats || {
      activeCoursesCount: 0,
      nearCompletionCoursesCount: 0,
      pendingTasksCount: 0,
      dueSoonTasksCount: 0,
      certificatesCount: 0,
      weeklyHours: 0,
      weeklyHoursTrend: null
    };

    const inProgress = (inProgressState.data || []).slice(0, 4);
    const due = (dueState.data || []).slice(0, 6);
    const weeklyHoursData = (weeklyHoursState.data && weeklyHoursState.data.length > 0)
      ? weeklyHoursState.data : [0,0,0,0,0,0,0];
    const quizzes = (quizzesState.data || []).slice(0, 6);

    return (
      <div className="page fade-in">
        <div className="page-head">
          <h1 className="t-display">Chào mừng trở lại, {studentName} 👋</h1>
          <p>Hôm nay là ngày tốt để tiếp tục hành trình học tập của bạn.</p>
        </div>

        {statsState.error ? (
          <SectionRetryBox onRetry={loadStats} text="Lỗi khi tải thông số học tập" />
        ) : (
          <div className="grid grid-stats" style={{ marginBottom: 22 }}>
            <SC icon="cap" iconBg="#eaf1ff" iconColor="#2563eb" value={statsState.loading ? "..." : String(stats.activeCoursesCount)} label="Khóa học đang học" sub={stats.nearCompletionCoursesCount > 0 ? `${stats.nearCompletionCoursesCount} sắp hoàn thành` : "Đang tiến hành"} trend={null} />
            <SC icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value={statsState.loading ? "..." : String(stats.pendingTasksCount)} label="Bài tập cần làm" sub={stats.dueSoonTasksCount > 0 ? `${stats.dueSoonTasksCount} sắp đến hạn` : "Chưa hoàn thành"} />
            <SC icon="award" iconBg="#e7f8f0" iconColor="#059669" value={statsState.loading ? "..." : String(stats.certificatesCount)} label="Chứng chỉ đạt được" trend={null} />
            <SC icon="clock" iconBg="#f3edff" iconColor="#7c3aed" value={statsState.loading ? "..." : `${stats.weeklyHours}h`} label="Giờ học tuần này" trend={stats.weeklyHoursTrend || null} />
          </div>
        )}

        <div className="grid grid-3-2" style={{ marginBottom: 22 }}>
          <div style={{ display: "flex", flexDirection: "column", gap: 22 }}>
            <Sn title="Tiếp tục học" sub="Các khóa học bạn đang theo dõi" action={<span className="link" onClick={() => nav("courses")}>Xem tất cả</span>} pad={false} style={{ height: 570, display: "flex", flexDirection: "column" }} bodyStyle={{ flex: 1, overflowY: "auto" }}>
              {inProgressState.error ? (
                <div style={{ padding: 12 }}><SectionRetryBox onRetry={loadInProgress} text="Lỗi khi tải danh sách khóa học" /></div>
              ) : (
                <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 12, minHeight: "100%" }}>
                  {inProgressState.loading && <div className="t-sm muted" style={{ display: "grid", placeItems: "center", height: "100%", flex: 1 }}>Đang tải khóa học...</div>}
                  {!inProgressState.loading && inProgress.length === 0 && (
                    <div className="t-sm muted" style={{ display: "grid", placeItems: "center", height: "100%", flex: 1, textAlign: "center" }}>
                      Bạn chưa tham gia khóa học nào đang tiến hành. <span className="link" onClick={() => nav("courses")}>Khám phá ngay</span>
                    </div>
                  )}
                  {!inProgressState.loading && inProgress.slice(0, 5).map(c => (
                    <div key={c.id} className="row gap-16" style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 14, cursor: "pointer", flex: "none" }} onClick={() => nav("player", { courseId: c.id, from: "dashboard" })}>
                      <div style={{ width: 92, height: 64, borderRadius: 10, backgroundImage: `url(${c.thumbnailUrl || "assets/courses/placeholder.png"})`, backgroundSize: "cover", backgroundPosition: "center", flex: "none" }} />
                      <div className="grow">
                        <div className="t-xs" style={{ color: "var(--accent)", fontWeight: 700, marginBottom: 4 }}>{c.category}</div>
                        <div style={{ fontWeight: 700, fontSize: 15 }} className="clamp-1 truncate">{c.title}</div>
                        <div style={{ marginTop: 8 }}><Pg value={c.progress} /></div>
                      </div>
                      <button className="btn btn-primary btn-sm" style={{ flex: "none" }}><Ic n="play" size={15} fill="#fff" />Học</button>
                    </div>
                  ))}
                </div>
              )}
            </Sn>
            <Sn title="Hoạt động học tập" sub="Số giờ học 7 ngày qua" style={{ height: 318 }}>
              {weeklyHoursState.error ? (
                <SectionRetryBox onRetry={loadWeeklyHours} text="Lỗi khi tải biểu đồ hoạt động" />
              ) : weeklyHoursState.loading ? (
                <div style={{ height: 210, display: "grid", placeItems: "center" }}><span className="t-sm dim">Đang tải biểu đồ...</span></div>
              ) : (
                <LC data={weeklyHoursData} labels={["Mon","Tue","Wed","Thu","Fri","Sat","Sun"]} color="#7c3aed" height={210} unit="giờ" />
              )}
            </Sn>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 22 }}>
            <Sn title="Bài tập sắp đến hạn" action={<span className="link" onClick={() => nav("tasks")}>Tất cả</span>} pad={false} style={{ height: 444, display: "flex", flexDirection: "column" }} bodyStyle={{ flex: 1, overflowY: "auto" }}>
              {dueState.error ? (
                <div style={{ padding: 12 }}><SectionRetryBox onRetry={loadDue} text="Lỗi khi tải bài tập sắp đến hạn" /></div>
              ) : (
                <div style={{ padding: 10, display: "flex", flexDirection: "column", gap: 10, minHeight: "100%" }}>
                  {dueState.loading && <div className="t-sm muted" style={{ display: "grid", placeItems: "center", height: "100%", flex: 1 }}>Đang tải bài tập...</div>}
                  {!dueState.loading && due.length === 0 && (
                    <div className="t-sm muted" style={{ display: "grid", placeItems: "center", height: "100%", flex: 1, textAlign: "center" }}>
                      Tuyệt vời! Bạn không có bài tập nào cần nộp gấp.
                    </div>
                  )}
                  {!dueState.loading && due.slice(0, 5).map(a => {
                    const st = a.status === "pending" ? "assignment_pending" : a.status;
                    return (
                      <div key={a.id} className="row gap-12" style={{ padding: 12, borderRadius: 11, cursor: "pointer", flex: "none" }} onClick={() => nav("tasks")}>
                        <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 11, background: a.status === "late" ? "var(--chip-error-bg)" : "var(--chip-warning-bg)", color: a.status === "late" ? "var(--error)" : "var(--warning)" }}>
                          <Ic n={a.type === "quiz" ? "clipboard" : "file"} size={19} />
                        </div>
                        <div className="grow" style={{ minWidth: 0 }}>
                          <div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">{a.title}</div>
                          <div className="t-xs muted row gap-6" style={{ marginTop: 3 }}><Ic n="calendar" size={13} />Hạn: {a.deadline}</div>
                        </div>
                        <St s={st} />
                      </div>
                    );
                  })}
                </div>
              )}
            </Sn>
            <Sn title="Quiz sắp đến hạn" action={<span className="link" onClick={() => nav("quizzes")}>Tất cả</span>} pad={false} style={{ height: 444, display: "flex", flexDirection: "column" }} bodyStyle={{ flex: 1, overflowY: "auto" }}>
              {quizzesState.error ? (
                <div style={{ padding: 12 }}><SectionRetryBox onRetry={loadQuizzes} text="Lỗi khi tải danh sách quiz" /></div>
              ) : (
                <div style={{ padding: 10, display: "flex", flexDirection: "column", gap: 10, minHeight: "100%" }}>
                  {quizzesState.loading && <div className="t-sm muted" style={{ display: "grid", placeItems: "center", height: "100%", flex: 1 }}>Đang tải quiz...</div>}
                  {!quizzesState.loading && quizzes.length === 0 && (
                    <div className="t-sm muted" style={{ display: "grid", placeItems: "center", height: "100%", flex: 1, textAlign: "center" }}>
                      Bạn chưa có bài quiz nào cần làm.
                    </div>
                  )}
                  {!quizzesState.loading && quizzes.slice(0, 5).map(q => {
                    const st = q.status === "pending" ? "quiz_pending" : q.status;
                    return (
                      <div key={q.id} className="row gap-12" style={{ padding: 12, borderRadius: 11, cursor: "pointer", flex: "none" }} onClick={() => nav("quizzes")}>
                        <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 11, background: q.status === "late" ? "var(--chip-error-bg)" : "var(--chip-warning-bg)", color: q.status === "late" ? "var(--error)" : "var(--warning)" }}>
                          <Ic n="clipboard" size={19} />
                        </div>
                        <div className="grow" style={{ minWidth: 0 }}>
                          <div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">{q.title}</div>
                          <div className="t-xs muted row gap-6" style={{ marginTop: 3 }}><Ic n="calendar" size={13} />Hạn: {q.deadline}</div>
                        </div>
                        <St s={st} />
                      </div>
                    );
                  })}
                </div>
              )}
            </Sn>
          </div>
        </div>
      </div>
    );
  }

  window.StuDashboard = StuDashboard;
})();
