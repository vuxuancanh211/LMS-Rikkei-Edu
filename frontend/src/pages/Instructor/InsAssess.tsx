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
    aiGenerateQuestions, aiSaveQuestions,
  } = window.__quizService;

  const { getMyCourses } = window.__courseService;

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
    const [courses, setCourses] = useState([]);
    const [coursesLoading, setCoursesLoading] = useState(true);
    const [selectedCourseId, setSelectedCourseId] = useState(courseId || null);
    const activeCourseId = selectedCourseId;

    useEffect(() => {
      getMyCourses()
        .then(page => {
          const list = page.content || [];
          setCourses(list);
          if (!selectedCourseId && list.length > 0) {
            setSelectedCourseId(list[0].id);
          }
        })
        .catch(() => {})
        .finally(() => setCoursesLoading(false));
    }, []);

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
    const [bankStatusFilter, setBankStatusFilter] = useState('ALL');

    // Modal states
    const [createQuizOpen, setCreateQuizOpen] = useState(false);
    const [addBankOpen, setAddBankOpen] = useState(false);
    const [editBankItem, setEditBankItem] = useState(null);
    const [importOpen, setImportOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // Stats modal state
    const [statsQuiz, setStatsQuiz] = useState(null);
    const [statsOpen, setStatsOpen] = useState(false);

    // AI generate modal state
    const [aiOpen, setAiOpen] = useState(false);

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
        const params = {};
        if (bankFilter !== 'ALL') params.difficulty = bankFilter;
        if (bankStatusFilter !== 'ALL') params.status = bankStatusFilter;
        const data = await listBankQuestions(activeCourseId, params);
        setBankList(data);
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setBankLoading(false);
      }
    }, [activeCourseId, bankFilter, bankStatusFilter, showToast]);

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

    /* ── Toggle bank question status (optimistic — no reload flash) ── */
    const handleToggleStatus = useCallback(async (item) => {
      const newStatus = item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      setBankList(prev => prev.map(x => x.id === item.id ? { ...x, status: newStatus } : x));
      try {
        await toggleBankQuestionStatus(activeCourseId, item.id);
        showToast(newStatus === 'INACTIVE' ? 'Đã vô hiệu hóa câu hỏi' : 'Đã kích hoạt câu hỏi');
      } catch (err) {
        setBankList(prev => prev.map(x => x.id === item.id ? { ...x, status: item.status } : x));
        showToast(extractError(err), 'error');
      }
    }, [activeCourseId, showToast]);

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
              {coursesLoading ? (
                <span className="muted" style={{ fontSize: 13 }}>Đang tải khóa học...</span>
              ) : courses.length === 0 ? (
                <span className="muted" style={{ fontSize: 13 }}>Bạn chưa có khóa học nào</span>
              ) : (
                <Select
                  value={selectedCourseId || ''}
                  onChange={v => { setSelectedCourseId(v); setQuizzes([]); setBankList([]); setBankFilter('ALL'); setBankStatusFilter('ALL'); }}
                  options={courses.map(c => ({ v: c.id, label: c.title }))}
                  style={{ width: 280, flex: 'none' }}
                />
              )}
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
                <button className="btn btn-ghost" style={{ color: 'var(--accent)', borderColor: 'var(--accent)' }}
                  onClick={() => setAiOpen(true)}>
                  <Ic n="sparkles" size={17} />Tạo bằng AI
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
            <>
              <Select
                value={bankStatusFilter}
                onChange={setBankStatusFilter}
                options={[
                  { v: 'ALL', label: 'Tất cả trạng thái' },
                  { v: 'ACTIVE', label: 'Hoạt động' },
                  { v: 'INACTIVE', label: 'Vô hiệu' },
                ]}
                style={{ width: 170, flex: 'none' }}
              />
              <Select
                value={bankFilter}
                onChange={setBankFilter}
                options={[
                  { v: 'ALL', label: 'Tất cả độ khó' },
                  { v: 'EASY', label: 'Dễ' },
                  { v: 'MEDIUM', label: 'Trung bình' },
                  { v: 'HARD', label: 'Khó' },
                ]}
                style={{ width: 160, flex: 'none' }}
              />
            </>
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

        {/* ═══ Modal: AI tạo câu hỏi ═══ */}
        <AiGenerateModal
          open={aiOpen}
          onClose={() => setAiOpen(false)}
          courseId={activeCourseId}
          onSaved={() => { setAiOpen(false); fetchBank(); }}
        />

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

  /* ═══ AiGenerateModal ══════════════════════════════════════ */
  function AiGenerateModal({ open, onClose, courseId, onSaved }) {
    const STEPS = { form: 'form', loading: 'loading', preview: 'preview', saving: 'saving' };
    const [step, setStep] = useState(STEPS.form);
    const [error, setError] = useState('');

    // Form state
    const [topic, setTopic] = useState('');
    const [qType, setQType] = useState('SINGLE_CHOICE');
    const [diff, setDiff] = useState('MEDIUM');
    const [tag, setTag] = useState('');
    const [count, setCount] = useState(5);
    const [threshold, setThreshold] = useState(0.88);

    // Preview state
    const [result, setResult] = useState(null);
    const [selected, setSelected] = useState([]);

    // Inline edit state
    const [editingIdx, setEditingIdx] = useState(null);
    const [draft, setDraft] = useState(null); // { questionText, questionType, difficulty, options: [{text, correct}] }

    function reset() {
      setStep(STEPS.form); setError(''); setResult(null); setSelected([]);
      setTopic(''); setTag(''); setCount(5); setThreshold(0.88);
      setQType('SINGLE_CHOICE'); setDiff('MEDIUM');
      setEditingIdx(null); setDraft(null);
    }

    function openEdit(i) {
      const q = result.questions[i];
      setDraft({
        questionText: q.questionText,
        questionType: q.questionType,
        difficulty: q.difficulty,
        options: q.options.map(o => ({ text: o.text, correct: o.correct })),
      });
      setEditingIdx(i);
    }

    function cancelEdit() { setEditingIdx(null); setDraft(null); }

    function saveEdit(i) {
      if (!draft.questionText.trim()) return;
      const updated = { ...result.questions[i], ...draft };
      const newQuestions = result.questions.map((q, idx) => idx === i ? updated : q);
      setResult({ ...result, questions: newQuestions });
      setEditingIdx(null);
      setDraft(null);
      // Khi edit xong → tự động chọn câu đó
      setSelected(prev => prev.map((v, idx) => idx === i ? true : v));
    }

    function draftToggleCorrect(j) {
      const isSingle = draft.questionType === 'SINGLE_CHOICE';
      setDraft(prev => ({
        ...prev,
        options: prev.options.map((o, k) => ({
          ...o,
          correct: isSingle ? k === j : (k === j ? !o.correct : o.correct),
        })),
      }));
    }

    async function handleGenerate() {
      if (!topic.trim()) { setError('Vui lòng nhập chủ đề.'); return; }
      setError(''); setStep(STEPS.loading);
      try {
        const data = await aiGenerateQuestions(courseId, {
          topic, questionType: qType, difficulty: diff,
          subjectTag: tag || undefined, count, duplicateThreshold: threshold,
        });
        setResult(data);
        setSelected(data.questions.map(q => !q.duplicate));
        setStep(STEPS.preview);
      } catch (err) {
        setError(err?.response?.data?.message || err?.message || 'Lỗi kết nối dịch vụ AI.');
        setStep(STEPS.form);
      }
    }

    async function handleSave() {
      const toSave = result.questions.filter((_, i) => selected[i]);
      if (toSave.length === 0) { setError('Chưa chọn câu nào để lưu.'); return; }
      setStep(STEPS.saving);
      try {
        const payload = toSave.map(q => ({
          questionText: q.questionText,
          questionType: q.questionType,
          difficulty: q.difficulty,
          subjectTag: tag || null,
          points: 1,
          options: q.options.map(o => ({ optionText: o.text, isCorrect: o.correct, orderIndex: 0 })),
        }));
        await aiSaveQuestions(courseId, payload);
        onSaved();
        reset();
      } catch (err) {
        setError(err?.response?.data?.message || 'Lưu thất bại. Vui lòng thử lại.');
        setStep(STEPS.preview);
      }
    }

    function toggleSelect(i) {
      setSelected(prev => prev.map((v, idx) => idx === i ? !v : v));
    }

    if (!open) return null;

    const DIFF_LBL = { EASY: 'Dễ', MEDIUM: 'Trung bình', HARD: 'Khó' };
    const DIFF_OPTS = [{ v: 'EASY', label: 'Dễ' }, { v: 'MEDIUM', label: 'Trung bình' }, { v: 'HARD', label: 'Khó' }];
    const TYPE_LBL  = { SINGLE_CHOICE: 'Một đáp án', MULTIPLE_CHOICE: 'Nhiều đáp án' };
    const TYPE_OPTS = [{ v: 'SINGLE_CHOICE', label: 'Một đáp án đúng' }, { v: 'MULTIPLE_CHOICE', label: 'Nhiều đáp án đúng' }];
    const selectedCount = selected.filter(Boolean).length;

    return (
      <Modal open={open} onClose={() => { if (step !== STEPS.loading && step !== STEPS.saving) { reset(); onClose(); } }} max={760}>
        <ModalHead
          title="Tạo câu hỏi bằng AI"
          sub={step === STEPS.preview
            ? `${result?.totalGenerated} câu sinh được · ${result?.duplicateCount} trùng · ${result?.newCount} mới`
            : 'AI sẽ sinh câu hỏi và kiểm tra trùng lặp với ngân hàng hiện có'}
          icon="sparkles" iconBg="#f3edff" iconColor="#7c3aed"
          onClose={() => { if (step !== STEPS.loading && step !== STEPS.saving) { reset(); onClose(); } }}
        />

        {/* ── Step 1: Form ── */}
        {step === STEPS.form && (
          <>
            <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {error && <div style={{ padding: '10px 14px', background: 'var(--chip-error-bg)', color: 'var(--error)', borderRadius: 10, fontSize: 13 }}>{error}</div>}
              <div>
                <div className="t-label" style={{ marginBottom: 6 }}>Chủ đề / Nội dung cần ra đề <span style={{ color: 'var(--error)' }}>*</span></div>
                <textarea className="input" rows={2} style={{ width: '100%', resize: 'vertical', fontFamily: 'inherit' }}
                  placeholder="Ví dụ: Vòng lặp trong Java, Chuẩn hóa cơ sở dữ liệu 3NF, Spring Security JWT..."
                  value={topic} onChange={e => setTopic(e.target.value)} />
              </div>
              <div className="grid grid-2" style={{ gap: 12 }}>
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>Loại câu hỏi</div>
                  <Select value={qType} onChange={setQType} options={TYPE_OPTS} />
                </div>
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>Độ khó</div>
                  <Select value={diff} onChange={setDiff} options={DIFF_OPTS} />
                </div>
              </div>
              <div className="grid grid-2" style={{ gap: 12 }}>
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>Số câu muốn sinh</div>
                  <input className="input" type="number" min={1} max={20} style={{ width: '100%' }} value={count}
                    onChange={e => setCount(Math.max(1, Math.min(20, Number(e.target.value))))} />
                </div>
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>Chuyên đề / Tag (tuỳ chọn)</div>
                  <input className="input" style={{ width: '100%' }} placeholder="VD: OOP, SQL, Security..."
                    value={tag} onChange={e => setTag(e.target.value)} />
                </div>
              </div>
              <div>
                <div className="between" style={{ marginBottom: 4 }}>
                  <div className="t-label">Ngưỡng phát hiện trùng</div>
                  <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent)' }}>{Math.round(threshold * 100)}%</span>
                </div>
                <input type="range" min={0.5} max={1} step={0.01} style={{ width: '100%', accentColor: 'var(--accent)' }}
                  value={threshold} onChange={e => setThreshold(Number(e.target.value))} />
                <div className="between muted" style={{ fontSize: 11, marginTop: 2 }}>
                  <span>Ít nghiêm ngặt hơn</span><span>Chặt hơn (100% = exact match)</span>
                </div>
              </div>
            </div>
            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={() => { reset(); onClose(); }}>Huỷ</button>
              <button className="btn btn-primary" onClick={handleGenerate} disabled={!topic.trim()}>
                <Ic n="sparkles" size={16} />Sinh câu hỏi
              </button>
            </div>
          </>
        )}

        {/* ── Step 2: Loading ── */}
        {step === STEPS.loading && (
          <div className="modal-body" style={{ textAlign: 'center', padding: '48px 24px' }}>
            <div style={{ width: 56, height: 56, borderRadius: 999, background: '#f3edff', display: 'grid', placeItems: 'center', margin: '0 auto 16px', animation: 'spin 1.2s linear infinite' }}>
              <Ic n="sparkles" size={26} style={{ color: '#7c3aed' }} />
            </div>
            <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 6 }}>AI đang sinh câu hỏi...</div>
            <div className="muted t-sm">Đang phân tích chủ đề và kiểm tra trùng lặp với ngân hàng</div>
          </div>
        )}

        {/* ── Step 3: Preview + Edit ── */}
        {step === STEPS.preview && result && (
          <>
            <div className="modal-body" style={{ padding: 0 }}>
              {error && <div style={{ padding: '10px 16px', background: 'var(--chip-error-bg)', color: 'var(--error)', fontSize: 13 }}>{error}</div>}

              {/* Summary bar */}
              <div className="row gap-16" style={{ padding: '12px 20px', background: 'var(--surface-2)', borderBottom: '1px solid var(--border)', flexWrap: 'wrap' }}>
                <span className="row gap-6 muted t-sm"><Ic n="clipboard" size={14} />{result.totalGenerated} câu sinh được</span>
                <span className="row gap-6 t-sm" style={{ color: 'var(--success)' }}><Ic n="check_circle" size={14} />{result.newCount} câu mới</span>
                {result.duplicateCount > 0 && (
                  <span className="row gap-6 t-sm" style={{ color: 'var(--warning)' }}><Ic n="warn" size={14} />{result.duplicateCount} câu trùng</span>
                )}
                <span className="row gap-6 t-sm muted" style={{ marginLeft: 'auto' }}>
                  Đã chọn: <b style={{ color: 'var(--accent)' }}>{selectedCount}</b>
                  <span style={{ color: 'var(--text-3)', marginLeft: 8, fontSize: 11 }}>· Nhấn ✏️ để chỉnh sửa trước khi lưu</span>
                </span>
              </div>

              {/* Question list */}
              <div style={{ maxHeight: 460, overflowY: 'auto' }}>
                {result.questions.map((q, i) => {
                  const isEditing = editingIdx === i;
                  return (
                    <div key={i} style={{
                      borderBottom: '1px solid var(--border)',
                      background: isEditing ? 'var(--surface-2)' : q.duplicate ? 'rgba(245,158,11,.04)' : selected[i] ? 'rgba(37,99,235,.03)' : 'transparent',
                    }}>
                      {/* ── View mode ── */}
                      {!isEditing && (
                        <div style={{ padding: '14px 20px', opacity: q.duplicate && !selected[i] ? 0.65 : 1 }}>
                          <div className="row gap-12" style={{ alignItems: 'flex-start' }}>
                            <input type="checkbox" style={{ marginTop: 3, flex: 'none', accentColor: 'var(--accent)', width: 16, height: 16 }}
                              checked={!!selected[i]} onChange={() => toggleSelect(i)} />
                            <div className="grow" style={{ minWidth: 0 }}>
                              <div className="row gap-8" style={{ marginBottom: 6, flexWrap: 'wrap' }}>
                                <span style={{ fontWeight: 600, fontSize: 13 }}>Câu {i + 1}</span>
                                <span className={`chip chip-${q.difficulty === 'EASY' ? 'success' : q.difficulty === 'MEDIUM' ? 'warning' : 'error'}`} style={{ fontSize: 10 }}>
                                  {DIFF_LBL[q.difficulty] || q.difficulty}
                                </span>
                                <span className="chip chip-neutral" style={{ fontSize: 10 }}>{TYPE_LBL[q.questionType] || q.questionType}</span>
                                {q.duplicate && (
                                  <span className="chip chip-warning" style={{ fontSize: 10 }}>
                                    <Ic n="warn" size={10} />Trùng {Math.round(q.similarityScore * 100)}%
                                  </span>
                                )}
                                <button className="icon-btn" style={{ marginLeft: 'auto', width: 28, height: 28, color: 'var(--accent)' }}
                                  title="Chỉnh sửa câu hỏi" onClick={() => openEdit(i)}>
                                  <Ic n="edit" size={14} />
                                </button>
                              </div>
                              <div style={{ fontSize: 13.5, fontWeight: 500, marginBottom: 10, lineHeight: 1.6 }}>{q.questionText}</div>
                              <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                                {q.options.map((o, j) => (
                                  <div key={j} style={{
                                    padding: '6px 10px', borderRadius: 8, fontSize: 12.5,
                                    background: o.correct ? 'var(--chip-success-bg)' : 'var(--surface-2)',
                                    color: o.correct ? 'var(--success)' : 'var(--text)',
                                    display: 'flex', gap: 8, alignItems: 'flex-start',
                                  }}>
                                    <span style={{ flex: 'none', fontWeight: 700 }}>{String.fromCharCode(65 + j)}.</span>
                                    <span className="grow">{o.text}</span>
                                    {o.correct && <Ic n="check" size={13} style={{ flex: 'none', marginTop: 1 }} />}
                                  </div>
                                ))}
                              </div>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* ── Edit mode ── */}
                      {isEditing && draft && (
                        <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
                          <div className="row gap-8" style={{ marginBottom: 2 }}>
                            <span style={{ fontWeight: 700, fontSize: 13, color: 'var(--accent)' }}>✏️ Chỉnh sửa câu {i + 1}</span>
                          </div>

                          {/* Loại + Độ khó */}
                          <div className="grid grid-2" style={{ gap: 10 }}>
                            <div>
                              <div className="t-label" style={{ marginBottom: 4, fontSize: 11 }}>Loại câu hỏi</div>
                              <Select value={draft.questionType} onChange={v => setDraft(d => ({ ...d, questionType: v }))} options={TYPE_OPTS} />
                            </div>
                            <div>
                              <div className="t-label" style={{ marginBottom: 4, fontSize: 11 }}>Độ khó</div>
                              <Select value={draft.difficulty} onChange={v => setDraft(d => ({ ...d, difficulty: v }))} options={DIFF_OPTS} />
                            </div>
                          </div>

                          {/* Nội dung câu hỏi */}
                          <div>
                            <div className="t-label" style={{ marginBottom: 4, fontSize: 11 }}>Nội dung câu hỏi *</div>
                            <textarea className="input" rows={2}
                              style={{ width: '100%', resize: 'vertical', fontFamily: 'inherit', fontSize: 13 }}
                              value={draft.questionText}
                              onChange={e => setDraft(d => ({ ...d, questionText: e.target.value }))} />
                          </div>

                          {/* Đáp án */}
                          <div>
                            <div className="t-label" style={{ marginBottom: 6, fontSize: 11 }}>
                              Đáp án — {draft.questionType === 'MULTIPLE_CHOICE' ? 'click để bật/tắt đúng' : 'click để chọn đáp án đúng'}
                            </div>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                              {draft.options.map((o, j) => (
                                <div key={j} className="row gap-8" style={{
                                  padding: '8px 12px', borderRadius: 9,
                                  border: `1.5px solid ${o.correct ? 'var(--success)' : 'var(--border)'}`,
                                  background: o.correct ? 'var(--chip-success-bg)' : '#fff',
                                  cursor: 'pointer',
                                }} onClick={() => draftToggleCorrect(j)}>
                                  <span style={{
                                    width: 20, height: 20, borderRadius: draft.questionType === 'MULTIPLE_CHOICE' ? 4 : 999,
                                    flex: 'none', border: `2px solid ${o.correct ? 'var(--success)' : 'var(--border-input)'}`,
                                    display: 'grid', placeItems: 'center',
                                  }}>
                                    {o.correct && <span style={{ width: 10, height: 10, borderRadius: draft.questionType === 'MULTIPLE_CHOICE' ? 2 : 999, background: 'var(--success)' }} />}
                                  </span>
                                  <span style={{ fontWeight: 700, color: 'var(--text-3)', width: 16, fontSize: 12 }}>{String.fromCharCode(65 + j)}</span>
                                  <input className="input" style={{ height: 32, border: 'none', background: 'transparent', padding: 0, flex: 1, fontSize: 12.5 }}
                                    value={o.text}
                                    onClick={e => e.stopPropagation()}
                                    onChange={e => {
                                      const val = e.target.value;
                                      setDraft(d => ({ ...d, options: d.options.map((op, k) => k === j ? { ...op, text: val } : op) }));
                                    }} />
                                  {o.correct && <span className="chip chip-success" style={{ flex: 'none', fontSize: 10 }}>Đúng</span>}
                                </div>
                              ))}
                            </div>
                          </div>

                          {/* Actions */}
                          <div className="row gap-8" style={{ justifyContent: 'flex-end' }}>
                            <button className="btn btn-ghost btn-sm" style={{ fontSize: 12 }} onClick={cancelEdit}>Hủy</button>
                            <button className="btn btn-success btn-sm" style={{ fontSize: 12 }}
                              disabled={!draft.questionText.trim() || !draft.options.some(o => o.correct)}
                              onClick={() => saveEdit(i)}>
                              <Ic n="check" size={13} />Lưu chỉnh sửa
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={() => { cancelEdit(); setStep(STEPS.form); }}>← Thay đổi yêu cầu</button>
              <button className="btn btn-primary" onClick={handleSave} disabled={selectedCount === 0 || editingIdx !== null}>
                <Ic n="check" size={16} />Lưu {selectedCount} câu vào ngân hàng
              </button>
            </div>
          </>
        )}

        {/* ── Step 4: Saving ── */}
        {step === STEPS.saving && (
          <div className="modal-body" style={{ textAlign: 'center', padding: '48px 24px' }}>
            <div className="muted t-sm">Đang lưu {selectedCount} câu hỏi vào ngân hàng...</div>
          </div>
        )}
      </Modal>
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
