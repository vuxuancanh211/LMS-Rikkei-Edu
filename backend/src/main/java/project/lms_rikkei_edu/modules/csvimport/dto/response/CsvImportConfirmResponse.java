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
public class CsvImportConfirmResponse {

    private int totalProcessed;
    private int successCount;
    private int failCount;
    private List<CsvImportRowResult> results;
}
