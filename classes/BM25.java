import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class BM25 {

    // Method that converts a list of tokens to their IDs
    public static ArrayList<Integer> convertTokensToID(ArrayList<String> tokens, HashMap<String, Integer> lexiconSI) throws IOException, ClassNotFoundException {
        ArrayList<Integer> tokenIDs = new ArrayList<Integer>();
        for(int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if(token != null) {
                tokenIDs.add(lexiconSI.get(token));
            }
        }
        return tokenIDs;
    }

    // Method that takes in a doc lengths file and outputs its length
    public static ArrayList<Integer> getDocLengths(String directory) throws FileNotFoundException {
        ArrayList<Integer> docLengthsArr = new ArrayList<>();
        File docLengths = new File(directory + "/doc-lengths.txt");
        Scanner scanner = new Scanner(docLengths);
        while (scanner.hasNextLine()) {
            int docLength = Integer.parseInt(scanner.nextLine());
            docLengthsArr.add(docLength);
        }
        return docLengthsArr;
    }

    // Method that outputs a BM25 score for a term and a document in its postings list
    public static double getBM25(ArrayList<Integer> postingsList, int docId, int tokenFreq, ArrayList<Integer> docLengthsArr, Double averageLength) {
        int docLength = docLengthsArr.get(docId - 1);
        double K = 1.2 * ((1 - 0.75) + 0.75 * ((docLength * 1.0 )/averageLength));
        int ni = postingsList.size()/2;
        double BM25 = (tokenFreq/ (K + tokenFreq)) * Math.log((docLengthsArr.size() - ni + 0.5)/(ni + 0.5));
        return BM25;
    }

    // Method that implements term at a time scoring with a particular metric (in this case, BM25)
    public static HashMap<String, Double> termAtATimeScoring (ArrayList<Integer> queryTokenIDs, HashMap<Integer, ArrayList<Integer>> invertedIndex, HashMap<Integer, String> internalDocNoMap,
                                                               ArrayList<Integer> docLengthsArr, Double averageLength) throws IOException, ClassNotFoundException {
        HashMap<String, Double> accumulator = new HashMap<>();
        // Look through each query token
        for(int i = 0; i < queryTokenIDs.size(); i ++) {
            // Obtain postings list for that token
            ArrayList<Integer> postingsList = invertedIndex.get(queryTokenIDs.get(i));
            // Go through all documents in that token's postings list, if it exists
            if(postingsList != null) {
                for (int j = 0; j < postingsList.size(); j = j + 2) {
                    int docId = postingsList.get(j);
                    String docNo = internalDocNoMap.get(docId);
                    int termFreq = postingsList.get(j + 1);
                    double BM25 = getBM25(postingsList, docId, termFreq, docLengthsArr, averageLength);
                    // If accumulator already has that document, increase its score, else add the BM25 score to the existing one.
                    if (accumulator.containsKey(docNo)) {
                        double currBM25 = accumulator.get(docNo);
                        currBM25 += BM25;
                        accumulator.put(docNo, currBM25);
                    } else {
                        accumulator.put(docNo, BM25);
                    }
                }
            }
        }
        // Obtained code for sorting hashmap on values and getting top x from Brian Goetz's post on this SO forum: https://stackoverflow.com/questions/109383/sort-a-mapkey-value-by-values
        HashMap<String,Double> accumulatorThousand = accumulator.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .limit(1000)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        return accumulatorThousand;
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if(args.length < 2) {
            System.out.println("To use this program, first navigate to the /classes folder, and compile the code with javac BM25.java. " +
                    "Then, enter the command java BM25 [directory of inverted index/doclengths files] [true/false if using the Porter Stemmer or not]");
            return;
        }
        GetDoc hashmapRetrieval = new GetDoc();
        HashMap<Integer, ArrayList<Integer>> invertedIndex;
        String directory = args[0];
        Boolean porterStem = Boolean.parseBoolean(args[1]);

        HashMap<String, Integer> lexiconSI = hashmapRetrieval.deserializeHashmap(directory + "\\lexiconSI.ser");;
        HashMap<Integer, String> internalDocNoMap = hashmapRetrieval.deserializeHashmap(directory + "\\DocnoInternalMap.ser");
        // Print out when required data structures are deserialized to view how long the term at a time scoring takes.

        BooleanAND ba = new BooleanAND();
        ArrayList<Integer> docLengthsArr = getDocLengths(directory);
        // Obtained calculation for average of ArrayList in Java from robjwilson's StackOverflow post: https://stackoverflow.com/questions/10791568/calculating-average-of-an-array-list
        Double averageLength = docLengthsArr.stream().mapToDouble(a -> a).average().orElse(0.0);
        File topicsFile = new File(directory + "\\queries.txt");
        Scanner scanner = new Scanner(topicsFile);
        StringBuilder trecResults = new StringBuilder();
        String runTag;
        if(porterStem == false) {
            runTag = "a8premjiBM25noStem";
        } else {
            runTag = "a8premjiBM25stem";
        }
        try {
            invertedIndex = hashmapRetrieval.deserializeHashmap(directory + "\\InvertedIndex.ser");
        } catch (IOException | ClassNotFoundException e) {
            System.out.print("Location of inverted index not found. Please check that the directory of the inverted index is specified correctly.");
            return;
        }
        System.out.println("Inverted Index, Lexicon and Docno Map Deserialized.");
        while(scanner.hasNextLine()) {
            // Obtain topicID and query
            String topicID = scanner.nextLine();
            String topicQuery = scanner.nextLine();
            ArrayList<String> queryTokens = ba.tokenizeQuery(topicQuery, porterStem);
            ArrayList<Integer> queryTokenIDs = convertTokensToID(queryTokens, lexiconSI);
            // Obtain BM25 accumulator HashMap for that topic
            HashMap<String, Double> docScore = termAtATimeScoring(queryTokenIDs,invertedIndex, internalDocNoMap, docLengthsArr,averageLength);
            int rank = 1;
            // For obtained accumulator, append results in TREC format by iterating through docScore HashMap.
            for (Map.Entry<String, Double> entry : docScore.entrySet()) {
                String docNo = entry.getKey();
                Double BM25 = entry.getValue();

                trecResults.append(topicID + " Q0 " + docNo + " " + rank + " " + BM25 + " " + runTag + "\n");
                rank += 1;
            }
        }
        String finalResults = trecResults.toString().trim();
        String fileName;
        if(porterStem == false) {
            fileName = "hw4-bm25-baseline-a8premji.txt";
        } else {
            fileName = "hw4-bm25-stem-a8premji.txt";
        }
        File queryOutputs = new File(directory,fileName);
        FileOutputStream fos = new FileOutputStream(queryOutputs);
        // Convert string to bytes and write to file. Idea from Peter Knego from this StackOverflow post: https://stackoverflow.com/questions/4069028/write-string-to-output-stream
        fos.write(finalResults.getBytes(StandardCharsets.UTF_8));
        fos.close();
    }
}