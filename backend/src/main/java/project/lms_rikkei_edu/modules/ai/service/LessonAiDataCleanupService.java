package project.lms_rikkei_edu.modules.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.ai.entity.AiConversation;
import project.lms_rikkei_edu.modules.ai.entity.AiMessage;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.repository.AiConversationRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiIngestionJobRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageDebugRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dọn hẳn toàn bộ dữ liệu AI (nguồn tài liệu đã embedding + lịch sử hội thoại) gắn với các
 * lesson SẮP BỊ XÓA HẲN (VD: admin duyệt cập nhật xóa hẳn 1 bài giảng đã từng live).
 *
 * <p>Cả {@code ai_sources.lesson_id} lẫn {@code ai_conversations.lesson_id} đều có FK
 * REFERENCES lessons(id) nhưng KHÔNG có ON DELETE CASCADE — và bản thân 2 bảng này lại có
 * chuỗi tham chiếu riêng cũng không cascade (ai_sources -> ai_ingestion_jobs/document_chunks;
 * ai_conversations -> ai_messages -> ai_message_debugs). Xóa hẳn 1 lesson mà không dọn theo
 * đúng thứ tự (con trước, cha sau) ở TẤT CẢ các tầng này sẽ vỡ foreign key constraint ở
 * Postgres, khiến thao tác chính (VD: approveUpdate) thất bại với lỗi 500 chung chung.
 *
 * <p>{@code ai_sources.resource_id} cũng có FK REFERENCES lesson_resources(id) không cascade —
 * nguồn AI tạo từ luồng "đưa resource có sẵn vào AI" (CourseEmbeddingService.upsertResourceSource)
 * chỉ set resourceId, KHÔNG set lessonId, nên phải tìm theo CẢ 2 chiều (lessonId lẫn resourceId
 * của các resource thuộc lesson đó) mới bắt hết được — nếu không, xóa lesson_resources theo
 * cascade khi xóa lesson vẫn vỡ FK này dù đã dọn xong phần theo lessonId.
 *
 * <p>Bảng {@code ai_chunk_references} cũng tham chiếu document_chunks nhưng không có entity/
 * repository nào trong code ghi vào bảng này (tính năng chưa được dùng tới) nên bỏ qua.
 */
@Service
@RequiredArgsConstructor
public class LessonAiDataCleanupService {

    private final AiSourceRepository aiSourceRepo;
    private final DocumentChunkRepository documentChunkRepo;
    private final AiIngestionJobRepository aiIngestionJobRepo;
    private final AiConversationRepository aiConversationRepo;
    private final AiMessageRepository aiMessageRepo;
    private final AiMessageDebugRepository aiMessageDebugRepo;

    @Transactional
    public void hardDeleteByLessonIds(List<UUID> lessonIds, List<UUID> resourceIds) {
        if ((lessonIds == null || lessonIds.isEmpty()) && (resourceIds == null || resourceIds.isEmpty())) return;

        List<AiSource> sources = new ArrayList<>();
        if (lessonIds != null && !lessonIds.isEmpty()) sources.addAll(aiSourceRepo.findByLessonIdIn(lessonIds));
        if (resourceIds != null && !resourceIds.isEmpty()) sources.addAll(aiSourceRepo.findByResourceIdIn(resourceIds));
        sources = sources.stream().collect(Collectors.collectingAndThen(
                Collectors.toMap(AiSource::getId, s -> s, (a, b) -> a), m -> new ArrayList<>(m.values())));
        if (!sources.isEmpty()) {
            List<UUID> sourceIds = sources.stream().map(AiSource::getId).toList();
            documentChunkRepo.deleteBySourceIdIn(sourceIds);
            aiIngestionJobRepo.deleteBySourceIdIn(sourceIds);
            aiSourceRepo.deleteAllByIdInBulk(sourceIds);
        }

        List<AiConversation> conversations = (lessonIds == null || lessonIds.isEmpty())
                ? List.of() : aiConversationRepo.findByLessonIdIn(lessonIds);
        if (!conversations.isEmpty()) {
            List<UUID> conversationIds = conversations.stream().map(AiConversation::getId).toList();
            List<AiMessage> messages = aiMessageRepo.findByConversationIdIn(conversationIds);
            if (!messages.isEmpty()) {
                List<UUID> messageIds = messages.stream().map(AiMessage::getId).toList();
                aiMessageDebugRepo.deleteByMessageIdIn(messageIds);
            }
            aiMessageRepo.deleteByConversationIdIn(conversationIds);
            aiConversationRepo.deleteAllByIdInBulk(conversationIds);
        }
    }
}
