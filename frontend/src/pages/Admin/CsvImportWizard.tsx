(function () {
  const { useState, useCallback, useRef, useEffect } = React;
  const Ic = window.Icon;
  const { Modal, ModalHead, Select } = window;

  const ROLE_OPTIONS = [
    { v: 'STUDENT', label: 'Học viên' },
    { v: 'INSTRUCTOR', label: 'Giảng viên' },
    { v: 'ADMIN', label: 'Quản trị viên' },
  ];

  const STATUS_META = {
    VALID:               { group: 'valid',     label: 'Tài khoản mới',                color: '#059669', bg: '#e7f8f0', icon: 'user-plus' },
    EXISTING_USER:       { group: 'valid',     label: 'Ghi danh vào khoá học',        color: '#2563eb', bg: '#eaf1ff', icon: 'user-check' },
    FORMAT_ERROR:        { group: 'error',     label: 'Lỗi định dạng',                color: '#d97706', bg: '#fef3c7', icon: 'alert-triangle' },
    DUPLICATE_IN_FILE:   { group: 'error',     label: 'Trùng trong file',             color: '#dc2626', bg: '#fdecec', icon: 'x-circle' },
    DUPLICATE_IN_DB:     { group: 'error',     label: 'Đã tồn tại trong hệ thống',    color: '#7c3aed', bg: '#f3edff', icon: 'x-circle' },
    ALREADY_ENROLLED:    { group: 'skipped',   label: 'Đã tham gia khoá học',         color: '#a855f7', bg: '#f3e8ff', icon: 'skip-forward' },
    NAME_MISMATCH:       { group: 'warning',   label: 'Tên không khớp',               color: '#d97706', bg: '#fef3c7', icon: 'alert-triangle' },
    IMPORTED:            { group: 'imported',  label: 'Đã nhập thành công',           color: '#059669', bg: '#e7f8f0', icon: 'check-circle' },
    IMPORT_FAILED:       { group: 'imported',  label: 'Nhập thất bại',                color: '#dc2626', bg: '#fdecec', icon: 'x-circle' },
  };

  function CsvImportWizard({ open, onClose, onImportSuccess }) {
    const [step, setStep] = useState(1);
    const [file, setFile] = useState(null);
    const [fileName, setFileName] = useState('');
    const [defaultRole, setDefaultRole] = useState('STUDENT');
    const [previewData, setPreviewData] = useState(null);
    const [confirmResult, setConfirmResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [activeTab, setActiveTab] = useState('valid');
    const fileInputRef = useRef(null);

    const [courses, setCourses] = useState([]);
    const [coursesLoading, setCoursesLoading] = useState(false);
    const [courseId, setCourseId] = useState('');

    const isStudentRole = defaultRole === 'STUDENT';

    useEffect(() => {
      if (!open) return;
      setCoursesLoading(true);
      window.httpClient.get('/admin/courses?size=100').then(res => {
        const data = res.data || res;
        setCourses(data.content || data || []);
      }).catch(() => {}).finally(() => setCoursesLoading(false));
    }, [open]);

    const reset = useCallback(() => {
      setStep(1);
      setFile(null);
      setFileName('');
      setDefaultRole('STUDENT');
      setPreviewData(null);
      setConfirmResult(null);
      setLoading(false);
      setError(null);
      setActiveTab('valid');
      setCourseId('');
    }, []);

    const handleClose = useCallback(() => {
      if (!loading) { reset(); onClose(); }
    }, [loading, reset, onClose]);

    const handleFileDrop = useCallback((e) => {
      e.preventDefault();
      const f = e.dataTransfer?.files?.[0] || e.target?.files?.[0];
      if (f) {
        if (!f.name.toLowerCase().endsWith('.csv')) {
          setError('Vui lòng chọn file có đuôi .csv');
          return;
        }
        setFile(f);
        setFileName(f.name);
        setError(null);
      }
    }, []);

    const handleDownloadTemplate = useCallback(() => {
      const bom = '\uFEFF';
      const header = 'fullname,email,phone,role';
      const sample = 'Nguyễn Văn A,nguyenvana@rikkei.edu,0912345678,STUDENT';
      const blob = new Blob([bom + header + '\n' + sample + '\n'], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'mau_import_user.csv';
      a.click();
      URL.revokeObjectURL(url);
    }, []);

    const handlePreview = useCallback(async () => {
      if (!file) return;
      if (isStudentRole && !courseId) {
        setError('Vui lòng chọn khoá học cho học viên');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const result = await window.__csvImportService.previewCsvImport(
          file, defaultRole,
          isStudentRole ? courseId : undefined,
          undefined,
        );
        setPreviewData(result);
        setStep(2);
      } catch (err) {
        const msg = err?.response?.data?.message || err?.message || 'Không thể kiểm tra file CSV';
        setError(msg);
      } finally {
        setLoading(false);
      }
    }, [file, defaultRole, courseId, isStudentRole]);

    const handleConfirm = useCallback(async () => {
      if (!previewData) return;
      setLoading(true);
      setError(null);
      try {
        const result = await window.__csvImportService.confirmCsvImport(
          previewData.token,
          isStudentRole ? courseId : undefined,
          undefined,
        );
        setConfirmResult(result);
        setStep(4);
        if (result.successCount > 0 && onImportSuccess) onImportSuccess();
      } catch (err) {
        const msg = err?.response?.data?.message || err?.message || 'Không thể nhập dữ liệu';
        setError(msg);
      } finally {
        setLoading(false);
      }
    }, [previewData, courseId, isStudentRole, onImportSuccess]);

    const statCards = previewData ? [
      { key: 'valid',                  count: previewData.validCount,             meta: STATUS_META.VALID },
      { key: 'existing_user',          count: previewData.existingUserCount,      meta: STATUS_META.EXISTING_USER },
      { key: 'already_enrolled',       count: previewData.alreadyEnrolledCount,   meta: STATUS_META.ALREADY_ENROLLED },
      { key: 'name_mismatch',          count: previewData.nameMismatchCount,      meta: STATUS_META.NAME_MISMATCH },
      { key: 'format_error',           count: previewData.formatErrorCount,       meta: STATUS_META.FORMAT_ERROR },
      { key: 'duplicate_in_file',      count: previewData.duplicateInFileCount,   meta: STATUS_META.DUPLICATE_IN_FILE },
      { key: 'duplicate_in_db',        count: previewData.duplicateInDbCount,     meta: STATUS_META.DUPLICATE_IN_DB },
    ].filter(s => s.count > 0) : [];

    const getRowsByStatuses = (statuses) => {
      if (!previewData) return [];
      return previewData.rows.filter(r => statuses.includes(r.status));
    };

    const renderStepIndicator = () => (
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, alignItems: 'center' }}>
        {[1, 2, 3, 4].map(s => (
          <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1 }}>
            <div style={{
              width: 28, height: 28, borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 13, fontWeight: 700,
              background: step === s ? '#2563eb' : step > s ? '#059669' : 'var(--bg-2)',
              color: step >= s ? '#fff' : 'var(--text-3)',
              border: step >= s ? 'none' : '2px solid var(--border)',
            }}>{step > s ? '\u2713' : s}</div>
            <div style={{
              flex: 1, height: 3, borderRadius: 2,
              background: step > s ? '#059669' : 'var(--border)',
              display: s === 4 ? 'none' : 'block',
            }} />
          </div>
        ))}
      </div>
    );

    const renderStep1 = () => (
      <>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15 }}>Tải lên file CSV</div>
            <div className="t-sm muted">Chọn file CSV chứa danh sách người dùng cần thêm</div>
          </div>
          <button className="btn btn-outline" onClick={handleDownloadTemplate} style={{ fontSize: 13 }}>
            <Ic n="download" size={15} /> Tải file mẫu
          </button>
        </div>

        {error && (
          <div style={{
            background: 'var(--chip-error-bg)', borderRadius: 11, padding: '10px 14px',
            fontSize: 13, color: 'var(--chip-error-fg)', marginBottom: 14,
            display: 'flex', gap: 8, alignItems: 'center',
          }}>
            <Ic n="alert" size={16} style={{ flex: 'none' }} /><span>{error}</span>
          </div>
        )}

        <div
          style={{
            border: '2px dashed var(--border)', borderRadius: 12, padding: 36,
            textAlign: 'center', cursor: 'pointer', transition: '.15s',
            background: fileName ? 'var(--bg-2)' : 'transparent',
          }}
          onDragOver={e => { e.preventDefault(); e.currentTarget.style.borderColor = '#2563eb'; }}
          onDragLeave={e => { e.currentTarget.style.borderColor = 'var(--border)'; }}
          onDrop={handleFileDrop}
          onClick={() => fileInputRef.current?.click()}
        >
          {fileName ? (
            <div>
              <Ic n="file" size={28} style={{ color: '#2563eb', marginBottom: 8 }} />
              <div style={{ fontWeight: 600, color: 'var(--text)' }}>{fileName}</div>
              <div className="t-xs muted" style={{ marginTop: 4 }}>Nhấp để chọn file khác</div>
            </div>
          ) : (
            <div>
              <Ic n="upload" size={32} style={{ color: 'var(--text-3)', marginBottom: 8 }} />
              <div style={{ fontWeight: 600 }}>Kéo thả file CSV vào đây</div>
              <div className="t-sm muted" style={{ marginTop: 4 }}>hoặc nhấp để chọn file</div>
            </div>
          )}
          <input ref={fileInputRef} type="file" accept=".csv" hidden
            onChange={e => handleFileDrop(e)} />
        </div>

        <div style={{ marginTop: 16 }}>
          <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Vai trò mặc định</label>
          <Select value={defaultRole} onChange={v => { setDefaultRole(v); if (v !== 'STUDENT') setCourseId(''); }} options={ROLE_OPTIONS} />
          <div className="t-xs muted" style={{ marginTop: 4 }}>
            Vai trò này sẽ được áp dụng cho tất cả người dùng trong file CSV
          </div>
        </div>

        {isStudentRole && (
          <div style={{ marginTop: 16 }}>
            <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Khoá học <span style={{ color: '#dc2626' }}>*</span></label>
            <select value={courseId} onChange={e => setCourseId(e.target.value)}
              style={{ width: '100%', height: 36, borderRadius: 8, border: '1px solid var(--border)',
                padding: '0 10px', fontSize: 13, outline: 'none', background: '#fff' }}>
              <option value="">{coursesLoading ? 'Đang tải...' : 'Chọn khoá học'}</option>
              {courses.map(c => (
                <option key={c.id} value={c.id}>{c.title}</option>
              ))}
            </select>
          </div>
        )}
      </>
    );

    const renderStep2 = () => {
      if (!previewData) return null;

      const tabs = [
        { key: 'valid',             label: 'Hợp lệ',                          count: previewData.validCount + previewData.existingUserCount, statuses: ['VALID', 'EXISTING_USER'] },
        { key: 'already_enrolled',  label: STATUS_META.ALREADY_ENROLLED.label, count: previewData.alreadyEnrolledCount,                     statuses: ['ALREADY_ENROLLED'] },
        { key: 'name_mismatch',     label: STATUS_META.NAME_MISMATCH.label,    count: previewData.nameMismatchCount,                        statuses: ['NAME_MISMATCH'] },
        { key: 'format_error',      label: STATUS_META.FORMAT_ERROR.label,     count: previewData.formatErrorCount,                         statuses: ['FORMAT_ERROR'] },
        { key: 'duplicate_in_file', label: STATUS_META.DUPLICATE_IN_FILE.label,count: previewData.duplicateInFileCount,                     statuses: ['DUPLICATE_IN_FILE'] },
        { key: 'duplicate_in_db',   label: STATUS_META.DUPLICATE_IN_DB.label,  count: previewData.duplicateInDbCount,                      statuses: ['DUPLICATE_IN_DB'] },
      ].filter(t => t.count > 0);

      const activeTabKey = tabs.some(t => t.key === activeTab) ? activeTab : (tabs[0]?.key || '');
      const currentStatuses = tabs.find(t => t.key === activeTabKey)?.statuses || [];
      const currentRows = getRowsByStatuses(currentStatuses);

      return (
        <>
          <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 14 }}>Kết quả kiểm tra</div>

          <div className="grid" style={{ gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 18 }}>
            {statCards.map(s => (
              <div key={s.key} style={{
                background: s.meta.bg, borderRadius: 11, padding: '12px 14px',
                display: 'flex', flexDirection: 'column', gap: 2,
              }}>
                <div style={{ fontSize: 22, fontWeight: 800, color: s.meta.color }}>{s.count}</div>
                <div style={{ fontSize: 12, fontWeight: 600, color: s.meta.color }}>{s.meta.label}</div>
              </div>
            ))}
          </div>

          {error && (
            <div style={{
              background: 'var(--chip-error-bg)', borderRadius: 11, padding: '10px 14px',
              fontSize: 13, color: 'var(--chip-error-fg)', marginBottom: 14, display: 'flex', gap: 8, alignItems: 'center',
            }}>
              <Ic n="alert" size={16} style={{ flex: 'none' }} /><span>{error}</span>
            </div>
          )}

          <div style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
            {tabs.map(t => (
              <button key={t.key}
                style={{
                  padding: '7px 14px', borderRadius: 8, border: 'none', cursor: 'pointer',
                  fontSize: 13, fontWeight: 600,
                  background: activeTabKey === t.key ? '#e2e8f0' : 'var(--bg-2)',
                  color: activeTabKey === t.key ? '#0f172a' : 'var(--text-3)',
                }}
                onClick={() => setActiveTab(t.key)}>
                {`${t.label} (${t.count})`}
              </button>
            ))}
          </div>

          {currentRows.length > 0 ? (
            <div style={{ maxHeight: 280, overflow: 'auto', borderRadius: 11, border: '1px solid var(--border)' }}>
              <table className="tbl" style={{ width: '100%', fontSize: 13 }}>
                <thead>
                  <tr>
                    <th style={{ padding: '6px 10px', width: 50 }}>Dòng</th>
                    <th style={{ padding: '6px 10px' }}>Họ tên</th>
                    <th style={{ padding: '6px 10px' }}>Email</th>
                    <th style={{ padding: '6px 10px' }}>SĐT</th>
                    <th style={{ padding: '6px 10px' }}>Ghi chú</th>
                  </tr>
                </thead>
                <tbody>
                  {currentRows.map(r => {
                    const meta = Object.values(STATUS_META).find(m => {
                      const key = Object.keys(STATUS_META).find(k => STATUS_META[k] === m);
                      return key === r.status;
                    });
                    const isError = meta?.group === 'error' || meta?.group === 'warning';
                    const isSkipped = meta?.group === 'skipped';
                    return (
                      <tr key={r.rowNumber}>
                        <td style={{ padding: '6px 10px', color: 'var(--text-3)', fontWeight: 600 }}>{r.rowNumber}</td>
                        <td style={{ padding: '6px 10px' }}>{r.fullName}</td>
                        <td style={{ padding: '6px 10px', fontSize: 12 }}>{r.email}</td>
                        <td style={{ padding: '6px 10px', fontSize: 12, color: 'var(--text-3)' }}>{r.phoneNumber || '\u2014'}</td>
                        <td style={{ padding: '6px 10px' }}>
                          {r.errors.length > 0 ? (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                              {r.errors.map((e, i) => (
                                <span key={i} style={{ fontSize: 12, color: isError ? 'var(--error)' : isSkipped ? '#a855f7' : '#d97706' }}>{'\u26A0'} {e}</span>
                              ))}
                            </div>
                          ) : (
                            <span style={{ fontSize: 12, color: '#059669' }}>{'\u2713'} Hợp lệ</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: 24, color: 'var(--text-3)', fontSize: 14 }}>
              Không có dữ liệu trong nhóm này
            </div>
          )}
        </>
      );
    };

    const renderStep3 = () => {
      if (!previewData) return null;
      const selectedCourse = courses.find(c => c.id === courseId);

      return (
        <>
          <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 16 }}>Xác nhận nhập dữ liệu</div>

          {error && (
            <div style={{
              background: 'var(--chip-error-bg)', borderRadius: 11, padding: '10px 14px',
              fontSize: 13, color: 'var(--chip-error-fg)', marginBottom: 14, display: 'flex', gap: 8, alignItems: 'center',
            }}>
              <Ic n="alert" size={16} style={{ flex: 'none' }} /><span>{error}</span>
            </div>
          )}

          <div style={{
            background: 'var(--bg-2)', borderRadius: 12, padding: 20,
            display: 'flex', flexDirection: 'column', gap: 14,
          }}>
            <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
              {previewData.validCount > 0 && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 44, height: 44, borderRadius: '50%',
                    background: '#e7f8f0', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    <Ic n="user-plus" size={22} style={{ color: '#059669' }} />
                  </div>
                  <div>
                    <div style={{ fontSize: 24, fontWeight: 800, color: '#059669' }}>{previewData.validCount}</div>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>tài khoản mới</div>
                  </div>
                </div>
              )}
              {previewData.existingUserCount > 0 && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 44, height: 44, borderRadius: '50%',
                    background: '#eaf1ff', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    <Ic n="user-check" size={22} style={{ color: '#2563eb' }} />
                  </div>
                  <div>
                    <div style={{ fontSize: 24, fontWeight: 800, color: '#2563eb' }}>{previewData.existingUserCount}</div>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>ghi danh vào khoá học</div>
                  </div>
                </div>
              )}
            </div>

            <div style={{ fontSize: 13, color: 'var(--text-2)', lineHeight: 1.6 }}>
              Vai trò áp dụng: <b>{ROLE_OPTIONS.find(o => o.v === defaultRole)?.label || defaultRole}</b>
              <br />
              File: <b>{fileName}</b>
              {isStudentRole && selectedCourse && (
                <>
                  <br />
                  Khoá học: <b>{selectedCourse.title}</b>
                </>
              )}
            </div>
          </div>
        </>
      );
    };

    const renderStep4 = () => {
      if (!confirmResult) return null;

      return (
        <>
          <div style={{ textAlign: 'center', marginBottom: 20 }}>
            <div style={{
              width: 56, height: 56, borderRadius: '50%', margin: '0 auto 12px',
              background: confirmResult.failCount === 0 ? '#e7f8f0' : '#fdecec',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Ic n={confirmResult.failCount === 0 ? 'check-circle' : 'alert-triangle'}
                size={28} style={{ color: confirmResult.failCount === 0 ? '#059669' : '#dc2626' }} />
            </div>
            <div style={{ fontWeight: 700, fontSize: 17 }}>Hoàn tất nhập dữ liệu</div>
          </div>

          <div className="grid" style={{ gridTemplateColumns: 'repeat(2, 1fr)', gap: 10, marginBottom: 18 }}>
            <div style={{ background: '#e7f8f0', borderRadius: 11, padding: '14px 16px', textAlign: 'center' }}>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#059669' }}>{confirmResult.successCount}</div>
              <div style={{ fontSize: 12, fontWeight: 600, color: '#059669' }}>Thành công</div>
            </div>
            <div style={{ background: '#fdecec', borderRadius: 11, padding: '14px 16px', textAlign: 'center' }}>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#dc2626' }}>{confirmResult.failCount}</div>
              <div style={{ fontSize: 12, fontWeight: 600, color: '#dc2626' }}>Thất bại</div>
            </div>
          </div>

          {confirmResult.results.filter(r => r.status === 'IMPORT_FAILED').length > 0 && (
            <div style={{ maxHeight: 200, overflow: 'auto', borderRadius: 11, border: '1px solid var(--border)' }}>
              <table className="tbl" style={{ width: '100%', fontSize: 13 }}>
                <thead>
                  <tr>
                    <th style={{ padding: '6px 10px', width: 50 }}>Dòng</th>
                    <th style={{ padding: '6px 10px' }}>Họ tên</th>
                    <th style={{ padding: '6px 10px' }}>Email</th>
                    <th style={{ padding: '6px 10px' }}>Kết quả</th>
                  </tr>
                </thead>
                <tbody>
                  {confirmResult.results.map(r => {
                    const isSuccess = r.status === 'IMPORTED';
                    return (
                      <tr key={r.rowNumber}>
                        <td style={{ padding: '6px 10px', color: 'var(--text-3)' }}>{r.rowNumber}</td>
                        <td style={{ padding: '6px 10px' }}>{r.fullName}</td>
                        <td style={{ padding: '6px 10px', fontSize: 12 }}>{r.email}</td>
                        <td style={{ padding: '6px 10px' }}>
                          {isSuccess ? (
                            <span style={{ fontSize: 12, color: '#059669' }}>{'\u2713'} Thành công</span>
                          ) : (
                            <span style={{ fontSize: 12, color: '#dc2626' }}>
                              {'\u2717'} {r.errors[0] || 'Lỗi không xác định'}
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      );
    };

    return (
      <Modal open={open} onClose={handleClose} max={640}>
        <ModalHead
          title="Nhập người dùng từ CSV"
          sub={step === 1 ? 'Tải lên file CSV để thêm nhiều người dùng cùng lúc'
            : step === 2 ? 'Kiểm tra thông tin trước khi nhập'
            : step === 3 ? 'Xác nhận nhập dữ liệu'
            : 'Kết quả nhập dữ liệu'}
          icon="upload"
          iconBg="#f0fdf4"
          iconColor="#16a34a"
          onClose={handleClose}
        />
        <div className="modal-body">
          {renderStepIndicator()}

          {step === 1 && renderStep1()}
          {step === 2 && renderStep2()}
          {step === 3 && renderStep3()}
          {step === 4 && renderStep4()}
        </div>
        <div className="modal-foot">
          {step < 4 && (
            <button className="btn btn-ghost" onClick={() => {
              if (step === 1) handleClose();
              else setStep(s => s - 1);
            }} disabled={loading}>
              {step === 1 ? 'Hủy' : 'Quay lại'}
            </button>
          )}
          {step === 4 && (
            <button className="btn btn-primary" onClick={handleClose}>
              Đóng
            </button>
          )}

          <div style={{ flex: 1 }} />

          {step === 1 && (
            <button className="btn btn-primary" onClick={handlePreview} disabled={!file || loading}>
              {loading ? 'Đang kiểm tra...' : 'Kiểm tra dữ liệu'}
            </button>
          )}
          {step === 2 && (
            <button className="btn btn-primary" onClick={() => setStep(3)}
              disabled={!previewData || (previewData.validCount === 0 && previewData.existingUserCount === 0 && previewData.nameMismatchCount === 0)}>
              Tiếp theo
            </button>
          )}
          {step === 3 && (
            <button className="btn btn-primary" onClick={handleConfirm} disabled={loading}>
              {loading ? 'Đang nhập...' : `Xác nhận nhập (${(previewData?.validCount || 0) + (previewData?.existingUserCount || 0) + (previewData?.nameMismatchCount || 0)} người dùng)`}
            </button>
          )}
        </div>
      </Modal>
    );
  }

  Object.assign(window, { CsvImportWizard });
})();
