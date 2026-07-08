package project.lms_rikkei_edu.modules.group.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AddGroupMembersRequest {

    @NotEmpty(message = "Email list is required")
    private List<String> emails;
}
