package com.mainuddin.codereview.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class DatabaseProfileGuard {
    @Bean
    static BeanFactoryPostProcessor requireExplicitDatabaseProfile(Environment environment) {
        // Tests supply their own isolated H2 datasource via src/test/resources/application.yml.
        if (!environment.getProperty("app.database.test-mode", Boolean.class, false)) {
            boolean h2 = environment.acceptsProfiles(Profiles.of("h2"));
            boolean oracle = environment.acceptsProfiles(Profiles.of("oracle"));
            if (h2 == oracle) {
                throw new IllegalStateException("Select exactly one database profile: h2 or oracle");
            }
        }
        return beanFactory -> { };
    }
}
