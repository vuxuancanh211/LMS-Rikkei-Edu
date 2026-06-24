package project.lms_rikkei_edu.modules.forum.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAttachmentResponse;
import project.lms_rikkei_edu.modules.forum.entity.ForumAttachmentEntity;
import project.lms_rikkei_edu.modules.forum.repository.ForumAttachmentRepository;
import project.lms_rikkei_edu.modules.forum.service.ForumAttachmentService.AttachmentContent;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumAttachmentServiceImplTest {

    @Mock
    private ForumAttachmentRepository forumAttachmentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private S3Client s3Client;

    private ForumAttachmentServiceImpl service;

    private static final String TOKEN_SECRET = "test-hmac-secret";
    private static final String BUCKET = "test-bucket";
    private static final long TTL_SECONDS = 3600;
    private static final long ORPHAN_TTL_HOURS = 24;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ForumAttachmentServiceImpl(forumAttachmentRepository, currentUserProvider, s3Client);
        ReflectionTestUtils.setField(service, "bucket", BUCKET);
        ReflectionTestUtils.setField(service, "tokenSecret", TOKEN_SECRET);
        ReflectionTestUtils.setField(service, "contentTokenTtlSeconds", TTL_SECONDS);
        ReflectionTestUtils.setField(service, "orphanTtlHours", ORPHAN_TTL_HOURS);

        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(USER_ID));
    }

    @Test
    void uploadImageSavesAndReturnsResponse() throws Exception {
        MultipartFile file = mockImageFile("photo.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 4096);

        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ForumAttachmentResponse response = service.upload(file);

        assertThat(response.getFileName()).isEqualTo("photo.jpg");
        assertThat(response.getAttachmentType()).isEqualTo("IMAGE");
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getSizeBytes()).isEqualTo(4096);
        assertThat(response.getUrl()).startsWith("/api/forum/attachments/" + response.getId() + "/content?token=");
        assertThat(response.getId()).isNotNull();
        verify(s3Client).putObject(any(java.util.function.Consumer.class), any(RequestBody.class));
    }

    @Test
    void uploadFileSavesAndReturnsResponse() throws Exception {
        byte[] pdfHeader = new byte[]{(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46, (byte) 0x2D};
        MultipartFile file = mockImageFile("doc.pdf", "application/pdf", pdfHeader, 10 * 1024 * 1024);

        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ForumAttachmentResponse response = service.upload(file);

        assertThat(response.getAttachmentType()).isEqualTo("FILE");
        assertThat(response.getFileName()).isEqualTo("doc.pdf");
    }

    @Test
    void uploadRejectsEmptyFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/bmp", "application/exe", "text/html"})
    void uploadRejectsUnsupportedMimeType(String mimeType) throws Exception {
        MultipartFile file = mockImageFile("file.x", mimeType, new byte[]{0, 0, 0}, 128);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File type is not allowed");
    }

    @Test
    void uploadRejectsExtensionMismatch() throws Exception {
        MultipartFile file = mockImageFile("photo.png", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 4096);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File extension does not match file type");
    }

    @Test
    void uploadRejectsBadSignature() throws Exception {
        MultipartFile file = mockImageFile("photo.jpg", "image/jpeg",
                new byte[]{0x00, 0x00, 0x00}, 4096);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File signature does not match file type");
    }

    @Test
    void uploadRejectsOversizedImage() throws Exception {
        byte[] header = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        MultipartFile file = mockImageFile("photo.jpg", "image/jpeg", header, 6L * 1024 * 1024);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File size exceeds limit");
        verify(s3Client, never()).putObject(any(java.util.function.Consumer.class), any(RequestBody.class));
    }

    @Test
    void uploadRejectsOversizedFile() throws Exception {
        byte[] header = new byte[]{(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46, (byte) 0x2D};
        MultipartFile file = mockImageFile("doc.pdf", "application/pdf", header, 21L * 1024 * 1024);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File size exceeds limit");
    }

    @Test
    void getContentReturnsBytesWithValidToken() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        String token = createValidToken(attachmentId);
        byte[] expectedBytes = "file-content".getBytes();

        when(forumAttachmentRepository.findById(attachmentId))
                .thenReturn(Optional.of(attachmentEntity(attachmentId, "doc.pdf", "application/pdf")));

        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(expectedBytes);
        when(s3Client.getObjectAsBytes(any(java.util.function.Consumer.class))).thenReturn(responseBytes);

        AttachmentContent content = service.getContent(attachmentId, token);

        assertThat(content.bytes()).isEqualTo(expectedBytes);
        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.fileName()).isEqualTo("doc.pdf");
    }

    @Test
    void getContentRejectsExpiredToken() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        long expiredTime = System.currentTimeMillis() - 1000;
        String token = createTokenAtTime(attachmentId, expiredTime);

        assertThatThrownBy(() -> service.getContent(attachmentId, token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Attachment token expired");
    }

    @Test
    void getContentRejectsWrongAttachmentId() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        UUID wrongId = UUID.randomUUID();
        String token = createValidToken(wrongId);

        assertThatThrownBy(() -> service.getContent(attachmentId, token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid attachment token");
    }

    @Test
    void getContentRejectsInvalidBase64() throws Exception {
        assertThatThrownBy(() -> service.getContent(UUID.randomUUID(), "!!!not-base64!!!"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid attachment token");
    }

    @Test
    void getContentRejectsTamperedSignature() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + 3600_000;
        String payload = attachmentId + ":" + expiresAt;
        String badSignature = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + ":" + badSignature).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.getContent(attachmentId, token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid attachment token");
    }

    @Test
    void getContentRejectsNotFound() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        String token = createValidToken(attachmentId);

        when(forumAttachmentRepository.findById(attachmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getContent(attachmentId, token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Attachment not found");
    }

    @Test
    void attachToPostAssignsPostId() {
        UUID postId = UUID.randomUUID();
        List<UUID> attachmentIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<ForumAttachmentEntity> attachments = attachmentIds.stream()
                .map(id -> attachmentEntity(id, "a.pdf", "application/pdf"))
                .toList();

        when(forumAttachmentRepository.findByIdInAndUploaderId(attachmentIds, USER_ID)).thenReturn(attachments);

        service.attachToPost(attachmentIds, postId, USER_ID);

        verify(forumAttachmentRepository, never()).save(any());
        assertThat(attachments).allMatch(a -> a.getPostId().equals(postId));
    }

    @Test
    void attachToReplyAssignsReplyId() {
        UUID replyId = UUID.randomUUID();
        List<UUID> attachmentIds = List.of(UUID.randomUUID());
        List<ForumAttachmentEntity> attachments = List.of(attachmentEntity(attachmentIds.getFirst(), "a.pdf", "application/pdf"));

        when(forumAttachmentRepository.findByIdInAndUploaderId(attachmentIds, USER_ID)).thenReturn(attachments);

        service.attachToReply(attachmentIds, replyId, USER_ID);

        assertThat(attachments.getFirst().getReplyId()).isEqualTo(replyId);
    }

    @Test
    void attachRejectsTooManyAttachments() {
        List<UUID> tooMany = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.attachToPost(tooMany, UUID.randomUUID(), USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Too many attachments");
    }

    @Test
    void attachRejectsAlreadyUsedAttachment() {
        UUID attachmentId = UUID.randomUUID();
        ForumAttachmentEntity used = attachmentEntity(attachmentId, "a.pdf", "application/pdf");
        used.setPostId(UUID.randomUUID());

        when(forumAttachmentRepository.findByIdInAndUploaderId(List.of(attachmentId), USER_ID))
                .thenReturn(List.of(used));

        assertThatThrownBy(() -> service.attachToPost(List.of(attachmentId), UUID.randomUUID(), USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Attachment is already used");
    }

    @Test
    void attachRejectsMismatchedIds() {
        List<UUID> attachmentIds = List.of(UUID.randomUUID());
        when(forumAttachmentRepository.findByIdInAndUploaderId(attachmentIds, USER_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.attachToPost(attachmentIds, UUID.randomUUID(), USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid attachment");
    }

    @SuppressWarnings("unchecked")
    @Test
    void findByPostIdsGroupsCorrectly() {
        UUID postId1 = UUID.randomUUID();
        UUID postId2 = UUID.randomUUID();
        ForumAttachmentEntity a1 = attachmentEntity(UUID.randomUUID(), "a.pdf", "application/pdf");
        a1.setPostId(postId1);
        ForumAttachmentEntity a2 = attachmentEntity(UUID.randomUUID(), "b.pdf", "application/pdf");
        a2.setPostId(postId2);

        when(forumAttachmentRepository.findByPostIdInOrderByCreatedAtAsc(List.of(postId1, postId2)))
                .thenReturn(List.of(a1, a2));

        Map<UUID, List<ForumAttachmentResponse>> result = service.findByPostIds(List.of(postId1, postId2));

        assertThat(result).hasSize(2);
        assertThat(result.get(postId1)).hasSize(1);
        assertThat(result.get(postId2)).hasSize(1);
    }

    @Test
    void cleanupOrphanAttachmentsDeletesOldOrphans() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(ORPHAN_TTL_HOURS);
        ForumAttachmentEntity orphan = attachmentEntity(UUID.randomUUID(), "orphan.pdf", "application/pdf");
        orphan.setCreatedAt(OffsetDateTime.now().minusHours(48));

        when(forumAttachmentRepository.findByPostIdIsNullAndReplyIdIsNullAndCreatedAtBefore(
                argThat(t -> t.isBefore(cutoff.plusMinutes(1)))))
                .thenReturn(List.of(orphan));

        int deleted = service.cleanupOrphanAttachments();

        assertThat(deleted).isEqualTo(1);
        verify(s3Client).deleteObject(any(java.util.function.Consumer.class));
        verify(forumAttachmentRepository).delete(orphan);
    }

    @Test
    void cleanupOrphanAttachmentsHandlesNoSuchKey() {
        ForumAttachmentEntity orphan = attachmentEntity(UUID.randomUUID(), "orphan.pdf", "application/pdf");
        orphan.setCreatedAt(OffsetDateTime.now().minusHours(48));

        when(forumAttachmentRepository.findByPostIdIsNullAndReplyIdIsNullAndCreatedAtBefore(any()))
                .thenReturn(List.of(orphan));
        when(s3Client.deleteObject(any(java.util.function.Consumer.class)))
                .thenThrow(mock(software.amazon.awssdk.services.s3.model.NoSuchKeyException.class));

        int deleted = service.cleanupOrphanAttachments();

        assertThat(deleted).isEqualTo(1);
        verify(forumAttachmentRepository).delete(orphan);
    }

    // --- validateSignature switch branches ---

    @Test
    void uploadPNG() throws Exception {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MultipartFile file = mockImageFile("img.png", "image/png", pngHeader, 2048);
        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ForumAttachmentResponse response = service.upload(file);
        assertThat(response.getAttachmentType()).isEqualTo("IMAGE");
    }

    @Test
    void uploadGIF() throws Exception {
        byte[] gifHeader = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        MultipartFile file = mockImageFile("img.gif", "image/gif", gifHeader, 1024);
        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ForumAttachmentResponse response = service.upload(file);
        assertThat(response.getAttachmentType()).isEqualTo("IMAGE");
    }

    @Test
    void uploadGIF87a() throws Exception {
        byte[] gifHeader = "GIF87a".getBytes(StandardCharsets.US_ASCII);
        MultipartFile file = mockImageFile("img87.gif", "image/gif", gifHeader, 512);
        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ForumAttachmentResponse response = service.upload(file);
        assertThat(response.getAttachmentType()).isEqualTo("IMAGE");
    }

    @Test
    void uploadWebP() throws Exception {
        byte[] webpHeader = buildWebPHeader();
        MultipartFile file = mockImageFile("img.webp", "image/webp", webpHeader, 3072);
        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ForumAttachmentResponse response = service.upload(file);
        assertThat(response.getAttachmentType()).isEqualTo("IMAGE");
    }

    @Test
    void uploadOLE2() throws Exception {
        byte[] oleHeader = new byte[]{(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1};
        MultipartFile file = mockImageFile("doc.doc", "application/msword", oleHeader, 16384);
        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ForumAttachmentResponse response = service.upload(file);
        assertThat(response.getAttachmentType()).isEqualTo("FILE");
    }

    @Test
    void uploadZIP() throws Exception {
        byte[] zipHeader = new byte[]{0x50, 0x4B, 0x03, 0x04};
        MultipartFile file = mockImageFile("archive.zip", "application/zip", zipHeader, 8192);
        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ForumAttachmentResponse response = service.upload(file);
        assertThat(response.getAttachmentType()).isEqualTo("FILE");
    }

    @Test
    void uploadDocx() throws Exception {
        byte[] pkHeader = new byte[]{0x50, 0x4B, 0x03, 0x04};
        MultipartFile file = mockImageFile("doc.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", pkHeader, 12288);
        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ForumAttachmentResponse response = service.upload(file);
        assertThat(response.getAttachmentType()).isEqualTo("FILE");
    }

    @Test
    void uploadTextPlain() throws Exception {
        MultipartFile file = mockImageFile("notes.txt", "text/plain", new byte[]{0x48, 0x65, 0x6C}, 64);
        when(forumAttachmentRepository.save(any(ForumAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ForumAttachmentResponse response = service.upload(file);
        assertThat(response.getAttachmentType()).isEqualTo("FILE");
    }

    @Test
    void uploadRejectsUnknownSignatureType() throws Exception {
        MultipartFile file = mockImageFile("data.pdf", "application/pdf", new byte[]{0x00, 0x00, 0x00}, 128);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File signature does not match file type");
    }

    @Test
    void uploadWithNullContentType() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(false);
        lenient().when(file.getContentType()).thenReturn(null);
        lenient().when(file.getOriginalFilename()).thenReturn("noext");
        lenient().when(file.getSize()).thenReturn(64L);
        lenient().when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{0x48}));

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File type is not allowed");
    }

    // --- null/empty guards ---

    @Test
    void attachEmptyListIsNoOp() {
        service.attachToPost(List.of(), UUID.randomUUID(), USER_ID);
        verify(forumAttachmentRepository, never()).findByIdInAndUploaderId(anyList(), any());
    }

    @Test
    void attachNullIdsIsNoOp() {
        service.attachToPost(null, UUID.randomUUID(), USER_ID);
        verify(forumAttachmentRepository, never()).findByIdInAndUploaderId(anyList(), any());
    }

    @Test
    void findByPostIdsNullReturnsEmpty() {
        Map<UUID, List<ForumAttachmentResponse>> result = service.findByPostIds(null);
        assertThat(result).isEmpty();
    }

    @Test
    void findByReplyIdsNullReturnsEmpty() {
        Map<UUID, List<ForumAttachmentResponse>> result = service.findByReplyIds(null);
        assertThat(result).isEmpty();
    }

    // --- total quota ---

    @Test
    void attachRejectsTotalQuotaExceeded() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> ids = List.of(id1, id2);
        ForumAttachmentEntity a1 = attachmentEntity(id1, "big1.pdf", "application/pdf");
        a1.setSizeBytes(30L * 1024 * 1024);
        ForumAttachmentEntity a2 = attachmentEntity(id2, "big2.pdf", "application/pdf");
        a2.setSizeBytes(30L * 1024 * 1024);

        when(forumAttachmentRepository.findByIdInAndUploaderId(ids, USER_ID)).thenReturn(List.of(a1, a2));

        assertThatThrownBy(() -> service.attachToPost(ids, UUID.randomUUID(), USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Total attachment size exceeds limit");
    }

    // --- malformed token ---

    @Test
    void getContentRejectsMalformedTokenFormat() {
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-enough-colons".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.getContent(UUID.randomUUID(), token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid attachment token");
    }

    // --- cleanup recent (no orphans) ---

    @Test
    void cleanupOrphanAttachmentsSkipsRecent() {
        when(forumAttachmentRepository.findByPostIdIsNullAndReplyIdIsNullAndCreatedAtBefore(any()))
                .thenReturn(List.of());

        int deleted = service.cleanupOrphanAttachments();

        assertThat(deleted).isZero();
        verify(forumAttachmentRepository, never()).delete(any());
    }

    // helpers

    private byte[] buildWebPHeader() {
        byte[] header = new byte[12];
        byte[] riff = "RIFF".getBytes(StandardCharsets.US_ASCII);
        byte[] webp = "WEBP".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(riff, 0, header, 0, 4);
        // bytes 4-7: file size (arbitrary)
        System.arraycopy(webp, 0, header, 8, 4);
        return header;
    }

    private MultipartFile mockImageFile(String name, String contentType, byte[] headerBytes, long size) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(false);
        lenient().when(file.getOriginalFilename()).thenReturn(name);
        lenient().when(file.getContentType()).thenReturn(contentType);
        lenient().when(file.getSize()).thenReturn(size);
        lenient().when(file.getInputStream()).thenReturn(new ByteArrayInputStream(headerBytes));
        return file;
    }

    private ForumAttachmentEntity attachmentEntity(UUID id, String fileName, String contentType) {
        ForumAttachmentEntity entity = new ForumAttachmentEntity();
        entity.setId(id);
        entity.setUploaderId(USER_ID);
        entity.setFileName(fileName);
        entity.setFileKey("forum/attachments/" + USER_ID + "/" + id + ".ext");
        entity.setContentType(contentType);
        entity.setSizeBytes(1024L);
        entity.setAttachmentType("FILE");
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }

    private String createValidToken(UUID attachmentId) {
        long expiresAt = System.currentTimeMillis() + TTL_SECONDS * 1000;
        return createTokenAtTime(attachmentId, expiresAt);
    }

    private String createTokenAtTime(UUID attachmentId, long expiresAt) {
        try {
            String payload = attachmentId + ":" + expiresAt;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TOKEN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
