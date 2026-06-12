package project.lms_rikkei_edu.modules.ai.service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocIngestionHandler implements SourceIngestionHandler {

    private final S3Service s3Service;

    @Override
    public SourceType supportedType() {
        return SourceType.DOC;
    }

    @Override
    public List<String> extractText(AiSource source) {
        String s3Key = source.getExternalId();
        if (s3Key == null || s3Key.isBlank()) {
            throw new IllegalArgumentException("AiSource missing S3 key (externalId) for DOC: " + source.getId());
        }

        log.info("Extracting DOCX text from S3 key={}", s3Key);

        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Service.getObject(s3Key);
             XWPFDocument doc = new XWPFDocument(s3Stream)) {

            List<String> paragraphs = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText().trim();
                if (text.isEmpty()) {
                    if (!current.isEmpty()) {
                        paragraphs.add(current.toString().trim());
                        current.setLength(0);
                    }
                } else {
                    current.append(text).append("\n");
                }
            }
            if (!current.isEmpty()) {
                paragraphs.add(current.toString().trim());
            }

            log.info("DOCX extracted: s3Key={}, paragraphs={}", s3Key, paragraphs.size());
            return paragraphs;

        } catch (IOException e) {
            throw new RuntimeException("Failed to extract text from DOCX: " + s3Key, e);
        }
    }
}
