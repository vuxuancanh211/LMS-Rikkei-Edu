// @ts-nocheck
import { forgotPassword, login, resetPassword } from '../../services';
import { forgotPasswordSchema, loginSchema, resetPasswordSchema } from '../../schemas';
import { useAuthStore } from '../../store';

/* ============================================================
   RIKKEI EDU — Auth screens (Split Layout)
   ============================================================ */
(function () {
  const { useState } = React;
  const Ic = window.Icon, D = window.DATA;

  const slides = [
    { img: D.T.react, h: 'Hệ thống quản lý học tập', p: 'Nền tảng đào tạo toàn diện, giúp quản lý khóa học, học viên và theo dõi tiến độ một cách hiệu quả.' },
    { img: D.T.data2, h: 'Học tập cùng Trợ lý AI 24/7', p: 'AI Chatbot giải đáp thắc mắc về bài giảng và hướng dẫn sử dụng nền tảng mọi lúc.' },
    { img: D.T.lesson, h: 'Thi cử minh bạch, đáng tin cậy', p: 'Giám sát thi trình duyệt tự động và cấp chứng chỉ điện tử có mã xác thực.' },
  ];

  function getErrorMessage(error) {
    const data = error?.response?.data;
    return data?.message || data?.error || 'Có lỗi xảy ra. Vui lòng thử lại.';
  }

  function AuthShell({ children }) {
    const [dot, setDot] = useState(0);
    const s = slides[dot];

    return (
      <div className="login-split">
        <aside className="login-aside">
          <div className="login-aside-bg" />
          <div className="login-aside-mid">
            <div className="login-photo" style={{ backgroundImage: `url(${s.img})` }} />
            <h2 className="login-aside-h">{s.h}</h2>
            <p className="login-aside-p">{s.p}</p>
            <div className="login-dots">
              {slides.map((_, i) => <span key={i} onClick={() => setDot(i)} className={i === dot ? 'on' : ''} />)}
            </div>
          </div>
          <div className="login-feats">
            {[["sparkles", "AI Chatbot"], ["shield", "Giám sát thi"], ["award", "Chứng chỉ số"]].map((f, i) => (
              <div key={i} className="login-feat"><Ic n={f[0]} size={16} />{f[1]}</div>
            ))}
          </div>
        </aside>
        <main className="login-form-wrap">{children}</main>
      </div>
    );
  }

  function Alert({ type = 'error', children }) {
    if (!children) return null;
    return (
      <div style={{
        background: type === 'success' ? 'rgba(16,185,129,.11)' : 'rgba(239,68,68,.1)',
        border: `1px solid ${type === 'success' ? 'rgba(16,185,129,.25)' : 'rgba(239,68,68,.22)'}`,
        color: type === 'success' ? 'var(--success)' : 'var(--error)',
        borderRadius: 12,
        padding: '11px 13px',
        fontSize: 13.5,
        lineHeight: 1.5,
        marginBottom: 16,
      }}>
        {children}
      </div>
    );
  }

  function LoginScreen({ onLogin, onForgot }) {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [remember, setRemember] = useState(true);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const loginSuccess = useAuthStore((state) => state.loginSuccess);

    const submit = async (event) => {
      event.preventDefault();
      setError('');

      const parsed = loginSchema.safeParse({ email, password });
      if (!parsed.success) {
        setError('Vui lòng nhập email hợp lệ và mật khẩu.');
        return;
      }

      try {
        setLoading(true);
        const response = await login(parsed.data);
        loginSuccess(response, response.user);
        onLogin && onLogin(response.user.role);
      } catch (err) {
        setError(getErrorMessage(err));
      } finally {
        setLoading(false);
      }
    };

    return (
      <AuthShell>
        <form className="login-form" onSubmit={submit}>
          <div className="login-form-logo"><Ic n="cap" size={26} /></div>
          <h1 className="t-h1" style={{ textAlign: 'center', marginBottom: 6 }}>Chào mừng trở lại!</h1>
          <p className="muted" style={{ textAlign: 'center', margin: '0 0 28px' }}>Đăng nhập để tiếp tục với Rikkei Edu LMS</p>

          <Alert>{error}</Alert>

          <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Email</label>
          <div className="field-icon" style={{ marginBottom: 18 }}><Ic n="mail" /><input className="input" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email@rikkei.edu" autoComplete="email" disabled={loading} /></div>

          <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Mật khẩu</label>
          <div className="field-icon" style={{ marginBottom: 16 }}>
            <Ic n="lock" />
            <input className="input has-right-action" value={password} onChange={(e) => setPassword(e.target.value)} type={showPassword ? 'text' : 'password'} placeholder="••••••••" autoComplete="current-password" disabled={loading} />
            <button
              type="button"
              className="field-action"
              onClick={() => setShowPassword((value) => !value)}
              aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              title={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              disabled={loading}
            >
              <Ic n={showPassword ? 'eye_off' : 'eye'} />
            </button>
          </div>

          <div className="between" style={{ marginBottom: 22 }}>
            <label className="row gap-8" style={{ cursor: 'pointer', fontSize: 13.5, color: 'var(--text-2)' }}><input checked={remember} onChange={(e) => setRemember(e.target.checked)} type="checkbox" style={{ width: 17, height: 17 }} />Ghi nhớ đăng nhập</label>
            <button type="button" className="link auth-link-button" onClick={onForgot}>Quên mật khẩu?</button>
          </div>

          <button className="btn btn-primary btn-block btn-lg" disabled={loading}>{loading ? 'Đang đăng nhập...' : 'Đăng nhập'}</button>

          <div className="row gap-12" style={{ margin: '22px 0' }}>
            <div style={{ flex: 1, height: 1, background: 'var(--border)' }} /><span className="t-xs dim">bảo mật</span><div style={{ flex: 1, height: 1, background: 'var(--border)' }} />
          </div>
          <div style={{ background: 'var(--surface-2)', borderRadius: 12, padding: '12px 14px', fontSize: 12.5, color: 'var(--text-2)', lineHeight: 1.6 }}>
            <b style={{ color: 'var(--text)' }}>Tài khoản học viên</b> được khởi tạo bởi Quản trị viên. Vui lòng liên hệ bộ phận đào tạo nếu chưa có tài khoản.
          </div>

          <div className="t-xs dim" style={{ textAlign: 'center', marginTop: 26, lineHeight: 1.7 }}>© 2026 Rikkei Edu. Đã đăng ký bản quyền.</div>
        </form>
      </AuthShell>
    );
  }

  function ForgotPasswordScreen({ onBack }) {
    const [email, setEmail] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const submit = async (event) => {
      event.preventDefault();
      setError('');
      setSuccess('');

      const parsed = forgotPasswordSchema.safeParse({ email });
      if (!parsed.success) {
        setError('Vui lòng nhập email hợp lệ.');
        return;
      }

      try {
        setLoading(true);
        await forgotPassword(parsed.data);
        setSuccess('Nếu email tồn tại, link đặt lại mật khẩu đã được gửi.');
      } catch (err) {
        setError(getErrorMessage(err));
      } finally {
        setLoading(false);
      }
    };

    return (
      <AuthShell>
        <form className="login-form" onSubmit={submit}>
          <div className="login-form-logo"><Ic n="mail" size={26} /></div>
          <h1 className="t-h1" style={{ textAlign: 'center', marginBottom: 6 }}>Quên mật khẩu?</h1>
          <p className="muted" style={{ textAlign: 'center', margin: '0 0 28px' }}>Nhập email tài khoản để nhận liên kết đặt lại mật khẩu.</p>

          <Alert>{error}</Alert>
          <Alert type="success">{success}</Alert>

          <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Email</label>
          <div className="field-icon" style={{ marginBottom: 22 }}><Ic n="mail" /><input className="input" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email@rikkei.edu" autoComplete="email" disabled={loading} /></div>

          <button className="btn btn-primary btn-block btn-lg" disabled={loading}>{loading ? 'Đang gửi...' : 'Gửi liên kết đặt lại'}</button>
          <button type="button" className="btn btn-ghost btn-block" style={{ marginTop: 12 }} onClick={onBack}>Quay lại đăng nhập</button>
        </form>
      </AuthShell>
    );
  }

  function ResetPasswordScreen({ token, onBack }) {
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(token ? '' : 'Liên kết đặt lại mật khẩu không hợp lệ hoặc thiếu token.');
    const [success, setSuccess] = useState('');

    const submit = async (event) => {
      event.preventDefault();
      setError('');
      setSuccess('');

      const parsed = resetPasswordSchema.safeParse({ token, newPassword, confirmPassword });
      if (!parsed.success) {
        setError(newPassword.length < 8 ? 'Mật khẩu mới phải có ít nhất 8 ký tự.' : 'Mật khẩu nhập lại không khớp.');
        return;
      }

      try {
        setLoading(true);
        await resetPassword(parsed.data);
        setSuccess('Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới.');
        window.setTimeout(() => onBack && onBack(), 1500);
      } catch (err) {
        setError(getErrorMessage(err));
      } finally {
        setLoading(false);
      }
    };

    return (
      <AuthShell>
        <form className="login-form" onSubmit={submit}>
          <div className="login-form-logo"><Ic n="lock" size={26} /></div>
          <h1 className="t-h1" style={{ textAlign: 'center', marginBottom: 6 }}>Đặt lại mật khẩu</h1>
          <p className="muted" style={{ textAlign: 'center', margin: '0 0 28px' }}>Tạo mật khẩu mới cho tài khoản của bạn.</p>

          <Alert>{error}</Alert>
          <Alert type="success">{success}</Alert>

          <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Mật khẩu mới</label>
          <div className="field-icon" style={{ marginBottom: 18 }}><Ic n="lock" /><input className="input" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} type="password" placeholder="Tối thiểu 8 ký tự" autoComplete="new-password" disabled={loading || !token} /></div>

          <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Nhập lại mật khẩu mới</label>
          <div className="field-icon" style={{ marginBottom: 22 }}><Ic n="lock" /><input className="input" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} type="password" placeholder="Nhập lại mật khẩu" autoComplete="new-password" disabled={loading || !token} /></div>

          <button className="btn btn-primary btn-block btn-lg" disabled={loading || !token}>{loading ? 'Đang cập nhật...' : 'Đặt lại mật khẩu'}</button>
          <button type="button" className="btn btn-ghost btn-block" style={{ marginTop: 12 }} onClick={onBack}>Quay lại đăng nhập</button>
        </form>
      </AuthShell>
    );
  }

  Object.assign(window, { AuthShell, LoginScreen, ForgotPasswordScreen, ResetPasswordScreen });
})();
