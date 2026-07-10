package project.lms_rikkei_edu.modules.certificate.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.certificate.exception.CertificatePdfException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates a decorative "diploma style" certificate PDF, restyled to the
 * app's navy / indigo / blue / green theme: navy double border, indigo
 * corner medallions, a script headline, serif institute/course text, a
 * blue-ringed seal, and a small green "verified" badge.
 *
 * NOTE: the script headline font is NOT a Standard-14 PDF font
 * (Helvetica/Times don't have a cursive variant), so we embed a
 * TrueType font at runtime. Put a .ttf file on the classpath at
 * src/main/resources/fonts/GreatVibes-Regular.ttf
 * (any elegant script font works, e.g. Google Fonts "Great Vibes",
 * "Pinyon Script", "Alex Brush"). If the file is missing we quietly
 * fall back to Times-Italic so the service never breaks.
 */
@Service
public class CertificatePdfService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ---- palette: matches the app's navy / indigo / blue / green theme --
    private static final float[] TEAL_DARK   = rgb(15, 23, 42);    // NAVY   - main dark text / outer border
    private static final float[] TEAL_LINE   = rgb(79, 70, 229);   // INDIGO - inner border, dividers, ribbon
    private static final float[] GOLD        = rgb(37, 99, 235);   // BLUE   - seal ring, accent line, course title
    private static final float[] GOLD_LIGHT  = rgb(238, 242, 255); // light indigo tint - seal fill
    private static final float[] TEXT_GRAY   = rgb(71, 85, 105);   // SLATE  - body text
    private static final float[] STAMP_RED   = rgb(185, 28, 28);

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    private static final String SCRIPT_FONT_RESOURCE = "/fonts/GreatVibes-Regular.ttf";

    public byte[] generate(
            String studentName,
            String courseTitle,
            String instructorName,
            OffsetDateTime issuedAt,
            String credentialId,
            String verifyUrl) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            document.addPage(page);

            float pageWidth = page.getMediaBox().getWidth();   // 842
            float pageHeight = page.getMediaBox().getHeight(); // 595
            float centerX = pageWidth / 2f;

            PDType1Font serifBold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            PDType1Font serif = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            PDType1Font serifItalic = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
            PDFont scriptFont = loadScriptFont(document, serifItalic);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {

                drawBorder(content, pageWidth, pageHeight);

                float y = pageHeight - 95;
                drawCenteredTracked(content, serifBold, 14, "RIKKEI EDU", centerX, y, TEAL_DARK, 2.5f);

                y -= 55;
                drawCentered(content, scriptFont, 40, "Certificate of Completion", centerX, y, TEAL_DARK);

                y -= 30;
                drawDivider(content, centerX, y);

                y -= 40;
                drawCentered(content, serifBold, 20, ascii(courseTitle).toUpperCase(Locale.ROOT), centerX, y, TEAL_DARK);

                y -= 35;
                drawCentered(content, serifItalic, 13, "This is to certify that", centerX, y, TEXT_GRAY);

                y -= 38;
                drawCentered(content, serifBold, 28, ascii(studentName), centerX, y, TEAL_DARK);

                y -= 30;
                String description = "has successfully completed all requirements of the course "
                        + "\"" + ascii(courseTitle) + "\" under the guidance of " + ascii(instructorName) + ".";
                y = drawWrappedCentered(content, serif, 10.5f, description, centerX, y, 520, 14, TEXT_GRAY);

                // ---- footer: left info block ----
                float footerY = 118;
                float leftX = 90;
                drawLabelValue(content, serifBold, serif, 11, "Certificate ID: ", credentialId, leftX, footerY);
                drawLabelValue(content, serifBold, serif, 11, "Date: ", issuedAt.format(DATE_FORMATTER), leftX, footerY - 20);
                drawLabelValue(content, serifBold, serif, 11, "Instructor: ", ascii(instructorName), leftX, footerY - 40);
                drawText(content, serif, 9.5f, "Verify: " + ascii(verifyUrl), leftX, footerY - 60, TEXT_GRAY);

                // ---- footer: seal (center) ----
                drawSeal(content, centerX, 105, 42);

                // ---- footer: official stamp (right) ----
                drawStamp(content, serifBold, serifItalic, pageWidth - 175, footerY - 8, 44);
            }

            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new CertificatePdfException("Failed to generate certificate PDF", e);
        }
    }

    // ---------------------------------------------------------------------
    // decorative elements
    // ---------------------------------------------------------------------

    private void drawBorder(PDPageContentStream content, float w, float h) throws IOException {
        float outerMargin = 22;
        float innerMargin = 34;

        content.setStrokingColor(TEAL_LINE[0], TEAL_LINE[1], TEAL_LINE[2]);
        content.setLineWidth(4f);
        content.addRect(outerMargin, outerMargin, w - 2 * outerMargin, h - 2 * outerMargin);
        content.stroke();

        content.setStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
        content.setLineWidth(1.2f);
        content.addRect(innerMargin, innerMargin, w - 2 * innerMargin, h - 2 * innerMargin);
        content.stroke();

        // Corners intentionally stay clean to match the current app visual style.
    }

    private void drawDivider(PDPageContentStream content, float centerX, float y) throws IOException {
        content.setStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
        content.setLineWidth(1f);
        content.moveTo(centerX - 90, y);
        content.lineTo(centerX - 12, y);
        content.stroke();
        content.moveTo(centerX + 12, y);
        content.lineTo(centerX + 90, y);
        content.stroke();
        drawDiamond(content, centerX, y, 5, GOLD);
    }

    private void drawDiamond(PDPageContentStream content, float cx, float cy, float r, float[] color) throws IOException {
        content.setNonStrokingColor(color[0], color[1], color[2]);
        content.moveTo(cx, cy + r);
        content.lineTo(cx + r, cy);
        content.lineTo(cx, cy - r);
        content.lineTo(cx - r, cy);
        content.closePath();
        content.fill();
    }

    private void drawSeal(PDPageContentStream content, float cx, float cy, float r) throws IOException {
        drawCircle(content, cx, cy, r, GOLD, 0, true);
        drawCircle(content, cx, cy, r - 5, GOLD_LIGHT, 0, true);
        drawCircle(content, cx, cy, r - 5, TEAL_DARK, 1.5f, false);
        drawCircle(content, cx, cy, r - 12, TEAL_DARK, 1f, false);

        PDType1Font sealFont = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
        drawCentered(content, sealFont, 11, "RIKKEI", cx, cy + 4, TEAL_DARK);
        drawCentered(content, sealFont, 8, "EDU", cx, cy - 8, TEAL_DARK);

        // little ribbon tails under the seal
        content.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
        content.moveTo(cx - 14, cy - r + 4);
        content.lineTo(cx - 6, cy - r - 18);
        content.lineTo(cx - 2, cy - r + 2);
        content.closePath();
        content.fill();
        content.moveTo(cx + 14, cy - r + 4);
        content.lineTo(cx + 6, cy - r - 18);
        content.lineTo(cx + 2, cy - r + 2);
        content.closePath();
        content.fill();
    }

    private void drawStamp(PDPageContentStream content, PDFont boldFont, PDFont italicFont, float cx, float cy, float r)
            throws IOException {
        drawCircle(content, cx, cy, r, STAMP_RED, 2.4f, false);
        drawCircle(content, cx, cy, r - 5, STAMP_RED, 0.8f, false);
        drawCircle(content, cx, cy, r - 11, STAMP_RED, 1.2f, false);

        drawCentered(content, boldFont, 7.2f, "RIKKEI EDU", cx, cy + 22, STAMP_RED);
        drawCentered(content, boldFont, 10.5f, "VERIFIED", cx, cy + 6, STAMP_RED);
        drawCentered(content, italicFont, 7.2f, "CERTIFICATE", cx, cy - 7, STAMP_RED);
        drawCentered(content, boldFont, 6.6f, "OFFICIAL SEAL", cx, cy - 23, STAMP_RED);

        content.setStrokingColor(STAMP_RED[0], STAMP_RED[1], STAMP_RED[2]);
        content.setLineWidth(0.7f);
        content.moveTo(cx - 29, cy + 15);
        content.lineTo(cx + 29, cy + 15);
        content.stroke();
        content.moveTo(cx - 27, cy - 16);
        content.lineTo(cx + 27, cy - 16);
        content.stroke();

        drawStar(content, cx - 31, cy, 4.2f, STAMP_RED);
        drawStar(content, cx + 31, cy, 4.2f, STAMP_RED);
        drawStar(content, cx, cy - 33, 3.5f, STAMP_RED);
        drawSmallTicks(content, cx, cy, r - 2);
    }

    private void drawSmallTicks(PDPageContentStream content, float cx, float cy, float r) throws IOException {
        content.setStrokingColor(STAMP_RED[0], STAMP_RED[1], STAMP_RED[2]);
        content.setLineWidth(0.45f);
        for (int i = 0; i < 32; i++) {
            double angle = Math.toRadians(i * 360.0 / 32.0);
            float outerX = cx + (float) Math.cos(angle) * r;
            float outerY = cy + (float) Math.sin(angle) * r;
            float innerX = cx + (float) Math.cos(angle) * (r - 3.2f);
            float innerY = cy + (float) Math.sin(angle) * (r - 3.2f);
            content.moveTo(innerX, innerY);
            content.lineTo(outerX, outerY);
            content.stroke();
        }
    }

    private void drawStar(PDPageContentStream content, float cx, float cy, float r, float[] color) throws IOException {
        content.setNonStrokingColor(color[0], color[1], color[2]);
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            float radius = i % 2 == 0 ? r : r * 0.45f;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            if (i == 0) {
                content.moveTo(x, y);
            } else {
                content.lineTo(x, y);
            }
        }
        content.closePath();
        content.fill();
    }

    /** Draws a circle using 4 bezier arcs; stroke and/or fill depending on args. */
    private void drawCircle(PDPageContentStream content, float cx, float cy, float r,
                            float[] color, float lineWidth, boolean fill) throws IOException {
        final float k = 0.5522848f * r;
        content.moveTo(cx + r, cy);
        content.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        content.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        content.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        content.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        content.closePath();
        if (fill) {
            content.setNonStrokingColor(color[0], color[1], color[2]);
            content.fill();
        } else {
            content.setStrokingColor(color[0], color[1], color[2]);
            content.setLineWidth(lineWidth);
            content.stroke();
        }
    }

    // ---------------------------------------------------------------------
    // text helpers
    // ---------------------------------------------------------------------

    private void drawLabelValue(PDPageContentStream content, PDFont labelFont, PDFont valueFont, float size,
                                String label, String value, float x, float y) throws IOException {
        content.setNonStrokingColor(TEAL_DARK[0], TEAL_DARK[1], TEAL_DARK[2]);
        content.beginText();
        content.setFont(labelFont, size);
        content.newLineAtOffset(x, y);
        content.showText(label);
        content.endText();

        float labelWidth = (float) (labelFont.getStringWidth(label) / 1000d * size);
        content.setNonStrokingColor(TEXT_GRAY[0], TEXT_GRAY[1], TEXT_GRAY[2]);
        content.beginText();
        content.setFont(valueFont, size);
        content.newLineAtOffset(x + labelWidth, y);
        content.showText(value == null ? "" : value);
        content.endText();
    }

    private void drawCenteredTracked(PDPageContentStream content, PDFont font, float fontSize, String text,
                                     float centerX, float y, float[] color, float extraTracking) throws IOException {
        // approximate letter-spacing by summing widths + tracking
        float total = 0;
        for (char c : text.toCharArray()) {
            total += (float) (font.getStringWidth(String.valueOf(c)) / 1000d * fontSize) + extraTracking;
        }
        total -= extraTracking; // no trailing gap
        float x = centerX - total / 2;
        content.setNonStrokingColor(color[0], color[1], color[2]);
        for (char c : text.toCharArray()) {
            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(x, y);
            content.showText(String.valueOf(c));
            content.endText();
            x += (float) (font.getStringWidth(String.valueOf(c)) / 1000d * fontSize) + extraTracking;
        }
    }

    private void drawCentered(PDPageContentStream content, PDFont font, float fontSize, String text,
                              float centerX, float y, float[] color) throws IOException {
        float textWidth = (float) (font.getStringWidth(text) / 1000d * fontSize);
        drawText(content, font, fontSize, text, centerX - textWidth / 2, y, color);
    }

    /** Wraps text to maxWidth, centers each line, returns the y position after the last line. */
    private float drawWrappedCentered(PDPageContentStream content, PDFont font, float fontSize, String text,
                                      float centerX, float startY, float maxWidth, float lineHeight,
                                      float[] color) throws IOException {
        List<String> lines = wrap(font, fontSize, text, maxWidth);
        float y = startY;
        for (String line : lines) {
            drawCentered(content, font, fontSize, line, centerX, y, color);
            y -= lineHeight;
        }
        return y;
    }

    private List<String> wrap(PDFont font, float fontSize, String text, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(candidate) / 1000 * fontSize;
            if (width > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void drawText(PDPageContentStream content, PDFont font, float fontSize, String text, float x, float y)
            throws IOException {
        drawText(content, font, fontSize, text, x, y, null);
    }

    private void drawText(PDPageContentStream content, PDFont font, float fontSize, String text, float x, float y,
                          float[] color) throws IOException {
        if (color != null) {
            content.setNonStrokingColor(color[0], color[1], color[2]);
        }
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    // ---------------------------------------------------------------------
    // fonts / text sanitizing
    // ---------------------------------------------------------------------

    /** Loads the embedded script font from the classpath; falls back to Times-Italic if absent. */
    private PDFont loadScriptFont(PDDocument document, PDFont fallback) {
        try (InputStream is = getClass().getResourceAsStream(SCRIPT_FONT_RESOURCE)) {
            if (is == null) {
                return fallback;
            }
            return PDType0Font.load(document, is);
        } catch (IOException e) {
            return fallback;
        }
    }

    private String ascii(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return DIACRITICS.matcher(normalized)
                .replaceAll("")
                .replaceAll("[^\\x20-\\x7E]", "")
                .toUpperCase(Locale.ROOT);
    }
}
