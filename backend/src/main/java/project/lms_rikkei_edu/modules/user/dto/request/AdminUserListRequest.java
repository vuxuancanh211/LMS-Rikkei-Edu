package project.lms_rikkei_edu.modules.user.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserListRequest {

    private String search;

    private String role;

    private String status;

    private String sortBy = "created_at";

    private String sortDir = "desc";

    @Min(value = 1, message = "Page must be at least 1")
    private int page = 1;

    @Min(value = 1, message = "Size must be at least 1")
    private int size = 10;
}
