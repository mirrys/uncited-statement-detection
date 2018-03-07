package extraction;

import io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import parser.HTMLWikiParser;
import utils.WebUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by besnik on 2/27/18.
 */
public class HTMLExtractor {
    public static boolean remove_citations;

    public static void main(String[] args) throws IOException {
        String[] args1 = {"-entity_seeds", "/Users/besnik/Documents/L3S/unsourced_statements/featured_links.csv",
                "-out_dir", "/Users/besnik/Documents/L3S/unsourced_statements/html_data/",
                "-wiki_dump", "/Users/besnik/Documents/L3S/unsourced_statements/html_data/",
                "-lang", "frwiki", "-option", "statements", "-clean_citations", "true"};
        args = args1;
        String entity_seeds = "", out_dir = "", lang = "", option = "", wiki_dump = "";

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-entity_seeds")) {
                entity_seeds = args[++i];
            } else if (args[i].equals("-out_dir")) {
                out_dir = args[++i];
            } else if (args[i].equals("-lang")) {
                lang = args[++i];
            } else if (args[i].equals("-option")) {
                option = args[++i];
            } else if (args[i].equals("-wiki_dump")) {
                wiki_dump = args[++i];
            } else if (args[i].equals("-clean_citations")) {
                remove_citations = args[++i].equals("true");
            }
        }


        //load the list of entities for which we want to perform the analysis.
        Map<String, Integer> entities = new HashMap<>();
        String[] entity_seed_data = FileUtils.readText(entity_seeds).split("\n");
        for (String line : entity_seed_data) {
            String[] tmp = line.split("\t");
            if (!tmp[1].equals(lang)) {
                continue;
            }
            entities.put(tmp[2].trim(), Integer.parseInt(tmp[0]));
        }

        if (option.equals("extract")) {
            extractHTMLWikiPageContent(entities, out_dir, lang);
        } else if (option.equals("statements")) {
            wiki_dump += "/" + lang + "/wiki_subset.txt";
            out_dir += "/" + lang;
            extractCitations(wiki_dump, out_dir, lang);
        }
    }

    /**
     * Extract the citations and sentences with citations from Wikipedia pages. We output for each sentence
     * the section in which it appears, the start position (in the original HTML file), the offset (length),
     * and all the citation information.
     *
     * @param wiki_file
     * @param out_dir
     * @throws IOException
     */
    public static void extractCitations(String wiki_file, String out_dir, String lang) throws IOException {
        lang = lang.replace("wiki", "");
        BufferedReader reader = FileUtils.getFileReader(wiki_file);
        String line;
        String out_file = !remove_citations ? out_dir + "/statements.txt" : out_dir + "/clean_statements.txt";

        HTMLWikiParser wp = new HTMLWikiParser();
        String header = "entity_id\trevision_id\tentity_title\tsection\tstart\toffset\tstatement\tparagraph\tcitations\n";
        FileUtils.saveText(header, out_file);

        //parse the individual entities.
        while ((line = reader.readLine()) != null) {
            String[] data = line.split("\t");
            String entity = data[0];
            String entity_body_html = data[1].replaceAll("\\n", "\n");

            Document entity_doc = Jsoup.parse(entity_body_html);
            Map<String, Map<String, String>> entity_citations = wp.extractCitationFromWikiPage(entity_doc);
            System.out.printf("Entity %s has %d citations.\n", entity, entity_citations.size());

            extractStatementCitations(entity_body_html, entity_doc, entity_citations, out_file, wp, lang);
        }
    }

    private static void extractStatementCitations(String entity_text, Document doc, Map<String, Map<String, String>> citations, String out_file, HTMLWikiParser wp, String lang) {
        String page_id = "", revision_id = "";
        Elements meta_data = doc.head().select("meta");
        for (Element md : meta_data) {
            if (md.attr("property").equals("mw:pageId")) {
                page_id = md.attr("content");
            }
        }
        revision_id = doc.select("html").first().attr("about").replace("http://" + lang + ".wikipedia.org/wiki/Special:Redirect/revision/", "");
        Elements sections = doc.select("section");

        for (Element section : sections) {
            String section_id = section.attr("data-mw-section-id");
            String section_name = wp.getSectionName(section, section_id);

            StringBuffer sb = new StringBuffer();
            Elements paragraphs = section.select("p");

            for (Element prg : paragraphs) {
                String prg_text = prg.toString();
                String paragraph_id = "<p id=\"" + prg.id() + "\"";
                int paragraph_start = entity_text.indexOf(paragraph_id);
                if (prg.id().isEmpty() || paragraph_start == -1) {
                    continue;
                }

                //the paragraph contains citations
                if (!prg.select("sup").isEmpty()) {
                    //extract sentences and the corresponding citations.
                    extractSentenceCitations(prg, citations, sb, page_id, revision_id, section_name, doc.title(), paragraph_start);
                } else {
                    int start = paragraph_start;
                    int offset = start + prg_text.length();
                    prg_text = "<div class=\"unsourced-statement\">" + prg_text.replaceAll("\n", "\\n") + "</div>";
                    sb.append(page_id).append("\t").append(revision_id).append("\t").append(doc.title()).append("\t").
                            append(section_name).append("\t").append(start).append("\t").append(offset).append("\t").
                            append(prg_text).append("\t").append("N/A").append("\t").append("N/A").append("\n");
                }
            }

            FileUtils.saveText(sb.toString(), out_file, true);
        }
    }

    /**
     * Extract the citations for all entities.
     *
     * @param paragraph
     * @param citations
     * @param sb
     * @param page_id
     * @param revision_id
     * @param section_name
     * @param title
     */
    private static void extractSentenceCitations(Element paragraph, Map<String, Map<String, String>> citations, StringBuffer sb,
                                                 String page_id, String revision_id, String section_name, String title, int paragraph_start) {
        String prg_text = StringEscapeUtils.unescapeHtml4(paragraph.toString());
        String prg_raw_text = paragraph.toString();
        //split text into sentences.
        String[] sentences = prg_raw_text.split("\\.\\s+");

        String title_tmp = title.replaceAll(" ", "_");
        for (String sentence : sentences) {
            //the sentence does not contain any citation
            if (!sentence.contains("<sup about=")) {
                continue;
            }

            Document sentence_doc = Jsoup.parse(sentence);
            Elements scs = sentence_doc.getElementsByClass("mw-ref");

            //in case we want to remove the citations from the sentence
            sentence = StringEscapeUtils.unescapeHtml4(sentence);

            int start = paragraph_start + prg_text.indexOf(sentence);
            int offset = start + sentence.length();


            //this is only for display
            sentence = remove_citations ? sentence.replaceAll("<sup (.*?)>(.*?)</sup>", "") : sentence;

            sb.append(page_id).append("\t").append(revision_id).append("\t").append(title).append("\t").
                    append(section_name).append("\t").append(start).append("\t").append(offset).append("\t").
                    append(sentence).append("\t").append(prg_text.replaceAll("\n", "\\n")).append("\t");

            for (Element sc : scs) {
                sb.append(getCitationAttributes(sc, citations, title_tmp));
            }
            sb.append("\n");
        }
    }


    public static String getCitationAttributes(Element in_cite, Map<String, Map<String, String>> citations, String title) {
        StringBuffer sb = new StringBuffer();
        JSONObject data_mw = new JSONObject(in_cite.attributes().get("data-mw"));
        if (data_mw.has("body") && data_mw.getJSONObject("body").has("id")) {
            String note_ref_cite_id = data_mw.getJSONObject("body").getString("id").replace("mw-reference-text-", "");

            if (citations.containsKey(note_ref_cite_id)) {
                Map<String, String> cite_attr = citations.get(note_ref_cite_id);
                sb.append(cite_attr.toString()).append("\t");
            }
        } else {
            Elements a_ins = in_cite.select("a");
            for (Element a_in : a_ins) {
                String a_href = a_in.attributes().get("href");
                if (a_href.contains(title)) {
                    a_href = a_href.replace("./" + title + "#", "");
                    sb.append(citations.get(a_href)).append("\t");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Extract the HTML content of Wikipedia pages. We call the REST API by Wikimedia which returns the HTML content
     * of an article.
     *
     * @param entities
     * @param out_dir
     * @param lang
     * @throws IOException
     */
    public static void extractHTMLWikiPageContent(Map<String, Integer> entities, String out_dir, String lang) throws IOException {
        FileUtils.checkDir(out_dir + "/" + lang + "/");
        String out_file = out_dir + "/" + lang + "/wiki_subset.json";
        lang = lang.replace("wiki", "");
        for (String entity : entities.keySet()) {
            String entity_label = URLEncoder.encode(entity);
            //https://en.wikipedia.org/api/rest_v1/page/html/
            String url = "https://" + lang + ".wikipedia.org/api/rest_v1/page/html/" + entity_label;
            String url_body = WebUtils.getURLContent(url);

            String out_json = entity + "\t" + StringEscapeUtils.escapeJson(url_body) + "\n";
            FileUtils.saveText(out_json, out_file, true);

            System.out.printf("Finished extracting HTML content for entity %s.\n", entity);
        }
    }
}
