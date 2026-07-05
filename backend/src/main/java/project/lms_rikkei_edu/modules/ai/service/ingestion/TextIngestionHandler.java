package project.lms_rikkei_edu.modules.ai.service.ingestion;

import org.springframework.stereotype.Component;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;

import java.util.List;
import java.util.Map;

/**
 * Handles plain-text sources.
 *
 * <p>Text is read from (in priority order):
 * <ol>
 *   <li>{@code metadata["content"]} — explicit text payload in the metadata map</li>
 *   <li>{@code sourceUrl} — treated as raw text if no URL fetching is available</li>
 * </ol>
 *
 * <p>To add PDF support, create a new class implementing {@link SourceIngestionHandler}
 * with {@code supportedType() = SourceType.PDF} — no changes needed here.
 */
@Component
public class TextIngestionHandler implements SourceIngestionHandler {

    @Override
    public SourceType supportedType() {
        return SourceType.TEXT;
    }

    @Override
    public List<String> extractText(AiSource source) {
        Map<String, Object> meta = source.getMetadata();

        if (meta != null && meta.get("content") instanceof String text && !text.isBlank()) {
            return List.of(text);
        }

        if (source.getSourceUrl() != null && !source.getSourceUrl().isBlank()) {
            return List.of(source.getSourceUrl());
        }

        return List.of();
    }
}
