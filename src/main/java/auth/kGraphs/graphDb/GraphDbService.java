package auth.kGraphs.graphDb;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.rio.RDFFormat;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;

public class GraphDbService {
    // Configuration
    private String baseDir = "./data/";
    private String ontologyFile = baseDir + "semanticMeasurements.ttl";
    private String ontologyData = baseDir + "semanticMeasurementsRmlResults.ttl";
    private HTTPRepository repository;

    public GraphDbService(String serverUrl, String repositoryName) {
        repository = new HTTPRepository(serverUrl, repositoryName);
        repository.init();
    }

    public void upload() {
        try (RepositoryConnection conn = repository.getConnection()) {
            // Clear the repository
            conn.begin();
            conn.prepareUpdate("CLEAR ALL").execute();
            conn.commit();

            // Load the ontology and ontology data
            conn.begin();
            try (
                    InputStream ontStream = new FileInputStream(ontologyFile);
                    InputStream ontDataStream = new FileInputStream(ontologyData)
            ) {
                conn.add(ontStream, "urn:base", RDFFormat.TURTLE);
                conn.add(ontDataStream, "urn:base", RDFFormat.TURTLE);
            }
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void executeSparqlQueries() {
        try (RepositoryConnection conn = repository.getConnection()) {
            // Classify scammers
            String classifyScammersQuery = String.join("\n",
                    "PREFIX : <http://example.com/semanticMeasurements#>",
                    "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>",
                    "INSERT {",
                    "  ?consumer a :Scammer .",
                    "}",
                    "WHERE {",
                    "  {",
                    "    SELECT DISTINCT ?consumer WHERE {",
                    "      ?m1 a :WaterMeasurement ; :measuredBy ?meter ; :readingDate ?d1 ; :volume ?v1 .",
                    "      ?m2 a :WaterMeasurement ; :measuredBy ?meter ; :readingDate ?d2 ; :volume ?v2 .",
                    "      FILTER(xsd:dateTime(?d1) < xsd:dateTime(?d2))",
                    "      BIND(xsd:decimal(?v1) AS ?dec1)",
                    "      BIND(xsd:decimal(?v2) AS ?dec2)",
                    "      FILTER(?dec1 > ?dec2)",
                    "      ?meter :ownedBy ?consumer .",
                    "    }",
                    "  }",
                    "}"
            );
            conn.begin();
            conn.prepareUpdate(classifyScammersQuery).execute();
            conn.commit();

            // Classify hommies as consumers that are not scammers
            String updateHommiesQuery = String.join("\n",
                    "PREFIX : <http://example.com/semanticMeasurements#>",
                    "INSERT {",
                    "  ?consumer a :Hommie .",
                    "}",
                    "WHERE {",
                    "  ?consumer a :Consumer .",
                    "  FILTER NOT EXISTS { ?consumer a :Scammer }",
                    "}"
            );
            conn.begin();
            conn.prepareUpdate(updateHommiesQuery).execute();
            conn.commit();

            // Fetch and print Scammers & Hommies
            String fetchClassificationQuery = String.join("\n",
                    "PREFIX : <http://example.com/semanticMeasurements#>",
                    "SELECT ?consumer ?class WHERE {",
                    "  VALUES ?class { :Scammer :Hommie }",
                    "  ?consumer a ?class .",
                    "}"
            );
            TupleQuery tq1 = conn.prepareTupleQuery(fetchClassificationQuery);
            StringBuilder sb1 = new StringBuilder();
            try (TupleQueryResult rs = tq1.evaluate()) {
                System.out.println("Consumer classifications:");
                while (rs.hasNext()) {
                    BindingSet bs = rs.next();
                    String line = String.format("- %s is %s",
                            bs.getValue("consumer"),
                            bs.getValue("class"));
                    System.out.println(line);
                    sb1.append(line).append("\n");
                }
            }
            // write results to file
            try (PrintWriter out = new PrintWriter(new FileWriter("classification_results.txt"))) {
                out.print(sb1.toString());
            }

            // Top 3 water & energy consumers
            String topConsumersQuery = String.join("\n",
                    "PREFIX : <http://example.com/semanticMeasurements#>",
                    "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>",
                    "SELECT ?consumer ?type (SUM(xsd:decimal(?cons)) AS ?totalConsumption) WHERE {",
                    "  {",
                    "    ?m a :WaterMeasurement ; :readingDate ?date ; :consumption ?cons ; :measuredBy ?meter .",
                    "    ?meter :ownedBy ?consumer .",
                    "    BIND(\"Water\" AS ?type)",
                    "  } UNION {",
                    "    ?m a :EnergyMeasurement ; :readingDate ?date ; :consumption ?cons ; :measuredBy ?meter .",
                    "    ?meter :ownedBy ?consumer .",
                    "    BIND(\"Energy\" AS ?type)",
                    "  }",
                    "}",
                    "GROUP BY ?consumer ?type",
                    "ORDER BY DESC(?totalConsumption)",
                    "LIMIT 3"
            );
            TupleQuery tq2 = conn.prepareTupleQuery(topConsumersQuery);
            StringBuilder sb2 = new StringBuilder();
            try (TupleQueryResult rs = tq2.evaluate()) {
                System.out.println("Top 3 consumers:");
                while (rs.hasNext()) {
                    BindingSet bs = rs.next();
                    String line = String.format("- %s (%s): %s",
                            bs.getValue("consumer"),
                            bs.getValue("type"),
                            bs.getValue("totalConsumption"));
                    System.out.println(line);
                    sb2.append(line).append("\n");
                }
            }
            try (PrintWriter out = new PrintWriter(new FileWriter("top_consumers_results.txt"))) {
                out.print(sb2.toString());
            }

            // Meters emitting measurements received by each gateway
            String gatewayMetersQuery = String.join("\n",
                    "PREFIX : <http://example.com/semanticMeasurements#>",
                    "SELECT DISTINCT ?gateway ?meter WHERE {",
                    "  ?measurement a :WaterMeasurement ;",
                    "               :receivedBy ?gateway ;",
                    "               :measuredBy ?meter .",
                    "}"
            );
            TupleQuery tq3 = conn.prepareTupleQuery(gatewayMetersQuery);
            StringBuilder sb3 = new StringBuilder();
            try (TupleQueryResult rs = tq3.evaluate()) {
                System.out.println("Meters emitting measurements received by each gateway:");
                while (rs.hasNext()) {
                    BindingSet bs = rs.next();
                    String line = String.format("- Gateway %s ← Meter %s",
                            bs.getValue("gateway"),
                            bs.getValue("meter"));
                    System.out.println(line);
                    sb3.append(line).append("\n");
                }
            }
            try (PrintWriter out = new PrintWriter(new FileWriter("gateway_meters_results.txt"))) {
                out.print(sb3.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Consumer classification failed.");
        }
    }
}
