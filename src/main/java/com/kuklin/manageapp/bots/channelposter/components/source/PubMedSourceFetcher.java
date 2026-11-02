package com.kuklin.manageapp.bots.channelposter.components.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Свежие исследования из PubMed через NCBI E-utilities (без ключа — до 3 запросов в секунду).
 * address источника — поисковый запрос PubMed, например
 * «(weight loss[tiab]) AND randomized controlled trial[pt]».
 */
@Component
@RequiredArgsConstructor
public class PubMedSourceFetcher implements SourceFetcher {

    private static final String EUTILS = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/";
    private static final String USER_AGENT = "manageapp-channelposter/1.0";
    // окно поиска шире интервала сбора, чтобы ничего не пропустить; дубли отсекаются по PMID
    private static final int SEARCH_DAYS = 10;
    private static final int ABSTRACT_SUMMARY_CHARS = 600;

    private final SourceHttpClient http;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<FetchedItem> fetch(ContentSource source) throws Exception {
        int max = source.getMaxItems() == null ? 10 : source.getMaxItems();
        String searchUrl = EUTILS + "esearch.fcgi?db=pubmed&retmode=json&sort=relevance&datetype=edat"
                + "&reldate=" + SEARCH_DAYS
                + "&retmax=" + max
                + "&term=" + URLEncoder.encode(source.getAddress(), StandardCharsets.UTF_8);
        List<String> ids = parseSearchIds(http.get(searchUrl, USER_AGENT, Map.of()));
        if (ids.isEmpty()) {
            return List.of();
        }
        Thread.sleep(400); // лимит NCBI без ключа
        String fetchUrl = EUTILS + "efetch.fcgi?db=pubmed&retmode=xml&id=" + String.join(",", ids);
        return parseArticles(http.get(fetchUrl, USER_AGENT, Map.of()));
    }

    @Override
    public ContentSource.SourceType supportedType() {
        return ContentSource.SourceType.PUBMED;
    }

    List<String> parseSearchIds(String json) throws Exception {
        JsonNode idList = objectMapper.readTree(json).path("esearchresult").path("idlist");
        List<String> ids = new ArrayList<>();
        idList.forEach(n -> ids.add(n.asText()));
        return ids;
    }

    /**
     * efetch XML → материалы. Статьи без абстракта пропускаем: пересказывать нечего.
     */
    public static List<FetchedItem> parseArticles(String xml) {
        List<FetchedItem> result = new ArrayList<>();
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());

        for (Element article : doc.select("PubmedArticle")) {
            String pmid = text(article.selectFirst("MedlineCitation > PMID"));
            String title = text(article.selectFirst("ArticleTitle"));
            if (TextUtils.isBlank(pmid) || TextUtils.isBlank(title)) continue;

            String abstractText = article.select("Abstract > AbstractText").stream()
                    .map(a -> {
                        String label = a.attr("Label");
                        String body = a.text().trim();
                        return label.isBlank() ? body : label + ": " + body;
                    })
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining("\n"));
            if (abstractText.isBlank()) continue;

            String journal = text(article.selectFirst("Journal > Title"));
            String types = article.select("PublicationTypeList > PublicationType").stream()
                    .map(Element::text)
                    .filter(t -> !t.equals("Journal Article"))
                    .collect(Collectors.joining(", "));

            StringBuilder content = new StringBuilder();
            content.append("Исследование из PubMed\n");
            content.append("Название: ").append(title).append('\n');
            if (!TextUtils.isBlank(journal)) content.append("Журнал: ").append(journal).append('\n');
            if (!types.isBlank()) content.append("Тип публикации: ").append(types).append('\n');
            content.append("\nАбстракт:\n").append(abstractText);

            String summary = (types.isBlank() ? "" : "[" + types + "] ")
                    + TextUtils.truncate(abstractText, ABSTRACT_SUMMARY_CHARS);

            result.add(new FetchedItem()
                    .setExternalId("pubmed:" + pmid)
                    .setUrl("https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/")
                    .setTitle(title)
                    .setSummary(summary)
                    .setContent(content.toString())
                    .setPublishedAt(entryDate(article)));
        }
        return result;
    }

    // дата появления в PubMed (History/PubMedPubDate[PubStatus=pubmed])
    private static java.time.Instant entryDate(Element article) {
        for (Element d : article.select("History > PubMedPubDate")) {
            String status = d.attr("PubStatus");
            if (!status.equals("pubmed") && !status.equals("entrez")) continue;
            try {
                return LocalDate.of(
                        Integer.parseInt(text(d.selectFirst("Year"))),
                        Integer.parseInt(text(d.selectFirst("Month"))),
                        Integer.parseInt(text(d.selectFirst("Day")))
                ).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String text(Element el) {
        return el == null ? null : el.text().trim();
    }
}
