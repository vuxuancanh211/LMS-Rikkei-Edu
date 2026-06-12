package project.lms_rikkei_edu.modules.ai.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;

import java.util.List;

/**
 * Placeholder for Word document (.docx) ingestion — NOT yet registered as a Spring bean.
 *
 * <h3>How to activate:</h3>
 * <ol>
 *   <li>Add Apache POI to pom.xml:</li>
 *   <pre>{@code
 *   <dependency>
 *       <groupId>org.apache.poi</groupId>
 *       <artifactId>poi-ooxml</artifactId>
 *       <version>5.3.0</version>
 *   </dependency>
 *   }</pre>
 *   <li>Uncomment {@code @Component} on this class.</li>
 *   <li>Implement {@code extractText} to:
 *       <ul>
 *         <li>Download the .docx from S3 using the {@code s3Key} in metadata.</li>
 *         <li>Open it with {@code XWPFDocument}.</li>
 *         <li>Return one {@code String} per paragraph or per page.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Slf4j
// @Component   ← uncomment when implemented
public class DocIngestionHandler implements SourceIngestionHandler {

    @Override
    public SourceType supportedType() {
        return SourceType.DOC;
    }

    @Override
    public List<String> extractText(AiSource source) {
        throw new UnsupportedOperationException(
                "DOC ingestion is not yet implemented. " +
                "See class Javadoc for activation instructions.");
    }
}
