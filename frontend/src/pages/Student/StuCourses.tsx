// @ts-nocheck
/* ============================================================
   RIKKEI EDU — StuCourses
   ============================================================ */
(function () {
  const { useState: uS, useEffect } = React;
  const Ic = window.Icon;
  const api = window.httpClient;
  const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

function hasSearchText(value) {
  return /[\p{L}\p{N}]/u.test(String(value || ''));
}

function courseMatchesSearch(c, q) {
  const keywords = (q || "").toLowerCase().match(/[\p{L}\p{N}]+/gu) || [];
  if (keywords.length === 0) return true;
  const haystack = String(c.title || '').toLowerCase();
  return keywords.some(keyword => haystack.includes(keyword));
}

/* ---------------- My Courses (card grid) ---------------- */
function StuCourses({ nav }) {
  const [tab, setTab] = uS("all");
  const [q, setQ] = uS("");
  const [sort, setSort] = uS("Mới nhất");
  const [courses, setCourses] = uS([]);
  const [loading, setLoading] = uS(true);

  useEffect(() => {
    api.get("/student/courses")
      .then(r => setCourses(r.data || []))
      .catch(() => setCourses([]))
      .finally(() => setLoading(false));
  }, []);

  const mapped = courses.map(c => {
    const totalItems = (c.totalLessons ?? 0) + (c.totalAssignments ?? 0);
    const completedItems = (c.completedLessons ?? 0) + (c.completedAssignments ?? 0);
    const rawProgress = c.progress ?? c.progressPercentage ?? (totalItems > 0 ? Math.round((completedItems / totalItems) * 100) : 0);
    const progress = Math.max(0, Math.min(100, rawProgress || 0));

    return {
      ...c,
      thumb: c.thumbnailUrl || "assets/courses/placeholder.png",
      cat: c.category,
      progress,
      lessons: c.completedLessons != null && c.totalLessons != null ? `${c.completedLessons}/${c.totalLessons}` : c.lessons,
      instructor: c.instructorName || c.instructor,
      sStatus: c.sStatus || (progress >= 100 ? "done" : progress > 0 ? "learning" : "new"),
    };
  });

  let list = mapped;
  if (tab === "learning") list = mapped.filter(c => c.sStatus === "learning");
  else if (tab === "done") list = mapped.filter(c => c.sStatus === "done");
  else if (tab === "new") list = mapped.filter(c => c.sStatus === "new");
  list = list.filter(c => courseMatchesSearch(c, q));

  if (sort === "Tiến độ cao nhất") list = [...list].sort((a, b) => b.progress - a.progress);
  else if (sort === "Tên A-Z") list = [...list].sort((a, b) => a.title.localeCompare(b.title));

  const pg = window.usePaged(list, 8);
  return (
    <div className="page fade-in">
      <div className="page-head"><h1 className="t-h1">Khóa học của tôi</h1><p>Quản lý và theo dõi các khóa học bạn đã được phân bổ.</p></div>
      <div className="toolbar">
        <Tb items={[{v:"all",label:"Tất cả",count:mapped.length},{v:"learning",label:"Đang học",count:mapped.filter(c=>c.sStatus==="learning").length},{v:"done",label:"Hoàn thành",count:mapped.filter(c=>c.sStatus==="done").length},{v:"new",label:"Chưa bắt đầu",count:mapped.filter(c=>c.sStatus==="new").length}]} value={tab} onChange={setTab} />
        <div className="grow" />
        <Se placeholder="Tìm tên khóa học..." value={q} onChange={(value) => { if (!value || hasSearchText(value)) setQ(value); }} style={{ maxWidth: 280, flex: "none", width: 280 }} />
        <Sl value={sort} onChange={setSort} options={["Mới nhất","Tiến độ cao nhất","Tên A-Z"]} style={{ width: 160 }} />
      </div>
      {loading ? (
        <div style={{ padding: 40, textAlign: "center", color: "#94a3b8" }}>Đang tải...</div>
      ) : pg.total ? (
        <>
          <div className="grid grid-cards">{pg.slice.map(c => <CC key={c.id} c={c} onOpen={() => nav("courseDetail", { courseId: c.id })} onCert={() => nav("certs", { courseId: c.id })} />)}</div>
          <window.PageBar pg={pg} unit="khóa học" />
        </>
      ) : <Em icon="book" title="Không tìm thấy khóa học" text="Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm khác." />}
    </div>
  );
}

window.StuCourses = StuCourses;
})();
