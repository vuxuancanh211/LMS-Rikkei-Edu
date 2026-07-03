package project.lms_rikkei_edu.modules.ai.entity.enums;

/**
 * Types of knowledge sources that can be ingested into the RAG pipeline.
 * Adding a new type requires a corresponding {@code SourceIngestionHandler} implementation.
 */
public enum SourceType {
    TEXT,   // raw plain text supplied directly in the request
    URL,    // web page to scrape
    PDF,    // PDF file stored in S3
    DOC,    // Word document stored in S3
    VIDEO;  // transcript of a lesson video

    /** Maps a MIME type to an ingestible {@link SourceType}, or null if unsupported (e.g. video/image/slide). */
    public static SourceType fromMimeType(String mimeType) {
        if (mimeType == null) return null;
        String m = mimeType.toLowerCase();
        if (m.contains("pdf")) return PDF;
        if (m.contains("word") || m.contains("docx") || m.contains("openxmlformats")) return DOC;
        return null;
    }
}
