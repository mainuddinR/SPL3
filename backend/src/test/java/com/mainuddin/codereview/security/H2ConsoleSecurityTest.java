package com.mainuddin.codereview.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:h2_console_security_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.h2.console.enabled=true"
})
@AutoConfigureMockMvc
class H2ConsoleSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void consoleAllowsSameOriginFramesWhileNormalApiStaysProtected() throws Exception {
        var console = mvc.perform(get("/h2-console/login.jsp"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andReturn().getResponse();
        // MockMvc does not dispatch to the H2 servlet; a 404 here means security let it through.
        assertNotEquals(302, console.getStatus());

        var api = mvc.perform(get("/api/prs/1"))
                .andReturn().getResponse();
        assertNotEquals(200, api.getStatus());
    }
}
