import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class IndexEngine {

    public static String getMonthFromNumber(String num) {
        Map<String, String> monthMapping = new HashMap<>();
        monthMapping.put("01", "January");
        monthMapping.put("02", "February");
        monthMapping.put("03", "March");
        monthMapping.put("04", "April");
        monthMapping.put("05", "May");
        monthMapping.put("06", "June");
        monthMapping.put("07", "July");
        monthMapping.put("08", "August");
        monthMapping.put("09", "September");
        monthMapping.put("10", "October");
        monthMapping.put("11", "November");
        monthMapping.put("12", "December");

        return monthMapping.get(num);
    }

    public static String processDate(String date) {
        if(date.charAt(0) == '0') {
            return date.substring(1);
        } else {
            return date;
        }
    }

    public static void saveDocLengths(String directory, String fileName, String docLengths) throws IOException {
        File docLengthsFile = new File(directory, fileName);
        FileOutputStream fos = new FileOutputStream(docLengthsFile);
        // Convert string to bytes and write to file. Idea from Peter Knego from this StackOverflow post: https://stackoverflow.com/questions/4069028/write-string-to-output-stream
        fos.write(docLengths.getBytes(StandardCharsets.UTF_8));
        fos.close();
    }

    public static void serializeHashmap(String directory, String fileName, HashMap hmap) throws IOException {
        File metadataSerial = new File(directory, fileName);
        FileOutputStream fos_meta = new FileOutputStream(metadataSerial);
        ObjectOutputStream oos_meta = new ObjectOutputStream(fos_meta);
        oos_meta.writeObject(hmap);
        oos_meta.close();
        fos_meta.close();
    }

    public static BufferedReader bufferFiles(String filePath) throws IOException {
        // Obtained from the top answer of the following article: https://stackoverflow.com/questions/1080381/gzipinputstream-reading-line-by-line
        InputStream fileStream = new FileInputStream(filePath);
        InputStream gzipStream = new GZIPInputStream(fileStream);
        Reader decoder = new InputStreamReader(gzipStream, "UTF-8");
        BufferedReader buffered = new BufferedReader(decoder);
        return buffered;
    }

    public static void directoryExists(String directory) throws FileAlreadyExistsException {
        Path path = Paths.get(directory);
        if (Files.exists(path)) {
            throw new FileAlreadyExistsException("Document store has already been created in that location!");
        }
    }

    public static void gzipDocument(String directory, String docName, String documentString) throws IOException {
        File document = new File(directory, docName + ".gz");
        FileOutputStream fos = new FileOutputStream(document);
        GZIPOutputStream gzos = new GZIPOutputStream(fos);
        gzos.write(documentString.getBytes());
        gzos.close();
        fos.close();
    }

    // Method that iterates through all documents, extracting metadata, storing tokens for document and storing document itself.
    public static void storeFilesAndGetTokens(BufferedReader buffered, String directoryStore, Boolean porterStem) throws IOException {
        // method that returns all the text between a tag
        HashMap<Integer, String> internalIdDocnoMap = new HashMap();
        HashMap<String, Integer> lexiconSI = new HashMap<String, Integer>();
        HashMap<Integer, String> lexiconIS = new HashMap<Integer, String>();
        HashMap<Integer, ArrayList<Integer>> invertedIndex = new HashMap<Integer, ArrayList<Integer>>();

        StringBuilder documentString = new StringBuilder();
        StringBuilder docLengths = new StringBuilder();

        documentString.append(buffered.readLine());
        // Throw error message if year 89 directory already exists
        directoryExists(directoryStore + "/89");
        int docId = 1;
        while (documentString != null) {
            String nextLine = buffered.readLine();
            // The second line of any document is the DOCNO
            String docNo = nextLine.substring(8, 21);
            String month = docNo.substring(2, 4);
            String date = docNo.substring(4, 6);
            String year = docNo.substring(6, 8);
            // Doc name is the last 4 characters of the string, so it can be retrieved
            String docName = docNo.substring(9);
            HashMap<String, Object> metadataMap = new HashMap();
            metadataMap.put("docno", docNo);
            metadataMap.put("internal id", docId);
            // Internal id must be converted to an integer for mapping
            internalIdDocnoMap.put(docId, docNo);
            String stringDate = getMonthFromNumber(month) + " " + processDate(date) + ", 19" + year;
            metadataMap.put("date", stringDate);
            StringBuilder headline = new StringBuilder();
            StringBuilder text = new StringBuilder();
            StringBuilder graphic = new StringBuilder();
            // Store relevant text that will be tokenized
            StringBuilder docTokenText = new StringBuilder();
            while (!nextLine.equals("</DOC>")) {
                if (nextLine.equals("<HEADLINE>")) {
                    nextLine = getContentBetweenTags("</HEADLINE>", nextLine, headline, documentString, buffered);
                }
                else if (nextLine.equals("<TEXT>")) {
                    nextLine = getContentBetweenTags("</TEXT>", nextLine, text, documentString, buffered);
                }
                else if (nextLine.equals("<GRAPHIC>")) {
                    nextLine = getContentBetweenTags("</GRAPHIC>", nextLine, graphic, documentString, buffered);
                }
                else {
                    documentString.append("\n");
                    documentString.append(nextLine);
                    nextLine = buffered.readLine();
                }
            }
            // Adding the last "</DOC>" header
            documentString.append("\n");
            documentString.append(nextLine);

            metadataMap.put("headline", headline.toString());
            // Understood how to re-zip and save a file from this StackOverflow post, from user2813148: https://stackoverflow.com/questions/19044638/compress-a-string-to-gzip-in-java
            // Understood how to create directories from the top answer of this post: https://stackoverflow.com/questions/28947250/create-a-directory-if-it-does-not-exist-and-then-create-the-files-in-that-direct
            String directory = directoryStore + "\\" + year + "\\" + month + "\\" + date + "\\";
            Files.createDirectories(Paths.get(directory));
            // Name of document is the last 4 characters of the Docno
            gzipDocument(directory, docName, documentString.toString());
            // Understood how to serialize a hashmap for storage from this article: https://beginnersbook.com/2013/12/how-to-serialize-hashmap-in-java/
            String mapName = docName + " - MetadataMap.ser";
            serializeHashmap(directory, mapName, metadataMap);
            docTokenText.append(headline + " ").append(text + " ").append(graphic);
            String nextDocLine = buffered.readLine();
            // if last doc in collection, change how doclength is printed to text file.
            if(nextDocLine != null) {
                documentString = new StringBuilder(nextDocLine);
            } else {
                buildIndex(docTokenText.toString(), docId, lexiconSI, lexiconIS, invertedIndex, docLengths, true, porterStem);
                break;
            }
            buildIndex(docTokenText.toString(), docId, lexiconSI, lexiconIS, invertedIndex, docLengths, false, porterStem);
            docId += 1;
        }
        // Saving mappings of ids and DocNos
        serializeHashmap(directoryStore, "DocnoInternalMap.ser", internalIdDocnoMap);
        serializeHashmap(directoryStore, "lexiconSI.ser", lexiconSI);
        serializeHashmap(directoryStore, "lexiconIS.ser", lexiconIS);
        saveDocLengths(directoryStore, "doc-lengths.txt", docLengths.toString());
        serializeHashmap(directoryStore, "InvertedIndex.ser", invertedIndex);
    }

    public static String getContentBetweenTags(String docTag, String nextLine, StringBuilder content, StringBuilder documentString, BufferedReader buffered) throws IOException {
        Boolean firstLine = true;
        while (!nextLine.contains(docTag)) {
            // Only text without this opening tag should be considered a document
            if(nextLine.charAt(0) != '<') {
                // If it is the first headline, don't add a space before the next line is added.
                if (firstLine){
                    content.append(nextLine.trim());
                    firstLine = false;
                } else {
                    content.append(" " + nextLine.trim());
                }
            }
            documentString.append("\n");
            documentString.append(nextLine);
            nextLine = buffered.readLine();
        }
        return nextLine;
    }
    public static void buildIndex(String doc,
                                  int docID,
                                  HashMap<String, Integer> lexiconSI,
                                  HashMap<Integer, String> lexiconIS,
                                  HashMap<Integer, ArrayList<Integer>> invertedIndex,
                                  StringBuilder docLengths,
                                  Boolean lastDoc,
                                  Boolean porterStem) {
        ArrayList<String> tokens = tokenizeDoc(doc, docLengths, lastDoc, porterStem);
        ArrayList<Integer> tokenIDs = convertTokensToIDs(tokens, lexiconSI, lexiconIS);
        HashMap<Integer, Integer> wordCount = countWords(tokenIDs);
        addToPostings(wordCount, docID, invertedIndex);
    }

    // Pseudocode obtained from Dr. Smucker's lectures
    public static ArrayList<String> tokenizeDoc(String doc, StringBuilder docLengths, Boolean lastDoc, Boolean porterStemmer) {
        // Tokenization scheme obtained from MSCI 541 course notes
        ArrayList<String> tokens = new ArrayList<String>();
        PorterStemmer ps = new PorterStemmer();
        doc = doc.toLowerCase();
        int start = 0;
        int i;
        for(i = 0; i < doc.length(); i++) {
            char c = doc.charAt(i);
            if(!Character.isDigit(c) && !Character.isLetter(c)) {
                if(start != i) {
                    String token = doc.substring(start, i);
                    if(porterStemmer) {
                        token = ps.stem(token);
                    }
                    tokens.add(token);
                }
                start = i + 1;
            }
        }
        if(start != i) {
            String token = doc.substring(start, i);
            if(porterStemmer) {
                token = ps.stem(token);
            }
            tokens.add(token);
        }
        if(!lastDoc) {
            docLengths.append(tokens.size() + "\n");
        } else {
            docLengths.append(tokens.size());
        }
        return tokens;
    }

    public static ArrayList<Integer> convertTokensToIDs(ArrayList<String> tokens, HashMap<String, Integer> lexiconSI, HashMap<Integer, String> lexiconIS) {
        ArrayList<Integer> tokenIDs = new ArrayList<Integer>();
        for(int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if(lexiconSI.containsKey(token)) {
                tokenIDs.add(lexiconSI.get(token));
            } else {
                // First term ID is 0 in this scheme
                int id = lexiconSI.size();
                lexiconSI.put(token, id);
                lexiconIS.put(id, token);
                tokenIDs.add(id);
            }
        }
        return tokenIDs;
    }

    public static HashMap<Integer, Integer> countWords(ArrayList<Integer> tokenIDs) {
        HashMap<Integer, Integer> wordCount = new HashMap<Integer, Integer>();
        for(int i = 0; i < tokenIDs.size(); i++) {
            int id = tokenIDs.get(i);
            if(wordCount.containsKey(id)) {
                int count = wordCount.get(id);
                wordCount.put(id, count + 1);
            } else {
                wordCount.put(id, 1);
            }
        }
        return wordCount;
    }

    public static void addToPostings(HashMap<Integer, Integer> wordCount, int docID, HashMap<Integer, ArrayList<Integer>> invertedIndex) {
        // Learned how to iterate through a HashMap from the top answer of this post: https://stackoverflow.com/questions/1066589/iterate-through-a-hashmap
        for(Map.Entry<Integer, Integer> entry : wordCount.entrySet()) {
            int id = entry.getKey();
            int count = entry.getValue();
            ArrayList<Integer> postings;
            if(invertedIndex.containsKey(id)) {
                postings = invertedIndex.get(id);
            } else {
                postings = new ArrayList<Integer>();
            }
            postings.add(docID);
            postings.add(count);
            invertedIndex.put(id, postings);
        }
    }

    public static void main (String[] args) throws IOException {
        if (args.length != 3) {
            System.out.print("To use this program, navigate to the /classes folder and compile the code with the command: javac IndexEngine.java." +
                    " After compiling, enter the command: java IndexEngine [directory of latimes.gz folder] [true/false if wanting to use porter stemmer or not] [directory of desired document storage]");
        } else {
            try {
                BufferedReader buffer = bufferFiles(args[0]);
                Boolean porterStem = Boolean.parseBoolean(args[1]);
                storeFilesAndGetTokens(buffer, args[2], porterStem);
            } catch(FileNotFoundException f) {
                System.out.print("File location not found. Please check that you have correctly specified the directory.");
            }
        }
    }
}