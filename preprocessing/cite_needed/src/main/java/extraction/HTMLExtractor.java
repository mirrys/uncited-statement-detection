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
import java.util.*;

/**
 * Created by besnik on 2/27/18.
 */
public class HTMLExtractor {
    public static final int STATEMENT_LENGTH = 200;

    public static void main(String[] args) throws IOException {
        String[] args1 = {"-entity_seeds", "/Users/besnik/Documents/L3S/unsourced_statements/all_featured_links.csv",
                "-out_dir", "/Users/besnik/Documents/L3S/unsourced_statements/html_data_all_articles/",
                "-wiki_dump", "/Users/besnik/Documents/L3S/unsourced_statements/html_data_all_articles/",
                "-option", "sample", "-lang", "enwiki"};

        args = args1;
        String entity_seeds = "", out_dir = "", option = "", wiki_dump = "", lang = "";

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-entity_seeds")) {
                entity_seeds = args[++i];
            } else if (args[i].equals("-out_dir")) {
                out_dir = args[++i];
            } else if (args[i].equals("-option")) {
                option = args[++i];
            } else if (args[i].equals("-wiki_dump")) {
                wiki_dump = args[++i];
            } else if (args[i].equals("-lang")) {
                lang = args[++i];
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
        } else if (option.equals("sample")) {
            out_dir += "/" + lang + "/";
            sampleStatements(out_dir);
        }

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
        String out_file = out_dir + "/" + lang + "/wiki_subset.txt";
        FileUtils.saveText("", out_file);
        lang = lang.replace("wiki", "");
        for (String entity : entities.keySet()) {
            String entity_label = URLEncoder.encode(entity);
            //https://en.wikipedia.org/api/rest_v1/page/html/
            String url = "https://" + lang + ".wikipedia.org/api/rest_v1/page/html/" + entity_label;
            String url_body = WebUtils.getURLContent(url).replaceAll("<!--(.*?)-->", "");

            String out_json = entity + "\t" + url_body.replace("\n", "\\n") + "\n";
            FileUtils.saveText(out_json, out_file, true);

            System.out.printf("Finished extracting HTML content for entity %s.\n", entity);
        }
    }

    /**
     * Store the sampled lines.
     *
     * @param lines
     * @param file
     * @param out_file
     * @throws IOException
     */
    public static void saveSampledStatements(Set<Integer> lines, String file, String out_file) throws IOException {
        int line_number = 0;
        String line;
        BufferedReader reader = FileUtils.getFileReader(file);

        StringBuffer sb = new StringBuffer();
        while ((line = reader.readLine()) != null) {
            if (lines.contains(line_number)) {
                sb.append(line).append("\n");
            }
            line_number++;
        }

        FileUtils.saveText(sb.toString(), out_file);
    }


    public static Set<Integer> sampleLines(String file, int num_samples) throws IOException {
        Random rand = new Random(100);
        //count num lines
        int num_lines = 0;

        Map<Boolean, Set<Integer>> lines = new HashMap<>();
        lines.put(false, new HashSet<>());
        lines.put(true, new HashSet<>());
        BufferedReader cl_reader = FileUtils.getFileReader(file);
        String line;
        while ((line = cl_reader.readLine()) != null) {
            if (line.length() < 100) {
                num_lines++;
                continue;
            }
            boolean is_unsourced = line.endsWith("N/A");
            lines.get(is_unsourced).add(num_lines);
            num_lines++;
        }
        cl_reader.close();


        List<Integer> unsourced = new ArrayList<>(lines.get(true));
        List<Integer> sourced = new ArrayList<>(lines.get(false));
        System.out.printf("There are %d sourced and %d unsourced statements.\n", sourced.size(), unsourced.size());

        Collections.shuffle(unsourced, rand);
        Collections.shuffle(sourced, rand);

        Set<Integer> sampled_indices = new HashSet<>();
        sampled_indices.addAll(unsourced.subList(0, num_samples));
        sampled_indices.addAll(sourced.subList(0, num_samples));
        return sampled_indices;
    }

    /**
     * Sample statements.
     *
     * @param out_dir
     * @throws IOException
     */
    public static void sampleStatements(String out_dir) throws IOException {
        int num_samples = 1000;
        Set<Integer> lines = sampleLines(out_dir + "/statements.txt", num_samples);

        //store the sampled lines.
        saveSampledStatements(lines, out_dir + "/statements.txt", out_dir + "/sampled_statements.txt");
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
        String out_file = out_dir + "/statements.txt";

        HTMLWikiParser wp = new HTMLWikiParser();
        String header = "entity_id\trevision_id\ttimestamp\tentity_title\tsection_id\tsection\tprg_idx\tsentence_idx\tstatement\tcitations\n";
        FileUtils.saveText(header, out_file);

        //parse the individual entities.
        while ((line = reader.readLine()) != null) {
            String[] data = line.split("\t");
            String entity = data[0].replaceAll(" ", "_");
            String entity_body_html = data[1].replace("\\n", "\n");

            Document entity_doc = Jsoup.parse(entity_body_html);
            Map<String, Map<String, String>> entity_citations = wp.extractCitationFromWikiPage(entity_doc);
            System.out.printf("Entity %s has %d citations.\n", entity, entity_citations.size());

            extractStatementCitations(entity_doc, entity_citations, out_file, wp, lang);
        }
    }

    private static void extractStatementCitations(Document doc, Map<String, Map<String, String>> citations,
                                                  String out_file, HTMLWikiParser wp, String lang) {
        String page_id = "", revision_id = "", timestamp = "";
        Elements meta_data = doc.select("meta");
        for (Element md : meta_data) {
            if (md.attr("property").equals("mw:pageId")) {
                page_id = md.attr("content");
            }

            if (md.attr("property").equals("mw:TimeUuid")) {
                timestamp = md.attr("content");
            }
        }
        revision_id = doc.select("html").first().attr("about").replace("http://" + lang + ".wikipedia.org/wiki/Special:Redirect/revision/", "");
        Elements sections = doc.select("section");
        String title = doc.title().replaceAll(" ", "_");

        for (Element section : sections) {
            String section_id = section.attr("data-mw-section-id");
            String section_name = wp.getSectionName(section, section_id);
            if (section_name.isEmpty()) {
                continue;
            }

            StringBuffer sb = new StringBuffer();
            Elements paragraphs = section.select("p");

            for (int prg_id = 0; prg_id < paragraphs.size(); prg_id++) {
                Element prg = paragraphs.get(prg_id);
                String prg_text = prg.toString();
                if (prg.id().isEmpty()) {
                    continue;
                }

                //the paragraph contains citations
                if (!prg.select("sup").isEmpty()) {
                    //extract sentences and the corresponding citations.
                    extractSentenceCitations(prg, citations, sb, page_id, revision_id, timestamp, section_id, section_name, title, prg_id);
                } else if (prg_text.length() > STATEMENT_LENGTH) {
                    prg_text = "<div class=\"unsourced-statement\">" + prg_text.replace("\n", "\\n") + "</div>";
                    prg_text = StringEscapeUtils.unescapeHtml4(prg_text.replaceAll("\n", "\\n"));
                    sb.append(page_id).append("\t").append(revision_id).append("\t").append(timestamp).append("\t").append(title).append("\t").
                            append(section_id).append("\t").append(section_name).append("\t").append(prg_id).append("\t").append(-1).append("\t").
                            append(prg_text).append("\t").append("N/A").append("\n");
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
    private static void extractSentenceCitations(Element paragraph, Map<String, Map<String, String>> citations,
                                                 StringBuffer sb, String page_id, String revision_id, String timestamp,
                                                 String section_id, String section_name, String title, int prg_idx) {
        String prg_raw_escaped_text = paragraph.toString();
        //split text into sentences.
        String[] sentences = prg_raw_escaped_text.split("\\.\\s+");

        for (int i = 0; i < sentences.length; i++) {
            String sentence = StringEscapeUtils.unescapeHtml4(sentences[i]);
            //the sentence does not contain any citation
            if (!sentence.contains("<sup about=") || sentence.length() < STATEMENT_LENGTH) {
                continue;
            }

            Document sentence_doc = Jsoup.parse(sentences[i]);
            Elements scs = sentence_doc.getElementsByClass("mw-ref");

            sb.append(page_id).append("\t").append(revision_id).append("\t").append(timestamp).append("\t").append(title).append("\t").
                    append(section_id).append("\t").append(section_name).append("\t").append(prg_idx).append("\t").append(i).append("\t").
                    append("<div class=\"sourced-statement\">").append(sentence.replaceAll("\n", "\\n")).append("</div>").append("\t");

            for (Element sc : scs) {
                String citation_text = getCitationAttributes(sc, citations, title);
                sb.append(citation_text);
            }
            sb.append("\n");
        }
    }


    public static String getCitationAttributes(Element in_cite, Map<String, Map<String, String>> citations, String title) {
        StringBuffer sb = new StringBuffer();
        JSONObject data_mw = new JSONObject(in_cite.attributes().get("data-mw").toString().replace("\n", "\\n"));
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

}
