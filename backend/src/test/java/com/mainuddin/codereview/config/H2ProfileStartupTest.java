package com.mainuddin.codereview.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("h2")
class H2ProfileStartupTest {
    @Autowired DataSource dataSource;
    @Autowired Environment environment;

    @Test void isolatedLocalProfileUsesInMemoryH2AndConsole() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:devdb"));
        }
        assertEquals("true", environment.getProperty("spring.h2.console.enabled"));
        assertEquals("create-drop", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }
}
