package com.mainuddin.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatdCandidateDTO {
    private String commentText;
    private String surroundingCode;
    private String filename;
    private String language;
    private Integer lineNumber;
}
