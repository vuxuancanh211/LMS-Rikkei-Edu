package project.lms_rikkei_edu.modules.group.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StudentSearchResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private UUID courseId;
    private String courseTitle;
}
