package project.lms_rikkei_edu.modules.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(max = 100, message = "Group name must not exceed 100 characters")
    private String name;

    private String description;

    @Positive(message = "Max capacity must be greater than 0")
    private Integer maxCapacity;

    private LocalDate startDate;

    private LocalDate endDate;
}
