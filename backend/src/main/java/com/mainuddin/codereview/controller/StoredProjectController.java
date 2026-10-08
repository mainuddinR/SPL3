package com.mainuddin.codereview.controller;

import com.mainuddin.codereview.service.StoredProjectDeletionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
public class StoredProjectController {
    private final StoredProjectDeletionService deletion;
    private final com.mainuddin.codereview.repository.GithubProjectRepository projects;
    public StoredProjectController(StoredProjectDeletionService deletion, com.mainuddin.codereview.repository.GithubProjectRepository projects) { this.deletion = deletion; this.projects = projects; }

    public record StoredProject(Long id, Long githubRepoId, String fullName) {}
    @GetMapping
    public java.util.List<StoredProject> stored() {
        return projects.findByUserGithubIdOrderByIdAsc(SecurityContextHolder.getContext().getAuthentication().getName())
                .stream().map(p -> new StoredProject(p.getId(), p.getGithubRepoId(), p.getFullName())).toList();
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId) {
        try {
            deletion.delete(projectId, SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (com.mainuddin.codereview.exception.ResourceNotFoundException | org.springframework.web.server.ResponseStatusException expected) {
            throw expected;
        } catch (RuntimeException failure) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Repository could not be removed. Please try again.");
        }
        return ResponseEntity.noContent().build();
    }
}
