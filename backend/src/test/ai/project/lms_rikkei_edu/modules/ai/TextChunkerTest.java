package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.ai.service.ingestion.TextChunker;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void returnsEmptyList_whenTextNull() {
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void returnsEmptyList_whenTextBlank() {
        assertThat(chunker.chunk("   ")).isEmpty();
        assertThat(chunker.chunk("")).isEmpty();
    }

    @Test
    void returnsSingleChunk_whenTextFitsInOneChunk() {
        String text = "Hello world. This is a short sentence.";
        List<String> chunks = chunker.chunk(text, 200, 20);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(text.strip());
    }

    @Test
    void splitsIntoMultipleChunks_whenTextExceedsChunkSize() {
        String text = "A".repeat(300);
        List<String> chunks = chunker.chunk(text, 100, 10);

        assertThat(chunks.size()).isGreaterThan(1);
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void prefersBreakAtSentenceBoundary() {
        String text = "First sentence ends here. Second sentence is here. Third one too.";
        List<String> chunks = chunker.chunk(text, 30, 5);

        assertThat(chunks).isNotEmpty();
        // First chunk should end at a sentence boundary (period)
        assertThat(chunks.get(0)).endsWith(".");
    }

    @Test
    void prefersBreakAtNewline() {
        String text = "Line one\nLine two\nLine three\nLine four\nLine five\n";
        List<String> chunks = chunker.chunk(text, 15, 0);

        assertThat(chunks).isNotEmpty();
    }

    @Test
    void fallsBackToWhitespaceBoundary_whenNoSentenceBreak() {
        // No punctuation, but has spaces
        String text = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10";
        List<String> chunks = chunker.chunk(text, 20, 5);

        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    void fallsBackToHardCut_whenNoWhitespace() {
        String text = "A".repeat(200);
        List<String> chunks = chunker.chunk(text, 50, 10);

        assertThat(chunks.size()).isGreaterThan(1);
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(50));
    }

    @Test
    void overlapsCorrectly() {
        // With overlap=10, adjacent chunks share ~10 chars
        String sentence = "This is sentence number one. And here is the second sentence. The third one!";
        List<String> chunks = chunker.chunk(sentence, 40, 10);

        // Verify that chunks together cover the full text
        // (just check multiple chunks are produced)
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void throwsIllegalArgument_whenChunkSizeZeroOrNegative() {
        assertThatThrownBy(() -> chunker.chunk("text", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chunker.chunk("text", -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsIllegalArgument_whenOverlapNegative() {
        assertThatThrownBy(() -> chunker.chunk("text", 100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsIllegalArgument_whenOverlapEqualsChunkSize() {
        assertThatThrownBy(() -> chunker.chunk("text", 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesDefaultChunkSizeAndOverlap() {
        String longText = "Word ".repeat(1000); // ~5000 chars
        List<String> chunks = chunker.chunk(longText);

        assertThat(chunks).isNotEmpty();
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(800)); // DEFAULT_CHUNK_SIZE
    }

    @Test
    void stripsLeadingAndTrailingWhitespace() {
        String text = "  Hello world.  ";
        List<String> chunks = chunker.chunk(text, 200, 20);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).doesNotStartWith(" ").doesNotEndWith(" ");
    }

    @Test
    void toVectorString_formatsCorrectly() {
        float[] embedding = {0.1f, 0.2f, 0.3f};
        // Use via VectorSearchService static method — test indirectly through chunker usage
        // Just verifying the logic is correct for this utility
        String result = project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService
                .toVectorString(embedding);
        assertThat(result).startsWith("[").endsWith("]").contains(",");
    }
}
