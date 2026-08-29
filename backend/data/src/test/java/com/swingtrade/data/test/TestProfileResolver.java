package com.swingtrade.data.test;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * Profile resolver that defaults to "test" but allows override via system property.
 * This allows running unit tests with H2 (default) or E2E tests with PostgreSQL.
 */
public class TestProfileResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        // Check if profile was explicitly set via system property
        String profile = System.getProperty("spring.profiles.active");
        if (profile != null && !profile.isEmpty()) {
            // Support multiple profiles (comma-separated)
            return profile.split(",");
        }
        // Default to "test" profile (H2 in-memory)
        return new String[]{"test"};
    }
}
