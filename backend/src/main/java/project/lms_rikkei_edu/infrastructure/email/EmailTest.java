package project.lms_rikkei_edu.infrastructure.email;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test")
public class EmailTest {

    private final EmailService emailService;

    @GetMapping("/email")
    public String testEmail(@RequestParam String to) {

        emailService.sendTestMail(to);

        return "Email sent";
    }
}