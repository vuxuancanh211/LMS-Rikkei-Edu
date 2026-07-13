// @ts-nocheck
(function () {
  const { useState, useEffect, useRef, useCallback } = React;
  const Ic = window.Icon;
  const { Avatar, StatCard, Search, Section, Modal, ModalHead, Empty } = window;
  const { getGroupDetail, addGroupMembers, removeGroupMember, searchStudents, previewGroupMembersCsvImport, confirmGroupMembersCsvImport } = window.__groupService;

  function InsGroupDetail({ nav, groupId: propGroupId }: { nav?: any; groupId?: string } = {}) {
    const [urlGroupId, setUrlGroupId] = useState(() => new URLSearchParams(window.location.search).get("groupId"));
    useEffect(() => {
      const checkUrl = () => setUrlGroupId(new URLSearchParams(window.location.search).get("groupId"));
      window.addEventListener("popstate", checkUrl);
      return () => window.removeEventListener("popstate", checkUrl);
    }, []);
    const groupId = propGroupId || urlGroupId || window.__selectedGroupId || sessionStorage.getItem("selectedGroupId") || null;

    const [q, setQ] = useState("");
    const [group, setGroup] = useState(null);
    const [loading, setLoading] = useState(true);
    const [addOpen, setAddOpen] = useState(false);
    const [csvOpen, setCsvOpen] = useState(false);
    const [removeTarget, setRemoveTarget] = useState(null);
    const [removing, setRemoving] = useState(false);

    useEffect(() => {
      if (!groupId) { setLoading(false); return; }
      setLoading(true);
      getGroupDetail(groupId)
        .then(setGroup)
        .catch(() => setGroup(null))
        .finally(() => setLoading(false));
    }, [groupId]);

    const members = group?.members || [];
    let list = members.filter(s => !q || s.studentName.toLowerCase().includes(q.toLowerCase()));
    const pg = window.usePaged(list, 10);

    const statusLabel = { UPCOMING: "Sắp diễn ra", ACTIVE: "Đang hoạt động", COMPLETED: "Đã kết thúc" };
    const statusColor = { UPCOMING: "warning", ACTIVE: "success", COMPLETED: "muted" };

    const handleRemoveConfirm = () => {
      if (!removeTarget) return;
      setRemoving(true);
      removeGroupMember(groupId, removeTarget.studentId)
        .then(() => {
          setRemoveTarget(null);
          getGroupDetail(groupId).then(setGroup);
        })
        .catch(() => {})
        .finally(() => setRemoving(false));
    };

    if (loading) return (
      <div className="page fade-in">
        <div className="row gap-10" style={{ marginBottom: 16, cursor: "pointer", color: "var(--text-2)" }} onClick={() => nav("groups")}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách nhóm</span></div>
        <div className="t-center muted" style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 300 }}>Đang tải...</div>
      </div>
    );

    return (
      <div className="page fade-in">
        <div className="row gap-10" style={{ marginBottom: 16, cursor: "pointer", color: "var(--text-2)" }} onClick={() => nav("groups")}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách nhóm</span></div>
        <div className="page-head between">
          <div>
            <h1 className="t-h1">{group?.name}</h1>
            <p className="row gap-8">{group?.courseTitle}<span className={"chip chip-" + statusColor[group?.status]}>{statusLabel[group?.status]}</span>• {group?.memberCount}/{group?.maxCapacity || '∞'} thành viên • {group?.startDate}{group?.endDate ? ` \u2013 ${group?.endDate}` : ''}</p>
          </div>
          <div className="row gap-10">
            <button className="btn btn-ghost" onClick={() => setCsvOpen(true)}><Ic n="upload" size={17} />Thêm bằng CSV</button>
            <button className="btn btn-primary" onClick={() => setAddOpen(true)}><Ic n="plus" size={17} />Thêm học viên</button>
          </div>
        </div>
        <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns: "repeat(4,1fr)" }}>
          <StatCard icon="users" iconBg="#eaf1ff" iconColor="#2563eb" value={String(group?.memberCount || 0)} label="Thành viên" />
          <StatCard icon="trending" iconBg="#e7f8f0" iconColor="#059669" value="—" label="Tiến độ TB" />
          <StatCard icon="check_circle" iconBg="#f3edff" iconColor="#7c3aed" value="—" label="Đã nộp bài" />
          <StatCard icon="warn" iconBg="#fef5e6" iconColor="#d97706" value="—" label="Chưa nộp / trễ" />
        </div>
        <div className="toolbar"><Search placeholder="Tìm học viên..." value={q} onChange={setQ} /></div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Học viên</th><th>Email</th><th>Ngày tham gia</th><th></th></tr></thead>
            <tbody>{pg.slice.map(m => (
              <tr key={m.id}>
                <td><div className="row gap-10"><Avatar name={m.studentName} size={36} src={m.avatarUrl} /><b style={{ fontSize: 14 }}>{m.studentName}</b></div></td>
                <td className="muted">{m.studentEmail}</td>
                <td className="muted">{new Date(m.joinedAt).toLocaleDateString("vi-VN")}</td>
                <td><button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => setRemoveTarget(m)}><Ic n="x" size={18} /></button></td>
              </tr>
            ))}</tbody>
          </table></div>
        </Section>
        {pg.total === 0 && !loading && <Empty text="Chưa có học viên nào trong nhóm" />}
        <window.PageBar pg={pg} unit="học viên" />

        <AddMemberModal
          open={addOpen}
          onClose={() => setAddOpen(false)}
          groupId={groupId}
          group={group}
          onAdd={() => getGroupDetail(groupId).then(setGroup)}
          searchStudents={searchStudents}
          addGroupMembers={addGroupMembers}
          Ic={Ic}
          Avatar={Avatar}
          Modal={Modal}
          ModalHead={ModalHead}
        />
        <CsvAddMemberModal
          open={csvOpen}
          onClose={() => setCsvOpen(false)}
          groupId={groupId}
          onAdd={() => getGroupDetail(groupId).then(setGroup)}
          previewGroupMembersCsvImport={previewGroupMembersCsvImport}
          confirmGroupMembersCsvImport={confirmGroupMembersCsvImport}
          Ic={Ic}
          Modal={Modal}
          ModalHead={ModalHead}
        />
        <Modal open={!!removeTarget} onClose={() => { if (!removing) setRemoveTarget(null); }}>
          <ModalHead title="Xoá học viên" sub={removeTarget ? `Học viên sẽ bị xoá khỏi nhóm "${group?.name}"` : ""} icon="x" iconBg="#fef2f2" iconColor="#dc2626" onClose={() => { if (!removing) setRemoveTarget(null); }} />
          <div className="modal-body">
            {removeTarget && (
              <div className="row gap-12" style={{ alignItems: "center", padding: "12px 14px", background: "var(--bg-2)", borderRadius: 10 }}>
                <Avatar name={removeTarget.studentName} size={40} src={removeTarget.avatarUrl} />
                <div>
                  <div style={{ fontWeight: 600, fontSize: 15 }}>{removeTarget.studentName}</div>
                  <div className="t-sm muted">{removeTarget.studentEmail}</div>
                </div>
              </div>
            )}
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => setRemoveTarget(null)} disabled={removing}>Hủy</button>
            <button className="btn" style={{ background: "#dc2626", color: "#fff" }} disabled={removing} onClick={handleRemoveConfirm}>
              {removing ? "Đang xoá..." : "Xác nhận xoá"}
            </button>
          </div>
        </Modal>
      </div>
    );
  }

  function AddMemberModal({ open, onClose, groupId, group, onAdd, searchStudents, addGroupMembers, Ic, Avatar, Modal, ModalHead }) {
    const [searchQuery, setSearchQuery] = useState("");
    const [results, setResults] = useState([]);
    const [selected, setSelected] = useState([]);
    const [adding, setAdding] = useState(false);
    const [showDropdown, setShowDropdown] = useState(false);
    const [error, setError] = useState("");
    const searchRef = useRef(null);
    const debounceRef = useRef(null);

    useEffect(() => {
      if (!open) {
        setSearchQuery("");
        setResults([]);
        setSelected([]);
        setShowDropdown(false);
        setError("");
      }
    }, [open]);

    useEffect(() => {
      if (!open || !searchQuery.trim()) {
        setResults([]);
        setShowDropdown(false);
        return;
      }
      setError("");
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => {
        searchStudents(searchQuery.trim()).then(data => {
          setResults(data || []);
          setShowDropdown(true);
        }).catch(() => {
          setResults([]);
        });
      }, 300);
      return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
    }, [searchQuery, open]);

    useEffect(() => {
      if (!open) return;
      const h = (e) => {
        if (searchRef.current && !searchRef.current.contains(e.target)) {
          setShowDropdown(false);
        }
      };
      document.addEventListener("mousedown", h);
      return () => document.removeEventListener("mousedown", h);
    }, [open]);

    const toggleSelect = useCallback((student) => {
      if (group?.maxCapacity && group?.memberCount != null) {
        const remaining = group.maxCapacity - group.memberCount;
        if (selected.length >= remaining && !selected.find(s => s.id === student.id)) {
          setError("Số lượng vượt quá sức chứa của nhóm");
          return;
        }
      }
      setSelected(prev => {
        const exists = prev.find(s => s.id === student.id);
        if (exists) return prev.filter(s => s.id !== student.id);
        return [...prev, student];
      });
      setError("");
    }, [group, selected]);

    const handleAdd = async () => {
      if (selected.length === 0) return;
      setAdding(true);
      setError("");
      try {
        const emails = selected.map(s => s.email);
        await addGroupMembers(groupId, { emails });
        onAdd();
        onClose();
      } catch (err) {
        if (err.response) {
          setError(err.response.data?.message || err.message || "Có lỗi xảy ra");
        } else if (err.code === "ECONNABORTED") {
          setError("Kết nối đến máy chủ bị timeout, vui lòng thử lại");
        } else {
          setError("Không thể kết nối đến máy chủ, vui lòng kiểm tra lại kết nối");
        }
      } finally {
        setAdding(false);
      }
    };

    const existingEmails = new Set((group?.members || []).map(m => m.studentEmail?.toLowerCase()));
    const filteredResults = results.filter(s => !existingEmails.has(s.email?.toLowerCase()));

    return (
      <Modal open={open} onClose={onClose} max="520px">
        <ModalHead title="Thêm học viên" sub="Tìm kiếm bằng email, chọn nhiều học viên" icon="users" iconBg="#eaf1ff" iconColor="#2563eb" onClose={onClose} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", minHeight: 320 }}>
          {error && (
            <div style={{ padding: "10px 14px", borderRadius: 8, background: "#fef2f2", color: "#b91c1c", fontSize: 14, marginBottom: 12, flex: "none" }}>{error}</div>
          )}
          <div ref={searchRef} style={{ position: "relative", flex: "none" }}>
            <div className="field-icon">
              <Ic n="search" />
              <input
                className="input"
                placeholder="Nhập email học viên..."
                value={searchQuery}
                onChange={(e) => { setSearchQuery(e.target.value); setShowDropdown(true); }}
                onFocus={() => { if (results.length > 0) setShowDropdown(true); }}
                disabled={adding}
              />
              {showDropdown && filteredResults.length > 0 && (
                <span onClick={() => setShowDropdown(false)}
                  style={{ position: "absolute", right: 12, top: "50%", transform: "translateY(-50%)", cursor: "pointer", fontSize: 18, lineHeight: 1, color: "var(--text-3)", userSelect: "none" }}
                >×</span>
              )}
            </div>
            {showDropdown && filteredResults.length > 0 && (
              <div className="card" style={{
                position: "absolute", top: "100%", left: 0, right: 0, zIndex: 10,
                marginTop: 4, maxHeight: 200, overflowY: "auto", padding: 4,
                boxShadow: "0 8px 24px rgba(0,0,0,.12)"
              }}>
                {filteredResults.map(s => {
                  const isSel = selected.find(x => x.id === s.id);
                  return (
                    <div key={s.id} className="row gap-10" style={{
                      padding: "9px 10px", borderRadius: 8, cursor: "pointer",
                      alignItems: "center", transition: "background .15s",
                      background: isSel ? "#eef2ff" : "transparent"
                    }} onClick={() => toggleSelect(s)}
                       onMouseEnter={(e) => { if (!isSel) e.currentTarget.style.background = "var(--bg-2)"; }}
                       onMouseLeave={(e) => { if (!isSel) e.currentTarget.style.background = "transparent"; }}>
                      <Avatar name={s.fullName} size={34} src={s.avatarUrl} />
                      <div className="grow" style={{ minWidth: 0 }}>
                        <div className="truncate" style={{ fontSize: 14, fontWeight: 600 }}>{s.fullName}</div>
                        <div className="t-sm muted truncate">{s.email}</div>
                      </div>
                      <span style={{ width: 22, display: "flex", alignItems: "center", justifyContent: "center", flex: "none" }}>
                        {isSel && <Ic n="check_circle" size={20} style={{ color: "var(--primary)" }} />}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
          {selected.length > 0 && (
            <div style={{ marginTop: 16, flex: "1", overflowY: "auto", border: "1px solid var(--border)", borderRadius: 8 }}>
              <div style={{ display: "table", width: "100%", borderCollapse: "collapse" }}>
                <div style={{ display: "table-header-group" }}>
                  <div style={{ display: "table-row", fontSize: 12, fontWeight: 600, color: "var(--text-3)", textTransform: "uppercase" }}>
                    <div style={{ display: "table-cell", padding: "8px 12px", borderBottom: "1px solid var(--border)" }}>Học viên</div>
                    <div style={{ display: "table-cell", padding: "8px 12px", borderBottom: "1px solid var(--border)" }}>Email</div>
                    <div style={{ display: "table-cell", padding: "8px 12px", borderBottom: "1px solid var(--border)", width: 40 }}></div>
                  </div>
                </div>
                <div style={{ display: "table-row-group" }}>
                  {selected.map(s => (
                    <div key={s.id} style={{ display: "table-row", fontSize: 14 }}>
                      <div style={{ display: "table-cell", padding: "10px 12px", borderBottom: "1px solid var(--border)", verticalAlign: "middle" }}>
                        <div className="row gap-8" style={{ alignItems: "center" }}>
                          <Avatar name={s.fullName} size={28} src={s.avatarUrl} />
                          <span style={{ fontWeight: 500 }}>{s.fullName}</span>
                        </div>
                      </div>
                      <div style={{ display: "table-cell", padding: "10px 12px", borderBottom: "1px solid var(--border)", color: "var(--text-2)", verticalAlign: "middle" }}>{s.email}</div>
                      <div style={{ display: "table-cell", padding: "10px 12px", borderBottom: "1px solid var(--border)", verticalAlign: "middle", textAlign: "center" }}>
                        <span onClick={() => toggleSelect(s)} style={{ cursor: "pointer", fontSize: 18, lineHeight: 1, color: "var(--text-3)", userSelect: "none" }}>×</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
          {filteredResults.length === 0 && searchQuery.trim() && results && !error && (
            <div className="t-sm muted" style={{ marginTop: 12, textAlign: "center", flex: "none" }}>Không tìm thấy học viên phù hợp</div>
          )}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose} disabled={adding}>Hủy</button>
          <button className="btn btn-primary" disabled={selected.length === 0 || adding} onClick={handleAdd}>
            {adding ? "Đang thêm..." : `Thêm ${selected.length} học viên`}
          </button>
        </div>
      </Modal>
      );
  }

  function CsvAddMemberModal({ open, onClose, groupId, onAdd, previewGroupMembersCsvImport, confirmGroupMembersCsvImport, Ic, Modal, ModalHead }) {
    const [file, setFile] = useState(null);
    const [fileName, setFileName] = useState("");
    const [preview, setPreview] = useState(null);
    const [confirmResult, setConfirmResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [activeTab, setActiveTab] = useState("valid");
    const fileInputRef = useRef(null);

    const STATUS_META = {
      VALID: { label: "Hợp lệ", color: "#059669", bg: "#e7f8f0" },
      FORMAT_ERROR: { label: "Sai định dạng", color: "#d97706", bg: "#fef5e6" },
      DUPLICATE_IN_FILE: { label: "Trùng trong file", color: "#dc2626", bg: "#fef2f2" },
      NOT_FOUND: { label: "Không tìm thấy", color: "#7c3aed", bg: "#f3edff" },
      ALREADY_IN_GROUP: { label: "Đã có trong nhóm", color: "#2563eb", bg: "#eaf1ff" },
      CAPACITY_EXCEEDED: { label: "Vượt sức chứa", color: "#d97706", bg: "#fef5e6" },
      IMPORTED: { label: "Đã thêm", color: "#059669", bg: "#e7f8f0" },
      IMPORT_FAILED: { label: "Thêm thất bại", color: "#dc2626", bg: "#fef2f2" },
    };

    useEffect(() => {
      if (!open) reset();
    }, [open]);

    const reset = () => {
      setFile(null);
      setFileName("");
      setPreview(null);
      setConfirmResult(null);
      setLoading(false);
      setError("");
      setActiveTab("valid");
    };

    const close = () => {
      if (!loading) { reset(); onClose(); }
    };

    const handleFile = (e) => {
      e.preventDefault?.();
      const f = e.dataTransfer?.files?.[0] || e.target?.files?.[0];
      if (!f) return;
      if (!f.name.toLowerCase().endsWith(".csv")) {
        setError("Vui lòng chọn file .csv");
        return;
      }
      setFile(f);
      setFileName(f.name);
      setPreview(null);
      setConfirmResult(null);
      setError("");
    };

    const downloadTemplate = () => {
      const blob = new Blob(["\uFEFFemail\nstudent1@example.com\nstudent2@example.com\n"], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "mau_them_hoc_vien_vao_nhom.csv";
      a.click();
      URL.revokeObjectURL(url);
    };

    const handlePreview = async () => {
      if (!file) return;
      setLoading(true);
      setError("");
      try {
        const data = await previewGroupMembersCsvImport(groupId, file);
        setPreview(data);
        setConfirmResult(null);
        setActiveTab(data.validCount > 0 ? "valid" : "error");
      } catch (err) {
        setError(err?.response?.data?.message || err?.message || "Không thể kiểm tra file CSV");
      } finally {
        setLoading(false);
      }
    };

    const handleConfirm = async () => {
      if (!preview?.token || preview.validCount === 0) return;
      setLoading(true);
      setError("");
      try {
        const result = await confirmGroupMembersCsvImport(groupId, preview.token);
        setConfirmResult(result);
        if (result.successCount > 0) onAdd();
      } catch (err) {
        setError(err?.response?.data?.message || err?.message || "Không thể thêm học viên từ CSV");
      } finally {
        setLoading(false);
      }
    };

    const rows = confirmResult?.results || preview?.rows || [];
    const tabs = preview ? [
      { key: "valid", label: `Hợp lệ (${preview.validCount})`, statuses: ["VALID"] },
      { key: "error", label: `Cần xem lại (${preview.totalRows - preview.validCount})`, statuses: ["FORMAT_ERROR", "DUPLICATE_IN_FILE", "NOT_FOUND", "ALREADY_IN_GROUP", "CAPACITY_EXCEEDED"] },
    ] : [];
    const visibleRows = confirmResult ? rows : rows.filter(r => (tabs.find(t => t.key === activeTab)?.statuses || []).includes(r.status));

    return (
      <Modal open={open} onClose={close} max="680px">
        <ModalHead title="Thêm học viên bằng CSV" sub="File CSV chỉ cần cột email hoặc mỗi dòng một email" icon="upload" iconBg="#eaf1ff" iconColor="#2563eb" onClose={close} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          {error && <div style={{ padding: "10px 14px", borderRadius: 8, background: "#fef2f2", color: "#b91c1c", fontSize: 14 }}>{error}</div>}
          <div className="between" style={{ gap: 12 }}>
            <div>
              <div style={{ fontWeight: 700, fontSize: 15 }}>Tải lên file CSV</div>
              <div className="t-sm muted">Hỗ trợ cột <b>email</b> hoặc danh sách email từng dòng.</div>
            </div>
            <button className="btn btn-ghost btn-sm" onClick={downloadTemplate}><Ic n="download" size={15} />Tải file mẫu</button>
          </div>
          <div
            style={{ border: "2px dashed var(--border)", borderRadius: 12, padding: 28, textAlign: "center", cursor: loading ? "not-allowed" : "pointer", background: fileName ? "var(--bg-2)" : "transparent" }}
            onDragOver={e => { e.preventDefault(); e.currentTarget.style.borderColor = "#2563eb"; }}
            onDragLeave={e => { e.currentTarget.style.borderColor = "var(--border)"; }}
            onDrop={handleFile}
            onClick={() => !loading && fileInputRef.current?.click()}
          >
            <Ic n={fileName ? "file" : "upload"} size={30} style={{ color: fileName ? "#2563eb" : "var(--text-3)", marginBottom: 8 }} />
            <div style={{ fontWeight: 600 }}>{fileName || "Kéo thả file CSV vào đây"}</div>
            <div className="t-sm muted" style={{ marginTop: 4 }}>{fileName ? "Nhấp để chọn file khác" : "hoặc nhấp để chọn file"}</div>
            <input ref={fileInputRef} type="file" accept=".csv" hidden onChange={handleFile} disabled={loading} />
          </div>

          {preview && !confirmResult && (
            <>
              <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))", gap: 10 }}>
                {[
                  { label: "Hợp lệ", count: preview.validCount, meta: STATUS_META.VALID },
                  { label: "Sai định dạng", count: preview.formatErrorCount, meta: STATUS_META.FORMAT_ERROR },
                  { label: "Trùng file", count: preview.duplicateInFileCount, meta: STATUS_META.DUPLICATE_IN_FILE },
                  { label: "Không tìm thấy", count: preview.notFoundCount, meta: STATUS_META.NOT_FOUND },
                  { label: "Đã có nhóm", count: preview.alreadyInGroupCount, meta: STATUS_META.ALREADY_IN_GROUP },
                  { label: "Vượt sức chứa", count: preview.capacityExceededCount, meta: STATUS_META.CAPACITY_EXCEEDED },
                ].filter(x => x.count > 0).map(x => (
                  <div key={x.label} style={{ background: x.meta.bg, color: x.meta.color, borderRadius: 10, padding: "10px 12px" }}>
                    <div style={{ fontSize: 20, fontWeight: 800 }}>{x.count}</div>
                    <div style={{ fontSize: 12, fontWeight: 600 }}>{x.label}</div>
                  </div>
                ))}
              </div>
              <div className="row gap-8">
                {tabs.map(t => <button key={t.key} className={activeTab === t.key ? "btn btn-primary btn-sm" : "btn btn-ghost btn-sm"} onClick={() => setActiveTab(t.key)}>{t.label}</button>)}
              </div>
            </>
          )}

          {confirmResult && (
            <div style={{ padding: "10px 14px", borderRadius: 8, background: confirmResult.failCount > 0 ? "#fef5e6" : "#e7f8f0", color: confirmResult.failCount > 0 ? "#92400e" : "#059669", fontSize: 14 }}>
              Đã thêm thành công {confirmResult.successCount}/{confirmResult.totalProcessed} học viên.
            </div>
          )}

          {visibleRows.length > 0 && (
            <div style={{ maxHeight: 260, overflow: "auto", border: "1px solid var(--border)", borderRadius: 10 }}>
              <table className="tbl" style={{ width: "100%" }}>
                <thead><tr><th>Dòng</th><th>Email</th><th>Trạng thái</th><th>Ghi chú</th></tr></thead>
                <tbody>{visibleRows.map(r => {
                  const meta = STATUS_META[r.status] || STATUS_META.FORMAT_ERROR;
                  return <tr key={r.rowNumber + r.email}>
                    <td>{r.rowNumber}</td>
                    <td className="muted">{r.email}</td>
                    <td><span style={{ padding: "3px 8px", borderRadius: 999, background: meta.bg, color: meta.color, fontSize: 12, fontWeight: 700 }}>{meta.label}</span></td>
                    <td className="muted">{(r.errors || []).join(", ") || "—"}</td>
                  </tr>;
                })}</tbody>
              </table>
            </div>
          )}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={close} disabled={loading}>{confirmResult ? "Đóng" : "Hủy"}</button>
          {!confirmResult && <button className="btn btn-ghost" onClick={handlePreview} disabled={!file || loading}>{loading ? "Đang kiểm tra..." : "Kiểm tra file"}</button>}
          {!confirmResult && <button className="btn btn-primary" onClick={handleConfirm} disabled={!preview || preview.validCount === 0 || loading}>{loading ? "Đang thêm..." : `Thêm ${preview?.validCount || 0} học viên`}</button>}
        </div>
      </Modal>
    );
  }

  Object.assign(window, { InsGroupDetail });
})();
