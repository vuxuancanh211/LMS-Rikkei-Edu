// @ts-nocheck
import { getCertificateDownloadUrl, getMyCertificates } from '../../services';

/* ============================================================
   RIKKEI EDU — StuCerts
   ============================================================ */
(function () {
const { useEffect: uE, useState: uS } = React;
const Ic = window.Icon;
const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

function formatDate(value) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value));
}

function certificateMatches(c, q) {
  const keywords = q.toLowerCase().match(/[\p{L}\p{N}]+/gu) || [];
  if (keywords.length === 0) return true;
  const haystack = String(c.courseTitle || '').toLowerCase();
  return keywords.some(keyword => haystack.includes(keyword));
}

function hasSearchText(value) {
  return /[\p{L}\p{N}]/u.test(String(value || ''));
}

function sortCertificates(list, sort) {
  const copy = [...list];
  if (sort === 'az') return copy.sort((a, b) => a.courseTitle.localeCompare(b.courseTitle, 'vi'));
  return copy.sort((a, b) => new Date(b.issuedAt).getTime() - new Date(a.issuedAt).getTime());
}

function certificateBackground(certificate, revoked) {
  if (certificate.courseThumbnailUrl) {
    const overlay = revoked ? 'rgba(63,29,29,.74)' : 'rgba(15,23,42,.58)';
    return `linear-gradient(135deg, ${overlay}, ${overlay}), url(${certificate.courseThumbnailUrl}) center/cover`;
  }
  return revoked ? "linear-gradient(135deg,#3f1d1d,#7f1d1d)" : "linear-gradient(135deg,#0f172a,#1e293b)";
}

