package com.ovaledge.csp.apps.${artifactId}.main;

import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.SupportedObject;
import com.ovaledge.csp.v3.core.apps.model.request.ContainersRequest;
import com.ovaledge.csp.v3.core.apps.model.request.FieldsRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.response.ContainersResponse;
import com.ovaledge.csp.v3.core.apps.model.response.FieldsResponse;
import com.ovaledge.csp.v3.core.apps.model.response.ObjectResponse;
import com.ovaledge.csp.v3.core.apps.model.response.SupportedObjectsResponse;
import com.ovaledge.csp.v3.core.apps.service.MetadataService;
import java.util.ArrayList;
import java.util.List;

public class ${classPrefix}MetadataService implements MetadataService {

    @Override
    public SupportedObjectsResponse getSupportedObjects() {
        List<SupportedObject> types = new ArrayList<>();
        types.add(new SupportedObject(ObjectKind.ENTITY.value(), "Entity", "${classPrefix} entity"));
        return new SupportedObjectsResponse()
                .withSupportedObjects(types)
                .withSuccess(true);
    }

    @Override
    public ContainersResponse getContainers(ContainersRequest request) {
        /*
         * Implement getContainers to return top-level containers for the requested object kind.
         *
         * Examples:
         * - For a database connector: return list of schemas or databases
         * - For an API connector: return list of workspaces, accounts or projects
         *
         * Notes:
         * - Use request parameters (filters, paging) where applicable.
         * - Return withSuccess(true) when containers are found, otherwise withSuccess(false) and a helpful message.
         */
        return new ContainersResponse()
                .withContainers(new ArrayList<>())
                .withSuccess(false)
                .withMessage("Containers are not implemented for this connector.");
    }

    @Override
    public ObjectResponse getObjects(ObjectRequest request) {
        /*
         * Implement getObjects to list objects for a container and object kind.
         *
         * Responsibilities:
         * - Inspect request.getObjectKind() and request.getContainer() to build the query or API call.
         * - Return objects with identifiers and display names expected by the UI.
         */
        return new ObjectResponse()
                .withChildren(new ArrayList<>())
                .withSuccess(false)
                .withMessage("Objects are not implemented for this connector.");
    }

    @Override
    public FieldsResponse getFields(FieldsRequest request) {
        /*
         * Implement getFields to return field/column metadata for the requested object.
         *
         * Guidance:
         * - Map connector native types to the UI-friendly field descriptors.
         * - Include field id, name, data type, and any searchable/filterable flags.
         */
        return new FieldsResponse()
                .withFields(new ArrayList<>())
                .withSuccess(true)
                .withMessage("Fields are not implemented for this connector.");
    }
}
