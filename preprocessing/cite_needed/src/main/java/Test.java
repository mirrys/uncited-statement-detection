import io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;

/**
 * Created by besnik on 2/27/18.
 */
public class Test {
    public static void main(String[] args) throws IOException {
        String text = FileUtils.readText("/Users/besnik/Documents/L3S/unsourced_statements/html_data/enwiki/out.txt");
        Document entity_doc = Jsoup.parse(text);
        Elements meta_data = entity_doc.select("meta");
        System.out.println(meta_data.get(0).attr("content"));
    }
}
