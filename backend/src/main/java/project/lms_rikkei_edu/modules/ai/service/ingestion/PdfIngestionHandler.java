package project.lms_rikkei_edu.modules.ai.service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
public class PdfIngestionHandler implements SourceIngestionHandler {

    private final S3Service s3Service;

    @Override
    public SourceType supportedType() {
        return SourceType.PDF;
    }

    @Override
    public List<String> extractText(AiSource source) {
        String s3Key = source.getExternalId();
        if (s3Key == null || s3Key.isBlank()) {
            throw new IllegalArgumentException("AiSource missing S3 key (externalId) for PDF: " + source.getId());
        }

        log.info("Extracting PDF text from S3 key={}", s3Key);

        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Service.getObject(s3Key);
             PDDocument doc = Loader.loadPDF(s3Stream.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>();

            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc).trim();
                if (!text.isEmpty()) {
                    pages.add(text);
                }
            }

            log.info("PDF extracted: s3Key={}, pages={}, non-empty={}", s3Key, doc.getNumberOfPages(), pages.size());
            return pages;

        } catch (IOException e) {
            throw new RuntimeException("Failed to extract text from PDF: " + s3Key, e);
        }
    }
}
