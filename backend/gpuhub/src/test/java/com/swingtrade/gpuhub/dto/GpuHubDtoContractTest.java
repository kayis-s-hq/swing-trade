package com.swingtrade.gpuhub.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GpuHubDtoContractTest {
    @Test
    void containerTemplateDefaultsAndRequestBuilderPopulateApiFields() {
        ContainerTemplate template = ContainerTemplate.defaultTemplate();
        template.setDcList(List.of("sg"));
        template.setGpuNameSet(List.of("A100"));
        template.setImageUuid("image");
        template.setCmd("python app.py");
        CreateDeploymentRequest request = CreateDeploymentRequest.builder()
            .name("demo").deploymentType("ReplicaSet").replicaNum(2)
            .reuseContainer(true).reuseContainerScope("all").containerTemplate(template).build();

        assertThat(template.getGpuNum()).isEqualTo(1);
        assertThat(template.getCudaVFrom()).isEqualTo(118);
        assertThat(template.getCudaVTo()).isEqualTo(128);
        assertThat(template.getDcList()).containsExactly("sg");
        assertThat(template.getGpuNameSet()).containsExactly("A100");
        assertThat(template.getImageUuid()).isEqualTo("image");
        assertThat(request.getName()).isEqualTo("demo");
        assertThat(request.getDeploymentType()).isEqualTo("ReplicaSet");
        assertThat(request.getReplicaNum()).isEqualTo(2);
        assertThat(request.getReuseContainer()).isTrue();
        assertThat(request.getContainerTemplate()).isSameAs(template);
    }

    @Test
    void mutableDtosExposeAllFieldsAndSuccessWrapper() {
        ContainerInfo.Info info = new ContainerInfo.Info();
        info.setSshCommand("ssh");
        info.setRootPassword("secret");
        info.setService(Map.of("llm", "ready"));
        ContainerInfo container = new ContainerInfo();
        container.setUuid("c");
        container.setDataCenter("sg");
        container.setStatus("Running");
        container.setPrice(1.5);
        container.setCreatedAt("created");
        container.setStartedAt("started");
        container.setStoppedAt("stopped");
        container.setInfo(info);
        PrivateImage image = new PrivateImage();
        image.setId(1);
        image.setImageName("Qwen");
        image.setImageUuid("i");

        assertThat(container.getUuid()).isEqualTo("c");
        assertThat(container.getDataCenter()).isEqualTo("sg");
        assertThat(container.getStatus()).isEqualTo("Running");
        assertThat(container.getPrice()).isEqualTo(1.5);
        assertThat(container.getInfo().getSshCommand()).isEqualTo("ssh");
        assertThat(container.getInfo().getRootPassword()).isEqualTo("secret");
        assertThat(container.getInfo().getService()).containsEntry("llm", "ready");
        assertThat(image.getId()).isEqualTo(1);
        assertThat(image.getImageName()).isEqualTo("Qwen");
        assertThat(image.getImageUuid()).isEqualTo("i");
        GpuHubApiResponse<String> response = new GpuHubApiResponse<>();
        assertThat(response.isSuccess()).isFalse();
    }
}
