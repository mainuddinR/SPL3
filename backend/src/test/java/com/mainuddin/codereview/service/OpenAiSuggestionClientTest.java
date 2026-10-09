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
    private final OpenAiSuggestionClient client = new OpenAiSuggestionClient(
            new ObjectMapper(), "test-key", "test-model", "https://api.openai.com/v1", true, 1);
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
            assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(
                    OpenAiSuggestionClient.SuggestionProviderException.class, () -> client.generate("excerpt")).reasonCode());
            server.verify();
        }
        for (var status : new org.springframework.http.HttpStatus[] {
                org.springframework.http.HttpStatus.UNAUTHORIZED, org.springframework.http.HttpStatus.FORBIDDEN,
                org.springframework.http.HttpStatus.NOT_FOUND, org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                org.springframework.http.HttpStatus.BAD_REQUEST, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR }) {
            var server = server();
            server.expect(requestTo("https://api.openai.com/v1/responses"))
                    .andRespond(withStatus(status).body("private provider details"));
            var error = assertThrows(OpenAiSuggestionClient.SuggestionProviderException.class, () -> client.generate("excerpt"));
            assertEquals(switch (status) {
                case UNAUTHORIZED, FORBIDDEN -> "AUTHENTICATION_FAILED";
                case NOT_FOUND -> "MODEL_UNAVAILABLE";
                case TOO_MANY_REQUESTS -> "RATE_LIMITED";
                case BAD_REQUEST -> "INVALID_REQUEST";
                default -> "PROVIDER_UNAVAILABLE";
            }, error.reasonCode());
            assertFalse(error.getMessage().contains("private"));
            server.verify();
        }
    }

    @Test void transientStructuredGenerationErrorRetriesExactlyOnceAndKeepsBodyPrivate() {
        var server = server();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"json_validate_failed\",\"message\":\"private source code\",\"failed_generation\":\"do not log this\"}}"));
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"summary\\\":\\\"Summary\\\",\\\"risk\\\":\\\"Risk\\\",\\\"recommendation\\\":\\\"Review method\\\",\\\"verification\\\":\\\"Run tests\\\",\\\"limitations\\\":\\\"More context needed\\\"}\"}]}]}", MediaType.APPLICATION_JSON));
        assertEquals("Review method", client.generate("saved excerpt").recommendation());
        server.verify();
    }

    @Test void other400NeverTriggersAutomaticRetry() {
        var server = server();
        server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"invalid_request_error\",\"message\":\"private source code\"}}"));
        var error = assertThrows(OpenAiSuggestionClient.SuggestionProviderException.class,
                () -> client.generate("saved excerpt"));
        assertEquals("INVALID_REQUEST", error.reasonCode());
        assertFalse(error.getMessage().contains("private"));
        server.verify();
    }

    @Test void groqGptOssUsesLowReasoningAndMoreOutputRoom() {
        var groq = new OpenAiSuggestionClient(new ObjectMapper(), "test-key",
                "openai/gpt-oss-20b", "https://api.groq.com/openai/v1", false, 1);
        var mockedHttp = new RestTemplate();
        ReflectionTestUtils.setField(groq, "http", mockedHttp);
        var server = MockRestServiceServer.bindTo(mockedHttp).build();
        server.expect(requestTo("https://api.groq.com/openai/v1/responses"))
                .andExpect(jsonPath("$.reasoning.effort").value("low"))
                .andExpect(jsonPath("$.max_output_tokens").value(1200))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andRespond(withSuccess("{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"summary\\\":\\\"Summary\\\",\\\"risk\\\":\\\"Risk\\\",\\\"recommendation\\\":\\\"Review method\\\",\\\"verification\\\":\\\"Run tests\\\",\\\"limitations\\\":\\\"More context needed\\\"}\"}]}]}", MediaType.APPLICATION_JSON));
        assertEquals("Review method", groq.generate("saved excerpt").recommendation());
        server.verify();
    }

    @Test void timeoutHasSafeCategory() {
        var server = server();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withException(new java.net.SocketTimeoutException("private transport details")));
        var error = assertThrows(OpenAiSuggestionClient.SuggestionProviderException.class,
                () -> client.generate("excerpt"));
        assertEquals("TIMEOUT", error.reasonCode());
        assertFalse(error.getMessage().contains("private"));
        server.verify();
    }

    @Test void rateLimitExposesOnlyBoundedRetrySeconds() {
        var server = server();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "40").body("private provider body"));
        var error = assertThrows(OpenAiSuggestionClient.SuggestionProviderException.class,
                () -> client.generate("excerpt"));
        assertEquals("RATE_LIMITED", error.reasonCode());
        assertEquals(40, error.retryAfterSeconds());
        assertFalse(error.getMessage().contains("private"));
        server.verify();
    }
}
