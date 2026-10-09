package com.mainuddin.codereview.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.service.OpenAiSuggestionClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DesignAdviceProviderTest {
    @Test void designPromptUsesExistingGroqAdapterAndNeverClaimsCodeBertClassifiedIt() {
        var client = new OpenAiSuggestionClient(new ObjectMapper(), "test-key", "openai/gpt-oss-20b", "https://api.groq.com/openai/v1", false, 1);
        var http = new RestTemplate(); ReflectionTestUtils.setField(client, "http", http);
        var server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("https://api.groq.com/openai/v1/responses"))
                .andExpect(request -> {
                    var body = new ObjectMapper().readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString());
                    assertTrue(body.path("instructions").asText().contains("not a SATD prediction"));
                    assertFalse(body.path("instructions").asText().contains("CodeBERT already classified"));
                    assertTrue(body.path("text").path("format").path("strict").asBoolean());
                    assertEquals("low", body.path("reasoning").path("effort").asText());
                })
                .andRespond(withSuccess("{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"summary\\\":\\\"Summary\\\",\\\"risk\\\":\\\"Coupling\\\",\\\"recommendation\\\":\\\"Review service boundary\\\",\\\"verification\\\":\\\"Test routing\\\",\\\"limitations\\\":\\\"Partial context\\\"}\"}]}]}", MediaType.APPLICATION_JSON));
        assertEquals("Review service boundary", client.generateDesign("saved deterministic evidence").recommendation()); server.verify();
    }
}
