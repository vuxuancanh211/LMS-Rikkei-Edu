package project.lms_rikkei_edu.modules.assignment.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BatchReleaseRequest {

    @NotEmpty
    private List<UUID> submissionIds;
}
