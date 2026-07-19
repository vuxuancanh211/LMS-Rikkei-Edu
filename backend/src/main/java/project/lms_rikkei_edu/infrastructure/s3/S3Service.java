package project.lms_rikkei_edu.infrastructure.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.presigned-url-expiry:3600}")
    private long defaultExpirySeconds;

    /**
     * Tạo presigned PUT URL để client upload thẳng lên S3.
     * Content-Type phải khớp với header khi client gọi PUT.
     */
    public PresignedPutObjectRequest generatePresignedPutUrl(String key, String contentType) {
        return generatePresignedPutUrl(key, contentType, defaultExpirySeconds);
    }

    public PresignedPutObjectRequest generatePresignedPutUrl(String key, String contentType, long expirySeconds) {
        return s3Presigner.presignPutObject(req -> req
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .putObjectRequest(put -> put
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                )
        );
    }

    /**
     * Tạo presigned GET URL để client tải file từ S3.
     */
    public PresignedGetObjectRequest generatePresignedGetUrl(String key) {
        return generatePresignedGetUrl(key, defaultExpirySeconds);
    }

    public PresignedGetObjectRequest generatePresignedGetUrl(String key, long expirySeconds) {
        return s3Presigner.presignGetObject(req -> req
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(get -> get
                        .bucket(bucket)
                        .key(key)
                )
        );
    }

    /** Presigned GET URL với Content-Disposition: inline — browser hiển thị thay vì tải. */
    public PresignedGetObjectRequest generatePresignedInlineUrl(String key, long expirySeconds) {
        return s3Presigner.presignGetObject(req -> req
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(get -> get
                        .bucket(bucket)
                        .key(key)
                        .responseContentDisposition("inline")
                )
        );
    }

    /**
     * Download object từ S3 dưới dạng stream. Caller phải đóng stream sau khi dùng.
     */
    public ResponseInputStream<GetObjectResponse> getObject(String key) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    public void putObject(String key, byte[] bytes, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build(), RequestBody.fromBytes(bytes));
    }

    /**
     * Xóa object khỏi S3. Không throw nếu key không tồn tại.
     */
    public void deleteObject(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    /**
     * Xóa "best-effort", KHÔNG chặn luồng gọi — dùng cho dọn rác S3 khi DB đã là nguồn sự thật
     * (submit/withdraw/rollback/approve chỉ cần S3 sạch dần, không cần chờ ngay). S3Client hiện
     * timeout 30s/lần gọi — nếu gọi đồng bộ trong vòng lặp nhiều file (VD: duyệt cập nhật xóa
     * nhiều tài liệu cùng lúc), tổng thời gian dễ vượt xa timeout 15s của frontend dù bản thân
     * thao tác chính (ghi DB) đã xong từ lâu, tạo cảm giác "duyệt/hủy/rollback bị treo".
     */
    @Async
    public void deleteObjectAsync(String key) {
        try {
            deleteObject(key);
        } catch (Exception e) {
            log.warn("Không thể xóa S3 key {}: {}", key, e.getMessage());
        }
    }

    /**
     * Kiểm tra object có tồn tại trên S3 không.
     */
    public boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    public String getBucket() {
        return bucket;
    }
}
