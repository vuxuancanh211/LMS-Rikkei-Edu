package project.lms_rikkei_edu.modules.ai;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.service.ingestion.PdfIngestionHandler;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfIngestionHandlerTest {

    private final S3Service s3Service = mock(S3Service.class);
    private final PdfIngestionHandler handler = new PdfIngestionHandler(s3Service);

    @Test
    void supportedType_isPdf() {
        assertThat(handler.supportedType()).isEqualTo(SourceType.PDF);
    }

    @Test
    void extractText_throws_whenExternalIdMissing() {
        AiSource source = AiSource.builder().id(UUID.randomUUID()).externalId(null).build();

        assertThatThrownBy(() -> handler.extractText(source)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractText_throws_whenExternalIdBlank() {
        AiSource source = AiSource.builder().id(UUID.randomUUID()).externalId("").build();

        assertThatThrownBy(() -> handler.extractText(source)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractText_returnsOneEntryPerNonEmptyPage() throws Exception {
        // Standard-14 fonts only support WinAnsi/Latin-1, so use plain ASCII for the fixture text.
        byte[] pdf = buildPdf("Page one content", "Page two content");
        String key = "courses/test.pdf";
        AiSource source = AiSource.builder().id(UUID.randomUUID()).externalId(key).build();
        when(s3Service.getObject(key)).thenReturn(wrap(pdf));

        List<String> pages = handler.extractText(source);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0)).contains("Page one content");
        assertThat(pages.get(1)).contains("Page two content");
    }

    @Test
    void extractText_skipsBlankPages() throws Exception {
        byte[] pdf = buildPdf("", "Only content");
        String key = "courses/blank-first-page.pdf";
        AiSource source = AiSource.builder().id(UUID.randomUUID()).externalId(key).build();
        when(s3Service.getObject(key)).thenReturn(wrap(pdf));

        List<String> pages = handler.extractText(source);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0)).contains("Only content");
    }

    private static byte[] buildPdf(String... pageTexts) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (!text.isEmpty()) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(font, 12);
                        cs.newLineAtOffset(50, 700);
                        cs.showText(text);
                        cs.endText();
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static ResponseInputStream<GetObjectResponse> wrap(byte[] bytes) {
        return new ResponseInputStream<>(GetObjectResponse.builder().build(), new ByteArrayInputStream(bytes));
    }
}
