package project.lms_rikkei_edu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "POSTGRES_DB=lms_test",
        "POSTGRES_USER=test_user",
        "POSTGRES_PASSWORD=test_password",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/lms_test",
        "spring.datasource.username=test_user",
        "spring.datasource.password=test_password",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=never",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "REDIS_PASSWORD=test_password",
        "SENDGRID_API_KEY=test_sendgrid_key",
        "AWS_ACCESS_KEY=test_access_key",
        "AWS_SECRET_KEY=test_secret_key",
        "AWS_REGION=ap-southeast-1",
        "AWS_S3_BUCKET=test-bucket",
        "aws.region=ap-southeast-1",
        "spring.cloud.aws.region.static=ap-southeast-1",
        "spring.cloud.aws.credentials.access-key=test_access_key",
        "spring.cloud.aws.credentials.secret-key=test_secret_key",
        "JWT_SECRET=test-secret-key-must-be-at-least-32-characters",
        "MAIL_FROM=test@example.com"
})
class LmsRikkeiEduApplicationTests {

    @Test
    void contextLoads() {
    }

}
