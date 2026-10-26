package com.kuklin.manageapp.bots.caloriebot.utils;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * XHTML → PDF (openhtmltopdf + Batik для SVG) и PDF → PNG (PDFBox). Общий для PDF-отчётов (ReportService)
 * и картинок (DayCardService): одни шрифты, один протокол classpath: для @font-face. Ограничения рендерера —
 * таблицы вместо flex, без calc() и box-shadow, монохромные эмодзи.
 */
@Slf4j
public final class HtmlPdfRenderer {

    public static final String CLASSPATH_PROTOCOL = "classpath";
    /** 1 CSS-пиксель = 1/96 дюйма: при 96 dpi страница шириной 1080px даёт картинку 1080 точек. */
    public static final float CSS_PIXEL_DPI = 96f;

    /**
     * Шрифты. Nunito — как в мини-приложении (для SVG-графиков он же подключается в шаблоне через @font-face).
     * Noto Emoji (монохромные эмодзи: цветные рендерер не умеет) и DejaVu Sans (стрелки и символы,
     * которых нет в Nunito) — запасные: подставляются для недостающих символов.
     */
    private static final List<RenderFont> FONTS = List.of(
            new RenderFont("/fonts/Nunito-Regular.ttf", "Nunito", 400, false),
            new RenderFont("/fonts/Nunito-Bold.ttf", "Nunito", 700, false),
            new RenderFont("/fonts/Nunito-ExtraBold.ttf", "Nunito", 800, false),
            new RenderFont("/fonts/Nunito-Black.ttf", "Nunito", 900, false),
            new RenderFont("/fonts/NotoEmoji-Regular.ttf", "Noto Emoji", 400, true),
            new RenderFont("/fonts/DejaVuSans.ttf", "DejaVu Sans", 400, true)
    );

    private HtmlPdfRenderer() {
    }

    /**
     * XHTML → PDF. baseUrl — откуда брать относительные картинки.
     *
     * @return PDF или null — ошибка рендера (в лог)
     */
    public static byte[] renderPdfOrNull(String html, String baseUrl) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useSVGDrawer(new BatikSVGDrawer());
            builder.withHtmlContent(html, baseUrl);
            // classpath:/fonts/… в @font-face шаблона — шрифты для текста SVG (их Batik берёт только оттуда)
            builder.useProtocolsStreamImplementation(ClasspathStream::new, CLASSPATH_PROTOCOL);
            for (RenderFont font : FONTS) {
                Set<BaseRendererBuilder.FSFontUseCase> useCases = font.fallback()
                        ? EnumSet.of(BaseRendererBuilder.FSFontUseCase.DOCUMENT, BaseRendererBuilder.FSFontUseCase.FALLBACK_FINAL)
                        : EnumSet.of(BaseRendererBuilder.FSFontUseCase.DOCUMENT);
                builder.useFont(() -> HtmlPdfRenderer.class.getResourceAsStream(font.path()), font.family(), font.weight(),
                        BaseRendererBuilder.FontStyle.NORMAL, true, useCases);
            }
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("PDF rendering failed", e);
            return null;
        }
    }

    /**
     * XHTML → PNG первой страницы. Размер картинки задаёт @page size шаблона в px (при CSS_PIXEL_DPI).
     *
     * @return PNG или null — ошибка рендера (в лог)
     */
    public static byte[] renderPngOrNull(String html, String baseUrl) {
        byte[] pdf = renderPdfOrNull(html, baseUrl);
        if (pdf == null) {
            return null;
        }
        try (PDDocument doc = PDDocument.load(pdf); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            BufferedImage image = new PDFRenderer(doc).renderImageWithDPI(0, CSS_PIXEL_DPI);
            ImageIO.write(image, "png", os);
            return os.toByteArray();
        } catch (Exception e) {
            log.error("PNG rendering failed", e);
            return null;
        }
    }

    /** Ресурс приложения по адресу classpath:/путь из HTML-шаблона. */
    private record ClasspathStream(String url) implements FSStream {
        @Override
        public InputStream getStream() {
            return HtmlPdfRenderer.class.getResourceAsStream(url.substring(CLASSPATH_PROTOCOL.length() + 1));
        }

        @Override
        public Reader getReader() {
            InputStream stream = getStream();
            return stream != null ? new InputStreamReader(stream, StandardCharsets.UTF_8) : null;
        }
    }

    /** Шрифт; fallback — подставляется только для символов, которых нет в основном шрифте. */
    private record RenderFont(String path, String family, int weight, boolean fallback) {
    }
}
