package com.swingtrade.gpuhub.service;

import com.swingtrade.gpuhub.client.GpuHubDeploymentClient;
import com.swingtrade.gpuhub.dto.ContainerInfo;
import com.swingtrade.gpuhub.dto.CreateDeploymentResponse;
import com.swingtrade.gpuhub.dto.DeploymentInfo;
import com.swingtrade.gpuhub.dto.PrivateImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GpuHubDeploymentServiceTest {
    @Mock private GpuHubDeploymentClient client;

    @Test
    void createBuildsTheExpectedDeploymentRequest() {
        CreateDeploymentResponse response = new CreateDeploymentResponse();
        when(client.createDeployment(any())).thenReturn(Mono.just(response));

        assertThat(new GpuHubDeploymentService(client).create("demo", "sg", "A100",
            "image", 2, true, "python app.py")).isSameAs(response);
        verify(client).createDeployment(any());
    }

    @Test
    void delegatesAllBlockingOperationsAndRunningStatus() throws Exception {
        DeploymentInfo running = deployment("Running");
        ContainerInfo container = new ContainerInfo();
        PrivateImage image = new PrivateImage();
        when(client.listDeployments(1, 10)).thenReturn(Mono.just(List.of(running)));
        when(client.listDeploymentsByUuid("d")).thenReturn(Mono.just(List.of(running)));
        when(client.listContainers("d")).thenReturn(Mono.just(List.of(container)));
        when(client.listPrivateImages(1, 10)).thenReturn(Mono.just(List.of(image)));
        when(client.stopDeployment("d")).thenReturn(Mono.empty());
        when(client.deleteDeployment("d")).thenReturn(Mono.empty());
        when(client.stopContainer("c")).thenReturn(Mono.empty());

        GpuHubDeploymentService service = new GpuHubDeploymentService(client);
        assertThat(service.list(1, 10)).containsExactly(running);
        assertThat(service.status("d")).isSameAs(running);
        assertThat(service.listContainers("d")).containsExactly(container);
        assertThat(service.listPrivateImages(1, 10)).containsExactly(image);
        service.stop("d");
        service.delete("d");
        service.stopContainer("c");
        assertThat(service.isRunning("d")).isTrue();
    }

    @Test
    void statusAndRunningAreSafeForMissingOrInactiveDeployments() throws Exception {
        when(client.listDeploymentsByUuid("missing")).thenReturn(Mono.just(List.of()));
        when(client.listDeploymentsByUuid("stopped")).thenReturn(Mono.just(List.of(deployment("Stopped"))));
        GpuHubDeploymentService service = new GpuHubDeploymentService(client);

        assertThat(service.status("missing")).isNull();
        assertThat(service.isRunning("missing")).isFalse();
        assertThat(service.isRunning("stopped")).isFalse();
    }

    private static DeploymentInfo deployment(String status) throws Exception {
        var info = new tools.jackson.databind.ObjectMapper().readValue(
            "{\"deployment_uuid\":\"d\",\"status\":\"" + status + "\"}", DeploymentInfo.class);
        return info;
    }
}
