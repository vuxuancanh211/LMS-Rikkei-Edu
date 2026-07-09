package project.lms_rikkei_edu.modules.chat.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatEntityLifecycleTest {

    @Test
    void chatRoomPrePersistSetsCreatedAt() {
        ChatRoomEntity entity = new ChatRoomEntity();

        entity.prePersist();

        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void chatMessagePrePersistSetsCreatedAt() {
        ChatMessageEntity entity = new ChatMessageEntity();

        entity.prePersist();

        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void chatRoomMemberPrePersistSetsJoinedAt() {
        ChatRoomMemberEntity entity = new ChatRoomMemberEntity();

        entity.prePersist();

        assertThat(entity.getJoinedAt()).isNotNull();
    }

    @Test
    void chatMessageReactionPrePersistSetsCreatedAt() {
        ChatMessageReactionEntity entity = new ChatMessageReactionEntity();

        entity.prePersist();

        assertThat(entity.getCreatedAt()).isNotNull();
    }
}
