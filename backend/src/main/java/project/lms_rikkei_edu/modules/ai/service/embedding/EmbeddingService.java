package project.lms_rikkei_edu.modules.ai.service.embedding;

import java.util.List;

/**
 * Converts text into dense vector embeddings.
 *
 * <p>The returned {@code float[]} length must match
 * the {@code openai.embedding-dimension} configuration (default 1024).
 */
public interface EmbeddingService {

    /** Embed a single piece of text. */
    float[] embed(String text);

    /**
     * Embed multiple texts in one API call.
     * Preserves input order in the returned list.
     */
    List<float[]> embedBatch(List<String> texts);
}
