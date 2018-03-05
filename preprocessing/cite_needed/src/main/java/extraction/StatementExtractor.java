package extraction;

import entities.WikiEntity;
import entities.WikiSection;
import entities.WikiStatement;
import gnu.trove.set.hash.TIntHashSet;
import io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.w3c.dom.Document;
import utils.WikiReaderUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by besnik on 1/15/18.
 */
public class StatementExtractor {
    //the citation pattern
    public static final Pattern cite_pattern = Pattern.compile("\\{[0-9]*\\}");

    public static void main(String[] args) throws IOException, InterruptedException {
        String[] args1 = {"-entity_seeds", "/Users/besnik/Documents/L3S/unsourced_statements/featured_links.csv",
                "-wiki_dump", "/Users/besnik/Documents/L3S/unsourced_statements/frwiki/wiki_subset.log", "-out_dir", "/Users/besnik/Documents/L3S/unsourced_statements/unsourced_statements/",
                "-lang", "frwiki", "-filters", "/Users/besnik/Documents/L3S/unsourced_statements/filters.txt", "-log", "false"};
        args = args1;
        String entity_seeds = "", wiki_dump = "", out_dir = "", lang = "";
        boolean log_wiki = true;
        String filters[] = {};

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-entity_seeds")) {
                entity_seeds = args[++i];
            } else if (args[i].equals("-wiki_dump")) {
                wiki_dump = args[++i];
            } else if (args[i].equals("-out_dir")) {
                out_dir = args[++i];
            } else if (args[i].equals("-lang")) {
                lang = args[++i];
            } else if (args[i].equals("-filters")) {
                filters = FileUtils.readText(args[++i]).split("\n");
            } else if (args[i].equals("-log")) {
                log_wiki = args[++i].equals("true");
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
            entities.put(tmp[2].replaceAll("_", " ").trim(), Integer.parseInt(tmp[0]));
        }

