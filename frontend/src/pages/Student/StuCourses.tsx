// @ts-nocheck
/* ============================================================
   RIKKEI EDU — StuCourses
   ============================================================ */
(function () {
const { useState: uS } = React;
const Ic = window.Icon;
const D = window.DATA;
const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

/* ---------------- My Courses (card grid) ---------------- */
function StuCourses({ nav }) {
  const [tab, setTab] = uS("all");
  const [q, setQ] = uS("");
  const [sort, setSort] = uS("Mới nhất");
  let list = D.courses.filter(c => c.progress > 0 || c.sStatus !== "new" || true);
  // students see assigned courses; show all 12
  if (tab === "learning") list = D.courses.filter(c => c.sStatus === "learning");
  else if (tab === "done") list = D.courses.filter(c => c.sStatus === "done");
  else if (tab === "new") list = D.courses.filter(c => c.sStatus === "new");
  else list = D.courses;
  if (q) list = list.filter(c => c.title.toLowerCase().includes(q.toLowerCase()));
  const pg = window.usePaged(list, 8);
  return (
    <div className="page fade-in">
      <div className="page-head"><h1 className="t-h1">Khóa học của tôi</h1><p>Quản lý và theo dõi các khóa học bạn đã được phân bổ.</p></div>
      <div className="toolbar">
        <Tb items={[{v:"all",label:"Tất cả",count:D.courses.length},{v:"learning",label:"Đang học",count:D.courses.filter(c=>c.sStatus==="learning").length},{v:"done",label:"Hoàn thành",count:D.courses.filter(c=>c.sStatus==="done").length},{v:"new",label:"Chưa bắt đầu",count:D.courses.filter(c=>c.sStatus==="new").length}]} value={tab} onChange={setTab} />
        <div className="grow" />
        <Se placeholder="Tìm tên khóa học..." value={q} onChange={setQ} style={{ maxWidth: 280, flex: "none", width: 280 }} />
        <Sl value={sort} onChange={setSort} options={["Mới nhất","Tiến độ cao nhất","Tên A-Z"]} style={{ width: 160 }} />
      </div>
      {pg.total ? (
        <>
          <div className="grid grid-cards">{pg.slice.map(c => <CC key={c.id} c={c} onOpen={() => nav(c.sStatus === "done" ? "certs" : "player")} />)}</div>
          <window.PageBar pg={pg} unit="khóa học" />
        </>
      ) : <Em icon="book" title="Không tìm thấy khóa học" text="Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm khác." />}
    </div>
  );
}

window.StuCourses = StuCourses;
})();
