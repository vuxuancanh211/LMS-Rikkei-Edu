package project.lms_rikkei_edu.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public void sendPasswordResetMail(String to, String resetLink) {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

        try {
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Đặt lại mật khẩu Rikkei Edu");

            helper.setText(buildResetEmailHtml(resetLink), true);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to create email message", e);
        }

        mailSender.send(message);
    }

    private String buildResetEmailHtml(String resetLink) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background-color:#f4f6f9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif">
                <table role="presentation" style="width:100%%;background-color:#f4f6f9;padding:32px 16px">
                <tr><td align="center">
                <table role="presentation" style="max-width:520px;width:100%%;background-color:#ffffff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.06)">
                <tr><td style="padding:40px 36px 32px;text-align:center">
                <h1 style="font-size:22px;font-weight:700;color:#1e293b;margin:24px 0 8px">Đặt lại mật khẩu</h1>
                <p style="font-size:15px;color:#64748b;line-height:1.6;margin:0 0 8px">Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Rikkei Edu của bạn.</p>
                <p style="font-size:15px;color:#64748b;line-height:1.6;margin:0 0 24px">Link có hiệu lực trong <strong>10 phút</strong>.</p>
                <a href="%s" style="display:inline-block;background-color:#4F46E5;color:#ffffff;font-size:15px;font-weight:600;text-decoration:none;padding:14px 32px;border-radius:10px">Đặt lại mật khẩu</a>
                <p style="font-size:13px;color:#94a3b8;margin:24px 0 0;line-height:1.5">Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>
                </td></tr>
                <tr><td style="padding:0 36px 28px;text-align:center;font-size:12px;color:#94a3b8">&copy; 2026 Rikkei Edu. Hệ thống Quản lý Học tập.</td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(resetLink);
    }
}
