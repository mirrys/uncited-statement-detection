package parser;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by besnik on 2/28/18.
 */
public class HTMLWikiParser {

    /**
     * Extract the section title. Depending on the level of the section, we need to look for either <h2></h2>, <h3></h3> etc.
     *
     * @param section
     * @param section_id
     * @return
     */
    public String getSectionName(Element section, String section_id) {
        String section_name = "";
        if (section_id.equals("0")) {
            return "MAIN_SECTION";
        }
        if (!section.select("h2").isEmpty()) {
            return section.select("h2").first().text();
        }

//        else if (!section.select("h3").isEmpty()) {
//            section_name = section.select("h3").first().text();
//        } else if (!section.select("h4").isEmpty()) {
//            section_name = section.select("h4").first().text();
//        } else if (!section.select("h5").isEmpty()) {
//            section_name = section.select("h5").first().text();
//        }

        return section_name;
    }


    /**
     * Extracts the citation information from a Wikipedia page. Citaitons in the HTML content of a Wikipedia page
     * appear in the HTML clauses <sup about=(.*?) class="mw-ref"(.*?)>(.*?)</sup>
     *
     * @return
     */
    public Map<String, Map<String, String>> extractCitationFromWikiPage(Document doc) {
        String title = doc.title().replaceAll(" ", "_");
        Map<String, String> inline_citations = extractInlineCitations(doc, title);
        Map<String, Map<String, String>> c = new HashMap<>();

        extractNamedCitations(doc, c, inline_citations);
        return c;
    }


    /**
     * Extract all inline citations from a Wikipedia page.
     *
     * @param doc
     * @return
     */
    public Map<String, String> extractInlineCitations(Document doc, String title) {
        Map<String, String> inline_citations = new HashMap<>();

        //extracts inline citations
        Elements elements = doc.getElementsByClass("mw-ref");
        for (Element in_cite : elements) {
            try {
                JSONObject data_mw = new JSONObject(in_cite.attr("data-mw"));
                if (data_mw.has("body") && data_mw.getJSONObject("body").has("id")) {
                    String note_ref_cite_id = data_mw.getJSONObject("body").getString("id").replace("mw-reference-text-", "");
                    inline_citations.put(note_ref_cite_id, in_cite.toString());
                } else {
                    Elements a_ins = in_cite.select("a");
                    for (Element a_in : a_ins) {
                        String a_href = a_in.attributes().get("href");
                        if (a_href.contains(title)) {
                            a_href = a_href.replace("./" + title + "#", "");
                            inline_citations.put(a_href, in_cite.toString());
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error parsing " + StringEscapeUtils.unescapeHtml4(in_cite.toString()));
            }

        }
        return inline_citations;
    }

    /**
     * Extracts the named citations in a page, which appear in a HTML list, and each citation appears within the
     * HTML block <cite class="citation TT"> </cite>
     *
     * @param doc
     * @param c
     */
    public void extractNamedCitations(Document doc, Map<String, Map<String, String>> c, Map<String, String> inline_citations) {
        Elements nc_elements = doc.select("li");
        for (Element nc_element : nc_elements) {
            String id = nc_element.id();
            if (id == null || !inline_citations.containsKey(id)) {
                if (id.isEmpty()) {
                    continue;
                }
                continue;
            }

            //extract the remaining information from the citation
            String cite_id = nc_element.id();
            if (!c.containsKey(cite_id)) {
                c.put(cite_id, new HashMap<>());
            }

            Map<String, String> c_atr = c.get(cite_id);

            //check if its a standard citation first.
            if (!nc_element.select("cite").isEmpty()) {
                //get the citation element
                Element c_element = nc_element.select("cite").first();

                String cite_type = c_element.attr("class").replace("citation", "");
                String about = c_element.attr("about");
                c_atr.put("type", cite_type);
                c_atr.put("about", about);

                Elements a_href = c_element.select("a");
                if (a_href != null) {
                    List<Element> alst = a_href.stream().filter(a -> a.attributes().get("rel").equals("mw:ExtLink")).collect(Collectors.toList());
                    if (alst != null && !alst.isEmpty()) {
                        String url = alst.get(0).attributes().get("href");
                        c_atr.put("url", url);
                        c_atr.put("text", alst.get(0).text());
                    }
                } else {
                    c_atr.put("text", nc_element.text());
                }
            } else {
                c_atr.put("type", "NA");
                c_atr.put("text", nc_element.text());
            }
        }
    }

}
