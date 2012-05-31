package org.neo4j.community.console;

import com.google.gson.Gson;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.neo4j.geoff.except.SubgraphError;
import org.neo4j.geoff.except.SyntaxError;
import org.neo4j.graphdb.*;
import org.neo4j.rest.graphdb.RestAPI;
import org.neo4j.rest.graphdb.RestGraphDatabase;
import org.neo4j.rest.graphdb.query.RestCypherQueryEngine;
import org.neo4j.rest.graphdb.util.QueryResult;
import org.neo4j.test.ImpermanentGraphDatabase;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.neo4j.helpers.collection.MapUtil.map;

/**
* @author mh
* @since 08.04.12
*/
class Neo4jService {
    private GraphDatabaseService gdb = new ImpermanentGraphDatabase();

    private Index index = new Index(gdb);
    private CypherQueryExecutor cypherQueryExecutor = new CypherQueryExecutor(gdb,index);
    private GeoffImportService geoffService = new GeoffImportService(gdb, index);
    private GeoffExportService geoffExportService = new GeoffExportService(gdb);
    private CypherExportService cypherExportService = new CypherExportService(gdb);
    private String version;

    public Map cypherQueryViz(String query) {
        final boolean invalidQuery = query == null || query.trim().isEmpty() || cypherQueryExecutor.isMutatingQuery(query);
        return invalidQuery ? cypherQueryViz((CypherQueryExecutor.CypherResult) null) : cypherQueryViz(cypherQuery(query));
    }
    public Map cypherQueryViz(CypherQueryExecutor.CypherResult result) {
        final SubGraph subGraph = SubGraph.from(gdb).markSelection(result);
        return map("nodes", subGraph.getNodes().values(), "links", subGraph.getRelationships().values());
    }

    public String exportToGeoff() {
        return geoffExportService.export();
    }
    
    public String exportToCypher() {
        return cypherExportService.export();
    }

    public Map mergeGeoff(String geoff) {
        try {
            return geoffService.mergeGeoff(geoff);
        } catch (SubgraphError subgraphError) {
            throw new RuntimeException("Error merging:\n"+geoff,subgraphError);
        } catch (SyntaxError syntaxError) {
            throw new RuntimeException("Syntax error merging:\n"+geoff,syntaxError);
        }
    }

    public Collection<Map<String,Object>> cypherQueryResults(String query) {
        Collection<Map<String,Object>> result=new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : cypherQuery(query)) {
            result.add(row);
        }
        return result;
    }

    public CypherQueryExecutor.CypherResult initCypherQuery(String query) {
        return cypherQueryExecutor.cypherQuery(query,null);
    }
    public CypherQueryExecutor.CypherResult cypherQuery(String query) {
        return cypherQueryExecutor.cypherQuery(query,version);
    }

    public void stop() {
        if (gdb!=null) {
            System.err.println("Shutting down service "+this);
            gdb.shutdown();
            index = null;
            cypherQueryExecutor=null;
            geoffExportService =null;
            cypherExportService =null;
            geoffService =null;
            gdb=null;
        }
    }

    public void deleteReferenceNode() {
        final Node root = gdb.getReferenceNode();
        if (root!=null) {
            final Transaction tx = gdb.beginTx();
            try {
                root.delete();
                tx.success();
            } finally {
                tx.finish();
            }
        }
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        if (version==null || version.trim().isEmpty()) this.version=null;
        else {
            version = version.replaceAll("^(\\d+\\.\\d+).*","$1");
            if (!version.matches("\\d+\\.\\d+")) throw new IllegalArgumentException("Incorrect version string "+version);
            this.version = version;
        }
    }

    public boolean hasReferenceNode() {
        try {
            return gdb.getReferenceNode() != null;
        } catch (NotFoundException nfe) {
            return false;
        }
    }

    public boolean isMutatingQuery(String query) {
        return cypherQueryExecutor.isMutatingQuery(query);
    }
    public boolean isCypherQuery(String query) {
        return cypherQueryExecutor.isCypherQuery(query);
    }

    public GraphDatabaseService getGraphDatabase() {
        return gdb;
    }

    public void importGraph(SubGraph graph) {
        final Transaction tx = gdb.beginTx();
        try {
            graph.importTo(gdb, hasReferenceNode());
            tx.success();
        } finally {
            tx.finish();
        }
    }

    public URL toUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            return null;
        }
    }
}
