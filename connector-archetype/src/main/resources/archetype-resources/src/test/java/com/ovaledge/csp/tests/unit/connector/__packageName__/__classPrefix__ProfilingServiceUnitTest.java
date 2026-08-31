package com.ovaledge.csp.tests.unit.connector.${packageName};

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ovaledge.csp.apps.${packageName}.main.${classPrefix}ProfilingService;
import com.ovaledge.csp.v3.core.apps.exceptions.ProfilingUnsupportedException;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.request.FileProfileRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileRowCountRequest;
import com.ovaledge.csp.v3.core.apps.model.request.SampleProfileRequest;
import com.ovaledge.csp.v3.core.apps.model.response.ProfileColumnResult;
import com.ovaledge.csp.v3.core.apps.model.response.FileProfileResponse;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AUTO-GENERATED TEST TEMPLATE for ProfilingService.
 *
 * <p>TODO: Replace skeleton assertions with connector-specific profiling tests.
 */
@ExtendWith(MockitoExtension.class)
class ${classPrefix}ProfilingServiceUnitTest {

${profilingServiceUnitTestBody}
}
