package project.lms_rikkei_edu.modules.course.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

import java.util.regex.Pattern;

/**
 * Mô tả khóa học được soạn bằng rich-text editor (contentEditable) ở frontend nên lưu dưới dạng
 * HTML thô — phải sanitize trước khi lưu DB để chặn stored XSS (vd instructor dán script độc hại).
 * Whitelist chỉ gồm các thẻ định dạng cơ bản mà toolbar hỗ trợ: đậm/nghiêng/gạch chân, cỡ chữ
 * (qua style="font-size"), danh sách. Không cho phép thẻ script/link/iframe hay bất kỳ event handler nào.
 */
public final class CourseDescriptionSanitizer {

    // Chỉ chấp nhận style="font-size: <số>px" — mọi CSS property khác (background-url, expression()...)
    // đều bị loại bỏ để tránh chèn nội dung/hành vi ngoài ý muốn qua thuộc tính style.
    private static final Pattern FONT_SIZE = Pattern.compile("^font-size:\\s*\\d{1,3}(\\.\\d+)?px;?$");

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("u")
            .addAttributes("span", "style")
            .removeTags("h1", "h2", "img", "table", "thead", "tbody", "tr", "td", "th", "blockquote", "a");

    private CourseDescriptionSanitizer() {}

    public static String sanitize(String rawHtml) {
        if (rawHtml == null) return null;
        Document dirty = Jsoup.parseBodyFragment(rawHtml);
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String clean = Jsoup.clean(dirty.body().html(), "", SAFELIST, outputSettings);

        Document doc = Jsoup.parseBodyFragment(clean);
        Elements withStyle = doc.body().select("[style]");
        for (Element el : withStyle) {
            String style = el.attr("style").trim();
            if (!FONT_SIZE.matcher(style).matches()) el.removeAttr("style");
        }
        String result = doc.body().html();
        return result.isBlank() ? null : result;
    }
}
