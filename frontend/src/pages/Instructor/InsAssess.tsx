// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Bài tập & Trắc nghiệm
   ============================================================ */
(function () {
  const { useState, useEffect, useCallback } = React;
  const Ic = window.Icon;
  const { Avatar, Status, Search, Tabs, Select, Section, Modal, ModalHead, Empty } = window;

  const {
    listQuizzes, createQuiz, publishQuiz, archiveQuiz, deleteQuiz,
    listBankQuestions, createBankQuestion, updateBankQuestion, deleteBankQuestion,
    toggleBankQuestionStatus, previewBankImport, confirmBankImport, exportBankQuestions,
    getQuizStats, getAllAttemptsForQuiz,
  } = window.__quizService;

  // Lấy danh sách khóa học từ mock data (sẽ swap sang API khi có course-service)
  function getInstructorCourses() {
    try { return (window.DATA?.courses || []).slice(0, 20); } catch { return []; }
  }

  const DIFF_LABEL = { EASY: 'Dễ', MEDIUM: 'Trung bình', HARD: 'Khó' };
  const DIFF_CHIP  = { EASY: 'success', MEDIUM: 'warning', HARD: 'error' };
  const STATUS_LABEL = { DRAFT: 'Nháp', PUBLISHED: 'Công khai', ARCHIVED: 'Lưu trữ' };
  const STATUS_CHIP  = { DRAFT: 'neutral', PUBLISHED: 'success', ARCHIVED: 'muted' };
  const TYPE_LABEL = { STATIC: 'Cố định', SHUFFLED_POOL: 'Xáo câu', RANDOM_DRAW: 'Ngẫu nhiên' };

  function formatDate(d) {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  function extractError(err) {
    const data = err?.response?.data;
    if (!data) return 'Không thể kết nối tới máy chủ';
    return data.message || 'Lỗi không xác định';
  }

  /* ─── Main Component ──────────────────────────────────────── */
  function InsAssess({ courseId, demo }) {
    const courses = getInstructorCourses();
    // courseId có thể truyền từ ngoài; nếu không thì chọn từ dropdown
    const [selectedCourseId, setSelectedCourseId] = useState(courseId || (courses[0]?.id || null));
    const activeCourseId = selectedCourseId;

    const [tab, setTab] = useState('quiz');
    const [q, setQ] = useState('');
    const [toast, setToast] = useState(null);

    // Quiz state
    const [quizzes, setQuizzes] = useState([]);
    const [quizLoading, setQuizLoading] = useState(false);

    // Bank state
    const [bankList, setBankList] = useState([]);
    const [bankLoading, setBankLoading] = useState(false);
    const [bankFilter, setBankFilter] = useState('ALL');

    // Modal states
    const [createQuizOpen, setCreateQuizOpen] = useState(false);
    const [addBankOpen, setAddBankOpen] = useState(false);
    const [editBankItem, setEditBankItem] = useState(null);
    const [importOpen, setImportOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // Stats modal state
    const [statsQuiz, setStatsQuiz] = useState(null);   // quiz object được chọn để xem stats
    const [statsOpen, setStatsOpen] = useState(false);

    // Create quiz form
    const [qzTitle, setQzTitle] = useState('');
    const [qzType, setQzType] = useState('STATIC');
    const [qzDuration, setQzDuration] = useState(45);
    const [qzMax, setQzMax] = useState(3);
    const [qzPass, setQzPass] = useState(50);
    const [qzProctoring, setQzProctoring] = useState(false);
    const [qzEndDate, setQzEndDate] = useState('');

    // Bank question form
    const [bqText, setBqText] = useState('');
    const [bqDiff, setBqDiff] = useState('EASY');
    const [bqType, setBqType] = useState('SINGLE_CHOICE');
    const [bqTag, setBqTag] = useState('');
    const [bqPoints, setBqPoints] = useState(1);
    const [bqOpts, setBqOpts] = useState([
      { optionText: '', isCorrect: true },
      { optionText: '', isCorrect: false },
      { optionText: '', isCorrect: false },
      { optionText: '', isCorrect: false },
    ]);

    // Import state
    const [importFile, setImportFile] = useState(null);
    const [importPreview, setImportPreview] = useState(null);
    const [importStep, setImportStep] = useState('pick'); // pick | preview | done

    const showToast = useCallback((msg, type = 'success') => {
      setToast({ msg, type });
      setTimeout(() => setToast(null), 4000);
    }, []);

    /* ── Fetch quizzes ── */
    const fetchQuizzes = useCallback(async () => {
      if (!activeCourseId) return;
      setQuizLoading(true);
      try {
        const data = await listQuizzes(activeCourseId);
        setQuizzes(data);
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setQuizLoading(false);
      }
    }, [activeCourseId, showToast]);

    /* ── Fetch bank questions ── */
    const fetchBank = useCallback(async () => {
      if (!activeCourseId) return;
      setBankLoading(true);
      try {
        const params = bankFilter !== 'ALL' ? { difficulty: bankFilter } : {};
        const data = await listBankQuestions(activeCourseId, params);
        setBankList(data);
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setBankLoading(false);
      }
    }, [activeCourseId, bankFilter, showToast]);

    useEffect(() => { fetchQuizzes(); }, [fetchQuizzes]);
    useEffect(() => { if (tab === 'bank') fetchBank(); }, [tab, fetchBank]);

    /* ── Filtered lists ── */
    const filteredQuizzes = quizzes.filter(x =>
      !q || x.title.toLowerCase().includes(q.toLowerCase())
    );
    const filteredBank = bankList.filter(x =>
      !q || x.questionText.toLowerCase().includes(q.toLowerCase())
    );

    /* ── Create quiz ── */
    const handleCreateQuiz = useCallback(async () => {
      if (!qzTitle.trim()) { showToast('Vui lòng nhập tên quiz', 'error'); return; }
      if (!activeCourseId) { showToast('Chưa chọn khóa học', 'error'); return; }
      setSubmitting(true);
      try {
        await createQuiz(activeCourseId, {
          title: qzTitle.trim(),
          quizType: qzType,
          durationMinutes: Number(qzDuration),
          maxAttempts: Number(qzMax),
          passScore: Number(qzPass),
          proctoringEnabled: qzProctoring,
          endDate: qzEndDate ? new Date(qzEndDate).toISOString() : undefined,
        });
        setCreateQuizOpen(false);
        showToast('Tạo quiz thành công');
        fetchQuizzes();
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setSubmitting(false);
      }
    }, [activeCourseId, qzTitle, qzType, qzDuration, qzMax, qzPass, qzProctoring, qzEndDate, fetchQuizzes, showToast]);

    /* ── Publish / Archive quiz ── */
    const handlePublish = useCallback(async (quiz) => {
      try {
        if (quiz.status === 'DRAFT') {
          await publishQuiz(activeCourseId, quiz.id);
          showToast(`Đã xuất bản "${quiz.title}"`);
        } else if (quiz.status === 'PUBLISHED') {
          await archiveQuiz(activeCourseId, quiz.id);
          showToast(`Đã lưu trữ "${quiz.title}"`);
        }
        fetchQuizzes();
      } catch (err) {
        showToast(extractError(err), 'error');
      }
    }, [activeCourseId, fetchQuizzes, showToast]);

    /* ── Delete quiz ── */
    const handleDeleteQuiz = useCallback(async (quiz) => {
      if (!confirm(`Xóa quiz "${quiz.title}"?`)) return;
      try {
        await deleteQuiz(activeCourseId, quiz.id);
        showToast('Đã xóa quiz');
        fetchQuizzes();
      } catch (err) {
        showToast(extractError(err), 'error');
      }
    }, [activeCourseId, fetchQuizzes, showToast]);

    /* ── Save bank question ── */
    const resetBankForm = () => {
      setBqText(''); setBqDiff('EASY'); setBqType('SINGLE_CHOICE');
      setBqTag(''); setBqPoints(1);
      setBqOpts([
        { optionText: '', isCorrect: true },
        { optionText: '', isCorrect: false },
        { optionText: '', isCorrect: false },
        { optionText: '', isCorrect: false },
      ]);
    };

    const handleSaveBankQuestion = useCallback(async () => {
      if (!bqText.trim()) { showToast('Vui lòng nhập nội dung câu hỏi', 'error'); return; }
      const filledOpts = bqOpts.filter(o => o.optionText.trim());
      if (filledOpts.length < 2) { showToast('Cần ít nhất 2 đáp án', 'error'); return; }
      if (!filledOpts.some(o => o.isCorrect)) { showToast('Cần chọn ít nhất 1 đáp án đúng', 'error'); return; }
      setSubmitting(true);
      try {
        const payload = {
          questionText: bqText.trim(),
          questionType: bqType,
          difficulty: bqDiff,
          subjectTag: bqTag.trim() || undefined,
          points: Number(bqPoints),
          options: filledOpts.map((o, i) => ({ optionText: o.optionText.trim(), isCorrect: o.isCorrect, orderIndex: i })),
        };
        if (editBankItem) {
          await updateBankQuestion(activeCourseId, editBankItem.id, payload);
          showToast('Đã cập nhật câu hỏi');
        } else {
          await createBankQuestion(activeCourseId, payload);
          showToast('Đã thêm câu hỏi vào ngân hàng');
        }
        setAddBankOpen(false);
        setEditBankItem(null);
        resetBankForm();
        fetchBank();
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setSubmitting(false);
      }
    }, [activeCourseId, bqText, bqType, bqDiff, bqTag, bqPoints, bqOpts, editBankItem, fetchBank, showToast]);

    /* ── Toggle bank question status ── */
    const handleToggleStatus = useCallback(async (q) => {
      try {
        await toggleBankQuestionStatus(activeCourseId, q.id);
        showToast(q.status === 'ACTIVE' ? 'Đã vô hiệu hóa câu hỏi' : 'Đã kích hoạt câu hỏi');
        fetchBank();
      } catch (err) {
        showToast(extractError(err), 'error');
      }
    }, [activeCourseId, fetchBank, showToast]);

    /* ── Delete bank question ── */
    const handleDeleteBank = useCallback(async (item) => {
      if (!confirm('Xóa câu hỏi này?')) return;
      try {
        await deleteBankQuestion(activeCourseId, item.id);
        showToast('Đã xóa câu hỏi');
        fetchBank();
      } catch (err) {
        showToast(extractError(err), 'error');
      }
    }, [activeCourseId, fetchBank, showToast]);

    /* ── Import bank ── */
    const handleImportPreview = useCallback(async () => {
      if (!importFile) { showToast('Chọn file CSV trước', 'error'); return; }
      setSubmitting(true);
      try {
        const preview = await previewBankImport(activeCourseId, importFile);
        setImportPreview(preview);
        setImportStep('preview');
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setSubmitting(false);
      }
    }, [activeCourseId, importFile, showToast]);

    const handleImportConfirm = useCallback(async () => {
      setSubmitting(true);
      try {
        const result = await confirmBankImport(activeCourseId, importPreview.token);
        showToast(`Import thành công ${result.successCount} câu hỏi`);
        setImportOpen(false);
        setImportStep('pick');
        setImportFile(null);
        setImportPreview(null);
        fetchBank();
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setSubmitting(false);
      }
    }, [activeCourseId, importPreview, fetchBank, showToast]);

    /* ─── Render ─────────────────────────────────────────────── */
    return (
      <div className="page fade-in">
        {/* Toast */}
        {toast && (
          <div style={{ position: 'fixed', top: 20, right: 24, zIndex: 9999, minWidth: 280,
            background: toast.type === 'error' ? 'var(--error)' : '#10b981',
            color: '#fff', borderRadius: 11, padding: '13px 18px', fontWeight: 600, fontSize: 14,
            boxShadow: '0 4px 24px rgba(0,0,0,.18)' }}>
            {toast.msg}
          </div>
        )}

        <div className="page-head between">
          <div>
            <h1 className="t-h1">Bài tập & Trắc nghiệm</h1>
            <div className="row gap-8" style={{ marginTop: 6 }}>
              <span className="muted" style={{ fontSize: 13 }}>Khóa học:</span>
              <Select
                value={selectedCourseId || ''}
                onChange={v => { setSelectedCourseId(v); setQuizzes([]); setBankList([]); }}
                options={courses.map(c => ({ v: c.id, label: c.title }))}
                style={{ width: 280, flex: 'none' }}
              />
            </div>
          </div>
          <div className="row gap-10">
            {tab === 'quiz' && (
              <button className="btn btn-primary" onClick={() => {
                setQzTitle(''); setQzType('STATIC'); setQzDuration(45);
                setQzMax(3); setQzPass(50); setQzProctoring(false); setQzEndDate('');
                setCreateQuizOpen(true);
              }}>
                <Ic n="plus" size={17} />Tạo quiz
              </button>
            )}
            {tab === 'bank' && (
              <div className="row gap-8">
                <button className="btn btn-ghost" onClick={() => exportBankQuestions(activeCourseId, 'xlsx')}>
                  <Ic n="download" size={16} />Xuất Excel
                </button>
                <button className="btn btn-ghost" onClick={() => { setImportStep('pick'); setImportFile(null); setImportPreview(null); setImportOpen(true); }}>
                  <Ic n="upload" size={16} />Import CSV
                </button>
                <button className="btn btn-primary" onClick={() => {
                  resetBankForm(); setEditBankItem(null); setAddBankOpen(true);
                }}>
                  <Ic n="plus" size={17} />Thêm câu hỏi
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="toolbar">
          <Tabs
            items={[
              { v: 'quiz', label: 'Đề trắc nghiệm', count: quizzes.length },
              { v: 'bank', label: 'Ngân hàng câu hỏi', count: bankList.length },
            ]}
            value={tab}
            onChange={(v) => { setTab(v); setQ(''); }}
          />
          <div className="grow" />
          {tab === 'bank' && (
            <Select
              value={bankFilter}
              onChange={setBankFilter}
              options={[
                { v: 'ALL', label: 'Tất cả độ khó' },
                { v: 'EASY', label: 'Dễ' },
                { v: 'MEDIUM', label: 'Trung bình' },
                { v: 'HARD', label: 'Khó' },
              ]}
              style={{ width: 180, flex: 'none' }}
            />
          )}
          <Search placeholder="Tìm theo tên..." value={q} onChange={setQ} style={{ width: 240, flex: 'none' }} />
        </div>

        {/* ── QUIZ TAB ── */}
        {tab === 'quiz' && (
          <Section pad={false}>
            {quizLoading ? (
              <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>Đang tải...</div>
            ) : filteredQuizzes.length === 0 ? (
              <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>
                <Ic n="clipboard" size={36} style={{ marginBottom: 10, opacity: 0.3 }} />
                <p>Chưa có quiz nào. Nhấn "Tạo quiz" để bắt đầu.</p>
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>Tên quiz</th>
                      <th>Loại</th>
                      <th>Thời gian</th>
                      <th>Số câu</th>
                      <th>Giám sát</th>
                      <th>Hạn</th>
                      <th>Trạng thái</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredQuizzes.map(quiz => (
                      <tr key={quiz.id}>
                        <td>
                          <div className="row gap-10">
                            <div className="stat-ic" style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--surface-3)', color: 'var(--text-2)' }}>
                              <Ic n="clipboard" size={17} />
                            </div>
                            <div>
                              <b style={{ fontSize: 13.5, display: 'block', maxWidth: 220 }} className="truncate">{quiz.title}</b>
                              <span className="t-xs muted">{quiz.questionCount} câu hỏi</span>
                            </div>
                          </div>
                        </td>
                        <td><span className="chip chip-neutral">{TYPE_LABEL[quiz.quizType] || quiz.quizType}</span></td>
                        <td className="muted">{quiz.durationMinutes} phút</td>
                        <td><b>{quiz.questionCount}</b></td>
                        <td>
                          {quiz.proctoringEnabled
                            ? <span className="chip chip-error"><Ic n="shield" size={12} />Bật</span>
                            : <span className="chip chip-neutral">Tắt</span>}
                        </td>
                        <td className="muted">{formatDate(quiz.endDate)}</td>
                        <td>
                          <span className={`chip chip-${STATUS_CHIP[quiz.status] || 'neutral'}`}>
                            {STATUS_LABEL[quiz.status] || quiz.status}
                          </span>
                        </td>
                        <td>
                          <div className="row gap-6">
                            <button
                              className="icon-btn" style={{ width: 34, height: 34 }}
                              title="Xem thống kê"
                              onClick={() => { setStatsQuiz(quiz); setStatsOpen(true); }}
                            >
                              <Ic n="bar_chart" size={16} />
                            </button>
                            {quiz.status !== 'ARCHIVED' && (
                              <button
                                className={`btn btn-sm ${quiz.status === 'DRAFT' ? 'btn-success' : 'btn-ghost'}`}
                                style={{ fontSize: 12, padding: '4px 10px' }}
                                onClick={() => handlePublish(quiz)}
                              >
                                {quiz.status === 'DRAFT' ? 'Xuất bản' : 'Lưu trữ'}
                              </button>
                            )}
                            <button className="icon-btn" style={{ width: 34, height: 34, color: 'var(--error)' }}
                              onClick={() => handleDeleteQuiz(quiz)}>
                              <Ic n="x" size={16} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Section>
        )}

        {/* ── BANK TAB ── */}
        {tab === 'bank' && (
          <Section pad={false}>
            {bankLoading ? (
              <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>Đang tải...</div>
            ) : filteredBank.length === 0 ? (
              <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>
                <Ic n="layers" size={36} style={{ marginBottom: 10, opacity: 0.3 }} />
                <p>Ngân hàng câu hỏi trống. Thêm câu hỏi hoặc import CSV.</p>
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="tbl">
                  <thead>
                    <tr><th>Câu hỏi</th><th>Độ khó</th><th>Điểm</th><th>Dùng trong</th><th>Trạng thái</th><th></th></tr>
                  </thead>
                  <tbody>
                    {filteredBank.map(item => (
                      <tr key={item.id} style={{ opacity: item.status === 'ARCHIVED' ? 0.55 : 1 }}>
                        <td>
                          <b style={{ fontSize: 13.5, maxWidth: 340, display: 'block' }} className="truncate">
                            {item.questionText}
                          </b>
                          {item.subjectTag && <span className="t-xs muted">{item.subjectTag}</span>}
                        </td>
                        <td>
                          <span className={`chip chip-${DIFF_CHIP[item.difficulty] || 'neutral'}`}>
                            {DIFF_LABEL[item.difficulty] || item.difficulty}
                          </span>
                        </td>
                        <td className="muted">{item.points} điểm</td>
                        <td className="muted">{item.quizUsageCount} quiz</td>
                        <td>
                          <span className={`chip chip-${item.status === 'ACTIVE' ? 'success' : 'neutral'}`}>
                            {item.status === 'ACTIVE' ? 'Hoạt động' : 'Vô hiệu'}
                          </span>
                        </td>
                        <td>
                          <div className="row gap-6">
                            <button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => {
                              setEditBankItem(item);
                              setBqText(item.questionText);
                              setBqDiff(item.difficulty);
                              setBqType(item.questionType);
                              setBqTag(item.subjectTag || '');
                              setBqPoints(item.points);
                              setBqOpts(item.options.length >= 2 ? item.options.map(o => ({
                                optionText: o.optionText, isCorrect: o.isCorrect
                              })) : [
                                { optionText: '', isCorrect: true },
                                { optionText: '', isCorrect: false },
                                { optionText: '', isCorrect: false },
                                { optionText: '', isCorrect: false },
                              ]);
                              setAddBankOpen(true);
                            }}>
                              <Ic n="edit" size={16} />
                            </button>
                            <button className="icon-btn" style={{ width: 34, height: 34 }}
                              title={item.status === 'ACTIVE' ? 'Vô hiệu hóa' : 'Kích hoạt'}
                              onClick={() => handleToggleStatus(item)}>
                              <Ic n={item.status === 'ACTIVE' ? 'eye_off' : 'eye'} size={16} />
                            </button>
                            <button className="icon-btn" style={{ width: 34, height: 34, color: 'var(--error)' }}
                              onClick={() => handleDeleteBank(item)}>
                              <Ic n="x" size={16} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Section>
        )}

        {/* ═══ Modal: Tạo quiz ═══ */}
        <Modal open={createQuizOpen} onClose={() => setCreateQuizOpen(false)} max={560}>
          <ModalHead title="Tạo quiz mới" sub="Bạn có thể thêm câu hỏi và xuất bản sau" icon="clipboard" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => setCreateQuizOpen(false)} />
          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Tên quiz *</label>
              <input className="input" placeholder="VD: Kiểm tra giữa kỳ ReactJS" value={qzTitle} onChange={e => setQzTitle(e.target.value)} />
            </div>
            <div className="grid grid-2" style={{ gap: 12 }}>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Loại quiz</label>
                <Select value={qzType} onChange={setQzType} options={[
                  { v: 'STATIC', label: 'Cố định' },
                  { v: 'SHUFFLED_POOL', label: 'Xáo câu' },
                  { v: 'RANDOM_DRAW', label: 'Ngẫu nhiên (bank)' },
                ]} />
              </div>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Thời gian (phút)</label>
                <input className="input" type="number" min={1} value={qzDuration} onChange={e => setQzDuration(e.target.value)} />
              </div>
            </div>
            <div className="grid grid-2" style={{ gap: 12 }}>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Số lần làm tối đa</label>
                <input className="input" type="number" min={1} value={qzMax} onChange={e => setQzMax(e.target.value)} />
              </div>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Điểm đạt (%)</label>
                <input className="input" type="number" min={0} max={100} value={qzPass} onChange={e => setQzPass(e.target.value)} />
              </div>
            </div>
            <div>
              <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Hạn nộp bài</label>
              <input className="input" type="datetime-local" value={qzEndDate} onChange={e => setQzEndDate(e.target.value)} />
            </div>
            <label className="row gap-10" style={{ padding: '11px 14px', background: 'var(--chip-error-bg)', borderRadius: 10, cursor: 'pointer' }}>
              <input type="checkbox" checked={qzProctoring} onChange={e => setQzProctoring(e.target.checked)} style={{ width: 17, height: 17 }} />
              <div>
                <div style={{ fontWeight: 600, fontSize: 13.5, color: 'var(--chip-error-fg)' }}>Bật chế độ giám sát</div>
                <div className="t-xs" style={{ color: 'var(--chip-error-fg)', opacity: 0.8 }}>Phát hiện chuyển tab khi làm bài</div>
              </div>
            </label>
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => setCreateQuizOpen(false)}>Hủy</button>
            <button className="btn btn-primary" onClick={handleCreateQuiz} disabled={submitting}>
              {submitting ? 'Đang tạo...' : <><Ic n="plus" size={16} />Tạo quiz</>}
            </button>
          </div>
        </Modal>

        {/* ═══ Modal: Thêm / Sửa câu hỏi bank ═══ */}
        <Modal open={addBankOpen} onClose={() => { setAddBankOpen(false); setEditBankItem(null); }} max={640}>
          <ModalHead
            title={editBankItem ? 'Sửa câu hỏi' : 'Thêm câu hỏi vào ngân hàng'}
            sub="Câu hỏi được lưu vào ngân hàng của khóa học này"
            icon="layers" iconBg="#f3edff" iconColor="#7c3aed"
            onClose={() => { setAddBankOpen(false); setEditBankItem(null); }}
          />
          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className="grid grid-2" style={{ gap: 12 }}>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Loại câu hỏi</label>
                <Select value={bqType} onChange={setBqType} options={[
                  { v: 'SINGLE_CHOICE', label: 'Một đáp án' },
                  { v: 'MULTIPLE_CHOICE', label: 'Nhiều đáp án' },
                ]} />
              </div>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Độ khó</label>
                <Select value={bqDiff} onChange={setBqDiff} options={[
                  { v: 'EASY', label: 'Dễ' },
                  { v: 'MEDIUM', label: 'Trung bình' },
                  { v: 'HARD', label: 'Khó' },
                ]} />
              </div>
            </div>
            <div className="grid grid-2" style={{ gap: 12 }}>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Chủ đề (tag)</label>
                <input className="input" placeholder="VD: React Hooks" value={bqTag} onChange={e => setBqTag(e.target.value)} />
              </div>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Điểm</label>
                <input className="input" type="number" min={0.1} step={0.1} value={bqPoints} onChange={e => setBqPoints(e.target.value)} />
              </div>
            </div>
            <div>
              <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Nội dung câu hỏi *</label>
              <textarea className="input" style={{ height: 80, padding: 12, resize: 'vertical' }}
                placeholder="Nhập nội dung câu hỏi..." value={bqText} onChange={e => setBqText(e.target.value)} />
            </div>
            <div>
              <label className="t-label" style={{ display: 'block', marginBottom: 8 }}>
                Đáp án — {bqType === 'MULTIPLE_CHOICE' ? 'chọn nhiều đáp án đúng' : 'chọn 1 đáp án đúng'}
              </label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
                {bqOpts.map((opt, i) => (
                  <div key={i} className="row gap-10" style={{
                    padding: '8px 12px', borderRadius: 10,
                    border: `1.5px solid ${opt.isCorrect ? 'var(--success)' : 'var(--border)'}`,
                    background: opt.isCorrect ? 'var(--chip-success-bg)' : '#fff',
                  }}>
                    <span
                      onClick={() => {
                        const next = bqOpts.map((o, j) => ({
                          ...o,
                          isCorrect: bqType === 'SINGLE_CHOICE' ? j === i : (j === i ? !o.isCorrect : o.isCorrect),
                        }));
                        setBqOpts(next);
                      }}
                      style={{
                        width: 22, height: 22, borderRadius: bqType === 'MULTIPLE_CHOICE' ? 5 : 999,
                        flex: 'none', cursor: 'pointer',
                        border: `2px solid ${opt.isCorrect ? 'var(--success)' : 'var(--border-input)'}`,
                        display: 'grid', placeItems: 'center',
                      }}
                    >
                      {opt.isCorrect && <span style={{
                        width: 11, height: 11,
                        borderRadius: bqType === 'MULTIPLE_CHOICE' ? 3 : 999,
                        background: 'var(--success)',
                      }} />}
                    </span>
                    <span style={{ fontWeight: 700, color: 'var(--text-3)', width: 18 }}>{String.fromCharCode(65 + i)}</span>
                    <input
                      className="input"
                      style={{ height: 36, border: 'none', background: 'transparent', padding: 0, flex: 1 }}
                      placeholder={`Đáp án ${String.fromCharCode(65 + i)}`}
                      value={opt.optionText}
                      onChange={e => setBqOpts(bqOpts.map((o, j) => j === i ? { ...o, optionText: e.target.value } : o))}
                    />
                    {opt.isCorrect && <span className="chip chip-success" style={{ flex: 'none' }}>Đúng</span>}
                  </div>
                ))}
              </div>
            </div>
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => { setAddBankOpen(false); setEditBankItem(null); }}>Hủy</button>
            <button className="btn btn-success" onClick={handleSaveBankQuestion} disabled={submitting}>
              {submitting ? 'Đang lưu...' : <><Ic n="check" size={16} />{editBankItem ? 'Cập nhật' : 'Lưu vào ngân hàng'}</>}
            </button>
          </div>
        </Modal>

        {/* ═══ Modal: Thống kê quiz ═══ */}
        <QuizStatsModal
          open={statsOpen}
          onClose={() => { setStatsOpen(false); setStatsQuiz(null); }}
          courseId={activeCourseId}
          quiz={statsQuiz}
        />

        {/* ═══ Modal: Import CSV ═══ */}
        <Modal open={importOpen} onClose={() => setImportOpen(false)} max={560}>
          <ModalHead title="Import câu hỏi từ CSV" sub="File CSV theo mẫu chuẩn của hệ thống" icon="upload" iconBg="#f0fdf4" iconColor="#059669" onClose={() => setImportOpen(false)} />
          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {importStep === 'pick' && (
              <>
                <div
                  style={{ border: '2px dashed var(--border-strong)', borderRadius: 12, padding: 28, textAlign: 'center', cursor: 'pointer', color: 'var(--text-2)' }}
                  onClick={() => document.getElementById('bank-csv-input').click()}
                >
                  <Ic n="upload" size={28} style={{ marginBottom: 8 }} />
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>
                    {importFile ? importFile.name : 'Nhấn để chọn file CSV'}
                  </div>
                  <div className="t-sm muted">Định dạng: questionText, questionType, difficulty, optionA, optionB, optionC, optionD, correctOptions</div>
                  <input id="bank-csv-input" type="file" accept=".csv" style={{ display: 'none' }}
                    onChange={e => setImportFile(e.target.files[0])} />
                </div>
              </>
            )}
            {importStep === 'preview' && importPreview && (
              <div>
                <div className="grid grid-2" style={{ gap: 10, marginBottom: 14 }}>
                  {[
                    { l: 'Tổng dòng', v: importPreview.totalRows },
                    { l: 'Hợp lệ', v: importPreview.validCount, c: 'success' },
                    { l: 'Lỗi định dạng', v: importPreview.formatErrorCount, c: 'error' },
                    { l: 'Trùng lặp', v: importPreview.duplicateInFileCount + importPreview.duplicateInDbCount, c: 'warning' },
                  ].map((s, i) => (
                    <div key={i} style={{ padding: '10px 14px', borderRadius: 10, background: 'var(--surface-2)', textAlign: 'center' }}>
                      <div style={{ fontSize: 22, fontWeight: 800, color: s.c ? `var(--${s.c})` : 'var(--text)' }}>{s.v}</div>
                      <div className="t-sm muted">{s.l}</div>
                    </div>
                  ))}
                </div>
                <div style={{ maxHeight: 200, overflowY: 'auto', fontSize: 12 }}>
                  {importPreview.rows.filter(r => r.status !== 'VALID').slice(0, 20).map((r, i) => (
                    <div key={i} style={{ padding: '6px 10px', borderRadius: 7, marginBottom: 4, background: 'var(--chip-error-bg)', color: 'var(--chip-error-fg)' }}>
                      Dòng {r.rowNumber}: {r.errors.join(', ')}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => setImportOpen(false)}>Hủy</button>
            {importStep === 'pick' && (
              <button className="btn btn-primary" onClick={handleImportPreview} disabled={!importFile || submitting}>
                {submitting ? 'Đang xử lý...' : 'Kiểm tra file'}
              </button>
            )}
            {importStep === 'preview' && (
              <>
                <button className="btn btn-ghost" onClick={() => setImportStep('pick')}>Chọn lại</button>
                <button className="btn btn-success" onClick={handleImportConfirm} disabled={submitting || importPreview?.validCount === 0}>
                  {submitting ? 'Đang import...' : `Import ${importPreview?.validCount || 0} câu hợp lệ`}
                </button>
              </>
            )}
          </div>
        </Modal>
      </div>
    );
  }

  /* ═══ QuizStatsModal ═══════════════════════════════════════ */
  function QuizStatsModal({ open, onClose, courseId, quiz }) {
    const [stats, setStats] = useState(null);
    const [attempts, setAttempts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [view, setView] = useState('overview'); // overview | attempts

    useEffect(() => {
      if (!open || !courseId || !quiz) return;
      setLoading(true);
      setError('');
      setStats(null);
      setAttempts([]);
      Promise.all([
        getQuizStats(courseId, quiz.id),
        getAllAttemptsForQuiz(courseId, quiz.id),
      ])
        .then(([s, a]) => { setStats(s); setAttempts(a); })
        .catch(err => setError(extractError(err)))
        .finally(() => setLoading(false));
    }, [open, courseId, quiz]);

    function fmtTime(secs) {
      if (!secs) return '—';
      const m = Math.floor(secs / 60), s = secs % 60;
      return `${m}p${String(s).padStart(2,'0')}s`;
    }

    function fmtDate(d) {
      if (!d) return '—';
      return new Date(d).toLocaleString('vi-VN', { day:'2-digit', month:'2-digit', year:'numeric', hour:'2-digit', minute:'2-digit' });
    }

    const DIFF_COLOR = { EASY: 'success', MEDIUM: 'warning', HARD: 'error' };
    const DIFF_LBL   = { EASY: 'Dễ', MEDIUM: 'TB', HARD: 'Khó' };

    return (
      <Modal open={open} onClose={onClose} max={820}>
        <ModalHead
          title={`Thống kê: ${quiz?.title || ''}`}
          sub="Tổng hợp kết quả và chi tiết từng lần thi"
          icon="bar_chart" iconBg="#eaf1ff" iconColor="#2563eb"
          onClose={onClose}
        />

        {/* Sub-tabs */}
        <div style={{ padding: '0 24px', borderBottom: '1px solid var(--border)' }}>
          <div className="row gap-0">
            {[['overview','Tổng quan'], ['attempts','Tất cả lần thi']].map(([v, l]) => (
              <button
                key={v}
                onClick={() => setView(v)}
                style={{
                  padding: '10px 18px', border: 'none', background: 'none',
                  cursor: 'pointer', fontSize: 13, fontWeight: 600,
                  color: view === v ? 'var(--accent)' : 'var(--text-2)',
                  borderBottom: view === v ? '2px solid var(--accent)' : '2px solid transparent',
                  marginBottom: -1,
                }}
              >
                {l}
              </button>
            ))}
          </div>
        </div>

        <div className="modal-body" style={{ minHeight: 320 }}>
          {loading && (
            <div style={{ padding: 48, textAlign: 'center', color: 'var(--text-2)' }}>
              Đang tải thống kê...
            </div>
          )}
          {error && (
            <div style={{ padding: 32, textAlign: 'center', color: 'var(--error)' }}>{error}</div>
          )}

          {/* ── Overview ── */}
          {!loading && !error && stats && view === 'overview' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>

              {/* KPI row */}
              <div className="grid" style={{ gridTemplateColumns: 'repeat(4,1fr)', gap: 12 }}>
                {[
                  { l: 'Tổng lần thi', v: stats.totalAttempts, ic: 'clipboard', bg: '#eaf1ff', c: '#2563eb' },
                  { l: 'Học viên', v: stats.uniqueStudents, ic: 'users', bg: '#f3edff', c: '#7c3aed' },
                  { l: 'Tỉ lệ đạt', v: `${Number(stats.passRate ?? 0).toFixed(1)}%`, ic: 'target', bg: '#e7f8f0', c: '#059669' },
                  { l: 'TB thời gian', v: fmtTime(Math.round(stats.avgTimeSpentSeconds)), ic: 'clock', bg: '#fef5e6', c: '#d97706' },
                ].map((k, i) => (
                  <div key={i} style={{ padding: '14px 16px', background: 'var(--surface-2)', borderRadius: 12 }}>
                    <div className="row gap-8" style={{ marginBottom: 8 }}>
                      <div style={{ width: 32, height: 32, borderRadius: 9, background: k.bg, color: k.c, display: 'grid', placeItems: 'center', flex: 'none' }}>
                        <Ic n={k.ic} size={15} />
                      </div>
                      <span className="t-xs muted">{k.l}</span>
                    </div>
                    <div style={{ fontSize: 24, fontWeight: 800, color: 'var(--text)' }}>{k.v}</div>
                  </div>
                ))}
              </div>

              {/* Score stats */}
              <div style={{ padding: '16px 18px', background: 'var(--surface-2)', borderRadius: 12 }}>
                <div className="t-label" style={{ marginBottom: 12 }}>Điểm số</div>
                <div className="grid grid-2" style={{ gap: 16 }}>
                  <div>
                    <div className="muted t-sm" style={{ marginBottom: 4 }}>Điểm trung bình</div>
                    <div style={{ fontSize: 28, fontWeight: 800, color: 'var(--text)' }}>
                      {Number(stats.avgScore ?? 0).toFixed(2)}
                    </div>
                  </div>
                  <div>
                    <div className="muted t-sm" style={{ marginBottom: 4 }}>TB % đúng</div>
                    <div style={{ fontSize: 28, fontWeight: 800, color: 'var(--text)' }}>
                      {Number(stats.avgScorePercentage ?? 0).toFixed(1)}%
                    </div>
                  </div>
                </div>

                {/* Pass rate bar */}
                <div style={{ marginTop: 16 }}>
                  <div className="between" style={{ marginBottom: 6 }}>
                    <span className="t-sm muted">Tỉ lệ đạt</span>
                    <span style={{ fontWeight: 700, color: 'var(--success)' }}>
                      {stats.passCount} / {stats.totalAttempts} lần đạt
                    </span>
                  </div>
                  <div style={{ height: 10, background: 'var(--border)', borderRadius: 999, overflow: 'hidden' }}>
                    <div style={{
                      height: '100%', background: 'var(--success)', borderRadius: 999,
                      width: `${stats.totalAttempts > 0 ? (stats.passCount / stats.totalAttempts) * 100 : 0}%`,
                    }} />
                  </div>
                </div>
              </div>

              {/* Per-question stats */}
              {stats.questionStats?.length > 0 && (
                <div>
                  <div className="t-label" style={{ marginBottom: 10 }}>Tỉ lệ đúng từng câu</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {stats.questionStats.map((qs, i) => {
                      const rate = Number(qs.correctRate ?? 0);
                      const barColor = rate >= 70 ? 'var(--success)' : rate >= 40 ? 'var(--warning)' : 'var(--error)';
                      return (
                        <div key={qs.questionId} style={{ padding: '10px 14px', background: 'var(--surface-2)', borderRadius: 10 }}>
                          <div className="between" style={{ marginBottom: 6 }}>
                            <div className="row gap-8" style={{ minWidth: 0, flex: 1 }}>
                              <span className="muted" style={{ fontSize: 12, flex: 'none' }}>C{i + 1}</span>
                              <span style={{ fontSize: 13, fontWeight: 600 }} className="truncate">{qs.questionText}</span>
                              <span className={`chip chip-${DIFF_COLOR[qs.difficulty] || 'neutral'}`} style={{ fontSize: 10, flex: 'none' }}>
                                {DIFF_LBL[qs.difficulty] || qs.difficulty}
                              </span>
                            </div>
                            <span style={{ fontWeight: 700, fontSize: 14, color: barColor, flex: 'none', marginLeft: 12 }}>
                              {rate.toFixed(1)}%
                            </span>
                          </div>
                          <div style={{ height: 6, background: 'var(--border)', borderRadius: 999, overflow: 'hidden' }}>
                            <div style={{ height: '100%', background: barColor, borderRadius: 999, width: `${rate}%`, transition: 'width .4s' }} />
                          </div>
                          <div className="muted" style={{ fontSize: 11, marginTop: 4 }}>
                            {qs.correctCount} / {qs.totalAnswers} trả lời đúng
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ── Attempts table ── */}
          {!loading && !error && view === 'attempts' && (
            <div>
              {attempts.length === 0 ? (
                <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>
                  <Ic n="clipboard" size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
                  <p>Chưa có lần thi nào.</p>
                </div>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table className="tbl">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>Lần thi</th>
                        <th>Điểm</th>
                        <th>%</th>
                        <th>Đúng</th>
                        <th>Thời gian</th>
                        <th>Nộp lúc</th>
                        <th>Kết quả</th>
                      </tr>
                    </thead>
                    <tbody>
                      {attempts.map((a, i) => (
                        <tr key={a.attemptId}>
                          <td className="muted">{i + 1}</td>
                          <td>
                            <div className="row gap-6">
                              <b>Lần {a.attemptNumber}</b>
                              {a.autoSubmitted && <span className="chip chip-warning" style={{ fontSize: 10 }}>Tự nộp</span>}
                              {a.violationCount > 0 && (
                                <span className="chip chip-error" style={{ fontSize: 10 }}>
                                  <Ic n="shield" size={10} />{a.violationCount}
                                </span>
                              )}
                            </div>
                          </td>
                          <td style={{ fontWeight: 700 }}>{Number(a.score ?? 0).toFixed(2)}</td>
                          <td style={{ fontWeight: 700, color: a.isPassed ? 'var(--success)' : 'var(--error)' }}>
                            {Number(a.scorePercentage ?? 0).toFixed(1)}%
                          </td>
                          <td className="muted">{a.correctCount}/{a.correctCount + a.incorrectCount + a.unansweredCount}</td>
                          <td className="muted">{fmtTime(a.timeSpentSeconds)}</td>
                          <td className="muted" style={{ fontSize: 12 }}>{fmtDate(a.submittedAt)}</td>
                          <td>
                            <span className={`chip chip-${a.isPassed ? 'success' : a.status === 'IN_PROGRESS' ? 'warning' : 'error'}`}>
                              {a.isPassed ? 'Đạt' : a.status === 'IN_PROGRESS' ? 'Đang thi' : 'Chưa đạt'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
        </div>
      </Modal>
    );
  }

  Object.assign(window, { InsAssess });
})();
