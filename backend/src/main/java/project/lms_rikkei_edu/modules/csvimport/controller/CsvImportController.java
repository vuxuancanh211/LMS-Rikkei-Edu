package project.lms_rikkei_edu.modules.csvimport.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.csvimport.dto.request.CsvImportConfirmRequest;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.CsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.csvimport.service.CsvImportService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users/import-csv")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CsvImportPreviewResponse> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("defaultRole") String defaultRole) {
        return ResponseEntity.ok(csvImportService.preview(file, defaultRole));
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CsvImportConfirmResponse> confirm(
            @Valid @RequestBody CsvImportConfirmRequest request) {
        UUID adminId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Admin not authenticated"));
        return ResponseEntity.ok(csvImportService.confirm(request.getToken(), adminId));
    }
}
