package project.lms_rikkei_edu.infrastructure.s3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock S3Client s3Client;
    @Mock S3Presigner s3Presigner;

    S3Service service;

    @BeforeEach
    void setUp() {
        service = new S3Service(s3Client, s3Presigner);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "defaultExpirySeconds", 3600L);
    }

    @Nested
    class GeneratePresignedPutUrl {

        @Test
        @SuppressWarnings("unchecked")
        void returnsPresignedUrl_withCustomExpiry() {
            PresignedPutObjectRequest mockResult = mock(PresignedPutObjectRequest.class);
            when(s3Presigner.presignPutObject(any(Consumer.class))).thenReturn(mockResult);

            PresignedPutObjectRequest result = service.generatePresignedPutUrl("key.mp4", "video/mp4", 7200L);

            assertThat(result).isSameAs(mockResult);
            verify(s3Presigner).presignPutObject(any(Consumer.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void usesDefaultExpiry_whenCalledWithoutExpiry() {
            PresignedPutObjectRequest mockResult = mock(PresignedPutObjectRequest.class);
            when(s3Presigner.presignPutObject(any(Consumer.class))).thenReturn(mockResult);

            PresignedPutObjectRequest result = service.generatePresignedPutUrl("key.mp4", "video/mp4");

            assertThat(result).isSameAs(mockResult);
        }
    }

    @Nested
    class GeneratePresignedGetUrl {

        @Test
        @SuppressWarnings("unchecked")
        void returnsPresignedUrl_withCustomExpiry() {
            PresignedGetObjectRequest mockResult = mock(PresignedGetObjectRequest.class);
            when(s3Presigner.presignGetObject(any(Consumer.class))).thenReturn(mockResult);

            PresignedGetObjectRequest result = service.generatePresignedGetUrl("key.pdf", 1800L);

            assertThat(result).isSameAs(mockResult);
            verify(s3Presigner).presignGetObject(any(Consumer.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void usesDefaultExpiry_whenCalledWithoutExpiry() {
            PresignedGetObjectRequest mockResult = mock(PresignedGetObjectRequest.class);
            when(s3Presigner.presignGetObject(any(Consumer.class))).thenReturn(mockResult);

            PresignedGetObjectRequest result = service.generatePresignedGetUrl("key.pdf");

            assertThat(result).isSameAs(mockResult);
        }
    }

    @Nested
    class GeneratePresignedInlineUrl {

        @Test
        @SuppressWarnings("unchecked")
        void returnsPresignedUrl_withInlineDisposition() {
            PresignedGetObjectRequest mockResult = mock(PresignedGetObjectRequest.class);
            when(s3Presigner.presignGetObject(any(Consumer.class))).thenReturn(mockResult);

            PresignedGetObjectRequest result = service.generatePresignedInlineUrl("doc.pdf", 3600L);

            assertThat(result).isSameAs(mockResult);
        }
    }

    @Nested
    class GetObject {

        @Test
        void delegatesToS3Client() {
            var mockStream = mock(software.amazon.awssdk.core.ResponseInputStream.class);
            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(mockStream);

            var result = service.getObject("courses/video.mp4");

            assertThat(result).isSameAs(mockStream);
        }
    }

    @Nested
    class DeleteObject {

        @Test
        void callsS3ClientDelete() {
            service.deleteObject("courses/old-video.mp4");

            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }
    }

    @Nested
    class ObjectExists {

        @Test
        void returnsTrue_whenObjectExists() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().build());

            assertThat(service.objectExists("courses/file.pdf")).isTrue();
        }

        @Test
        void returnsFalse_whenNoSuchKey() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("Not found").build());

            assertThat(service.objectExists("courses/missing.pdf")).isFalse();
        }
    }

    @Test
    void getBucket_returnsBucketName() {
        assertThat(service.getBucket()).isEqualTo("test-bucket");
    }
}
