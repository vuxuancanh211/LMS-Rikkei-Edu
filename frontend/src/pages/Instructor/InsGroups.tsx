// @ts-nocheck
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Search, Select, Modal, ModalHead, Empty } = window;
  const { getGroups, createGroup, updateGroup, deleteGroup, getUnassignedStudents } = window.__groupService;
  const { getMyCourses } = window.__courseService;

  function InsGroups({ nav }) {
    const [add, setAdd] = useState(false);
    const [q, setQ] = useState("");
    const [courseFilter, setCourseFilter] = useState("all");
    const [groups, setGroups] = useState([]);
    const [loading, setLoading] = useState(true);
    const [courses, setCourses] = useState([]);
    const [coursesLoading, setCoursesLoading] = useState(true);
    const [selectedCourse, setSelectedCourse] = useState("");
    const [error, setError] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [refreshKey, setRefreshKey] = useState(0);
    const [creating, setCreating] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [deleting, setDeleting] = useState(false);
    const [editTarget, setEditTarget] = useState(null);
    const [editing, setEditing] = useState(false);
    const [editName, setEditName] = useState("");
    const [editDescription, setEditDescription] = useState("");
    const [editMaxCapacity, setEditMaxCapacity] = useState(20);
    const [editStartDate, setEditStartDate] = useState("");
    const [editEndDate, setEditEndDate] = useState("");
    const [editError, setEditError] = useState("");
    const [editFieldErrors, setEditFieldErrors] = useState({});

    const [activeTab, setActiveTab] = useState("groups");
    const [unassignedStudents, setUnassignedStudents] = useState([]);
    const [unassignedLoading, setUnassignedLoading] = useState(false);

    const today = new Date().toISOString().split("T")[0];

    const resetForm = () => {
      setSelectedCourse("");
      setError("");
      setFieldErrors({});
    };

    useEffect(() => {
      if (!editTarget) return;
      setEditName(editTarget.name || "");
      setEditDescription(editTarget.description || "");
      setEditMaxCapacity(editTarget.maxCapacity || 20);
      setEditStartDate(editTarget.startDate || "");
      setEditEndDate(editTarget.endDate || "");
      setEditError("");
      setEditFieldErrors({});
    }, [editTarget]);

    useEffect(() => {
      getMyCourses()
        .then(res => setCourses(res.content || []))
        .catch(() => setCourses([]))
        .finally(() => setCoursesLoading(false));
    }, []);

    const load = () => {
      setLoading(true);
      if (!add) setError("");
      getGroups({ keyword: q || undefined, courseId: courseFilter !== "all" ? courseFilter : undefined })
        .then(res => setGroups(res.content || []))
        .catch((err) => {
          console.error("Load groups failed:", err?.response?.status, err?.response?.data || err?.message);
          setError(err?.response?.data?.message || err?.message || "Không thể tải danh sách nhóm");
          setGroups([]);
        })
        .finally(() => setLoading(false));
    };

    useEffect(() => { load(); }, [q, courseFilter, refreshKey]);

    useEffect(() => {
      if (activeTab !== "unassigned" || courseFilter === "all") return;
      setUnassignedLoading(true);
      getUnassignedStudents(courseFilter)
        .then(res => setUnassignedStudents(res || []))
        .catch(() => setUnassignedStudents([]))
        .finally(() => setUnassignedLoading(false));
    }, [activeTab, courseFilter]);

    const pg = window.usePaged(groups, 8);

    const statusLabel = { UPCOMING: "Sắp diễn ra", ACTIVE: "Đang hoạt động", COMPLETED: "Đã kết thúc" };
    const statusColor = { UPCOMING: "warning", ACTIVE: "success", COMPLETED: "muted" };

    const handleCreate = () => {
      const f = document.forms["groupForm"];
      const courseId = f.courseId?.value || selectedCourse;
      const errs = {};

      if (!courseId) errs.courseId = "Vui lòng chọn khóa học";
      if (!f.name.value.trim()) errs.name = "Vui lòng nhập tên nhóm";
      if (!f.startDate.value) errs.startDate = "Vui lòng chọn ngày bắt đầu";
      if (!f.endDate.value) errs.endDate = "Vui lòng chọn ngày kết thúc";
      if (f.endDate.value && f.startDate.value && f.endDate.value < f.startDate.value) {
        errs.endDate = "Ngày kết thúc không được trước ngày bắt đầu";
      }

      if (Object.keys(errs).length > 0) {
        setFieldErrors(errs);
        setError("Dữ liệu nhập không hợp lệ");
        return;
      }

      const data = {
        courseId,
        name: f.name.value,
        maxCapacity: parseInt(f.maxCapacity.value) || 20,
        startDate: f.startDate.value,
        endDate: f.endDate.value || null,
        description: f.description.value || null,
      };
      setCreating(true);
      setError("");
      setFieldErrors({});
      createGroup(data)
        .then(() => {
          setAdd(false);
          resetForm();
          setRefreshKey(k => k + 1);
        })
        .catch((err) => {
          const d = err?.response?.data;
          if (d?.validationErrors) {
            setFieldErrors(d.validationErrors);
            setError(d.message || "Dữ liệu nhập không hợp lệ");
          } else {
            const msg = d?.message || err?.message || "Có lỗi xảy ra khi tạo nhóm";
            setError(msg);
          }
        })
        .finally(() => setCreating(false));
    };

    const handleDeleteConfirm = () => {
      if (!deleteTarget) return;
      setDeleting(true);
      deleteGroup(deleteTarget.id)
        .then(() => {
          setDeleteTarget(null);
          setRefreshKey(k => k + 1);
        })
        .catch(() => {})
        .finally(() => setDeleting(false));
    };

    const handleEditSubmit = () => {
      if (!editTarget) return;
      const errs = {};
      if (!editName.trim()) errs.name = "Vui lòng nhập tên nhóm";
      if (!editStartDate) errs.startDate = "Vui lòng chọn ngày bắt đầu";
      if (editEndDate && editStartDate && editEndDate < editStartDate) {
        errs.endDate = "Ngày kết thúc không được trước ngày bắt đầu";
      }
      if (Object.keys(errs).length > 0) {
        setEditFieldErrors(errs);
        setEditError("Dữ liệu nhập không hợp lệ");
        return;
      }
      setEditing(true);
      setEditError("");
      setEditFieldErrors({});
      updateGroup(editTarget.id, {
        name: editName.trim(),
        description: editDescription || null,
        maxCapacity: editMaxCapacity,
        startDate: editStartDate,
        endDate: editEndDate || null,
      })
        .then(() => {
          setEditTarget(null);
          setRefreshKey(k => k + 1);
        })
        .catch((err) => {
          const d = err?.response?.data;
          if (d?.validationErrors) {
            setEditFieldErrors(d.validationErrors);
            setEditError(d.message || "Dữ liệu nhập không hợp lệ");
          } else {
            setEditError(d?.message || err?.message || "Có lỗi xảy ra khi cập nhật");
          }
        })
        .finally(() => setEditing(false));
    };

    const courseOpts = courses.map(c => ({ v: c.id, label: c.title }));

    const pgUnassigned = window.usePaged(unassignedStudents, 10);

    function exportUnassignedCsv() {
      const courseTitle = courses.find(c => c.id === courseFilter)?.title || "unknown";
      const csv = unassignedStudents.map(s => s.email).join("\n");
      const blob = new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `email-sinh-vien-chua-co-nhom_${courseTitle.replace(/[^a-zA-Z0-9]/g, "_")}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    }

    const tabStyle = (tab) => ({
      flex: 1, height: 38, border: "none", background: "transparent",
      cursor: "pointer", fontSize: 13, fontWeight: 600,
      color: activeTab === tab ? "#2563eb" : "#94a3b8",
      borderBottom: `2px solid ${activeTab === tab ? "#2563eb" : "transparent"}`,
      transition: ".13s", display: "flex", alignItems: "center",
      justifyContent: "center", gap: 5,
    });

    return (
      <div className="page fade-in">
        <div className="page-head between">
          <div><h1 className="t-h1">Quản lý Nhóm</h1><p>Tạo nhóm, phân bổ học viên và theo dõi tiến độ từng đợt học.</p></div>
          <button className="btn btn-primary" onClick={() => { resetForm(); setAdd(true); }}><Ic n="plus" size={17} />Tạo nhóm mới</button>
        </div>

        {/* ── Tab bar ──────────────────────────────── */}
        <div style={{ display: "flex", marginTop: -8, marginBottom: 16 }}>
          {["groups", "unassigned"].map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)} style={tabStyle(tab)}>
              <Ic n={tab === "groups" ? "layers" : "users"} size={13} />
              {tab === "groups" ? "Nhóm" : "Sinh viên chưa có nhóm"}
            </button>
          ))}
        </div>

        {activeTab === "groups" ? (
          <>
            <div className="toolbar">
              <Search placeholder="Tìm nhóm theo tên..." value={q} onChange={setQ} />
              <Select value={courseFilter} onChange={setCourseFilter} options={[{ v: "all", label: "Tất cả khóa học" }, ...courseOpts]} style={{ width: 200, flex: "none" }} />
            </div>
            {error && !add && <div className="alert alert-danger" style={{ padding: "10px 14px", borderRadius: 8, background: "#fef2f2", color: "#b91c1c", fontSize: 14, marginBottom: 16 }}>{error}</div>}
            {loading ? <div className="t-center muted" style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 300 }}>Đang tải...</div> : (
              <>
                {pg.slice.length === 0 ? (
                  <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 300 }}>
                    <Empty text="Chưa có nhóm nào" />
                  </div>
                ) : (
                  <div className="grid grid-cards">
                    {pg.slice.map(g => (
                      <div key={g.id} className="card card-pad fade-in" style={{ cursor: "pointer" }} onClick={() => nav("groupDetail", { groupId: g.id })}>
                        <div className="between" style={{ marginBottom: 14 }}>
                          <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 12, background: "#f3edff", color: "#7c3aed" }}><Ic n="layers" size={22} /></div>
                          <div className="row gap-4">
                            <button className="icon-btn" style={{ width: 34, height: 34 }} onClick={(e) => { e.stopPropagation(); setEditTarget(g); }}><Ic n="edit" size={18} /></button>
                            <button className="icon-btn" style={{ width: 34, height: 34, color: "#dc2626" }} onClick={(e) => { e.stopPropagation(); setDeleteTarget(g); }}><Ic n="trash" size={18} /></button>
                          </div>
                        </div>
                        <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700 }}>{g.name}</h3>
                        <div className="t-sm muted truncate">{g.courseTitle}</div>
                        <div className="row gap-8" style={{ margin: "14px 0" }}>
                          <span className={"chip chip-" + statusColor[g.status]}>{statusLabel[g.status]}</span>
                          <span className="t-sm muted">{g.memberCount}/{g.maxCapacity || '∞'} thành viên</span>
                        </div>
                        <div className="t-xs dim row gap-6" style={{ marginTop: 12 }}><Ic n="calendar" size={13} />{g.startDate}{g.endDate ? ` – ${g.endDate}` : ''}</div>
                      </div>
                    ))}
                  </div>
                )}
                <window.PageBar pg={pg} unit="nhóm" />
              </>
            )}
          </>
        ) : (
          <>
            <div className="toolbar">
              <div style={{ flex: 1 }} />
              <Select value={courseFilter} onChange={setCourseFilter} options={[{ v: "all", label: "Tất cả khóa học" }, ...courseOpts]} style={{ width: 220, flex: "none" }} />
              {unassignedStudents.length > 0 && (
                <button className="btn btn-ghost" onClick={exportUnassignedCsv}>
                  <Ic n="download" size={15} />Xuất CSV
                </button>
              )}
            </div>
            {courseFilter === "all" ? (
              <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 200, color: "#94a3b8", fontSize: 13 }}>
                Vui lòng chọn một khóa học để xem danh sách sinh viên chưa có nhóm
              </div>
            ) : unassignedLoading ? (
              <div className="t-center muted" style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 200 }}>Đang tải...</div>
            ) : pgUnassigned.slice.length === 0 ? (
              <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 200 }}>
                <Empty text="Tất cả sinh viên đã được xếp vào nhóm" />
              </div>
            ) : (
              <>
                <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden", background: "#fff" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 14px",
                    background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, fontWeight: 600, color: "#64748b" }}>
                    <span style={{ width: 36, flexShrink: 0, textAlign: "center" }}>STT</span>
                    <span style={{ flex: 1 }}>Học viên</span>
                    <span style={{ width: 200 }}>Email</span>
                    <span style={{ width: 140 }}>SĐT</span>
                  </div>
                  {pgUnassigned.slice.map((s, i) => (
                    <div key={s.id}
                      style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px",
                        borderBottom: i < pgUnassigned.slice.length - 1 ? "1px solid #f1f5f9" : "none" }}>
                      <span style={{ width: 36, flexShrink: 0, textAlign: "center", fontSize: 12, color: "#94a3b8" }}>
                        {pgUnassigned.from + i}
                      </span>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, flex: 1, minWidth: 0 }}>
                        <div style={{ width: 32, height: 32, borderRadius: 999, background: "#e2e8f0",
                          display: "grid", placeItems: "center", fontSize: 11, fontWeight: 700, color: "#64748b", flexShrink: 0 }}>
                          {s.fullName?.charAt(0)?.toUpperCase() || "?"}
                        </div>
                        <span style={{ fontSize: 13, fontWeight: 600, color: "#0f172a" }} className="truncate">{s.fullName}</span>
                      </div>
                      <span style={{ width: 200, fontSize: 12, color: "#94a3b8" }}>{s.email}</span>
                      <span style={{ width: 140, fontSize: 12, color: "#475569" }}>{s.phoneNumber || "—"}</span>
                    </div>
                  ))}
                </div>
                <window.PageBar pg={pgUnassigned} unit="sinh viên" />
              </>
            )}
          </>
        )}
        <Modal open={add} onClose={() => { resetForm(); setAdd(false); }}>
          <ModalHead title="Tạo nhóm mới" sub="Thêm một đợt học mới vào khóa học" icon="layers" iconBg="#f3edff" iconColor="#7c3aed" onClose={() => { resetForm(); setAdd(false); }} />
          <form name="groupForm" onSubmit={e => { e.preventDefault(); handleCreate(); }}>
            <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              {error && <div className="alert alert-danger" style={{ padding: "10px 14px", borderRadius: 8, background: "#fef2f2", color: "#b91c1c", fontSize: 14 }}>{error}</div>}
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên nhóm</label><input name="name" className={"input" + (fieldErrors.name ? " input-error" : "")} placeholder="VD: Nhóm A1 - ReactJS K16" required />{fieldErrors.name && <div className="field-err">{fieldErrors.name}</div>}</div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Khóa học</label>{coursesLoading ? <div className="t-sm muted">Đang tải...</div> : <Select name="courseId" value={selectedCourse} onChange={v => setSelectedCourse(v)} options={courseOpts} />}{fieldErrors.courseId && <div className="field-err">{fieldErrors.courseId}</div>}</div>
              <div className="grid grid-2" style={{ gap: 14 }}>
                <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Số thành viên tối đa</label><input name="maxCapacity" className={"input" + (fieldErrors.maxCapacity ? " input-error" : "")} type="number" defaultValue={20} min={1} />{fieldErrors.maxCapacity && <div className="field-err">{fieldErrors.maxCapacity}</div>}</div>
                <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Ngày bắt đầu</label><input name="startDate" className={"input" + (fieldErrors.startDate ? " input-error" : "")} type="date" min={today} required />{fieldErrors.startDate && <div className="field-err">{fieldErrors.startDate}</div>}</div>
              </div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Ngày kết thúc</label><input name="endDate" className={"input" + (fieldErrors.endDate ? " input-error" : "")} type="date" min={today} required />{fieldErrors.endDate && <div className="field-err">{fieldErrors.endDate}</div>}</div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Mô tả</label><textarea name="description" className="input" style={{ height: 84, padding: 12, resize: "none" }} placeholder="Mô tả ngắn về nhóm..." /></div>
            </div>
            <div className="modal-foot"><button type="button" className="btn btn-ghost" onClick={() => { resetForm(); setAdd(false); }} disabled={creating}>Hủy</button><button type="submit" className="btn btn-primary" disabled={creating}>{creating ? "Đang tạo..." : "Tạo nhóm"}</button></div>
          </form>
        </Modal>
        <Modal open={!!deleteTarget} onClose={() => { if (!deleting) setDeleteTarget(null); }}>
          <ModalHead title="Xoá nhóm" sub={deleteTarget ? `Nhóm "${deleteTarget.name}" sẽ bị xoá vĩnh viễn` : ""} icon="trash" iconBg="#fef2f2" iconColor="#dc2626" onClose={() => { if (!deleting) setDeleteTarget(null); }} />
          <div className="modal-body">
            {deleteTarget && (
              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                <div className="row gap-12" style={{ alignItems: "center", padding: "12px 14px", background: "var(--bg-2)", borderRadius: 10 }}>
                  <div className="stat-ic" style={{ width: 44, height: 44, borderRadius: 12, background: "#f3edff", color: "#7c3aed", flex: "none" }}><Ic n="layers" size={22} /></div>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 15 }}>{deleteTarget.name}</div>
                    <div className="t-sm muted">{deleteTarget.courseTitle}</div>
                  </div>
                </div>
                {deleteTarget.memberCount > 0 && (
                  <div style={{ padding: "10px 14px", borderRadius: 8, background: "#fef5e6", color: "#92400e", fontSize: 13 }}>
                    <Ic n="warn" size={15} style={{ marginRight: 6, verticalAlign: "middle" }} />
                    Nhóm có {deleteTarget.memberCount} thành viên — tất cả sẽ bị xoá khỏi nhóm này.
                  </div>
                )}
              </div>
            )}
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => setDeleteTarget(null)} disabled={deleting}>Hủy</button>
            <button className="btn" style={{ background: "#dc2626", color: "#fff" }} disabled={deleting} onClick={handleDeleteConfirm}>
              {deleting ? "Đang xoá..." : "Xác nhận xoá"}
            </button>
          </div>
        </Modal>
        <Modal open={!!editTarget} onClose={() => { if (!editing) setEditTarget(null); }}>
          <ModalHead title="Chỉnh sửa nhóm" sub={editTarget ? `Cập nhật thông tin nhóm "${editTarget.name}"` : ""} icon="edit" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => { if (!editing) setEditTarget(null); }} />
          <form name="editGroupForm" onSubmit={e => { e.preventDefault(); handleEditSubmit(); }}>
            <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              {editError && <div className="alert alert-danger" style={{ padding: "10px 14px", borderRadius: 8, background: "#fef2f2", color: "#b91c1c", fontSize: 14 }}>{editError}</div>}
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tên nhóm</label><input className={"input" + (editFieldErrors.name ? " input-error" : "")} placeholder="VD: Nhóm A1 - ReactJS K16" value={editName} onChange={e => setEditName(e.target.value)} required />{editFieldErrors.name && <div className="field-err">{editFieldErrors.name}</div>}</div>
              <div className="grid grid-2" style={{ gap: 14 }}>
                <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Số thành viên tối đa</label><input className={"input" + (editFieldErrors.maxCapacity ? " input-error" : "")} type="number" value={editMaxCapacity} onChange={e => setEditMaxCapacity(parseInt(e.target.value) || 0)} min={1} />{editFieldErrors.maxCapacity && <div className="field-err">{editFieldErrors.maxCapacity}</div>}</div>
                <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Ngày bắt đầu</label><input className={"input" + (editFieldErrors.startDate ? " input-error" : "")} type="date" value={editStartDate} onChange={e => setEditStartDate(e.target.value)} required />{editFieldErrors.startDate && <div className="field-err">{editFieldErrors.startDate}</div>}</div>
              </div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Ngày kết thúc</label><input className={"input" + (editFieldErrors.endDate ? " input-error" : "")} type="date" value={editEndDate} onChange={e => setEditEndDate(e.target.value)} />{editFieldErrors.endDate && <div className="field-err">{editFieldErrors.endDate}</div>}</div>
              <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Mô tả</label><textarea className="input" style={{ height: 84, padding: 12, resize: "none" }} placeholder="Mô tả ngắn về nhóm..." value={editDescription} onChange={e => setEditDescription(e.target.value)} /></div>
            </div>
            <div className="modal-foot"><button type="button" className="btn btn-ghost" onClick={() => setEditTarget(null)} disabled={editing}>Hủy</button><button type="submit" className="btn btn-primary" disabled={editing}>{editing ? "Đang lưu..." : "Lưu thay đổi"}</button></div>
          </form>
        </Modal>
      </div>
    );
  }

  Object.assign(window, { InsGroups });
})();
