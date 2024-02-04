import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class BooleanAND {
    public static ArrayList<String> tokenizeQuery(String query, Boolean porterStem) {
        // Tokenization scheme obtained from MSCI 541 course notes
        ArrayList<String> tokens = new ArrayList<String>();
        PorterStemmer ps = new PorterStemmer();
            query = query.toLowerCase();
            int start = 0;
            int i;
            for(i = 0; i < query.length(); i++) {
                char c = query.charAt(i);
                if(!Character.isDigit(c) && !Character.isLetter(c)) {
                    if(start != i) {
                        String token = query.substring(start, i);
                        if(porterStem) {
                            token = ps.stem(token);
                        }
                        tokens.add(token);
                    }
                    start = i + 1;
                }
            }
            if(start != i) {
                // Ensuring last token is stemmed as well
                String token = query.substring(start, i);
                if(porterStem) {
                    token = ps.stem(token);
                }
                tokens.add(token);
            }
        return tokens;
    }
    public static ArrayList<Integer> convertTokensToID(String directory, ArrayList<String> tokens, HashMap<String, Integer> lexiconSI) throws IOException, ClassNotFoundException {
        ArrayList<Integer> tokenIDs = new ArrayList<Integer>();
        GetDoc hashmapRetrieval = new GetDoc();
        for(int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if(token != null) {
                tokenIDs.add(lexiconSI.get(token));
            }
        }
        return tokenIDs;
    }

    public static ArrayList<ArrayList<Integer>> sortPostings(ArrayList<ArrayList<Integer>> postings) {
        // Used this doc as a reminder template of how to perform bubble sort: https://www.javatpoint.com/bubble-sort-in-java
        // Note: Modified to use length of postings list, rather than content of postings list itself.
        int length = postings.size();
        ArrayList<Integer> tempPosting;
        for(int i = 0; i < length; i++){
            for(int j = 1; j < length - i; j ++) {
                if(postings.get(j - 1).size() > postings.get(j).size()) {
                    tempPosting = postings.get(j - 1);
                    postings.set(j - 1,postings.get(j));
                    postings.set(j, tempPosting);
                }
            }
        }
        return postings;
    }

    public static ArrayList<ArrayList<Integer>> getPostings(ArrayList<Integer> tokenIDs, HashMap<Integer, ArrayList<Integer>> invertedIndex) throws IOException, ClassNotFoundException {
        ArrayList<ArrayList<Integer>> postingsLists = new ArrayList<ArrayList<Integer>>();
        for(int i = 0; i < tokenIDs.size(); i++) {
            ArrayList<Integer> posting = invertedIndex.get(tokenIDs.get(i));
            if(posting != null) {
                postingsLists.add(posting);
            }
        }
        postingsLists = sortPostings(postingsLists);
        return postingsLists;
    }

    // Method to merge first two postings
    public static ArrayList<Integer> intersectTwoLists(ArrayList<Integer> posting1, ArrayList<Integer> posting2) {
        ArrayList<Integer> intersection = new ArrayList<Integer>();
        int i = 0;
        int j = 0;
        while(i != posting1.size() && j != posting2.size()) {
            int posting1Item = posting1.get(i);
            int posting2Item = posting2.get(j);
            if(posting1Item == posting2Item) {
                intersection.add(posting1Item);
                i = i + 2;
                j = j + 2;
            } else if(posting1Item < posting2Item) {
                i = i + 2;
            } else {
                j = j + 2;
            }
        }
        return intersection;
    }

    // Intersecting merged list and next postings
    public static ArrayList<Integer> intersectMergedAndPosting(ArrayList<Integer> merged, ArrayList<Integer> posting) {
        ArrayList<Integer> intersection = new ArrayList<Integer>();
        int i = 0;
        int j = 0;
        while(i != merged.size() && j != posting.size()) {
            int mergedItem = merged.get(i);
            int postingItem = posting.get(j);
            if(mergedItem == postingItem) {
                intersection.add(mergedItem);
                i = i + 1;
                j = j + 2;
            } else if(mergedItem < postingItem) {
                i = i + 1;
            } else {
                j = j + 2;
            }
        }
        return intersection;
    }

    public static ArrayList<Integer> intersectLists(ArrayList<ArrayList<Integer>> sortedPostings) {
        // if there are no postings, return an empty list
        if(sortedPostings.size() == 0) {
            return new ArrayList<Integer>();
        }
        // if there is only one postings list, can return all the doc ids within that postings list (nothing else to intersect with)
        else if(sortedPostings.size() == 1) {
            ArrayList<Integer> oneTerm = new ArrayList<Integer>();
            for(int i = 0; i < sortedPostings.get(0).size(); i = i + 2) {
                oneTerm.add(sortedPostings.get(0).get(i));
            }
            return oneTerm;
        // if there are two postings lists, perform one intersection and return the results
        } else if(sortedPostings.size() == 2) {
            ArrayList<Integer> intersection = intersectTwoLists(sortedPostings.get(0), sortedPostings.get(1));
            return intersection;
        // otherwise,
        } else {
            ArrayList<Integer> intersection = intersectTwoLists(sortedPostings.get(0), sortedPostings.get(1));
            // Remove first two postings, since they were used to create the intersected list
            sortedPostings.remove(0);
            sortedPostings.remove(0);
            while(sortedPostings.size() > 0) {
                intersection = intersectMergedAndPosting(intersection, sortedPostings.get(0));
                sortedPostings.remove(0);
            }
            return intersection;
        }
    }

    public static ArrayList<Integer> getQueryResults(String directory, String query, HashMap<Integer, ArrayList<Integer>> invertedIndex, HashMap<String, Integer> lexiconSI, Boolean porterStem) throws IOException, ClassNotFoundException {
        ArrayList<String> tokenizedQuery = tokenizeQuery(query, porterStem);
        ArrayList<Integer> tokenIDs = convertTokensToID(directory, tokenizedQuery, lexiconSI);
        ArrayList<ArrayList<Integer>> postingsLists = getPostings(tokenIDs, invertedIndex);
        ArrayList<Integer> results = intersectLists(postingsLists);
        return results;
    }


    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if(args.length != 4) {
            System.out.print("To use this program, navigate to the /classes folder and compile the code with the command: javac BooleanAND.java. "
            + "After the code has been compiled, enter the command: java BooleanAND [directory of index] [name of queries file] [name of output file] [true/false if wanting to use porter stemmer]");
        } else if (!args[2].contains(".txt")) {
            System.out.print("Please choose an output file with the extension .txt.");
        }
        else {
                String directory = args[0];
                String queryFileName = args[1];
                String outputFileName = args[2];
                Boolean porterStem = Boolean.parseBoolean(args[3]);

                File queryFile = new File(directory + "/" + queryFileName);
                Scanner scanner;
                GetDoc hashmapRetrieval = new GetDoc();
                HashMap<Integer, ArrayList<Integer>> invertedIndex;

                try {
                    scanner = new Scanner(queryFile);
                } catch (FileNotFoundException f) {
                    System.out.print("Query file not found. Please check that the path for the Query file is correctly specified.");
                    return;
                }

                try {
                    invertedIndex = hashmapRetrieval.deserializeHashmap(directory + "/InvertedIndex.ser");
                } catch (FileNotFoundException e) {
                    System.out.print("Location of inverted index not found. Please check that the directory of the inverted index is specified correctly.");
                    return;
                }

                StringBuilder formattedResults = new StringBuilder();
                String mapLocation = directory + "/DocnoInternalMap.ser";
                HashMap<Integer, String> internalDocNoMap = hashmapRetrieval.deserializeHashmap(mapLocation);
                HashMap<String, Integer> lexiconSI = hashmapRetrieval.deserializeHashmap(directory + "/lexiconSI.ser");

                while (scanner.hasNextLine()) {
                    String topicID = scanner.nextLine();
                    String query = scanner.nextLine();
                    ArrayList<Integer> results = getQueryResults(directory, query, invertedIndex, lexiconSI, porterStem);
                    int rank = 1;
                    int resultsSize = results.size();
                    for (int i = 0; i < resultsSize; i++) {
                        int score = resultsSize - rank;
                        String docNo = internalDocNoMap.get(results.get(i));
                        formattedResults.append(topicID + " " + "Q0 " + docNo + " " + rank + " " + score + " a8premjiAND" + "\n");
                        rank += 1;
                    }
                }
                String finalResults = formattedResults.toString().trim();
                File queryOutputs = new File(directory, outputFileName);
                FileOutputStream fos = new FileOutputStream(queryOutputs);
                // Convert string to bytes and write to file. Idea from Peter Knego from this StackOverflow post: https://stackoverflow.com/questions/4069028/write-string-to-output-stream
                fos.write(finalResults.getBytes(StandardCharsets.UTF_8));
                fos.close();
        }
    }
}
