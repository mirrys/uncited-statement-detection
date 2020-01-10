import extraction.HTMLExtractor;
import io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import parser.HTMLWikiParser;
import utils.WebUtils;

import java.io.IOException;
import java.util.Map;

/**
 * Created by besnik on 2/27/18.
 */

public class Test {
    public static void main(String[] args) throws IOException {
        String url = "https://en.wikipedia.org/api/rest_v1/page/html/Hate_crime";
        String content = WebUtils.getURLContent(url).replaceAll("<!--(.*?)-->", "");

        HTMLExtractor ex = new HTMLExtractor();
        HTMLWikiParser wp = new HTMLWikiParser();
        Document entity_doc = Jsoup.parse(content);
        Map<String, Map<String, String>> entity_citations = wp.extractCitationFromWikiPage(entity_doc);
        System.out.printf("Entity %s has %d citations.\n", "Test", entity_citations.size());

        ex.extractStatementCitations(entity_doc, entity_citations, "/Users/besnik/Desktop/wiki_out.txt", wp, "en");
    }


}
