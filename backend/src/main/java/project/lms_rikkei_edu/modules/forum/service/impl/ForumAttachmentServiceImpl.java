package project.lms_rikkei_edu.modules.forum.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAttachmentResponse;
import project.lms_rikkei_edu.modules.forum.entity.ForumAttachmentEntity;
import project.lms_rikkei_edu.modules.forum.repository.ForumAttachmentRepository;
import project.lms_rikkei_edu.modules.forum.service.ForumAttachmentService;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class ForumAttachmentServiceImpl implements ForumAttachmentService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS_PER_REQUEST = 10;
    private static final long MAX_TOTAL_ATTACHMENTS_BYTES = 50L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", Set.of(".jpg", ".jpeg")),
            Map.entry("image/png", Set.of(".png")),
            Map.entry("image/webp", Set.of(".webp")),
            Map.entry("image/gif", Set.of(".gif")),
            Map.entry("application/pdf", Set.of(".pdf")),
            Map.entry("application/msword", Set.of(".doc")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of(".docx")),
            Map.entry("application/vnd.ms-powerpoint", Set.of(".ppt")),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of(".pptx")),
            Map.entry("application/vnd.ms-excel", Set.of(".xls")),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of(".xlsx")),
            Map.entry("text/plain", Set.of(".txt")),
            Map.entry("application/zip", Set.of(".zip")),
            Map.entry("application/x-zip-compressed", Set.of(".zip"))
    );
    private static final Set<String> FILE_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "application/zip",
            "application/x-zip-compressed"
    );

    private final ForumAttachmentRepository forumAttachmentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final S3Client s3Client;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.jwt.secret}")
    private String tokenSecret;

    @Value("${app.forum.attachments.content-token-ttl-seconds:3600}")
    private long contentTokenTtlSeconds;

    @Value("${app.forum.attachments.orphan-ttl-hours:24}")
    private long orphanTtlHours;

    @Override
    @Transactional
    public ForumAttachmentResponse upload(MultipartFile file) {
        UUID uploaderId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));

        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is required", HttpStatus.BAD_REQUEST);
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String attachmentType = resolveAttachmentType(contentType);
        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(originalFileName);
        validateExtension(contentType, extension);
        validateSignature(file, contentType);

        long maxBytes = "IMAGE".equals(attachmentType) ? MAX_IMAGE_BYTES : MAX_FILE_BYTES;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("File size exceeds limit", HttpStatus.BAD_REQUEST);
        }

        UUID attachmentId = UUID.randomUUID();
        String key = "forum/attachments/" + uploaderId + "/" + attachmentId + extension;

        try {
            s3Client.putObject(
                    request -> request.bucket(bucket).key(key).contentType(contentType),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException exception) {
            throw new BusinessException("Unable to read upload file", HttpStatus.BAD_REQUEST);
        }

        ForumAttachmentEntity attachment = new ForumAttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setUploaderId(uploaderId);
        attachment.setFileName(originalFileName);
        attachment.setFileKey(key);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setAttachmentType(attachmentType);
        attachment.setCreatedAt(OffsetDateTime.now());

        return toResponse(forumAttachmentRepository.save(attachment));
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentContent getContent(UUID attachmentId, String token) {
        validateContentToken(attachmentId, token);
        ForumAttachmentEntity attachment = forumAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException("Attachment not found", HttpStatus.NOT_FOUND));
        ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(request -> request
                .bucket(bucket)
                .key(attachment.getFileKey())
        );
        return new AttachmentContent(object.asByteArray(), attachment.getContentType(), attachment.getFileName());
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${app.forum.attachments.cleanup-delay-ms:3600000}")
    public int cleanupOrphanAttachments() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(orphanTtlHours);
        List<ForumAttachmentEntity> orphanAttachments = forumAttachmentRepository.findByPostIdIsNullAndReplyIdIsNullAndCreatedAtBefore(cutoff);
        int deleted = 0;

        for (ForumAttachmentEntity attachment : orphanAttachments) {
            try {
                s3Client.deleteObject(request -> request.bucket(bucket).key(attachment.getFileKey()));
                forumAttachmentRepository.delete(attachment);
                deleted++;
            } catch (NoSuchKeyException exception) {
                forumAttachmentRepository.delete(attachment);
                deleted++;
            }
        }

        return deleted;
    }

    @Override
    @Transactional
    public void attachToPost(List<UUID> attachmentIds, UUID postId, UUID uploaderId) {
        attach(attachmentIds, uploaderId, attachment -> attachment.setPostId(postId));
    }

    @Override
    @Transactional
    public void attachToReply(List<UUID> attachmentIds, UUID replyId, UUID uploaderId) {
        attach(attachmentIds, uploaderId, attachment -> attachment.setReplyId(replyId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<ForumAttachmentResponse>> findByPostIds(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        return groupByTarget(forumAttachmentRepository.findByPostIdInOrderByCreatedAtAsc(postIds), true);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<ForumAttachmentResponse>> findByReplyIds(List<UUID> replyIds) {
        if (replyIds == null || replyIds.isEmpty()) return Map.of();
        return groupByTarget(forumAttachmentRepository.findByReplyIdInOrderByCreatedAtAsc(replyIds), false);
    }

    private ForumAttachmentResponse toResponse(ForumAttachmentEntity attachment) {
        return ForumAttachmentResponse.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .url("/api/forum/attachments/" + attachment.getId() + "/content?token=" + createContentToken(attachment.getId()))
                .contentType(attachment.getContentType())
                .sizeBytes(attachment.getSizeBytes())
                .attachmentType(attachment.getAttachmentType())
                .build();
    }

    private void attach(List<UUID> attachmentIds, UUID uploaderId, java.util.function.Consumer<ForumAttachmentEntity> targetSetter) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return;
        if (attachmentIds.size() > MAX_ATTACHMENTS_PER_REQUEST) {
            throw new BusinessException("Too many attachments", HttpStatus.BAD_REQUEST);
        }

        List<ForumAttachmentEntity> attachments = forumAttachmentRepository.findByIdInAndUploaderId(attachmentIds, uploaderId);
        if (attachments.size() != attachmentIds.size()) {
            throw new BusinessException("Invalid attachment", HttpStatus.BAD_REQUEST);
        }

        long totalSize = attachments.stream().mapToLong(ForumAttachmentEntity::getSizeBytes).sum();
        if (totalSize > MAX_TOTAL_ATTACHMENTS_BYTES) {
            throw new BusinessException("Total attachment size exceeds limit", HttpStatus.BAD_REQUEST);
        }

        for (ForumAttachmentEntity attachment : attachments) {
            if (attachment.getPostId() != null || attachment.getReplyId() != null) {
                throw new BusinessException("Attachment is already used", HttpStatus.BAD_REQUEST);
            }
            targetSetter.accept(attachment);
        }
    }

    private Map<UUID, List<ForumAttachmentResponse>> groupByTarget(List<ForumAttachmentEntity> attachments, boolean postTarget) {
        Map<UUID, List<ForumAttachmentResponse>> grouped = new HashMap<>();
        for (ForumAttachmentEntity attachment : attachments) {
            UUID targetId = postTarget ? attachment.getPostId() : attachment.getReplyId();
            grouped.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(toResponse(attachment));
        }
        return grouped;
    }

    private String resolveAttachmentType(String contentType) {
        if (IMAGE_TYPES.contains(contentType)) return "IMAGE";
        if (FILE_TYPES.contains(contentType)) return "FILE";
        throw new BusinessException("File type is not allowed", HttpStatus.BAD_REQUEST);
    }

    private String sanitizeFileName(String fileName) {
        String normalized = fileName == null || fileName.isBlank() ? "attachment" : fileName.trim();
        return normalized.replaceAll("[\\\\/\u0000-\u001F]", "_");
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) return "";
        return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String contentType, String extension) {
        Set<String> allowed = ALLOWED_EXTENSIONS.get(contentType);
        if (allowed == null || !allowed.contains(extension)) {
            throw new BusinessException("File extension does not match file type", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateSignature(MultipartFile file, String contentType) {
        byte[] header = new byte[12];
        int read;
        try (var inputStream = file.getInputStream()) {
            read = inputStream.read(header);
        } catch (IOException exception) {
            throw new BusinessException("Unable to read upload file", HttpStatus.BAD_REQUEST);
        }

        byte[] actual = read <= 0 ? new byte[0] : Arrays.copyOf(header, read);
        boolean valid = switch (contentType) {
            case "image/jpeg" -> startsWith(actual, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(actual, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif" -> startsWithAscii(actual, "GIF87a") || startsWithAscii(actual, "GIF89a");
            case "image/webp" -> startsWithAscii(actual, "RIFF") && actual.length >= 12 && new String(actual, 8, 4).equals("WEBP");
            case "application/pdf" -> startsWithAscii(actual, "%PDF-");
            case "application/zip", "application/x-zip-compressed",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> startsWith(actual, 0x50, 0x4B, 0x03, 0x04) || startsWith(actual, 0x50, 0x4B, 0x05, 0x06) || startsWith(actual, 0x50, 0x4B, 0x07, 0x08);
            case "application/msword", "application/vnd.ms-powerpoint", "application/vnd.ms-excel" -> startsWith(actual, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case "text/plain" -> true;
            default -> false;
        };

        if (!valid) {
            throw new BusinessException("File signature does not match file type", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean startsWith(byte[] actual, int... expected) {
        if (actual.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((actual[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private boolean startsWithAscii(byte[] actual, String expected) {
        byte[] expectedBytes = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (actual.length < expectedBytes.length) return false;
        for (int i = 0; i < expectedBytes.length; i++) {
            if (actual[i] != expectedBytes[i]) return false;
        }
        return true;
    }

    private String createContentToken(UUID attachmentId) {
        long expiresAt = System.currentTimeMillis() + contentTokenTtlSeconds * 1000;
        String payload = attachmentId + ":" + expiresAt;
        String signature = hmac(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    private void validateContentToken(UUID attachmentId, String token) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Invalid attachment token", HttpStatus.FORBIDDEN);
        }

        String[] parts = decoded.split(":", 3);
        if (parts.length != 3 || !parts[0].equals(attachmentId.toString())) {
            throw new BusinessException("Invalid attachment token", HttpStatus.FORBIDDEN);
        }

        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException exception) {
            throw new BusinessException("Invalid attachment token", HttpStatus.FORBIDDEN);
        }

        if (expiresAt < System.currentTimeMillis()) {
            throw new BusinessException("Attachment token expired", HttpStatus.FORBIDDEN);
        }

        String payload = parts[0] + ":" + parts[1];
        if (!hmac(payload).equals(parts[2])) {
            throw new BusinessException("Invalid attachment token", HttpStatus.FORBIDDEN);
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign attachment token", exception);
        }
    }
}
