package project.lms_rikkei_edu.modules.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import project.lms_rikkei_edu.modules.ai.entity.AiConversation;
import project.lms_rikkei_edu.modules.ai.entity.AiMessage;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.repository.AiConversationRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiIngestionJobRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageDebugRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LessonAiDataCleanupServiceTest {

    @Mock private AiSourceRepository aiSourceRepo;
    @Mock private DocumentChunkRepository documentChunkRepo;
    @Mock private AiIngestionJobRepository aiIngestionJobRepo;
    @Mock private AiConversationRepository aiConversationRepo;
    @Mock private AiMessageRepository aiMessageRepo;
    @Mock private AiMessageDebugRepository aiMessageDebugRepo;

    private LessonAiDataCleanupService service;

    private UUID lessonId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        service = new LessonAiDataCleanupService(
                aiSourceRepo, documentChunkRepo, aiIngestionJobRepo,
                aiConversationRepo, aiMessageRepo, aiMessageDebugRepo);
        lessonId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
    }

    private AiSource source(UUID id) {
        AiSource s = new AiSource();
        s.setId(id);
        return s;
    }

    private AiConversation conversation(UUID id) {
        AiConversation c = new AiConversation();
        c.setId(id);
        return c;
    }

    private AiMessage message(UUID id) {
        AiMessage m = new AiMessage();
        m.setId(id);
        return m;
    }

    @Test
    void bothListsNullOrEmpty_noInteractionsAtAll() {
        service.hardDeleteByLessonIds(null, null);
        service.hardDeleteByLessonIds(List.of(), List.of());

        verifyNoInteractions(aiSourceRepo, documentChunkRepo, aiIngestionJobRepo,
                aiConversationRepo, aiMessageRepo, aiMessageDebugRepo);
    }

    @Test
    void lessonIdsOnly_noSourcesNoConversations_noDeleteCalls() {
        when(aiSourceRepo.findByLessonIdIn(List.of(lessonId))).thenReturn(List.of());
        when(aiConversationRepo.findByLessonIdIn(List.of(lessonId))).thenReturn(List.of());

        service.hardDeleteByLessonIds(List.of(lessonId), null);

        verify(aiSourceRepo, never()).deleteAllByIdInBulk(anyList());
        verify(aiConversationRepo, never()).deleteAllByIdInBulk(anyList());
        verifyNoInteractions(documentChunkRepo, aiIngestionJobRepo, aiMessageRepo, aiMessageDebugRepo);
    }

    @Test
    void resourceIdsOnly_lessonIdsNull_doesNotQueryConversationsAtAll() {
        UUID sourceId = UUID.randomUUID();
        when(aiSourceRepo.findByResourceIdIn(List.of(resourceId))).thenReturn(List.of(source(sourceId)));

        service.hardDeleteByLessonIds(null, List.of(resourceId));

        verify(documentChunkRepo).deleteBySourceIdIn(List.of(sourceId));
        verify(aiIngestionJobRepo).deleteBySourceIdIn(List.of(sourceId));
        verify(aiSourceRepo).deleteAllByIdInBulk(List.of(sourceId));
        verifyNoInteractions(aiConversationRepo, aiMessageRepo, aiMessageDebugRepo);
        verify(aiSourceRepo, never()).findByLessonIdIn(anyList());
    }

    @Test
    void sourcesFoundViaBothLessonIdAndResourceId_deduplicatedById() {
        UUID sharedSourceId = UUID.randomUUID();
        UUID onlyByLessonId = UUID.randomUUID();
        // Cùng 1 AiSource được trả về từ CẢ 2 truy vấn (VD: nguồn có cả lessonId lẫn resourceId
        // set sẵn) — không được xóa/đếm 2 lần.
        when(aiSourceRepo.findByLessonIdIn(List.of(lessonId)))
                .thenReturn(List.of(source(sharedSourceId), source(onlyByLessonId)));
        when(aiSourceRepo.findByResourceIdIn(List.of(resourceId)))
                .thenReturn(List.of(source(sharedSourceId)));
        when(aiConversationRepo.findByLessonIdIn(List.of(lessonId))).thenReturn(List.of());

        service.hardDeleteByLessonIds(List.of(lessonId), List.of(resourceId));

        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiSourceRepo).deleteAllByIdInBulk(captor.capture());
        assertThat(captor.getValue()).hasSize(2).containsExactlyInAnyOrder(sharedSourceId, onlyByLessonId);
    }

    @Test
    void conversationsWithMessages_cascadesThroughMessageDebugs() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(aiSourceRepo.findByLessonIdIn(List.of(lessonId))).thenReturn(List.of());
        when(aiConversationRepo.findByLessonIdIn(List.of(lessonId)))
                .thenReturn(List.of(conversation(conversationId)));
        when(aiMessageRepo.findByConversationIdIn(List.of(conversationId)))
                .thenReturn(List.of(message(messageId)));

        service.hardDeleteByLessonIds(List.of(lessonId), null);

        verify(aiMessageDebugRepo).deleteByMessageIdIn(List.of(messageId));
        verify(aiMessageRepo).deleteByConversationIdIn(List.of(conversationId));
        verify(aiConversationRepo).deleteAllByIdInBulk(List.of(conversationId));
    }

    @Test
    void conversationsWithNoMessages_skipsMessageDebugDeleteButStillDeletesConversation() {
        UUID conversationId = UUID.randomUUID();
        when(aiSourceRepo.findByLessonIdIn(List.of(lessonId))).thenReturn(List.of());
        when(aiConversationRepo.findByLessonIdIn(List.of(lessonId)))
                .thenReturn(List.of(conversation(conversationId)));
        when(aiMessageRepo.findByConversationIdIn(List.of(conversationId))).thenReturn(List.of());

        service.hardDeleteByLessonIds(List.of(lessonId), null);

        verify(aiMessageDebugRepo, never()).deleteByMessageIdIn(anyList());
        verify(aiMessageRepo).deleteByConversationIdIn(List.of(conversationId));
        verify(aiConversationRepo).deleteAllByIdInBulk(List.of(conversationId));
    }

    @Test
    void sourcesAndConversationsBothPresent_deletesBothIndependently() {
        UUID sourceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(aiSourceRepo.findByLessonIdIn(List.of(lessonId))).thenReturn(List.of(source(sourceId)));
        when(aiConversationRepo.findByLessonIdIn(List.of(lessonId)))
                .thenReturn(List.of(conversation(conversationId)));
        when(aiMessageRepo.findByConversationIdIn(List.of(conversationId))).thenReturn(List.of());

        service.hardDeleteByLessonIds(List.of(lessonId), List.of(resourceId));

        verify(aiSourceRepo).deleteAllByIdInBulk(List.of(sourceId));
        verify(aiConversationRepo).deleteAllByIdInBulk(List.of(conversationId));
    }
}