/* ---------------- Certificates ---------------- */
function StuCerts({ nav }) {
  const [q, setQ] = uS("");
  const [status, setStatus] = uS('all');
  const [sort, setSort] = uS('new');
  const [items, setItems] = uS([]);
  const [loading, setLoading] = uS(true);
  const [error, setError] = uS('');
  const [downloadingId, setDownloadingId] = uS('');

  uE(() => {
    let cancelled = false;

    async function loadCertificates() {
      setLoading(true);
      setError('');
      try {
        const data = await getMyCertificates();
        if (!cancelled) setItems(data || []);
      } catch {
        if (!cancelled) setError('Không thể tải danh sách chứng chỉ. Vui lòng thử lại sau.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadCertificates();

    return () => { cancelled = true; };
  }, []);

  async function handleDownload(certificate) {
    setDownloadingId(certificate.id);
    try {
      const { url } = await getCertificateDownloadUrl(certificate.id);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch {
      alert('Không thể tạo link tải chứng chỉ. Vui lòng thử lại sau.');
    } finally {
      setDownloadingId('');
    }
  }

  let list = items.filter(c => certificateMatches(c, q));
  if (status !== 'all') list = list.filter(c => c.status === status);
  list = sortCertificates(list, sort);
  const pg = window.usePaged(list, 6);
  const issuedCount = items.filter(c => c.status === 'ISSUED').length;
  const revokedCount = items.filter(c => c.status === 'REVOKED').length;

  return (
    <div className="page fade-in">
      <div className="page-head"><h1 className="t-h1">Chứng chỉ của tôi</h1><p>Chúc mừng bạn! Đây là thành quả cho những nỗ lực học tập của bạn.</p></div>
      <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns:"repeat(3,1fr)" }}>
        <SC icon="award" iconBg="#eaf1ff" iconColor="#2563eb" value={items.length} label="Tổng số chứng chỉ" />
        <SC icon="trending" iconBg="#e7f8f0" iconColor="#059669" value={issuedCount} label="Đang hợp lệ" />
        <SC icon="star" iconBg="#fef5e6" iconColor="#d97706" value={revokedCount} label="Đã thu hồi" />
      </div>
      <div className="toolbar">
        <Se placeholder="Tìm chứng chỉ theo tên khóa học..." value={q} onChange={(value) => { if (!value || hasSearchText(value)) setQ(value); }} />
        <Sl value={status} onChange={setStatus} options={[{v:"all",label:"Tất cả trạng thái"},{v:"ISSUED",label:"Hợp lệ"},{v:"REVOKED",label:"Đã thu hồi"}]} style={{width:180,flex:"none"}} />
        <Sl value={sort} onChange={setSort} options={[{v:"new",label:"Mới nhất"},{v:"az",label:"Tên A-Z"}]} style={{width:150,flex:"none"}} />
      </div>
      {loading && <div className="card card-pad muted">Đang tải chứng chỉ...</div>}
      {!loading && error && <div className="card card-pad" style={{ color: 'var(--chip-error-fg)', background: 'var(--chip-error-bg)', borderColor: '#f8caca' }}>{error}</div>}
      {!loading && !error && pg.total === 0 && <Em icon="award" title="Chưa có chứng chỉ" text="Khi bạn được cấp chứng chỉ, thông tin sẽ xuất hiện tại đây." />}
      {!loading && !error && pg.total > 0 && <>
        <div className="grid grid-cards">
        {pg.slice.map(ct => {
          const revoked = ct.status === 'REVOKED';
          return (
          <div key={ct.id} className="card fade-in" style={{ overflow: "hidden", opacity: revoked ? .82 : 1 }}>
            <div style={{ padding: 18, background: certificateBackground(ct, revoked), position: "relative" }}>
              <div style={{ border: "1.5px solid rgba(255,255,255,.18)", borderRadius: 12, padding: "22px 18px", textAlign: "center", position: "relative" }}>
                <div style={{ width: 46, height: 46, borderRadius: 12, background: "#fff", display: "grid", placeItems: "center", margin: "0 auto 12px", fontWeight: 800, fontSize: 22, color: "#0f172a" }}>R</div>
                <div style={{ height: 5, width: "70%", background: "rgba(255,255,255,.22)", borderRadius: 9, margin: "0 auto 7px" }} />
                <div style={{ height: 5, width: "45%", background: "rgba(255,255,255,.14)", borderRadius: 9, margin: "0 auto" }} />
                <div style={{ position: "absolute", right: 12, bottom: 10, width: 30, height: 30, borderRadius: 999, background: revoked ? "var(--error)" : "var(--warning)", display: "grid", placeItems: "center", color: "#fff" }}><Ic n="award" size={16} /></div>
              </div>
            </div>
            <div style={{ padding: 18 }}>
              <div className="between gap-10" style={{ alignItems: 'flex-start' }}>
                <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }} className="clamp-2">{ct.courseTitle}</h3>
                <span className={`chip ${revoked ? 'chip-error' : 'chip-success'}`}>{revoked ? 'Thu hồi' : 'Hợp lệ'}</span>
              </div>
              <div className="t-sm muted row gap-6" style={{ marginTop: 10 }}><Ic n="calendar" size={14} />Cấp ngày: {formatDate(ct.issuedAt)}</div>
              <div className="t-sm muted row gap-6" style={{ marginTop: 5 }}><Ic n="user" size={14} />Giảng viên: {ct.instructorName || 'Rikkei Edu'}</div>
              <div className="t-sm muted row gap-6" style={{ marginTop: 5 }}><Ic n="finger" size={14} />ID: <span className="mono" style={{ color: "var(--text)", fontWeight: 600 }}>{ct.credentialId}</span></div>
              <div className="row gap-10" style={{ marginTop: 16 }}>
                <button className="btn btn-ghost btn-sm grow" onClick={() => nav('certDetail', { certificateId: ct.id })}>Xem chi tiết</button>
                <button className="btn btn-primary btn-sm grow" disabled={downloadingId === ct.id} onClick={() => handleDownload(ct)}><Ic n="download" size={15} />{downloadingId === ct.id ? 'Đang tải...' : 'Tải PDF'}</button>
              </div>
            </div>
          </div>
        );})}
        </div>
        <window.PageBar pg={pg} unit="chứng chỉ" />
      </>}

    </div>
  );
}

window.StuCerts = StuCerts;
})();