        //extract the statements
        String out_lang_dir = out_dir + "/" + lang + "/";
        FileUtils.checkDir(out_lang_dir);
        extractStatements(wiki_dump, entities, filters, out_lang_dir, log_wiki);
    }

    /**
     * Extracts all the statements for a given set of Wikipedia articles of choice.
     * We keep the extracted statement along with the position where it occurs in the  Wikipedia page,
     * and print it out together with the actual paragraph and the citations or the citation needed marker.
     *
     * @param wiki_file
     * @param entities
     * @param out_dir
     */
    public static void extractStatements(String wiki_file, Map<String, Integer> entities, String[] filters, String out_dir,
                                         boolean log_wiki) throws IOException, InterruptedException {
        BufferedReader reader = FileUtils.getFileReader(wiki_file);
        StringBuffer sb = new StringBuffer();
        List<Document> entities_doc = new ArrayList<>();
        String line;
        boolean is_data_region = false;
        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.contains("<page>")) {
                is_data_region = true;
                if (sb.length() != 0) {
                    String entity_text = "<page>\n" + sb.toString() + "\n</page>";
                    Document doc = FileUtils.readXMLDocumentFromString(entity_text);

                    String entity_name = doc.getElementsByTagName("title").item(0).getTextContent();
                    sb.delete(0, sb.length());
                    if (!entities.containsKey(entity_name)) {
                        continue;
                    }
                    System.out.println("Processing " + entity_name);
                    entities_doc.add(doc);

                    if (log_wiki) {
                        FileUtils.saveText(entity_text + "\n", out_dir + "/wiki_subset.log", true);
                    }

                    if (entities.size() > 1000 && entities_doc.size() > 100000) {
                        Map<String, WikiEntity> parsed_entities = WikiReaderUtil.parseEntities(entities_doc, filters, true);
                        parsed_entities.values().stream().forEach(entity -> printEntityStatements(entity, entities, out_dir));
                        parsed_entities.clear();
                    }

                    //we have found all the entities of interest.
                    if (entities_doc.size() == entities.size()) {
                        break;
                    }
                }
                continue;
            } else if (line.contains("</page>")) {
                continue;
            }
            if (is_data_region) {
                sb.append(line).append("\n");
            }
        }

        //perform the statement extraction operations.
        Map<String, WikiEntity> parsed_entities = WikiReaderUtil.parseEntities(entities_doc, filters, true);
        parsed_entities.values().stream().forEach(entity -> printEntityStatements(entity, entities, out_dir));
    }


    /**
     * Prints each statement in a line. We keep the start position, offset, in the original markup text.
     * <p>
     * entity_id[TAB]entity[TAB]section[TAB]start[TAB]offset[TAB]statement[TAB]paragraph
     *
     * @param entity
     * @param out_dir
     */
    public static void printEntityStatements(WikiEntity entity, Map<String, Integer> entities, String out_dir) {
        String entity_markup = entity.content;
        String entity_markup_out = entity.title + "\t" + StringEscapeUtils.escapeJson(entity_markup) + "\n";

        StringBuffer sb = new StringBuffer();
        for (String section_label : entity.getSectionKeys()) {
            WikiSection section = entity.getSection(section_label);

            String[] section_paragraphs = section.section_text.split("\n");
            //iterate over all the statements.
            Map<Integer, WikiStatement> statements = section.getSectionStatements();
            for (int statement_id : statements.keySet()) {
                WikiStatement statement = statements.get(statement_id);

                String statement_text = statement.getStatement();
                String paragraph = getStatementParagraph(section_paragraphs, statement_text);

                int start = entity_markup.indexOf(statement_text);
                int offset = statement_text.length();

                sb.append(entities.get(entity.title)).append("\t").append(entity.getRevisionID()).append("\t").append(entity.title).append("\t").
                        append(section.section_label).append("\t").
                        append(start).append("\t").append(offset).append("\t").
                        append(statement_text).append("\t").
                        append(StringEscapeUtils.escapeJson(paragraph)).append("\t").
                        append(printCitations(statement.citations, entity)).append("\n");
            }

            //print also the sentences which come from paragraphs that have no citation
            sb.append(printUnsourcedStatements(entity.title, entity.getRevisionID(), section_label, entities.get(entity.title), section_paragraphs, entity_markup));
        }

        FileUtils.saveText(sb.toString(), out_dir + "/statement_dataset.tsv", true);
        FileUtils.saveText(entity_markup_out, out_dir + "/entities_wikimarkup.txt", true);
    }


    /**
     * From a paragraph without any citation, print each sentence in a line.
     * We keep the start position, offset, in the original markup text.
     * <p>
     * entity_id[TAB]entity[TAB]section[TAB]start[TAB]offset[TAB]statement[TAB]paragraph
     *
     * @param entity_title
     * @param section_label
     * @param entity_id
     * @param entity_markup
     * @param section_paragraphs
     */
    public static String printUnsourcedStatements(String entity_title, long revision_id, String section_label, int entity_id, String[] section_paragraphs, String entity_markup) {
        StringBuffer sb = new StringBuffer();
        //here we check only those paragraphs in which there is no citation
        for (String paragraph : section_paragraphs) {
            Matcher cite_matcher = cite_pattern.matcher(paragraph);

            //in case there are no citation, extract all the sentences for this paragraph
            if (!cite_matcher.find() && !paragraph.trim().isEmpty()) {
                String prg_replaced = paragraph.replaceAll("\\[(.*?)\\]?", "").replaceAll("\\{(.*?)\\}?", "").trim();
                if (prg_replaced.isEmpty() || prg_replaced.length() < 200
                        || paragraph.contains("[[File:") || paragraph.startsWith("*")
                        || paragraph.contains("[[Fichier")) {
                    continue;
                }
                int start = entity_markup.indexOf(paragraph);
                int offset = paragraph.length();

                sb.append(entity_id).append("\t").append(revision_id).append("\t").append(entity_title).append("\t").
                        append(section_label).append("\t").
                        append(start).append("\t").append(offset).append("\t").
                        append(paragraph).append("\t").
                        append("N/A").append("\t").
                        append("N/A").append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Print the citation information.
     *
     * @param citations
     * @param entity
     * @return
     */
    private static String printCitations(TIntHashSet citations, WikiEntity entity) {
        StringBuffer sb = new StringBuffer();

        for (int cite_id : citations.toArray()) {
            Map<String, String> cite_attributes = entity.getCitation(cite_id);
            if (cite_attributes == null || cite_attributes.isEmpty()) {
                continue;
            }

            sb.append(cite_attributes.toString()).append("\t");
        }
        return sb.toString().trim();
    }

    /**
     * Return the paragraph which contains the statement in a section.
     *
     * @param section_paragraphs
     * @param statement
     * @return
     */
    private static String getStatementParagraph(String[] section_paragraphs, String statement) {
        for (String paragraph : section_paragraphs) {
            if (paragraph.contains(statement)) {
                return paragraph;
            }
        }
        return null;
    }

}
