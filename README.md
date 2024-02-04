# LA Times Search Engine

## Description of How to Run and Build Code

Ensure that you have a version of Java installed. If not, follow these instructions on downloading an appropriate version for your machine:
https://www.java.com/en/download/help/download_options.html. For this project, version 11.0.9.1 of Java was used.

Clone the repository to your local machine with the following command:
```git clone https://github.com/UWaterloo-MSCI-541/msci-541-f23-hw5-aniqp.git```

### Interactive Retrieval

To run the QueryBiasedSummary.java program, which allows users to interact with the search engine, first navigate to the /classes folder, and compile the code with the command: ```javac QueryBiasedSummary.java```. To run the program, enter the command: ```java QueryBiasedSummary [directory of document store]```. In this program, you may enter your query to return the documents with the 10 highest BM25 scores. After this, you may type in one of the ranks to view the document at that rank, press 'N' to enter a new query, or 'Q' to quit the program.

### Data Structure Setup

To run IndexEngine, navigate to the /classes folder and compile the code with the command: javac IndexEngine.java. Once the code has been compiled, enter the command: ```java IndexEngine [directory of latimes.gz folder] [true/false if wanting to use porter stemmer or not] [directory of desired document storage]```. **Note**: Porter stemming is not supported for interactive query sessions with QueryBiasedSummary. If you desire to use this program, please set the porter stemmer to ```false```.

To run GetDoc, remain in the /classes folder and enter the command: ```javac GetDoc.java```. After compiling, run the command: ```java GetDoc [directory of specified document store] [either "docno" or "id"] [corresponding DocNo or internal id]```.

To run BooleanAND, remain in the /classes folder and compile the code with the command: ```javac BooleanAND.java```. After the code has been compiled, enter the command: ```java BooleanAND [directory of index] [name of queries file] [name of output file] [true/false if wanting to use porter stemmer]```.

To run BM25, first remain in the /classes folder, and compile the code with ```javac BM25.java```. Then, enter the command: ```java BM25 [directory of inverted index/doclengths files] [true/false if using the Porter Stemmer or not]```. Ensure that the queries file used is in the same directory as the inverted index and doclengths files.
