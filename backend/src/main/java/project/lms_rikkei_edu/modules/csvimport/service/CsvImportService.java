package project.lms_rikkei_edu.modules.csvimport.service;

import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportPreviewResponse;

import java.util.UUID;

public interface CsvImportService {

    CsvImportPreviewResponse preview(MultipartFile file, String defaultRole);

    CsvImportConfirmResponse confirm(String token, UUID adminId);
}
