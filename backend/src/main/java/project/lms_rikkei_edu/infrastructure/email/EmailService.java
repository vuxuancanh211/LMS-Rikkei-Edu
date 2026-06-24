package project.lms_rikkei_edu.infrastructure.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public void sendTestMail(String to) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(to);
        message.setSubject("123");
        message.setText("Hello133i Edu");

        mailSender.send(message);
    }

    public void sendPasswordResetMail(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reset your Rikkei Edu password");
        message.setText("We received a request to reset your password.\n\n"
                + "Use this link to reset your password. This link expires in 15 minutes:\n"
                + resetLink
                + "\n\nIf you did not request this, you can ignore this email.");

        mailSender.send(message);
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
