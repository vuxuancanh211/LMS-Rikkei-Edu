package project.lms_rikkei_edu.modules.chat.exception;

import java.util.UUID;

public class ChatRoomNotFoundException extends RuntimeException {
    public ChatRoomNotFoundException(UUID roomId) {
        super("Không tìm thấy phòng chat: " + roomId);
    }
}
