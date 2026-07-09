// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Giảng viên · Bài tập & Trắc nghiệm
   ============================================================ */
(function () {
  const { useState, useEffect, useCallback, useRef } = React;
  const Ic = window.Icon;
  const { Avatar, Status, Search, Tabs, Select, Section, Modal, ModalHead, Empty, PageBar } = window;

  const {
    listQuizzes, createQuiz, updateQuiz, publishQuiz, archiveQuiz, deleteQuiz,
    listBankQuestions, searchBankQuestions, createBankQuestion, updateBankQuestion, deleteBankQuestion,
    toggleBankQuestionStatus, previewBankImport, confirmBankImport, exportBankQuestions,
    getQuizStats, getAllAttemptsForQuiz,
    startAiGenerateQuestions, getAiGenerateJobStatus, aiSaveQuestions,
    getQuizDetail, addBankQuestionsToQuiz, addManualQuestionToQuiz,
    removeQuestionFromQuiz, configureRandomDraw,
  } = window.__quizService;

  const { getMyCourses } = window.__courseService;
  const { listAiSources } = window.__aiService;

  const DIFF_LABEL = { EASY: 'Dễ', MEDIUM: 'Trung bình', HARD: 'Khó' };
  const DIFF_CHIP  = { EASY: 'success', MEDIUM: 'warning', HARD: 'error' };
  const STATUS_LABEL = { DRAFT: 'Nháp', PUBLISHED: 'Công khai', ARCHIVED: 'Lưu trữ' };
  const STATUS_CHIP  = { DRAFT: 'neutral', PUBLISHED: 'success', ARCHIVED: 'muted' };
  const TYPE_LABEL = { STATIC: 'Cố định', SHUFFLED_POOL: 'Xáo câu', RANDOM_DRAW: 'Ngẫu nhiên' };

  // Số câu tối đa sinh được trong 1 lần gọi AI — khớp @Max(40) ở AiGenerateQuestionsRequest phía BE
  const AI_MAX_QUESTIONS_PER_GEN = 40;

  // Thứ tự các bước sinh câu hỏi AI — dùng để hiện tiến trình + chấm tròn highlight bước đang chạy
  const GEN_STEP_ORDER = ['RETRIEVING_CONTEXT', 'GENERATING', 'CHECKING_DUPLICATES'];
  const GEN_STEP_LABEL = {
    RETRIEVING_CONTEXT: { title: 'Đang tìm tài liệu liên quan...', sub: 'Tìm đoạn tài liệu AI phù hợp nhất với chủ đề trong khóa học' },
    GENERATING:          { title: 'Đang gọi AI sinh câu hỏi...', sub: 'Có thể mất 30–90 giây tuỳ số câu yêu cầu' },
    CHECKING_DUPLICATES: { title: 'Đang kiểm tra trùng lặp...', sub: 'So sánh với ngân hàng câu hỏi hiện có của khóa học' },
  };

  function formatDate(d) {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  function toDatetimeLocalValue(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function extractError(err) {
    const data = err?.response?.data;
    if (!data) return 'Không thể kết nối tới máy chủ';
    return data.message || 'Lỗi không xác định';
  }

  /* ─── Main Component ──────────────────────────────────────── */
  function InsAssess({ courseId, demo, nav }) {
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

    // Quiz state — phân trang phía server, tránh tải hết quiz lên 1 lượt gây lag giao diện
    const QUIZ_PAGE_SIZE = 10;
    const [quizzes, setQuizzes] = useState([]);
    const [quizLoading, setQuizLoading] = useState(false);
    const [quizPageNum, setQuizPageNum] = useState(1); // 1-based (khớp Pager component)
    const [quizTotalElements, setQuizTotalElements] = useState(0);
    const [quizTotalPages, setQuizTotalPages] = useState(1);
    const [debouncedQuizSearch, setDebouncedQuizSearch] = useState('');

    // Bank state — 2 chế độ: "duyệt" (browse, phân trang phía server, tránh lag) khi không
    // gõ tìm kiếm, và "tìm kiếm" (hybrid search — khớp chữ + tương đồng ngữ nghĩa, không phân
    // trang vì kết quả đã tự giới hạn hợp lý) khi gõ ≥ 3 ký tự.
    const BANK_PAGE_SIZE = 10;
    const [bankList, setBankList] = useState([]);
    const [bankLoading, setBankLoading] = useState(false);
    const [bankFilter, setBankFilter] = useState('ALL');
    const [bankStatusFilter, setBankStatusFilter] = useState('ALL');
    const [bankPageNum, setBankPageNum] = useState(1); // 1-based (khớp Pager component)
    const [bankTotalElements, setBankTotalElements] = useState(0);
    const [bankTotalPages, setBankTotalPages] = useState(1);

    // Chế độ tìm kiếm (hybrid) — khớp chữ xếp trước, tương đồng ngữ nghĩa nối sau
    const [searchHits, setSearchHits] = useState([]);
    const [searchLoading, setSearchLoading] = useState(false);
    const bankSearchSeq = useRef(0);

    // Modal states
    const [createQuizOpen, setCreateQuizOpen] = useState(false);
    const [addBankOpen, setAddBankOpen] = useState(false);
    const [editBankItem, setEditBankItem] = useState(null);
    const [importOpen, setImportOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // Stats modal state
    const [statsQuiz, setStatsQuiz] = useState(null);
    const [statsOpen, setStatsOpen] = useState(false);

    // Quiz detail modal state
    const [detailQuiz, setDetailQuiz] = useState(null);
    const [detailOpen, setDetailOpen] = useState(false);

    // AI generate modal state
    const [aiOpen, setAiOpen] = useState(false);

    // Create / Edit quiz form
    const [editQuizItem, setEditQuizItem] = useState(null);
    const [qzTitle, setQzTitle] = useState('');
    const [qzType, setQzType] = useState('STATIC');
    const [qzDuration, setQzDuration] = useState(45);
    const [qzMax, setQzMax] = useState(3);
    const [qzPass, setQzPass] = useState(50);
    const [qzProctoring, setQzProctoring] = useState(false);
    const [qzShuffleQuestions, setQzShuffleQuestions] = useState(false);
    const [qzShuffleOptions, setQzShuffleOptions] = useState(false);
    const [qzEndDate, setQzEndDate] = useState('');

    // Bank question form
    const [bqText, setBqText] = useState('');
    const [bqDiff, setBqDiff] = useState('EASY');
    const [bqType, setBqType] = useState('SINGLE_CHOICE');
    const [bqTag, setBqTag] = useState('');
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

    /* ── Fetch quizzes (phân trang) ── */
    const fetchQuizzes = useCallback(async () => {
      if (!activeCourseId) return;
      setQuizLoading(true);
      try {
        const data = await listQuizzes(activeCourseId, {
          page: quizPageNum - 1,
          size: QUIZ_PAGE_SIZE,
          title: debouncedQuizSearch || undefined,
        });
        setQuizzes(data.content);
        setQuizTotalElements(data.totalElements);
        setQuizTotalPages(Math.max(1, data.totalPages));
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setQuizLoading(false);
      }
    }, [activeCourseId, quizPageNum, debouncedQuizSearch, showToast]);

    // Debounce ô tìm kiếm (dùng chung với tab bank) khi đang ở tab quiz — 400ms, về trang 1
    useEffect(() => {
      if (tab !== 'quiz') return;
      const timer = setTimeout(() => {
        setDebouncedQuizSearch(q.trim());
        setQuizPageNum(1);
      }, 400);
      return () => clearTimeout(timer);
    }, [q, tab]);

    // Đổi khóa học → về trang 1
    useEffect(() => { setQuizPageNum(1); }, [activeCourseId]);

    /* ── Fetch bank questions — chế độ duyệt (phân trang), chỉ chạy khi KHÔNG tìm kiếm ── */
    const fetchBank = useCallback(async () => {
      if (!activeCourseId) return;
      setBankLoading(true);
      try {
        const params = { page: bankPageNum - 1, size: BANK_PAGE_SIZE };
        if (bankFilter !== 'ALL') params.difficulty = bankFilter;
        if (bankStatusFilter !== 'ALL') params.status = bankStatusFilter;
        const data = await listBankQuestions(activeCourseId, params);
        setBankList(data.content);
        setBankTotalElements(data.totalElements);
        setBankTotalPages(Math.max(1, data.totalPages));
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setBankLoading(false);
      }
    }, [activeCourseId, bankPageNum, bankFilter, bankStatusFilter, showToast]);

    useEffect(() => { fetchQuizzes(); }, [fetchQuizzes]);
    useEffect(() => {
      if (tab === 'bank' && q.trim().length < 3) fetchBank();
    }, [tab, fetchBank, q]);

    // Đổi khóa học/bộ lọc → về trang 1
    useEffect(() => { setBankPageNum(1); }, [activeCourseId, bankFilter, bankStatusFilter]);

    /* ── Chế độ tìm kiếm (hybrid, debounce 400ms) — thay hẳn cho danh sách phân trang ── */
    useEffect(() => {
      if (tab !== 'bank' || q.trim().length < 3) { setSearchHits([]); setSearchLoading(false); return; }
      const mySeq = ++bankSearchSeq.current;
      setSearchLoading(true);
      const timer = setTimeout(async () => {
        try {
          const params = {};
          if (bankFilter !== 'ALL') params.difficulty = bankFilter;
          if (bankStatusFilter !== 'ALL') params.status = bankStatusFilter;
          const hits = await searchBankQuestions(activeCourseId, q.trim(), params);
          if (bankSearchSeq.current !== mySeq) return; // response cũ, bỏ qua
          setSearchHits(hits);
        } catch {
          if (bankSearchSeq.current === mySeq) setSearchHits([]);
        } finally {
          if (bankSearchSeq.current === mySeq) setSearchLoading(false);
        }
      }, 400);
      return () => clearTimeout(timer);
    }, [q, tab, activeCourseId, bankFilter, bankStatusFilter]);

    /* ── 1 dòng bảng ngân hàng câu hỏi — dùng chung cho kết quả khớp chữ và ngữ nghĩa ── */
    const renderBankRow = (item, similarity) => (
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
          {similarity != null && (
            <span className="chip chip-neutral" style={{ marginLeft: 6, fontSize: 11 }}>
              Tương đồng {Math.round(similarity * 100)}%
            </span>
          )}
        </td>
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
    );

    /* ── Create / Edit quiz ── */
    function openEditQuiz(q) {
      setEditQuizItem(q);
      setQzTitle(q.title);
      setQzType(q.quizType);
      setQzDuration(q.durationMinutes || 45);
      setQzMax(q.maxAttempts || 3);
      setQzPass(q.passScore ?? 50);
      setQzProctoring(!!q.proctoringEnabled);
      setQzShuffleQuestions(!!q.shuffleQuestions);
      setQzShuffleOptions(!!q.shuffleOptions);
      setQzEndDate(toDatetimeLocalValue(q.endDate));
      setCreateQuizOpen(true);
    }

    const handleCreateQuiz = useCallback(async () => {
      if (!qzTitle.trim()) { showToast('Vui lòng nhập tên quiz', 'error'); return; }
      if (!activeCourseId) { showToast('Chưa chọn khóa học', 'error'); return; }
      setSubmitting(true);
      try {
        const payload = {
          title: qzTitle.trim(),
          quizType: qzType,
          durationMinutes: Number(qzDuration),
          maxAttempts: Number(qzMax),
          passScore: Number(qzPass),
          proctoringEnabled: qzProctoring,
          shuffleQuestions: qzShuffleQuestions,
          shuffleOptions: qzShuffleOptions,
          endDate: qzEndDate ? new Date(qzEndDate).toISOString() : undefined,
        };
        if (editQuizItem) {
          await updateQuiz(activeCourseId, editQuizItem.id, payload);
          showToast('Đã cập nhật quiz');
        } else {
          await createQuiz(activeCourseId, payload);
          showToast('Tạo quiz thành công');
        }
        setCreateQuizOpen(false);
        setEditQuizItem(null);
        fetchQuizzes();
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setSubmitting(false);
      }
    }, [activeCourseId, qzTitle, qzType, qzDuration, qzMax, qzPass, qzProctoring, qzShuffleQuestions, qzShuffleOptions, qzEndDate, editQuizItem, fetchQuizzes, showToast]);

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
      setBqTag('');
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
    }, [activeCourseId, bqText, bqType, bqDiff, bqTag, bqOpts, editBankItem, fetchBank, showToast]);

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
                setQzMax(3); setQzPass(50); setQzProctoring(false);
                setQzShuffleQuestions(false); setQzShuffleOptions(false); setQzEndDate('');
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
              { v: 'quiz', label: 'Đề trắc nghiệm', count: quizTotalElements },
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
            ) : quizzes.length === 0 ? (
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
                    {quizzes.map(quiz => (
                      <tr key={quiz.id} style={{ cursor: 'pointer' }}
                        onClick={() => { setDetailQuiz(quiz); setDetailOpen(true); }}>
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
                        <td onClick={e => e.stopPropagation()}>
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
        {tab === 'quiz' && !quizLoading && (
          <PageBar pg={{
            page: quizPageNum,
            pages: quizTotalPages,
            total: quizTotalElements,
            from: quizTotalElements ? (quizPageNum - 1) * QUIZ_PAGE_SIZE + 1 : 0,
            to: Math.min(quizPageNum * QUIZ_PAGE_SIZE, quizTotalElements),
            setPage: setQuizPageNum,
          }} unit="quiz" forcePager />
        )}

        {/* ── BANK TAB ── */}
        {tab === 'bank' && (() => {
          const searching = q.trim().length >= 3;
          const rows = searching ? searchHits : bankList;
          const loading = searching ? searchLoading : bankLoading;
          const firstSemanticIdx = searching ? searchHits.findIndex(h => h.matchType === 'SEMANTIC') : -1;
          return (
            <Section pad={false}>
              {loading && rows.length === 0 ? (
                <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>Đang tải...</div>
              ) : rows.length === 0 ? (
                <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-2)' }}>
                  <Ic n="layers" size={36} style={{ marginBottom: 10, opacity: 0.3 }} />
                  <p>{searching ? 'Không tìm thấy câu hỏi phù hợp.' : 'Ngân hàng câu hỏi trống. Thêm câu hỏi hoặc import CSV.'}</p>
                </div>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table className="tbl">
                    <thead>
                      <tr><th>Câu hỏi</th><th>Độ khó</th><th>Dùng trong</th><th>Trạng thái</th><th></th></tr>
                    </thead>
                    <tbody>
                      {searching
                        ? searchHits.flatMap((hit, idx) => [
                            idx === firstSemanticIdx && (
                              <tr key={`divider-${hit.question.id}`}>
                                <td colSpan={5} style={{ padding: '10px 18px', background: 'var(--surface-2)' }}>
                                  <div className="row gap-8" style={{ alignItems: 'center' }}>
                                    <Ic n="sparkles" size={13} style={{ color: 'var(--accent)' }} />
                                    <span className="t-xs" style={{ fontWeight: 600 }}>Kết quả tương đồng ngữ nghĩa</span>
                                  </div>
                                </td>
                              </tr>
                            ),
                            renderBankRow(hit.question, hit.matchType === 'SEMANTIC' ? hit.similarity : null),
                          ].filter(Boolean))
                        : bankList.map(item => renderBankRow(item, null))}
                      {searching && searchLoading && (
                        <tr>
                          <td colSpan={5} className="t-xs muted" style={{ padding: '10px 18px' }}>
                            Đang tìm câu hỏi tương tự...
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              )}
            </Section>
          );
        })()}
        {tab === 'bank' && !bankLoading && q.trim().length < 3 && (
          <PageBar pg={{
            page: bankPageNum,
            pages: bankTotalPages,
            total: bankTotalElements,
            from: bankTotalElements ? (bankPageNum - 1) * BANK_PAGE_SIZE + 1 : 0,
            to: Math.min(bankPageNum * BANK_PAGE_SIZE, bankTotalElements),
            setPage: setBankPageNum,
          }} unit="câu hỏi" />
        )}

        {/* ═══ Modal: Tạo / Sửa quiz ═══ */}
        <Modal open={createQuizOpen} onClose={() => { setCreateQuizOpen(false); setEditQuizItem(null); }} max={560}>
          <ModalHead
            title={editQuizItem ? 'Sửa quiz' : 'Tạo quiz mới'}
            sub={editQuizItem ? 'Chỉ áp dụng được khi quiz đang ở trạng thái Nháp' : 'Bạn có thể thêm câu hỏi và xuất bản sau'}
            icon="clipboard" iconBg="#eaf1ff" iconColor="#2563eb"
            onClose={() => { setCreateQuizOpen(false); setEditQuizItem(null); }}
          />
          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Tên quiz *</label>
              <input className="input" placeholder="VD: Kiểm tra giữa kỳ ReactJS" value={qzTitle} onChange={e => setQzTitle(e.target.value)} />
            </div>
            <div className="grid grid-2" style={{ gap: 12 }}>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Loại quiz</label>
                {editQuizItem ? (
                  <div className="input" style={{ display: 'flex', alignItems: 'center', color: 'var(--text-2)', background: 'var(--surface-2)' }}>
                    {TYPE_LABEL[qzType] || qzType}
                  </div>
                ) : (
                  <Select value={qzType} onChange={setQzType} options={[
                    { v: 'STATIC', label: 'Cố định' },
                    { v: 'SHUFFLED_POOL', label: 'Xáo câu' },
                    { v: 'RANDOM_DRAW', label: 'Ngẫu nhiên (bank)' },
                  ]} />
                )}
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
            <div className="grid grid-2" style={{ gap: 12 }}>
              <label className="row gap-10" style={{ padding: '11px 14px', background: 'var(--surface-2)', borderRadius: 10, cursor: 'pointer' }}>
                <input type="checkbox" checked={qzShuffleQuestions} onChange={e => setQzShuffleQuestions(e.target.checked)} style={{ width: 17, height: 17 }} />
                <div>
                  <div style={{ fontWeight: 600, fontSize: 13.5 }}>Xáo thứ tự câu hỏi</div>
                  <div className="t-xs muted">Đổi thứ tự câu mỗi lần thi</div>
                </div>
              </label>
              <label className="row gap-10" style={{ padding: '11px 14px', background: 'var(--surface-2)', borderRadius: 10, cursor: 'pointer' }}>
                <input type="checkbox" checked={qzShuffleOptions} onChange={e => setQzShuffleOptions(e.target.checked)} style={{ width: 17, height: 17 }} />
                <div>
                  <div style={{ fontWeight: 600, fontSize: 13.5 }}>Xáo thứ tự đáp án</div>
                  <div className="t-xs muted">Đổi thứ tự A/B/C/D mỗi lần thi</div>
                </div>
              </label>
            </div>
            <label className="row gap-10" style={{ padding: '11px 14px', background: 'var(--chip-error-bg)', borderRadius: 10, cursor: 'pointer' }}>
              <input type="checkbox" checked={qzProctoring} onChange={e => setQzProctoring(e.target.checked)} style={{ width: 17, height: 17 }} />
              <div>
                <div style={{ fontWeight: 600, fontSize: 13.5, color: 'var(--chip-error-fg)' }}>Bật chế độ giám sát</div>
                <div className="t-xs" style={{ color: 'var(--chip-error-fg)', opacity: 0.8 }}>
                  Phát hiện chuyển tab / mất focus cửa sổ khi làm bài. Vi phạm lần 1-2: cảnh báo, lần 3: tự động nộp bài.
                </div>
              </div>
            </label>
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => { setCreateQuizOpen(false); setEditQuizItem(null); }}>Hủy</button>
            <button className="btn btn-primary" onClick={handleCreateQuiz} disabled={submitting}>
              {submitting
                ? (editQuizItem ? 'Đang lưu...' : 'Đang tạo...')
                : <><Ic n={editQuizItem ? 'check' : 'plus'} size={16} />{editQuizItem ? 'Lưu thay đổi' : 'Tạo quiz'}</>}
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
            <div>
              <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Chủ đề (tag)</label>
              <input className="input" placeholder="VD: React Hooks" value={bqTag} onChange={e => setBqTag(e.target.value)} />
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

        {/* ═══ Modal: Chi tiết quiz — quản lý câu hỏi ═══ */}
        <QuizDetailModal
          open={detailOpen}
          onClose={() => { setDetailOpen(false); setDetailQuiz(null); fetchQuizzes(); }}
          courseId={activeCourseId}
          quiz={detailQuiz}
          showToast={showToast}
          nav={nav}
          onEdit={(q) => { setDetailOpen(false); setDetailQuiz(null); openEditQuiz(q); }}
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
    const STEPS = { form: 'form', progress: 'progress', preview: 'preview', saving: 'saving' };
    const [step, setStep] = useState(STEPS.form);
    const [error, setError] = useState('');

    // Form state
    const [topic, setTopic] = useState('');
    const [qType, setQType] = useState('SINGLE_CHOICE');
    const [diff, setDiff] = useState('MEDIUM');
    const [tag, setTag] = useState(''); // gõ trực tiếp hoặc chọn từ datalist các tag có sẵn
    const [bankTags, setBankTags] = useState([]); // tag đã dùng trong ngân hàng câu hỏi của khóa học
    const [count, setCount] = useState(5);
    const [threshold, setThreshold] = useState(0.88);

    // Giới hạn tài liệu AI tham khảo — chọn riêng vài tài liệu giúp sinh câu hỏi nhanh hơn
    // thay vì AI phải đọc toàn bộ tài liệu đã index của khóa học
    const [aiSources, setAiSources] = useState([]);
    const [sourceIds, setSourceIds] = useState([]); // rỗng = không giới hạn, dùng toàn bộ tài liệu
    const [docPickerOpen, setDocPickerOpen] = useState(false);

    // Progress state (chạy nền — poll jobId để biết đang ở bước nào)
    const [jobId, setJobId] = useState(null);
    const [jobStep, setJobStep] = useState(null);

    // Preview state
    const [result, setResult] = useState(null);
    const [selected, setSelected] = useState([]);

    // Inline edit state
    const [editingIdx, setEditingIdx] = useState(null);
    const [draft, setDraft] = useState(null); // { questionText, questionType, difficulty, options: [{text, correct}] }

    function reset() {
      setStep(STEPS.form); setError(''); setResult(null); setSelected([]);
      setTopic(''); setTag(''); setCount(5); setThreshold(0.88);
      setQType('SINGLE_CHOICE'); setDiff('MEDIUM'); setSourceIds([]); setDocPickerOpen(false);
      setEditingIdx(null); setDraft(null);
      setJobId(null); setJobStep(null);
    }

    // Nạp danh sách tag hiện có (từ ngân hàng câu hỏi) + tài liệu AI đã index của khóa học mỗi khi mở modal
    useEffect(() => {
      if (!open || !courseId) return;
      listBankQuestions(courseId, { status: 'ACTIVE', size: 500 })
        .then(data => {
          const tagSet = new Set();
          (data?.content || []).forEach(q => { if (q.subjectTag) tagSet.add(q.subjectTag); });
          setBankTags(Array.from(tagSet).sort());
        })
        .catch(() => setBankTags([]));
      listAiSources(courseId)
        .then(data => setAiSources((data || []).filter(s => s.ingestStatus === 'INDEXED')))
        .catch(() => setAiSources([]));
    }, [open, courseId]);

    function toggleSource(id) {
      setSourceIds(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);
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
      if (!tag.trim()) { setError('Vui lòng chọn hoặc nhập chuyên đề / tag.'); return; }
      setError(''); setStep(STEPS.progress); setJobStep('RETRIEVING_CONTEXT');
      try {
        const { jobId: newJobId } = await startAiGenerateQuestions(courseId, {
          topic, questionType: qType, difficulty: diff,
          subjectTag: tag.trim(),
          sourceIds: sourceIds.length > 0 ? sourceIds : undefined,
          count, duplicateThreshold: threshold,
        });
        setJobId(newJobId);
      } catch (err) {
        setError(err?.response?.data?.message || err?.message || 'Lỗi kết nối dịch vụ AI.');
        setStep(STEPS.form);
      }
    }

    // Poll tiến trình job sinh câu hỏi mỗi 1.5s, tự dừng khi DONE/FAILED
    useEffect(() => {
      if (!jobId || step !== STEPS.progress) return;
      let cancelled = false;
      const interval = setInterval(async () => {
        try {
          const status = await getAiGenerateJobStatus(courseId, jobId);
          if (cancelled) return;
          setJobStep(status.step);
          if (status.step === 'DONE') {
            clearInterval(interval);
            setResult(status.result);
            setSelected(status.result.questions.map(q => !q.duplicate));
            setStep(STEPS.preview);
          } else if (status.step === 'FAILED') {
            clearInterval(interval);
            setError(status.errorMessage || 'Sinh câu hỏi thất bại. Vui lòng thử lại.');
            setStep(STEPS.form);
          }
        } catch {
          // Lỗi mạng tạm thời lúc poll — bỏ qua, thử lại ở lượt kế tiếp
        }
      }, 1500);
      return () => { cancelled = true; clearInterval(interval); };
    }, [jobId, step, courseId]);

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
      <Modal open={open} onClose={() => { if (step !== STEPS.progress && step !== STEPS.saving) { reset(); onClose(); } }} max={760}>
        <ModalHead
          title="Tạo câu hỏi bằng AI"
          sub={step === STEPS.preview
            ? `${result?.totalGenerated} câu sinh được · ${result?.duplicateCount} trùng · ${result?.newCount} mới`
            : 'AI sẽ sinh câu hỏi và kiểm tra trùng lặp với ngân hàng hiện có'}
          icon="sparkles" iconBg="#f3edff" iconColor="#7c3aed"
          onClose={() => { if (step !== STEPS.progress && step !== STEPS.saving) { reset(); onClose(); } }}
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
                  <div className="t-label" style={{ marginBottom: 6 }}>Số câu muốn sinh (tối đa {AI_MAX_QUESTIONS_PER_GEN})</div>
                  <input className="input" type="number" min={1} max={AI_MAX_QUESTIONS_PER_GEN} style={{ width: '100%' }} value={count}
                    onChange={e => setCount(Math.max(1, Math.min(AI_MAX_QUESTIONS_PER_GEN, Number(e.target.value))))} />
                </div>
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>Chuyên đề / Tag <span style={{ color: 'var(--error)' }}>*</span></div>
                  <input className="input" style={{ width: '100%' }} list="ai-gen-tag-options"
                    placeholder="Chọn tag có sẵn hoặc gõ tag mới..."
                    value={tag} onChange={e => setTag(e.target.value)} />
                  <datalist id="ai-gen-tag-options">
                    {bankTags.map(t => <option key={t} value={t} />)}
                  </datalist>
                </div>
              </div>
              {aiSources.length > 0 && (
                <div>
                  <div className="t-label" style={{ marginBottom: 4 }}>Tài liệu tham khảo (tuỳ chọn)</div>
                  <div className="muted t-xs" style={{ marginBottom: 8 }}>
                    Không chọn = AI chỉ dựa theo chủ đề đã nhập (nhanh nhất, không đọc tài liệu). Chọn tài liệu để AI tham khảo thêm nội dung khóa học.
                  </div>
                  <button type="button" className="input" style={{
                    width: '100%', textAlign: 'left', cursor: 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8,
                  }} onClick={() => setDocPickerOpen(true)}>
                    <span style={{ color: sourceIds.length > 0 ? 'var(--accent)' : undefined }}>
                      {sourceIds.length === 0
                        ? 'Không dùng tài liệu — chỉ dựa theo chủ đề'
                        : `Đã chọn ${sourceIds.length}/${aiSources.length} tài liệu`}
                    </span>
                    <Ic n="chevron_down" size={16} style={{ flex: 'none', color: 'var(--text-3)' }} />
                  </button>
                </div>
              )}
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
              <button className="btn btn-primary" onClick={handleGenerate} disabled={!topic.trim() || !tag.trim()}>
                <Ic n="sparkles" size={16} />Sinh câu hỏi
              </button>
            </div>
          </>
        )}

        {/* ── Step 2: Progress ── */}
        {step === STEPS.progress && (
          <div className="modal-body" style={{ textAlign: 'center', padding: '48px 24px' }}>
            <div style={{ width: 56, height: 56, borderRadius: 999, background: '#f3edff', display: 'grid', placeItems: 'center', margin: '0 auto 16px', animation: 'spin 1.2s linear infinite' }}>
              <Ic n="sparkles" size={26} style={{ color: '#7c3aed' }} />
            </div>
            <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 6 }}>
              {GEN_STEP_LABEL[jobStep]?.title || 'AI đang sinh câu hỏi...'}
            </div>
            <div className="muted t-sm">{GEN_STEP_LABEL[jobStep]?.sub || 'Vui lòng chờ trong giây lát'}</div>
            <div className="row" style={{ gap: 8, justifyContent: 'center', marginTop: 20 }}>
              {GEN_STEP_ORDER.map(s => {
                const curIdx = GEN_STEP_ORDER.indexOf(jobStep);
                const sIdx = GEN_STEP_ORDER.indexOf(s);
                const done = curIdx > sIdx;
                const active = s === jobStep;
                return (
                  <div key={s} style={{
                    width: active ? 22 : 8, height: 8, borderRadius: 999,
                    background: done || active ? '#7c3aed' : 'var(--border)',
                    transition: 'all .25s ease',
                  }} />
                );
              })}
            </div>
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

        {docPickerOpen && (
          <AiSourcePickerModal
            sources={aiSources}
            sourceIds={sourceIds}
            onToggle={toggleSource}
            onSetAll={setSourceIds}
            onClose={() => setDocPickerOpen(false)}
          />
        )}
      </Modal>
    );
  }

  /* ═══ AiSourcePickerModal — chọn 1 hoặc nhiều tài liệu AI để giới hạn phạm vi tham khảo ═══ */
  function AiSourcePickerModal({ sources, sourceIds, onToggle, onSetAll, onClose }) {
    const [search, setSearch] = useState('');
    const filtered = sources.filter(s =>
      !search.trim() || s.sourceName.toLowerCase().includes(search.trim().toLowerCase()));
    const visibleSelectedCount = filtered.filter(s => sourceIds.includes(s.id)).length;
    const allVisibleSelected = filtered.length > 0 && visibleSelectedCount === filtered.length;

    function toggleAllVisible() {
      const visibleIds = filtered.map(s => s.id);
      onSetAll(allVisibleSelected
        ? sourceIds.filter(id => !visibleIds.includes(id))
        : Array.from(new Set([...sourceIds, ...visibleIds])));
    }

    return (
      <Modal open onClose={onClose} max={560}>
        <ModalHead title="Chọn tài liệu tham khảo" sub={`${sources.length} tài liệu đã index · Đã chọn ${sourceIds.length}`}
          icon="file_text" iconBg="#f3edff" iconColor="#7c3aed" onClose={onClose} />
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: 0 }}>
          <div style={{ padding: '16px 20px 0' }}>
            <Search placeholder="Tìm theo tên tài liệu..." value={search} onChange={setSearch} style={{ width: '100%' }} />
            {filtered.length > 0 && (
              <div className="between" style={{ marginTop: 10, marginBottom: 4 }}>
                <span className="t-xs muted">Hiển thị {filtered.length} / {sources.length} tài liệu</span>
                <button className="btn btn-ghost btn-sm" style={{ fontSize: 12, padding: '3px 10px' }} onClick={toggleAllVisible}>
                  {allVisibleSelected ? 'Bỏ chọn tất cả' : 'Chọn tất cả (đang hiển thị)'}
                </button>
              </div>
            )}
          </div>
          {filtered.length === 0 && (
            <div style={{ padding: '0 20px' }}>
              <Empty icon="search" title="Không tìm thấy tài liệu phù hợp" sub="Thử từ khóa khác." />
            </div>
          )}
          {filtered.length > 0 && (
            <div style={{ maxHeight: 380, overflowY: 'auto', padding: '0 20px 4px', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {filtered.map(s => (
                <label key={s.id} className="row gap-10" style={{
                  padding: '10px 12px', borderRadius: 10, cursor: 'pointer',
                  border: `1px solid ${sourceIds.includes(s.id) ? 'var(--accent)' : 'var(--border)'}`,
                  background: sourceIds.includes(s.id) ? 'rgba(37,99,235,.04)' : 'transparent',
                }}>
                  <input type="checkbox" checked={sourceIds.includes(s.id)} onChange={() => onToggle(s.id)} />
                  <span className="grow" style={{ fontSize: 13.5 }}>{s.sourceName}</span>
                  {s.chunkCount != null && <span className="t-xs muted">{s.chunkCount} đoạn</span>}
                </label>
              ))}
            </div>
          )}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={() => onSetAll([])} disabled={sourceIds.length === 0}>Bỏ chọn hết</button>
          <button className="btn btn-primary" onClick={onClose}>Xong ({sourceIds.length})</button>
        </div>
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
                <div className="t-label" style={{ marginBottom: 4 }}>TB % đúng</div>
                <div style={{ fontSize: 28, fontWeight: 800, color: 'var(--text)' }}>
                  {Number(stats.avgScorePercentage ?? 0).toFixed(1)}%
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

  /* ═══ QuizDetailModal — quản lý câu hỏi / cấu hình random ═══ */
  function QuizDetailModal({ open, onClose, courseId, quiz, showToast, nav, onEdit }) {
    const [detail, setDetail] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [tab, setTab] = useState('questions');
    const [removingId, setRemovingId] = useState(null);

    const [pickOpen, setPickOpen] = useState(false);
    const [manualOpen, setManualOpen] = useState(false);

    // Random draw config form
    const [randomMode, setRandomMode] = useState('FULLY_RANDOM');
    const [totalCount, setTotalCount] = useState(10);
    const [easyCount, setEasyCount] = useState(0);
    const [mediumCount, setMediumCount] = useState(0);
    const [hardCount, setHardCount] = useState(0);
    const [subjectTagFilter, setSubjectTagFilter] = useState('');
    const [randomSaving, setRandomSaving] = useState(false);
    const [randomError, setRandomError] = useState('');

    // Tóm tắt ngân hàng ACTIVE (dùng cho tab cấu hình ngẫu nhiên)
    const [bankSummary, setBankSummary] = useState({ total: 0, counts: { EASY: 0, MEDIUM: 0, HARD: 0 }, tags: [] });

    useEffect(() => {
      if (!open || !courseId) return;
      listBankQuestions(courseId, { status: 'ACTIVE', size: 500 })
        .then(data => {
          const items = data?.content || [];
          const counts = { EASY: 0, MEDIUM: 0, HARD: 0 };
          const tagSet = new Set();
          items.forEach(q => {
            counts[q.difficulty] = (counts[q.difficulty] || 0) + 1;
            if (q.subjectTag) tagSet.add(q.subjectTag);
          });
          setBankSummary({ total: items.length, counts, tags: Array.from(tagSet).sort() });
        })
        .catch(() => {});
    }, [open, courseId]);

    const load = useCallback(async () => {
      if (!courseId || !quiz) return;
      setLoading(true); setError('');
      try {
        const data = await getQuizDetail(courseId, quiz.id);
        setDetail(data);
        setRandomMode(data.randomMode || 'FULLY_RANDOM');
        setTotalCount(data.randomTotalCount || 10);
        setEasyCount(data.difficultyConfig?.EASY || 0);
        setMediumCount(data.difficultyConfig?.MEDIUM || 0);
        setHardCount(data.difficultyConfig?.HARD || 0);
        setSubjectTagFilter(data.subjectTagFilter || '');
      } catch (err) {
        setError(extractError(err));
      } finally {
        setLoading(false);
      }
    }, [courseId, quiz]);

    useEffect(() => {
      if (!open || !quiz) return;
      setTab(quiz.quizType === 'RANDOM_DRAW' ? 'random' : 'questions');
      setRandomError('');
      load();
    }, [open, quiz?.id]);

    if (!open || !quiz) return null;

    const isDraft = (detail?.status || quiz.status) === 'DRAFT';
    const isRandom = quiz.quizType === 'RANDOM_DRAW';
    const byDifficultyTotal = Number(easyCount || 0) + Number(mediumCount || 0) + Number(hardCount || 0);

    function handleDryRun() {
      const proctoringEnabled = !!(detail || quiz).proctoringEnabled;
      nav?.('dryRun', { courseId, quizId: quiz.id, quizTitle: quiz.title, proctoringEnabled });
    }

    async function handleRemoveQuestion(qId) {
      if (!confirm('Xóa câu hỏi này khỏi quiz?')) return;
      setRemovingId(qId);
      try {
        await removeQuestionFromQuiz(courseId, quiz.id, qId);
        showToast('Đã xóa câu hỏi khỏi quiz');
        load();
      } catch (err) {
        showToast(extractError(err), 'error');
      } finally {
        setRemovingId(null);
      }
    }

    async function handleAddBank(bankQuestionIds) {
      try {
        await addBankQuestionsToQuiz(courseId, quiz.id, { bankQuestionIds });
        showToast(`Đã thêm ${bankQuestionIds.length} câu vào quiz`);
        setPickOpen(false);
        load();
      } catch (err) {
        showToast(extractError(err), 'error');
      }
    }

    async function handleAddManual(payload) {
      try {
        await addManualQuestionToQuiz(courseId, quiz.id, payload);
        showToast('Đã thêm câu hỏi vào quiz');
        setManualOpen(false);
        load();
      } catch (err) {
        showToast(extractError(err), 'error');
      }
    }

    async function handleSaveRandomConfig() {
      setRandomSaving(true); setRandomError('');
      try {
        const payload = { randomMode, subjectTagFilter: subjectTagFilter.trim() || undefined };
        if (randomMode === 'FULLY_RANDOM') {
          payload.totalCount = Number(totalCount);
        } else {
          payload.difficultyConfig = { EASY: Number(easyCount), MEDIUM: Number(mediumCount), HARD: Number(hardCount) };
        }
        await configureRandomDraw(courseId, quiz.id, payload);
        showToast('Đã lưu cấu hình random draw');
        load();
      } catch (err) {
        setRandomError(extractError(err));
      } finally {
        setRandomSaving(false);
      }
    }

    const tabItems = isRandom
      ? [{ v: 'random', label: 'Cấu hình ngẫu nhiên' }]
      : [{ v: 'questions', label: 'Câu hỏi', count: detail?.questions?.length || 0 }];

    return (
      <Modal open={open} onClose={onClose} max={820}>
        <ModalHead
          title={quiz.title}
          sub={`${TYPE_LABEL[quiz.quizType] || quiz.quizType} · ${STATUS_LABEL[quiz.status] || quiz.status}`}
          icon="clipboard" iconBg="#eaf1ff" iconColor="#2563eb"
          onClose={onClose}
        />

        <div className="row gap-8" style={{ padding: '0 24px', borderBottom: '1px solid var(--border)', alignItems: 'center' }}>
          <div className="grow"><Tabs items={tabItems} value={tab} onChange={setTab} /></div>
          {isDraft && (
            <>
              <button className="btn btn-ghost btn-sm" style={{ flex: 'none', marginBottom: 8 }} onClick={() => onEdit?.(detail || quiz)}>
                <Ic n="edit" size={14} />Sửa quiz
              </button>
              <button className="btn btn-ghost btn-sm" style={{ flex: 'none', marginBottom: 8 }} onClick={handleDryRun}>
                <Ic n="play" size={14} />Làm thử
              </button>
            </>
          )}
        </div>

        <div className="modal-body" style={{ minHeight: 320 }}>
          {loading && <div style={{ padding: 48, textAlign: 'center', color: 'var(--text-2)' }}>Đang tải...</div>}
          {error && <div style={{ padding: 32, textAlign: 'center', color: 'var(--error)' }}>{error}</div>}

          {/* ── Tab Câu hỏi (STATIC / SHUFFLED_POOL) ── */}
          {!loading && !error && detail && tab === 'questions' && !isRandom && (
            <div>
              <div className="row gap-8" style={{ marginBottom: 14, flexWrap: 'wrap' }}>
                {!isDraft && (
                  <span className="t-xs muted">Quiz đã xuất bản — không thể sửa câu hỏi. Chuyển về Nháp để chỉnh sửa.</span>
                )}
                {isDraft && (
                  <>
                    <button className="btn btn-ghost btn-sm" onClick={() => setPickOpen(true)}>
                      <Ic n="layers" size={14} />Thêm từ ngân hàng
                    </button>
                    <button className="btn btn-ghost btn-sm" onClick={() => setManualOpen(true)}>
                      <Ic n="plus" size={14} />Thêm thủ công
                    </button>
                  </>
                )}
              </div>

              {detail.questions.length === 0 ? (
                <Empty icon="clipboard" title="Chưa có câu hỏi nào" sub="Thêm câu hỏi từ ngân hàng hoặc nhập thủ công." />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {detail.questions.map((qq, i) => (
                    <div key={qq.id} style={{ padding: 12, border: '1px solid var(--border)', borderRadius: 12 }}>
                      <div className="row gap-8" style={{ alignItems: 'flex-start' }}>
                        <span className="muted" style={{ fontSize: 12, flex: 'none', marginTop: 2 }}>#{i + 1}</span>
                        <div className="grow" style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 13.5, fontWeight: 500 }}>{qq.questionText}</div>
                          <div className="row gap-6" style={{ marginTop: 6 }}>
                            <span className={`chip chip-${DIFF_CHIP[qq.difficulty] || 'neutral'}`} style={{ fontSize: 10 }}>
                              {DIFF_LABEL[qq.difficulty] || qq.difficulty}
                            </span>
                          </div>
                        </div>
                        {isDraft && (
                          <button className="icon-btn" style={{ width: 30, height: 30, color: 'var(--error)', flex: 'none' }}
                            disabled={removingId === qq.id}
                            onClick={() => handleRemoveQuestion(qq.id)}>
                            <Ic n="x" size={14} />
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ── Tab Cấu hình ngẫu nhiên (RANDOM_DRAW) ── */}
          {!loading && !error && detail && tab === 'random' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 18, maxWidth: 540 }}>

              {/* Thông tin ngân hàng ACTIVE hiện có */}
              <div style={{ padding: '12px 16px', background: 'var(--surface-2)', borderRadius: 12 }}>
                <div className="t-xs muted" style={{ marginBottom: 8 }}>Ngân hàng câu hỏi đang hoạt động của khóa học</div>
                <div className="row gap-16" style={{ flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 13 }}><b>{bankSummary.total}</b> tổng số câu</span>
                  <span className="row gap-6" style={{ fontSize: 13 }}>
                    <span className="chip chip-success" style={{ fontSize: 10 }}>Dễ</span> {bankSummary.counts.EASY || 0}
                  </span>
                  <span className="row gap-6" style={{ fontSize: 13 }}>
                    <span className="chip chip-warning" style={{ fontSize: 10 }}>TB</span> {bankSummary.counts.MEDIUM || 0}
                  </span>
                  <span className="row gap-6" style={{ fontSize: 13 }}>
                    <span className="chip chip-error" style={{ fontSize: 10 }}>Khó</span> {bankSummary.counts.HARD || 0}
                  </span>
                </div>
              </div>

              {!isDraft ? (
                /* ── Read-only summary khi quiz đã publish ── */
                <div style={{ padding: '14px 16px', border: '1px solid var(--border)', borderRadius: 12 }}>
                  <div className="t-xs muted" style={{ marginBottom: 10 }}>
                    Quiz đã xuất bản — chuyển về Nháp để đổi cấu hình. Cấu hình hiện tại:
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13.5 }}>
                    <div>Chế độ: <b>{randomMode === 'FULLY_RANDOM' ? 'Ngẫu nhiên hoàn toàn' : 'Theo tỉ lệ độ khó'}</b></div>
                    {randomMode === 'FULLY_RANDOM'
                      ? <div>Tổng số câu rút: <b>{totalCount}</b></div>
                      : <div>Tỉ lệ: <b>{easyCount} Dễ · {mediumCount} Trung bình · {hardCount} Khó</b> (tổng {byDifficultyTotal})</div>}
                    <div>Chủ đề lọc: <b>{subjectTagFilter || 'Không lọc — toàn bộ ngân hàng'}</b></div>
                  </div>
                </div>
              ) : (
                <>
                  {randomError && <div style={{ padding: '10px 14px', background: 'var(--chip-error-bg)', color: 'var(--error)', borderRadius: 10, fontSize: 13 }}>{randomError}</div>}

                  <div>
                    <div className="t-label" style={{ marginBottom: 8 }}>Chế độ rút câu</div>
                    <div className="row gap-16">
                      <label className="row gap-6" style={{ cursor: 'pointer' }}>
                        <input type="radio" checked={randomMode === 'FULLY_RANDOM'} onChange={() => setRandomMode('FULLY_RANDOM')} />
                        Ngẫu nhiên hoàn toàn
                      </label>
                      <label className="row gap-6" style={{ cursor: 'pointer' }}>
                        <input type="radio" checked={randomMode === 'BY_DIFFICULTY'} onChange={() => setRandomMode('BY_DIFFICULTY')} />
                        Theo tỉ lệ độ khó
                      </label>
                    </div>
                  </div>

                  {randomMode === 'FULLY_RANDOM' ? (
                    <div>
                      <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>
                        Tổng số câu <span className="muted" style={{ fontWeight: 400 }}>(tối đa {bankSummary.total} câu khả dụng)</span>
                      </label>
                      <input className="input" type="number" min={1} value={totalCount}
                        style={Number(totalCount) > bankSummary.total ? { borderColor: 'var(--error)' } : undefined}
                        onChange={e => setTotalCount(e.target.value)} />
                      {Number(totalCount) > bankSummary.total && (
                        <div className="t-xs" style={{ color: 'var(--error)', marginTop: 4 }}>
                          Vượt quá số câu hiện có trong ngân hàng ({bankSummary.total}).
                        </div>
                      )}
                    </div>
                  ) : (
                    <div>
                      <div className="grid grid-3" style={{ gap: 10 }}>
                        {[
                          { key: 'EASY', label: 'Dễ', value: easyCount, set: setEasyCount },
                          { key: 'MEDIUM', label: 'Trung bình', value: mediumCount, set: setMediumCount },
                          { key: 'HARD', label: 'Khó', value: hardCount, set: setHardCount },
                        ].map(f => {
                          const max = bankSummary.counts[f.key] || 0;
                          const over = Number(f.value || 0) > max;
                          return (
                            <div key={f.key}>
                              <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>
                                {f.label} <span className="muted" style={{ fontWeight: 400 }}>(tối đa {max})</span>
                              </label>
                              <input className="input" type="number" min={0} value={f.value}
                                style={over ? { borderColor: 'var(--error)' } : undefined}
                                onChange={e => f.set(e.target.value)} />
                              {over && <div className="t-xs" style={{ color: 'var(--error)', marginTop: 4 }}>Chỉ có {max} câu</div>}
                            </div>
                          );
                        })}
                      </div>
                      <div className="t-xs muted" style={{ marginTop: 8 }}>Tổng cộng: {byDifficultyTotal} câu</div>
                    </div>
                  )}

                  <div>
                    <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Lọc theo chủ đề (tuỳ chọn)</label>
                    <Select
                      value={subjectTagFilter || ''}
                      onChange={setSubjectTagFilter}
                      options={[
                        { v: '', label: 'Không lọc — rút từ toàn bộ ngân hàng' },
                        ...bankSummary.tags.map(t => ({ v: t, label: t })),
                      ]}
                    />
                    <div className="t-xs muted" style={{ marginTop: 6, lineHeight: 1.5 }}>
                      Khi chọn 1 chủ đề, hệ thống chỉ rút câu hỏi có gắn tag đó khi học viên bắt đầu làm bài — dùng
                      khi ngân hàng có nhiều chủ đề khác nhau và bạn muốn quiz này chỉ kiểm tra riêng một mảng kiến
                      thức (VD: chỉ "Hooks" thay vì toàn bộ ReactJS). Để trống nếu muốn rút từ toàn bộ câu hỏi.
                    </div>
                  </div>

                  <div>
                    <button className="btn btn-primary" onClick={handleSaveRandomConfig} disabled={randomSaving}>
                      {randomSaving ? 'Đang lưu...' : <><Ic n="check" size={16} />Lưu cấu hình</>}
                    </button>
                  </div>
                </>
              )}
            </div>
          )}

        </div>

        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
        </div>

        {pickOpen && (
          <PickBankQuestionsModal
            courseId={courseId}
            existingBankIds={(detail?.questions || []).map(q => q.bankQuestionId).filter(Boolean)}
            onClose={() => setPickOpen(false)}
            onConfirm={handleAddBank}
          />
        )}
        {manualOpen && (
          <ManualQuizQuestionModal
            onClose={() => setManualOpen(false)}
            onConfirm={handleAddManual}
          />
        )}
      </Modal>
    );
  }

  /* ═══ PickBankQuestionsModal — chọn nhiều câu từ ngân hàng để thêm vào quiz ═══ */
  function PickBankQuestionsModal({ courseId, existingBankIds, onClose, onConfirm }) {
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [selected, setSelected] = useState({});
    const [saving, setSaving] = useState(false);

    const [search, setSearch] = useState('');
    const [diffFilter, setDiffFilter] = useState('ALL');
    const [tagFilter, setTagFilter] = useState('ALL');

    // Semantic search (hybrid) — nối thêm câu tương đồng ngữ nghĩa dưới kết quả khớp chữ
    const [semanticExtra, setSemanticExtra] = useState([]); // [{ question, similarity }]
    const [semanticLoading, setSemanticLoading] = useState(false);
    const semanticSeq = useRef(0);

    useEffect(() => {
      setLoading(true);
      listBankQuestions(courseId, { status: 'ACTIVE', size: 500 })
        .then(data => setItems(data?.content || []))
        .catch(err => setError(extractError(err)))
        .finally(() => setLoading(false));
    }, [courseId]);

    const existingSet = new Set(existingBankIds);
    const available = items.filter(it => !existingSet.has(it.id));

    const tagOptions = Array.from(new Set(available.map(it => it.subjectTag).filter(Boolean))).sort();

    const filtered = available.filter(it => {
      if (diffFilter !== 'ALL' && it.difficulty !== diffFilter) return false;
      if (tagFilter !== 'ALL' && it.subjectTag !== tagFilter) return false;
      if (search.trim() && !it.questionText.toLowerCase().includes(search.trim().toLowerCase())) return false;
      return true;
    });

    useEffect(() => {
      if (search.trim().length < 3) { setSemanticExtra([]); setSemanticLoading(false); return; }
      const mySeq = ++semanticSeq.current;
      setSemanticLoading(true);
      const timer = setTimeout(async () => {
        try {
          const hits = await searchBankQuestions(courseId, search.trim(), {
            status: 'ACTIVE',
            difficulty: diffFilter !== 'ALL' ? diffFilter : undefined,
            subjectTag: tagFilter !== 'ALL' ? tagFilter : undefined,
          });
          if (semanticSeq.current !== mySeq) return;
          const knownIds = new Set([...existingBankIds, ...filtered.map(it => it.id)]);
          setSemanticExtra(hits
            .filter(h => h.matchType === 'SEMANTIC' && !knownIds.has(h.question.id))
            .map(h => ({ question: h.question, similarity: h.similarity })));
        } catch {
          if (semanticSeq.current === mySeq) setSemanticExtra([]);
        } finally {
          if (semanticSeq.current === mySeq) setSemanticLoading(false);
        }
      }, 400);
      return () => clearTimeout(timer);
    }, [search, diffFilter, tagFilter, courseId]);

    // Gộp filtered + semantic-only để "chọn tất cả" và đếm số hiển thị bao gồm cả 2 nhóm
    const visibleItems = [...filtered, ...semanticExtra.map(h => h.question)];

    const selectedCount = Object.values(selected).filter(Boolean).length;
    const visibleSelectedCount = visibleItems.filter(it => selected[it.id]).length;
    const allVisibleSelected = visibleItems.length > 0 && visibleSelectedCount === visibleItems.length;

    function toggle(id) {
      setSelected(prev => ({ ...prev, [id]: !prev[id] }));
    }

    function toggleAllVisible() {
      setSelected(prev => {
        const next = { ...prev };
        visibleItems.forEach(it => { next[it.id] = !allVisibleSelected; });
        return next;
      });
    }

    async function submit() {
      const ids = Object.keys(selected).filter(id => selected[id]);
      if (ids.length === 0) return;
      setSaving(true);
      await onConfirm(ids);
      setSaving(false);
    }

    const renderPickRow = (item, similarity) => (
      <label key={item.id} className="row gap-10" style={{
        padding: '10px 12px', borderRadius: 10, cursor: 'pointer', alignItems: 'flex-start',
        border: `1px solid ${selected[item.id] ? 'var(--accent)' : 'var(--border)'}`,
        background: selected[item.id] ? 'rgba(37,99,235,.04)' : 'transparent',
      }}>
        <input type="checkbox" style={{ marginTop: 3 }} checked={!!selected[item.id]} onChange={() => toggle(item.id)} />
        <div className="grow" style={{ minWidth: 0 }}>
          <div style={{ fontSize: 13, lineHeight: 1.5 }}>{item.questionText}</div>
          <div className="row gap-6" style={{ marginTop: 6, flexWrap: 'wrap' }}>
            <span className={`chip chip-${DIFF_CHIP[item.difficulty] || 'neutral'}`} style={{ fontSize: 10 }}>
              {DIFF_LABEL[item.difficulty] || item.difficulty}
            </span>
            {item.subjectTag && <span className="chip chip-neutral" style={{ fontSize: 10 }}>{item.subjectTag}</span>}
            {similarity != null && (
              <span className="chip chip-neutral" style={{ fontSize: 10 }}>Tương đồng {Math.round(similarity * 100)}%</span>
            )}
          </div>
        </div>
      </label>
    );

    return (
      <Modal open onClose={onClose} max={640}>
        <ModalHead title="Thêm câu hỏi từ ngân hàng" sub={`${available.length} câu khả dụng · Đã chọn ${selectedCount}`}
          icon="layers" iconBg="#f3edff" iconColor="#7c3aed" onClose={onClose} />
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 0 }}>
          <div style={{ padding: '16px 20px 0' }}>
            <div className="row gap-8" style={{ flexWrap: 'wrap', marginBottom: 10 }}>
              <input className="input" style={{ flex: '1 1 200px', minWidth: 180 }}
                placeholder="Tìm theo nội dung câu hỏi..." value={search} onChange={e => setSearch(e.target.value)} />
              <Select value={diffFilter} onChange={setDiffFilter} style={{ width: 150, flex: 'none' }} options={[
                { v: 'ALL', label: 'Tất cả độ khó' },
                { v: 'EASY', label: 'Dễ' },
                { v: 'MEDIUM', label: 'Trung bình' },
                { v: 'HARD', label: 'Khó' },
              ]} />
              {tagOptions.length > 0 && (
                <Select value={tagFilter} onChange={setTagFilter} style={{ width: 160, flex: 'none' }} options={[
                  { v: 'ALL', label: 'Tất cả chủ đề' },
                  ...tagOptions.map(t => ({ v: t, label: t })),
                ]} />
              )}
            </div>
            {!loading && !error && visibleItems.length > 0 && (
              <div className="between" style={{ marginBottom: 4 }}>
                <span className="t-xs muted">Hiển thị {visibleItems.length} / {available.length} câu</span>
                <button className="btn btn-ghost btn-sm" style={{ fontSize: 12, padding: '3px 10px' }} onClick={toggleAllVisible}>
                  {allVisibleSelected ? 'Bỏ chọn tất cả' : 'Chọn tất cả (đang hiển thị)'}
                </button>
              </div>
            )}
          </div>

          {loading && <div className="muted" style={{ fontSize: 13.5, padding: '0 20px' }}>Đang tải...</div>}
          {error && <div style={{ color: 'var(--error)', fontSize: 13, padding: '0 20px' }}>{error}</div>}
          {!loading && !error && available.length === 0 && (
            <div style={{ padding: '0 20px' }}>
              <Empty icon="layers" title="Không có câu hỏi khả dụng" sub="Ngân hàng trống hoặc tất cả câu đã có trong quiz này." />
            </div>
          )}
          {!loading && !error && available.length > 0 && filtered.length === 0 && semanticExtra.length === 0 && !semanticLoading && (
            <div style={{ padding: '0 20px' }}>
              <Empty icon="search" title="Không tìm thấy câu hỏi phù hợp" sub="Thử từ khóa khác hoặc bỏ bớt bộ lọc." />
            </div>
          )}
          {!loading && !error && (filtered.length > 0 || semanticLoading || semanticExtra.length > 0) && (
            <div style={{ maxHeight: 400, overflowY: 'auto', padding: '0 20px 4px', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {filtered.map(item => renderPickRow(item, null))}
              {search.trim().length >= 3 && (semanticLoading || semanticExtra.length > 0) && (
                <div className="row gap-8" style={{ alignItems: 'center', padding: '6px 2px' }}>
                  <Ic n="sparkles" size={13} style={{ color: 'var(--accent)' }} />
                  <span className="t-xs" style={{ fontWeight: 600 }}>Kết quả tương đồng ngữ nghĩa</span>
                  {semanticLoading && <span className="t-xs muted">— Đang tìm câu hỏi tương tự...</span>}
                </div>
              )}
              {!semanticLoading && semanticExtra.map(h => renderPickRow(h.question, h.similarity))}
            </div>
          )}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Hủy</button>
          <button className="btn btn-primary" onClick={submit} disabled={saving || selectedCount === 0}>
            {saving ? 'Đang thêm...' : `Thêm (${selectedCount})`}
          </button>
        </div>
      </Modal>
    );
  }

  /* ═══ ManualQuizQuestionModal — thêm câu hỏi thủ công trực tiếp vào quiz ═══ */
  function ManualQuizQuestionModal({ onClose, onConfirm }) {
    const [text, setText] = useState('');
    const [type, setType] = useState('SINGLE_CHOICE');
    const [diff, setDiff] = useState('EASY');
    const [tag, setTag] = useState('');
    const [saveToBank, setSaveToBank] = useState(true);
    const [opts, setOpts] = useState([
      { optionText: '', isCorrect: true },
      { optionText: '', isCorrect: false },
      { optionText: '', isCorrect: false },
      { optionText: '', isCorrect: false },
    ]);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');

    async function submit() {
      if (!text.trim()) { setError('Vui lòng nhập nội dung câu hỏi'); return; }
      const filled = opts.filter(o => o.optionText.trim());
      if (filled.length < 2) { setError('Cần ít nhất 2 đáp án'); return; }
      if (!filled.some(o => o.isCorrect)) { setError('Cần chọn ít nhất 1 đáp án đúng'); return; }
      setSaving(true); setError('');
      try {
        await onConfirm({
          questionText: text.trim(),
          questionType: type,
          difficulty: diff,
          subjectTag: tag.trim() || undefined,
          saveToBank,
          options: filled.map((o, i) => ({ optionText: o.optionText.trim(), isCorrect: o.isCorrect, orderIndex: i })),
        });
      } finally {
        setSaving(false);
      }
    }

    return (
      <Modal open onClose={onClose} max={640}>
        <ModalHead title="Thêm câu hỏi thủ công" sub="Câu hỏi được thêm trực tiếp vào quiz"
          icon="edit" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {error && <div style={{ padding: '10px 14px', background: 'var(--chip-error-bg)', color: 'var(--error)', borderRadius: 10, fontSize: 13 }}>{error}</div>}
          <div className="grid grid-2" style={{ gap: 12 }}>
            <div>
              <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Loại câu hỏi</label>
              <Select value={type} onChange={setType} options={[
                { v: 'SINGLE_CHOICE', label: 'Một đáp án' },
                { v: 'MULTIPLE_CHOICE', label: 'Nhiều đáp án' },
              ]} />
            </div>
            <div>
              <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Độ khó</label>
              <Select value={diff} onChange={setDiff} options={[
                { v: 'EASY', label: 'Dễ' },
                { v: 'MEDIUM', label: 'Trung bình' },
                { v: 'HARD', label: 'Khó' },
              ]} />
            </div>
          </div>
          <div>
            <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Chủ đề (tag)</label>
            <input className="input" placeholder="VD: React Hooks" value={tag} onChange={e => setTag(e.target.value)} />
          </div>
          <div>
            <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>Nội dung câu hỏi *</label>
            <textarea className="input" style={{ height: 80, padding: 12, resize: 'vertical' }}
              placeholder="Nhập nội dung câu hỏi..." value={text} onChange={e => setText(e.target.value)} />
          </div>
          <div>
            <label className="t-label" style={{ display: 'block', marginBottom: 8 }}>
              Đáp án — {type === 'MULTIPLE_CHOICE' ? 'chọn nhiều đáp án đúng' : 'chọn 1 đáp án đúng'}
            </label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
              {opts.map((opt, i) => (
                <div key={i} className="row gap-10" style={{
                  padding: '8px 12px', borderRadius: 10,
                  border: `1.5px solid ${opt.isCorrect ? 'var(--success)' : 'var(--border)'}`,
                  background: opt.isCorrect ? 'var(--chip-success-bg)' : '#fff',
                }}>
                  <span
                    onClick={() => {
                      const next = opts.map((o, j) => ({
                        ...o,
                        isCorrect: type === 'SINGLE_CHOICE' ? j === i : (j === i ? !o.isCorrect : o.isCorrect),
                      }));
                      setOpts(next);
                    }}
                    style={{
                      width: 22, height: 22, borderRadius: type === 'MULTIPLE_CHOICE' ? 5 : 999,
                      flex: 'none', cursor: 'pointer',
                      border: `2px solid ${opt.isCorrect ? 'var(--success)' : 'var(--border-input)'}`,
                      display: 'grid', placeItems: 'center',
                    }}
                  >
                    {opt.isCorrect && <span style={{
                      width: 11, height: 11,
                      borderRadius: type === 'MULTIPLE_CHOICE' ? 3 : 999,
                      background: 'var(--success)',
                    }} />}
                  </span>
                  <span style={{ fontWeight: 700, color: 'var(--text-3)', width: 18 }}>{String.fromCharCode(65 + i)}</span>
                  <input
                    className="input"
                    style={{ height: 36, border: 'none', background: 'transparent', padding: 0, flex: 1 }}
                    placeholder={`Đáp án ${String.fromCharCode(65 + i)}`}
                    value={opt.optionText}
                    onChange={e => setOpts(opts.map((o, j) => j === i ? { ...o, optionText: e.target.value } : o))}
                  />
                  {opt.isCorrect && <span className="chip chip-success" style={{ flex: 'none' }}>Đúng</span>}
                </div>
              ))}
            </div>
          </div>
          <label className="row gap-10" style={{ cursor: 'pointer' }}>
            <input type="checkbox" checked={saveToBank} onChange={e => setSaveToBank(e.target.checked)} />
            <span style={{ fontSize: 13 }}>Đồng thời lưu vào ngân hàng câu hỏi</span>
          </label>
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Hủy</button>
          <button className="btn btn-success" onClick={submit} disabled={saving}>
            {saving ? 'Đang lưu...' : <><Ic n="check" size={16} />Thêm vào quiz</>}
          </button>
        </div>
      </Modal>
    );
  }

  Object.assign(window, { InsAssess });
})();
