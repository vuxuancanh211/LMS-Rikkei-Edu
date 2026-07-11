// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Học viên · Bài tập & Quiz
   ============================================================ */
(function () {
  const { useState, useEffect, useCallback } = React;
  const Ic = window.Icon;
  const api = window.httpClient;
  const D = window.DATA;
  const { Status: St, Search: Se, Tabs: Tb, Select: Sl, Section: Sn, Modal: Md, ModalHead: MH, StatCard: SC } = window;

  const { getStudentCourseProgress, getMyAttempts } = window.__quizService;

  const QUIZ_STATUS_CHIP = { DRAFT: 'neutral', PUBLISHED: 'success', ARCHIVED: 'muted' };
  const QUIZ_TYPE_LABEL  = { STATIC: 'Cố định', SHUFFLED_POOL: 'Xáo câu', RANDOM_DRAW: 'Ngẫu nhiên' };

  /* ─── Tab: Trắc nghiệm (API thực) ──────────────────────── */
  function QuizTab({ nav, courses }) {
    const [courseId, setCourseId] = useState(courses[0]?.id || null);
    const [quizList, setQuizList] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [q, setQ] = useState('');
    const [historyQuiz, setHistoryQuiz] = useState(null); // { quizId, quizTitle } — quiz đang xem lịch sử làm bài

    // Courses tải bất đồng bộ ở component cha (StuTasks) — chọn khóa đầu tiên
    // ngay khi danh sách thực về, vì lúc mount courses vẫn còn rỗng.
    useEffect(() => {
      if (!courseId && courses.length > 0) setCourseId(courses[0].id);
    }, [courses, courseId]);

    const fetchProgress = useCallback(async () => {
      if (!courseId) return;
      setLoading(true);
      setError('');
      try {
        const data = await getStudentCourseProgress(courseId);
        setQuizList(data);
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
            onChange={v => { setCourseId(v); setQuizList([]); }}
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

  /* ─── Tab: Bài tập tự luận (mock) ──────────────────────── */
  function AssignTab() {
    const [q, setQ] = useState('');
    const [open, setOpen] = useState(null);

    let list = D?.assignments?.filter(a => a.type === 'assignment') || [];
    if (q) list = list.filter(a =>
      a.title.toLowerCase().includes(q.toLowerCase()) ||
      a.course.toLowerCase().includes(q.toLowerCase())
    );
    const pg = window.usePaged(list, 10);

    return (
      <div>
        <div className="toolbar" style={{ marginBottom: 0 }}>
          <div className="grow" />
          <Se placeholder="Tìm bài tập..." value={q} onChange={setQ} style={{ width: 240, flex: 'none' }} />
        </div>
        <Sn pad={false} style={{ marginTop: 16 }}>
          <div style={{ overflowX: 'auto' }}>
            <table className="tbl">
              <thead>
                <tr><th>Tên bài</th><th>Khóa học</th><th>Hạn nộp</th><th>Trạng thái</th><th>Điểm</th><th></th></tr>
              </thead>
              <tbody>
                {pg.slice.map(a => (
                  <tr key={a.id}>
                    <td>
                      <div className="row gap-10">
                        <div className="stat-ic" style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--surface-3)', color: 'var(--text-2)' }}>
                          <Ic n="file" size={17} />
                        </div>
                        <div style={{ fontWeight: 600, maxWidth: 240 }} className="truncate">{a.title}</div>
                      </div>
                    </td>
                    <td className="muted truncate" style={{ maxWidth: 160 }}>{a.course}</td>
                    <td className="muted">{a.deadline}</td>
                    <td><St s={a.status} /></td>
                    <td style={{ fontWeight: 700 }}>{a.score || '—'}</td>
                    <td>
                      {(a.status === 'pending' || a.status === 'late')
                        ? <button className="btn btn-primary btn-sm" onClick={() => setOpen(a)}>Nộp bài</button>
                        : <button className="btn btn-ghost btn-sm" onClick={() => setOpen(a)}>Xem</button>}
                    </td>
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

  /* ─── Main StuTasks ─────────────────────────────────────── */
  function StuTasks({ nav }) {
    const [tab, setTab] = useState('quiz');
    const [courses, setCourses] = useState([]);

    // Danh sách khóa học thực đã đăng ký — dùng chung cho bộ chọn khóa của tab Trắc nghiệm
    // và số liệu thống kê (trước đây lấy nhầm từ window.DATA — dữ liệu demo tĩnh).
    useEffect(() => {
      api.get('/student/courses')
        .then(r => setCourses(r.data || []))
        .catch(() => setCourses([]));
    }, []);

    // Stats (bài tập tự luận vẫn là mock — chưa có API thật)
    const allAssign = D?.assignments || [];
    const pendingCount = allAssign.filter(a => a.status === 'pending' || a.status === 'late').length;
    const gradedCount  = allAssign.filter(a => a.status === 'graded').length;

    return (
      <div className="page fade-in">
        <div className="page-head">
          <h1 className="t-h1">Bài tập & Bài kiểm tra</h1>
          <p>Theo dõi deadline, trạng thái nộp bài và làm bài trắc nghiệm trực tuyến.</p>
        </div>

        {/* Stats row */}
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <SC icon="clipboard" iconBg="#eaf1ff" iconColor="#2563eb" value={String(courses.length)} label="Khóa đang học" />
          <SC icon="warn" iconBg="#fef5e6" iconColor="#d97706" value={String(pendingCount)} label="Bài tập chờ nộp" />
          <SC icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={String(gradedCount)} label="Đã được chấm" />
          <SC icon="target" iconBg="#f3edff" iconColor="#7c3aed" value="—" label="Điểm TB quiz" />
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

        {tab === 'quiz'   && <QuizTab nav={nav} courses={courses} />}
        {tab === 'assign' && <AssignTab />}
      </div>
    );
  }

  /* ─── Assignment Modal (mock) ───────────────────────────── */
  function AssignmentModal({ a, onClose }) {
    const [file, setFile] = useState(null);
    if (!a) return null;
    const submitted = a.status === 'submitted' || a.status === 'graded';
    const graded    = a.status === 'graded';
    return (
      <Md open={!!a} onClose={onClose} max={600}>
        <MH title={a.title} sub={a.course} icon="file" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div className="row gap-8 wrap">
            <span className="chip chip-neutral">Tự luận</span>
            <St s={a.status} />
          </div>
          <div className="grid grid-2" style={{ gap: 10 }}>
            {[
              { l: 'Hạn nộp', v: a.deadline, ic: 'calendar' },
              { l: 'Thang điểm', v: '10 điểm', ic: 'target' },
            ].map((m, i) => (
              <div key={i} className="row gap-10" style={{ padding: 11, background: 'var(--surface-2)', borderRadius: 11 }}>
                <div className="stat-ic" style={{ width: 34, height: 34, borderRadius: 10, background: '#fff', color: 'var(--accent)', flex: 'none' }}>
                  <Ic n={m.ic} size={16} />
                </div>
                <div><div className="t-xs muted">{m.l}</div><div style={{ fontWeight: 700, fontSize: 13.5 }}>{m.v}</div></div>
              </div>
            ))}
          </div>
          <div>
            <div className="t-label" style={{ marginBottom: 6 }}>Yêu cầu bài tập</div>
            <p className="muted t-sm" style={{ margin: 0, lineHeight: 1.6 }}>
              Hoàn thành các yêu cầu trong đề bài đính kèm. Nộp mã nguồn dưới dạng file .zip kèm hướng dẫn chạy.
            </p>
          </div>
          {graded && (
            <div style={{ background: 'var(--chip-success-bg)', borderRadius: 12, padding: 14 }}>
              <div className="between">
                <span className="t-label" style={{ color: 'var(--chip-success-fg)', margin: 0 }}>Kết quả</span>
                <span style={{ fontWeight: 800, fontSize: 22, color: 'var(--success)' }}>{a.score}</span>
              </div>
            </div>
          )}
          {!submitted && (
            <div>
              <div className="t-label" style={{ marginBottom: 8 }}>Nộp bài làm</div>
              <label htmlFor="subfile" style={{ display: 'block', border: '2px dashed var(--border-strong)', borderRadius: 12, padding: 22, textAlign: 'center', cursor: 'pointer', background: file ? 'var(--accent-soft)' : 'var(--surface-2)' }}>
                <Ic n={file ? 'check_circle' : 'upload'} size={26} style={{ marginBottom: 8, color: file ? 'var(--accent)' : 'var(--text-3)' }} />
                <div style={{ fontWeight: 600, fontSize: 14 }}>{file ? file : 'Kéo thả hoặc bấm để chọn file'}</div>
                <div className="t-xs muted" style={{ marginTop: 4 }}>ZIP, PDF, DOCX · tối đa 50MB</div>
                <input id="subfile" type="file" style={{ display: 'none' }} onChange={e => setFile(e.target.files[0]?.name || null)} />
              </label>
            </div>
          )}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
          {submitted
            ? <button className="btn btn-ghost" disabled>Đã nộp bài</button>
            : <button className="btn btn-success" onClick={onClose}><Ic n="upload" size={16} />Nộp bài</button>}
        </div>
      </Md>
    );
  }

  window.StuTasks = StuTasks;
})();
