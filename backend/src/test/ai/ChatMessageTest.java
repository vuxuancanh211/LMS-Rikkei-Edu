package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.ai.service.llm.ChatMessage;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTest {

    @Test
    void system_setsRoleAndContent() {
        ChatMessage m = ChatMessage.system("Bạn là trợ lý AI");

        assertThat(m.role()).isEqualTo("system");
        assertThat(m.content()).isEqualTo("Bạn là trợ lý AI");
    }

    @Test
    void user_setsRoleAndContent() {
        ChatMessage m = ChatMessage.user("Xin chào");

        assertThat(m.role()).isEqualTo("user");
        assertThat(m.content()).isEqualTo("Xin chào");
    }

    @Test
    void assistant_setsRoleAndContent() {
        ChatMessage m = ChatMessage.assistant("Chào bạn");

        assertThat(m.role()).isEqualTo("assistant");
        assertThat(m.content()).isEqualTo("Chào bạn");
    }
}
