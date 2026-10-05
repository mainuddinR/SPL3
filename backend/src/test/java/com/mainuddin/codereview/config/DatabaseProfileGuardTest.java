package com.mainuddin.codereview.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseProfileGuardTest {
    @Test void normalRuntimeRequiresExactlyOneDatabaseProfile() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseProfileGuard.requireExplicitDatabaseProfile(new MockEnvironment()));
        assertDoesNotThrow(() -> DatabaseProfileGuard.requireExplicitDatabaseProfile(
                new MockEnvironment().withProperty("spring.profiles.active", "oracle")));
        assertDoesNotThrow(() -> DatabaseProfileGuard.requireExplicitDatabaseProfile(
                new MockEnvironment().withProperty("spring.profiles.active", "h2")));
        assertThrows(IllegalStateException.class, () -> DatabaseProfileGuard.requireExplicitDatabaseProfile(
                new MockEnvironment().withProperty("spring.profiles.active", "h2,oracle")));
    }
}
