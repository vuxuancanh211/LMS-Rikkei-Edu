package project.lms_rikkei_edu.modules.csvimport.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.csvimport.dto.request.GroupMemberCsvImportConfirmRequest;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.csvimport.service.GroupMemberCsvImportService;

import java.util.UUID;

@RestController
@RequestMapping("/api/instructor/groups/{groupId}/members/import-csv")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
public class GroupMemberCsvImportController {

    private final GroupMemberCsvImportService groupMemberCsvImportService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GroupMemberCsvImportPreviewResponse> preview(
            @PathVariable UUID groupId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(groupMemberCsvImportService.preview(groupId, file));
    }

    @PostMapping("/confirm")
    public ResponseEntity<GroupMemberCsvImportConfirmResponse> confirm(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupMemberCsvImportConfirmRequest request) {
        return ResponseEntity.ok(groupMemberCsvImportService.confirm(groupId, request.getToken()));
    }
}
