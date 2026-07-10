// @ts-nocheck
import { getCertificateDownloadUrl, getMyCertificate } from '../../services';

/* ============================================================
   RIKKEI EDU — Student Certificate Detail
   ============================================================ */
(function () {
const { useEffect: uE, useState: uS } = React;
const Ic = window.Icon;
const { StatCard:SC, Empty:Em } = window;

function formatDate(value) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value));
}

function Info({ label, value, mono }) {
  return (
    <div className="card" style={{ padding: 16, background: 'var(--surface-2)' }}>
      <div className="t-xs dim" style={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.04em' }}>{label}</div>
      <div className={mono ? 'mono' : ''} style={{ marginTop: 6, fontWeight: 750, color: 'var(--text)', lineHeight: 1.45 }}>{value || '-'}</div>
    </div>
  );
}

function StuCertDetail({ nav, certificateId }) {
  const [certificate, setCertificate] = uS(null);
  const [loading, setLoading] = uS(true);
  const [error, setError] = uS('');
  const [downloading, setDownloading] = uS(false);

  uE(() => {
    let cancelled = false;

    async function loadCertificate() {
      setLoading(true);
      setError('');
      try {
        const found = await getMyCertificate(certificateId);
        if (!cancelled) setCertificate(found);
      } catch {
        if (!cancelled) setError('Không tìm thấy chứng chỉ hoặc chứng chỉ không thuộc tài khoản của bạn.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadCertificate();

    return () => { cancelled = true; };
  }, [certificateId]);

  async function handleDownload() {
    if (!certificate) return;
    setDownloading(true);
    try {
      const { url } = await getCertificateDownloadUrl(certificate.id);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch {
      alert('Không thể tạo link tải chứng chỉ. Vui lòng thử lại sau.');
    } finally {
      setDownloading(false);
    }
  }

  if (loading) {
    return <div className="page fade-in"><div className="card card-pad muted">Đang tải chi tiết chứng chỉ...</div></div>;
  }

  if (error || !certificate) {
    return (
      <div className="page fade-in">
        <button className="btn btn-ghost" onClick={() => nav('certs')} style={{ marginBottom: 18 }}><Ic n="arrow_left" size={16} />Quay lại</button>
        <Em icon="award" title="Không tìm thấy chứng chỉ" text={error || 'Chứng chỉ không tồn tại.'} />
      </div>
    );
  }

  const revoked = certificate.status === 'REVOKED';

  return (
    <div className="page fade-in">
      <div className="row gap-10" style={{ marginBottom: 16, cursor: 'pointer', color: 'var(--text-2)' }} onClick={() => nav('certs')}>
        <Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại danh sách chứng chỉ</span>
      </div>

      <div className="page-head between">
        <div>
          <h1 className="t-h1">Chi tiết chứng chỉ</h1>
          <p>Thông tin chứng chỉ điện tử đã cấp cho tài khoản của bạn.</p>
        </div>
        <button className="btn btn-primary" onClick={handleDownload} disabled={downloading}>
          <Ic n="download" size={16} />{downloading ? 'Đang tải...' : 'Tải PDF'}
        </button>
      </div>

      <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns: 'repeat(3,1fr)' }}>
        <SC icon="award" iconBg="#eaf1ff" iconColor="#2563eb" value={certificate.credentialId} label="Mã chứng chỉ" />
        <SC icon="calendar" iconBg="#e7f8f0" iconColor="#059669" value={formatDate(certificate.issuedAt)} label="Ngày cấp" />
        <SC icon="shield" iconBg={revoked ? '#fdecec' : '#e7f8f0'} iconColor={revoked ? '#b91c1c' : '#059669'} value={revoked ? 'Đã thu hồi' : 'Hợp lệ'} label="Trạng thái" />
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'minmax(0,1.05fr) minmax(300px,.7fr)', gap: 22, alignItems: 'start' }}>
        <div className="card" style={{ overflow: 'hidden' }}>
          <div style={{ padding: 26, background: revoked ? 'linear-gradient(135deg,#3f1d1d,#7f1d1d)' : 'linear-gradient(135deg,#0f172a,#1e293b)', color: '#fff' }}>
            <div style={{ border: '1.5px solid rgba(255,255,255,.18)', borderRadius: 16, padding: 30, textAlign: 'center' }}>
              <div style={{ width: 58, height: 58, borderRadius: 16, background: '#fff', color: '#0f172a', display: 'grid', placeItems: 'center', margin: '0 auto 16px', fontWeight: 900, fontSize: 28 }}>R</div>
              <div style={{ fontSize: 12, opacity: .72, fontWeight: 800, letterSpacing: '.12em', textTransform: 'uppercase' }}>Rikkei Edu Certificate</div>
              <h2 style={{ margin: '12px auto 0', maxWidth: 560, color: '#fff', fontSize: 30, lineHeight: 1.18, letterSpacing: '-.02em' }}>{certificate.courseTitle}</h2>
              <div style={{ height: 5, width: '64%', background: 'rgba(255,255,255,.22)', borderRadius: 9, margin: '24px auto 8px' }} />
              <div style={{ height: 5, width: '38%', background: 'rgba(255,255,255,.14)', borderRadius: 9, margin: '0 auto' }} />
            </div>
          </div>
        </div>

        <div className="card card-pad">
          <span className={`chip ${revoked ? 'chip-error' : 'chip-success'}`}>{revoked ? 'Đã thu hồi' : 'Hợp lệ'}</span>
          <div className="grid" style={{ gap: 12, marginTop: 16 }}>
            <Info label="Học viên" value={certificate.studentName} />
            <Info label="Khóa học" value={certificate.courseTitle} />
            <Info label="Giảng viên" value={certificate.instructorName || 'Rikkei Edu'} />
            <Info label="Mã chứng chỉ" value={certificate.credentialId} mono />
            <Info label="Ngày cấp" value={formatDate(certificate.issuedAt)} />
            {revoked && <Info label="Ngày thu hồi" value={formatDate(certificate.revokedAt)} />}
          </div>
        </div>
      </div>
    </div>
  );
}

window.StuCertDetail = StuCertDetail;
})();
