package project.lms_rikkei_edu.modules.ai.service.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a long text into overlapping chunks suitable for embedding.
 *
 * <p>The algorithm tries to break at sentence boundaries (. ! ? newline)
 * rather than cutting mid-sentence. If no good boundary is found, it falls
 * back to the nearest space, and finally to the hard character limit.
 */
@Component
public class TextChunker {

    static final int DEFAULT_CHUNK_SIZE = 800;
    static final int DEFAULT_OVERLAP    = 100;

    /**
     * Chunk with default size / overlap.
     *
     * @param text raw text to split
     * @return ordered list of chunks; empty list if text is blank
     */
    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * Chunk with explicit parameters.
     *
     * @param text      raw text to split
     * @param chunkSize target maximum chunk length in characters
     * @param overlap   number of characters shared between consecutive chunks
     */
    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be > 0");
        if (overlap < 0 || overlap >= chunkSize) throw new IllegalArgumentException("overlap must be in [0, chunkSize)");

        text = text.strip();
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int hardEnd = Math.min(start + chunkSize, text.length());
            int end = (hardEnd < text.length()) ? findBreak(text, start, hardEnd) : hardEnd;

            String chunk = text.substring(start, end).strip();
            if (!chunk.isEmpty()) chunks.add(chunk);

            // If we consumed up to the end of the text, we're done.
            if (end >= text.length()) break;

            // Advance with overlap: back up by `overlap` chars so the next chunk
            // starts within the tail of the current one, ensuring context continuity.
            start = Math.max(start + 1, end - overlap);
        }

        return chunks;
    }

    /** Find the best split point in {@code [start, hardEnd]}. */
    private int findBreak(String text, int start, int hardEnd) {
        int midpoint = start + (hardEnd - start) / 2;

        // Prefer sentence-ending punctuation or newline in the back half of the window.
        for (int i = hardEnd - 1; i >= midpoint; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return Math.min(i + 1, text.length());
            }
        }

        // Fall back to a whitespace boundary.
        for (int i = hardEnd - 1; i >= midpoint; i--) {
            if (Character.isWhitespace(text.charAt(i))) return i;
        }

        return hardEnd;
    }
}
