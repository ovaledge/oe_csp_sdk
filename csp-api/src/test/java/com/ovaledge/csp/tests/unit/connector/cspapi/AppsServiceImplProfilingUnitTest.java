package com.ovaledge.csp.tests.unit.connector.cspapi;

import com.ovaledge.csp.apps.app.service.AppsRegistry;
import com.ovaledge.csp.apps.app.service.AppsServiceImpl;
import com.ovaledge.csp.v3.core.apps.exceptions.ProfilingUnsupportedException;
import com.ovaledge.csp.v3.core.apps.model.request.FileProfileRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileBatchRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileColumnRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileRowCountRequest;
import com.ovaledge.csp.v3.core.apps.model.request.SampleProfileRequest;
import com.ovaledge.csp.v3.core.apps.service.AppsConnector;
import com.ovaledge.csp.v3.core.apps.service.ProfilingService;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppsServiceImplProfilingUnitTest {

    @Mock
    private AppsRegistry appsRegistry;
    @Mock
    private AppsConnector connector;
    @Mock
    private ProfilingService profilingService;
    @InjectMocks
    private AppsServiceImpl appsService;

    private ConnectionConfig config;

    @BeforeEach
    void setUp() {
        config = new ConnectionConfig().withServerType("quickbooks");
        when(appsRegistry.getConnector("quickbooks")).thenReturn(connector);
    }

    @Test
    void getRowCount_whenProfilingServiceNull_throwsProfilingUnsupportedException() {
        when(connector.getProfilingService()).thenReturn(null);
        ProfileRowCountRequest request = new ProfileRowCountRequest();
        request.setConnectionConfig(config);

        ProfilingUnsupportedException ex = assertThrows(
                ProfilingUnsupportedException.class, () -> appsService.getRowCount(request));
        assertEquals("Profiling is not supported for serverType: quickbooks", ex.getMessage());
    }

    @Test
    void profileColumn_whenProfilingServiceNull_throwsProfilingUnsupportedException() {
        when(connector.getProfilingService()).thenReturn(null);
        ProfileColumnRequest request = new ProfileColumnRequest();
        request.setConnectionConfig(config);

        assertThrows(ProfilingUnsupportedException.class, () -> appsService.profileColumn(request));
    }

    @Test
    void sampleProfile_whenProfilingServiceNull_throwsProfilingUnsupportedException() {
        when(connector.getProfilingService()).thenReturn(null);
        SampleProfileRequest request = new SampleProfileRequest();
        request.setConnectionConfig(config);

        assertThrows(ProfilingUnsupportedException.class, () -> appsService.sampleProfile(request));
    }

    @Test
    void profileBatch_whenProfilingServiceNull_throwsProfilingUnsupportedException() {
        when(connector.getProfilingService()).thenReturn(null);
        ProfileBatchRequest request = new ProfileBatchRequest();
        request.setConnectionConfig(config);

        assertThrows(ProfilingUnsupportedException.class, () -> appsService.profileBatch(request));
    }

    @Test
    void profileFile_whenProfilingServiceNull_throwsProfilingUnsupportedException() {
        when(connector.getProfilingService()).thenReturn(null);
        FileProfileRequest request = new FileProfileRequest();
        request.setConnectionConfig(config);

        assertThrows(ProfilingUnsupportedException.class, () -> appsService.profileFile(request));
    }

    @Test
    void getRowCount_whenServicePresent_delegates() {
        when(connector.getProfilingService()).thenReturn(profilingService);
        ProfileRowCountRequest request = new ProfileRowCountRequest();
        request.setConnectionConfig(config);
        when(profilingService.getRowCount(request)).thenReturn(42L);

        assertEquals(42L, appsService.getRowCount(request));
        verify(profilingService).getRowCount(request);
    }
}
