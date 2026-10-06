package com.mainuddin.codereview.dto;

import java.util.List;
import java.util.Set;

public record RepositoryPageDTO(List<GithubRepoDTO> repositories, Set<Long> storedRepositoryIds,
                                int page, boolean hasNext) {}
