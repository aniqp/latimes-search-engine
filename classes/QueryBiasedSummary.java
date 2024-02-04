import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

// This program largely copies much of the logic of HW4 as it also implements BM25 retrieval.
// In HW5, it is done for a user-inputted query rather than a text file of queries.
public class QueryBiasedSummary {

    public static String[] splitDocByStops(String directory, String docNo) throws IOException, ClassNotFoundException {
        String docNoDirectory = GetDoc.convertDocNoToDirectory(docNo);
        BufferedReader reader = GetDoc.fetchDocument(directory + docNoDirectory + ".gz");
        StringBuilder document = new StringBuilder();
        String line = reader.readLine();
        Boolean text = false;
        while (line != null) {
            // Skipping until the text part of the document.
            if(!text) {
                while (line != null && line.equals("<TEXT>") == false) {
                    line = reader.readLine();
                }
                if(line == null) {
                    // if text tag not found, reset reader and look for the graphic tag instead
                    reader = GetDoc.fetchDocument(directory + docNoDirectory + ".gz");
                    line = reader.readLine();
                    while (line.equals("<GRAPHIC>") == false) {
                        line = reader.readLine();
                    }
                }
                text = true;
            }
            if(line.contains("<") == false) {
                document.append(line + "\n");
            }
            line = reader.readLine();
        }
        String documentString = document.toString();
        // Obtained regex for finding a question/exclamation mark from this SO forum: https://stackoverflow.com/questions/889957/escaping-question-mark-in-regex-javascript
        // Using lookbehind to match sentences that end with a period, question mark, or exclamation mark (if the character prior to a character is one of these, then split on that character - learned from this SO forum: https://stackoverflow.com/questions/2973436/regex-lookahead-lookbehind-and-atomic-groups)
        String[] docSentenceArr = documentString.split("(?<=[\\.\\?\\!])");
        for(int i = 0; i < docSentenceArr.length; i ++) {
            docSentenceArr[i] = docSentenceArr[i].trim();
            // Removing line breaks, obtained code from Stephen C's post: https://stackoverflow.com/questions/2163045/how-to-remove-line-breaks-from-a-file-in-java
            docSentenceArr[i] = docSentenceArr[i].replaceAll("\\R", "");
        }
        return docSentenceArr;
    }

    public static HashMap scoreSentences(String[] docSentences, String query) {
        // Using a linked hashmap to preserve order of insertion.
        Map<String, Integer> docSentenceScores = new LinkedHashMap<>();
        int counter = 1;
        for(String sentence: docSentences) {
            int l;
            int c = 0;
            int d = 0;
            // Assigning points for if the sentence is the first or second
            if(counter == 1) {
                l = 2;
            } else if(counter == 2) {
                l = 1;
            } else {
                l = 0;
            }
            String sentenceLower = sentence.toLowerCase();
            String queryLower = query.toLowerCase();
            BooleanAND ba = new BooleanAND();
            ArrayList<String> sentenceWords = ba.tokenizeQuery(sentenceLower, false);
            ArrayList<String> queryWords = ba.tokenizeQuery(queryLower, false);
            for(String word: sentenceWords) {
                // Counting number of occurrences of sentence words in query
                // Obtained code to check if word in array from here: https://www.digitalocean.com/community/tutorials/java-array-contains-value
                if(queryWords.contains(word)) {
                    c+=1;
                }
            }
            // Counting number of occurrences of query words in sentence (distinct)
            for(String queryWord: queryWords) {
                if(sentenceWords.contains(queryWord)) {
                    d+=1;
                }
            }
            int score = l + c + d;
            docSentenceScores.put(sentence, score);
            counter += 1;
        }
        // Obtained code for sorting hashmap on values and getting top x from Brian Goetz's post on this SO forum: https://stackoverflow.com/questions/109383/sort-a-mapkey-value-by-values
        HashMap<String,Integer> sortedDocSentenceScores = docSentenceScores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        return sortedDocSentenceScores;
    }

