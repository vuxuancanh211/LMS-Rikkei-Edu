package project.lms_rikkei_edu.modules.ai.dto.response;

/** One text chunk actually extracted and embedded for a source — what the AI "read" from it. */
public record ChunkResponse(Integer chunkIndex, String sectionTitle, String chunkText) {}
