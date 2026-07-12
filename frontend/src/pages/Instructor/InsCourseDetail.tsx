// @ts-nocheck
/* ============================================================
     RIKKEI EDU – Giảng viên · Chi tiết Khóa học
   ============================================================ */
(function () {
  const { useState, useEffect, useRef } = React;
  const Ic = window.Icon;
  const { Status, StatCard, Tabs, Select, Section, Modal, ModalHead, Empty } = window;
  const { AddChapterModal, AddLessonModal, ChangeQuizModal, AddResourceModal, ResourcePreviewModal, CourseContentTab, CourseVersionsTab, CourseHistoryTab, AiDocsTab } = window;
  const EditResourceInner = window.EditResourceModal;
  const api = window.httpClient;

  const CATS   = ["Frontend", "Backend", "DevOps", "Database", "AI/ML", "Mobile", "Testing", "Design", "Security", "Quy trình"];
  const LEVELS = [
    { label: "Cơ bản",    value: "BEGINNER"     },
    { label: "Trung cấp", value: "INTERMEDIATE" },
    { label: "Nâng cao",  value: "ADVANCED"     },
  ];

  const STATUS_LABEL = {
    DRAFT:          "Bản nháp",
    PENDING:        "Chờ duyệt",
    PUBLISHED:      "Đã xuất bản",
    REJECTED:       "Từ chối",
    PENDING_UPDATE: "Chờ cập nhật",
    ARCHIVED:       "Lưu trữ",
  };
  const STATUS_COLOR = {
    DRAFT:          { bg: "#f1f5f9", color: "#64748b" },
    PENDING:        { bg: "#fef9c3", color: "#a16207" },
    PUBLISHED:      { bg: "#dcfce7", color: "#16a34a" },
    REJECTED:       { bg: "#fee2e2", color: "#dc2626" },
    PENDING_UPDATE: { bg: "#e0f2fe", color: "#0284c7" },
    ARCHIVED:       { bg: "#f1f5f9", color: "#475569" },
  };

  const RES_TYPE = {
    VIDEO: { ic: "video",  bg: "#eaf1ff", fg: "#2563eb" },
    PDF:   { ic: "file",   bg: "#fdecec", fg: "#dc2626" },
    DOC:   { ic: "file",   bg: "#eaf1ff", fg: "#2563eb" },
    SLIDE: { ic: "layers", bg: "#fef5e6", fg: "#d97706" },
    IMAGE: { ic: "image",  bg: "#f0fdf4", fg: "#16a34a" },
    OTHER: { ic: "file",   bg: "#f1f5f9", fg: "#475569" },
  };

  /* ── helpers ──────────────────────────────────────────── */
  function fmtDur(s) {
    if (!s) return null;
    return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
  }

  function mapCourse(data) {
    return (data.chapters || []).map(ch => ({
      id:            ch.id,
      name:          ch.title,
      isDraft:       ch.isDraft       || false,
      pendingDelete: ch.pendingDelete || false,
      items: (ch.lessons || []).map(l => ({
        lessonId:         l.id,
        title:            l.title,
        lessonType:       l.lessonType || l.type,
        dur:              fmtDur(l.durationSeconds),
        isDraft:          l.isDraft          || false,
        pendingDelete:    l.pendingDelete    || false,
        draftTitle:       l.draftTitle       || null,
        draftContentText: l.draftContentText || null,
        quizId:           l.quizId    || null,
        quizTitle:        l.quizTitle || null,
        quizStatus:       l.quizStatus || null,
        resources:        (l.resources || []).map(r => ({
          resourceId:    r.id,
          title:         r.displayName || r.originalFilename,
          resourceType:  r.resourceType,
          kind:          r.resourceType?.toLowerCase(),
          externalUrl:   r.externalUrl || null,
          mimeType:      r.mimeType || null,
          isNewInUpdate: r.isNewInUpdate || false,
          pendingDelete: r.pendingDelete || false,
        })),
      })),
    }));
  }

  function Field({ label, children, hint, full }) {
    return (
      <div style={{ gridColumn: full ? "1 / -1" : "auto" }}>
        <label className="t-label" style={{ display: "block", marginBottom: 7 }}>{label}</label>
        {children}
        {hint && <div className="t-xs muted" style={{ marginTop: 6 }}>{hint}</div>}
      </div>
    );
  }

  function Dropzone({ icon, title, hint, h, file, onClick, onDrop }) {
    return (
      <div style={{ border: "2px dashed var(--border-strong)", borderRadius: 12, padding: h || 26, textAlign: "center", color: "var(--text-3)", cursor: "pointer", background: "var(--surface-2)", transition: ".15s" }}
        onClick={onClick}
        onMouseEnter={e => { e.currentTarget.style.borderColor = "var(--accent)"; e.currentTarget.style.background = "var(--accent-soft)"; }}
        onMouseLeave={e => { e.currentTarget.style.borderColor = "var(--border-strong)"; e.currentTarget.style.background = "var(--surface-2)"; }}
        onDragOver={e => { e.preventDefault(); e.currentTarget.style.borderColor = "var(--accent)"; }}
        onDragLeave={e => { e.currentTarget.style.borderColor = "var(--border-strong)"; }}
        onDrop={e => { e.preventDefault(); e.currentTarget.style.borderColor = "var(--border-strong)"; onDrop?.(e.dataTransfer.files?.[0]); }}>
        {file
          ? <div style={{ fontWeight: 600, color: "var(--text)", fontSize: 14 }}>{file.name} <span className="muted t-xs">({(file.size / 1024 / 1024).toFixed(1)} MB)</span></div>
          : <>
              <div className="stat-ic" style={{ width: 46, height: 46, borderRadius: 12, background: "#fff", color: "var(--accent)", margin: "0 auto 10px" }}><Ic n={icon} size={22} /></div>
              <div style={{ fontWeight: 600, fontSize: 14, color: "var(--text)" }}>{title}</div>
              <div className="t-xs" style={{ marginTop: 4 }}>{hint}</div>
            </>}
      </div>
    );
  }

  /* Inline edit tên chương / bài giảng */
  function InlineEdit({ value, onSave, style, placeholder }) {
    const [editing, setEditing] = useState(false);
    const [val, setVal]         = useState(value);
    const ref = useRef();
    useEffect(() => { setVal(value); }, [value]);
    useEffect(() => { if (editing) ref.current?.select(); }, [editing]);
    function commit() {
      setEditing(false);
      const t = val.trim();
      if (t && t !== value) onSave(t); else setVal(value);
    }
    if (editing) return (
      <input ref={ref} value={val} onChange={e => setVal(e.target.value)}
        onBlur={commit}
        onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); commit(); } if (e.key === "Escape") { setEditing(false); setVal(value); } }}
        onClick={e => e.stopPropagation()}
        style={{ background: "var(--surface)", border: "1px solid var(--accent)", borderRadius: 6, padding: "3px 8px", fontSize: "inherit", fontWeight: "inherit", color: "inherit", outline: "none", width: "100%", ...style }} />
    );
    return (
      <span style={{ cursor: "text", ...style }} title="Nhấn để đổi tên" onClick={e => { e.stopPropagation(); setEditing(true); }}>
        {value || <span style={{ color: "var(--text-3)", fontStyle: "italic" }}>{placeholder}</span>}
      </span>
    );
  }

  function InsCourseDetail({ nav, slug }) {
    const [courseId, setCourseId] = useState(null);

    const [course, setCourse]     = useState(null);
    const [chapters, setChapters] = useState([]);
    const [loading, setLoading]   = useState(true);
    const [err, setErr]           = useState(null);
    const [tab, setTab]           = useState("content");
    const [open, setOpen]         = useState(0);
    const [submitting, setSubmitting] = useState(false);
    const [submitMsg, setSubmitMsg]   = useState(null);

    const [editTitle,        setEditTitle]        = useState("");
    const [editDesc,         setEditDesc]         = useState("");
    const [editLevel,        setEditLevel]        = useState(LEVELS[1].value);
    const [editCat,          setEditCat]          = useState(null);
    const [categories,       setCategories]       = useState([]);
    const [infoSaving,       setInfoSaving]       = useState(false);
    const [infoMsg,          setInfoMsg]          = useState(null);
    const [thumbFile,        setThumbFile]        = useState(null);
    const [thumbPreview,     setThumbPreview]     = useState(null);
    const [thumbUploading,   setThumbUploading]   = useState(false);
    const [thumbProgress,    setThumbProgress]    = useState(0);
    const [thumbModalOpen,   setThumbModalOpen]   = useState(false);
    const [thumbModalFile,   setThumbModalFile]   = useState(null);
    const [thumbModalPreview,setThumbModalPreview]= useState(null);
    const thumbInputRef      = useRef<any>();
    const thumbModalInputRef = useRef<any>();

    const [addChapterOpen,    setAddChapterOpen]    = useState(false);
    const [addLessonState,    setAddLessonState]    = useState(null);
    const [addResourceState,  setAddResourceState]  = useState(null);
    const [renameLessonState, setRenameLessonState] = useState(null);
    const [changeQuizState,   setChangeQuizState]   = useState(null); // { chapterId, lessonId, currentQuizId }
    const [editResourceState, setEditResourceState] = useState(null);
    const [showPreview,       setShowPreview]       = useState(false);
    const [history,           setHistory]           = useState([]);
    const [historyLoading,    setHistoryLoading]    = useState(false);
    const [snapshotView,      setSnapshotView]      = useState(null); // { versionNo, snapshot }
    const [versions,          setVersions]          = useState([]);   // CourseVersionResponse[]
    const [rollingBack,       setRollingBack]       = useState(null); // versionId đang rollback
    const [savingDraft,       setSavingDraft]       = useState(false);
    const [deletingDraft,     setDeletingDraft]     = useState(null); // versionId đang xóa
    const [showSaveDraft,     setShowSaveDraft]     = useState(false);
    const [draftLabel,        setDraftLabel]        = useState("");
    const [viewingVersion,    setViewingVersion]    = useState(null); // null = live | CourseVersionResponse
    const [showVersionPicker, setShowVersionPicker] = useState(false);
    const [cloningDraft,      setCloningDraft]      = useState(false);
    const [submittingVersion, setSubmittingVersion] = useState(false);
    const [renamingVersion,   setRenamingVersion]   = useState(null); // versionId đang đổi tên
    const [renameInput,       setRenameInput]       = useState("");
    const [previewResource,   setPreviewResource]   = useState(null); // { resourceId, lessonId, title, resourceType, externalUrl, mimeType }
    const [resourceMenu,      setResourceMenu]      = useState(null); // { resourceId, el } dropdown đang mở
    const [openLessons,       setOpenLessons]       = useState<Record<string, boolean>>({}); // lessonId → expanded
    const toggleLesson = (id: string) => setOpenLessons(prev => ({ ...prev, [id]: !prev[id] }));
    const [loadDraftTarget,   setLoadDraftTarget]   = useState(null); // version đang chờ load khi cần chọn draft để xóa
    const [showReplaceDraft,  setShowReplaceDraft]  = useState(false);
    const [confirmState,      setConfirmState]      = useState(null); // { message, title?, danger?, onConfirm }
    const [alertState,        setAlertState]        = useState(null); // { message, title?, type? }
    const showConfirm = (message, onConfirm, opts?) => setConfirmState({ message, onConfirm, ...opts });
    const showAlert   = (message, opts?)           => setAlertState({ message, ...opts });

    function loadCourse(silent = false) {
      if (!slug) { setLoading(false); return; }
      if (!silent) { setLoading(true); setErr(null); }
      const scrollEl = document.querySelector(".page");
      const savedScroll = scrollEl?.scrollTop ?? 0;
      // Sau lần tải đầu tiên đã biết courseId thật — dùng lại nó cho các lần tải ngầm
      // (sau khi sửa) thay vì tra lại theo slug, vì đổi tên khóa học sẽ sinh slug mới
      // khiến slug cũ trên URL không còn khớp dữ liệu (dù URL chưa được cập nhật).
      const request = courseId
        ? api.get(`/instructor/courses/${courseId}`)
        : api.get(`/instructor/courses/by-slug/${encodeURIComponent(slug)}`);
      request
        .then(r => {
          setCourse(r.data);
          setCourseId(r.data.id);
          // AIChatbot/LecturePlayer đọc khóa học "đang mở" qua kênh này — giữ để
          // không phá vỡ các nơi đó, dù trang này giờ không còn phụ thuộc vào nó nữa.
          window.__selectedCourseId = r.data.id;
          sessionStorage.setItem("selectedCourseId", r.data.id);
          setChapters(mapCourse(r.data));
          setEditTitle(r.data.title || "");
          setEditDesc(r.data.description || "");
          setEditLevel(r.data.level || LEVELS[1].value);
          setEditCat(r.data.category?.id || null);
          setThumbFile(null);
          setThumbPreview(null);
          if (silent) requestAnimationFrame(() => { scrollEl?.scrollTo({ top: savedScroll }); });
        })
        .catch(e => setErr(e?.response?.data?.message || "Không thể tải khóa học"))
        .finally(() => { if (!silent) setLoading(false); });
    }
    useEffect(() => { loadCourse(); setViewingVersion(null); }, [slug]);
    useEffect(() => {
      api.get("/instructor/courses/categories").then(r => setCategories(r.data || [])).catch(() => {});
    }, []);
    useEffect(() => {
      if (courseId) {
        api.get(`/instructor/courses/${courseId}/versions`).then(r => setVersions(r.data || [])).catch(() => {});
      }
    }, [courseId]);

    async function handleSubmit() {
      setSubmitting(true); setSubmitMsg(null);
      try { await api.put(`/instructor/courses/${courseId}/submit`); setSubmitMsg("Đã gửi duyệt thành công!"); loadCourse(true); }
      catch (e) { setSubmitMsg(e?.response?.data?.message || "Gửi duyệt thất bại"); }
      finally { setSubmitting(false); }
    }

    async function handleWithdraw() {
      const isPendingUpdate = course?.status === "PENDING_UPDATE";
      const msg = isPendingUpdate
        ? "Hủy toàn bộ thay đổi đang chờ duyệt? Khóa học sẽ trở về trạng thái live gốc."
        : "Rút khỏi hàng chờ duyệt và chuyển về Bản nháp?";
      showConfirm(msg, async () => {
        setSubmitting(true); setSubmitMsg(null);
        try {
          await api.put(`/instructor/courses/${courseId}/withdraw`);
          setSubmitMsg(isPendingUpdate ? "Đã hủy cập nhật – khóa học trở về trạng thái đang xuất bản." : "Đã rút duyệt – khóa học trở về Bản nháp.");
          loadCourse(true);
        }
        catch (e) { setSubmitMsg(e?.response?.data?.message || "Thao tác thất bại"); }
        finally { setSubmitting(false); }
      }, { title: "Xác nhận rút duyệt", danger: true, confirmLabel: "Rút duyệt" });
    }

    async function handleRollback(versionId, versionNumber) {
      showConfirm(
        `Thao tác này sẽ tạo bản nháp từ nội dung cũ. Bạn cần xem lại và bấm "Gửi cập nhật" để admin duyệt.`,
        async () => {
          setRollingBack(versionId);
          try {
            await api.post(`/instructor/courses/${courseId}/versions/${versionId}/rollback`);
            setSubmitMsg(`Đã khôi phục v${versionNumber}. Xem lại nội dung rồi bấm "Gửi cập nhật".`);
            setViewingVersion(null);
            loadCourse(true);
            setTab("content");
          } catch (e) {
            setSubmitMsg(e?.response?.data?.message || "Khôi phục thất bại");
          } finally {
            setRollingBack(null);
          }
        },
        { title: `Rollback về v${versionNumber}?`, confirmLabel: "Rollback" }
      );
    }

    async function doSubmitVersion(versionId, versionLabel) {
      setSubmittingVersion(true);
      try {
        const res = await api.post(`/instructor/courses/${courseId}/versions/${versionId}/submit`);
        const [vRes] = await Promise.all([
          api.get(`/instructor/courses/${courseId}/versions`),
          api.get(`/instructor/courses/${courseId}`).then(r => { setCourse(r.data); setChapters(mapCourse(r.data)); }),
        ]);
        setVersions(vRes.data || []);
        setViewingVersion(v => v?.id === versionId ? { ...v, status: "PENDING", versionNumber: res.data.versionNumber } : v);
        setSubmitMsg(`Đã nộp "${versionLabel}" để admin duyệt.`);
      } catch (e) {
        showAlert(e?.response?.data?.message || "Nộp duyệt thất bại", { title: "Lỗi nộp duyệt" });
      } finally {
        setSubmittingVersion(false);
      }
    }
    async function handleSubmitVersion(versionId, versionLabel) {
      setSubmittingVersion(true);
      try {
        const { data: hasPending } = await api.get(`/instructor/courses/${courseId}/versions/has-pending`);
        setSubmittingVersion(false);
        if (hasPending) {
          showConfirm(
            `Đang có một phiên bản khác chờ admin duyệt.\n\nBạn có muốn thay bằng "${versionLabel}" không?\nPhiên bản cũ sẽ bị hủy và chuyển về bản nháp.`,
            () => doSubmitVersion(versionId, versionLabel),
            { title: "Xác nhận thay thế phiên bản", confirmLabel: "Thay thế", danger: true }
          );
        } else {
          await doSubmitVersion(versionId, versionLabel);
        }
      } catch (e) {
        showAlert(e?.response?.data?.message || "Nộp duyệt thất bại", { title: "Lỗi" });
        setSubmittingVersion(false);
      }
    }

    // Tải bản nháp để chỉnh sửa: auto-save bản đang sửa nếu có thay đổi, rồi rollback
    async function handleLoadForEdit(targetVersion) {
      const draftVersions = versions.filter(v => v.status === "DRAFT");
      const doRollback = async () => {
        setRollingBack(targetVersion.id);
        try {
          await api.post(`/instructor/courses/${courseId}/versions/${targetVersion.id}/rollback`);
          setViewingVersion(null);
          loadCourse(true);
          setTab("content");
        } catch (e) {
          showAlert(e?.response?.data?.message || "Không thể tải bản nháp", { title: "Lỗi" });
          throw e;
        } finally {
          setRollingBack(null);
        }
      };

      // Luôn auto-save bản đang sửa trước khi tải (dù có thay đổi hay không)
      if (draftVersions.length < 3) {
        // Còn chỗ → auto-save rồi rollback
        setSavingDraft(true);
        try {
          const autoLabel = `Auto-save trước khi tải "${targetVersion.label || `v${targetVersion.versionNumber}` || "bản nháp"}"`;
          const res = await api.post(`/instructor/courses/${courseId}/versions/save-draft?label=${encodeURIComponent(autoLabel)}`);
          setVersions(prev => [res.data, ...prev]);
        } catch (e) {
          showAlert(e?.response?.data?.message || "Không thể lưu bản đang chỉnh sửa", { title: "Lỗi lưu bản nháp" });
          setSavingDraft(false);
          return;
        } finally {
          setSavingDraft(false);
        }
        await doRollback();
      } else {
        // Đã đủ 3 draft → cần chọn 1 bản nháp để xóa
        setLoadDraftTarget(targetVersion);
        setShowReplaceDraft(true);
      }
    }

    async function handleCloneAsDraft(versionId, defaultLabel) {
      setCloningDraft(true);
      try {
        const params = defaultLabel?.trim() ? `?label=${encodeURIComponent(defaultLabel.trim())}` : "";
        const res = await api.post(`/instructor/courses/${courseId}/versions/${versionId}/clone-as-draft${params}`);
        setVersions(prev => [res.data, ...prev]);
        setSubmitMsg(`Đã tạo bản nháp "${res.data.label || defaultLabel || "mới"}" từ phiên bản này.`);
      } catch (e) {
        showAlert(e?.response?.data?.message || "Tạo bản nháp thất bại", { title: "Lỗi" });
      } finally {
        setCloningDraft(false);
      }
    }

    async function handleSaveDraft(label) {
      setSavingDraft(true);
      try {
        const params = label?.trim() ? `?label=${encodeURIComponent(label.trim())}` : "";
        await api.post(`/instructor/courses/${courseId}/versions/save-draft${params}`);
        setShowSaveDraft(false);
        setDraftLabel("");
        setSubmitMsg("Đã lưu bản nháp thành công!");
        // Reload versions list
        api.get(`/instructor/courses/${courseId}/versions`).then(r => setVersions(r.data || []));
      } catch (e) {
        showAlert(e?.response?.data?.message || "Lưu bản nháp thất bại", { title: "Lỗi lưu bản nháp" });
      } finally {
        setSavingDraft(false);
      }
    }

    async function deleteVersion(versionId) {
      setDeletingDraft(versionId);
      try {
        await api.delete(`/instructor/courses/${courseId}/versions/${versionId}/draft`);
        setVersions(prev => prev.filter(v => v.id !== versionId));
      } catch (e) {
        showAlert(e?.response?.data?.message || "Xóa thất bại", { title: "Lỗi xóa phiên bản" });
      } finally {
        setDeletingDraft(null);
      }
    }
    async function handleRenameVersion(versionId, newLabel) {
      const trimmed = newLabel.trim();
      try {
        await api.patch(`/instructor/courses/${courseId}/versions/${versionId}/label`, { label: trimmed || null });
        setVersions(prev => prev.map(v => v.id === versionId ? { ...v, label: trimmed || null } : v));
        setViewingVersion(v => v?.id === versionId ? { ...v, label: trimmed || null } : v);
      } catch (e) {
        showAlert(e?.response?.data?.message || "Đổi tên thất bại", { title: "Lỗi đổi tên" });
      } finally {
        setRenamingVersion(null);
      }
    }

    async function handleDeleteDraft(versionId) {
      showConfirm(
        "Hành động này không thể hoàn tác.",
        () => deleteVersion(versionId),
        { title: "Xóa phiên bản này?", danger: true, confirmLabel: "Xóa" }
      );
    }

    async function handleWithdrawVersion() {
      showConfirm(
        "Phiên bản đang chờ sẽ chuyển về bản nháp, bạn có thể chỉnh sửa và nộp lại sau.",
        async () => {
          try {
            await api.put(`/instructor/courses/${courseId}/withdraw`);
            await loadCourse(true);
            const vRes = await api.get(`/instructor/courses/${courseId}/versions`);
            setVersions(vRes.data || []);
          } catch (e) {
            showAlert(e?.response?.data?.message || "Rút gửi duyệt thất bại", { title: "Lỗi" });
          }
        },
        { title: "Rút khỏi hàng chờ duyệt?", danger: true, confirmLabel: "Rút duyệt" }
      );
    }

    async function handleSaveInfo() {
      if (!editTitle.trim()) { setInfoMsg("Vui lòng nhập tên khóa học"); return; }
      setInfoSaving(true); setInfoMsg(null);
      try {
        await api.put(`/instructor/courses/${courseId}`, {
          title: editTitle.trim(),
          description: editDesc.trim() || null,
          level: editLevel,
          categoryId: (editCat && editCat !== "null") ? editCat : null,
        });
        setInfoMsg("Đã lưu thay đổi!"); loadCourse(true);
      } catch (e) { setInfoMsg(e?.response?.data?.message || "Lưu thất bại"); }
      finally { setInfoSaving(false); }
    }

    async function handleRenameChapter(chapterId, newTitle) {
      try { await api.put(`/instructor/courses/${courseId}/chapters/${chapterId}`, { title: newTitle }); loadCourse(true); } catch (e) { console.error("Rename chapter failed", e); }
    }
    async function handleRenameLesson(chapterId, lessonId, newTitle) {
      try { await api.put(`/instructor/courses/${courseId}/chapters/${chapterId}/lessons/${lessonId}`, { title: newTitle }); loadCourse(true); } catch (e) { console.error("Rename lesson failed", e); }
    }
    async function handleDeleteChapter(chapterId) {
      showConfirm(
        "Toàn bộ bài giảng và tài liệu trong chương này cũng sẽ bị xóa.",
        async () => {
          try { await api.delete(`/instructor/courses/${courseId}/chapters/${chapterId}`); loadCourse(true); }
          catch (e) { showAlert(e?.response?.data?.message || "Xóa thất bại", { title: "Lỗi" }); }
        },
        { title: "Xóa chương này?", danger: true, confirmLabel: "Xóa chương" }
      );
    }
    async function handleDeleteLesson(chapterId, lessonId) {
      showConfirm(
        "Bài giảng và tài liệu đính kèm sẽ bị xóa.",
        async () => {
          try { await api.delete(`/instructor/courses/${courseId}/chapters/${chapterId}/lessons/${lessonId}`); loadCourse(true); }
          catch (e) { showAlert(e?.response?.data?.message || "Xóa thất bại", { title: "Lỗi" }); }
        },
        { title: "Xóa bài giảng này?", danger: true, confirmLabel: "Xóa bài giảng" }
      );
    }
    async function handleDeleteResource(lessonId, resourceId) {
      showConfirm(
        "Tài liệu sẽ bị xóa khỏi bài giảng này.",
        async () => {
          try { await api.delete(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/${resourceId}`); loadCourse(true); }
          catch (e) { showAlert(e?.response?.data?.message || "Xóa thất bại", { title: "Lỗi" }); }
        },
        { title: "Xóa tài liệu này?", danger: true, confirmLabel: "Xóa" }
      );
    }
    async function handleRenameResource(lessonId, resourceId, newTitle) {
      try { await api.patch(`/instructor/courses/${courseId}/lessons/${lessonId}/resources/${resourceId}`, { displayName: newTitle }); loadCourse(true); }
      catch (e) { showAlert(e?.response?.data?.message || "Đổi tên thất bại", { title: "Lỗi" }); }
    }
    async function handleReorderChapter(fromIndex, toIndex) {
      if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0) return;
      const ids = chapters.map(c => c.id);
      const [moved] = ids.splice(fromIndex, 1);
      ids.splice(toIndex, 0, moved);
      try { await api.put(`/instructor/courses/${courseId}/chapters/reorder`, { ids }); loadCourse(true); }
      catch (e) { showAlert(e?.response?.data?.message || "Sắp xếp lại chương thất bại", { title: "Lỗi" }); }
    }
    async function handleReorderLesson(chapterId, fromIndex, toIndex) {
      const chapter = chapters.find(c => c.id === chapterId);
      if (!chapter || fromIndex === toIndex || fromIndex < 0 || toIndex < 0) return;
      const ids = chapter.items.map(l => l.lessonId);
      const [moved] = ids.splice(fromIndex, 1);
      ids.splice(toIndex, 0, moved);
      try { await api.put(`/instructor/courses/${courseId}/chapters/${chapterId}/lessons/reorder`, { ids }); loadCourse(true); }
      catch (e) { showAlert(e?.response?.data?.message || "Sắp xếp lại bài giảng thất bại", { title: "Lỗi" }); }
    }

    /* guards */
    if (!slug) return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Chi tiết Khóa học</h1></div>
        <Empty icon="book" title="Chưa chọn khóa học" sub="Quay lại danh sách và chọn một khóa học." />
        <button className="btn btn-ghost" style={{ marginTop: 24 }} onClick={() => nav("courses")}><Ic n="arrow_left" size={16} />Quay lại</button>
      </div>
    );
    if (loading) return <div className="page fade-in"><div className="muted" style={{ textAlign: "center", padding: 80 }}>Đang tải...</div></div>;
    if (err) return (
      <div className="page fade-in">
        <div style={{ color: "var(--error)", padding: 24 }}>{err}</div>
        <button className="btn btn-ghost" onClick={() => nav("courses")}><Ic n="arrow_left" size={16} />Quay lại</button>
      </div>
    );

    // PENDING_UPDATE = đang chờ admin duyệt → khóa mọi chỉnh sửa
    const canEdit     = ["DRAFT", "REJECTED", "PUBLISHED"].includes(course?.status);
    const canEditInfo = course?.status !== "ARCHIVED" && course?.status != null;
    const isLive      = course?.status === "PUBLISHED" || course?.status === "PENDING_UPDATE";
    const sc      = STATUS_COLOR[course?.status] || {};

    const allLessons   = chapters.flatMap(ch => ch.items);
    const allResources = allLessons.flatMap(l => l.resources || []);
    const counts = {
      lessons: allLessons.length,
      VIDEO:   allResources.filter(r => r.resourceType === "VIDEO").length,
      doc:     allResources.filter(r => r.resourceType !== "VIDEO").length,
    };

    // True nếu có thay đổi chưa gửi duyệt (draft chapters/lessons hoặc resource flags)
    const hasPendingResourceChanges = allResources.some(r => r.isNewInUpdate || r.pendingDelete);
    const hasPendingChanges = course?.hasPendingDraft || hasPendingResourceChanges;

    return (
      <div className="page fade-in">
        {/* Header */}
        <div className="page-head between" style={{ marginBottom: 20 }}>
          <div className="row gap-10" style={{ alignItems: "center" }}>
            <button className="btn btn-ghost btn-sm" onClick={() => nav("courses")} style={{ gap: 6 }}>
              <Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách khóa học</span>
            </button>

            {/* Version picker dropdown */}
            {versions.length > 0 && (() => {
              const latestApproved = versions.find(v => v.status === "APPROVED");
              const draftVersions  = versions.filter(v => v.status === "DRAFT");
              const otherVersions  = versions.filter(v => v.status !== "DRAFT" && v.status !== "APPROVED");

              // Label & màu cho nút trigger
              const vLabel = viewingVersion === null
                ? "Bản đang chỉnh sửa"
                : viewingVersion.status === "DRAFT"
                  ? (viewingVersion.label || "Bản nháp")
                  : `v${viewingVersion.versionNumber} · ${viewingVersion.status === "APPROVED" ? "Đã duyệt" : viewingVersion.status === "REJECTED" ? "Từ chối" : "Chờ duyệt"}`;

              const vColor = viewingVersion === null
                ? "#64748b"
                : viewingVersion.status === "APPROVED" ? "#16a34a"
                : viewingVersion.status === "DRAFT"    ? "#64748b"
                : viewingVersion.status === "REJECTED" ? "#dc2626" : "#0284c7";

              function PickerItem({ dot, title, sub, active, onClick: oc, badge }) {
                return (
                  <button style={{
                    width: "100%", textAlign: "left", padding: "9px 14px",
                    background: active ? "var(--surface-2)" : "transparent",
                    border: "none", cursor: "pointer", display: "flex", alignItems: "center", gap: 10,
                  }} onClick={oc}>
                    <div style={{ width: 8, height: 8, borderRadius: "50%", background: dot, flex: "none" }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: dot }} className="truncate">{title}</div>
                      <div style={{ fontSize: 11, color: "var(--text-3)" }}>{sub}</div>
                    </div>
                    {badge && <span style={{ fontSize: 10, padding: "1px 6px", borderRadius: 999, background: "#dcfce7", color: "#16a34a", fontWeight: 700, flex: "none" }}>{badge}</span>}
                    {active && <Ic n="check" size={13} style={{ color: dot, flex: "none" }} />}
                  </button>
                );
              }

              return (
                <div style={{ position: "relative" }}>
                  <button
                    className="btn btn-ghost btn-sm"
                    style={{ gap: 6, borderColor: vColor + "60", color: vColor, fontWeight: 600 }}
                    onClick={() => setShowVersionPicker(v => !v)}
                  >
                    <Ic n="clock" size={14} />
                    {vLabel}
                    <Ic n="chevron_down" size={13} style={{ opacity: 0.6 }} />
                  </button>

                  {showVersionPicker && (
                    <>
                      <div style={{ position: "fixed", inset: 0, zIndex: 99 }} onClick={() => setShowVersionPicker(false)} />
                      <div style={{
                        position: "absolute", top: "calc(100% + 6px)", left: 0, zIndex: 100,
                        background: "var(--surface)", border: "1px solid var(--border)",
                        borderRadius: 12, boxShadow: "0 8px 24px rgba(0,0,0,.12)",
                        minWidth: 270, maxHeight: 340, overflowY: "auto", padding: "6px 0",
                      }}>

                        {/* Phiên bản đang publish (APPROVED snapshot mới nhất) */}
                        {latestApproved && (
                          <PickerItem
                            dot="#16a34a"
                            title={`v${latestApproved.versionNumber} · Đang publish`}
                            sub={`Phiên bản live · ${new Date(latestApproved.submittedAt).toLocaleDateString("vi-VN")}`}
                            active={viewingVersion?.id === latestApproved.id}
                            badge="LIVE"
                            onClick={() => { setViewingVersion(latestApproved); setShowVersionPicker(false); setTab("content"); }}
                          />
                        )}

                        {/* Bản đang chỉnh sửa (live draft state) */}
                        <PickerItem
                          dot={hasPendingChanges ? "#f59e0b" : "#64748b"}
                          title="Bản đang chỉnh sửa"
                          sub={hasPendingChanges ? "Có thay đổi chưa lưu thành bản nháp" : "Chưa có thay đổi mới"}
                          active={viewingVersion === null}
                          onClick={() => { setViewingVersion(null); setShowVersionPicker(false); setTab("content"); }}
                        />

                        {/* DRAFT snapshots */}
                        {draftVersions.length > 0 && (
                          <>
                            <div style={{ height: 1, background: "var(--border)", margin: "4px 0" }} />
                            <div style={{ padding: "4px 14px 2px", fontSize: 10.5, fontWeight: 700, color: "var(--text-3)", textTransform: "uppercase", letterSpacing: "0.06em" }}>
                              Bản nháp đã lưu
                            </div>
                            {draftVersions.map(v => (
                              <PickerItem key={v.id}
                                dot="#94a3b8"
                                title={v.label || "Bản nháp chưa đặt tên"}
                                sub={`Nháp · ${new Date(v.submittedAt).toLocaleDateString("vi-VN")}`}
                                active={viewingVersion?.id === v.id}
                                onClick={() => { setViewingVersion(v); setShowVersionPicker(false); setTab("content"); }}
                              />
                            ))}
                          </>
                        )}

                        {/* Các version chính thức khác (PENDING, REJECTED, APPROVED cũ) */}
                        {otherVersions.length > 0 && (
                          <>
                            <div style={{ height: 1, background: "var(--border)", margin: "4px 0" }} />
                            <div style={{ padding: "4px 14px 2px", fontSize: 10.5, fontWeight: 700, color: "var(--text-3)", textTransform: "uppercase", letterSpacing: "0.06em" }}>
                              Lịch sử phiên bản
                            </div>
                            {otherVersions.map(v => {
                              const clr = v.status === "REJECTED" ? "#dc2626" : v.status === "PENDING" ? "#0284c7" : "#16a34a";
                              const lbl = v.status === "REJECTED" ? "Từ chối" : v.status === "PENDING" ? "Chờ duyệt" : "Đã duyệt";
                              return (
                                <PickerItem key={v.id}
                                  dot={clr}
                                  title={`v${v.versionNumber} · ${lbl}`}
                                  sub={new Date(v.submittedAt).toLocaleDateString("vi-VN")}
                                  active={viewingVersion?.id === v.id}
                                  onClick={() => { setViewingVersion(v); setShowVersionPicker(false); setTab("content"); }}
                                />
                              );
                            })}
                          </>
                        )}
                      </div>
                    </>
                  )}
                </div>
              );
            })()}
          </div>
          <div className="row gap-10">
            <button className="btn btn-ghost btn-sm" onClick={() => { window.__previewCourse = { courseId, role: "instructor" }; setShowPreview(true); }}><Ic n="eye" size={15} />Xem trước</button>
            {!viewingVersion && (course?.status === "DRAFT" || course?.status === "REJECTED") && (
              <button className="btn btn-success btn-sm" disabled={submitting} onClick={handleSubmit}><Ic n="send" size={15} />{submitting ? "Đang gửi..." : "Gửi duyệt"}</button>
            )}
            {!viewingVersion && course?.status === "PUBLISHED" && hasPendingChanges && (
              <button className="btn btn-ghost btn-sm" disabled={savingDraft} onClick={() => setShowSaveDraft(true)}
                style={{ gap: 6, borderColor: "#93c5fd", color: "#1d4ed8" }}>
                <Ic n="download" size={15} />{savingDraft ? "Đang lưu..." : "Lưu bản nháp"}
              </button>
            )}
            {!viewingVersion && course?.status === "PUBLISHED" && hasPendingChanges && (
              <button className="btn btn-success btn-sm" disabled={submitting} onClick={handleSubmit}><Ic n="send" size={15} />{submitting ? "Đang gửi..." : "Gửi cập nhật"}</button>
            )}
            {!viewingVersion && course?.status === "PUBLISHED" && hasPendingChanges && (
              <button className="btn btn-ghost btn-sm" disabled={submitting} onClick={handleWithdraw}>Hủy thay đổi</button>
            )}
            {!viewingVersion && (course?.status === "PENDING" || course?.status === "PENDING_UPDATE") && (
              <button className="btn btn-ghost btn-sm" disabled={submitting} onClick={handleWithdraw}>
                {course?.status === "PENDING_UPDATE" ? "Hủy cập nhật" : "Rút duyệt"}
              </button>
            )}
          </div>
        </div>

        {/* Hero card */}
        <div className="card" style={{ overflow: "hidden", marginBottom: 20 }}>
          {/* Thumbnail banner */}
          <div style={{
            height: 150, position: "relative",
            background: course?.thumbnailUrl
              ? `#0f172a url(${course.thumbnailUrl}) center/cover`
              : "linear-gradient(135deg,#1e3a5f,#2563eb)",
          }}>
            {canEditInfo && (
              <button className="btn btn-ghost btn-sm"
                style={{ position: "absolute", right: 14, top: 14, background: "rgba(255,255,255,.92)", gap: 6 }}
                onClick={() => setThumbModalOpen(true)}>
                <Ic n="upload" size={14} />Đổi ảnh bìa
              </button>
            )}
            {thumbUploading && (
              <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,.55)", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 10 }}>
                <div style={{ width: 200, height: 5, borderRadius: 999, background: "rgba(255,255,255,.25)", overflow: "hidden" }}>
                  <div style={{ width: thumbProgress + "%", height: "100%", background: "#fff", transition: "width .2s" }} />
                </div>
                <span style={{ color: "#fff", fontSize: 13, fontWeight: 600 }}>Đang upload... {thumbProgress}%</span>
              </div>
            )}
          </div>

          {/* Title + chips + actions */}
          <div className="card-pad between wrap" style={{ gap: 16 }}>
            <div style={{ minWidth: 0 }}>
              <div className="row gap-10 wrap" style={{ marginBottom: 8 }}>
                <span className="chip" style={{ background: sc.bg, color: sc.color, fontWeight: 600 }}>{STATUS_LABEL[course?.status] || course?.status}</span>
                {course?.category?.name && <span className="chip chip-info">{course.category.name}</span>}
                {course?.level && <span className="chip chip-neutral">{course.level}</span>}
              </div>
              <h1 className="t-h2" style={{ margin: 0 }}>{course?.title}</h1>
              <div className="row gap-16 wrap" style={{ marginTop: 10, color: "var(--text-2)" }}>
                <span className="meta-row"><Ic n="layers" size={15} /> {chapters.length} chương</span>
                <span className="meta-row"><Ic n="book"   size={15} /> {counts.lessons} bài giảng</span>
                <span className="meta-row"><Ic n="video"  size={15} /> {counts.VIDEO} video</span>
                <span className="meta-row"><Ic n="file"   size={15} /> {counts.doc} tài liệu</span>
              </div>
            </div>
          </div>
        </div>

        {/* Banner: draft rejection reason (update bị từ chối) */}
        {!viewingVersion && course?.draftRejectionReason && (
          <div style={{ padding: "12px 16px", borderRadius: 10, marginBottom: 12, fontSize: 13.5, background: "#fee2e2", color: "#dc2626", display: "flex", gap: 10, alignItems: "center" }}>
            <Ic n="x" size={16} style={{ flex: "none" }} />
            <span><strong>Cập nhật bị từ chối:</strong> {course.draftRejectionReason}</span>
          </div>
        )}

        {/* Banner: đang chờ admin duyệt cập nhật — khóa toàn bộ chỉnh sửa */}
        {!viewingVersion && course?.status === "PENDING_UPDATE" && (
          <div style={{ padding: "12px 16px", borderRadius: 10, marginBottom: 12, fontSize: 13.5, background: "#e0f2fe", color: "#0284c7", display: "flex", gap: 10, alignItems: "flex-start" }}>
            <Ic n="clock" size={16} style={{ flex: "none", marginTop: 1 }} />
            <span>
              Cập nhật đang chờ admin duyệt — <strong>chỉnh sửa bị tạm khóa</strong> trong thời gian này.
              Khóa học vẫn hiển thị cho học viên theo nội dung đang live.
              Nếu muốn chỉnh sửa thêm, hãy <strong>Hủy cập nhật</strong> trước, sau đó sửa và gửi lại.
            </span>
          </div>
        )}

        {/* Banner: đang published — không có thay đổi */}
        {!viewingVersion && course?.status === "PUBLISHED" && !hasPendingChanges && (
          <div style={{ padding: "12px 16px", borderRadius: 10, marginBottom: 12, fontSize: 13.5, background: "#f0fdf4", color: "#15803d", display: "flex", gap: 10, alignItems: "center" }}>
            <Ic n="book" size={16} style={{ flex: "none" }} />
            <span>Khóa học đang được xuất bản. Mọi thay đổi sẽ lưu thành bản nháp — bạn có thể xem lại rồi mới <strong>Gửi cập nhật</strong> để admin duyệt.</span>
          </div>
        )}
        {/* Banner: có thay đổi chưa gửi */}
        {!viewingVersion && course?.status === "PUBLISHED" && hasPendingChanges && (
          <div style={{ padding: "12px 16px", borderRadius: 10, marginBottom: 12, fontSize: 13.5, background: "#fef9c3", color: "#854d0e", display: "flex", gap: 10, alignItems: "center" }}>
            <Ic n="edit" size={16} style={{ flex: "none" }} />
            <span>Bạn có thay đổi chưa gửi duyệt. Bấm <strong>Gửi cập nhật</strong> để gửi admin xem xét, hoặc <strong>Hủy thay đổi</strong> để xóa toàn bộ bản nháp.</span>
          </div>
        )}

        {submitMsg && (
          <div style={{ padding: "10px 16px", borderRadius: 10, marginBottom: 14, fontSize: 13.5,
            background: submitMsg.startsWith("Đã") ? "#dcfce7" : "#fee2e2",
            color: submitMsg.startsWith("Đã") ? "#16a34a" : "#dc2626" }}>{submitMsg}</div>
        )}


        {/* Tabs */}
        <div className="toolbar">
          <Tabs items={[
            { v: "content", label: "Nội dung khóa học" },
            { v: "info",    label: "Thông tin" },
            { v: "versions", label: "Phiên bản" },
            { v: "history",  label: "Lịch sử duyệt" },
            { v: "ai-docs",  label: "Tài liệu AI" },
          ]} value={tab} onChange={v => {
            setTab(v);
            if ((v === "versions" || v === "history") && courseId) {
              setHistoryLoading(true);
              Promise.all([
                api.get(`/instructor/courses/${courseId}/history`),
                ...(versions.length === 0 ? [api.get(`/instructor/courses/${courseId}/versions`)] : [Promise.resolve({ data: versions })]),
              ])
                .then(([hRes, vRes]) => {
                  setHistory(hRes.data || []);
                  setVersions(vRes.data || []);
                })
                .catch(() => {})
                .finally(() => setHistoryLoading(false));
            }
          }} />
        </div>

        {/* ── Content tab ── */}
        {tab === "content" && viewingVersion && (() => {
          const snap = (() => { try { return viewingVersion.snapshot ? JSON.parse(viewingVersion.snapshot) : null; } catch { return null; } })();
          const isDraft    = viewingVersion.status === "DRAFT";
          const isApproved = viewingVersion.status === "APPROVED";
          const isPending  = viewingVersion.status === "PENDING";
          const vName = isDraft    ? (viewingVersion.label || "Bản nháp")
                      : isApproved ? `v${viewingVersion.versionNumber} · Đang publish`
                      : `v${viewingVersion.versionNumber}`;
          const vColor = isDraft ? "#64748b" : isApproved ? "#16a34a" : isPending ? "#0284c7" : "#dc2626";
          const vBg    = isDraft ? "#f8fafc"  : isApproved ? "#f0fdf4"  : isPending ? "#e0f2fe"  : "#fee2e2";
          const statusLabel = isDraft    ? "Bản nháp — chỉ xem"
                            : isApproved ? "Phiên bản đang publish — chỉ xem"
                            : isPending  ? "Đang chờ admin duyệt — chỉ xem"
                            : "Phiên bản bị từ chối — chỉ xem";

          return (
            <Section>
              {/* Banner */}
              <div style={{ padding: "10px 16px", borderRadius: 10, marginBottom: 16,
                background: vBg, border: `1.5px solid ${vColor}40`,
                display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                <Ic n={isDraft ? "file" : isApproved ? "check" : isPending ? "clock" : "x"} size={15} style={{ color: vColor, flex: "none" }} />
                <span style={{ fontSize: 13, color: vColor, flex: 1, fontWeight: 500, minWidth: 180 }}>
                  <strong>{vName}</strong>
                  {viewingVersion.submittedAt && <> · {new Date(viewingVersion.submittedAt).toLocaleDateString("vi-VN")}</>}
                  {" "}— {statusLabel}
                </span>
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  {/* Tải để chỉnh sửa — chỉ với DRAFT */}
                  {isDraft && (
                    <button className="btn btn-ghost btn-sm"
                      style={{ fontSize: 12, gap: 5, borderColor: "#64748b50", color: "#475569" }}
                      disabled={rollingBack === viewingVersion.id || savingDraft}
                      onClick={() => handleLoadForEdit(viewingVersion)}>
                      <Ic n="edit" size={13} />
                      {rollingBack === viewingVersion.id ? "Đang tải..." : savingDraft ? "Đang lưu..." : "Tải để chỉnh sửa"}
                    </button>
                  )}
                  {/* Nộp duyệt — chỉ với DRAFT */}
                  {isDraft && (
                    <button className="btn btn-success btn-sm"
                      style={{ fontSize: 12, gap: 5 }}
                      disabled={submittingVersion}
                      onClick={() => handleSubmitVersion(viewingVersion.id, viewingVersion.label || "bản nháp")}>
                      <Ic n="send" size={13} />
                      {submittingVersion ? "Đang nộp..." : "Nộp duyệt"}
                    </button>
                  )}
                  {/* Tạo bản nháp từ phiên bản APPROVED/PENDING/REJECTED */}
                  {!isDraft && (
                    <button className="btn btn-ghost btn-sm"
                      style={{ fontSize: 12, gap: 5, borderColor: vColor + "50", color: vColor }}
                      disabled={cloningDraft}
                      onClick={() => handleCloneAsDraft(viewingVersion.id,
                        isApproved ? `Clone từ v${viewingVersion.versionNumber}` : `Clone · chờ duyệt`)}>
                      <Ic n="download" size={13} />
                      {cloningDraft ? "Đang tạo..." : "Tạo bản nháp từ đây"}
                    </button>
                  )}
                  {/* Xóa cứng — DRAFT hoặc REJECTED */}
                  {(isDraft || viewingVersion.status === "REJECTED") && (
                    <button className="btn btn-ghost btn-sm"
                      style={{ fontSize: 12, gap: 5, borderColor: "#fca5a5", color: "#dc2626" }}
                      disabled={deletingDraft === viewingVersion.id}
                      onClick={() => showConfirm(
                        "Hành động này không thể hoàn tác.",
                        async () => { await deleteVersion(viewingVersion.id); setViewingVersion(null); },
                        { title: "Xóa phiên bản này?", danger: true, confirmLabel: "Xóa" }
                      )}>
                      <Ic n="x" size={13} />
                      {deletingDraft === viewingVersion.id ? "Đang xóa..." : "Xóa phiên bản"}
                    </button>
                  )}
                  <button className="btn btn-ghost btn-sm" style={{ fontSize: 12 }} onClick={() => setViewingVersion(null)}>
                    Về bản đang sửa
                  </button>
                </div>
              </div>

              <h2 className="t-h2" style={{ marginBottom: 14 }}>Chương trình học — {vName}</h2>

              {!snap && <div className="muted" style={{ fontSize: 13.5 }}>Không có dữ liệu snapshot cho phiên bản này.</div>}

              {snap && (snap.chapters || []).map((ch, ci) => (
                <div key={ci} style={{ border: "1px solid var(--border)", borderRadius: 12, marginBottom: 10, overflow: "hidden" }}>
                  <div style={{ padding: "12px 16px", background: "var(--surface-2)", display: "flex", alignItems: "center", gap: 10 }}>
                    <Ic n="layers" size={14} style={{ color: "var(--text-3)" }} />
                    <span style={{ fontWeight: 600, fontSize: 14 }}>{ci + 1}. {ch.title}</span>
                    <span className="muted t-xs" style={{ marginLeft: "auto" }}>{(ch.lessons || []).length} bài</span>
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: 1, background: "var(--border)" }}>
                    {(ch.lessons || []).map((l, li) => {
                      const resources = (l.resources || []).filter(r => !r.pendingDelete);
                      return (
                        <div key={li} style={{ background: "var(--surface)" }}>
                          {/* Lesson row */}
                          <div style={{ padding: "10px 16px", display: "flex", alignItems: "center", gap: 10 }}>
                            <div style={{ width: 32, height: 32, borderRadius: 8,
                              background: l.lessonType === "VIDEO" ? "#1e293b" : "var(--surface-2)",
                              display: "grid", placeItems: "center", flex: "none" }}>
                              <Ic n={l.lessonType === "VIDEO" ? "play" : "file_text"} size={13}
                                style={{ color: l.lessonType === "VIDEO" ? "#fff" : "var(--text-3)" }} />
                            </div>
                            <div style={{ flex: 1, minWidth: 0 }}>
                              <div style={{ fontSize: 13.5, fontWeight: 500 }} className="truncate">{li + 1}. {l.title}</div>
                              <div style={{ fontSize: 11, color: "var(--text-3)", display: "flex", gap: 6, marginTop: 2 }}>
                                <span>{l.lessonType === "VIDEO" ? "Video" : "Văn bản"}</span>
                                {resources.length > 0 && <><span>·</span><Ic n="paperclip" size={10} />{resources.length} tài liệu</>}
                              </div>
                            </div>
                          </div>
                          {/* Resource list */}
                          {resources.length > 0 && (
                            <div style={{ padding: "0 16px 10px 58px", display: "flex", flexDirection: "column", gap: 5 }}>
                              {resources.map((r, ri) => (
                                <div key={ri} style={{ display: "flex", alignItems: "center", gap: 8,
                                  padding: "6px 10px", borderRadius: 8, background: "var(--surface-2)",
                                  border: "1px solid var(--border-soft)" }}>
                                  <Ic n={r.resourceType === "PDF" ? "file_text" : r.resourceType === "VIDEO" ? "play" : "file"}
                                    size={12} style={{ color: "var(--text-3)", flex: "none" }} />
                                  <span style={{ fontSize: 12, flex: 1, minWidth: 0 }} className="truncate">
                                    {r.title || r.displayName || r.originalFilename || "Tài liệu"}
                                  </span>
                                  <span style={{ fontSize: 10.5, color: "var(--text-3)", flex: "none" }}>
                                    {r.resourceType}
                                  </span>
                                  {(() => {
                                    const noKey = !r.s3Key;
                                    const noKeyMsg = "Bản nháp này được lưu trước khi hỗ trợ xem tài liệu. Vui lòng lưu lại bản nháp mới.";
                                    const getViewUrl = async () => {
                                      if (noKey) throw new Error(noKeyMsg);
                                      const res = await api.get(`/instructor/courses/${courseId}/resources/presign-view`, { params: { s3Key: r.s3Key } });
                                      return res.data.url;
                                    };
                                    const getDownUrl = async () => {
                                      if (noKey) throw new Error(noKeyMsg);
                                      const res = await api.get(`/instructor/courses/${courseId}/resources/presign-download`, { params: { s3Key: r.s3Key } });
                                      return res.data.url;
                                    };
                                    const btnBase = { height: 24, borderRadius: 6, cursor: "pointer", flex: "none", display: "flex", alignItems: "center", justifyContent: "center", padding: "0 6px", gap: 3, fontSize: 11, fontWeight: 500, border: "1px solid" };
                                    return (<>
                                      <button title="Xem tài liệu" style={{ ...btnBase, borderColor: "#bae6fd", background: "#f0f9ff", color: "#0284c7" }}
                                        onClick={async () => { try { window.open(await getViewUrl(), "_blank"); } catch (e) { showAlert(e.message || "Không thể xem tài liệu", { title: "Lỗi" }); } }}>
                                        <Ic n="eye" size={11} />Xem
                                      </button>
                                      <button title="Tải tài liệu" style={{ ...btnBase, borderColor: "#bbf7d0", background: "#f0fdf4", color: "#16a34a" }}
                                        onClick={async () => {
                                          try {
                                            const url = await getDownUrl();
                                            const a = document.createElement("a"); a.href = url; a.download = r.displayName || "tai-lieu"; a.target = "_blank"; a.click();
                                          } catch (e) { showAlert(e.message || "Không thể tải tài liệu", { title: "Lỗi" }); }
                                        }}>
                                        <Ic n="download" size={11} />Tải
                                      </button>
                                    </>);
                                  })()}
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}
            </Section>
          );
        })()}

        {tab === "content" && !viewingVersion && (
          <CourseContentTab
            courseId={courseId}
            chapters={chapters}
            canEdit={canEdit}
            open={open}
            setOpen={setOpen}
            openLessons={openLessons}
            toggleLesson={toggleLesson}
            resourceMenu={resourceMenu}
            setResourceMenu={setResourceMenu}
            setPreviewResource={setPreviewResource}
            setAddChapterOpen={setAddChapterOpen}
            setAddLessonState={setAddLessonState}
            setAddResourceState={setAddResourceState}
            setRenameLessonState={setRenameLessonState}
            setEditResourceState={setEditResourceState}
            handleRenameChapter={handleRenameChapter}
            handleRenameLesson={handleRenameLesson}
            handleDeleteChapter={handleDeleteChapter}
            handleDeleteLesson={handleDeleteLesson}
            handleDeleteResource={handleDeleteResource}
            handleReorderChapter={handleReorderChapter}
            handleReorderLesson={handleReorderLesson}
            setChangeQuizState={setChangeQuizState}
          />
        )}

        {/* ── Info tab ── */}
        {tab === "info" && (
          <Section>
            <div className="between" style={{ marginBottom: 20 }}>
              <h2 className="t-h2">Thông tin khóa học</h2>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 18 }}>
              <Field label="Tên khóa học" full>
                <input className="input" value={editTitle} onChange={e => setEditTitle(e.target.value)} disabled={!canEditInfo} />
              </Field>
              <Field label="Danh mục">
                <Select
                  value={editCat}
                  onChange={v => setEditCat(v === "null" ? null : v)}
                  options={[{ v: null, label: "— Chưa chọn —" }, ...categories.map(c => ({ v: c.id, label: c.name }))]}
                  disabled={!canEditInfo}
                />
              </Field>
              <Field label="Cấp độ">
                <Select value={editLevel} onChange={setEditLevel} options={LEVELS.map(l => ({ v: l.value, label: l.label }))} disabled={!canEditInfo} />
              </Field>
              <Field label="Mô tả" full>
                <textarea className="input" style={{ height: 100, padding: 12, resize: "none" }} value={editDesc}
                  onChange={e => setEditDesc(e.target.value)} disabled={!canEditInfo}
                  placeholder="Mô tả ngắn gọn về khóa học..." />
              </Field>
            </div>
            {infoMsg && (
              <div style={{ marginTop: 12, padding: "10px 14px", borderRadius: 10, fontSize: 13.5,
                background: infoMsg.startsWith("Đã") ? "#dcfce7" : "#fee2e2",
                color: infoMsg.startsWith("Đã") ? "#16a34a" : "#dc2626" }}>{infoMsg}
              </div>
            )}
            {canEditInfo && (
              <div className="row gap-10" style={{ marginTop: 20, justifyContent: "flex-end" }}>
                <button className="btn btn-ghost" onClick={() => {
                  setEditTitle(course?.title || "");
                  setEditDesc(course?.description || "");
                  setEditLevel(course?.level || LEVELS[1].value);
                  setEditCat(course?.category?.id || null);
                  setInfoMsg(null);
                }}>Hủy</button>
                <button className="btn btn-primary" disabled={infoSaving} onClick={handleSaveInfo}>{infoSaving ? "Đang lưu..." : "Lưu thay đổi"}</button>
              </div>
            )}
          </Section>
        )}

        {/* ── History tab ── */}
        {tab === "versions" && !viewingVersion && (
          <CourseVersionsTab
            versions={versions}
            course={course}
            hasPendingChanges={hasPendingChanges}
            historyLoading={historyLoading}
            savingDraft={savingDraft}
            rollingBack={rollingBack}
            deletingDraft={deletingDraft}
            renamingVersion={renamingVersion}
            renameInput={renameInput}
            setRenamingVersion={setRenamingVersion}
            setRenameInput={setRenameInput}
            setSnapshotView={setSnapshotView}
            setShowSaveDraft={setShowSaveDraft}
            handleRenameVersion={handleRenameVersion}
            handleDeleteDraft={handleDeleteDraft}
            handleRollback={handleRollback}
            handleWithdraw={handleWithdraw}
            submittingVersion={submittingVersion}
            handleSubmitVersion={handleSubmitVersion}
          />
        )}

        {tab === "history" && (
          <CourseHistoryTab history={history} historyLoading={historyLoading} />
        )}

        {tab === "ai-docs" && (
          <AiDocsTab courseId={courseId} />
        )}

        {/* ── Modal đổi ảnh bìa ── */}
        {thumbModalOpen && (
          <Modal open onClose={() => { setThumbModalOpen(false); setThumbModalFile(null); setThumbModalPreview(null); }} max={520}>
            <ModalHead title="Đổi ảnh bìa" icon="image" iconBg="#eaf1ff" iconColor="#2563eb"
              onClose={() => { setThumbModalOpen(false); setThumbModalFile(null); setThumbModalPreview(null); }} />
            <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <input ref={thumbModalInputRef} type="file" accept="image/jpeg,image/png,image/webp" style={{ display: "none" }}
                onChange={e => {
                  const f = e.target.files?.[0];
                  if (!f) return;
                  if (f.size > 4 * 1024 * 1024) { showAlert("Ảnh bìa vượt quá 4MB", { title: "File quá lớn", type: "warning" }); return; }
                  setThumbModalFile(f);
                  setThumbModalPreview(URL.createObjectURL(f));
                  e.target.value = "";
                }} />

              {/* Preview */}
              <div style={{
                height: 200, borderRadius: 10, overflow: "hidden", position: "relative", cursor: "pointer",
                background: thumbModalPreview
                  ? `url(${thumbModalPreview}) center/cover`
                  : course?.thumbnailUrl
                    ? `#0f172a url(${course.thumbnailUrl}) center/cover`
                    : "linear-gradient(135deg,#1e3a5f,#2563eb)",
              }} onClick={() => thumbModalInputRef.current?.click()}>
                {!thumbModalPreview && (
                  <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 8, background: "rgba(0,0,0,.35)" }}>
                    <Ic n="upload" size={28} style={{ color: "#fff" }} />
                    <span style={{ color: "#fff", fontSize: 13, fontWeight: 600 }}>
                      {course?.thumbnailUrl ? "Nhấn để chọn ảnh mới" : "Nhấn để tải ảnh lên"}
                    </span>
                  </div>
                )}
                {thumbModalPreview && (
                  <div style={{ position: "absolute", right: 10, bottom: 10 }}>
                    <button className="btn btn-ghost btn-sm" style={{ background: "rgba(255,255,255,.9)", gap: 6 }}
                      onClick={e => { e.stopPropagation(); thumbModalInputRef.current?.click(); }}>
                      <Ic n="upload" size={13} />Đổi ảnh khác
                    </button>
                  </div>
                )}
              </div>

              <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                {!thumbModalPreview && (
                  <button className="btn btn-ghost btn-sm" style={{ gap: 6 }} onClick={() => thumbModalInputRef.current?.click()}>
                    <Ic n="upload" size={14} />Chọn ảnh từ máy tính
                  </button>
                )}
                <span className="muted" style={{ fontSize: 11.5 }}>JPG, PNG, WebP · tối đa 4MB · Đề xuất 1280×720</span>
              </div>
            </div>
            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={() => { setThumbModalOpen(false); setThumbModalFile(null); setThumbModalPreview(null); }}>Hủy</button>
              <button className="btn btn-primary" disabled={!thumbModalFile || thumbUploading}
                onClick={async () => {
                  if (!thumbModalFile) return;
                  setThumbUploading(true); setThumbProgress(0);
                  try {
                    const { data } = await api.post(`/instructor/courses/presign-thumbnail?mimeType=${encodeURIComponent(thumbModalFile.type)}`);
                    // XHR mặc định không có timeout — nếu kết nối treo (không byte nào đi tiếp)
                    // promise sẽ không bao giờ resolve/reject, kẹt ở "Đang upload... 0%" mãi mãi.
                    // Đồng hồ "stall" reset mỗi lần có tiến độ mới, chỉ hủy khi thực sự đứng yên.
                    await new Promise<void>((resolve, reject) => {
                      const xhr = new XMLHttpRequest();
                      xhr.open("PUT", data.uploadUrl);
                      xhr.setRequestHeader("Content-Type", thumbModalFile.type);
                      const STALL_TIMEOUT_MS = 30000;
                      let stallTimer: any;
                      const resetStallTimer = () => {
                        clearTimeout(stallTimer);
                        stallTimer = setTimeout(() => {
                          xhr.abort();
                          reject(new Error("Upload bị treo do mất kết nối — vui lòng thử lại."));
                        }, STALL_TIMEOUT_MS);
                      };
                      resetStallTimer();
                      xhr.upload.onprogress = e => {
                        resetStallTimer();
                        if (e.lengthComputable) setThumbProgress(Math.round(e.loaded / e.total * 100));
                      };
                      xhr.onload  = () => { clearTimeout(stallTimer); xhr.status < 300 ? resolve() : reject(new Error("Upload thất bại")); };
                      xhr.onerror = () => { clearTimeout(stallTimer); reject(new Error("Mất kết nối")); };
                      xhr.send(thumbModalFile);
                    });
                    await api.put(`/instructor/courses/${courseId}`, { thumbnailUrl: data.viewUrl });
                    setThumbModalOpen(false); setThumbModalFile(null); setThumbModalPreview(null);
                    loadCourse(true);
                  } catch (e: any) { showAlert(e?.response?.data?.message || e?.message || "Upload thất bại", { title: "Lỗi upload" }); }
                  finally { setThumbUploading(false); }
                }}>
                <Ic n="check" size={15} />{thumbUploading ? `Đang upload... ${thumbProgress}%` : "Xác nhận đổi ảnh"}
              </button>
            </div>
          </Modal>
        )}

        {/* ── Snapshot viewer modal ── */}
        {previewResource && (
          <ResourcePreviewModal
            resource={previewResource}
            courseId={courseId}
            lessonId={previewResource.lessonId}
            onClose={() => setPreviewResource(null)}
          />
        )}

        {snapshotView && (
          <Modal open onClose={() => setSnapshotView(null)} max={680} maxHeight="calc(100vh - 48px)">
            <ModalHead
              title={`Phiên bản v${snapshotView.versionNo}`}
              sub={snapshotView.snapshot ? `${snapshotView.snapshot.title}` : "Không có dữ liệu"}
              icon="clock"
              iconBg="#f0f9ff"
              iconColor="#0284c7"
              onClose={() => setSnapshotView(null)}
            />
            <div className="modal-body" style={{ overflowY: "auto" }}>
              {!snapshotView.snapshot ? (
                <Empty icon="alert_circle" title="Không có dữ liệu" sub="Phiên bản này không có snapshot được lưu." />
              ) : (() => {
                const s = snapshotView.snapshot;
                const LEVEL_LABEL = { BEGINNER: "Cơ bản", INTERMEDIATE: "Trung cấp", ADVANCED: "Nâng cao" };
                const RES_IC = { VIDEO: "video", PDF: "file", DOC: "file", SLIDE: "layers", IMAGE: "image", OTHER: "file" };
                const RES_COLOR = { PDF: "#dc2626", DOC: "#2563eb", SLIDE: "#d97706", IMAGE: "#16a34a", VIDEO: "#7c3aed", OTHER: "#64748b" };

                return (
                  <div>
                    {/* Course meta */}
                    {s.thumbnailUrl && (
                      <div style={{ height: 140, borderRadius: 10, overflow: "hidden", marginBottom: 14, position: "relative" }}>
                        <img src={s.thumbnailUrl} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                        <div style={{ position: "absolute", inset: 0, background: "linear-gradient(to top, rgba(0,0,0,.6) 40%, transparent)" }} />
                        <div style={{ position: "absolute", left: 14, bottom: 12, color: "#fff", fontWeight: 700, fontSize: 15 }}>{s.title}</div>
                      </div>
                    )}

                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 14 }}>
                      {[
                        { label: "Cấp độ",   value: LEVEL_LABEL[s.level] || s.level },
                        { label: "Danh mục", value: s.categoryName || "—" },
                      ].map(({ label, value }) => (
                        <div key={label} style={{ padding: "8px 12px", borderRadius: 9, background: "var(--surface-2)" }}>
                          <div style={{ fontSize: 10.5, color: "var(--text-3)", fontWeight: 600, marginBottom: 2 }}>{label}</div>
                          <div style={{ fontSize: 13, fontWeight: 600 }}>{value}</div>
                        </div>
                      ))}
                    </div>

                    {s.description && (
                      <div style={{ padding: "10px 14px", borderRadius: 9, background: "var(--surface-2)", fontSize: 13, color: "var(--text-2)", lineHeight: 1.65, marginBottom: 14 }}>
                        {s.description}
                      </div>
                    )}

                    {/* Chapters & Lessons */}
                    <div style={{ fontSize: 10.5, fontWeight: 700, color: "var(--text-3)", textTransform: "uppercase", letterSpacing: "0.07em", marginBottom: 8 }}>
                      Nội dung — {(s.chapters || []).length} chương · {(s.chapters || []).reduce((acc, ch) => acc + (ch.lessons || []).length, 0)} bài
                    </div>

                    {(s.chapters || []).map((ch, ci) => (
                      <div key={ci} style={{ marginBottom: 10, borderRadius: 10, overflow: "hidden", border: "1px solid var(--border)" }}>
                        {/* Chapter header */}
                        <div style={{ padding: "8px 14px", background: "var(--surface-2)", display: "flex", alignItems: "center", gap: 8 }}>
                          <Ic n="layers" size={13} style={{ color: "var(--text-3)" }} />
                          <span style={{ fontWeight: 700, fontSize: 13 }}>{ci + 1}. {ch.title}</span>
                          <span style={{ marginLeft: "auto", fontSize: 11, color: "var(--text-3)" }}>{(ch.lessons || []).length} bài</span>
                        </div>
                        {/* Lessons */}
                        <div style={{ display: "flex", flexDirection: "column", gap: 1, background: "var(--border)" }}>
                          {(ch.lessons || []).map((l, li) => {
                            const isVid = l.lessonType === "VIDEO";
                            return (
                              <div key={li} style={{ background: "var(--surface)", padding: "8px 14px" }}>
                                <div style={{ display: "flex", alignItems: "center", gap: 9 }}>
                                  <div style={{ width: 28, height: 20, borderRadius: 4, background: isVid ? "#1e293b" : "var(--surface-2)", display: "grid", placeItems: "center", flex: "none" }}>
                                    <Ic n={isVid ? "play" : "file_text"} size={10} style={{ color: isVid ? "#fff" : "var(--text-3)" }} />
                                  </div>
                                  <div style={{ flex: 1, minWidth: 0 }}>
                                    <div style={{ fontSize: 12.5, fontWeight: 500 }} className="truncate">{l.title}</div>
                                    <div style={{ fontSize: 11, color: "var(--text-3)", display: "flex", gap: 6, marginTop: 1 }}>
                                      {isVid ? "Video" : "Văn bản"}
                                      {l.durationSeconds ? <><span>·</span>{Math.floor(l.durationSeconds / 60)}:{String(l.durationSeconds % 60).padStart(2, "0")}</> : null}
                                      {(l.resources || []).length > 0 && <><span>·</span><Ic n="paperclip" size={9} />{l.resources.length} tài liệu</>}
                                    </div>
                                  </div>
                                </div>
                                {/* Resources */}
                                {(l.resources || []).length > 0 && (
                                  <div style={{ marginTop: 6, paddingLeft: 37, display: "flex", flexDirection: "column", gap: 4 }}>
                                    {l.resources.map((r, ri) => (
                                      <div key={ri} style={{ display: "flex", alignItems: "center", gap: 7, padding: "4px 8px", borderRadius: 6, background: "var(--surface-2)" }}>
                                        <Ic n={RES_IC[r.resourceType] || "file"} size={11} style={{ color: RES_COLOR[r.resourceType] || "#64748b", flex: "none" }} />
                                        <span style={{ fontSize: 11.5, flex: 1 }} className="truncate">{r.displayName}</span>
                                        <span style={{ fontSize: 10.5, color: "var(--text-3)" }}>
                                          {r.resourceType}{r.fileSizeBytes ? ` · ${(r.fileSizeBytes / 1024 / 1024).toFixed(1)}MB` : ""}
                                        </span>
                                      </div>
                                    ))}
                                  </div>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    ))}
                  </div>
                );
              })()}
            </div>
          </Modal>
        )}

        {/* Modal lưu bản nháp */}
        {showSaveDraft && (
          <Modal open onClose={() => setShowSaveDraft(false)} max={420}>
            <ModalHead title="Lưu bản nháp" icon="download" iconBg="#eff6ff" iconColor="#1d4ed8" onClose={() => setShowSaveDraft(false)} />
            <div className="modal-body">
              <label className="t-label" style={{ display: "block", marginBottom: 7 }}>
                Tên bản nháp <span className="muted">(tuỳ chọn)</span>
              </label>
              <input className="input" placeholder="VD: Thêm bài thực hành tuần 3" autoFocus
                value={draftLabel} onChange={e => setDraftLabel(e.target.value)}
                onKeyDown={e => { if (e.key === "Enter") handleSaveDraft(draftLabel); }} />
              <div className="muted" style={{ fontSize: 12, marginTop: 8 }}>
                Bạn có thể lưu tối đa 3 bản nháp. Bản nháp không gửi cho admin, chỉ lưu tiến độ.
              </div>
            </div>
            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={() => setShowSaveDraft(false)}>Hủy</button>
              <button className="btn btn-primary" disabled={savingDraft} onClick={() => handleSaveDraft(draftLabel)}>
                <Ic n="download" size={15} />{savingDraft ? "Đang lưu..." : "Lưu bản nháp"}
              </button>
            </div>
          </Modal>
        )}

        {/* Modals */}
        <AddChapterModal   open={addChapterOpen}     onClose={() => setAddChapterOpen(false)}   courseId={courseId} onAdded={() => loadCourse(true)} />
        <AddLessonModal    open={!!addLessonState}   onClose={() => setAddLessonState(null)}    courseId={courseId} chapterId={addLessonState?.chapterId} onAdded={() => loadCourse(true)} />
        <AddResourceModal  open={!!addResourceState} onClose={() => setAddResourceState(null)}  courseId={courseId} lessonId={addResourceState?.lessonId} lessonTitle={addResourceState?.lessonTitle} onAdded={() => loadCourse(true)} />
        <ChangeQuizModal   open={!!changeQuizState}  onClose={() => setChangeQuizState(null)}   courseId={courseId} chapterId={changeQuizState?.chapterId} lessonId={changeQuizState?.lessonId} currentQuizId={changeQuizState?.currentQuizId} onChanged={() => loadCourse(true)} />

        {showPreview && React.createElement(window.PreviewPlayer, { onBack: () => setShowPreview(false) })}

        <ConfirmModal
          open={!!confirmState}
          onClose={() => setConfirmState(null)}
          onConfirm={confirmState?.onConfirm}
          title={confirmState?.title}
          message={confirmState?.message}
          confirmLabel={confirmState?.confirmLabel}
          danger={confirmState?.danger}
        />
        <AlertModal
          open={!!alertState}
          onClose={() => setAlertState(null)}
          title={alertState?.title}
          message={alertState?.message}
          type={alertState?.type}
        />

        {renameLessonState && (() => {
          const s = renameLessonState;
          let name = s.title;
          return (
            <Modal open onClose={() => setRenameLessonState(null)} max={420}>
              <ModalHead title="Đổi tên bài giảng" icon="edit" iconBg="#f0fdf4" iconColor="#16a34a" onClose={() => setRenameLessonState(null)} />
              <div className="modal-body">
                <label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên bài giảng</label>
                <input className="input" defaultValue={s.title} autoFocus
                  onChange={e => { name = e.target.value; }}
                  onKeyDown={e => { if (e.key === "Enter") { handleRenameLesson(s.chapterId, s.lessonId, name); setRenameLessonState(null); } }} />
              </div>
              <div className="modal-foot">
                <button className="btn btn-ghost" onClick={() => setRenameLessonState(null)}>Hủy</button>
                <button className="btn btn-primary" onClick={() => { handleRenameLesson(s.chapterId, s.lessonId, name); setRenameLessonState(null); }}>
                  <Ic n="check" size={15} />Lưu
                </button>
              </div>
            </Modal>
          );
        })()}

        {editResourceState && (() => {
          const s   = editResourceState;
          const rm  = RES_TYPE[s.resourceType] || RES_TYPE.OTHER;
          const isVideo = s.resourceType === "VIDEO";
          const isImage = s.resourceType === "IMAGE";
          const isYT = !!(s.externalUrl && /youtube\.com|youtu\.be/.test(s.externalUrl));
          const ytId = isYT ? (s.externalUrl.match(/(?:v=|youtu\.be\/)([^&?/]+)/)?.[1] || "") : null;
          return (
            <EditResourceInner
              s={s} rm={rm} isVideo={isVideo} isImage={isImage} isYT={isYT} ytId={ytId}
              onClose={() => setEditResourceState(null)}
              onSave={newName => { handleRenameResource(s.lessonId, s.resourceId, newName); setEditResourceState(null); }}
              courseId={courseId}
              onReplaced={() => { loadCourse(true); setEditResourceState(null); }}
            />
          );
        })()}

        {/* Modal: chọn bản nháp để xóa khi đã đủ 3 bản */}
        {showReplaceDraft && loadDraftTarget && (() => {
          const draftVersions = versions.filter(v => v.status === "DRAFT");
          return (
            <Modal open onClose={() => { setShowReplaceDraft(false); setLoadDraftTarget(null); }} max={460}>
              <ModalHead title="Đã đủ 3 bản nháp" icon="alert-triangle" iconBg="#fff7ed" iconColor="#ea580c"
                onClose={() => { setShowReplaceDraft(false); setLoadDraftTarget(null); }} />
              <div className="modal-body">
                <p style={{ fontSize: 13, color: "#475569", marginBottom: 12 }}>
                  Bản đang chỉnh sửa có thay đổi chưa lưu nhưng đã đủ 3 bản nháp. Chọn một bản nháp để xóa và thay bằng bản đang chỉnh sửa:
                </p>
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {draftVersions.map(v => (
                    <button key={v.id} className="btn btn-ghost"
                      style={{ justifyContent: "flex-start", gap: 8, fontSize: 13, padding: "8px 12px", borderRadius: 8 }}
                      disabled={deletingDraft === v.id || savingDraft}
                      onClick={async () => {
                        setDeletingDraft(v.id);
                        try {
                          await api.delete(`/instructor/courses/${courseId}/versions/${v.id}/draft`);
                          setVersions(prev => prev.filter(x => x.id !== v.id));
                          // Lưu bản đang sửa
                          setSavingDraft(true);
                          const autoLabel = `Auto-save trước khi tải "${loadDraftTarget.label || "bản nháp"}"`;
                          const res = await api.post(`/instructor/courses/${courseId}/versions/save-draft?label=${encodeURIComponent(autoLabel)}`);
                          setVersions(prev => [res.data, ...prev]);
                          setSavingDraft(false);
                          setShowReplaceDraft(false);
                          // Rollback
                          setRollingBack(loadDraftTarget.id);
                          await api.post(`/instructor/courses/${courseId}/versions/${loadDraftTarget.id}/rollback`);
                          setViewingVersion(null);
                          loadCourse(true);
                          setTab("content");
                        } catch (e) {
                          showAlert(e?.response?.data?.message || "Thao tác thất bại", { title: "Lỗi" });
                        } finally {
                          setDeletingDraft(null);
                          setSavingDraft(false);
                          setRollingBack(null);
                          setLoadDraftTarget(null);
                        }
                      }}>
                      <Ic n="trash-2" size={14} style={{ color: "#dc2626" }} />
                      <span style={{ flex: 1, textAlign: "left" }}>
                        <strong>{v.label || "Bản nháp"}</strong>
                        {v.createdAt && <span style={{ color: "#94a3b8", marginLeft: 8, fontSize: 12 }}>
                          {new Date(v.createdAt).toLocaleDateString("vi-VN")}
                        </span>}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
              <div className="modal-foot">
                <button className="btn btn-ghost" onClick={() => { setShowReplaceDraft(false); setLoadDraftTarget(null); }}>Hủy</button>
              </div>
            </Modal>
          );
        })()}
      </div>
    );
  }

  Object.assign(window, { InsCourseDetail });
})();
