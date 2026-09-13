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
public class CodeBertRequestDTO {
    
    @JsonProperty("comment")
    private String comment;
    
    @JsonProperty("preceding_code")
    private String precedingCode;
    
    @JsonProperty("succeeding_code")
    private String succeedingCode;
}
