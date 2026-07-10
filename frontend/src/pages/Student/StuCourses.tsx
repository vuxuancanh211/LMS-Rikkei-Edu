// @ts-nocheck
/* ============================================================
   RIKKEI EDU — StuCourses
   ============================================================ */
(function () {
const { useState: uS, useEffect } = React;
const Ic = window.Icon;
const api = window.httpClient;
const D = window.DATA;
const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

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

  const mapped = courses.map(c => ({
    ...c,
    thumb: c.thumbnailUrl || "assets/courses/placeholder.png",
    cat: c.category,
  }));

  let list = mapped;
  if (tab === "learning") list = mapped.filter(c => c.sStatus === "learning");
  else if (tab === "done") list = mapped.filter(c => c.sStatus === "done");
  else if (tab === "new") list = mapped.filter(c => c.sStatus === "new");
  if (q) list = list.filter(c => c.title.toLowerCase().includes(q.toLowerCase()));

  if (sort === "Tiến độ cao nhất") list = [...list].sort((a, b) => b.progress - a.progress);
  else if (sort === "Tên A-Z") list = [...list].sort((a, b) => a.title.localeCompare(b.title));

  const pg = window.usePaged(list, 8);
  return (
    <div className="page fade-in">
      <div className="page-head"><h1 className="t-h1">Khóa học của tôi</h1><p>Quản lý và theo dõi các khóa học bạn đã được phân bổ.</p></div>
      <div className="toolbar">
        <Tb items={[{v:"all",label:"Tất cả",count:mapped.length},{v:"learning",label:"Đang học",count:mapped.filter(c=>c.sStatus==="learning").length},{v:"done",label:"Hoàn thành",count:mapped.filter(c=>c.sStatus==="done").length},{v:"new",label:"Chưa bắt đầu",count:mapped.filter(c=>c.sStatus==="new").length}]} value={tab} onChange={setTab} />
        <div className="grow" />
        <Se placeholder="Tìm tên khóa học..." value={q} onChange={setQ} style={{ maxWidth: 280, flex: "none", width: 280 }} />
        <Sl value={sort} onChange={setSort} options={["Mới nhất","Tiến độ cao nhất","Tên A-Z"]} style={{ width: 160 }} />
      </div>
      {loading ? (
        <div style={{ padding: 40, textAlign: "center", color: "#94a3b8" }}>Đang tải...</div>
      ) : pg.total ? (
        <>
          <div className="grid grid-cards">{pg.slice.map(c => <CC key={c.id} c={c} onOpen={() => nav("player", { courseId: c.id })} onCert={() => nav("certs", { courseId: c.id })} />)}</div>
          <window.PageBar pg={pg} unit="khóa học" />
        </>
      ) : <Em icon="book" title="Không tìm thấy khóa học" text="Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm khác." />}
    </div>
  );
}

window.StuCourses = StuCourses;
})();
