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
public class CsvImportPreviewResponse {

    private String token;
    private int totalRows;
    private int validCount;
    private int formatErrorCount;
    private int duplicateInFileCount;
    private int duplicateInDbCount;
    private List<CsvImportRowResult> rows;
}
