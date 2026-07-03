package project.lms_rikkei_edu.modules.csvimport.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberCsvImportRowResult {

    private int rowNumber;
    private String email;
    private String status;
    private List<String> errors;
}
