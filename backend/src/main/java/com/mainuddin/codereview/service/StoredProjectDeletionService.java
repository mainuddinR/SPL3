package com.mainuddin.codereview.service;

import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Deletes application-owned data only. This service has no GitHub client. */
@Service
public class StoredProjectDeletionService {
    private final GithubProjectRepository projects;
    private final EntityManager em;

    public StoredProjectDeletionService(GithubProjectRepository projects, EntityManager em) {
        this.projects = projects;
        this.em = em;
    }

    @Transactional
    public void delete(Long id, String githubId) {
        var project = projects.lockById(id)
                .filter(value -> value.getUser().getGithubId().equals(githubId))
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        // Do not delete records that an asynchronous worker is still using.
        String prs = "select p.id from PullRequest p where p.githubProject.id = :id";
        if (count("select count(r) from AnalysisRun r where r.pullRequest.id in (" + prs + ") and r.status = com.mainuddin.codereview.entity.AnalysisRunStatus.RUNNING", id) > 0
                || count("select count(r) from RepositoryPatternProfile r where r.project.id = :id and r.status = 'RUNNING'", id) > 0
                || count("select count(r) from PrPatternCheck r where r.pullRequest.id in (" + prs + ") and r.status = 'RUNNING'", id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for the running analysis to finish, then remove the repository.");
        }
        String profiles = "select r.id from RepositoryPatternProfile r where r.project.id = :id";
        remove("delete from PrPatternCheck r where r.pullRequest.id in (" + prs + ") or r.profile.id in (" + profiles + ")", id);
        remove("delete from PatternOccurrence r where r.profile.id in (" + profiles + ")", id);
        remove("delete from RepositoryPatternProfile r where r.project.id = :id", id);
        remove("delete from AppNotification n where n.githubProject.id = :id or n.pullRequest.id in (" + prs + ")", id);
        remove("delete from SATDFinding f where f.analysisRun.id in (select r.id from AnalysisRun r where r.pullRequest.id in (" + prs + "))", id);
        remove("delete from AnalysisRun r where r.pullRequest.id in (" + prs + ")", id);
        remove("delete from PullRequestComment c where c.pullRequest.id in (" + prs + ")", id);
        remove("delete from PullRequestFile f where f.pullRequest.id in (" + prs + ")", id);
        remove("delete from PullRequest p where p.githubProject.id = :id", id);
        em.remove(project);
        em.flush();
    }

    private long count(String query, Long id) { return em.createQuery(query, Long.class).setParameter("id", id).getSingleResult(); }
    private void remove(String query, Long id) { em.createQuery(query).setParameter("id", id).executeUpdate(); }
}
