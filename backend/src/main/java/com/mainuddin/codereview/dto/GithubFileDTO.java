package com.mainuddin.codereview.dto;

import lombok.Data;

@Data
public class GithubFileDTO {
    private String sha;
    private String filename;
    private String status;
    private Integer additions;
    private Integer deletions;
    private Integer changes;
    private String patch;
}
