// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Quản lý Người dùng (Backend API)
   ============================================================ */
(function () {
  const { useState, useEffect, useCallback } = React;
  const Ic = window.Icon;
  const { Avatar, Status, StatCard, Search, Tabs, Select, Section, Pager, Modal, ModalHead, Empty } = window;

    const { getUsers, createUser, updateUser, adminResetPassword } = window.__userService;

    const { getCourses } = window.__courseService;

  const ROLE_LABEL = { STUDENT:'Học viên', INSTRUCTOR:'Giảng viên', ADMIN:'Quản trị viên' };
  const ROLE_CHIP = { STUDENT:'neutral', INSTRUCTOR:'info', ADMIN:'warning' };
  const FIELD_LABEL = { fullName:'Họ tên', email:'Email', role:'Vai trò', phoneNumber:'Số điện thoại', status:'Trạng thái' };
  const ROLE_OPTIONS = [
    { v:'STUDENT', label:'Học viên' },
    { v:'INSTRUCTOR', label:'Giảng viên' },
    { v:'ADMIN', label:'Quản trị viên' },
  ];

  function formatDate(d) {
    if (!d) return '—';
    const date = new Date(d);
    return date.toLocaleDateString('vi-VN', { day:'2-digit', month:'2-digit', year:'numeric' });
  }

  function timeAgo(d) {
    if (!d) return '—';
    const now = new Date();
    const diff = now - new Date(d);
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'Vừa xong';
    if (mins < 60) return `${mins} phút trước`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours} giờ trước`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days} ngày trước`;
    return formatDate(d);
  }

  function AdminUsers() {
    const [tab, setTab] = useState('all');
    const [q, setQ] = useState('');
    const [debouncedQ, setDebouncedQ] = useState('');
    const [page, setPage] = useState(1);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [data, setData] = useState({ items:[], totalRecords:0, totalPages:1, page:1, size:10 });
    const [toast, setToast] = useState(null);

    // Modal states
    const [addOpen, setAddOpen] = useState(false);
    const [editOpen, setEditOpen] = useState(false);
    const [resetPwOpen, setResetPwOpen] = useState(false);
    const [detailOpen, setDetailOpen] = useState(false);
    const [csvOpen, setCsvOpen] = useState(false);
    const [detailUser, setDetailUser] = useState(null);

    // Form states
    const [selectedUser, setSelectedUser] = useState(null);
    const [formFullName, setFormFullName] = useState('');
    const [formEmail, setFormEmail] = useState('');
    const [formRole, setFormRole] = useState('STUDENT');
    const [formPhone, setFormPhone] = useState('');
    const [formStatus, setFormStatus] = useState('ACTIVE');
    const [courses, setCourses] = useState([]);
    const [coursesLoading, setCoursesLoading] = useState(false);
    const [formCourseId, setFormCourseId] = useState('');

    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});

    const size = 10;

    const showToast = useCallback((msg, type) => {
      setToast({ msg, type });
      setTimeout(() => setToast(null), 5000);
    }, []);

    function extractError(err) {
      const data = err?.response?.data;
      if (!data) return { msg: 'Không thể kết nối tới máy chủ', fields: {} };
      const fields = data.validationErrors || {};
      const msg = data.message || 'Lỗi không xác định';
      return { msg, fields };
    }

    function clearFormError() {
      setFormError(null);
      setFieldErrors({});
    }

    const fetchUsers = useCallback(async () => {
      setLoading(true);
      setError(null);
      try {
        const params = { page, size };
        if (debouncedQ) params.search = debouncedQ;
        if (tab !== 'all') params.role = tab;
        const result = await getUsers(params);
        setData(result);
      } catch (err) {
        const msg = err.response?.data?.message || 'Không thể tải danh sách người dùng';
        setError(msg);
        showToast(msg, 'error');
      } finally {
        setLoading(false);
      }
    }, [page, size, debouncedQ, tab, showToast]);

    useEffect(() => { fetchUsers(); }, [fetchUsers]);

    useEffect(() => {
      if (!addOpen) return;
      setCoursesLoading(true);
      getCourses({ size: 100 }).then(res => {
        const data = res.data || res;
        setCourses(data.content || data || []);
      }).catch(() => {}).finally(() => setCoursesLoading(false));
    }, [addOpen]);

    // Debounce search
    useEffect(() => {
      const t = setTimeout(() => { setDebouncedQ(q); setPage(1); }, 400);
      return () => clearTimeout(t);
    }, [q]);

    const handleTabChange = useCallback((v) => {
      setTab(v);
      setPage(1);
    }, []);

    const handleSearchChange = useCallback((v) => {
      setQ(v);
    }, []);

    // --- Create ---
    const openAddModal = useCallback(() => {
      clearFormError();
      setFormFullName('');
      setFormEmail('');
      setFormRole('STUDENT');
      setFormPhone('');
      setFormCourseId('');
      setAddOpen(true);
    }, []);

    const handleCreate = useCallback(async () => {
      if (!formFullName || !formEmail) {
        setFormError('Vui lòng nhập họ tên, email, số điện thoại');
        return;
      }
      setSubmitting(true);
      clearFormError();
      try {
        await createUser({ fullName:formFullName, email:formEmail, role:formRole, phoneNumber:formPhone || undefined, courseId: formCourseId || undefined });
        setAddOpen(false);
        showToast('Tạo tài khoản thành công', 'success');
        fetchUsers();
      } catch (err) {
        const { msg, fields } = extractError(err);
        setFormError(msg);
        setFieldErrors(fields);
        showToast(msg, 'error');
      } finally {
        setSubmitting(false);
      }
    }, [formFullName, formEmail, formRole, formPhone, formCourseId, fetchUsers, showToast]);

    // --- Edit ---
    const openEditModal = useCallback((user) => {
      clearFormError();
      setSelectedUser(user);
      setFormFullName(user.fullName);
      setFormEmail(user.email);
      setFormRole(user.role);
      setFormPhone(user.phoneNumber || '');
      setFormStatus(user.status);
      setEditOpen(true);
    }, []);

    const handleUpdate = useCallback(async () => {
      if (!selectedUser) return;
      setSubmitting(true);
      clearFormError();
      try {
        const body = {};
        if (formFullName !== selectedUser.fullName) body.fullName = formFullName;
        if (formEmail !== selectedUser.email) body.email = formEmail;
        if (formRole !== selectedUser.role) body.role = formRole;
        if (formPhone !== (selectedUser.phoneNumber || '')) body.phoneNumber = formPhone || null;
        if (Object.keys(body).length === 0) { setEditOpen(false); return; }
        await updateUser(selectedUser.id, body);
        setEditOpen(false);
        showToast('Cập nhật thành công', 'success');
        fetchUsers();
      } catch (err) {
        const { msg, fields } = extractError(err);
        setFormError(msg);
        setFieldErrors(fields);
        showToast(msg, 'error');
      } finally {
        setSubmitting(false);
      }
    }, [selectedUser, formFullName, formEmail, formRole, formPhone, fetchUsers, showToast]);

    // --- Toggle status (lock/unlock) ---
    const handleToggleStatus = useCallback(async (user) => {
      const newStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
      setSubmitting(true);
      try {
        await updateUser(user.id, { status:newStatus });
        showToast(newStatus === 'DISABLED' ? 'Đã vô hiệu hóa tài khoản' : 'Đã kích hoạt tài khoản', 'success');
        fetchUsers();
      } catch (err) {
        const { msg } = extractError(err);
        showToast(msg, 'error');
      } finally {
        setSubmitting(false);
      }
    }, [fetchUsers, showToast]);

    // --- Reset password ---
    const openResetPwModal = useCallback((user) => {
      clearFormError();
      setSelectedUser(user);
      setResetPwOpen(true);
    }, []);

    const handleResetPassword = useCallback(async () => {
      if (!selectedUser) return;
      setSubmitting(true);
      clearFormError();
      try {
        await adminResetPassword(selectedUser.id, 'Yêu cầu từ admin');
        setResetPwOpen(false);
        showToast('Đã gửi mật khẩu mới tới email người dùng', 'success');
      } catch (err) {
        const { msg } = extractError(err);
        setFormError(msg);
        showToast(msg, 'error');
      } finally {
        setSubmitting(false);
      }
    }, [selectedUser, showToast]);

    // --- Detail ---
    const openDetail = useCallback(async (user) => {
      setDetailUser(user);
      setDetailOpen(true);
    }, []);

    const handleCsvImportSuccess = useCallback(() => {
      showToast('Nhập dữ liệu từ CSV thành công', 'success');
      fetchUsers();
    }, [showToast, fetchUsers]);

    const pg = {
      page: data.page,
      pages: data.totalPages,
      total: data.totalRecords,
      from: data.totalRecords ? (data.page - 1) * data.size + 1 : 0,
      to: Math.min(data.page * data.size, data.totalRecords),
      setPage,
    };

    return (
      <div className="page fade-in">
        {toast && (
          <div style={{
            position:'fixed', top:16, right:16, zIndex:9999,
            background: toast.type === 'error' ? 'var(--error)' : 'var(--success)',
            color:'#fff', padding:'12px 20px', borderRadius:10, fontSize:14,
            boxShadow:'0 4px 12px rgba(0,0,0,.15)', maxWidth:400,
          }}>
            {toast.msg}
          </div>
        )}

        <div className="page-head between">
          <div>
            <h1 className="t-h1">Quản lý Người dùng</h1>
            <p>Quản lý tài khoản, phân quyền và trạng thái hoạt động của toàn hệ thống.</p>
          </div>
          <div style={{ display:'flex', gap:8 }}>
            <button className="btn btn-outline" onClick={() => setCsvOpen(true)}>
              <Ic n="upload" size={17} />Nhập từ CSV
            </button>
            <button className="btn btn-primary" onClick={openAddModal}>
              <Ic n="plus" size={17} />Thêm người dùng
            </button>
          </div>
        </div>

        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          <StatCard icon="users" iconBg="#eaf1ff" iconColor="#2563eb" value={data.totalRecords.toLocaleString()} label="Tổng người dùng" />
          <StatCard icon="cap" iconBg="#f3edff" iconColor="#7c3aed" value={data.items.filter(u => u.role === 'INSTRUCTOR').length} label="Giảng viên" />
          <StatCard icon="user" iconBg="#e7f8f0" iconColor="#059669" value={data.items.filter(u => u.role === 'STUDENT').length} label="Học viên" />
          <StatCard icon="lock" iconBg="#fdecec" iconColor="#dc2626" value={data.items.filter(u => u.status === 'DISABLED').length} label="Tài khoản bị khóa" />
        </div>

        <div className="toolbar">
          <Tabs
            items={[
              { v:'all', label:'Tất cả' },
              { v:'STUDENT', label:'Học viên' },
              { v:'INSTRUCTOR', label:'Giảng viên' },
              { v:'ADMIN', label:'Quản trị' },
            ]}
            value={tab}
            onChange={handleTabChange}
          />
          <div className="grow" />
          <Search placeholder="Tìm theo tên, email..." value={q} onChange={handleSearchChange} style={{ width: 280, flex: 'none' }} />
          <button className="btn btn-ghost btn-icon"><Ic n="filter" size={18} /></button>
        </div>

        <Section pad={false}>
          {loading && (
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-3)' }}>
              Đang tải dữ liệu...
            </div>
          )}
          {!loading && error && (
            <Empty icon="alert" title="Lỗi tải dữ liệu" text={error} />
          )}
          {!loading && !error && data.items.length === 0 && (
            <Empty icon="users" title="Không tìm thấy người dùng nào" text="Thử thay đổi bộ lọc hoặc tạo người dùng mới." />
          )}
          {!loading && !error && data.items.length > 0 && (
            <div style={{ overflowX: 'auto' }}>
              <table className="tbl">
                <thead>
                  <tr>
                    <th style={{ width: 50 }}>STT</th>
                    <th>Người dùng</th>
                    <th>Vai trò</th>
                    <th>Trạng thái</th>
                    <th>Email</th>
                    <th>SĐT</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((u, index) => (
                    <tr key={u.id}>
                      <td style={{ textAlign: 'center', verticalAlign: 'middle', fontWeight: 600 }}>
                        {(data.page - 1) * 10 + index + 1}
                      </td>
                      <td>
                        <div className="row gap-11" style={{ cursor: 'pointer' }} onClick={() => openDetail(u)}>
                          <Avatar name={u.fullName} size={38} />
                          <div>
                            <div style={{ fontWeight: 700, fontSize: 14 }}>{u.fullName}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className={'chip chip-' + ROLE_CHIP[u.role]}>{ROLE_LABEL[u.role] || u.role}</span>
                      </td>
                      <td><Status s={u.status === 'ACTIVE' ? 'active' : u.status === 'DISABLED' ? 'disabled' : 'neutral'} /></td>
                      <td className="muted">{u.email}</td>
                      <td className="muted">{u.phoneNumber || '—'}</td>
                      <td>
                        <div className="row gap-6">
                          <button
                            className="icon-btn"
                            style={{ width: 34, height: 34 }}
                            onClick={() => openEditModal(u)}
                            title="Chỉnh sửa"
                          >
                            <Ic n="edit" size={16} />
                          </button>
                          <button
                            className="icon-btn"
                            style={{
                              width: 34, height: 34,
                              color: u.status === 'ACTIVE' ? 'var(--error)' : 'var(--success)',
                            }}
                            onClick={() => handleToggleStatus(u)}
                            title={u.status === 'ACTIVE' ? 'Vô hiệu hóa' : 'Kích hoạt'}
                          >
                            <Ic n="lock" size={16} />
                          </button>
                          <button
                            className="icon-btn"
                            style={{ width: 34, height: 34, color: 'var(--text-3)' }}
                            onClick={() => openResetPwModal(u)}
                            title="Reset mật khẩu"
                          >
                            <Ic n="clock" size={16} />
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

        {data.totalRecords > 0 && (
          <div className="between wrap pagebar" style={{ gap: 12 }}>
            <span className="t-sm muted">
              Hiển thị <b style={{ color: 'var(--text)' }}>{pg.from}–{pg.to}</b> trong tổng số <b style={{ color: 'var(--text)' }}>{pg.total}</b> người dùng
            </span>
            {pg.pages > 1 && (
              <Pager page={pg.page} pages={pg.pages} onPage={pg.setPage} />
            )}
          </div>
        )}

        {/* ---------- Add Modal ---------- */}
        <Modal open={addOpen} onClose={() => setAddOpen(false)}>
          <ModalHead title="Thêm người dùng mới" sub="Tạo tài khoản cho nhân sự hoặc học viên" icon="users" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => setAddOpen(false)} />
          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {formError && (
              <div style={{ background: 'var(--chip-error-bg)', borderRadius: 11, padding: '12px 14px', fontSize: 13, color: 'var(--chip-error-fg)', display: 'flex', flexDirection: 'column', gap: 4 }}>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}><Ic n="warn" size={16} style={{ flex: 'none' }} /><span style={{ fontWeight: 600 }}>{formError}</span></div>
                {Object.keys(fieldErrors).length > 0 && (
                  <ul style={{ margin: '4px 0 0 24px', padding: 0 }}>
                    {Object.entries(fieldErrors).map(([field, msg]) => (
                      <li key={field} style={{ fontSize: 12 }}><b>{FIELD_LABEL[field] || field}:</b> {msg}</li>
                    ))}
                  </ul>
                )}
              </div>
            )}
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Họ và tên</label>
                <input className="input" placeholder="Nguyễn Văn A" value={formFullName} onChange={e => setFormFullName(e.target.value)} style={fieldErrors.fullName ? { borderColor: 'var(--error)' } : {}} />
                {fieldErrors.fullName && <div className="t-xs" style={{ color: 'var(--error)', marginTop: 4 }}>{fieldErrors.fullName}</div>}
              </div>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Email</label>
                <input className="input" placeholder="email@rikkei.edu" value={formEmail} onChange={e => setFormEmail(e.target.value)} style={fieldErrors.email ? { borderColor: 'var(--error)' } : {}} />
                {fieldErrors.email && <div className="t-xs" style={{ color: 'var(--error)', marginTop: 4 }}>{fieldErrors.email}</div>}
              </div>
            </div>
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Vai trò</label>
                <Select value={formRole} onChange={setFormRole} options={ROLE_OPTIONS} />
              </div>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Số điện thoại</label>
                <input className="input" placeholder="09xx xxx xxx" value={formPhone} onChange={e => setFormPhone(e.target.value)} />
              </div>
            </div>
            <div style={{ background: 'var(--chip-info-bg)', borderRadius: 11, padding: '12px 14px', fontSize: 13, color: 'var(--chip-info-fg)', display: 'flex', gap: 10 }}>
              <Ic n="mail" size={18} style={{ flex: 'none' }} />Mật khẩu khởi tạo sẽ được gửi tới email của người dùng.
            </div>
            {formRole === 'STUDENT' && (
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Khoá học</label>
                <select value={formCourseId} onChange={e => setFormCourseId(e.target.value)}
                  style={{ width: '100%', height: 36, borderRadius: 8, border: '1px solid var(--border)',
                    padding: '0 10px', fontSize: 13, outline: 'none', background: '#fff' }}>
                  <option value="" disabled>{coursesLoading ? 'Đang tải...' : 'Chọn khoá học'}</option>
                  {courses.map(c => (
                    <option key={c.id} value={c.id}>{c.title}</option>
                  ))}
                </select>
              </div>
            )}
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => setAddOpen(false)} disabled={submitting}>Hủy</button>
            <button className="btn btn-primary" onClick={handleCreate} disabled={submitting}>
              {submitting ? 'Đang tạo...' : 'Tạo tài khoản'}
            </button>
          </div>
        </Modal>

        {/* ---------- Edit Modal ---------- */}
        <Modal open={editOpen} onClose={() => setEditOpen(false)}>
          <ModalHead title="Chỉnh sửa người dùng" sub={selectedUser?.fullName || ''} icon="edit" iconBg="#f3edff" iconColor="#7c3aed" onClose={() => setEditOpen(false)} />
          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {formError && (
              <div style={{ background: 'var(--chip-error-bg)', borderRadius: 11, padding: '12px 14px', fontSize: 13, color: 'var(--chip-error-fg)', display: 'flex', gap: 8 }}>
                <Ic n="warn" size={16} style={{ flex: 'none' }} /><span>{formError}</span>
              </div>
            )}
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Họ và tên</label>
                <input className="input" value={formFullName} onChange={e => setFormFullName(e.target.value)} />
              </div>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Email</label>
                <input className="input" value={formEmail} onChange={e => setFormEmail(e.target.value)} />
              </div>
            </div>
            <div className="grid grid-2" style={{ gap: 14 }}>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Vai trò</label>
                <Select value={formRole} onChange={setFormRole} options={ROLE_OPTIONS} />
              </div>
              <div>
                <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Số điện thoại</label>
                <input className="input" value={formPhone} onChange={e => setFormPhone(e.target.value)} />
              </div>
            </div>
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => setEditOpen(false)} disabled={submitting}>Hủy</button>
            <button className="btn btn-primary" onClick={handleUpdate} disabled={submitting}>
              {submitting ? 'Đang lưu...' : 'Lưu thay đổi'}
            </button>
          </div>
        </Modal>

        {/* ---------- Reset Password Confirm ---------- */}
        <Modal open={resetPwOpen} onClose={() => setResetPwOpen(false)} max={420}>
          <ModalHead title="Reset mật khẩu" sub={`Gửi mật khẩu mới cho "${selectedUser?.fullName}"?`} icon="clock" iconBg="#f3edff" iconColor="#7c3aed" onClose={() => setResetPwOpen(false)} />
          <div className="modal-body">
            {formError && (
              <div style={{ background: 'var(--chip-error-bg)', borderRadius: 11, padding: '12px 14px', fontSize: 13, color: 'var(--chip-error-fg)', display: 'flex', gap: 8, marginBottom: 12 }}>
                <Ic n="warn" size={16} style={{ flex: 'none' }} /><span>{formError}</span>
              </div>
            )}
            <p style={{ color: 'var(--text-2)', fontSize: 14, lineHeight: 1.6 }}>
              Mật khẩu mới sẽ được gửi tới email <b>{selectedUser?.email}</b>.
            </p>
          </div>
          <div className="modal-foot">
            <button className="btn btn-ghost" onClick={() => setResetPwOpen(false)} disabled={submitting}>Hủy</button>
            <button className="btn btn-primary" onClick={handleResetPassword} disabled={submitting}>
              {submitting ? 'Đang gửi...' : 'Gửi mật khẩu mới'}
            </button>
          </div>
        </Modal>

        {/* ---------- Detail Modal ---------- */}
        <Modal open={detailOpen} onClose={() => setDetailOpen(false)} max={480}>
          {detailUser && (
            <>
              <ModalHead title="Chi tiết người dùng" sub={detailUser.fullName} icon="users" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => setDetailOpen(false)} />
              <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div className="row gap-16">
                  <Avatar name={detailUser.fullName} size={56} />
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 16 }}>{detailUser.fullName}</div>
                    <div className="muted t-sm">{detailUser.email}</div>
                    <div className="row gap-8" style={{ marginTop: 6 }}>
                      <span className={'chip chip-' + ROLE_CHIP[detailUser.role]}>{ROLE_LABEL[detailUser.role] || detailUser.role}</span>
                      <Status s={detailUser.status === 'ACTIVE' ? 'active' : detailUser.status === 'DISABLED' ? 'disabled' : 'neutral'} />
                    </div>
                  </div>
                </div>
                <div className="grid grid-2" style={{ gap: 12, marginTop: 8 }}>
                  <div><div className="t-sm muted">Số điện thoại</div><div style={{ fontWeight: 600 }}>{detailUser.phoneNumber || '—'}</div></div>
                </div>
                <div style={{ marginTop: 8 }} />
              </div>
              <div className="modal-foot">
                <button className="btn btn-ghost" onClick={() => setDetailOpen(false)}>Đóng</button>
                <button className="btn btn-primary" onClick={() => { setDetailOpen(false); openEditModal(detailUser); }}>Chỉnh sửa</button>
              </div>
            </>
          )}
        </Modal>

        {csvOpen && window.CsvImportWizard && (
          <window.CsvImportWizard
            open={csvOpen}
            onClose={() => setCsvOpen(false)}
            onImportSuccess={handleCsvImportSuccess}
          />
        )}
      </div>
    );
  }

  Object.assign(window, { AdminUsers });
})();
