import io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import parser.HTMLWikiParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by besnik on 2/27/18.
 */

public class Test {
    public static HTMLWikiParser wp = new HTMLWikiParser();

    public static void main(String[] args) throws IOException {
        Map<String, String> entity_text = new HashMap<>();
        BufferedReader reader = FileUtils.getFileReader("/Users/besnik/Documents/L3S/unsourced_statements/html_data/enwiki/wiki_subset.txt");
        String line;
        while ((line = reader.readLine()) != null) {
            String entity = line.substring(0, line.indexOf("\t")).trim();
            String text = line.substring(line.indexOf("\t")).trim().replace("\\n", "\n");
            entity_text.put(entity, text);
        }

        Map<String, Document> entity_doms = new HashMap<>();
        entity_text.keySet().forEach(e -> entity_doms.put(e, Jsoup.parse(entity_text.get(e))));

        reader = FileUtils.getFileReader("/Users/besnik/Documents/L3S/unsourced_statements/html_data/enwiki/prg_indexed_statements.txt");
        int line_counter = 0;
        FileUtils.saveText("", "/Users/besnik/Desktop/log.txt");

        StringBuffer sb = new StringBuffer();
        while ((line = reader.readLine()) != null) {
            try {
                String[] tmp = line.split("\t");
                if (line_counter == 0) {
                    line_counter++;
                    continue;
                }
                String entity = tmp[3];
                String section_id = tmp[4];
                String section = tmp[5];

                Document doc = entity_doms.get(entity);
                if (doc == null) {
                    continue;
                }

                int prg_idx = Integer.parseInt(tmp[6]);
                int sentence_idx = Integer.parseInt(tmp[7]);

                String statement = StringEscapeUtils.unescapeHtml4(tmp[8]);
                String statement_cmp = extractCitations(doc, section_id, section, prg_idx, sentence_idx);

                sb.append(statement).append("\t\t").append(statement_cmp).append("\n");

                if (sb.length() > 10000) {
                    FileUtils.saveText(sb.toString(), "/Users/besnik/Desktop/log.txt", true);
                    sb.delete(0, sb.length());
                }
            } catch (Exception e) {
                System.out.println(line);
                e.printStackTrace();
            }
        }
        FileUtils.saveText(sb.toString(), "/Users/besnik/Desktop/log.txt", true);
    }


    public static String extractCitations(Document doc, String section_id, String section_label, int prg_idx, int sentence_idx) {
        Elements sections = doc.select("section");
        for (Element section : sections) {
            String section_id_cmp = section.attr("data-mw-section-id");
            String section_label_cmp = wp.getSectionName(section, section_id);

            if (section_label.equals(section_label_cmp) && section_id.equals(section_id_cmp)) {
                Elements paragraphs = section.select("p");
                if (sentence_idx == -1) {
                    return paragraphs.get(prg_idx).toString();
                }
                String[] prg_text = paragraphs.get(prg_idx).toString().split("\\.\\s+");
                return prg_text[sentence_idx];
            }
        }
        return null;
    }

}
