package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.CodeBertRequestDTO;
import com.mainuddin.codereview.dto.CodeBertResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class CodeBertClientService {

    private final RestClient restClient;
    private final String mlServiceUrl;

    public CodeBertClientService(RestClient restClient, @Value("${app.ml-service.url}") String mlServiceUrl) {
        this.restClient = restClient;
        this.mlServiceUrl = mlServiceUrl;
    }

    public CodeBertResponseDTO analyzeCandidate(CodeBertRequestDTO request) {
        try {
            return restClient.post()
                    .uri(mlServiceUrl + "/api/v1/satd-detect")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CodeBertResponseDTO.class);
        } catch (RestClientException e) {
            log.error("Failed to analyze SATD candidate via CodeBERT ML Service", e);
            throw new RuntimeException("CodeBERT ML Service error: " + e.getMessage(), e);
        }
    }
}
