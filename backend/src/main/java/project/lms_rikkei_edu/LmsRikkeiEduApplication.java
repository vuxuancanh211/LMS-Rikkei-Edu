package project.lms_rikkei_edu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LmsRikkeiEduApplication {

    public static void main(String[] args) {
        SpringApplication.run(LmsRikkeiEduApplication.class, args);
    }

}
