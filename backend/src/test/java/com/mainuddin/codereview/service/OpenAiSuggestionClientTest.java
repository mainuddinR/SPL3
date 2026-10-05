package com.mainuddin.codereview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiSuggestionClientTest {
    private final OpenAiSuggestionClient client = new OpenAiSuggestionClient(new ObjectMapper(), "test-key", "test-model", 1);
    private final RestTemplate http = new RestTemplate();

    private MockRestServiceServer server() {
        ReflectionTestUtils.setField(client, "http", http);
        return MockRestServiceServer.bindTo(http).build();
    }

    @Test void strictRequestAndStructuredSuccess() {
        var server = server();
        server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andRespond(withSuccess("{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"summary\\\":\\\"Summary\\\",\\\"risk\\\":\\\"Risk\\\",\\\"recommendation\\\":\\\"Review method\\\",\\\"verification\\\":\\\"Run tests\\\",\\\"limitations\\\":\\\"More context needed\\\"}\"}]}]}", MediaType.APPLICATION_JSON));
        assertEquals("Review method", client.generate("saved excerpt").recommendation());
        server.verify();
    }

    @Test void refusalMalformedOversizeAndProviderErrorsStaySafe() {
        for (String body : new String[] {"{\"status\":\"completed\",\"output\":[]}", "not json", "x".repeat(25 * 1024)}) {
            var server = server();
            server.expect(requestTo("https://api.openai.com/v1/responses"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertEquals("Suggestion provider unavailable", assertThrows(
                    OpenAiSuggestionClient.SuggestionProviderException.class, () -> client.generate("excerpt")).getMessage());
            server.verify();
        }
        for (var status : new org.springframework.http.HttpStatus[] {
                org.springframework.http.HttpStatus.UNAUTHORIZED, org.springframework.http.HttpStatus.FORBIDDEN,
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR }) {
            var server = server();
            server.expect(requestTo("https://api.openai.com/v1/responses"))
                    .andRespond(withStatus(status).body("private provider details"));
            var error = assertThrows(OpenAiSuggestionClient.SuggestionProviderException.class, () -> client.generate("excerpt"));
            assertFalse(error.getMessage().contains("private"));
            server.verify();
        }
    }
}
