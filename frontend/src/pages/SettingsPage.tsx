// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Shared Settings / Profile screen
   ============================================================ */
(function () {
  const { useState, useEffect, useRef, useCallback } = React;
  const Ic = window.Icon;
  const { Avatar, Section } = window;

  const TABS = [
    { v: 'profile',  l: 'Hồ sơ cá nhân', ic: 'user' },
    { v: 'security', l: 'Bảo mật',       ic: 'lock' },
  ];
  function Settings({ persona }) {
    const [tab, setTab] = useState('profile');
    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [notif, setNotif] = useState(null);

    const [previewAvatar, setPreviewAvatar] = useState(null);

    // Profile form state
    const [fullName, setFullName] = useState('');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [birthDate, setBirthDate] = useState('');
    const [gender, setGender] = useState('');
    const [bio, setBio] = useState('');

    // Password form state
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [changingPassword, setChangingPassword] = useState(false);

    const fileInputRef = useRef(null);
    const uploadingRef = useRef(false);

    const showNotif = useCallback((message, type = 'success') => {
      setNotif({ message, type });
      setTimeout(() => setNotif(null), 3000);
    }, []);

    useEffect(() => {
      (async () => {
        setLoading(true);
        try {
          const data = await window.__profileService.getProfile();
          setProfile(data);
          setFullName(data.fullName || '');
          setPhoneNumber(data.phoneNumber || '');
          setBirthDate(data.birthDate || '');
          setGender(data.gender || '');
          setBio(data.bio || '');
        } catch {
          // Fall back to persona from auth store
          setProfile(null);
          setFullName(persona?.name || '');
        } finally {
          setLoading(false);
        }
      })();
    }, []);

    const handleSave = useCallback(async () => {
      setSaving(true);
      setNotif(null);
      try {
        const data = await window.__profileService.updateProfile({
          fullName: fullName.trim() || undefined,
          phoneNumber: phoneNumber.trim() || undefined,
          birthDate: birthDate || undefined,
          gender: gender || undefined,
          bio: bio || undefined,
        });
        setProfile(data);
        setFullName(data.fullName || '');
        setPhoneNumber(data.phoneNumber || '');
        setBirthDate(data.birthDate || '');
        setGender(data.gender || '');
        setBio(data.bio || '');
        showNotif('Cập nhật hồ sơ thành công');
      } catch (err) {
        const msg = err?.response?.data?.message || err?.message || 'Cập nhật thất bại';
        showNotif(msg, 'error');
      } finally {
        setSaving(false);
      }
    }, [fullName, phoneNumber, birthDate, gender, bio, showNotif]);

    const handleAvatarChange = useCallback(async (e) => {
      const file = e.target?.files?.[0];
      if (!file || uploadingRef.current) return;
      uploadingRef.current = true;
      setNotif(null);
      try {
        const data = await window.__profileService.uploadAvatar(file);
        setProfile(data);
        showNotif('Cập nhật ảnh đại diện thành công');
      } catch (err) {
        const msg = err?.response?.data?.message || err?.message || 'Tải ảnh thất bại';
        showNotif(msg, 'error');
      } finally {
        uploadingRef.current = false;
        if (fileInputRef.current) fileInputRef.current.value = '';
      }
    }, [showNotif]);

    const handleChangePassword = useCallback(async () => {
      if (!currentPassword || !newPassword) {
        showNotif('Vui lòng nhập đầy đủ thông tin', 'error');
        return;
      }
      if (newPassword !== confirmPassword) {
        showNotif('Mật khẩu mới không khớp', 'error');
        return;
      }
      if (newPassword.length < 6) {
        showNotif('Mật khẩu mới phải có ít nhất 6 ký tự', 'error');
        return;
      }
      setChangingPassword(true);
      setNotif(null);
      try {
        await window.__profileService.changePassword({ currentPassword, newPassword });
        setCurrentPassword('');
        setNewPassword('');
        setConfirmPassword('');
        showNotif('Đổi mật khẩu thành công');
      } catch (err) {
        const msg = err?.response?.data?.message || err?.message || 'Đổi mật khẩu thất bại';
        showNotif(msg, 'error');
      } finally {
        setChangingPassword(false);
      }
    }, [currentPassword, newPassword, confirmPassword, showNotif]);

    const displayName = profile?.fullName || persona?.name || '';
    const displayEmail = profile?.email || persona?.email || '';
    const displayRole = profile?.role || persona?.role || '';

    return (
      <div className="page fade-in">
        {notif && (
          <div style={{
            padding: '12px 16px', borderRadius: 10, marginBottom: 16,
            background: notif.type === 'error' ? '#fdecec' : '#e7f8f0',
            color: notif.type === 'error' ? '#dc2626' : '#059669',
            fontWeight: 600, fontSize: 14,
          }}>
            <Ic n={notif.type === 'error' ? 'alert-triangle' : 'check-circle'} size={16} /> {notif.message}
          </div>
        )}

        <div className="page-head">
          <h1 className="t-h1">Cài đặt tài khoản</h1>
          <p>Quản lý hồ sơ cá nhân và bảo mật tài khoản.</p>
        </div>

        {loading ? (
          <div className="card card-pad" style={{ textAlign: 'center', padding: 60 }}>
            <div className="spinner" style={{
              width: 36, height: 36, border: '3px solid var(--border)',
              borderTopColor: 'var(--primary)', borderRadius: '50%',
              animation: 'spin .8s linear infinite', margin: '0 auto 16px',
            }} />
            <div className="muted">Đang tải thông tin...</div>
          </div>
        ) : (
          <div className="grid settings-grid" style={{ gridTemplateColumns: '240px 1fr', gap: 22, alignItems: 'start' }}>
            <div className="card settings-nav" style={{ padding: 10 }}>
              {TABS.map(t => (
                <div key={t.v}
                  className="row gap-11"
                  style={{
                    padding: '11px 13px', borderRadius: 10, cursor: 'pointer',
                    fontWeight: 600, fontSize: 14,
                    color: tab === t.v ? 'var(--text)' : 'var(--text-2)',
                    background: tab === t.v ? 'var(--surface-3)' : 'transparent',
                  }}
                  onClick={() => setTab(t.v)}
                >
                  <Ic n={t.ic} size={18} />{t.l}
                </div>
              ))}
            </div>

            <div className="card card-pad">
              {tab === 'profile' && (
                <div>
                  <div className="row gap-16" style={{ marginBottom: 24 }}>
                    <div style={{ position: 'relative', cursor: 'pointer' }}
                      onClick={() => profile?.avatarUrl && setPreviewAvatar(profile.avatarUrl)}>
                      <Avatar name={displayName} size={76} src={profile?.avatarUrl} />
                      {profile?.avatarUrl && (
                        <span className="avatar-expand-icon">
                          <Ic n="maximize" size={18} />
                        </span>
                      )}
                    </div>
                    <div>
                      <div className="t-h3">{displayName}</div>
                      <div className="muted">{displayEmail}</div>
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/*"
                        style={{ display: 'none' }}
                        onChange={handleAvatarChange}
                      />
                      <button className="btn btn-ghost btn-sm" style={{ marginTop: 10 }}
                        onClick={() => fileInputRef.current?.click()}
                        disabled={uploadingRef.current}
                      >
                        <Ic n="upload" size={15} />
                        {uploadingRef.current ? 'Đang tải...' : 'Đổi ảnh đại diện'}
                      </button>
                    </div>
                  </div>

                  <div className="grid grid-2" style={{ gap: 16 }}>
                    <div>
                      <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Họ và tên</label>
                      <input className="input" value={fullName} onChange={e => setFullName(e.target.value)} />
                    </div>
                    <div>
                      <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Email</label>
                      <input className="input" value={displayEmail} disabled />
                    </div>
                    <div>
                      <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Số điện thoại</label>
                      <input className="input" value={phoneNumber} onChange={e => setPhoneNumber(e.target.value)} placeholder="0912 345 678" />
                    </div>
                    <div>
                      <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Ngày sinh</label>
                      <input className="input" type="date" value={birthDate} onChange={e => setBirthDate(e.target.value)} />
                    </div>
                    <div>
                      <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Giới tính</label>
                      <select className="select" value={gender} onChange={e => setGender(e.target.value)}>
                        <option value="">Chọn giới tính</option>
                        <option value="MALE">Nam</option>
                        <option value="FEMALE">Nữ</option>
                        <option value="OTHER">Khác</option>
                      </select>
                    </div>
                    <div>
                      <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Vai trò</label>
                      <input className="input" value={
                        displayRole === 'ADMIN' ? 'Quản trị viên'
                        : displayRole === 'INSTRUCTOR' ? 'Giảng viên'
                        : displayRole === 'STUDENT' ? 'Học viên' : displayRole
                      } disabled />
                    </div>
                    <div style={{ gridColumn: '1 / -1' }}>
                      <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Giới thiệu (Bio)</label>
                      <textarea className="input" style={{ height: 90, padding: 12, resize: 'none' }}
                        value={bio} onChange={e => setBio(e.target.value)}
                        placeholder="Giới thiệu ngắn về bản thân..." />
                    </div>
                  </div>
                  <div className="row gap-10" style={{ marginTop: 22, justifyContent: 'flex-end' }}>
                    <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                      {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                    </button>
                  </div>
                </div>
              )}

              {tab === 'security' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 460 }}>
                  <div>
                    <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Mật khẩu hiện tại</label>
                    <input className="input" type="password" placeholder="••••••••"
                      value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} />
                  </div>
                  <div>
                    <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Mật khẩu mới</label>
                    <input className="input" type="password" placeholder="••••••••"
                      value={newPassword} onChange={e => setNewPassword(e.target.value)} />
                  </div>
                  <div>
                    <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Xác nhận mật khẩu mới</label>
                    <input className="input" type="password" placeholder="••••••••"
                      value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} />
                  </div>
                  <button className="btn btn-primary" style={{ alignSelf: 'flex-start' }}
                    onClick={handleChangePassword} disabled={changingPassword}>
                    {changingPassword ? 'Đang xử lý...' : 'Cập nhật mật khẩu'}
                  </button>
                </div>
              )}

            </div>
          </div>
        )}

        {previewAvatar && (
          <div className="avatar-preview-overlay" onClick={() => setPreviewAvatar(null)}>
            <button className="avatar-preview-close" onClick={() => setPreviewAvatar(null)}>
              <Ic n="x" size={24} />
            </button>
            <img className="avatar-preview-img" src={previewAvatar} alt={displayName}
              onClick={e => e.stopPropagation()} />
          </div>
        )}

        <style>{`
          @keyframes spin { to { transform: rotate(360deg); } }
          .avatar-expand-icon {
            position: absolute; bottom: 0; right: 0;
            width: 28px; height: 28px; border-radius: 50%;
            background: var(--primary); color: #fff;
            display: flex; align-items: center; justify-content: center;
            opacity: 0; transition: opacity .2s;
            pointer-events: none;
          }
          div:hover > .avatar-expand-icon { opacity: 1; }
          .avatar-preview-overlay {
            position: fixed; inset: 0; z-index: 9999;
            background: rgba(0,0,0,.7);
            display: flex; align-items: center; justify-content: center;
            animation: fadeIn .2s ease;
          }
          .avatar-preview-close {
            position: absolute; top: 20px; right: 20px;
            background: rgba(0,0,0,.5); color: #fff;
            border: none; border-radius: 50%;
            width: 44px; height: 44px;
            display: flex; align-items: center; justify-content: center;
            cursor: pointer; transition: background .2s;
          }
          .avatar-preview-close:hover { background: rgba(0,0,0,.8); }
          .avatar-preview-img {
            max-width: 90vw; max-height: 90vh;
            border-radius: 16px; box-shadow: 0 8px 40px rgba(0,0,0,.4);
            object-fit: contain;
          }
          @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
        `}</style>
      </div>
    );
  }

  window.Settings = Settings;
})();
