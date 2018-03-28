import io.FileUtils;

import java.io.IOException;

/**
 * Created by besnik on 2/27/18.
 */

public class Test {
    public static void main(String[] args) throws IOException {
        String text = FileUtils.readText("/Users/besnik/Desktop/top_1.html").split("\t")[1];

        String sub = text.substring(34741  ,36867);
        System.out.println(sub);
    }
}
