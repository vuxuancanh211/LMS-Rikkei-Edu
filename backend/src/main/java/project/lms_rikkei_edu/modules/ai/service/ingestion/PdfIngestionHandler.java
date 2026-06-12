package project.lms_rikkei_edu.modules.ai.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;

import java.util.List;

/**
 * Placeholder for PDF ingestion — NOT yet registered as a Spring bean (@Component).
 *
 * <h3>How to activate:</h3>
 * <ol>
 *   <li>Add the PDF parsing library to pom.xml (e.g. Apache PDFBox or iText):</li>
 *   <pre>{@code
 *   <dependency>
 *       <groupId>org.apache.pdfbox</groupId>
 *       <artifactId>pdfbox</artifactId>
 *       <version>3.0.2</version>
 *   </dependency>
 *   }</pre>
 *   <li>Uncomment {@code @Component} on this class.</li>
 *   <li>Inject {@code software.amazon.awssdk.services.s3.S3Client} and implement
 *       {@code extractText} to:
 *       <ul>
 *         <li>Read {@code source.getMetadata().get("s3Key")} for the object path.</li>
 *         <li>Download the PDF bytes from S3.</li>
 *         <li>Parse each page with {@code PDDocument} → {@code PDFTextStripper}.</li>
 *         <li>Return one {@code String} per page.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * No changes needed in {@link IngestionOrchestrator} — it auto-discovers all
 * {@link SourceIngestionHandler} beans at startup.
 */
@Slf4j
// @Component   ← uncomment when implemented
public class PdfIngestionHandler implements SourceIngestionHandler {

    @Override
    public SourceType supportedType() {
        return SourceType.PDF;
    }

    @Override
    public List<String> extractText(AiSource source) {
        throw new UnsupportedOperationException(
                "PDF ingestion is not yet implemented. " +
                "See class Javadoc for activation instructions.");
    }
}
