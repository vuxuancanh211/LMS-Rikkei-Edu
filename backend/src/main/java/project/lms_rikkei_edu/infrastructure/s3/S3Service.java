package project.lms_rikkei_edu.infrastructure.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
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

    /**
     * Download object từ S3 dưới dạng stream. Caller phải đóng stream sau khi dùng.
     */
    public ResponseInputStream<GetObjectResponse> getObject(String key) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
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
