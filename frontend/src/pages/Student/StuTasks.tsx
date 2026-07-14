// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Học viên · Bài tập & Quiz
   ============================================================ */
(function () {
  const { useState, useEffect, useCallback } = React;
  const Ic = window.Icon;
  const api = window.httpClient;
  const { Status: St, Search: Se, Tabs: Tb, Select: Sl, Section: Sn, Modal: Md, ModalHead: MH, StatCard: SC } = window;

  const { getStudentCourseProgress, getMyAttempts } = window.__quizService;

  const QUIZ_STATUS_CHIP = { DRAFT: 'neutral', PUBLISHED: 'success', ARCHIVED: 'muted' };
  const QUIZ_TYPE_LABEL  = { STATIC: 'Cố định', SHUFFLED_POOL: 'Xáo câu', RANDOM_DRAW: 'Ngẫu nhiên' };

  /* ─── Tab: Trắc nghiệm (API thực) ──────────────────────── */
  function QuizTab({ nav, courses, initialCourseId, onQuizStatsUpdate }) {
    const [courseId, setCourseId] = useState(initialCourseId || courses[0]?.id || null);
    const [quizList, setQuizList] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [q, setQ] = useState('');
    const [historyQuiz, setHistoryQuiz] = useState(null); // { quizId, quizTitle } — quiz đang xem lịch sử làm bài

    // Courses tải bất đồng bộ ở component cha (StuTasks) — chọn khóa đầu tiên ngay khi danh sách
    // thực về, vì lúc mount courses vẫn còn rỗng. Ưu tiên courseId trên URL (initialCourseId) nếu
    // nó thuộc danh sách khóa đã đăng ký — để F5 lại trang vẫn giữ đúng khóa học đang xem thay vì
    // luôn nhảy về khóa đầu tiên.
    useEffect(() => {
      if (courseId || courses.length === 0) return;
      const fromUrl = initialCourseId && courses.some(c => c.id === initialCourseId);
      setCourseId(fromUrl ? initialCourseId : courses[0].id);
    }, [courses, courseId, initialCourseId]);

    // Đồng bộ courseId đang chọn lên URL (?courseId=...) — để F5 hoặc chia sẻ link vẫn mở đúng
    // khóa học đang xem, thay vì luôn quay về khóa đầu tiên như trước đây.
    const selectCourse = v => {
      setCourseId(v);
      setQuizList([]);
      nav('tasks', { courseId: v });
    };

    const fetchProgress = useCallback(async () => {
      if (!courseId) return;
      setLoading(true);
      setError('');
      try {
        const data = await getStudentCourseProgress(courseId);
        setQuizList(data);
        const done = data.filter(x => x.attemptsUsed > 0).length;
        const passed = data.filter(x => x.passed).length;
        const scores = data.map(x => x.bestScorePercentage).filter(v => v != null);
        const avg = scores.length > 0 ? (scores.reduce((a, b) => a + b, 0) / scores.length) : null;
        onQuizStatsUpdate({ done, passed, avg: avg != null ? avg.toFixed(1) + '%' : '—' });
      } catch (err) {
        setError(err?.response?.data?.message || 'Không thể tải danh sách quiz.');
      } finally {
        setLoading(false);
      }
    }, [courseId]);

    useEffect(() => { fetchProgress(); }, [fetchProgress]);

    const filtered = quizList.filter(x =>
      !q || x.quizTitle.toLowerCase().includes(q.toLowerCase())
    );

    return (
      <div>
        {/* Course + search bar */}
        <div className="toolbar" style={{ marginBottom: 0 }}>
          <Sl
            value={courseId || ''}
            onChange={selectCourse}
            options={courses.map(c => ({ v: c.id, label: c.title }))}
            style={{ width: 260, flex: 'none' }}
          />
          <div className="grow" />
          <Se placeholder="Tìm quiz..." value={q} onChange={setQ} style={{ width: 220, flex: 'none' }} />
        </div>

        {loading ? (
          <Sn pad={false} style={{ marginTop: 16 }}>
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>
              Đang tải...
            </div>
          </Sn>
        ) : error ? (
          <Sn pad={false} style={{ marginTop: 16 }}>
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--error)' }}>
              {error}
              <br />
              <button className="btn btn-ghost btn-sm" style={{ marginTop: 10 }} onClick={fetchProgress}>Thử lại</button>
            </div>
          </Sn>
        ) : filtered.length === 0 ? (
          <Sn pad={false} style={{ marginTop: 16 }}>
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>
              <Ic n="clipboard" size={34} style={{ marginBottom: 10, opacity: 0.3 }} />
              <p>{quizList.length === 0 ? 'Chưa có quiz nào trong khóa học này.' : 'Không tìm thấy quiz.'}</p>
            </div>
          </Sn>
        ) : (
          <div className="grid grid-cards" style={{ marginTop: 16 }}>
            {filtered.map(quiz => {
              const locked = quiz.quizStatus !== 'PUBLISHED';
              const notStarted = quiz.attemptsUsed === 0;
              const scorePct = quiz.bestScorePercentage != null ? Number(quiz.bestScorePercentage) : null;
              const barColor = quiz.passed ? 'var(--success)' : 'var(--error)';

              return (
                <div key={quiz.quizId} className="card card-pad quiz-card">
                  <div className="row gap-10" style={{ alignItems: 'flex-start' }}>
                    <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 12, background: 'var(--accent-soft)', color: 'var(--accent)', flex: 'none' }}>
                      <Ic n="clipboard" size={20} />
                    </div>
                    <div className="grow" style={{ minWidth: 0 }}>
                      <div className="truncate" style={{ fontWeight: 700, fontSize: 15 }} title={quiz.quizTitle}>
                        {quiz.quizTitle}
                      </div>
                      <div className="row gap-6 wrap" style={{ marginTop: 6 }}>
                        <span className="chip chip-neutral" style={{ fontSize: 11 }}>
                          {QUIZ_TYPE_LABEL[quiz.quizType] || quiz.quizType}
                        </span>
                        {locked ? (
                          <span className="chip chip-neutral" style={{ fontSize: 11 }}>Chưa mở</span>
                        ) : notStarted ? (
                          <span className="chip chip-neutral" style={{ fontSize: 11 }}>Chưa làm</span>
                        ) : quiz.passed ? (
                          <span className="chip chip-success" style={{ fontSize: 11 }}><Ic n="check" size={11} />Đạt</span>
                        ) : (
                          <span className="chip chip-error" style={{ fontSize: 11 }}><Ic n="x" size={11} />Chưa đạt</span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div style={{ margin: '18px 0' }}>
                    <div className="between" style={{ marginBottom: 8 }}>
                      <span className="t-xs muted">
                        Đã làm {quiz.attemptsUsed} / {quiz.maxAttempts != null ? quiz.maxAttempts : '∞'} lần
                      </span>
                      {scorePct != null && (
                        <span style={{ fontWeight: 800, fontSize: 19, color: barColor }}>
                          {scorePct.toFixed(1)}%
                        </span>
                      )}
                    </div>
                    <div className={`bar ${quiz.passed ? 'is-done' : ''}`}>
                      <span style={{ width: `${scorePct != null ? Math.min(100, scorePct) : 0}%`, background: scorePct != null ? barColor : undefined }} />
                    </div>
                  </div>

                  <div className="row gap-8" style={{ justifyContent: 'space-between' }}>
                    {quiz.attemptsUsed > 0 ? (
                      <button
                        className="btn btn-ghost btn-sm"
                        title="Xem lịch sử làm bài"
                        onClick={() => setHistoryQuiz({ quizId: quiz.quizId, quizTitle: quiz.quizTitle })}
                      >
                        <Ic n="clock" size={14} />Lịch sử
                      </button>
                    ) : <span />}

                    {locked ? (
                      <span className="chip chip-neutral" style={{ fontSize: 11 }}>Chưa mở</span>
                    ) : quiz.canRetry ? (
                      <button
                        className="btn btn-primary btn-sm"
                        onClick={() => nav('quiz', { courseId, quizId: quiz.quizId, from: 'tasks' })}
                      >
                        <Ic n="play" size={14} />
                        {notStarted ? 'Làm bài' : 'Làm lại'}
                      </button>
                    ) : (
                      <button className="btn btn-ghost btn-sm" disabled>
                        {quiz.maxAttempts != null && quiz.attemptsUsed >= quiz.maxAttempts
                          ? 'Hết lượt'
                          : 'Chờ cooldown'}
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        <AttemptHistoryModal
          quiz={historyQuiz}
          courseId={courseId}
          onClose={() => setHistoryQuiz(null)}
          onViewResult={attemptId => nav('result', { attemptId, courseId, quizId: historyQuiz.quizId })}
        />
      </div>
    );
  }

  /* ─── Modal: Lịch sử làm bài của 1 quiz ─────────────────── */
  function AttemptHistoryModal({ quiz, courseId, onClose, onViewResult }) {
    const [attempts, setAttempts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
      if (!quiz) return;
      setLoading(true);
      setError('');
      getMyAttempts(courseId, quiz.quizId)
        .then(data => setAttempts([...data].sort((a, b) => b.attemptNumber - a.attemptNumber)))
        .catch(err => setError(err?.response?.data?.message || 'Không thể tải lịch sử làm bài.'))
        .finally(() => setLoading(false));
    }, [quiz, courseId]);

    if (!quiz) return null;

    function fmtTime(secs) {
      if (!secs && secs !== 0) return '—';
      const m = Math.floor(secs / 60), s = secs % 60;
      return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    }
    function fmtDate(iso) {
      if (!iso) return '—';
      return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    }

    return (
      <Md open={!!quiz} onClose={onClose} max={720}>
        <MH title="Lịch sử làm bài" sub={quiz.quizTitle} icon="clock" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body">
          {loading ? (
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>Đang tải...</div>
          ) : error ? (
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--error)' }}>{error}</div>
          ) : attempts.length === 0 ? (
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>Chưa có lần làm bài nào.</div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Lần</th>
                    <th>Ngày nộp</th>
                    <th>Điểm</th>
                    <th>Đúng/Sai/Bỏ qua</th>
                    <th>Thời gian</th>
                    <th>Kết quả</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {attempts.map(a => (
                    <tr key={a.attemptId}>
                      <td style={{ fontWeight: 700 }}>#{a.attemptNumber}</td>
                      <td className="muted">{fmtDate(a.submittedAt)}</td>
                      <td style={{ fontWeight: 700 }}>
                        {a.scorePercentage != null
                          ? <span style={{ color: a.isPassed ? 'var(--success)' : 'var(--error)' }}>
                              {Number(a.scorePercentage).toFixed(1)}%
                            </span>
                          : <span className="muted">—</span>}
                      </td>
                      <td className="muted t-sm">
                        {a.correctCount}/{a.incorrectCount}/{a.unansweredCount}
                      </td>
                      <td className="muted">{fmtTime(a.timeSpentSeconds)}</td>
                      <td>
                        <div className="row gap-6 wrap">
                          {a.status === 'IN_PROGRESS' ? (
                            <span className="chip chip-neutral" style={{ fontSize: 11 }}>Đang làm</span>
                          ) : a.isPassed ? (
                            <span className="chip chip-success" style={{ fontSize: 11 }}><Ic n="check" size={11} />Đạt</span>
                          ) : (
                            <span className="chip chip-error" style={{ fontSize: 11 }}><Ic n="x" size={11} />Chưa đạt</span>
                          )}
                          {a.autoSubmitted && <span className="chip chip-warning" style={{ fontSize: 10 }}>Tự động nộp</span>}
                          {a.violationCount > 0 && <span className="chip chip-error" style={{ fontSize: 10 }}>{a.violationCount} vi phạm</span>}
                        </div>
                      </td>
                      <td>
                        {a.status !== 'IN_PROGRESS' && (
                          <button className="btn btn-ghost btn-sm" onClick={() => onViewResult(a.attemptId)}>
                            Xem chi tiết
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
        </div>
      </Md>
    );
  }

  /* ─── Tab: Bài tập tự luận (API thực) ──────────────────── */
  function AssignTab({ nav, courses, initialCourseId, onStatsUpdate }) {
    const [courseId, setCourseId] = useState(initialCourseId || courses[0]?.id || null);
    const [assignments, setAssignments] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [q, setQ] = useState('');
    const [open, setOpen] = useState(null);

    useEffect(() => {
      if (courseId || courses.length === 0) return;
      const fromUrl = initialCourseId && courses.some(c => c.id === initialCourseId);
      setCourseId(fromUrl ? initialCourseId : courses[0].id);
    }, [courses, courseId, initialCourseId]);

    const selectCourse = v => {
      setCourseId(v);
      setAssignments([]);
      nav('tasks', { courseId: v });
    };

    const fetchAssignments = useCallback(async () => {
      if (!courseId) return;
      setLoading(true);
      setError('');
      try {
        const res = await api.get(`/student/courses/${courseId}/assignments`);
        const list = res.data || [];
        setAssignments(list);
        onStatsUpdate({
          pending: list.filter(a => !a.submissionStatus).length,
          graded: list.filter(a => a.submissionStatus === 'GRADED').length,
          late: list.filter(a => a.submissionStatus === 'LATE').length,
        });
      } catch (err) {
        setError(err?.response?.data?.message || 'Không thể tải danh sách bài tập.');
      } finally {
        setLoading(false);
      }
    }, [courseId]);

    useEffect(() => { fetchAssignments(); }, [fetchAssignments]);

    const filtered = assignments.filter(a =>
      !q || a.title.toLowerCase().includes(q.toLowerCase())
    );
    const pg = window.usePaged(filtered, 10);

    return (
      <div>
        <div className="toolbar" style={{ marginBottom: 0 }}>
          <Sl
            value={courseId || ''}
            onChange={selectCourse}
            options={courses.map(c => ({ v: c.id, label: c.title }))}
            style={{ width: 260, flex: 'none' }}
          />
          <div className="grow" />
          <Se placeholder="Tìm bài tập..." value={q} onChange={setQ} style={{ width: 220, flex: 'none' }} />
        </div>

        {loading ? (
          <Sn pad={false} style={{ marginTop: 16 }}>
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>Đang tải...</div>
          </Sn>
        ) : error ? (
          <Sn pad={false} style={{ marginTop: 16 }}>
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--error)' }}>
              {error}
              <br />
              <button className="btn btn-ghost btn-sm" style={{ marginTop: 10 }} onClick={fetchAssignments}>Thử lại</button>
            </div>
          </Sn>
        ) : filtered.length === 0 ? (
          <Sn pad={false} style={{ marginTop: 16 }}>
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>
              <Ic n="clipboard" size={34} style={{ marginBottom: 10, opacity: 0.3 }} />
              <p>{assignments.length === 0 ? 'Chưa có bài tập nào trong khóa học này.' : 'Không tìm thấy bài tập.'}</p>
            </div>
          </Sn>
        ) : (
          <Sn pad={false} style={{ marginTop: 16 }}>
            <div style={{ overflowX: 'auto' }}>
              <table className="tbl">
                <thead>
                  <tr><th>Tên bài</th><th>Hạn nộp</th><th>Trạng thái</th><th>Điểm</th><th></th></tr>
                </thead>
                <tbody>
                  {pg.slice.map(a => {
                    const isLate = a.submissionStatus === 'LATE';
                    const isGraded = a.submissionStatus === 'GRADED';
                    const isSubmitted = a.submissionStatus === 'SUBMITTED' || isLate;
                    return (
                      <tr key={a.id}>
                        <td>
                          <div className="row gap-10">
                            <div className="stat-ic" style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--surface-3)', color: 'var(--text-2)' }}>
                              <Ic n="file" size={17} />
                            </div>
                            <div style={{ fontWeight: 600, maxWidth: 240 }} className="truncate">{a.title}</div>
                          </div>
                        </td>
                        <td className="muted" style={{ fontSize: 12.5 }}>
                          {a.deadline ? new Date(a.deadline).toLocaleDateString('vi-VN', {
                            day: '2-digit', month: '2-digit', year: 'numeric',
                            hour: '2-digit', minute: '2-digit',
                          }) : '—'}
                        </td>
                        <td>
                          <div className="row gap-6 wrap">
                            {!a.submissionStatus ? (
                              <span className="chip chip-neutral" style={{ fontSize: 11 }}>Chưa nộp</span>
                            ) : isGraded ? (
                              <span className="chip chip-success" style={{ fontSize: 11 }}>Đã chấm</span>
                            ) : isLate ? (
                              <>
                                <span className="chip chip-neutral" style={{ fontSize: 11, background: '#eaf1ff', color: '#2563eb' }}>Đã nộp</span>
                                <span className="chip chip-error" style={{ fontSize: 11 }}>Nộp trễ</span>
                              </>
                            ) : (
                              <span className="chip chip-neutral" style={{ fontSize: 11, background: '#eaf1ff', color: '#2563eb' }}>Đã nộp</span>
                            )}
                          </div>
                        </td>
                        <td style={{ fontWeight: 700 }}>{a.score != null ? a.score : '—'}</td>
                        <td>
                          {!a.submissionStatus ? (
                            <button className="btn btn-primary btn-sm" onClick={() => setOpen(a)}>Nộp bài</button>
                          ) : (
                            <button className="btn btn-ghost btn-sm" onClick={() => setOpen(a)}>Xem</button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </Sn>
        )}
        <window.PageBar pg={pg} unit="bài" />
        <AssignmentModal a={open} courseId={courseId} onClose={() => { setOpen(null); fetchAssignments(); }} />
      </div>
    );
  }

  /* ─── Main StuTasks ─────────────────────────────────────── */
  function StuTasks({ nav, courseId }) {
    const [tab, setTab] = useState('quiz');
    const [courses, setCourses] = useState([]);
    const [assignStats, setAssignStats] = useState({ pending: 0, graded: 0, late: 0 });
    const [quizStats, setQuizStats] = useState({ done: 0, passed: 0, avg: '—' });

    useEffect(() => {
      api.get('/student/courses')
        .then(r => setCourses(r.data || []))
        .catch(() => setCourses([]));
    }, []);

    return (
      <div className="page fade-in">
        <div className="page-head">
          <h1 className="t-h1">Bài tập & Bài kiểm tra</h1>
          <p>Theo dõi deadline, trạng thái nộp bài và làm bài trắc nghiệm trực tuyến.</p>
        </div>

        {/* Stats row — động theo tab */}
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <SC icon="clipboard" iconBg="#eaf1ff" iconColor="#2563eb" value={String(courses.length)} label="Khóa đang học" />
          {tab === 'assign' ? (
            <>
              <SC icon="warn" iconBg="#fef5e6" iconColor="#d97706" value={String(assignStats.pending)} label="Chưa nộp" />
              <SC icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={String(assignStats.graded)} label="Đã chấm" />
              <SC icon="alert_circle" iconBg="#fdecec" iconColor="#dc2626" value={String(assignStats.late)} label="Nộp trễ" />
            </>
          ) : (
            <>
              <SC icon="layers" iconBg="#fef5e6" iconColor="#d97706" value={String(quizStats.done)} label="Quiz đã làm" />
              <SC icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={String(quizStats.passed)} label="Đã đạt" />
              <SC icon="target" iconBg="#f3edff" iconColor="#7c3aed" value={quizStats.avg} label="Điểm TB" />
            </>
          )}
        </div>

        <div className="toolbar">
          <Tb
            items={[
              { v: 'quiz', label: 'Trắc nghiệm' },
              { v: 'assign', label: 'Bài tập tự luận' },
            ]}
            value={tab}
            onChange={setTab}
          />
        </div>

        {tab === 'quiz'   && <QuizTab nav={nav} courses={courses} initialCourseId={courseId} onQuizStatsUpdate={setQuizStats} />}
        {tab === 'assign' && <AssignTab nav={nav} courses={courses} initialCourseId={courseId} onStatsUpdate={setAssignStats} />}
      </div>
    );
  }

  /* ─── Assignment Modal (API thực) ────────────────────────── */
  function AssignmentModal({ a, courseId, onClose }) {
    const [detail, setDetail] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [files, setFiles] = useState([]);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
      if (!a) return;
      setLoading(true);
      setError('');
      setFiles([]);
      api.get(`/student/courses/${courseId}/assignments/${a.id}`)
        .then(r => setDetail(r.data))
        .catch(err => setError(err?.response?.data?.message || 'Không thể tải chi tiết bài tập.'))
        .finally(() => setLoading(false));
    }, [a?.id]);

    function fmtDate(iso) {
      if (!iso) return '—';
      return new Date(iso).toLocaleDateString('vi-VN', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
      });
    }

    if (!a) return null;

    const sub = detail?.studentSubmission;
    const submitted = !!sub;
    const graded = sub?.status === 'GRADED';
    const isPublished = detail?.status === 'PUBLISHED';
    const isClosed = detail?.status === 'CLOSED';

    async function handleSubmit() {
      if (files.length === 0) { setError('Vui lòng chọn file để nộp'); return; }
      setSubmitting(true);
      setError('');
      try {
        const formData = new FormData();
        files.forEach(f => formData.append('files', f));
        await api.post(`/student/courses/${courseId}/assignments/${a.id}/submit`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        const res = await api.get(`/student/courses/${courseId}/assignments/${a.id}`);
        setDetail(res.data);
        setFiles([]);
      } catch (err) {
        setError(err?.response?.data?.message || 'Nộp bài thất bại');
      } finally {
        setSubmitting(false);
      }
    }

    return (
      <Md open={!!a} onClose={onClose} max={600}>
        <MH title={detail?.title || a.title} sub={detail?.courseTitle || 'Đang tải...'} icon="file" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        {loading ? (
          <div className="modal-body" style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>Đang tải...</div>
        ) : error && !detail ? (
          <div className="modal-body" style={{ padding: 40, textAlign: 'center', color: 'var(--error)' }}>{error}</div>
        ) : detail ? (
          <>
            <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {/* Submission status badges — chỉ hiện khi đã nộp */}
              {submitted && (
                <div className="row gap-8 wrap">
                  {graded ? (
                    <span className="chip chip-success" style={{ fontSize: 11 }}>Đã chấm</span>
                  ) : (
                    <span className="chip chip-neutral" style={{ fontSize: 11, background: '#eaf1ff', color: '#2563eb' }}>Đã nộp</span>
                  )}
                  {sub?.isLate && (
                    <span className="chip chip-error" style={{ fontSize: 11 }}>Nộp trễ</span>
                  )}
                </div>
              )}

              {/* Info grid — giống AssignmentDetail */}
              <div style={{
                display: 'grid', gridTemplateColumns: '1fr 1fr',
                gap: '8px 16px', padding: 12, background: '#fff', borderRadius: 10,
                border: '1px solid #e2e8f0',
              }}>
                {[
                  { label: 'Điểm tối đa', value: detail.maxScore != null ? detail.maxScore + ' điểm' : '—' },
                  { label: 'Ngày bắt đầu', value: fmtDate(detail.startDate) },
                  { label: 'Hạn nộp', value: fmtDate(detail.deadline) },
                  {
                    label: 'Nộp muộn',
                    value: detail.allowLateSubmission
                      ? `Cho phép (trừ ${detail.latePenaltyPercent ?? 0}%/ngày)`
                      : 'Không cho phép'
                  },
                  {
                    label: 'Loại file',
                    value: detail.allowedFileTypes?.length > 0
                      ? detail.allowedFileTypes.map(t => {
                          const map = {
                            'image/jpeg': 'JPEG', 'image/png': 'PNG',
                            'application/pdf': 'PDF', 'application/zip': 'ZIP',
                            'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'DOCX',
                            'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'PPTX',
                          };
                          return map[t] || t.split('/').pop()?.toUpperCase() || t;
                        }).join(', ')
                      : 'Tất cả'
                  },
                  {
                    label: 'Số lần nộp',
                    value: sub
                      ? `Đã nộp: ${sub.submissionNumber} / ${detail.maxSubmissions ?? '?'} lần`
                      : `0 / ${detail.maxSubmissions ?? '?'} lần`
                  },
                  { label: 'Kích thước file', value: detail.maxFileSizeMb != null ? detail.maxFileSizeMb + ' MB' : '—' },
                  { label: 'Ngày tạo', value: fmtDate(detail.createdAt) },
                  ...(sub?.score != null
                    ? [{ label: 'Điểm của bạn', value: `${sub.score} / ${detail.maxScore ?? '?'}` }]
                    : []),
                ].map(item => (
                  <div key={item.label}>
                    <div style={{ fontSize: 11, fontWeight: 500, color: '#94a3b8', marginBottom: 2 }}>
                      {item.label}
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>{item.value}</div>
                  </div>
                ))}
              </div>

              {/* Description */}
              {detail.description && (
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>Yêu cầu bài tập</div>
                  <p className="muted t-sm" style={{ margin: 0, lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>{detail.description}</p>
                </div>
              )}

              {/* File đính kèm đề bài */}
              {detail.attachments?.length > 0 && (
                <div>
                  <div className="t-label" style={{ marginBottom: 6, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Ic n="paperclip" size={12} />File đính kèm ({detail.attachments.length})
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                    {detail.attachments.map(att => (
                      <div key={att.id} className="row gap-8" style={{ padding: '8px 10px', background: 'var(--surface-2)', borderRadius: 8 }}>
                        <div style={{ width: 28, height: 28, borderRadius: 6, background: '#f1f5f9', color: '#64748b', display: 'grid', placeItems: 'center', fontSize: 9, fontWeight: 800, flexShrink: 0 }}>
                          {(() => {
                            const m = (att.mimeType || '').toLowerCase();
                            if (m.includes('pdf')) return 'PDF';
                            if (m.includes('zip') || m.includes('rar')) return 'ZIP';
                            if (m.includes('word') || m.includes('doc')) return 'DOC';
                            if (m.includes('presentation') || m.includes('ppt')) return 'PPT';
                            if (m.includes('image')) return 'IMG';
                            return 'FILE';
                          })()}
                        </div>
                        <span className="t-sm" style={{ flex: 1 }}>{att.displayName || att.originalFilename}</span>
                        <span className="t-xs muted">{att.fileSizeBytes ? (att.fileSizeBytes >= 1048576 ? (att.fileSizeBytes / 1048576).toFixed(1) + ' MB' : (att.fileSizeBytes / 1024).toFixed(0) + ' KB') : ''}</span>
                        {att.url && <a href={att.url} target="_blank" rel="noopener noreferrer" className="btn btn-ghost btn-xs"><Ic n="download" size={12} /></a>}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Error in modal */}
              {error && (
                <div style={{ padding: '8px 12px', borderRadius: 8, background: '#fef2f2', color: '#dc2626', fontSize: 12.5 }}>
                  <Ic n="alert_circle" size={14} style={{ marginRight: 4 }} />{error}
                </div>
              )}

              {/* Grade result */}
              {graded && sub.score != null && (
                <div style={{ background: 'var(--chip-success-bg)', borderRadius: 12, padding: 14 }}>
                  <div className="between">
                    <span className="t-label" style={{ color: 'var(--chip-success-fg)', margin: 0 }}>Kết quả</span>
                    <span style={{ fontWeight: 800, fontSize: 22, color: 'var(--success)' }}>{sub.score}</span>
                  </div>
                  {sub.feedback && (
                    <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid rgba(0,0,0,.06)' }}>
                      <div className="t-xs muted" style={{ marginBottom: 4 }}>Nhận xét</div>
                      <div className="t-sm" style={{ whiteSpace: 'pre-wrap' }}>{sub.feedback}</div>
                    </div>
                  )}
                </div>
              )}

              {/* Submitted files */}
              {sub?.files?.length > 0 && (
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>Bài đã nộp</div>
                  {sub.files.map(f => (
                    <div key={f.id} className="row gap-8" style={{ padding: '8px 10px', background: 'var(--surface-2)', borderRadius: 8, marginBottom: 6 }}>
                      <Ic n="file" size={15} style={{ color: 'var(--text-2)' }} />
                      <span className="t-sm" style={{ flex: 1 }}>{f.displayName || f.originalFilename}</span>
                      {f.url && <a href={f.url} target="_blank" rel="noopener noreferrer" className="btn btn-ghost btn-xs">Xem</a>}
                    </div>
                  ))}
                </div>
              )}

              {/* Upload area — chỉ khi PUBLISHED và chưa nộp */}
              {isPublished && !sub && (
                <div>
                  <div className="t-label" style={{ marginBottom: 8 }}>Nộp bài làm</div>
                  <label htmlFor="subfile" style={{ display: 'block', border: '2px dashed var(--border-strong)', borderRadius: 12, padding: 22, textAlign: 'center', cursor: 'pointer', background: files.length > 0 ? 'var(--accent-soft)' : 'var(--surface-2)' }}>
                    <Ic n={files.length > 0 ? 'check_circle' : 'upload'} size={26} style={{ marginBottom: 8, color: files.length > 0 ? 'var(--accent)' : 'var(--text-3)' }} />
                    <div style={{ fontWeight: 600, fontSize: 14 }}>{files.length > 0 ? `${files.length} file đã chọn` : 'Kéo thả hoặc bấm để chọn file'}</div>
                    <div className="t-xs muted" style={{ marginTop: 4 }}>{detail.maxFileSizeMb ? `Tối đa ${detail.maxFileSizeMb}MB` : 'ZIP, PDF, DOCX'}</div>
                    <input id="subfile" type="file" multiple style={{ display: 'none' }}
                      onChange={e => setFiles(Array.from(e.target.files))} />
                  </label>
                </div>
              )}

              {/* Closed message */}
              {isClosed && !sub && (
                <div style={{ textAlign: 'center', padding: 16, color: 'var(--text-2)' }}>
                  <Ic n="lock" size={20} style={{ marginBottom: 6 }} />
                  <div>Bài tập đã đóng. Không thể nộp bài.</div>
                </div>
              )}
            </div>

            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
              {isPublished && !sub && (
                <button className="btn btn-success" onClick={handleSubmit} disabled={submitting || files.length === 0}>
                  <Ic n="upload" size={16} />{submitting ? 'Đang nộp...' : 'Nộp bài'}
                </button>
              )}
              {sub && (
                <button className="btn btn-ghost" disabled>Đã nộp bài</button>
              )}
            </div>
          </>
        ) : null}
      </Md>
    );
  }

  window.StuTasks = StuTasks;
})();
