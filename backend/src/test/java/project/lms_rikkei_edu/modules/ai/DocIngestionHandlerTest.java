package project.lms_rikkei_edu.modules.ai;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.service.ingestion.DocIngestionHandler;
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

class DocIngestionHandlerTest {

    private final S3Service s3Service = mock(S3Service.class);
    private final DocIngestionHandler handler = new DocIngestionHandler(s3Service);

    @Test
    void supportedType_isDoc() {
        assertThat(handler.supportedType()).isEqualTo(SourceType.DOC);
    }

    @Test
    void extractText_throws_whenExternalIdMissing() {
        AiSource source = AiSource.builder().id(UUID.randomUUID()).externalId(null).build();

        assertThatThrownBy(() -> handler.extractText(source)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractText_throws_whenExternalIdBlank() {
        AiSource source = AiSource.builder().id(UUID.randomUUID()).externalId("  ").build();

        assertThatThrownBy(() -> handler.extractText(source)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractText_splitsOnBlankParagraphs_mergesConsecutiveNonBlankOnes() throws Exception {
        // Consecutive non-blank paragraphs are merged into one segment; a blank paragraph starts a new one.
        byte[] docx = buildDocx("Đoạn văn 1", "Đoạn văn 2", "", "Đoạn văn 3");
        String key = "courses/test.docx";
        AiSource source = AiSource.builder().id(UUID.randomUUID()).externalId(key).build();
        when(s3Service.getObject(key)).thenReturn(wrap(docx));

        List<String> paragraphs = handler.extractText(source);

        assertThat(paragraphs).containsExactly("Đoạn văn 1\nĐoạn văn 2", "Đoạn văn 3");
    }

    @Test
    void extractText_returnsEmptyList_forDocWithNoText() throws Exception {
        byte[] docx = buildDocx();
        String key = "courses/empty.docx";
        AiSource source = AiSource.builder().id(UUID.randomUUID()).externalId(key).build();
        when(s3Service.getObject(key)).thenReturn(wrap(docx));

        assertThat(handler.extractText(source)).isEmpty();
    }

    private static byte[] buildDocx(String... paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                XWPFParagraph p = doc.createParagraph();
                if (!text.isEmpty()) {
                    p.createRun().setText(text);
                }
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static ResponseInputStream<GetObjectResponse> wrap(byte[] bytes) {
        return new ResponseInputStream<>(GetObjectResponse.builder().build(), new ByteArrayInputStream(bytes));
    }
}
