package com.valor.auth;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Test-classpath only. H2 supports generated expressions and unique keys, but not MySQL's STORED keyword.
 * The real V3 file is never edited. H2's migration checksum differs intentionally; no MySQL verification is implied.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "org.h2.Driver")
class H2WorkflowMigrationAdapter {
    @Bean
    FlywayConfigurationCustomizer h2WorkflowResources() throws IOException {
        List<LoadableResource> resources = new ArrayList<>();
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
            String name = resource.getFilename();
            String source;
            try (var input = resource.getInputStream()) { source = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            String sql = name.equals("V3__create_service_workflow.sql") ? source.replace(") STORED;", ");") : source;
            resources.add(new LoadableResource() {
                public Reader read() { return new StringReader(sql); }
                public String getFilename() { return name; }
                public String getRelativePath() { return name; }
                public String getAbsolutePath() { return "db/migration/" + name; }
                public String getAbsolutePathOnDisk() { return null; }
            });
        }
        return configuration -> configuration.resourceProvider(new ResourceProvider() {
            public LoadableResource getResource(String name) {
                return resources.stream().filter(r -> r.getFilename().equals(name)).findFirst().orElse(null);
            }
            public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
                return resources.stream().filter(r -> r.getFilename().startsWith(prefix)
                        && Arrays.stream(suffixes).anyMatch(suffix -> r.getFilename().endsWith(suffix))).toList();
            }
        });
    }
}
