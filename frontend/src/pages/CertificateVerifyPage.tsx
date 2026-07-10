import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { verifyCertificate, type CertificateVerifyResponse } from '../services';

function formatDate(value?: string | null) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value));
}

function Field({ label, value, mono = false }: { label: string; value?: string | null; mono?: boolean }) {
  return (
    <div style={{ padding: '14px 0', borderBottom: '1px solid var(--border)' }}>
      <div className="t-xs" style={{ color: 'var(--text-3)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.04em' }}>
        {label}
      </div>
      <div className={mono ? 'mono' : ''} style={{ marginTop: 5, color: 'var(--text)', fontWeight: mono ? 700 : 650, lineHeight: 1.45 }}>
        {value || '-'}
      </div>
    </div>
  );
}

function CertificatePreview({ data, revoked }: { data: CertificateVerifyResponse; revoked: boolean }) {
  return (
    <div className="card fade-in" style={{ overflow: 'hidden', boxShadow: 'var(--sh-lg)' }}>
      <div style={{ padding: 22, background: 'linear-gradient(135deg,#0f172a,#1e293b)', position: 'relative' }}>
        <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(circle at 20% 15%, rgba(37,99,235,.28), transparent 26%), radial-gradient(circle at 82% 72%, rgba(245,158,11,.22), transparent 24%)' }} />
        <div style={{ border: '1.5px solid rgba(255,255,255,.18)', borderRadius: 16, padding: '34px 24px 30px', textAlign: 'center', position: 'relative' }}>
          <div style={{ width: 54, height: 54, borderRadius: 14, background: '#fff', display: 'grid', placeItems: 'center', margin: '0 auto 16px', fontWeight: 900, fontSize: 26, color: '#0f172a', boxShadow: '0 14px 36px rgba(0,0,0,.18)' }}>
            R
          </div>
          <div style={{ color: 'rgba(255,255,255,.72)', fontSize: 12, fontWeight: 800, letterSpacing: '.12em', textTransform: 'uppercase' }}>
            Rikkei Edu Certificate
          </div>
          <h2 style={{ margin: '12px auto 0', maxWidth: 460, color: '#fff', fontSize: 28, lineHeight: 1.18, letterSpacing: '-.02em' }}>
            {data.courseTitle}
          </h2>
          <div style={{ height: 5, width: '64%', background: 'rgba(255,255,255,.22)', borderRadius: 9, margin: '22px auto 8px' }} />
          <div style={{ height: 5, width: '38%', background: 'rgba(255,255,255,.14)', borderRadius: 9, margin: '0 auto' }} />
          <div style={{ position: 'absolute', right: 16, bottom: 14, width: 38, height: 38, borderRadius: 999, background: revoked ? 'var(--error)' : 'var(--warning)', display: 'grid', placeItems: 'center', color: '#fff', fontWeight: 900 }}>
            ✓
          </div>
        </div>
      </div>

      <div style={{ padding: 22 }}>
        <div className="between wrap">
          <div>
            <div className="t-label">Được cấp cho</div>
            <div style={{ marginTop: 5, fontSize: 22, fontWeight: 800, color: 'var(--text)', letterSpacing: '-.02em' }}>
              {data.studentName}
            </div>
          </div>
          <span className={`chip ${revoked ? 'chip-error' : 'chip-success'}`} style={{ height: 30 }}>
            <span className="dot" style={{ background: 'currentColor' }} />
            {revoked ? 'Đã thu hồi' : 'Hợp lệ'}
          </span>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 14, marginTop: 20 }}>
          <div style={{ borderRadius: 12, background: 'var(--surface-2)', border: '1px solid var(--border)', padding: 14 }}>
            <div className="t-xs dim">Ngày cấp</div>
            <div style={{ marginTop: 5, fontWeight: 700 }}>{formatDate(data.issuedAt)}</div>
          </div>
          <div style={{ borderRadius: 12, background: 'var(--surface-2)', border: '1px solid var(--border)', padding: 14 }}>
            <div className="t-xs dim">Mã xác thực</div>
            <div className="mono" style={{ marginTop: 5, fontWeight: 800 }}>{data.credentialId}</div>
          </div>
        </div>
      </div>
    </div>
  );
}

export function CertificateVerifyPage() {
  const { code = '' } = useParams();
  const [data, setData] = useState<CertificateVerifyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;

    async function loadCertificate() {
      setLoading(true);
      setError('');
      try {
        const result = await verifyCertificate(code);
        if (!cancelled) setData(result);
      } catch {
        if (!cancelled) setError('Không tìm thấy chứng chỉ hoặc mã xác thực không hợp lệ.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadCertificate();

    return () => {
      cancelled = true;
    };
  }, [code]);

  const revoked = data?.status === 'REVOKED';

  return (
    <main style={{ minHeight: '100vh', background: 'var(--bg)', padding: '32px 16px' }}>
      <section style={{ maxWidth: 1120, margin: '0 auto' }}>
        <div className="between wrap" style={{ marginBottom: 22 }}>
          <div>
            <Link to="/login" style={{ color: 'var(--text)', textDecoration: 'none', fontWeight: 850, fontSize: 18 }}>
              Rikkei Edu
            </Link>
            <h1 className="t-h1" style={{ margin: '12px 0 6px' }}>Xác thực chứng chỉ</h1>
            <p className="muted" style={{ margin: 0 }}>Kiểm tra trạng thái và thông tin chứng chỉ điện tử công khai.</p>
          </div>
          <div className="chip chip-info" style={{ height: 34 }}>Public Verify</div>
        </div>

        {loading && (
          <div className="card card-pad fade-in" style={{ color: 'var(--text-2)' }}>
            Đang kiểm tra chứng chỉ...
          </div>
        )}

        {!loading && error && (
          <div className="card card-pad fade-in" style={{ borderColor: '#fecaca', background: '#fef2f2', color: '#991b1b', fontWeight: 700 }}>
            {error}
          </div>
        )}

        {!loading && data && (
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.05fr) minmax(320px,.75fr)', gap: 22, alignItems: 'start' }}>
            <CertificatePreview data={data} revoked={revoked} />

            <aside className="card card-pad fade-in">
              <div className={`chip ${revoked ? 'chip-error' : 'chip-success'}`} style={{ marginBottom: 16 }}>
                <span className="dot" style={{ background: 'currentColor' }} />
                {revoked ? 'Chứng chỉ đã bị thu hồi' : 'Chứng chỉ hợp lệ'}
              </div>

              <p style={{ margin: '0 0 18px', color: revoked ? '#991b1b' : '#047857', fontWeight: 650, lineHeight: 1.55 }}>
                {revoked
                  ? 'Chứng chỉ này đã bị thu hồi. Thông tin bên dưới chỉ phục vụ đối chiếu.'
                  : 'Chứng chỉ này hợp lệ và được phát hành bởi Rikkei Edu.'}
              </p>

              <Field label="Mã chứng chỉ" value={data.credentialId} mono />
              <Field label="Học viên" value={data.studentName} />
              <Field label="Khóa học" value={data.courseTitle} />
              <Field label="Giảng viên" value={data.instructorName} />
              <Field label="Ngày cấp" value={formatDate(data.issuedAt)} />

              {revoked && (
                <>
                  <Field label="Ngày thu hồi" value={formatDate(data.revokedAt)} />
                  <Field label="Lý do thu hồi" value={data.revokeReason || '-'} />
                </>
              )}
            </aside>
          </div>
        )}
      </section>
    </main>
  );
}
