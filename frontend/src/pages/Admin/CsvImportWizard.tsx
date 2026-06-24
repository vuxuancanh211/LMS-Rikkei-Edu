(function () {
  const { useState, useCallback, useRef } = React;
  const Ic = window.Icon;
  const { Modal, ModalHead, Select } = window;

  const ROLE_OPTIONS = [
    { v: 'STUDENT', label: 'Học viên' },
    { v: 'INSTRUCTOR', label: 'Giảng viên' },
    { v: 'ADMIN', label: 'Quản trị viên' },
  ];

  const STATUS_META = {
    VALID:            { label: 'Sẽ được thêm',      color: '#059669', bg: '#e7f8f0', icon: 'check-circle' },
    FORMAT_ERROR:     { label: 'Lỗi định dạng',      color: '#d97706', bg: '#fef3c7', icon: 'alert-triangle' },
    DUPLICATE_IN_FILE:{ label: 'Trùng trong file',   color: '#dc2626', bg: '#fdecec', icon: 'x-circle' },
    DUPLICATE_IN_DB:  { label: 'Đã tồn tại trong hệ thống', color: '#7c3aed', bg: '#f3edff', icon: 'x-circle' },
    IMPORTED:         { label: 'Đã nhập thành công', color: '#059669', bg: '#e7f8f0', icon: 'check-circle' },
    IMPORT_FAILED:    { label: 'Nhập thất bại',      color: '#dc2626', bg: '#fdecec', icon: 'x-circle' },
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
      setLoading(true);
      setError(null);
      try {
        const result = await window.__csvImportService.previewCsvImport(file, defaultRole);
        setPreviewData(result);
        setStep(2);
      } catch (err) {
        const msg = err?.response?.data?.message || err?.message || 'Không thể kiểm tra file CSV';
        setError(msg);
      } finally {
        setLoading(false);
      }
    }, [file, defaultRole]);

    const handleConfirm = useCallback(async () => {
      if (!previewData) return;
      setLoading(true);
      setError(null);
      try {
        const result = await window.__csvImportService.confirmCsvImport(previewData.token);
        setConfirmResult(result);
        setStep(4);
        if (result.successCount > 0 && onImportSuccess) onImportSuccess();
      } catch (err) {
        const msg = err?.response?.data?.message || err?.message || 'Không thể nhập dữ liệu';
        setError(msg);
      } finally {
        setLoading(false);
      }
    }, [previewData, onImportSuccess]);

    const statCards = previewData ? [
      { key: 'valid',            count: previewData.validCount,            meta: STATUS_META.VALID },
      { key: 'format_error',     count: previewData.formatErrorCount,     meta: STATUS_META.FORMAT_ERROR },
      { key: 'duplicate_in_file',count: previewData.duplicateInFileCount,  meta: STATUS_META.DUPLICATE_IN_FILE },
      { key: 'duplicate_in_db',  count: previewData.duplicateInDbCount,   meta: STATUS_META.DUPLICATE_IN_DB },
    ] : [];

    const getRowsByStatus = (statuses) => {
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
            }}>{step > s ? '✓' : s}</div>
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
          <Select value={defaultRole} onChange={setDefaultRole} options={ROLE_OPTIONS} />
          <div className="t-xs muted" style={{ marginTop: 4 }}>
            Vai trò này sẽ được áp dụng cho tất cả người dùng trong file CSV (trừ khi có cột role trong file)
          </div>
        </div>
      </>
    );

    const renderStep2 = () => {
      if (!previewData) return null;

      const tabs = [
        { key: 'valid',             label: `Sẽ được thêm (${previewData.validCount})`,            statuses: ['VALID'],            bg: '#e7f8f0', color: '#059669' },
        { key: 'format_error',      label: `Lỗi định dạng (${previewData.formatErrorCount})`,     statuses: ['FORMAT_ERROR'],     bg: '#fef3c7', color: '#d97706' },
        { key: 'duplicate',         label: `Đã tồn tại (${previewData.duplicateInFileCount + previewData.duplicateInDbCount})`, statuses: ['DUPLICATE_IN_FILE', 'DUPLICATE_IN_DB'], bg: '#fdecec', color: '#dc2626' },
      ];

      const currentRows = getRowsByStatus(tabs.find(t => t.key === activeTab)?.statuses || []);

      return (
        <>
          <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 14 }}>Kết quả kiểm tra</div>

          <div className="grid" style={{ gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 18 }}>
            {statCards.filter(s => s.count > 0).map(s => (
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

          <div style={{ display: 'flex', gap: 6, marginBottom: 12 }}>
            {tabs.filter(t => t.statuses.some(s => previewData.rows.some(r => r.status === s))).map(t => (
              <button key={t.key}
                style={{
                  padding: '7px 14px', borderRadius: 8, border: 'none', cursor: 'pointer',
                  fontSize: 13, fontWeight: 600,
                  background: activeTab === t.key ? t.bg : 'var(--bg-2)',
                  color: activeTab === t.key ? t.color : 'var(--text-3)',
                }}
                onClick={() => setActiveTab(t.key)}>
                {t.label}
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
                  {currentRows.map(r => (
                    <tr key={r.rowNumber}>
                      <td style={{ padding: '6px 10px', color: 'var(--text-3)', fontWeight: 600 }}>{r.rowNumber}</td>
                      <td style={{ padding: '6px 10px' }}>{r.fullName}</td>
                      <td style={{ padding: '6px 10px', fontSize: 12 }}>{r.email}</td>
                      <td style={{ padding: '6px 10px', fontSize: 12, color: 'var(--text-3)' }}>{r.phoneNumber || '—'}</td>
                      <td style={{ padding: '6px 10px' }}>
                        {r.errors.length > 0 ? (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                            {r.errors.map((e, i) => (
                              <span key={i} style={{ fontSize: 12, color: 'var(--error)' }}>⚠ {e}</span>
                            ))}
                          </div>
                        ) : (
                          <span style={{ fontSize: 12, color: '#059669' }}>✓ Hợp lệ</span>
                        )}
                      </td>
                    </tr>
                  ))}
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
      const totalWillImport = previewData.validCount;
      const totalSkipped = previewData.formatErrorCount + previewData.duplicateInFileCount + previewData.duplicateInDbCount;

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
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{
                width: 44, height: 44, borderRadius: '50%',
                background: '#e7f8f0', display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Ic n="users" size={22} style={{ color: '#059669' }} />
              </div>
              <div>
                <div style={{ fontSize: 24, fontWeight: 800, color: '#059669' }}>{totalWillImport}</div>
                <div style={{ fontSize: 13, fontWeight: 600 }}>người dùng sẽ được thêm</div>
              </div>
            </div>

            {totalSkipped > 0 && (
              <div style={{ padding: '10px 14px', background: '#fef3c7', borderRadius: 8, fontSize: 13, color: '#92400e' }}>
                <Ic n="alert" size={14} /> {totalSkipped} người dùng sẽ bỏ qua do lỗi hoặc đã tồn tại
              </div>
            )}

            <div style={{ fontSize: 13, color: 'var(--text-2)', lineHeight: 1.6 }}>
              Vai trò áp dụng: <b>{ROLE_OPTIONS.find(o => o.v === defaultRole)?.label || defaultRole}</b>
              <br />
              File: <b>{fileName}</b>
              <br />
              Mỗi người dùng sẽ nhận được email chứa mật khẩu tạm thời.
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
                            <span style={{ fontSize: 12, color: '#059669' }}>✓ Thành công</span>
                          ) : (
                            <span style={{ fontSize: 12, color: '#dc2626' }}>
                              ✗ {r.errors[0] || 'Lỗi không xác định'}
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
            <button className="btn btn-primary" onClick={() => setStep(3)} disabled={previewData?.validCount === 0}>
              Tiếp theo
            </button>
          )}
          {step === 3 && (
            <button className="btn btn-primary" onClick={handleConfirm} disabled={loading}>
              {loading ? 'Đang nhập...' : `Xác nhận nhập ${previewData?.validCount || 0} người dùng`}
            </button>
          )}
        </div>
      </Modal>
    );
  }

  Object.assign(window, { CsvImportWizard });
})();
