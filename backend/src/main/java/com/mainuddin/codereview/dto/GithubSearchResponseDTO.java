package com.mainuddin.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GithubSearchResponseDTO {
    @JsonProperty("total_count")
    private Integer totalCount;
    
    @JsonProperty("incomplete_results")
    private Boolean incompleteResults;
    
    private List<GithubRepoDTO> items;
}
