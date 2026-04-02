package com.ovaledge.csp.apps.${artifactId}.main;

import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.QueryResponse;
import com.ovaledge.csp.v3.core.apps.service.QueryService;

public class ${classPrefix}QueryService implements QueryService {

    @Override
    public QueryResponse fetchData(QueryRequest request) {
        /*
         * Implement fetchData to execute a query for the requested object.
         *
         * Responsibilities:
         * - Validate the incoming QueryRequest (fields, filters, limit, offset).
         * - Acquire a connection using your connector's connection config (use ConnectionPoolManager where available).
         * - Execute the query and map database rows into the QueryResponse rows/columns format expected by CSP.
         * - Set withSuccess(true/false), withMessage(...) and include any rows/metadata.
         *
         * Example (pseudocode):
         *   QueryResponse resp = new QueryResponse();
         *   List<Map<String,Object>> rows = executeQuery(sql, params);
         *   resp.withRows(rows).withSuccess(true);
         *   return resp;
         */
        return new QueryResponse()
                .withSuccess(false)
                .withMessage("Query execution not implemented for this connector.");
    }
}
