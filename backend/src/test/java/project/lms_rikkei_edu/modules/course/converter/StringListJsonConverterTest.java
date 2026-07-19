package project.lms_rikkei_edu.modules.course.converter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringListJsonConverterTest {

    private final StringListJsonConverter converter = new StringListJsonConverter();

    @Test
    void testConvertToDatabaseColumn() {
        // null or empty
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToDatabaseColumn(Collections.emptyList()));

        // valid list
        List<String> validList = Arrays.asList("item1", "item2");
        String jsonResult = converter.convertToDatabaseColumn(validList);
        assertNotNull(jsonResult);
        assertTrue(jsonResult.contains("item1"));
        assertTrue(jsonResult.contains("item2"));
    }

    @Test
    void testConvertToEntityAttribute() {
        // null or blank
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute(null));
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute(""));
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute("   "));

        // valid json
        String validJson = "[\"item1\",\"item2\"]";
        List<String> resultList = converter.convertToEntityAttribute(validJson);
        assertNotNull(resultList);
        assertEquals(2, resultList.size());
        assertEquals("item1", resultList.get(0));
        assertEquals("item2", resultList.get(1));

        // invalid json
        String invalidJson = "invalid-json";
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute(invalidJson));
    }
}
