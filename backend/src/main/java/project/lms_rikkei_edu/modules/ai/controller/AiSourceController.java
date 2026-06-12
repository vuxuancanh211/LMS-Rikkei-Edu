package project.lms_rikkei_edu.modules.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.modules.ai.dto.request.SourceIngestRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceResponse;
import project.lms_rikkei_edu.modules.ai.service.AiSourceService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/sources")
@RequiredArgsConstructor
public class AiSourceController {

    private final AiSourceService sourceService;

    /** Ingest a new knowledge source and start embedding. */
    @PostMapping
    public ResponseEntity<SourceResponse> ingest(@Valid @RequestBody SourceIngestRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.ingest(req));
    }

    /** List all active sources for a course. */
    @GetMapping
    public ResponseEntity<List<SourceResponse>> list(@RequestParam UUID courseId) {
        return ResponseEntity.ok(sourceService.listByCourse(courseId));
    }

    /** Get a single source by id. */
    @GetMapping("/{id}")
    public ResponseEntity<SourceResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(sourceService.getById(id));
    }

    /** Soft-delete a source and remove all its chunks. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        sourceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Re-trigger ingestion (clears old chunks first). */
    @PostMapping("/{id}/reingest")
    public ResponseEntity<SourceResponse> reingest(@PathVariable UUID id) {
        return ResponseEntity.ok(sourceService.reingest(id));
    }
}
