package project.lms_rikkei_edu.modules.csvimport.service;

import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportPreviewResponse;

import java.util.UUID;

public interface GroupMemberCsvImportService {

    GroupMemberCsvImportPreviewResponse preview(UUID groupId, MultipartFile file);

    GroupMemberCsvImportConfirmResponse confirm(UUID groupId, String token);
}
