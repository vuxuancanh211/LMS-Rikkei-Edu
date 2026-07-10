package project.lms_rikkei_edu.modules.assignment.dto.request;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublishAssignmentRequest {

    @AssertTrue(message = "Bạn phải xác nhận publish")
    private boolean confirm;

    private boolean sendNotification = true;
}
