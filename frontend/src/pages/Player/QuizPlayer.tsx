// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Toàn màn hình · Làm Quiz (+ Giám sát Proctoring)
   ============================================================ */
(function () {
  const { useState, useEffect, useRef, useCallback } = React;
  const Ic = window.Icon;
  const { Avatar, Modal } = window;

  const { startAttempt, autosave, submitAttempt, reportViolation } = window.__quizService;

  /* ── Top bar ──────────────────────────────────────────────── */
  function PlayerTop({ title, onBack, authUser }) {
    return (
      <div className="topbar" style={{ paddingLeft: 20, paddingRight: 24 }}>
        <button className="icon-btn" onClick={onBack}><Ic n="arrow_left" size={20} /></button>
        <div className="row gap-10">
          <div className="sb-logo" style={{ width: 34, height: 34, borderRadius: 9 }}><Ic n="cap" size={18} /></div>
          <b style={{ fontSize: 16 }}>Rikkei Edu</b>
        </div>
        <div className="grow" />
        {title && <span style={{ fontWeight: 600, fontSize: 14, color: 'var(--text-2)' }}>{title}</span>}
        <div className="grow" />
        <Avatar name={authUser?.fullName || 'Học viên'} size={40} />
      </div>
    );
  }

  /* ── Skeleton loading ─────────────────────────────────────── */
  function LoadingSkeleton() {
    return (
      <div className="main" style={{ minHeight: '100vh', background: '#eef2f7' }}>
        <div style={{ padding: '60px 28px', maxWidth: 860, margin: '0 auto' }}>
          <div style={{ height: 28, width: 120, background: '#e2e8f0', borderRadius: 8, marginBottom: 24 }} />
          <div style={{ height: 36, width: '70%', background: '#e2e8f0', borderRadius: 8, marginBottom: 16 }} />
          {[1, 2, 3, 4].map(i => (
            <div key={i} style={{ height: 60, background: '#e2e8f0', borderRadius: 12, marginBottom: 12 }} />
          ))}
        </div>
      </div>
    );
  }

  /* ── Error screen ─────────────────────────────────────────── */
  function ErrorScreen({ message, onBack }) {
    return (
      <div className="main" style={{ minHeight: '100vh', background: '#eef2f7', display: 'grid', placeItems: 'center' }}>
        <div style={{ textAlign: 'center', padding: 40 }}>
          <div style={{ width: 72, height: 72, borderRadius: 999, background: 'var(--chip-error-bg)', display: 'grid', placeItems: 'center', margin: '0 auto 18px' }}>
            <Ic n="warn" size={36} style={{ color: 'var(--error)' }} />
          </div>
          <h2 style={{ margin: '0 0 10px', color: 'var(--error)' }}>Không thể bắt đầu bài thi</h2>
          <p style={{ color: 'var(--text-2)', marginBottom: 24 }}>{message}</p>
          <button className="btn btn-primary" onClick={onBack}>Quay lại</button>
        </div>
      </div>
    );
  }

  /* ── Main QuizPlayer ──────────────────────────────────────── */
  function QuizPlayer({ courseId, quizId, onBack, onSubmit, authUser }) {
    // ── State ──────────────────────────────────────────────────
    const [phase, setPhase] = useState('loading'); // loading | playing | submitting | error
    const [errorMsg, setErrorMsg] = useState('');
    const [attempt, setAttempt] = useState(null);   // StartAttemptResponse
    const [questions, setQuestions] = useState([]);
    const [currentIdx, setCurrentIdx] = useState(0);
    const [answers, setAnswers] = useState({});     // { questionId: [optionId, ...] }
    const [flagged, setFlagged] = useState({});     // { questionId: bool }
    const [secsLeft, setSecsLeft] = useState(0);
    const [warnViolation, setWarnViolation] = useState(null); // { order, total }
    const [lockoutModal, setLockoutModal] = useState(false);
    const [confirmSubmit, setConfirmSubmit] = useState(false);

    const autosaveTimer = useRef(null);
    const attemptRef = useRef(null);
    const answersRef = useRef({});
    const proctoringRef = useRef(false);
    const submittingRef = useRef(false);

    // Keep refs in sync
    useEffect(() => { attemptRef.current = attempt; }, [attempt]);
    useEffect(() => { answersRef.current = answers; }, [answers]);

    /* ── Start attempt on mount ─────────────────────────────── */
    useEffect(() => {
      if (!courseId || !quizId) {
        setErrorMsg('Thiếu thông tin khóa học hoặc quiz. Vui lòng thử lại từ trang bài tập.');
        setPhase('error');
        return;
      }

      let cancelled = false;
      startAttempt(courseId, quizId)
        .then(data => {
          if (cancelled) return;
          setAttempt(data);
          setQuestions(data.questions || []);
          proctoringRef.current = data.proctoringEnabled;

          // Timer: tính từ expiresAt
          const expMs = new Date(data.expiresAt).getTime();
          const nowMs = Date.now();
          setSecsLeft(Math.max(0, Math.floor((expMs - nowMs) / 1000)));

          setPhase('playing');
        })
        .catch(err => {
          if (cancelled) return;
          const msg = err?.response?.data?.message || 'Không thể bắt đầu bài thi. Vui lòng thử lại.';
          setErrorMsg(msg);
          setPhase('error');
        });

      return () => { cancelled = true; };
    }, [courseId, quizId]);

    /* ── Countdown timer ────────────────────────────────────── */
    useEffect(() => {
      if (phase !== 'playing') return;
      const t = setInterval(() => {
        setSecsLeft(s => {
          if (s <= 1) {
            clearInterval(t);
            handleAutoSubmit();
            return 0;
          }
          return s - 1;
        });
      }, 1000);
      return () => clearInterval(t);
    }, [phase]);

    /* ── Autosave every 30s ─────────────────────────────────── */
    useEffect(() => {
      if (phase !== 'playing') return;
      autosaveTimer.current = setInterval(async () => {
        const att = attemptRef.current;
        if (!att) return;
        try {
          await autosave(courseId, quizId, att.attemptId, { answers: answersRef.current });
        } catch {
          // autosave failure is silent — data is kept in Redis anyway
        }
      }, 30_000);
      return () => clearInterval(autosaveTimer.current);
    }, [phase, courseId, quizId]);

    /* ── Proctoring: tab switch & window blur ───────────────── */
    useEffect(() => {
      if (phase !== 'playing') return;

      async function handleViolation(type) {
        const att = attemptRef.current;
        if (!att || !proctoringRef.current || submittingRef.current) return;
        try {
          const res = await reportViolation(att.attemptId, {
            violationType: type,
            clientTimestamp: new Date().toISOString(),
          });
          if (res.lockedOut) {
            setLockoutModal(true);
            await doSubmit(true);
          } else {
            setWarnViolation({ order: res.violationOrder, total: res.maxViolations });
          }
        } catch {
          // network error — show warning locally anyway
          setWarnViolation({ order: 1, total: 3 });
        }
      }

      function onVisibilityChange() {
        if (document.visibilityState === 'hidden') handleViolation('TAB_SWITCH');
      }
      function onBlur() { handleViolation('WINDOW_BLUR'); }

      document.addEventListener('visibilitychange', onVisibilityChange);
      window.addEventListener('blur', onBlur);
      return () => {
        document.removeEventListener('visibilitychange', onVisibilityChange);
        window.removeEventListener('blur', onBlur);
      };
    }, [phase, courseId, quizId]);

    /* ── Submit logic ───────────────────────────────────────── */
    async function doSubmit(auto = false) {
      const att = attemptRef.current;
      if (!att || submittingRef.current) return;
      submittingRef.current = true;
      clearInterval(autosaveTimer.current);
      setPhase('submitting');
      try {
        await submitAttempt(courseId, quizId, att.attemptId, {
          answers: auto ? null : answersRef.current,
        });
        onSubmit(att.attemptId, courseId, quizId);
      } catch (err) {
        const msg = err?.response?.data?.message || 'Nộp bài thất bại. Vui lòng thử lại.';
        setErrorMsg(msg);
        setPhase('error');
        submittingRef.current = false;
      }
    }

    function handleAutoSubmit() { doSubmit(true); }
    function handleManualSubmit() { setConfirmSubmit(true); }

    /* ── Answer selection ───────────────────────────────────── */
    function toggleAnswer(questionId, optionId, isMultiple) {
      setAnswers(prev => {
        const cur = prev[questionId] || [];
        if (isMultiple) {
          const next = cur.includes(optionId)
            ? cur.filter(id => id !== optionId)
            : [...cur, optionId];
          return { ...prev, [questionId]: next };
        }
        return { ...prev, [questionId]: [optionId] };
      });
    }

    function toggleFlag(questionId) {
      setFlagged(prev => ({ ...prev, [questionId]: !prev[questionId] }));
    }

    /* ── Timer display ──────────────────────────────────────── */
    const mm = String(Math.floor(secsLeft / 60)).padStart(2, '0');
    const ss = String(secsLeft % 60).padStart(2, '0');
    const timerWarning = secsLeft > 0 && secsLeft <= 300; // đỏ khi còn <= 5 phút

    /* ── Early returns ──────────────────────────────────────── */
    if (phase === 'loading') return <LoadingSkeleton />;
    if (phase === 'error') return <ErrorScreen message={errorMsg} onBack={onBack} />;
    if (phase === 'submitting') {
      return (
        <div className="main" style={{ minHeight: '100vh', background: '#eef2f7', display: 'grid', placeItems: 'center' }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{ width: 56, height: 56, borderRadius: 999, border: '4px solid var(--accent)', borderTopColor: 'transparent', margin: '0 auto 20px', animation: 'spin 1s linear infinite' }} />
            <p style={{ fontWeight: 600, color: 'var(--text-2)' }}>Đang nộp bài...</p>
          </div>
        </div>
      );
    }

    const currentQ = questions[currentIdx];
    if (!currentQ) return <ErrorScreen message="Không có câu hỏi nào trong đề thi." onBack={onBack} />;

    const isMultiple = currentQ.questionType === 'MULTIPLE_CHOICE';
    const selectedOpts = answers[currentQ.id] || [];
    const answeredCount = Object.keys(answers).filter(qid => (answers[qid] || []).length > 0).length;

    /* ─── Render ─────────────────────────────────────────────── */
    return (
      <div className="main" style={{ minHeight: '100vh', background: '#eef2f7' }}>
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
        <PlayerTop onBack={onBack} authUser={authUser} />

        <div style={{ display: 'flex', gap: 0, flex: 1, alignItems: 'stretch' }}>

          {/* ── Question area ──────────────────────────────── */}
          <div className="grow" style={{ minWidth: 0, padding: '28px 32px', maxWidth: 860, margin: '0 auto', width: '100%' }}>

            {/* Progress */}
            <div className="row gap-12" style={{ marginBottom: 20 }}>
              <span className="chip chip-info" style={{ fontSize: 13, padding: '6px 14px' }}>
                Câu {currentIdx + 1} / {questions.length}
              </span>
              {isMultiple && (
                <span className="chip chip-warning" style={{ fontSize: 12 }}>Nhiều đáp án</span>
              )}
              {flagged[currentQ.id] && (
                <span className="chip chip-error" style={{ fontSize: 12 }}><Ic n="flag" size={12} />Đã đánh dấu</span>
              )}
            </div>

            {/* Question text */}
            <h1 className="t-h1" style={{ margin: '0 0 8px', fontSize: 20 }}>{currentQ.questionText}</h1>
            <p className="muted" style={{ margin: '0 0 22px', fontSize: 13 }}>
              {isMultiple ? 'Chọn tất cả đáp án đúng' : 'Chọn một đáp án'}
            </p>

            {/* Options */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {currentQ.options.map((opt, i) => {
                const selected = selectedOpts.includes(opt.id);
                return (
                  <label
                    key={opt.id}
                    className="row gap-14"
                    style={{
                      padding: '15px 18px', borderRadius: 13, cursor: 'pointer',
                      background: selected ? 'var(--accent-soft)' : '#fff',
                      border: `1.5px solid ${selected ? 'var(--accent)' : 'var(--border)'}`,
                      transition: '.15s',
                    }}
                    onClick={() => toggleAnswer(currentQ.id, opt.id, isMultiple)}
                  >
                    <span style={{
                      width: 22, height: 22, flex: 'none',
                      borderRadius: isMultiple ? 5 : 999,
                      border: `2px solid ${selected ? 'var(--accent)' : 'var(--border-input)'}`,
                      display: 'grid', placeItems: 'center',
                    }}>
                      {selected && (
                        <span style={{
                          width: 11, height: 11,
                          borderRadius: isMultiple ? 3 : 999,
                          background: 'var(--accent)',
                        }} />
                      )}
                    </span>
                    <span style={{
                      fontSize: 15, fontWeight: selected ? 600 : 500,
                      color: selected ? 'var(--accent)' : 'var(--text)',
                    }}>
                      <b style={{ color: 'var(--text-3)', marginRight: 8 }}>{String.fromCharCode(65 + i)}.</b>
                      {opt.optionText}
                    </span>
                  </label>
                );
              })}
            </div>

            {/* Nav buttons */}
            <div className="between" style={{ marginTop: 28 }}>
              <div className="row gap-8">
                <button
                  className="btn btn-ghost"
                  disabled={currentIdx === 0}
                  onClick={() => setCurrentIdx(i => i - 1)}
                >
                  <Ic n="chevron_left" size={17} />Câu trước
                </button>
                <button
                  className="btn btn-ghost"
                  onClick={() => toggleFlag(currentQ.id)}
                  style={{ color: flagged[currentQ.id] ? 'var(--warning)' : undefined }}
                >
                  <Ic n="flag" size={16} />{flagged[currentQ.id] ? 'Bỏ đánh dấu' : 'Đánh dấu'}
                </button>
              </div>
              {currentIdx < questions.length - 1 ? (
                <button className="btn btn-primary" onClick={() => setCurrentIdx(i => i + 1)}>
                  Câu tiếp theo<Ic n="chevron_right" size={17} />
                </button>
              ) : (
                <button className="btn btn-success" onClick={handleManualSubmit}>
                  <Ic n="send" size={16} />Nộp bài
                </button>
              )}
            </div>
          </div>

          {/* ── Proctoring rail ────────────────────────────── */}
          <div className="dark-scroll quiz-rail" style={{
            width: 300, flex: 'none', background: 'var(--sidebar-bg)',
            color: '#fff', padding: 20, overflowY: 'auto',
          }}>
            {/* Timer */}
            <div style={{ background: 'rgba(255,255,255,.05)', borderRadius: 14, padding: 16, textAlign: 'center', marginBottom: 16 }}>
              <div className="row gap-7" style={{ justifyContent: 'center', color: timerWarning ? '#f87171' : '#fbbf24', marginBottom: 8 }}>
                <Ic n="clock" size={16} />
                <span style={{ fontSize: 13, fontWeight: 600 }}>Thời gian còn lại</span>
              </div>
              <div className="mono" style={{
                fontSize: 38, fontWeight: 700, letterSpacing: '0.02em',
                color: timerWarning ? '#f87171' : '#fff',
              }}>
                {mm}:{ss}
              </div>
            </div>

            {/* Progress */}
            <div style={{ background: 'rgba(255,255,255,.05)', borderRadius: 12, padding: 13, marginBottom: 14 }}>
              <div className="row gap-8" style={{ marginBottom: 8 }}>
                <span style={{ fontSize: 12, color: '#94a3b8' }}>Đã trả lời</span>
                <span style={{ fontWeight: 700, color: '#a7f3d0', marginLeft: 'auto' }}>
                  {answeredCount} / {questions.length}
                </span>
              </div>
              <div style={{ height: 6, background: 'rgba(255,255,255,.12)', borderRadius: 999 }}>
                <div style={{
                  height: '100%', borderRadius: 999, background: '#10b981',
                  width: `${questions.length ? (answeredCount / questions.length) * 100 : 0}%`,
                  transition: 'width .3s',
                }} />
              </div>
            </div>

            {/* Proctoring badge */}
            {proctoringRef.current && (
              <div style={{
                background: 'rgba(16,185,129,.1)', border: '1px solid rgba(16,185,129,.25)',
                borderRadius: 11, padding: 11, marginBottom: 14,
                display: 'flex', gap: 9, alignItems: 'center',
              }}>
                <Ic n="shield" size={16} style={{ color: 'var(--success)', flex: 'none' }} />
                <div style={{ fontSize: 11.5, color: '#a7f3d0' }}>Chế độ giám sát đang bật. Không chuyển tab.</div>
              </div>
            )}

            {/* Submit button */}
            <button
              className="btn btn-success btn-block btn-lg"
              style={{ marginBottom: 16 }}
              onClick={handleManualSubmit}
            >
              <Ic n="send" size={16} />Nộp bài ngay
            </button>

            {/* Question grid */}
            <div className="row gap-8" style={{ color: 'var(--success)', marginBottom: 12, fontWeight: 700 }}>
              <Ic n="grid" size={17} />Danh sách câu hỏi
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5,1fr)', gap: 7 }}>
              {questions.map((q, i) => {
                const answered = (answers[q.id] || []).length > 0;
                const cur = i === currentIdx;
                const fl = flagged[q.id];
                return (
                  <div
                    key={q.id}
                    onClick={() => setCurrentIdx(i)}
                    style={{
                      aspectRatio: '1', borderRadius: 8, display: 'grid',
                      placeItems: 'center', fontSize: 12, fontWeight: 700,
                      cursor: 'pointer', position: 'relative',
                      background: cur ? 'transparent' : answered ? 'rgba(37,99,235,.85)' : 'rgba(255,255,255,.05)',
                      border: cur ? '2px solid var(--success)' : fl ? '1.5px solid #f59e0b' : '1px solid rgba(255,255,255,.08)',
                      color: cur ? 'var(--success)' : answered ? '#fff' : '#94a3b8',
                    }}
                  >
                    {i + 1}
                    {fl && (
                      <span style={{
                        position: 'absolute', top: 2, right: 3,
                        width: 5, height: 5, borderRadius: 999, background: '#f59e0b',
                      }} />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* ═══ Modal: Xác nhận nộp bài ═══ */}
        <Modal open={confirmSubmit} onClose={() => setConfirmSubmit(false)} max={440}>
          <div style={{ padding: '28px 26px 22px', textAlign: 'center' }}>
            <div style={{
              width: 68, height: 68, borderRadius: 999,
              background: 'var(--accent-soft)', display: 'grid',
              placeItems: 'center', margin: '0 auto 16px',
            }}>
              <Ic n="send" size={32} style={{ color: 'var(--accent)' }} />
            </div>
            <h2 className="t-h2" style={{ margin: '0 0 8px' }}>Xác nhận nộp bài?</h2>
            <p className="muted" style={{ margin: '0 0 6px', lineHeight: 1.6 }}>
              Bạn đã trả lời <b style={{ color: 'var(--text)' }}>{answeredCount}/{questions.length}</b> câu.
              {answeredCount < questions.length && (
                <> Còn <b style={{ color: 'var(--error)' }}>{questions.length - answeredCount} câu chưa trả lời</b>.</>
              )}
            </p>
            <p className="muted" style={{ fontSize: 13 }}>Sau khi nộp bạn không thể chỉnh sửa đáp án.</p>
            <div className="row gap-10" style={{ marginTop: 22, justifyContent: 'center' }}>
              <button className="btn btn-ghost" onClick={() => setConfirmSubmit(false)}>Tiếp tục làm</button>
              <button className="btn btn-success" onClick={() => { setConfirmSubmit(false); doSubmit(false); }}>
                <Ic n="send" size={16} />Nộp bài
              </button>
            </div>
          </div>
        </Modal>

        {/* ═══ Modal: Cảnh báo vi phạm ═══ */}
        <Modal open={!!warnViolation} onClose={() => setWarnViolation(null)} max={460}>
          <div style={{ padding: '28px 26px 22px', textAlign: 'center' }}>
            <div style={{
              width: 68, height: 68, borderRadius: 999,
              background: 'var(--chip-error-bg)', display: 'grid',
              placeItems: 'center', margin: '0 auto 16px',
            }}>
              <Ic n="warn" size={34} style={{ color: 'var(--error)' }} />
            </div>
            <h2 className="t-h2" style={{ color: 'var(--error)', margin: '0 0 10px' }}>Cảnh báo vi phạm!</h2>
            <p className="muted" style={{ margin: '0 0 6px', lineHeight: 1.6 }}>
              Hệ thống phát hiện bạn đã rời khỏi trình duyệt. Đây là lần vi phạm thứ{' '}
              <b style={{ color: 'var(--text)' }}>{warnViolation?.order}/{warnViolation?.total}</b>.
            </p>
            <div style={{
              background: 'var(--chip-error-bg)', borderRadius: 10, padding: '11px 14px',
              margin: '14px 0 0', fontSize: 13, color: 'var(--chip-error-fg)',
            }}>
              Vi phạm lần thứ {warnViolation?.total} sẽ tự động nộp bài và kết thúc bài thi ngay lập tức.
            </div>
            <button
              className="btn btn-primary btn-block btn-lg"
              style={{ marginTop: 20 }}
              onClick={() => setWarnViolation(null)}
            >
              Tôi đã hiểu và cam kết không tái phạm
            </button>
          </div>
        </Modal>

        {/* ═══ Modal: Lockout ═══ */}
        <Modal open={lockoutModal} onClose={() => {}} max={440}>
          <div style={{ padding: '28px 26px 22px', textAlign: 'center' }}>
            <div style={{
              width: 68, height: 68, borderRadius: 999,
              background: 'var(--chip-error-bg)', display: 'grid',
              placeItems: 'center', margin: '0 auto 16px',
            }}>
              <Ic n="lock" size={34} style={{ color: 'var(--error)' }} />
            </div>
            <h2 className="t-h2" style={{ color: 'var(--error)', margin: '0 0 10px' }}>Bài thi đã bị khóa</h2>
            <p className="muted" style={{ lineHeight: 1.6 }}>
              Bạn đã vi phạm quá nhiều lần. Bài thi đã tự động được nộp.
            </p>
            <p className="muted" style={{ fontSize: 13, marginTop: 6 }}>Đang chuyển đến trang kết quả...</p>
          </div>
        </Modal>
      </div>
    );
  }

  Object.assign(window, { QuizPlayer });
})();
