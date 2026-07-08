// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Toàn màn hình · Kết quả bài kiểm tra
   ============================================================ */
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Avatar } = window;

  const { getAttemptResult } = window.__quizService;

  function PlayerTop({ onBack, authUser }) {
    return (
      <div className="topbar" style={{ paddingLeft: 20, paddingRight: 24 }}>
        <button className="icon-btn" onClick={onBack}><Ic n="arrow_left" size={20} /></button>
        <div className="row gap-10">
          <div className="sb-logo" style={{ width: 34, height: 34, borderRadius: 9 }}><Ic n="cap" size={18} /></div>
          <b style={{ fontSize: 16 }}>Rikkei Edu</b>
        </div>
        <div className="grow" />
        <Avatar name={authUser?.fullName || 'Học viên'} size={40} />
      </div>
    );
  }

  function LoadingSkeleton() {
    return (
      <div className="main" style={{ minHeight: '100vh' }}>
        <div style={{ padding: '60px 28px', maxWidth: 820, margin: '0 auto' }}>
          <div style={{ height: 180, background: '#e2e8f0', borderRadius: 16, marginBottom: 22 }} />
          {[1, 2, 3].map(i => (
            <div key={i} style={{ height: 60, background: '#e2e8f0', borderRadius: 11, marginBottom: 10 }} />
          ))}
        </div>
      </div>
    );
  }

  function QuizResult({ attemptId, courseId, quizId, onBack, authUser }) {
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [showAll, setShowAll] = useState(false);

    useEffect(() => {
      if (!attemptId || !courseId || !quizId) {
        setError('Không tìm thấy thông tin kết quả. Vui lòng quay lại trang bài tập.');
        setLoading(false);
        return;
      }
      getAttemptResult(courseId, quizId, attemptId)
        .then(data => { setResult(data); setLoading(false); })
        .catch(err => {
          setError(err?.response?.data?.message || 'Không thể tải kết quả. Vui lòng thử lại.');
          setLoading(false);
        });
    }, [attemptId, courseId, quizId]);

    if (loading) return <LoadingSkeleton />;

    if (error || !result) {
      return (
        <div className="main" style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
          <div style={{ textAlign: 'center', padding: 40 }}>
            <div style={{ width: 68, height: 68, borderRadius: 999, background: 'var(--chip-error-bg)', display: 'grid', placeItems: 'center', margin: '0 auto 16px' }}>
              <Ic n="warn" size={32} style={{ color: 'var(--error)' }} />
            </div>
            <h2 style={{ margin: '0 0 10px', color: 'var(--error)' }}>Không tải được kết quả</h2>
            <p style={{ color: 'var(--text-2)', marginBottom: 22 }}>{error}</p>
            <button className="btn btn-primary" onClick={onBack}>Quay lại</button>
          </div>
        </div>
      );
    }

    const pct = Number(result.scorePercentage ?? 0).toFixed(1);
    const passed = result.isPassed;
    const headerBg = passed
      ? 'linear-gradient(135deg,#059669,#10b981)'
      : 'linear-gradient(135deg,#dc2626,#ef4444)';
    const headerIcon = passed ? 'check_circle' : 'x_circle';

    const displayAnswers = showAll ? result.answers : result.answers.slice(0, 10);

    function fmtTime(secs) {
      if (!secs) return '—';
      const m = Math.floor(secs / 60), s = secs % 60;
      return `${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
    }

    return (
      <div className="main" style={{ minHeight: '100vh' }}>
        <PlayerTop onBack={onBack} authUser={authUser} />
        <div className="page" style={{ maxWidth: 820 }}>

          {/* ── Score card ── */}
          <div className="card fade-in" style={{ overflow: 'hidden', marginBottom: 22 }}>
            <div style={{ background: headerBg, padding: '32px 28px', textAlign: 'center', color: '#fff' }}>
              <div style={{ width: 76, height: 76, borderRadius: 999, background: 'rgba(255,255,255,.2)', display: 'grid', placeItems: 'center', margin: '0 auto 14px' }}>
                <Ic n={headerIcon} size={42} />
              </div>
              <div style={{ fontSize: 14, opacity: .9, marginBottom: 4 }}>
                {passed ? 'Chúc mừng! Bạn đã vượt qua bài kiểm tra' : 'Bạn chưa đạt yêu cầu. Hãy cố gắng hơn!'}
              </div>
              <div style={{ fontSize: 13, opacity: .75 }}>Kết quả bài thi</div>
            </div>

            {/* Stats row */}
            <div className="grid" style={{ gridTemplateColumns: 'repeat(3,1fr)', padding: '20px 24px', gap: 0 }}>
              {[
                { l: 'Tỉ lệ đúng', v: `${pct}%`, c: passed ? 'var(--success)' : 'var(--error)' },
                { l: 'Câu đúng', v: `${result.correctCount}/${result.totalQuestions}`, c: 'var(--text)' },
                { l: 'Thời gian', v: fmtTime(result.timeSpentSeconds), c: 'var(--text)' },
              ].map((s, i) => (
                <div key={i} style={{ textAlign: 'center', padding: '0 10px', borderRight: i < 2 ? '1px solid var(--border)' : 'none' }}>
                  <div style={{ fontSize: 28, fontWeight: 800, color: s.c }}>{s.v}</div>
                  <div className="t-sm muted" style={{ marginTop: 4 }}>{s.l}</div>
                </div>
              ))}
            </div>

            {/* Pass/fail badges */}
            <div className="row gap-10" style={{ padding: '0 24px 18px', justifyContent: 'center' }}>
              <span className={`chip chip-${passed ? 'success' : 'error'}`} style={{ fontSize: 13, padding: '6px 16px' }}>
                <Ic n={passed ? 'check' : 'x'} size={13} />
                {passed ? 'ĐẠT' : 'CHƯA ĐẠT'}
              </span>
              {result.autoSubmitted && (
                <span className="chip chip-warning" style={{ fontSize: 12 }}>
                  <Ic n="clock" size={12} />Tự động nộp
                </span>
              )}
              {result.violationCount > 0 && (
                <span className="chip chip-error" style={{ fontSize: 12 }}>
                  <Ic n="shield" size={12} />{result.violationCount} vi phạm
                </span>
              )}
            </div>
          </div>

          {/* ── Answer review ── */}
          <div className="card" style={{ padding: 0, overflow: 'hidden', marginBottom: 22 }}>
            <div style={{ padding: '18px 22px', borderBottom: '1px solid var(--border)' }}>
              <div className="between">
                <div>
                  <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700 }}>Chi tiết đáp án</h3>
                  <p className="t-sm muted" style={{ margin: '3px 0 0' }}>Xem lại từng câu trả lời và giải thích</p>
                </div>
                <div className="row gap-14" style={{ fontSize: 13 }}>
                  <span className="row gap-5" style={{ color: 'var(--success)' }}>
                    <Ic n="check_circle" size={14} />{result.correctCount} đúng
                  </span>
                  <span className="row gap-5" style={{ color: 'var(--error)' }}>
                    <Ic n="x_circle" size={14} />{result.incorrectCount} sai
                  </span>
                  {result.unansweredCount > 0 && (
                    <span className="row gap-5 muted">
                      <Ic n="help" size={14} />{result.unansweredCount} bỏ qua
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div style={{ padding: '8px 0' }}>
              {displayAnswers.map((ans, i) => (
                <div
                  key={ans.questionId}
                  style={{
                    padding: '14px 22px',
                    borderBottom: i < displayAnswers.length - 1 ? '1px solid var(--border)' : 'none',
                    background: ans.isCorrect ? 'rgba(16,185,129,.03)' : ans.selectedOptionIds.length === 0 ? 'rgba(100,116,139,.03)' : 'rgba(239,68,68,.03)',
                  }}
                >
                  <div className="row gap-12" style={{ alignItems: 'flex-start' }}>
                    {/* Status icon */}
                    <div style={{
                      width: 30, height: 30, borderRadius: 999, flex: 'none',
                      display: 'grid', placeItems: 'center', marginTop: 2,
                      background: ans.isCorrect ? 'var(--chip-success-bg)' : ans.selectedOptionIds.length === 0 ? 'var(--surface-2)' : 'var(--chip-error-bg)',
                      color: ans.isCorrect ? 'var(--success)' : ans.selectedOptionIds.length === 0 ? 'var(--text-3)' : 'var(--error)',
                    }}>
                      <Ic n={ans.isCorrect ? 'check' : ans.selectedOptionIds.length === 0 ? 'minus' : 'x'} size={16} />
                    </div>

                    <div className="grow" style={{ minWidth: 0 }}>
                      {/* Question */}
                      <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 6, lineHeight: 1.5 }}>
                        <span className="muted" style={{ fontWeight: 500, marginRight: 6 }}>Câu {i + 1}.</span>
                        {ans.questionText}
                      </div>

                      {/* Status */}
                      <div className="row gap-8" style={{ marginBottom: 8, flexWrap: 'wrap' }}>
                        <span className={`chip chip-${ans.isCorrect ? 'success' : ans.selectedOptionIds.length === 0 ? 'neutral' : 'error'}`} style={{ fontSize: 11 }}>
                          {ans.isCorrect ? 'Đúng' : ans.selectedOptionIds.length === 0 ? 'Bỏ qua' : 'Sai'}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {result.answers.length > 10 && (
              <div style={{ padding: '14px 22px', textAlign: 'center', borderTop: '1px solid var(--border)' }}>
                <button className="btn btn-ghost btn-sm" onClick={() => setShowAll(v => !v)}>
                  {showAll ? 'Thu gọn' : `Xem thêm ${result.answers.length - 10} câu`}
                  <Ic n={showAll ? 'chevron_up' : 'chevron_down'} size={15} />
                </button>
              </div>
            )}
          </div>

          {/* ── Action buttons ── */}
          <div className="row gap-12" style={{ justifyContent: 'center', marginBottom: 32 }}>
            <button className="btn btn-ghost" onClick={onBack}>
              <Ic n="arrow_left" size={16} />Về trang bài tập
            </button>
          </div>
        </div>
      </div>
    );
  }

  Object.assign(window, { QuizResult });
})();
