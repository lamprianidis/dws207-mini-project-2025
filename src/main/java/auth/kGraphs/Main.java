package auth.kGraphs;

import auth.kGraphs.rml.RmlMapper;

public class Main {
    public static void main(String[] args) {
        // Execute RML mapping
        boolean success = RmlMapper.map();
        System.out.println("RML mapping was " + (success? "successful" : "unsuccessful") + "." );
    }
}