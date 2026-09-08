package com.ovaledge.csp.apps.${packageName}.main;

import com.ovaledge.csp.v3.core.apps.exceptions.ProfilingException;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.request.FileProfileRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileColumnRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileRowCountRequest;
import com.ovaledge.csp.v3.core.apps.model.request.SampleProfileRequest;
import com.ovaledge.csp.v3.core.apps.model.response.ProfileColumnResult;
import com.ovaledge.csp.v3.core.apps.model.response.FileProfileResponse;
import com.ovaledge.csp.v3.core.apps.service.AbstractProfilingService;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AUTO-GENERATED profiling service scaffold.
 *
 * <p>TODO: Replace stubs with connector-specific profiling logic.
 * Helpers available in SDK core: {@code JdbcProfilingSqlUtils}, {@code ProfileStatsCalculator}.
 */
public class ${classPrefix}ProfilingService extends AbstractProfilingService {

${profilingServiceMethods}
    private static void validateIdentity(ConnectionConfig config, String containerId, String entityId) {
        if (config == null) {
            throw new ProfilingException("connectionConfig is required");
        }
        if (containerId == null || containerId.isBlank() || entityId == null || entityId.isBlank()) {
            throw new ProfilingException("containerId and entityId are required");
        }
    }
}
