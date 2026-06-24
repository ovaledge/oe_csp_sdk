package com.ovaledge.csp.apps.${packageName}.main;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ovaledge.csp.v3.core.apps.model.response.QueryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AUTO-GENERATED TEST TEMPLATE.
 *
 * <p>TODO: Replace skeleton assertions with connector-specific query execution tests.
 */
@ExtendWith(MockitoExtension.class)
class ${classPrefix}QueryServiceTest {

    private final ${classPrefix}QueryService queryService = new ${classPrefix}QueryService();

    @Test
    void fetchData_returnsDefaultNotImplementedResponse() {
        QueryResponse response = queryService.fetchData(null);
        assertNotNull(response);
        assertFalse(response.getSuccess());
    }

    // TODO: add tests for fetchData_returnsRowsForValidQueryRequest.
    // TODO: add tests for fetchData_returnsValidationFailureForInvalidRequest.
    // TODO: for each future void method, create a TODO-only skeleton test:
    //       void <methodName>_<expectedBehavior>() { /* TODO implement */ }
}
