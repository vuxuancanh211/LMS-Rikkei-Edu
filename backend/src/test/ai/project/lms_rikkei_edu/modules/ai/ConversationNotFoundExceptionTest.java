package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.ai.exception.ConversationNotFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationNotFoundExceptionTest {

    @Test
    void message_includesConversationId() {
        UUID id = UUID.randomUUID();

        ConversationNotFoundException ex = new ConversationNotFoundException(id);

        assertThat(ex.getMessage()).isEqualTo("Conversation not found: " + id);
    }
}
