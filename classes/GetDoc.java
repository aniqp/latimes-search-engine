import java.io.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

public class GetDoc {

    public static String convertDocNoToDirectory(String docNo){
        String month = docNo.substring(2, 4);
        String date = docNo.substring(4, 6);
        String year = docNo.substring(6, 8);
        String docName = docNo.substring(9);
        String filePath = "\\" + year + "\\" + month + "\\" + date + "\\" + docName;
        return filePath;
    }

    public static String getDocNoFromId(String filePath, int id) throws IOException, ClassNotFoundException {
        String mapLocation = filePath + "/DocnoInternalMap.ser";
        HashMap<Integer, String> internalDocNoMap = deserializeHashmap(mapLocation);
        return internalDocNoMap.get(id);
    }

    public static HashMap<String, Object> fetchMetadataMap(String filePath) throws IOException, ClassNotFoundException {
        HashMap<String, Object> metadataMap = deserializeHashmap(filePath);
        return metadataMap;
    }

    public static BufferedReader fetchDocument(String filePath) throws IOException {
        IndexEngine ie = new IndexEngine();
        BufferedReader buffered = ie.bufferFiles(filePath);
        return buffered;
    }

    public static HashMap deserializeHashmap(String filePath) throws IOException, ClassNotFoundException {
        InputStream fileStream = new FileInputStream(filePath);
        ObjectInputStream objectStream = new ObjectInputStream(fileStream);
        HashMap map = null;
        // From the same article I learned how to serialize a hashmap, I learned how to typecast
        // an object back to the expected type (in this case, a Map): https://beginnersbook.com/2013/12/how-to-serialize-hashmap-in-java/
        map = (HashMap) objectStream.readObject();
        return map;
    }

    public static void printDocument(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        while (line != null) {
            System.out.print("\n");
            System.out.print(line);
            line = reader.readLine();
        }
    }
    public static void printMetadata(Map<String, Object> metadataMap) {
        System.out.println("docno: " + metadataMap.get("docno"));
        System.out.println("internal id: " + metadataMap.get("internal id"));
        System.out.println("date: " + metadataMap.get("date"));
        System.out.println("headline: " + metadataMap.get("headline"));
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if(args.length != 3) {
            System.out.print("To use this program, navigate to the /classes folder and compile the code with javac GetDoc.java. " +
                    "After compiling, enter the command: java GetDoc [directory of Document Store] [either \"docno\" or \"id\"] [corresponding docno or internal id].");
        } else if(!args[1].toLowerCase().equals("docno") && (!args[1].toLowerCase().equals("id"))) {
            System.out.print("Please use the commands \"docno\" or \"id\" followed by the corresponding identifier.");
        }
        else {
            String directory = args[0];
            String command = args[1];
            String docNo = args[2];
            try {
                if(command.toLowerCase().equals("id")) {
                    int id = Integer.parseInt(args[2]);
                    docNo = getDocNoFromId(directory, id);
                }
                String docNoDirectory = convertDocNoToDirectory(docNo);
                BufferedReader reader = fetchDocument(directory + docNoDirectory + ".gz");
                HashMap<String, Object> metadataMap = fetchMetadataMap(directory + docNoDirectory + " - MetadataMap.ser");
                printMetadata(metadataMap);
                System.out.print("raw document:");
                printDocument(reader);
            } catch(NullPointerException n) {
                System.out.println("Please enter a valid DocNo or Internal ID.");
            } catch(StringIndexOutOfBoundsException s) {
                System.out.println("Please enter a valid DocNo or Internal ID.");
            } catch(FileNotFoundException f) {
                System.out.println("Please ensure the directory of the Document Store is correct, and that you have entered a valid DocNo or Internal ID.");
            }
        }

    }
}