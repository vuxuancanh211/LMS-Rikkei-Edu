package project.lms_rikkei_edu.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
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

    @Value("${app.auth.login-url}")
    private String loginUrl;

    public void sendNewAccountMail(String to, String fullName, String temporaryPassword) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(to);
        message.setSubject("[Rikkei Edu] Tài khoản của bạn đã được tạo");
        message.setText("Chào " + fullName + ",\n\n"
                + "Tài khoản của bạn tại hệ thống Rikkei Edu đã được tạo thành công.\n\n"
                + "Thông tin đăng nhập:\n"
                + "  - Email:    " + to + "\n"
                + "  - Mật khẩu: " + temporaryPassword + "\n\n"
                + "Vui lòng đăng nhập tại: " + loginUrl + "\n"
                + "Sau khi đăng nhập, hãy đổi mật khẩu ngay để bảo vệ tài khoản.\n\n"
                + "Trân trọng,\n"
                + "Đội ngũ Rikkei Edu");

        mailSender.send(message);
    }

    public void sendCertificateIssuedMail(
            String to,
            String fullName,
            String courseTitle,
            String verifyUrl,
            byte[] pdfBytes,
            String fileName) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("[Rikkei Edu] Chứng chỉ khóa học của bạn");
            helper.setText(buildCertificateEmailHtml(fullName, courseTitle, verifyUrl), true);
            helper.addAttachment(fileName, new ByteArrayResource(pdfBytes), "application/pdf");
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to create certificate email message", e);
        }

        mailSender.send(message);
    }

    private String buildCertificateEmailHtml(String fullName, String courseTitle, String verifyUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background-color:#f4f6f9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif">
                <table role="presentation" style="width:100%%;background-color:#f4f6f9;padding:32px 16px">
                <tr><td align="center">
                <table role="presentation" style="max-width:560px;width:100%%;background-color:#ffffff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.06)">
                <tr><td style="padding:36px;text-align:center">
                <h1 style="font-size:22px;font-weight:700;color:#1e293b;margin:0 0 12px">Chúc mừng %s!</h1>
                <p style="font-size:15px;color:#64748b;line-height:1.6;margin:0 0 8px">Bạn đã được cấp chứng chỉ cho khóa học:</p>
                <p style="font-size:17px;color:#0f172a;font-weight:700;margin:0 0 24px">%s</p>
                <a href="%s" style="display:inline-block;background-color:#4F46E5;color:#ffffff;font-size:15px;font-weight:600;text-decoration:none;padding:14px 28px;border-radius:10px">Xác thực chứng chỉ</a>
                <p style="font-size:13px;color:#94a3b8;margin:24px 0 0;line-height:1.5">File PDF chứng chỉ được đính kèm trong email này.</p>
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(fullName, courseTitle, verifyUrl);
    }

    public void sendAdminPasswordResetMail(String to, String fullName, String temporaryPassword) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(to);
        message.setSubject("[Rikkei Edu] Mật khẩu của bạn đã được đặt lại");
        message.setText("Chào " + fullName + ",\n\n"
                + "Mật khẩu tài khoản Rikkei Edu của bạn đã được quản trị viên đặt lại.\n\n"
                + "Thông tin đăng nhập:\n"
                + "  - Email:    " + to + "\n"
                + "  - Mật khẩu: " + temporaryPassword + "\n\n"
                + "Vui lòng đăng nhập tại: " + loginUrl + "\n"
                + "Sau khi đăng nhập, hãy đổi mật khẩu ngay để bảo vệ tài khoản.\n\n"
                + "Trân trọng,\n"
                + "Đội ngũ Rikkei Edu");

        mailSender.send(message);
    }
}
