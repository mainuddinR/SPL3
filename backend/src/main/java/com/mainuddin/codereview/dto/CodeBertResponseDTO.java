package com.mainuddin.codereview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeBertResponseDTO {
    
    @JsonProperty("label")
    private String label;
    
    @JsonProperty("satd_probability")
    private Double satdProbability;
    
    @JsonProperty("non_satd_probability")
    private Double nonSatdProbability;
    
    @JsonProperty("confidence")
    private Double confidence;
}
