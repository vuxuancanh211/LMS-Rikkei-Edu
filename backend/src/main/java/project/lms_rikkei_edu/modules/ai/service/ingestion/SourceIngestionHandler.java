package project.lms_rikkei_edu.modules.ai.service.ingestion;

import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;

import java.util.List;

/**
 * Strategy interface for extracting plain-text content from a specific source type.
 *
 * <p>To add support for a new file format (e.g. PDF, DOCX):
 * <ol>
 *   <li>Add its value to {@link SourceType}.</li>
 *   <li>Create a Spring bean that implements this interface.</li>
 *   <li>Return the new type from {@link #supportedType()}.</li>
 * </ol>
 * The {@link IngestionOrchestrator} auto-discovers all handlers via Spring's
 * dependency injection — no switch/if chain to update.
 */
public interface SourceIngestionHandler {

    /** The source type this handler knows how to process. */
    SourceType supportedType();

    /**
     * Extract raw text segments from {@code source}.
     * Each segment may be a paragraph, page, or logical section.
     * The orchestrator is responsible for chunking and embedding.
     *
     * @return non-null, possibly empty list of text segments
     */
    List<String> extractText(AiSource source);
}
