package utils;

import entities.WikiEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by besnik on 1/15/18.
 */
public class WikiReaderUtil {
    /**
     * Parse entities for a list of entities for which we have the raw wikimarkup text.
     *
     * @param entities
     * @param extract_statements
     * @throws InterruptedException
     */
    public static Map<String, WikiEntity> parseEntities(List<Document> entities, String[] filters, boolean extract_statements) throws InterruptedException {
        Map<String, WikiEntity> entity_out = new HashMap<>();
        ExecutorService thread_pool = Executors.newFixedThreadPool(5);
        entities.parallelStream().forEach(doc -> {
            Runnable r = () -> {
                String entity_name = doc.getElementsByTagName("title").item(0).getTextContent();
                String entity_text = doc.getElementsByTagName("text").item(0).getTextContent();
                long revision_id = Long.parseLong(((Element) doc.getElementsByTagName("revision").item(0)).getElementsByTagName("id").item(0).getTextContent());

                System.out.println("Parsing entity " + entity_name);
                for (String filter : filters) {
                    entity_text = entity_text.replaceAll("\\{+" + filter + "(.*?)\n(\\|.*\n)+\\}+", "");
                }

                WikiEntity entity = new WikiEntity();
                entity.title = entity_name;
                entity.content = entity_text;
                entity.setRevisionID(revision_id);

                //we do not want to extract here the references.
                entity.setExtractReferences(true);
                entity.setExtractFullCitationAttributes(true);
                entity.setMainSectionsOnly(false);
                entity.setExtractStatements(extract_statements);
                entity.setSplitSections(true);
                entity.is_citation_hash_key = false;
                entity.parseContent(false);
                entity_out.put(entity_name, entity);
            };
            thread_pool.submit(r);
        });
        thread_pool.shutdown();
        thread_pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        entities.clear();

        return entity_out;
    }
}
