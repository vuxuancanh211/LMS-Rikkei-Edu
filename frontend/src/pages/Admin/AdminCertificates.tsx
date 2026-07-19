// @ts-nocheck
import { getAdminCertificates, revokeCertificate } from '../../services';

/* ============================================================
   RIKKEI EDU — Admin · Certificate Management
   ============================================================ */
(function () {
  const { useEffect, useState } = React;
  const Ic = window.Icon;
  const { Search, Select, StatCard, Empty, Modal, ModalHead } = window;

  function formatDate(value) {
    if (!value) return '—';
    return new Intl.DateTimeFormat('vi-VN', { day:'2-digit', month:'2-digit', year:'numeric' }).format(new Date(value));
  }

  function statusChip(status) {
    const revoked = status === 'REVOKED';
    return <span className={`chip ${revoked ? 'chip-error' : 'chip-success'}`}>{revoked ? 'Đã thu hồi' : 'Hợp lệ'}</span>;
  }

  function hasSearchText(value) {
    return /[\p{L}\p{N}]/u.test(String(value || ''));
  }

  function AdminCertificates() {
    const [data, setData] = useState({ items: [], totalRecords: 0, totalPages: 1, page: 1, size: 10, totalIssued: 0, totalRevoked: 0 });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [q, setQ] = useState('');
    const [debouncedQ, setDebouncedQ] = useState('');
    const [page, setPage] = useState(1);
    const [status, setStatus] = useState('all');
    const [selected, setSelected] = useState(null);
    const [reason, setReason] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [toast, setToast] = useState(null);

    const size = 10;

    async function loadCertificates() {
      setLoading(true);
      setError('');
      try {
        const params = { page, size };
        const keywords = debouncedQ.toLowerCase().match(/[\p{L}\p{N}]+/gu) || [];
        if (keywords.length) params.search = debouncedQ;
        if (status !== 'all') params.status = status;
        const result = await getAdminCertificates(params);
        setData(result || { items: [], totalRecords: 0, totalPages: 1, page, size, totalIssued: 0, totalRevoked: 0 });
      } catch (err) {
        setError(err?.response?.data?.message || 'Không thể tải danh sách chứng chỉ');
      } finally {
        setLoading(false);
      }
    }

    useEffect(() => { loadCertificates(); }, [page, debouncedQ, status]);

    useEffect(() => {
      const timer = setTimeout(() => {
        setDebouncedQ(prev => {
          if (prev !== q) {
            setPage(1);
            return q;
          }
          return prev;
        });
      }, 350);
      return () => clearTimeout(timer);
    }, [q]);

    function showToast(msg, type = 'success') {
      setToast({ msg, type });
      setTimeout(() => setToast(null), 3500);
    }

    const items = data.items || [];
    const pg = {
      slice: items,
      page: data.page || page,
      pages: Math.max(data.totalPages || 1, 1),
      setPage,
      total: data.totalRecords || 0,
      from: data.totalRecords ? ((data.page || page) - 1) * (data.size || size) + 1 : 0,
      to: Math.min((data.page || page) * (data.size || size), data.totalRecords || 0),
    };

    function openRevoke(certificate) {
      setSelected(certificate);
      setReason('');
    }

    async function handleRevoke() {
      if (!selected) return;
      if (!reason.trim()) {
        showToast('Vui lòng nhập lý do thu hồi', 'error');
        return;
      }
      setSubmitting(true);
      try {
        const updated = await revokeCertificate(selected.id, reason.trim());
        setData(prev => ({
          ...prev,
          items: (prev.items || []).map(item =>
            item && item.id === (updated?.id || selected.id) ? (updated || item) : item
          ),
        }));
        setSelected(null);
        setReason('');
        showToast('Đã thu hồi chứng chỉ');
      } catch (err) {
        showToast(err?.response?.data?.message || 'Không thể thu hồi chứng chỉ', 'error');
      } finally {
        setSubmitting(false);
      }
    }

    return (
      <div className="page fade-in">
        {toast && (
          <div className="card" style={{ position:'fixed', right:24, top:92, zIndex:99, padding:'13px 16px', borderColor: toast.type === 'error' ? '#fecaca' : '#bbf7d0', background: toast.type === 'error' ? '#fef2f2' : '#f0fdf4', color: toast.type === 'error' ? '#991b1b' : '#166534', fontWeight:700, boxShadow:'var(--sh-lg)' }}>
            {toast.msg}
          </div>
        )}

        <div className="page-head between">
          <div>
            <h1 className="t-h1">Quản lý chứng chỉ</h1>
            <p>Theo dõi chứng chỉ đã cấp và thu hồi khi phát hiện gian lận.</p>
          </div>
          <button className="btn btn-ghost" onClick={loadCertificates} disabled={loading}><Ic n="refresh" size={16} />Làm mới</button>
        </div>

        <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns:'repeat(3,1fr)' }}>
          <StatCard icon="award" iconBg="#eaf1ff" iconColor="#2563eb" value={(data.totalIssued || 0) + (data.totalRevoked || 0)} label="Tổng chứng chỉ" />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={data.totalIssued || 0} label="Đang hợp lệ" />
          <StatCard icon="shield" iconBg="#fdecec" iconColor="#b91c1c" value={data.totalRevoked || 0} label="Đã thu hồi" />
        </div>

        <div className="toolbar">
          <Search placeholder="Tìm theo tên khóa học..." value={q} onChange={(value) => { if (!value || hasSearchText(value)) setQ(value); }} />
          <Select value={status} onChange={(value) => { setStatus(value); setPage(1); }} options={[{v:'all',label:'Tất cả trạng thái'},{v:'ISSUED',label:'Hợp lệ'},{v:'REVOKED',label:'Đã thu hồi'}]} style={{ width:190, flex:'none' }} />
        </div>

        {loading && <div className="card card-pad muted">Đang tải danh sách chứng chỉ...</div>}
        {!loading && error && <div className="card card-pad" style={{ color:'var(--chip-error-fg)', background:'var(--chip-error-bg)', borderColor:'#f8caca' }}>{error}</div>}
        {!loading && !error && items.length === 0 && <Empty icon="award" title="Không có chứng chỉ" text="Không tìm thấy chứng chỉ phù hợp với bộ lọc hiện tại." />}

        {!loading && !error && items.length > 0 && (
          <>
            <div className="card" style={{ overflow:'hidden' }}>
              <div style={{ overflowX:'auto' }}>
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>Mã chứng chỉ</th>
                      <th>Học viên</th>
                      <th>Khóa học</th>
                      <th>Ngày cấp</th>
                      <th>Trạng thái</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map(c => (
                      <tr key={c.id}>
                        <td><span className="mono" style={{ fontWeight:700 }}>{c.credentialId}</span></td>
                        <td><div style={{ fontWeight:700 }}>{c.studentName}</div></td>
                        <td><div className="truncate" style={{ maxWidth:280, fontWeight:650 }}>{c.courseTitle}</div><div className="t-xs dim">GV: {c.instructorName || 'Rikkei Edu'}</div></td>
                        <td className="muted">{formatDate(c.issuedAt)}</td>
                        <td>{statusChip(c.status)}</td>
                        <td>
                          <div className="row gap-8">
                            <button className="btn btn-soft btn-sm" onClick={() => window.open(`/verify/${c.credentialId}`, '_blank', 'noopener,noreferrer')}>URL công khai</button>
                            {c.status === 'ISSUED'
                              ? <button className="btn btn-danger btn-sm" onClick={() => openRevoke(c)}>Thu hồi</button>
                              : <span className="t-xs dim">Đã thu hồi {formatDate(c.revokedAt)}</span>}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            <window.PageBar pg={pg} unit="chứng chỉ" />
          </>
        )}

        <Modal open={!!selected} onClose={() => setSelected(null)} max={720}>
          <ModalHead
            title="Xác nhận thu hồi chứng chỉ"
            sub="Thao tác này có hiệu lực ngay trên trang xác thực công khai."
            icon="shield"
            iconBg="#fdecec"
            iconColor="#b91c1c"
            onClose={() => setSelected(null)}
          />
          {selected && (
            <>
              <div className="modal-body" style={{ paddingTop: 20 }}>
                <div style={{ borderRadius: 18, padding: 18, background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', marginBottom: 18 }}>
                  <div className="row gap-12" style={{ alignItems: 'flex-start' }}>
                    <div style={{ width: 42, height: 42, borderRadius: 14, display: 'grid', placeItems: 'center', background: '#fee2e2', color: '#b91c1c', flex: 'none' }}>
                      <Ic n="shield" size={20} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 850, fontSize: 15 }}>Chứng chỉ sẽ bị đánh dấu “Đã thu hồi”.</div>
                      <div className="t-sm" style={{ marginTop: 6, lineHeight: 1.6 }}>Người dùng vẫn có thể xem hoặc tải PDF đã cấp, nhưng trang xác thực public sẽ hiển thị trạng thái thu hồi và lý do bên dưới.</div>
                    </div>
                  </div>
                </div>

                <div className="card" style={{ overflow: 'hidden', marginBottom: 18, borderColor: '#dbe3ee' }}>
                  <div style={{ padding: 22, background: 'linear-gradient(135deg,#0f172a,#1e293b)', color: '#fff' }}>
                    <div className="row gap-14">
                      <div style={{ width: 52, height: 52, borderRadius: 14, background: '#fff', color: '#0f172a', display: 'grid', placeItems: 'center', fontWeight: 900, fontSize: 24, flex: 'none', boxShadow: '0 14px 34px rgba(0,0,0,.18)' }}>R</div>
                      <div className="grow" style={{ minWidth: 0 }}>
                        <div className="t-xs" style={{ opacity: .72, fontWeight: 800, letterSpacing: '.1em', textTransform: 'uppercase' }}>Rikkei Edu Certificate</div>
                        <div className="truncate" style={{ marginTop: 6, fontSize: 20, fontWeight: 850, letterSpacing: '-.01em' }}>{selected.courseTitle}</div>
                        <div className="t-sm" style={{ marginTop: 5, opacity: .74 }}>Cấp cho {selected.studentName}</div>
                      </div>
                      <div style={{ flex: 'none' }}>{statusChip(selected.status)}</div>
                    </div>
                  </div>
                  <div style={{ padding: 18, display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 14 }}>
                    <RevokeInfo label="Mã chứng chỉ" value={selected.credentialId} mono />
                    <RevokeInfo label="Ngày cấp" value={formatDate(selected.issuedAt)} />
                    <RevokeInfo label="Học viên" value={selected.studentName} />
                    <RevokeInfo label="Giảng viên" value={selected.instructorName || 'Rikkei Edu'} />
                  </div>
                </div>

                <div>
                  <label className="t-label">Lý do thu hồi</label>
                  <textarea
                    className="input"
                    value={reason}
                    onChange={e => setReason(e.target.value)}
                    placeholder="Ví dụ: Phát hiện gian lận trong quá trình học hoặc thi cuối khóa..."
                    style={{ height: 128, padding: '13px 15px', resize: 'vertical', marginTop: 9, lineHeight: 1.55, display: 'block' }}
                  />
                  <div className="t-xs dim" style={{ marginTop: 8, lineHeight: 1.5 }}>Lý do này sẽ được hiển thị trên trang xác thực public của chứng chỉ.</div>
                </div>
              </div>

              <div className="modal-foot">
                <button className="btn btn-ghost" onClick={() => setSelected(null)} disabled={submitting}>Hủy</button>
                <button className="btn btn-danger" onClick={handleRevoke} disabled={submitting}>{submitting ? 'Đang thu hồi...' : 'Xác nhận thu hồi'}</button>
              </div>
            </>
          )}
        </Modal>
      </div>
    );
  }

  function RevokeInfo({ label, value, mono }) {
    return <div className="card" style={{ padding: '15px 16px', background: 'var(--surface-2)', boxShadow: 'none' }}><div className="t-xs dim" style={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.035em' }}>{label}</div><div className={mono ? 'mono' : ''} style={{ marginTop: 7, fontWeight: 750, lineHeight: 1.45 }}>{value || '—'}</div></div>;
  }

  Object.assign(window, { AdminCertificates });
})();
