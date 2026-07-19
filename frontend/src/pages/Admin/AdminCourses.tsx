// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Quản lý Khóa học
   ============================================================ */
(function () {
  const { useEffect, useState } = React;
  const Ic = window.Icon;
  const { Status, StatCard, Search, Tabs, Select, Section, Modal, ModalHead, Empty } = window;
  const courseService = window.__courseService;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  function mapStatus(status) {
    const normalized = String(status || "").toUpperCase();
    if (normalized === "PUBLISHED") return "published";
    if (normalized === "PENDING" || normalized === "PENDING_UPDATE") return "pending";
    if (normalized === "DRAFT") return "draft";
    if (normalized === "REJECTED") return "rejected";
    if (normalized === "ARCHIVED") return "disabled";
    return "draft";
  }

  function mapCourse(course) {
    return {
      ...course,
      pubStatus: mapStatus(course.status),
      thumb: course.thumbnailUrl || "assets/courses/lesson.png",
      cat: course.category?.name || "Chưa phân loại",
      instructor: course.instructorName || course.instructor?.fullName || "—",
      students: course.studentCount || course.students || 0,
    };
  }

  function fmtDate(value) {
    if (!value) return "—";
    return new Date(value).toLocaleDateString("vi-VN");
  }

  function countLessons(course) {
    return (course?.chapters || []).reduce((sum, ch) => sum + (ch.lessons?.length || 0), 0);
  }

  function sanitizeSearch(value) {
    return String(value || "").replace(/[^\p{L}\p{N}\s]/gu, "").replace(/\s+/g, " ");
  }

  function matchSearch(title, keyword) {
    const terms = sanitizeSearch(keyword).trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (terms.length === 0) return true;
    const normalizedTitle = String(title || "").toLowerCase();
    return terms.some(term => normalizedTitle.includes(term));
  }

  function AdminCourses() {
    const [tab, setTab] = useState("all");
    const [q, setQ] = useState("");
    const [instructorFilter, setInstructorFilter] = useState("all");
    const [categoryFilter, setCategoryFilter] = useState("all");
    const [courses, setCourses] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [detail, setDetail] = useState(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState(null);

    function loadCourses() {
      setLoading(true);
      setError(null);
      if (!courseService?.getCourses) {
        setCourses([]);
        setError("Chưa khởi tạo dịch vụ khóa học");
        setLoading(false);
        return;
      }
      courseService.getCourses({ size: 100 })
        .then(res => {
          const page = res?.data || res;
          setCourses((page?.content || []).map(mapCourse));
        })
        .catch(err => {
          setCourses([]);
          setError(err?.response?.data?.message || "Không thể tải danh sách khóa học");
        })
        .finally(() => setLoading(false));
    }

    useEffect(() => { loadCourses(); }, []);

    function openDetail(course) {
      setDetail(mapCourse(course));
      setDetailLoading(true);
      setDetailError(null);
      courseService.getCourseDetail(course.id)
        .then(res => setDetail(mapCourse(res)))
        .catch(err => setDetailError(err?.response?.data?.message || "Không thể tải chi tiết khóa học"))
        .finally(() => setDetailLoading(false));
    }

    function closeDetail() {
      setDetail(null);
      setDetailError(null);
      setDetailLoading(false);
    }

    const instructorOptions = [
      { v: "all", label: "Tất cả giảng viên" },
      ...Array.from(new Set(courses.map(c => c.instructor).filter(Boolean))).sort()
        .map(name => ({ v: name, label: name })),
    ];

    const categoryOptions = [
      { v: "all", label: "Tất cả danh mục" },
      ...Array.from(new Set(courses.map(c => c.cat).filter(Boolean))).sort()
        .map(name => ({ v: name, label: name })),
    ];

    let baseFilteredCourses = courses;
    if (instructorFilter !== "all") baseFilteredCourses = baseFilteredCourses.filter(c => c.instructor === instructorFilter);
    if (categoryFilter !== "all") baseFilteredCourses = baseFilteredCourses.filter(c => c.cat === categoryFilter);
    if (q) baseFilteredCourses = baseFilteredCourses.filter(c => matchSearch(c.title, q));

    let visibleCourses = baseFilteredCourses;
    if (tab !== "all") visibleCourses = visibleCourses.filter(c => c.pubStatus === tab);

    const stats = {
      total: visibleCourses.length,
      published: visibleCourses.filter(c => c.pubStatus === "published").length,
      pending: visibleCourses.filter(c => c.pubStatus === "pending").length,
      draft: visibleCourses.filter(c => c.pubStatus === "draft").length,
    };

    const pg = window.usePaged(visibleCourses, 10);
    return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Quản lý Khóa học</h1><p>Toàn bộ khóa học trên hệ thống, theo dõi trạng thái xuất bản và hiệu suất.</p></div>
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="book" iconBg="#eaf1ff" iconColor="#2563eb" value={loading ? "..." : stats.total.toLocaleString()} label="Tổng khóa học" />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={loading ? "..." : stats.published.toLocaleString()} label="Đã xuất bản" />
          <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value={loading ? "..." : stats.pending.toLocaleString()} label="Chờ duyệt" />
          <StatCard icon="file" iconBg="#eef2f7" iconColor="#64748b" value={loading ? "..." : stats.draft.toLocaleString()} label="Bản nháp" />
        </div>
        <div className="toolbar">
          <Tabs items={[{v:"all",label:"Tất cả"},{v:"published",label:"Đã xuất bản"},{v:"pending",label:"Chờ duyệt"},{v:"draft",label:"Bản nháp"}]} value={tab} onChange={setTab} />
          <div className="grow" />
          <Select value={instructorFilter} onChange={setInstructorFilter} options={instructorOptions} style={{ width: 190, flex: "none" }} />
          <Select value={categoryFilter} onChange={setCategoryFilter} options={categoryOptions} style={{ width: 180, flex: "none" }} />
          <Search placeholder="Tìm khóa học..." value={q} onChange={value => setQ(sanitizeSearch(value))} style={{ width: 260, flex: "none" }} />
        </div>
        {loading && <div className="card card-pad muted">Đang tải danh sách khóa học...</div>}
        {!loading && error && <div className="card card-pad" style={{ color:"var(--chip-error-fg)", background:"var(--chip-error-bg)", borderColor:"#f8caca" }}>
          <div className="between wrap" style={{ gap: 12 }}>
            <span>{error}</span>
            <button className="btn btn-secondary" onClick={loadCourses}><Ic n="refresh" size={16} />Thử lại</button>
          </div>
        </div>}
        {!loading && !error && visibleCourses.length === 0 && <Empty icon="book" title="Không có khóa học" text="Không tìm thấy khóa học phù hợp với bộ lọc hiện tại." />}
        {!loading && !error && visibleCourses.length > 0 && <>
          <Section pad={false}>
            <div style={{ overflowX: "auto" }}>
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Khóa học</th>
                    <th>Giảng viên</th>
                    <th>Danh mục</th>
                    <th>Học viên</th>
                    <th>Trạng thái</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>{pg.slice.map(c => (
                  <tr key={c.id} onClick={() => openDetail(c)} style={{ cursor: "pointer" }}>
                    <td>
                      <div className="row gap-11">
                        <div style={{ width: 52, height: 36, borderRadius: 8, flex: "none", backgroundImage: `url(${c.thumb})`, backgroundSize: "cover", backgroundPosition: "center" }} />
                        <b style={{ fontSize: 13.5, maxWidth: 260 }} className="truncate">{c.title}</b>
                      </div>
                    </td>
                    <td className="muted t-sm">{c.instructor}</td>
                    <td><span className="chip chip-neutral">{c.cat}</span></td>
                    <td><b>{c.students.toLocaleString()}</b></td>
                    <td><Status s={c.pubStatus} /></td>
                    <td><button className="icon-btn" style={{ width: 34, height: 34 }} onClick={(e) => { e.stopPropagation(); openDetail(c); }} title="Xem chi tiết"><Ic n="dots" size={18} /></button></td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          </Section>
          <window.PageBar pg={pg} unit="khóa học" />
        </>}
        <Modal open={!!detail} onClose={closeDetail} max={760} maxHeight="86vh">
          {detail && <>
            <ModalHead
              title={detail.title}
              sub={`${detail.cat} • ${detail.instructor}`}
              icon="book"
              iconBg="#eaf1ff"
              iconColor="#2563eb"
              onClose={closeDetail}
            />
            <div className="modal-body" style={{ overflowY: "auto" }}>
              <div className="row gap-14" style={{ alignItems: "flex-start", marginBottom: 18 }}>
                <div style={{ width: 128, height: 82, borderRadius: 14, flex: "none", backgroundImage: `url(${detail.thumb})`, backgroundSize: "cover", backgroundPosition: "center", backgroundColor: "var(--surface-2)" }} />
                <div className="grow">
                  <div className="row gap-8 wrap" style={{ marginBottom: 10 }}>
                    <Status s={detail.pubStatus} />
                    <span className="chip chip-neutral">{detail.level || "Chưa đặt cấp độ"}</span>
                    <span className="chip chip-neutral">{detail.chatEnabled ? "Chat AI bật" : "Chat AI tắt"}</span>
                  </div>
                  <div className="t-sm muted" style={{ lineHeight: 1.65 }}>{detail.description || "Khóa học chưa có mô tả."}</div>
                </div>
              </div>

              {detailLoading && <div className="card card-pad muted">Đang tải chi tiết khóa học...</div>}
              {detailError && <div className="card card-pad" style={{ color:"var(--chip-error-fg)", background:"var(--chip-error-bg)", borderColor:"#f8caca" }}>{detailError}</div>}

              {!detailLoading && !detailError && <>
                <div className="grid" style={{ gridTemplateColumns: "repeat(4, 1fr)", gap: 10, marginBottom: 18 }}>
                  <div className="card card-pad" style={{ padding: 14 }}><div className="t-xs muted">Chương</div><b>{detail.chapters?.length || 0}</b></div>
                  <div className="card card-pad" style={{ padding: 14 }}><div className="t-xs muted">Bài học</div><b>{countLessons(detail)}</b></div>
                  <div className="card card-pad" style={{ padding: 14 }}><div className="t-xs muted">Ngày tạo</div><b>{fmtDate(detail.createdAt)}</b></div>
                  <div className="card card-pad" style={{ padding: 14 }}><div className="t-xs muted">Ngày nộp</div><b>{fmtDate(detail.submittedAt)}</b></div>
                </div>

                <h3 className="t-h3" style={{ marginBottom: 10 }}>Nội dung khóa học</h3>
                {(detail.chapters || []).length === 0 ? (
                  <div className="muted t-sm">Chưa có chương/bài học.</div>
                ) : (
                  <div style={{ display: "grid", gap: 10 }}>
                    {detail.chapters.map((ch, index) => (
                      <div key={ch.id || index} className="card card-pad" style={{ padding: 14 }}>
                        <div style={{ fontWeight: 700, marginBottom: 8 }}>{index + 1}. {ch.title}</div>
                        {(ch.lessons || []).length === 0 ? <div className="t-sm muted">Chưa có bài học.</div> : (
                          <div style={{ display: "grid", gap: 6 }}>
                            {ch.lessons.map((lesson, li) => (
                              <div key={lesson.id || li} className="row gap-8 t-sm muted">
                                <Ic n="book_open" size={14} />
                                <span className="truncate">{lesson.title}</span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </>}
            </div>
          </>}
        </Modal>
      </div>
    );
  }

  Object.assign(window, { AdminCourses });
})();
