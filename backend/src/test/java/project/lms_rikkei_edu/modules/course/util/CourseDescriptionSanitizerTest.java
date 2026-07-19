package project.lms_rikkei_edu.modules.course.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CourseDescriptionSanitizerTest {

    @Test
    void testSanitize() {
        // null or blank
        assertNull(CourseDescriptionSanitizer.sanitize(null));
        assertNull(CourseDescriptionSanitizer.sanitize(""));
        assertNull(CourseDescriptionSanitizer.sanitize("   "));

        // valid simple text
        assertEquals("Hello", CourseDescriptionSanitizer.sanitize("Hello"));

        // tags allowed
        assertEquals("<b>Bold</b> and <i>Italic</i>", CourseDescriptionSanitizer.sanitize("<b>Bold</b> and <i>Italic</i>"));
        assertEquals("<u>Underline</u>", CourseDescriptionSanitizer.sanitize("<u>Underline</u>"));

        // style font-size allowed
        assertEquals("<span style=\"font-size: 16px;\">Big text</span>", CourseDescriptionSanitizer.sanitize("<span style=\"font-size: 16px;\">Big text</span>"));
        
        // invalid style stripped
        assertEquals("<span>Bad style</span>", CourseDescriptionSanitizer.sanitize("<span style=\"font-size: expression(alert(1));\">Bad style</span>"));
        assertEquals("<span>Color style</span>", CourseDescriptionSanitizer.sanitize("<span style=\"color: red; font-size: 16px;\">Color style</span>"));

        // tags removed (h1, h2, a, img, iframe, script)
        assertEquals("Heading 1", CourseDescriptionSanitizer.sanitize("<h1>Heading 1</h1>"));
        assertEquals("Link text", CourseDescriptionSanitizer.sanitize("<a href=\"http://evil.com\">Link text</a>"));
        // script/img content is stripped entirely -> blank result -> sanitize returns null (line 43 ternary)
        assertNull(CourseDescriptionSanitizer.sanitize("<script>alert(1);</script>"));
        assertNull(CourseDescriptionSanitizer.sanitize("<img src=\"test.png\" onerror=\"alert(1)\">"));
    }

    @Test
    void testSanitize_multipleStyledElementsInSameCall() {
        // exercises the withStyle for-loop across more than one element in a single sanitize() call:
        // one element keeps its style (valid font-size), the other has it stripped (invalid).
        String result = CourseDescriptionSanitizer.sanitize(
                "<span style=\"font-size: 18px;\">Big</span><span style=\"color: red;\">Red</span>");
        assertEquals("<span style=\"font-size: 18px;\">Big</span><span>Red</span>", result);
    }

    @Test
    void testSanitize_decimalFontSize() {
        // FONT_SIZE regex allows an optional decimal part (\d{1,3}(\.\d+)?px) - exercise that branch too
        assertEquals("<span style=\"font-size: 16.5px;\">Text</span>",
                CourseDescriptionSanitizer.sanitize("<span style=\"font-size: 16.5px;\">Text</span>"));
    }
}
