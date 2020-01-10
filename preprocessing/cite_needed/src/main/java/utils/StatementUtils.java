package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Created by besnik on 9/10/18.
 */
public class StatementUtils {
    public static void main(String[] args) throws IOException {
        String option = "", filter = "", in_dir = "", out_dir = "", filter_tag = "";
        boolean remove_html = false, is_same = false, is_equal = false;
        int num_samples = 10000;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-option")) {
                option = args[++i];
            } else if (args[i].equals("-filter")) {
                filter = args[++i];
            } else if (args[i].equals("-in_dir")) {
                in_dir = args[++i];
            } else if (args[i].equals("-out_dir")) {
                out_dir = args[++i];
            } else if (args[i].equals("-remove_html")) {
                remove_html = args[++i].equals("true");
            } else if (args[i].equals("-is_same")) {
                is_same = args[++i].equals("true");
            } else if (args[i].equals("-is_equal")) {
                is_equal = args[++i].equals("true");
            } else if (args[i].equals("-filter_tag")) {
                filter_tag = args[++i];
            } else if (args[i].equals("-num_samples")) {
                num_samples = Integer.parseInt(args[++i]);
            }
        }

        if (option.equals("filter_cite")) {
            createSubsetStatementsCite(in_dir, out_dir, filter, filter_tag, is_same, is_equal, remove_html);
        } else if (option.equals("sample_statements")) {
            sampleStatements(in_dir, out_dir, num_samples);
        } else if (option.equals("trim_citation")) {
            trimMultipleCitations(in_dir, out_dir);
        } else if (option.equals("trim_space")) {
            trimSpace(in_dir, out_dir);
        }
    }

    /**
     * Some of the
     *
     * @param in_dir
     * @param out_dir
     * @throws IOException
     */
    public static void trimSpace(String in_dir, String out_dir) throws IOException {
        Set<String> files = new HashSet<>();
        FileUtils.getFilesList(in_dir, files);

        for (String file : files) {
            String file_name = (new File(file)).getName();
            file_name = file_name.substring(0, file_name.indexOf("."));
            String out_file = out_dir + "/" + file_name + "_out.txt";

            StringBuffer sb = new StringBuffer();
            String line;
            BufferedReader reader = FileUtils.getFileReader(file);


            while ((line = reader.readLine()) != null) {
                sb.append(line.trim()).append("\n");

                if (sb.length() > 100000) {
                    FileUtils.saveText(sb.toString(), out_file, true);
                    sb.delete(0, sb.length());
                }
            }

            FileUtils.saveText(sb.toString(), out_file, true);
            System.out.println("Finished processing file " + file_name);
        }

    }

    /**
     * Some of the
     *
     * @param in_dir
     * @param out_dir
     * @throws IOException
     */
    public static void trimMultipleCitations(String in_dir, String out_dir) throws IOException {
        //entity_id       revision_id     timestamp       entity_title    section_id      section prg_idx sentence_idx    statement       citations
        Set<String> files = new HashSet<>();
        FileUtils.getFilesList(in_dir, files);

        for (String file : files) {
            String file_name = (new File(file)).getName();
            file_name = file_name.substring(0, file_name.indexOf("."));
            String out_file = out_dir + "/" + file_name + "_out.txt";

            StringBuffer sb = new StringBuffer();
            String line;
            BufferedReader reader = FileUtils.getFileReader(file);


            while ((line = reader.readLine()) != null) {
                sb.append(trimCitationsForStatement(line));
                if (sb.length() > 100000) {
                    FileUtils.saveText(sb.toString(), out_file, true);
                    sb.delete(0, sb.length());
                }
            }

            FileUtils.saveText(sb.toString(), out_file, true);
            System.out.println("Finished processing file " + file_name);
        }

    }

    /**
     * Sample statements from a set of input files for a pre-determined number of samples.
     *
     * @param in_dir
     * @param out_dir
     * @param num_samples
     */
    public static void sampleStatements(String in_dir, String out_dir, int num_samples) throws IOException {
        Set<String> files = new HashSet<>();
        FileUtils.getFilesList(in_dir, files);

        for (String file : files) {
            String file_name = (new File(file)).getName();
            file_name = file_name.substring(0, file_name.indexOf("."));
            String out_file = out_dir + "/" + file_name + "_sample.txt";

            System.out.println("Sampling statements from file " + file_name);

            Set<Integer> sampled_lines = sampleLines(file, num_samples);
            StringBuffer sb = new StringBuffer();
            String line;
            int idx = 0;
            BufferedReader reader = FileUtils.getFileReader(file);
            while ((line = reader.readLine()) != null) {
                if (sampled_lines.contains(idx)) {
                    sb.append(line).append("\n");
                }
                idx++;
            }

            FileUtils.saveText(sb.toString(), out_file);
            System.out.println("Finished sampling statements from file " + file_name);
        }
    }

    /**
     * Filter the original statements based on some-predefined criteria on citation type, article etc.
     *
     * @param in_dir
     * @param out_dir
     * @param filter
     */
    public static void createSubsetStatementsCite(String in_dir, String out_dir, String filter, String filter_tag, boolean is_same, boolean is_equal, boolean remove_html) throws IOException {
        Set<String> files = new HashSet<>();
        FileUtils.getFilesList(in_dir, files);

        for (String file : files) {
            String file_name = (new File(file)).getName();
            file_name = file_name.substring(0, file_name.indexOf("."));
            String out_file = out_dir + "/" + file_name + "_" + filter_tag + ".txt";

            System.out.println("Filtering file " + file + " for filter " + filter);

            BufferedReader reader = FileUtils.getFileReader(file);
            String line;
            int idx = 0;

            StringBuffer sb = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                if (idx == 0) {
                    sb.append(line).append("\n");
                    idx++;
                }

                String[] data = line.trim().split("\t");
                boolean has_match = false;
                String cite_field = data[data.length - 1].trim();
                if (is_equal) {
                    if(is_same){
                        has_match = cite_field.equals(filter);
                    } else{
                        has_match = !cite_field.equals(filter);
                    }
                } else {
                    if(is_same){
                        has_match = cite_field.contains(filter);
                    } else{
                        has_match = !cite_field.contains(filter);
                    }
                }

                line = trimCitationsForStatement(line);
                if (has_match) {
                    if (remove_html) {
                        line = line.replaceAll("</?div(.*?)>", "");
                    }
                    sb.append(line.trim()).append("\n");
                }

                //flush out if the string is too large
                if (sb.length() > 100000) {
                    FileUtils.saveText(sb.toString(), out_file, true);
                    sb.delete(0, sb.length());
                }
            }
            FileUtils.saveText(sb.toString(), out_file, true);
            System.out.println("Finished processing file " + file);
        }
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
        BufferedReader cl_reader = io.FileUtils.getFileReader(file);
        while (cl_reader.readLine() != null) {
            num_lines++;
        }
        cl_reader.close();

        Set<Integer> lines = new HashSet<>();
        lines.add(0);
        while (lines.size() < num_samples && lines.size() < num_lines) {
            lines.add(rand.nextInt(num_lines));
        }

        return lines;
    }

    /**
     * Merge the multiple citations into one.
     *
     * @param line
     * @return
     */
    private static String trimCitationsForStatement(String line) {
        StringBuffer sb = new StringBuffer();
        String[] data = line.split("\t");
        if (data.length <= 10) {
            sb.append(line).append("\n");
        } else {
            for (int i = 0; i <= 8; i++) {
                sb.append(data[i]).append("\t");
            }

            sb.append(data[9]);
            for (int i = 10; i < data.length; i++) {
                sb.append(";").append(data[i]);
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
