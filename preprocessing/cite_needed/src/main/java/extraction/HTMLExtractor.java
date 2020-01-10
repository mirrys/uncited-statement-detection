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
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by besnik on 2/27/18.
 */
public class HTMLExtractor {
    public static final int STATEMENT_LENGTH = 200;
    public static Pattern cite_matcher = Pattern.compile("\\{+(.*?)\\}+");

    public static void main(String[] args) throws IOException {
        String entity_seeds = "", out_dir = "", option = "", wiki_dump = "", lang = "", article_dir = "";

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
            } else if (args[i].equals("-articles")) {
                article_dir = args[++i];
            }
        }

        if (option.equals("extract")) {
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
            extractHTMLWikiPageContent(entities, out_dir, lang);
        } else if (option.equals("extract_simple")) {
            Set<String> entities = FileUtils.readIntoSet(entity_seeds, "\n", false);
            extractSimpleArticleList(entities, out_dir, lang);
        } else if (option.equals("statements_simple")) {
            extractCitations(wiki_dump, out_dir, lang);
        } else if (option.equals("statements_simple_dir")) {
            extractCitationsDir(wiki_dump, out_dir, lang);
        } else if (option.equals("extract_articles_by_topic")) {
            extractArticlesByTopic(entity_seeds, out_dir);
        } else if (option.equals("statements")) {
            wiki_dump += "/" + lang + "/wiki_subset.txt";
            out_dir += "/" + lang;
            extractCitations(wiki_dump, out_dir, lang);
        } else if (option.equals("sample")) {
            out_dir += "/" + lang + "/";
            sampleStatements(out_dir);
        } else if (option.equals("groups")) {
            groupArticleStatements(out_dir, article_dir, out_dir + "/all_articles_groups/");
        } else if (option.equals("group_sample")) {
            sampleGroupedArticleStatements(out_dir + "/statement_groups/", out_dir + "/sampled_statement_groups/");
        }
    }

    /**
     * Extract article content from a list of articles.
     *
     * @param entities
     * @param outfile
     */
    public static void extractSimpleArticleList(Set<String> entities, String outfile, String lang) {
        for (String entity : entities) {
            crawlHTMLWikiPageContent(entity, outfile, lang);
        }
    }


    /**
     * Extract the HTML content of articles based on a set of pre-grouped Wikipedia articles.
     *
     * @param topic_dir
     * @param out_dir
     * @throws IOException
     */
    public static void extractArticlesByTopic(String topic_dir, String out_dir) throws IOException {
        Set<String> files = new HashSet<>();
        FileUtils.getFilesList(topic_dir, files);

        for (String file : files) {
            String file_name = (new File(file)).getName();
            String lang;
            if (file_name.contains("english")) {
                lang = "enwiki";
            } else if (file_name.contains("italian")) {
                lang = "itwiki";
            } else {
                lang = "frwiki";
            }

            String[] lines = FileUtils.readText(file).split("\n");
            Map<String, Integer> entities = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                String[] data = lines[i].split("\t");
                if (data.length != 2) {
                    continue;
                }
                entities.put(data[0], Integer.parseInt(data[1]));
            }

            String out_dir_topic = out_dir + "/" + file_name + "/";
            FileUtils.checkDir(out_dir_topic);
            extractHTMLWikiPageContent(entities, out_dir_topic, lang);

            System.out.println("Finished extracting data for " + file);
        }
    }

    /**
     * Sample the statements that are grouped based on specific entity seed sets.
     *
     * @param statements_dir
     * @param out_dir
     * @throws IOException
     */
    public static void sampleGroupedArticleStatements(String statements_dir, String out_dir) throws IOException {
        Set<String> files = new HashSet<>();
        FileUtils.getFilesList(statements_dir, files);

        FileUtils.checkDir(out_dir);

        int num_samples = 1000;
        for (String file : files) {
            Set<Integer> lines = sampleLines(file, num_samples);
            String file_name = (new File(file)).getName();
            //store the sampled lines.
            saveSampledStatements(lines, file, out_dir + "/sampled_" + file_name);
        }
    }

    /**
     * Extract statements from different sets of entity sets.
     *
     * @param statement_dir
     * @param article_dir
     * @param out_dir
     * @throws IOException
     */
    public static void groupArticleStatements(String statement_dir, String article_dir, String out_dir) throws IOException {
        FileUtils.checkDir(out_dir);
        Set<String> files = new HashSet<>();
        FileUtils.getFilesList(article_dir, files);

        for (String file : files) {
            String file_name = (new File(file)).getName();
            String lang;
            if (file_name.contains("english")) {
                lang = "enwiki";
            } else if (file_name.contains("italian")) {
                lang = "itwiki";
            } else {
                lang = "frwiki";
            }

            if (!lang.equals("enwiki")) {
                continue;
            }
            Map<String, String> entities = FileUtils.readIntoStringMap(file, "\t", false);
//            String statement_file = statement_dir + "/" + lang + "/statements.txt";
            String statement_file = statement_dir + "/enwiki-20180101_clean_statements.tsv.gz";
            String out_file = out_dir + "/statements_" + file_name;
            BufferedReader reader = FileUtils.getFileReader(statement_file);
            String line;
            StringBuffer sb = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                String[] data = line.split("\t");
                String entity = data[0].replaceAll(" ", "_");

                if (!entities.containsKey(entity)) {
                    continue;
                }
                sb.append(line).append("\n");

                if (sb.length() > 10000) {
                    FileUtils.saveText(sb.toString(), out_file, true);
                    sb.delete(0, sb.length());
                }
            }
            FileUtils.saveText(sb.toString(), out_file, true);
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
            crawlHTMLWikiPageContent(entity, out_file, lang);
        }
    }

    /**
     * Crawl the HTML content from an article from the Wikimedia API.
     *
     * @param entity
     * @param out_file
     * @param lang
     */
    public static void crawlHTMLWikiPageContent(String entity, String out_file, String lang) {
        lang = lang.replace("wiki", "");
        try {
            String entity_label = URLEncoder.encode(entity);
            //https://en.wikipedia.org/api/rest_v1/page/html/
            String url = "https://" + lang + ".wikipedia.org/api/rest_v1/page/html/" + entity_label;
            String url_body = WebUtils.getURLContent(url).replaceAll("<!--(.*?)-->", "");

            String out_json = entity + "\t" + url_body.replace("\n", "\\n") + "\n";
            FileUtils.saveText(out_json, out_file, true);

            System.out.printf("Finished extracting HTML content for entity %s.\n", entity);
        } catch (Exception e) {
            System.out.printf("Error processing entity %s with message %s\n", e.getMessage(), entity);
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

    /**
     * For a given file, return a random sample of lines for a pre-determined sample size.
     *
     * @param file
     * @param num_samples
     * @return
     * @throws IOException
     */
    public static Set<Integer> sampleLines(String file, int num_samples) throws IOException {
        Random rand = new Random(100);
        //count num lines
        int num_lines = 0;

        Map<Boolean, Set<Integer>> lines = new HashMap<>();
        lines.put(false, new HashSet<>());
        lines.put(true, new HashSet<>());
        BufferedReader cl_reader = io.FileUtils.getFileReader(file);
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
        sampled_indices.addAll(unsourced.subList(0, num_samples > unsourced.size() ? unsourced.size() : num_samples));
        sampled_indices.addAll(sourced.subList(0, num_samples > sourced.size() ? sourced.size() : num_samples));
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
     * Extract the statements from a directory containing different wikipedia subset raw data.
     *
     * @param in_dir
     * @param out_dir
     * @param lang
     * @throws IOException
     */
    public static void extractCitationsDir(String in_dir, String out_dir, String lang) throws IOException {
        Set<String> files = new HashSet<>();
        FileUtils.getFilesList(in_dir, files);


        for (String file : files) {
            System.out.println("Processing file " + file);
            String out_file = (new File(file)).getName();
            out_file = out_dir + "/" + out_file.substring(0, out_file.indexOf(".")) + "_statements.txt";

            extractCitations(file, out_file, lang);
            System.out.println("Finished processing file " + file);
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
        String out_file = "";
        if (!out_dir.endsWith(".txt")) {
            out_file = out_dir + "/statements.txt";
        } else {
            out_file = out_dir;
        }

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

    public static void extractStatementCitations(Document doc, Map<String, Map<String, String>> citations,
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
                String prg_text = prg.text();
                if (prg.id().isEmpty()) {
                    continue;
                }

                //the paragraph contains citations
                if (!prg.select("sup").isEmpty()) {
                    //extract sentences and the corresponding citations.
                    extractSentenceCitationsPrgClean(prg, citations, sb, page_id, revision_id, timestamp, section_id, section_name, title, prg_id);
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
    private static void extractSentenceCitationsPrgClean(Element paragraph, Map<String, Map<String, String>> citations,
                                                         StringBuffer sb, String page_id, String revision_id, String timestamp,
                                                         String section_id, String section_name, String title, int prg_idx) {
        try {
            StringBuffer sb_clean_prg = new StringBuffer();
            Map<String, String> prg_processed_citations = replaceHTMLCitations(paragraph, citations, title, sb_clean_prg);
            String clean_prg_text = Jsoup.parse(sb_clean_prg.toString()).text();
            clean_prg_text = replaceCitationNeededCases(prg_processed_citations, clean_prg_text);

            String[] sentences = clean_prg_text.split("\\.\\s+");
            for (int i = 0; i < sentences.length; i++) {
                String sentence = sentences[i];
                //the sentence does not contain any citation
                if (!sentence.contains("{{") || sentence.length() < STATEMENT_LENGTH) {
                    continue;
                }

                String sentence_clean = sentence.replaceAll("\\{+(.*?)\\}+", "").replaceAll("\n", "\\n");

                sb.append(page_id).append("\t").append(revision_id).append("\t").append(timestamp).append("\t").append(title).append("\t").
                        append(section_id).append("\t").append(section_name).append("\t").append(prg_idx).append("\t").append(i).append("\t").
                        append("<div class=\"sourced-statement\">").append(sentence_clean).append("</div>").append("\t");


                Matcher m = cite_matcher.matcher(sentence);
                while (m.find()) {
                    String cite = m.group().replaceAll("\\{|\\}", "");
                    sb.append(prg_processed_citations.get(cite));
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Clean the paragraph first by extracting the inline citations and then remove the citation HTML data such that
     * we can have better parsing of the sentences.
     *
     * @param paragraph
     * @param entity_citations
     * @param title
     * @param sb
     * @return
     */
    public static Map<String, String> replaceHTMLCitations(Element paragraph,
                                                           Map<String, Map<String, String>> entity_citations,
                                                           String title,
                                                           StringBuffer sb) {
        if (paragraph.select("sup").isEmpty()) {
            return null;
        }

        String paragraph_text = paragraph.toString();
        Map<String, String> citations = new HashMap<>();
        Elements scs = paragraph.getElementsByClass("mw-ref");
        int prev_pos = 0;
        for (Element sc : scs) {
            String citation_text = getCitationAttributes(sc, entity_citations, title);
            String citation_id = String.valueOf(sc.toString().hashCode());

            int start_pos = paragraph_text.indexOf(sc.toString());
            int end_pos = start_pos + sc.toString().length();

            String sub_text = paragraph_text.substring(prev_pos, start_pos);
            if (sub_text.trim().endsWith(".")) {
                sub_text = sub_text.substring(0, sub_text.length() - 1);
                sb.append(sub_text).append("{{").append(citation_id).append("}}").append(". ");
            } else {
                sb.append(sub_text).append("{{").append(citation_id).append("}}").append(" ");
            }
            prev_pos = end_pos;

            citations.put(citation_id, citation_text);
        }
        sb.append(paragraph_text.substring(prev_pos));

        return citations;
    }


    /**
     * Extract the citation attributes from text.
     *
     * @param in_cite
     * @param citations
     * @param title
     * @return
     */
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

    /**
     * Replace the citation needed text.
     *
     * @param citations
     * @param paragraph
     * @return
     */
    public static String replaceCitationNeededCases(Map<String, String> citations, String paragraph) {
        Pattern cite_needed = Pattern.compile("\\[citation needed\\]");

        if (!paragraph.contains("citation needed")) {
            return paragraph;
        }

        Matcher m = cite_needed.matcher(paragraph);
        StringBuffer sb = new StringBuffer();
        int prev_pos = 0;
        int cite_needed_id = 0;
        while (m.find()) {
            int start_pos = m.start();
            int end_pos = m.end();
            String citation_id = "cite_needed_" + cite_needed_id;
            cite_needed_id++;

            String sub_text = paragraph.substring(prev_pos, start_pos).trim();
            if (sub_text.trim().endsWith(".")) {
                sub_text = sub_text.substring(0, sub_text.length() - 1);
                sb.append(sub_text).append("{{").append(citation_id).append("}}").append(". ");
            } else {
                sb.append(sub_text).append("{{").append(citation_id).append("}}").append(" ");
            }
            prev_pos = end_pos;

            String cite_text = "{type=needed}";
            citations.put(citation_id, cite_text);
        }
        sb.append(paragraph.substring(prev_pos));
        return sb.toString();
    }

}
