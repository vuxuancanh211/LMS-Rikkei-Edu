// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Toàn màn hình · Làm thử Quiz (Dry Run, chỉ Giảng viên)
   Trải nghiệm giống hệt học viên (chọn đáp án, chuyển câu, chấm điểm)
   nhưng KHÔNG lưu kết quả vào hệ thống, không tính vào thống kê thật.
   ============================================================ */
(function () {
  const { useState, useEffect, useCallback, useRef } = React;
  const Ic = window.Icon;
  const { Avatar, Modal } = window;

  const { dryRunQuiz, gradeDryRunQuiz } = window.__quizService;

  const MAX_VIOLATIONS = 3;

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
        <Avatar name={authUser?.fullName || 'Giảng viên'} size={40} />
      </div>
    );
  }

  function LoadingSkeleton({ label }) {
    return (
      <div className="main" style={{ minHeight: '100vh', background: '#eef2f7' }}>
        <div style={{ padding: '60px 28px', maxWidth: 860, margin: '0 auto' }}>
          <div style={{ height: 28, width: 200, background: '#e2e8f0', borderRadius: 8, marginBottom: 24 }} />
          {[1, 2, 3, 4].map(i => (
            <div key={i} style={{ height: 60, background: '#e2e8f0', borderRadius: 12, marginBottom: 12 }} />
          ))}
          {label && <p className="muted" style={{ textAlign: 'center', marginTop: 8 }}>{label}</p>}
        </div>
      </div>
    );
  }

  function ErrorScreen({ message, onBack, onRetry }) {
    return (
      <div className="main" style={{ minHeight: '100vh', background: '#eef2f7', display: 'grid', placeItems: 'center' }}>
        <div style={{ textAlign: 'center', padding: 40, maxWidth: 440 }}>
          <div style={{ width: 72, height: 72, borderRadius: 999, background: 'var(--chip-error-bg)', display: 'grid', placeItems: 'center', margin: '0 auto 18px' }}>
            <Ic n="warn" size={36} style={{ color: 'var(--error)' }} />
          </div>
          <h2 style={{ margin: '0 0 10px', color: 'var(--error)' }}>Không chạy thử được</h2>
          <p style={{ color: 'var(--text-2)', marginBottom: 24 }}>{message}</p>
          <div className="row gap-10" style={{ justifyContent: 'center' }}>
            <button className="btn btn-ghost" onClick={onBack}>Quay lại</button>
            <button className="btn btn-primary" onClick={onRetry}>Thử lại</button>
          </div>
        </div>
      </div>
    );
  }

  function QuizDryRunPlayer({ courseId, quizId, quizTitle, proctoringEnabled, onBack, authUser }) {
    const [phase, setPhase] = useState('loading'); // loading | error | playing | grading | review
    const [errorMsg, setErrorMsg] = useState('');
    const [questions, setQuestions] = useState([]);
    const [currentIdx, setCurrentIdx] = useState(0);
    const [answers, setAnswers] = useState({}); // { questionId: [optionId, ...] }
    const [flagged, setFlagged] = useState({});
    const [gradeResult, setGradeResult] = useState(null);
    const [gradeError, setGradeError] = useState('');

    // Đếm ngược thời gian làm bài — giống hệt trải nghiệm học viên thật (không lưu, chỉ mô phỏng)
    const [durationMinutes, setDurationMinutes] = useState(null);
    const [secsLeft, setSecsLeft] = useState(null);

    // Mô phỏng giám sát (client-side only, không lưu, không gọi API proctoring thật)
    const [violationCount, setViolationCount] = useState(0);
    const [warnViolation, setWarnViolation] = useState(null); // { order, total }
    const [lockoutModal, setLockoutModal] = useState(false);
    const violationCountRef = useRef(0);
    const lockedRef = useRef(false);

    const load = useCallback(() => {
      if (!courseId || !quizId) {
        setErrorMsg('Không tìm thấy thông tin quiz. Vui lòng quay lại và thử lại.');
        setPhase('error');
        return;
      }
      setPhase('loading'); setErrorMsg('');
      dryRunQuiz(courseId, quizId)
        .then(data => {
          setQuestions(data.questions || []);
          setAnswers({});
          setFlagged({});
          setCurrentIdx(0);
          setGradeResult(null);
          violationCountRef.current = 0;
          lockedRef.current = false;
          setViolationCount(0);
          setWarnViolation(null);
          setLockoutModal(false);
          setDurationMinutes(data.durationMinutes ?? null);
          setSecsLeft(data.durationMinutes ? data.durationMinutes * 60 : null);
          setPhase((data.questions || []).length > 0 ? 'playing' : 'review');
        })
        .catch(err => {
          setErrorMsg(err?.response?.data?.message || 'Không thể chạy thử quiz. Vui lòng thử lại.');
          setPhase('error');
        });
    }, [courseId, quizId]);

    // Tự động chạy ngay khi vào trang — không cần bấm thêm nút nào
    useEffect(() => { load(); }, [load]);

    /* ── Mô phỏng giám sát: phát hiện chuyển tab / mất focus cửa sổ ── */
    useEffect(() => {
      if (phase !== 'playing' || !proctoringEnabled) return;

      function handleViolation() {
        if (lockedRef.current) return;
        const nextCount = violationCountRef.current + 1;
        violationCountRef.current = nextCount;
        setViolationCount(nextCount);
        if (nextCount >= MAX_VIOLATIONS) {
          lockedRef.current = true;
          setLockoutModal(true);
          setTimeout(() => { finishAndGrade(); }, 1500);
        } else {
          setWarnViolation({ order: nextCount, total: MAX_VIOLATIONS });
        }
      }

      function onVisibilityChange() {
        if (document.visibilityState === 'hidden') handleViolation();
      }
      function onBlur() { handleViolation(); }

      document.addEventListener('visibilitychange', onVisibilityChange);
      window.addEventListener('blur', onBlur);
      return () => {
        document.removeEventListener('visibilitychange', onVisibilityChange);
        window.removeEventListener('blur', onBlur);
      };
    }, [phase, proctoringEnabled]);

    /* ── Đếm ngược thời gian làm bài — hết giờ tự động hoàn tất & chấm điểm ── */
    useEffect(() => {
      if (phase !== 'playing' || secsLeft === null) return;
      const t = setInterval(() => {
        setSecsLeft(s => {
          if (s <= 1) {
            clearInterval(t);
            finishAndGrade();
            return 0;
          }
          return s - 1;
        });
      }, 1000);
      return () => clearInterval(t);
    }, [phase]);

    function toggleAnswer(questionId, optionId, isMultiple) {
      setAnswers(prev => {
        const cur = prev[questionId] || [];
        if (isMultiple) {
          const next = cur.includes(optionId) ? cur.filter(id => id !== optionId) : [...cur, optionId];
          return { ...prev, [questionId]: next };
        }
        return { ...prev, [questionId]: [optionId] };
      });
    }

    function toggleFlag(questionId) {
      setFlagged(prev => ({ ...prev, [questionId]: !prev[questionId] }));
    }

    function restart() {
      setAnswers({}); setFlagged({}); setCurrentIdx(0); setGradeResult(null);
      setSecsLeft(durationMinutes ? durationMinutes * 60 : null);
      setPhase('playing');
    }

    async function finishAndGrade() {
      setPhase('grading'); setGradeError('');
      try {
        const result = await gradeDryRunQuiz(courseId, quizId, {
          questionIds: questions.map(q => q.id),
          answers,
        });
        setGradeResult(result);
        setPhase('review');
      } catch (err) {
        setGradeError(err?.response?.data?.message || 'Không thể chấm điểm bản xem thử. Vui lòng thử lại.');
        setPhase('review');
      }
    }

    if (phase === 'loading') return <LoadingSkeleton />;
    if (phase === 'grading') return <LoadingSkeleton label="Đang chấm điểm..." />;
    if (phase === 'error') return <ErrorScreen message={errorMsg} onBack={onBack} onRetry={load} />;

    const answeredCount = Object.keys(answers).filter(qid => (answers[qid] || []).length > 0).length;

    /* ── Hiển thị đếm ngược ──────────────────────────────────── */
    const hasTimer = secsLeft !== null;
    const mm = hasTimer ? String(Math.floor(secsLeft / 60)).padStart(2, '0') : '';
    const ss = hasTimer ? String(secsLeft % 60).padStart(2, '0') : '';
    const timerWarning = hasTimer && secsLeft > 0 && secsLeft <= 300; // đỏ khi còn <= 5 phút

    /* ── Màn hình tổng kết + chấm điểm ── */
    if (phase === 'review') {
      const passed = gradeResult?.isPassed;
      const headerBg = gradeResult
        ? (passed ? 'linear-gradient(135deg,#059669,#10b981)' : 'linear-gradient(135deg,#dc2626,#ef4444)')
        : 'linear-gradient(135deg,#64748b,#94a3b8)';
      const resultByQuestion = {};
      (gradeResult?.answers || []).forEach(a => { resultByQuestion[a.questionId] = a; });

      return (
        <div className="main" style={{ minHeight: '100vh', background: '#eef2f7' }}>
          <PlayerTop title={quizTitle ? `Làm thử: ${quizTitle}` : 'Làm thử quiz'} onBack={onBack} authUser={authUser} />
          <div style={{ padding: '32px 28px', maxWidth: 820, margin: '0 auto', width: '100%' }}>

            {gradeError && (
              <div style={{ padding: '10px 14px', background: 'var(--chip-error-bg)', color: 'var(--error)', borderRadius: 10, fontSize: 13, marginBottom: 16 }}>
                {gradeError}
              </div>
            )}

            {gradeResult && (
              <div className="card fade-in" style={{ overflow: 'hidden', marginBottom: 20 }}>
                <div style={{ background: headerBg, padding: '26px 26px', textAlign: 'center', color: '#fff' }}>
                  <div style={{ width: 60, height: 60, borderRadius: 999, background: 'rgba(255,255,255,.2)', display: 'grid', placeItems: 'center', margin: '0 auto 10px' }}>
                    <Ic n={passed ? 'check_circle' : 'x_circle'} size={30} />
                  </div>
                  <div style={{ fontSize: 13.5, opacity: .9 }}>
                    {passed ? 'Đạt yêu cầu — bản xem thử' : 'Chưa đạt yêu cầu — bản xem thử'}
                  </div>
                </div>
                <div className="grid" style={{ gridTemplateColumns: 'repeat(4,1fr)', padding: '18px 20px', gap: 0 }}>
                  {[
                    { l: 'Điểm số', v: `${Number(gradeResult.score ?? 0)}/${Number(gradeResult.maxScore ?? 0)}` },
                    { l: 'Tỉ lệ đúng', v: `${Number(gradeResult.scorePercentage ?? 0)}%` },
                    { l: 'Câu đúng', v: `${gradeResult.correctCount}/${gradeResult.totalQuestions}` },
                    { l: 'Bỏ qua', v: gradeResult.unansweredCount },
                  ].map((s, i) => (
                    <div key={i} style={{ textAlign: 'center', padding: '0 8px', borderRight: i < 3 ? '1px solid var(--border)' : 'none' }}>
                      <div style={{ fontSize: 22, fontWeight: 800 }}>{s.v}</div>
                      <div className="t-xs muted" style={{ marginTop: 3 }}>{s.l}</div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {!gradeResult && !gradeError && (
              <div className="card fade-in" style={{ padding: '22px 26px', marginBottom: 20 }}>
                <h2 style={{ margin: '0 0 4px', fontSize: 17 }}>Đã xem thử xong</h2>
                <p className="t-sm muted" style={{ margin: 0 }}>
                  Bạn đã trả lời {answeredCount}/{questions.length} câu · Đây là bản xem trước, không lưu kết quả.
                </p>
              </div>
            )}

            {questions.length > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 24 }}>
                {questions.map((qq, i) => {
                  const selected = answers[qq.id] || [];
                  const r = resultByQuestion[qq.id];
                  const statusChip = !r ? null : !r.answered
                    ? <span className="chip chip-neutral" style={{ fontSize: 10 }}>Bỏ qua</span>
                    : r.isCorrect
                      ? <span className="chip chip-success" style={{ fontSize: 10 }}><Ic n="check" size={10} />Đúng</span>
                      : <span className="chip chip-error" style={{ fontSize: 10 }}><Ic n="x" size={10} />Sai</span>;
                  return (
                    <div key={qq.id || i} className="card" style={{
                      padding: 16,
                      borderLeft: r ? `3px solid ${r.isCorrect ? 'var(--success)' : r.answered ? 'var(--error)' : 'var(--border)'}` : undefined,
                    }}>
                      <div className="row gap-8" style={{ marginBottom: 10, flexWrap: 'wrap' }}>
                        <span className="muted" style={{ fontSize: 12.5, fontWeight: 600 }}>Câu {i + 1}</span>
                        <span className="t-xs muted">
                          {r ? `${Number(r.pointsEarned ?? 0)}/${qq.points} điểm` : `${qq.points} điểm`}
                        </span>
                        {statusChip}
                      </div>
                      <div style={{ fontSize: 14, fontWeight: 500, marginBottom: 10, lineHeight: 1.6 }}>{qq.questionText}</div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                        {qq.options?.map((o, j) => {
                          const isSelected = selected.includes(o.id);
                          const isCorrectOption = r?.correctOptionIds?.includes(o.id);
                          // Chỉ tô đáp án đúng khi câu này SAI (giúp giảng viên đối chiếu nhanh)
                          const highlightCorrect = r && !r.isCorrect && isCorrectOption;
                          const bg = isSelected
                            ? (r ? (r.isCorrect ? 'var(--chip-success-bg)' : 'var(--chip-error-bg)') : 'var(--accent-soft)')
                            : highlightCorrect ? 'var(--chip-success-bg)' : 'var(--surface-2)';
                          const border = isSelected
                            ? `1.5px solid ${r ? (r.isCorrect ? 'var(--success)' : 'var(--error)') : 'var(--accent)'}`
                            : highlightCorrect ? '1.5px solid var(--success)' : '1.5px solid transparent';
                          const color = isSelected
                            ? (r ? (r.isCorrect ? 'var(--success)' : 'var(--error)') : 'var(--accent)')
                            : highlightCorrect ? 'var(--success)' : 'var(--text)';
                          return (
                            <div key={o.id || j} style={{
                              padding: '8px 12px', borderRadius: 9, fontSize: 13, display: 'flex', gap: 10,
                              background: bg, border, color,
                              fontWeight: isSelected || highlightCorrect ? 600 : 400,
                            }}>
                              <span style={{ flex: 'none', fontWeight: 700 }}>{String.fromCharCode(65 + j)}.</span>
                              <span>{o.optionText}</span>
                              {isSelected && <Ic n={r?.isCorrect === false ? 'x' : 'check'} size={13} style={{ marginLeft: 'auto', flex: 'none' }} />}
                              {!isSelected && highlightCorrect && (
                                <span className="t-xs" style={{ marginLeft: 'auto', flex: 'none', fontWeight: 700 }}>Đáp án đúng</span>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            <p className="t-xs muted" style={{ textAlign: 'center', marginBottom: 20 }}>
              Bản xem thử này không được lưu vào hệ thống và không tính vào thống kê thật của quiz.
            </p>

            <div className="row gap-10" style={{ justifyContent: 'center', marginBottom: 32, flexWrap: 'wrap' }}>
              <button className="btn btn-ghost" onClick={onBack}>
                <Ic n="arrow_left" size={16} />Quay lại soạn quiz
              </button>
              <button className="btn btn-ghost" onClick={restart} disabled={questions.length === 0}>
                <Ic n="rotate_ccw" size={16} />Làm lại từ đầu
              </button>
              <button className="btn btn-primary" onClick={load}>
                <Ic n="sparkles" size={16} />Chạy thử bộ câu khác
              </button>
            </div>
          </div>
        </div>
      );
    }

    /* ── Màn hình làm bài (chọn đáp án, chuyển câu) ── */
    const currentQ = questions[currentIdx];
    if (!currentQ) return <ErrorScreen message="Không có câu hỏi nào trong đề thi." onBack={onBack} onRetry={load} />;

    const isMultiple = currentQ.questionType === 'MULTIPLE_CHOICE';
    const selectedOpts = answers[currentQ.id] || [];

    return (
      <div className="main" style={{ minHeight: '100vh', background: '#eef2f7' }}>
        <PlayerTop title={quizTitle ? `Làm thử: ${quizTitle}` : 'Làm thử quiz'} onBack={onBack} authUser={authUser} />

        <div style={{ display: 'flex', gap: 0, flex: 1, alignItems: 'stretch' }}>

          {/* ── Question area ──────────────────────────────── */}
          <div className="grow" style={{ minWidth: 0, padding: '28px 32px', maxWidth: 860, margin: '0 auto', width: '100%' }}>

            <div className="row gap-12" style={{ marginBottom: 16, flexWrap: 'wrap' }}>
              <span className="chip chip-info" style={{ fontSize: 13, padding: '6px 14px' }}>
                Câu {currentIdx + 1} / {questions.length}
              </span>
              {isMultiple && <span className="chip chip-warning" style={{ fontSize: 12 }}>Nhiều đáp án</span>}
              {flagged[currentQ.id] && (
                <span className="chip chip-error" style={{ fontSize: 12 }}><Ic n="flag" size={12} />Đã đánh dấu</span>
              )}
              {proctoringEnabled && (
                <span className="chip chip-error" style={{ fontSize: 11 }}>
                  <Ic n="shield" size={11} />Mô phỏng giám sát — tránh chuyển tab
                </span>
              )}
              <span className="chip chip-neutral" style={{ fontSize: 11, marginLeft: 'auto' }}>
                <Ic n="eye" size={11} />Bản xem thử — kết quả sẽ không được lưu
              </span>
            </div>

            <h1 className="t-h1" style={{ margin: '0 0 8px', fontSize: 20 }}>{currentQ.questionText}</h1>
            <p className="muted" style={{ margin: '0 0 22px', fontSize: 13 }}>
              {isMultiple ? 'Chọn tất cả đáp án đúng' : 'Chọn một đáp án'} · {currentQ.points} điểm
            </p>

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
                        <span style={{ width: 11, height: 11, borderRadius: isMultiple ? 3 : 999, background: 'var(--accent)' }} />
                      )}
                    </span>
                    <span style={{ fontSize: 15, fontWeight: selected ? 600 : 500, color: selected ? 'var(--accent)' : 'var(--text)' }}>
                      <b style={{ color: 'var(--text-3)', marginRight: 8 }}>{String.fromCharCode(65 + i)}.</b>
                      {opt.optionText}
                    </span>
                  </label>
                );
              })}
            </div>

            <div className="between" style={{ marginTop: 28, flexWrap: 'wrap', gap: 12 }}>
              <div className="row gap-8">
                <button className="btn btn-ghost" disabled={currentIdx === 0} onClick={() => setCurrentIdx(i => i - 1)}>
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
                <button className="btn btn-success" onClick={finishAndGrade}>
                  <Ic n="check" size={16} />Hoàn tất & chấm điểm
                </button>
              )}
            </div>
          </div>

          {/* ── Rail điều hướng câu hỏi ─────────────────────── */}
          <div className="dark-scroll quiz-rail" style={{
            width: 300, flex: 'none', background: 'var(--sidebar-bg)',
            color: '#fff', padding: 20, overflowY: 'auto',
          }}>
            {hasTimer && (
              <div style={{ background: 'rgba(255,255,255,.05)', borderRadius: 14, padding: 16, textAlign: 'center', marginBottom: 16 }}>
                <div className="row gap-7" style={{ justifyContent: 'center', color: timerWarning ? '#f87171' : '#fbbf24', marginBottom: 8 }}>
                  <Ic n="clock" size={16} />
                  <span style={{ fontSize: 13, fontWeight: 600 }}>Thời gian còn lại (mô phỏng)</span>
                </div>
                <div className="mono" style={{
                  fontSize: 38, fontWeight: 700, letterSpacing: '0.02em',
                  color: timerWarning ? '#f87171' : '#fff',
                }}>
                  {mm}:{ss}
                </div>
              </div>
            )}

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

            {proctoringEnabled && (
              <div style={{
                background: 'rgba(16,185,129,.1)', border: '1px solid rgba(16,185,129,.25)',
                borderRadius: 11, padding: 11, marginBottom: 14,
                display: 'flex', gap: 9, alignItems: 'center',
              }}>
                <Ic n="shield" size={16} style={{ color: 'var(--success)', flex: 'none' }} />
                <div style={{ fontSize: 11.5, color: '#a7f3d0' }}>
                  Mô phỏng giám sát: {violationCount}/{MAX_VIOLATIONS} vi phạm
                </div>
              </div>
            )}

            <button className="btn btn-success btn-block btn-lg" style={{ marginBottom: 16 }} onClick={finishAndGrade}>
              <Ic n="check" size={16} />Hoàn tất & chấm điểm
            </button>

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
                    {fl && <span style={{ position: 'absolute', top: 2, right: 3, width: 5, height: 5, borderRadius: 999, background: '#f59e0b' }} />}
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* ═══ Modal: Cảnh báo vi phạm (mô phỏng) ═══ */}
        <Modal open={!!warnViolation} onClose={() => setWarnViolation(null)} max={460}>
          <div style={{ padding: '28px 26px 22px', textAlign: 'center' }}>
            <div style={{
              width: 68, height: 68, borderRadius: 999,
              background: 'var(--chip-error-bg)', display: 'grid',
              placeItems: 'center', margin: '0 auto 16px',
            }}>
              <Ic n="warn" size={34} style={{ color: 'var(--error)' }} />
            </div>
            <h2 className="t-h2" style={{ color: 'var(--error)', margin: '0 0 10px' }}>Cảnh báo vi phạm! (mô phỏng)</h2>
            <p className="muted" style={{ margin: '0 0 6px', lineHeight: 1.6 }}>
              Hệ thống phát hiện bạn đã rời khỏi trình duyệt. Đây là lần vi phạm thứ{' '}
              <b style={{ color: 'var(--text)' }}>{warnViolation?.order}/{warnViolation?.total}</b>.
            </p>
            <div style={{
              background: 'var(--chip-error-bg)', borderRadius: 10, padding: '11px 14px',
              margin: '14px 0 0', fontSize: 13, color: 'var(--chip-error-fg)',
            }}>
              Đây là bản xem thử — vi phạm lần thứ {warnViolation?.total} sẽ mô phỏng tự động hoàn tất
              &amp; chấm điểm, giống hệt trải nghiệm học viên thật.
            </div>
            <button
              className="btn btn-primary btn-block btn-lg"
              style={{ marginTop: 20 }}
              onClick={() => setWarnViolation(null)}
            >
              Đã hiểu, tiếp tục xem thử
            </button>
          </div>
        </Modal>

        {/* ═══ Modal: Khóa (mô phỏng) ═══ */}
        <Modal open={lockoutModal} onClose={() => {}} max={440}>
          <div style={{ padding: '28px 26px 22px', textAlign: 'center' }}>
            <div style={{
              width: 68, height: 68, borderRadius: 999,
              background: 'var(--chip-error-bg)', display: 'grid',
              placeItems: 'center', margin: '0 auto 16px',
            }}>
              <Ic n="lock" size={34} style={{ color: 'var(--error)' }} />
            </div>
            <h2 className="t-h2" style={{ color: 'var(--error)', margin: '0 0 10px' }}>Bản xem thử đã bị khóa (mô phỏng)</h2>
            <p className="muted" style={{ lineHeight: 1.6 }}>
              Bạn đã vi phạm quá số lần cho phép. Học viên thật sẽ bị tự động nộp bài ngay tại đây.
            </p>
            <p className="muted" style={{ fontSize: 13, marginTop: 6 }}>Đang chuyển sang chấm điểm...</p>
          </div>
        </Modal>
      </div>
    );
  }

  Object.assign(window, { QuizDryRunPlayer });
})();