    public static ArrayList<String> printQueryBiasedSummary(String directory, String query, Boolean porterStem,
                                          BooleanAND ba, HashMap lexiconSI, HashMap invertedIndex, HashMap internalDocNoMap,
                                          ArrayList<Integer> docLengthsArr, Double averageLength) throws IOException, ClassNotFoundException {
        ArrayList<String> topDocNoList = new ArrayList<String>();
        long startTime = System.nanoTime();
        // Obtain topicID and query
        ArrayList<String> queryTokens = ba.tokenizeQuery(query, porterStem);
        ArrayList<Integer> queryTokenIDs = BM25.convertTokensToID(queryTokens, lexiconSI);
        // Obtain BM25 accumulator HashMap for that topic
        HashMap<String, Double> docScore = BM25.termAtATimeScoring(queryTokenIDs, invertedIndex, internalDocNoMap, docLengthsArr, averageLength);
        int rank = 1;
        for (Map.Entry<String, Double> entry : docScore.entrySet()) {
            String docNo = entry.getKey();
            topDocNoList.add(docNo);
            Double BM25 = entry.getValue();
            String[] docStringArr = splitDocByStops(directory, docNo);
            HashMap docSentenceScores = scoreSentences(docStringArr, query);
            HashMap<String, String> metadataMap = GetDoc.deserializeHashmap(directory + "\\" + GetDoc.convertDocNoToDirectory(docNo) + " - MetadataMap.ser");
            // Obtain headline from MetadataMap.
            String headline = metadataMap.get("headline");
            String date = metadataMap.get("date");
            // Learned how to index a HashMap from this SO forum: https://stackoverflow.com/questions/3973512/java-hashmap-how-to-get-a-key-and-value-by-index
            String firstSentence = (String) docSentenceScores.keySet().toArray()[0];
            String secondSentence = (String) docSentenceScores.keySet().toArray()[1];
            String snippet = firstSentence + " " + secondSentence;

            if(headline.equals("")) {
                // Handling cases when string could be shorter than 50 characters
                String fiftyCharSnippet = snippet.substring(0, Math.min(50, snippet.length()));
                System.out.println(rank + ". " + fiftyCharSnippet + " ... (" + date + ")");
                System.out.println(snippet + " (" + docNo + ")\n");
            } else {
                System.out.println(rank + ". " + headline + " (" + date + ")");
                System.out.println(snippet + " (" + docNo + ")\n");
            }

            // Only retrieve the top 10 results.
            if(rank >= 10) {
                break;
            }
            rank += 1;
        }
        long endTime = System.nanoTime();
        // Converting nanoseconds to seconds
        double totalTime = (double) (endTime  - startTime)/1000000000;
        System.out.println("Retrieval time: " + totalTime + " seconds." + "\n");
        return topDocNoList;
    }
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if(args.length != 1) {
            System.out.println("To use this program, navigate to the /classes folder and compile the program with javac QueryBiasedSummary.java. " +
                    "Then, run the compiled code with the command: java QueryBiasedSummary [directory of document store].");
            return;
        }
        String directory = args[0];
        GetDoc hashmapRetrieval = new GetDoc();
        HashMap<Integer, ArrayList<Integer>> invertedIndex;
        // Do not use Porter stemmer for this program
        Boolean porterStem = false;

        HashMap<String, Integer> lexiconSI = hashmapRetrieval.deserializeHashmap(directory + "\\lexiconSI.ser");
        ;
        HashMap<Integer, String> internalDocNoMap = hashmapRetrieval.deserializeHashmap(directory + "\\DocnoInternalMap.ser");
        // Print out when required data structures are deserialized to view how long the term at a time scoring takes.

