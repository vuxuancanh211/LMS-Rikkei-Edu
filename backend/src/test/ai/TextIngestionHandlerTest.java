package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.service.ingestion.TextIngestionHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TextIngestionHandlerTest {

    private final TextIngestionHandler handler = new TextIngestionHandler();

    @Test
    void supportedType_isText() {
        assertThat(handler.supportedType()).isEqualTo(SourceType.TEXT);
    }

    @Test
    void extractText_prefersMetadataContent_overSourceUrl() {
        AiSource source = AiSource.builder().id(UUID.randomUUID())
                .metadata(Map.of("content", "Nội dung từ metadata"))
                .sourceUrl("Nội dung từ sourceUrl")
                .build();

        List<String> result = handler.extractText(source);

        assertThat(result).containsExactly("Nội dung từ metadata");
    }

    @Test
    void extractText_fallsBackToSourceUrl_whenNoMetadataContent() {
        AiSource source = AiSource.builder().id(UUID.randomUUID())
                .metadata(null)
                .sourceUrl("Nội dung raw text")
                .build();

        List<String> result = handler.extractText(source);

        assertThat(result).containsExactly("Nội dung raw text");
    }

    @Test
    void extractText_fallsBackToSourceUrl_whenMetadataContentIsBlank() {
        AiSource source = AiSource.builder().id(UUID.randomUUID())
                .metadata(Map.of("content", "   "))
                .sourceUrl("Nội dung raw text")
                .build();

        List<String> result = handler.extractText(source);

        assertThat(result).containsExactly("Nội dung raw text");
    }

    @Test
    void extractText_fallsBackToSourceUrl_whenMetadataContentIsNotAString() {
        AiSource source = AiSource.builder().id(UUID.randomUUID())
                .metadata(Map.of("content", 123))
                .sourceUrl("Nội dung raw text")
                .build();

        List<String> result = handler.extractText(source);

        assertThat(result).containsExactly("Nội dung raw text");
    }

    @Test
    void extractText_returnsEmpty_whenNeitherContentNorSourceUrlPresent() {
        AiSource source = AiSource.builder().id(UUID.randomUUID())
                .metadata(null)
                .sourceUrl(null)
                .build();

        assertThat(handler.extractText(source)).isEmpty();
    }

    @Test
    void extractText_returnsEmpty_whenSourceUrlBlank() {
        AiSource source = AiSource.builder().id(UUID.randomUUID())
                .metadata(null)
                .sourceUrl("   ")
                .build();

        assertThat(handler.extractText(source)).isEmpty();
    }
}
