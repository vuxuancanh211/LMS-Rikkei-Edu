package project.lms_rikkei_edu.infrastructure.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/test/s3")
@RequiredArgsConstructor
public class S3Test {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    // Test kết nối — list bucket
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        try {
            s3Client.headBucket(r -> r.bucket(bucket));
            return Map.of("status", "ok", "bucket", bucket);
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    // Test upload file text nhỏ
    @GetMapping("/upload")
    public Map<String, Object> upload() {
        String key = "test/ping-" + System.currentTimeMillis() + ".txt";
        s3Client.putObject(
                r -> r.bucket(bucket).key(key).contentType("text/plain"),
                RequestBody.fromString("hello from lms")
        );
        return Map.of("status", "ok", "key", key);
    }

    // Test tạo presigned URL
    @GetMapping("/presign")
    public Map<String, Object> presign(@RequestParam String key) {
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(r -> r
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(get -> get.bucket(bucket).key(key))
        );
        return Map.of(
                "status", "ok",
                "url", presigned.url().toString()
        );
    }

    // Test xóa file test vừa upload
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String key) {
        s3Client.deleteObject(r -> r.bucket(bucket).key(key));
        return Map.of("status", "ok", "deleted", key);
    }
}
