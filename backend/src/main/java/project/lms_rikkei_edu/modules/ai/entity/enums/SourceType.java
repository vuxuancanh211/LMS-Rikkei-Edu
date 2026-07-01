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
    VIDEO   // transcript of a lesson video
}
