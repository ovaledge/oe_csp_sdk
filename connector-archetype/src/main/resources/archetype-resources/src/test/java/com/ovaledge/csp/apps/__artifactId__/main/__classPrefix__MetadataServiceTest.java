package com.ovaledge.csp.apps.${packageName}.main;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ovaledge.csp.v3.core.apps.model.response.ContainersResponse;
import com.ovaledge.csp.v3.core.apps.model.response.FieldsResponse;
import com.ovaledge.csp.v3.core.apps.model.response.ObjectResponse;
import com.ovaledge.csp.v3.core.apps.model.response.SupportedObjectsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AUTO-GENERATED TEST TEMPLATE.
 *
 * <p>TODO: Replace skeleton assertions with connector-specific metadata tests.
 */
@ExtendWith(MockitoExtension.class)
class ${classPrefix}MetadataServiceTest {

    private final ${classPrefix}MetadataService metadataService = new ${classPrefix}MetadataService();

    @Test
    void getSupportedObjects_returnsSuccessfulResponse() {
        SupportedObjectsResponse response = metadataService.getSupportedObjects();
        assertNotNull(response);
        assertTrue(response.getSuccess());
    }

    @Test
    void getContainers_returnsDefaultNotImplementedResponse() {
        ContainersResponse response = metadataService.getContainers(null);
        assertNotNull(response);
        assertFalse(response.getSuccess());
    }

    @Test
    void getObjects_returnsDefaultNotImplementedResponse() {
        ObjectResponse response = metadataService.getObjects(null);
        assertNotNull(response);
        assertFalse(response.getSuccess());
    }

    @Test
    void getFields_returnsDefaultResponse() {
        FieldsResponse response = metadataService.getFields(null);
        assertNotNull(response);
        assertTrue(response.getSuccess());
    }

    // TODO: add tests for getContainers_returnsContainerHierarchyForObjectKind.
    // TODO: add tests for getObjects_returnsObjectsForContainer.
    // TODO: add tests for getFields_returnsFieldMetadataForObject.
    // TODO: for each future void method, create a TODO-only skeleton test:
    //       void <methodName>_<expectedBehavior>() { /* TODO implement */ }
}
