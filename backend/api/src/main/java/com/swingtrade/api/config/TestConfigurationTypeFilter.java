package com.swingtrade.api.config;

import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

/**
 * Keeps test-only Spring configuration out of production component scanning
 * without putting the Spring test artifact on the application classpath.
 */
public class TestConfigurationTypeFilter implements TypeFilter {

    private static final String TEST_CONFIGURATION = "org.springframework.boot.test.context.TestConfiguration";

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
        AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();
        return metadata.hasAnnotation(TEST_CONFIGURATION) || metadata.hasMetaAnnotation(TEST_CONFIGURATION);
    }
}
