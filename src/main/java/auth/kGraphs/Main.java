package auth.kGraphs;

import auth.kGraphs.graphDb.GraphDbService;
import auth.kGraphs.rml.RmlMapper;

public class Main {
    static String serverUrl = "http://localhost:7200";
    static String repositoryName = "MiniProject";

    public static void main(String[] args) {
        // Execute RML mapping
        boolean success = RmlMapper.map();
        System.out.println("RML mapping was " + (success? "successful" : "unsuccessful") + "." );

        // Establish connection to the GraphDb repository
        GraphDbService dbService = new GraphDbService(serverUrl, repositoryName);

        // Upload ontology and ontology data
        dbService.upload();

        // Execute SparQL queries
        dbService.executeSparqlQueries();
    }
}