        BooleanAND ba = new BooleanAND();
        ArrayList<Integer> docLengthsArr = BM25.getDocLengths(directory);
        // Obtained calculation for average of ArrayList in Java from robjwilson's StackOverflow post: https://stackoverflow.com/questions/10791568/calculating-average-of-an-array-list
        Double averageLength = docLengthsArr.stream().mapToDouble(a -> a).average().orElse(0.0);
        try {
            invertedIndex = hashmapRetrieval.deserializeHashmap(directory + "\\InvertedIndex.ser");
        } catch (IOException | ClassNotFoundException e) {
            System.out.print("Location of inverted index not found. Please check that the directory of the inverted index is specified correctly.");
            return;
        }
        System.out.println("Please enter a query: ");
        Scanner userInput = new Scanner(System.in);
        String query = userInput.nextLine();
        // Handle when query is blank
        while(query.equals("")) {
            System.out.println("Query is blank. Please enter a non-blank query.");
            userInput = new Scanner(System.in);
            query = userInput.nextLine();
        }
        ArrayList<String> topDocNoList = printQueryBiasedSummary(directory, query, porterStem, ba, lexiconSI,
                invertedIndex, internalDocNoMap, docLengthsArr, averageLength);
        if(topDocNoList.size() != 0) {
            System.out.println("What would you like to do next? Type one of the document's ranks (ex. 1) to view it in full, " +
                    "type N to enter a new query, or type Q to quit.");
        } else {
            System.out.println("No documents were returned. Type N to enter a new query, or type Q to quit.");
        }
        String option = userInput.nextLine();
        while(!option.toLowerCase().equals("q")) {
            if(option.toLowerCase().equals("n")) {
                System.out.println("Please enter a query: ");
                query = userInput.nextLine();
                while(query.equals("")) {
                    System.out.println("Query is blank. Please enter a non-blank query.");
                    userInput = new Scanner(System.in);
                    query = userInput.nextLine();
                }
                topDocNoList = printQueryBiasedSummary(directory, query, porterStem, ba, lexiconSI,
                        invertedIndex, internalDocNoMap, docLengthsArr, averageLength);
                if(topDocNoList.size() != 0) {
                    System.out.println("What would you like to do next? Type one of the document's ranks (ex. 1) to view it in full, " +
                            "type N to enter a new query, or type Q to quit.");
                } else {
                    System.out.println("No documents were returned. Type N to enter a new query, or type Q to quit.");
                }
                option = userInput.nextLine();
            // If number is entered that is within the ranked documents available
            // Determined how to check if string is a number with Regex from the top answer of this SO forum: https://stackoverflow.com/questions/15111420/how-to-check-if-a-string-contains-only-digits-in-java
            } else if(option.matches("\\d+")) {
                if(topDocNoList.size() == 0) {
                    System.out.println("No documents returned, cannot select a rank. Please type N to enter a new query, or Q to exit.");
                } else {
                    int rankIndex = Integer.parseInt(option) - 1;
                    if(rankIndex < topDocNoList.size() && rankIndex > -1) {
                        String docNo = topDocNoList.get(rankIndex);
                        String docNoDirectory = GetDoc.convertDocNoToDirectory(docNo);
                        BufferedReader reader = GetDoc.fetchDocument(directory + docNoDirectory + ".gz");
                        HashMap<String, Object> metadataMap = GetDoc.fetchMetadataMap(directory + docNoDirectory + " - MetadataMap.ser");
                        GetDoc.printDocument(reader);
                        System.out.println("\n\nWhat would you like to do next? Type one of the document's ranks (ex. 1) to view it in full, " +
                                "type N to enter a new query, or type Q to quit.");
                    } else {
                        System.out.println("Rank out of range of top documents. Please type a document within the ranks of 1 - " + topDocNoList.size());
                    }
                }
                option = userInput.nextLine();
            } else if(option.equals("q") == false) {
                if(topDocNoList.size() != 0) {
                    System.out.println("Option selected not recognized. Please type one of the document's ranks (ex. 1) to view it in full, type N to enter a new query, or type Q to quit.");
                } else {
                    System.out.println("Option selected not recognized. Please type N to enter a new query, or type Q to quit.");
                }
                option = userInput.nextLine();
            }
        }
        userInput.close();
    }
}
