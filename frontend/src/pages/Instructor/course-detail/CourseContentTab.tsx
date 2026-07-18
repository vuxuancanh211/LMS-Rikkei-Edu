// @ts-nocheck
(function () {
  const { useState } = React;
  const Ic  = window.Icon;
  const { Section, Empty } = window;
  const api = window.httpClient;

  const RES_TYPE = {
    VIDEO: { ic: "video",  bg: "#eaf1ff", fg: "#2563eb" },
    PDF:   { ic: "file",   bg: "#fdecec", fg: "#dc2626" },
    DOC:   { ic: "file",   bg: "#eaf1ff", fg: "#2563eb" },
    SLIDE: { ic: "layers", bg: "#fef5e6", fg: "#d97706" },
    IMAGE: { ic: "image",  bg: "#f0fdf4", fg: "#16a34a" },
    OTHER: { ic: "file",   bg: "#f1f5f9", fg: "#475569" },
  };

  const LESSON_TYPE_BADGE = {
    VIDEO: { bg: "#eaf1ff", fg: "#2563eb", ic: "video" },
    QUIZ:  { bg: "#f3e8ff", fg: "#7e22ce", ic: "clipboard" },
    OTHER: { bg: "#f0fdf4", fg: "#16a34a", ic: "book" },
  };
  function lessonBadge(lesson) {
    if (lesson.lessonType === "QUIZ") return LESSON_TYPE_BADGE.QUIZ;
    if ((lesson.resources || []).some(r => r.resourceType === "VIDEO")) return LESSON_TYPE_BADGE.VIDEO;
    return LESSON_TYPE_BADGE.OTHER;
  }

  function InlineEdit({ value, onSave, placeholder }: any) {
    const [editing, setEditing] = useState(false);
    const [val, setVal]         = useState(value);
    if (!editing) return (
      <span style={{ cursor: "text" }} onDoubleClick={() => { setVal(value); setEditing(true); }}>{value || <span style={{ color: "var(--text-3)" }}>{placeholder}</span>}</span>
    );
    return (
      <input autoFocus className="input" style={{ padding: "2px 8px", fontSize: "inherit", height: 28, minWidth: 120 }}
        value={val} onChange={e => setVal(e.target.value)}
        onBlur={() => { setEditing(false); if (val.trim() && val.trim() !== value) onSave(val.trim()); }}
        onKeyDown={e => {
          if (e.key === "Enter") { setEditing(false); if (val.trim() && val.trim() !== value) onSave(val.trim()); }
          if (e.key === "Escape") { setEditing(false); setVal(value); }
        }}
      />
    );
  }

  /**
   * Tab "Nội dung khóa học" — live editing view.
   *
   * Props:
   *   courseId, chapters, canEdit, open, setOpen,
   *   openLessons, toggleLesson, resourceMenu, setResourceMenu,
   *   setPreviewResource, setAddChapterOpen, setAddLessonState,
   *   setAddResourceState, setRenameLessonState, setEditResourceState,
   *   handleRenameChapter, handleRenameLesson,
   *   handleDeleteChapter, handleDeleteLesson, handleDeleteResource
   */
  function CourseContentTab({
    courseId, chapters, canEdit, open, setOpen,
    openLessons, toggleLesson,
    resourceMenu, setResourceMenu,
    setPreviewResource,
    setAddChapterOpen, setAddLessonState, setAddResourceState,
    setRenameLessonState, setEditResourceState,
    handleRenameChapter, handleRenameLesson,
    handleDeleteChapter, handleDeleteLesson, handleDeleteResource,
    handleReorderChapter, handleReorderLesson,
    setChangeQuizState,
  }: any) {
    // Kéo-thả sắp xếp lại chương/bài giảng — chỉ 1 thứ đang kéo tại 1 thời điểm
    const [dragChapterIdx, setDragChapterIdx] = useState(null);
    const [dragOverChapterIdx, setDragOverChapterIdx] = useState(null);
    const [dragLesson, setDragLesson] = useState(null); // { chapterId, index }
    const [dragOverLesson, setDragOverLesson] = useState(null); // { chapterId, index }
    const [search, setSearch] = useState("");

    const allLessons = chapters.flatMap((ch: any) => ch.items || []);
    const allResources = allLessons.flatMap((l: any) => l.resources || []);
    const stats = {
      chapters: chapters.length,
      lessons: allLessons.length,
      video: allResources.filter((r: any) => r.resourceType === "VIDEO").length,
      pdf: allResources.filter((r: any) => r.resourceType === "PDF").length,
      doc: allResources.filter((r: any) => r.resourceType === "DOC" || r.resourceType === "SLIDE").length,
    };
    const emptyChapters = chapters.filter((ch: any) => (ch.items || []).length === 0).length;
    const emptyLessons = allLessons.filter((l: any) => l.lessonType !== "QUIZ" && !(l.resources || []).length).length;
    const publishIssues = emptyChapters + emptyLessons;

    const q = search.trim().toLowerCase();
    function chapterMatches(ch: any) {
      if (!q) return true;
      if (ch.name?.toLowerCase().includes(q)) return true;
      return (ch.items || []).some((l: any) => l.title?.toLowerCase().includes(q));
    }
    function lessonMatches(l: any) {
      if (!q) return true;
      return l.title?.toLowerCase().includes(q);
    }

    return (
      <Section>
        <div className="between wrap" style={{ marginBottom: 12, rowGap: 8 }}>
          <div>
            <h2 className="t-h2" style={{ marginBottom: 8 }}>Chương trình học</h2>
            {chapters.length > 0 && (
              <div className="row gap-14 wrap t-xs muted">
                <span className="row gap-5"><Ic n="layers" size={13} />{stats.chapters} chương</span>
                <span className="row gap-5"><Ic n="book" size={13} />{stats.lessons} bài giảng</span>
                {stats.video > 0 && <span style={{ color: "#2563eb" }}>{stats.video} video</span>}
                {stats.pdf > 0   && <span style={{ color: "#dc2626" }}>{stats.pdf} PDF</span>}
                {stats.doc > 0   && <span style={{ color: "#1d4ed8" }}>{stats.doc} tài liệu</span>}
              </div>
            )}
          </div>
          {canEdit && (
            <button className="btn btn-ghost btn-sm" onClick={() => setAddChapterOpen(true)}>
              <Ic n="plus" size={15} />Thêm chương
            </button>
          )}
        </div>

        {canEdit && publishIssues > 0 && (
          <div className="row gap-10" style={{ padding: "10px 14px", borderRadius: 10, marginBottom: 14, background: "#fef9c3", color: "#854d0e" }}>
            <Ic n="warn" size={15} style={{ flex: "none" }} />
            <span className="t-xs">{publishIssues} chương/bài giảng chưa có nội dung — hãy bổ sung trước khi gửi duyệt.</span>
          </div>
        )}

        {chapters.length > 0 && (
          <div className="field-icon" style={{ marginBottom: 14 }}>
            <Ic n="search" />
            <input className="input" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm chương/bài giảng theo tên..." />
          </div>
        )}

        {chapters.length === 0 && (
          <div className="card card-pad" style={{ textAlign: "center", padding: "40px 24px" }}>
            <div className="row gap-28" style={{ justifyContent: "center", marginBottom: 20 }}>
              {["Thêm chương", "Thêm bài giảng", "Tải video/tài liệu"].map((step, i) => (
                <div key={step} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, maxWidth: 130 }}>
                  <div style={{ width: 32, height: 32, borderRadius: 999, background: "var(--accent-soft)", color: "var(--accent)",
                    fontWeight: 700, fontSize: 13, display: "flex", alignItems: "center", justifyContent: "center" }}>{i + 1}</div>
                  <span className="t-xs muted">{step}</span>
                </div>
              ))}
            </div>
            <div className="t-sm muted" style={{ marginBottom: 16 }}>Chưa có chương nào. Bắt đầu xây dựng chương trình học của bạn.</div>
            {canEdit && (
              <button className="btn btn-primary btn-sm" onClick={() => setAddChapterOpen(true)}>
                <Ic n="plus" size={14} />Thêm chương
              </button>
            )}
          </div>
        )}

        {chapters.map((ch: any, ci: number) => (
          <div key={ch.id}
            onDragOver={e => { if (dragChapterIdx !== null) { e.preventDefault(); if (dragOverChapterIdx !== ci) setDragOverChapterIdx(ci); } }}
            onDragLeave={() => { if (dragOverChapterIdx === ci) setDragOverChapterIdx(null); }}
            onDrop={e => {
              e.preventDefault();
              if (dragChapterIdx !== null && dragChapterIdx !== ci) handleReorderChapter(dragChapterIdx, ci);
              setDragChapterIdx(null); setDragOverChapterIdx(null);
            }}
            style={{
              display: chapterMatches(ch) ? undefined : "none",
              border: dragOverChapterIdx === ci ? "1px solid var(--accent)" : "1px solid var(--border)", borderRadius: 12, marginBottom: 10,
              opacity: ch.pendingDelete ? 0.55 : dragChapterIdx === ci ? 0.4 : 1,
              outline: ch.isDraft ? "2px solid #22c55e" : ch.pendingDelete ? "2px solid #ef4444" : "none",
              transition: "opacity .12s, border-color .12s",
            }}>
            {/* Chapter header */}
            <div className="row gap-12" style={{ padding: "12px 16px", background: "var(--surface-2)", cursor: "pointer", userSelect: "none" }}
              onClick={() => setOpen(open === ci ? -1 : ci)}>
              <Ic n="chevron_down" size={18} style={{ transform: open === ci ? "none" : "rotate(-90deg)", transition: ".2s", color: "var(--text-3)", flexShrink: 0 }} />
              <div style={{ width: 26, height: 26, borderRadius: "50%", background: "var(--primary)", color: "#fff",
                fontSize: 11.5, fontWeight: 700, display: "flex", alignItems: "center", justifyContent: "center", flex: "none" }}>{ci + 1}</div>
              <div className="grow" style={{ fontWeight: 600, fontSize: 14.5, display: "flex", alignItems: "center", gap: 8 }}>
                {canEdit
                  ? <InlineEdit value={ch.name} onSave={(t: string) => handleRenameChapter(ch.id, t)} placeholder="Tên chương..." />
                  : ch.name}
                {ch.isDraft       && <span className="chip" style={{ fontSize: 10.5, padding: "1px 7px", background: "#dcfce7", color: "#16a34a", fontWeight: 700, flex: "none" }}>MỚI</span>}
                {ch.pendingDelete && <span className="chip" style={{ fontSize: 10.5, padding: "1px 7px", background: "#fee2e2", color: "#dc2626", fontWeight: 700, flex: "none" }}>Chờ xóa</span>}
                {ch.items.length === 0 && !ch.pendingDelete && <span className="chip" style={{ fontSize: 10, padding: "1px 7px", background: "#fef9c3", color: "#a16207", fontWeight: 700, flex: "none" }}>Chưa có bài giảng</span>}
              </div>
              <span className="muted t-xs">{ch.items.length} bài giảng</span>
              {canEdit && (
                <div className="row gap-4" onClick={e => e.stopPropagation()}>
                  <button className="btn btn-ghost btn-sm" style={{ height: 30, padding: "0 10px", fontSize: 12 }}
                    onClick={() => setAddLessonState({ chapterId: ch.id })}>
                    <Ic n="plus" size={13} />Bài giảng
                  </button>
                  <div draggable
                    onDragStart={e => { setDragChapterIdx(ci); e.dataTransfer.effectAllowed = "move"; }}
                    onDragEnd={() => { setDragChapterIdx(null); setDragOverChapterIdx(null); }}
                    style={{ width: 30, height: 30, display: "flex", alignItems: "center", justifyContent: "center", cursor: "grab", color: "var(--text-3)" }}
                    title="Kéo để sắp xếp lại">
                    <Ic n="menu" size={16} />
                  </div>
                  <button className="icon-btn" style={{ width: 34, height: 34, color: "var(--error)" }}
                    onClick={() => handleDeleteChapter(ch.id)}>
                    <Ic n="x" size={15} />
                  </button>
                </div>
              )}
            </div>

            {open === ci && (
              <div style={{ padding: "8px 0" }}>
                {ch.items.length === 0 && (
                  <div className="muted t-xs" style={{ padding: "12px 24px" }}>Chưa có bài giảng nào.</div>
                )}
                {ch.items.map((lesson: any, li: number) => {
                  const videoCount   = (lesson.resources || []).filter(r => r.resourceType === "VIDEO").length;
                  const docCount     = (lesson.resources || []).filter(r => r.resourceType !== "VIDEO").length;
                  const lessonOpen   = openLessons[lesson.lessonId] !== false;
                  const isQuiz       = lesson.lessonType === "QUIZ";
                  const hasResources = !isQuiz && lesson.resources?.length > 0;
                  const isDraggingThis = dragLesson?.chapterId === ch.id && dragLesson?.index === li;
                  const isDragOverThis = dragOverLesson?.chapterId === ch.id && dragOverLesson?.index === li;
                  const badge = lessonBadge(lesson);
                  return (
                    <div key={lesson.lessonId}
                      onDragOver={e => {
                        if (dragLesson?.chapterId !== ch.id) return;
                        e.preventDefault();
                        if (!isDragOverThis) setDragOverLesson({ chapterId: ch.id, index: li });
                      }}
                      onDragLeave={() => { if (isDragOverThis) setDragOverLesson(null); }}
                      onDrop={e => {
                        e.preventDefault();
                        if (dragLesson?.chapterId === ch.id && dragLesson.index !== li) handleReorderLesson(ch.id, dragLesson.index, li);
                        setDragLesson(null); setDragOverLesson(null);
                      }}
                      style={{
                      display: lessonMatches(lesson) ? undefined : "none",
                      margin: "0 10px 8px",
                      border: isDragOverThis ? "1px solid var(--accent)" : lesson.pendingDelete ? "1px dashed #fca5a5" : lesson.isDraft ? "1px solid #86efac" : "1px solid var(--border-soft, #e5e7eb)",
                      borderRadius: 10,
                      opacity: lesson.pendingDelete ? 0.6 : isDraggingThis ? 0.4 : 1,
                      background: "var(--surface)",
                      transition: "opacity .12s, border-color .12s",
                    }}>
                      {/* Lesson header */}
                      <div className="row gap-12" style={{
                        padding: "9px 12px 9px 14px",
                        cursor: hasResources ? "pointer" : "default",
                        userSelect: "none",
                        background: lesson.isDraft ? "rgba(34,197,94,.04)" : lesson.pendingDelete ? "rgba(239,68,68,.03)" : "var(--surface-2)",
                        borderBottom: lessonOpen && hasResources ? "1px solid var(--border-soft, #e5e7eb)" : "none",
                      }} onClick={() => hasResources && toggleLesson(lesson.lessonId)}>
                        {hasResources
                          ? <Ic n="chevron_down" size={15} style={{ transform: lessonOpen ? "none" : "rotate(-90deg)", transition: ".18s", color: "var(--text-3)", flex: "none" }} />
                          : <div style={{ width: 15, flex: "none" }} />}
                        <div className="stat-ic" style={{ width: 34, height: 34, borderRadius: 8,
                          background: badge.bg, color: badge.fg, flex: "none" }}>
                          <Ic n={badge.ic} size={15} />
                        </div>
                        <div className="grow" style={{ minWidth: 0 }}>
                          <div style={{ fontWeight: 500, fontSize: 13.5, display: "flex", alignItems: "center", gap: 7 }} className="truncate"
                            onClick={e => e.stopPropagation()}>
                            {canEdit
                              ? <InlineEdit value={lesson.title} onSave={(t: string) => handleRenameLesson(ch.id, lesson.lessonId, t)} />
                              : <span style={{ textDecoration: lesson.pendingDelete ? "line-through" : "none" }}>{lesson.title}</span>}
                            {lesson.isDraft       && <span className="chip" style={{ fontSize: 10, padding: "1px 6px", background: "#dcfce7", color: "#16a34a", fontWeight: 700, flex: "none" }}>MỚI</span>}
                            {lesson.pendingDelete && <span className="chip" style={{ fontSize: 10, padding: "1px 6px", background: "#fee2e2", color: "#dc2626", fontWeight: 700, flex: "none" }}>Chờ xóa</span>}
                          </div>
                          {lesson.draftTitle && (
                            <div className="t-xs" style={{ marginTop: 1, color: "#0284c7" }}>
                              Tên mới (chờ duyệt): <strong>{lesson.draftTitle}</strong>
                            </div>
                          )}
                          <div className="row gap-6 wrap" style={{ marginTop: 2 }}>
                            {isQuiz ? (
                              <>
                                <span className="chip" style={{ background: "#eaf1ff", color: "#2563eb", fontSize: 10.5, padding: "1px 7px" }}>Đề trắc nghiệm</span>
                                <span className={`chip ${lesson.quizStatus === "PUBLISHED" ? "" : ""}`}
                                  style={{ fontSize: 10.5, padding: "1px 7px",
                                    background: lesson.quizStatus === "PUBLISHED" ? "#dcfce7" : "#fef3c7",
                                    color: lesson.quizStatus === "PUBLISHED" ? "#16a34a" : "#b45309" }}>
                                  {lesson.quizStatus === "PUBLISHED" ? "Đã xuất bản" : "Nháp"}
                                </span>
                                {lesson.quizTitle && <span className="muted t-xs truncate" style={{ maxWidth: 180 }}>{lesson.quizTitle}</span>}
                              </>
                            ) : (
                              <>
                                <span className="chip" style={{ background: "#f0fdf4", color: "#16a34a", fontSize: 10.5, padding: "1px 7px" }}>Bài giảng</span>
                                {videoCount > 0 && <span className="muted t-xs">{videoCount} video</span>}
                                {docCount > 0   && <span className="muted t-xs">{docCount} tài liệu</span>}
                                {lesson.dur     && <span className="muted t-xs">{lesson.dur}</span>}
                                {videoCount === 0 && docCount === 0 && (
                                  <span className="chip" style={{ fontSize: 10, padding: "1px 7px", background: "#fef9c3", color: "#a16207", fontWeight: 700 }}>Chưa có nội dung</span>
                                )}
                              </>
                            )}
                          </div>
                        </div>
                        <div className="row gap-4" onClick={e => e.stopPropagation()}>
                          {canEdit && <>
                            <div draggable
                              onDragStart={e => { setDragLesson({ chapterId: ch.id, index: li }); e.dataTransfer.effectAllowed = "move"; }}
                              onDragEnd={() => { setDragLesson(null); setDragOverLesson(null); }}
                              style={{ width: 30, height: 30, display: "flex", alignItems: "center", justifyContent: "center", cursor: "grab", color: "var(--text-3)" }}
                              title="Kéo để sắp xếp lại">
                              <Ic n="menu" size={14} />
                            </div>
                            <button className="icon-btn" style={{ width: 30, height: 30 }} title="Đổi tên bài giảng"
                              onClick={() => setRenameLessonState({ chapterId: ch.id, lessonId: lesson.lessonId, title: lesson.title })}>
                              <Ic n="edit" size={14} />
                            </button>
                            {isQuiz ? (
                              <>
                                <button className="icon-btn" style={{ width: 30, height: 30, color: "#2563eb" }} title="Soạn câu hỏi cho đề này"
                                  onClick={() => { if (window.AppShell?.go) window.AppShell.go('assess', { courseId, quizId: lesson.quizId }); else window.location.href = `/instructor/assess?courseId=${courseId}&quizId=${lesson.quizId}`; }}>
                                  <Ic n="file_text" size={14} />
                                </button>
                                <button className="icon-btn" style={{ width: 30, height: 30, color: "#d97706" }} title="Đổi đề trắc nghiệm"
                                  onClick={() => setChangeQuizState({ chapterId: ch.id, lessonId: lesson.lessonId, currentQuizId: lesson.quizId })}>
                                  <Ic n="rotate_ccw" size={14} />
                                </button>
                              </>
                            ) : (
                              <button className="icon-btn" style={{ width: 30, height: 30, color: "#16a34a" }} title="Thêm nội dung"
                                onClick={() => setAddResourceState({ lessonId: lesson.lessonId, lessonTitle: lesson.title })}>
                                <Ic n="plus" size={14} />
                              </button>
                            )}
                            <button className="icon-btn" style={{ width: 30, height: 30, color: "var(--error)" }} title="Xóa bài giảng"
                              onClick={() => handleDeleteLesson(ch.id, lesson.lessonId)}>
                              <Ic n="x" size={14} />
                            </button>
                          </>}
                        </div>
                      </div>

                      {/* Resources list */}
                      {hasResources && lessonOpen && (
                        <div>
                          {lesson.resources.map((r: any, ri: number) => {
                            const rm     = RES_TYPE[r.resourceType] || RES_TYPE.OTHER;
                            const rIsDel = !!r.pendingDelete;
                            const rIsNew = !!r.isNewInUpdate;
                            const menuOpen = resourceMenu?.resourceId === r.resourceId;
                            return (
                              <div key={r.resourceId} className="row gap-10" style={{
                                padding: "7px 12px",
                                borderTop: ri === 0 ? "none" : "1px solid var(--border-soft, #f0f0f0)",
                                background: rIsDel ? "rgba(239,68,68,.04)" : rIsNew ? "rgba(34,197,94,.04)" : "transparent",
                                opacity: rIsDel ? 0.72 : 1,
                              }}>
                                <div style={{ width: 28, height: 28, borderRadius: 7, background: rm.bg, color: rm.fg, flex: "none", display: "flex", alignItems: "center", justifyContent: "center" }}>
                                  <Ic n={rm.ic} size={13} />
                                </div>
                                <div className="grow" style={{ minWidth: 0 }}>
                                  <div style={{ fontSize: 13, fontWeight: 500, textDecoration: rIsDel ? "line-through" : "none", color: rIsDel ? "#ef4444" : "var(--text-1)" }} className="truncate">{r.title}</div>
                                  <div className="row gap-5" style={{ marginTop: 1 }}>
                                    {r.kind && <span style={{ fontSize: 10, fontWeight: 600, color: rm.fg, letterSpacing: ".03em" }}>{r.kind.toUpperCase()}</span>}
                                    {rIsNew && <span className="chip t-xs" style={{ padding: "0px 5px", fontSize: 10, background: "#dcfce7", color: "#16a34a", fontWeight: 700 }}>MỚI</span>}
                                    {rIsDel && <span className="chip t-xs" style={{ padding: "0px 5px", fontSize: 10, background: "#fee2e2", color: "#dc2626", fontWeight: 700 }}>Chờ xóa</span>}
                                  </div>
                                </div>

                                {/* Eye menu */}
                                {!rIsDel && (
                                  <div style={{ position: "relative" }}>
                                    <button className="icon-btn" data-size="sm" style={{ width: 28, height: 28 }} title="Xem / Tải tài liệu"
                                      onClick={e => { e.stopPropagation(); setResourceMenu(menuOpen ? null : { resourceId: r.resourceId, lessonId: lesson.lessonId, r }); }}>
                                      <Ic n="eye" size={14} />
                                    </button>
                                    {menuOpen && (
                                      <>
                                        <div style={{ position: "fixed", inset: 0, zIndex: 200 }} onClick={() => setResourceMenu(null)} />
                                        <div style={{ position: "absolute", right: 0, top: "calc(100% + 4px)", zIndex: 201,
                                          background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 10,
                                          boxShadow: "0 6px 20px rgba(0,0,0,.12)", minWidth: 150, overflow: "hidden" }}>
                                          <button style={{ width: "100%", display: "flex", alignItems: "center", gap: 8, padding: "9px 14px",
                                            background: "none", border: "none", cursor: "pointer", fontSize: 13, color: "#0284c7", fontWeight: 500 }}
                                            onClick={() => {
                                              setResourceMenu(null);
                                              setPreviewResource({ resourceId: r.resourceId, lessonId: lesson.lessonId, title: r.title, resourceType: r.resourceType, externalUrl: r.externalUrl, mimeType: r.mimeType });
                                            }}>
                                            <Ic n="eye" size={14} />Xem tài liệu
                                          </button>
                                          {canEdit && (
                                            <button style={{ width: "100%", display: "flex", alignItems: "center", gap: 8, padding: "9px 14px",
                                              background: "none", border: "none", cursor: "pointer", fontSize: 13, color: "#16a34a", fontWeight: 500 }}
                                              onClick={async () => {
                                                setResourceMenu(null);
                                                try {
                                                  const url = r.externalUrl
                                                    ? r.externalUrl
                                                    : (await api.get(`/instructor/courses/${courseId}/lessons/${lesson.lessonId}/resources/${r.resourceId}/download-url`)).data.url;
                                                  const a = document.createElement("a"); a.href = url; a.download = r.title || "tai-lieu"; a.target = "_blank"; a.click();
                                                } catch (e: any) { alert(e?.response?.data?.message || "Không thể tải tài liệu"); }
                                              }}>
                                              <Ic n="download" size={14} />Tải về
                                            </button>
                                          )}
                                        </div>
                                      </>
                                    )}
                                  </div>
                                )}

                                {/* Edit / Delete */}
                                {canEdit && !rIsDel && <>
                                  <button className="icon-btn" data-size="sm" style={{ width: 28, height: 28 }} title="Đổi tên"
                                    onClick={() => setEditResourceState({ lessonId: lesson.lessonId, resourceId: r.resourceId, title: r.title, resourceType: r.resourceType, externalUrl: r.externalUrl, mimeType: r.mimeType })}>
                                    <Ic n="edit" size={13} />
                                  </button>
                                  <button className="icon-btn" data-size="sm" style={{ width: 28, height: 28, color: "var(--error)" }} title="Xóa tài liệu"
                                    onClick={() => handleDeleteResource(lesson.lessonId, r.resourceId)}>
                                    <Ic n="x" size={13} />
                                  </button>
                                </>}
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  );
                })}
                {canEdit && (
                  <div style={{ padding: "10px 24px" }}>
                    <button className="btn btn-ghost btn-sm" onClick={() => setAddLessonState({ chapterId: ch.id })}>
                      <Ic n="plus" size={14} />Thêm bài giảng
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </Section>
    );
  }

  Object.assign(window, { CourseContentTab });
})();
