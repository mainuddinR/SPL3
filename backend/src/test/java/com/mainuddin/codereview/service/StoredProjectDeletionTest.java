package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:delete_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class StoredProjectDeletionTest {
    @Autowired StoredProjectDeletionService service;
    @Autowired UserRepository users;
    @Autowired GithubProjectRepository projects;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.mainuddin.codereview.security.JwtUtils jwt;
    @org.springframework.boot.test.mock.mockito.MockBean GithubPullRequestClient github;

    private Long fixture(String owner) {
        var user = users.save(User.builder().githubId(owner).username(owner).role("ROLE_USER").build());
        return projects.save(GithubProject.builder().user(user).githubRepoId(999L).name("demo")
                .fullName(owner + "/demo").ownerLogin(owner).htmlUrl("https://github.com/" + owner + "/demo").build()).getId();
    }

    @Test void ownerOnlyDeletionPreservesOtherProjectsAndUser() {
        Long id = fixture("delete-owner"), other = fixture("delete-other");
        assertThrows(ResourceNotFoundException.class, () -> service.delete(id, "delete-other"));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(-1L, "delete-owner"));
        assertTrue(projects.existsById(id));
        service.delete(id, "delete-owner");
        assertFalse(projects.existsById(id)); assertTrue(projects.existsById(other));
        assertTrue(users.findByGithubId("delete-owner").isPresent());
    }

    @Test void allDependentTablesAreRemovedAndRollbackRestoresThem() {
        Long id = fixture("delete-children");
        Long uid = projects.findById(id).orElseThrow().getUser().getId();
        jdbc.update("insert into pull_requests(id,project_id,github_pr_id,pr_number,title,state) values(90001,?,1,1,'real fixture','open')", id);
        jdbc.update("insert into pull_request_files(pull_request_id,filename) values(90001,'A.java')");
        jdbc.update("insert into pull_request_comments(pull_request_id,body) values(90001,'comment')");
        jdbc.update("insert into analysis_runs(id,pull_request_id,status,publication_status) values(90001,90001,'COMPLETED','NOT_PUBLISHED')");
        jdbc.update("insert into satd_findings(analysis_run_id,filename,satd_probability,non_satd_probability,confidence,ai_suggestion) values(90001,'A.java',.9,.1,.9,'saved suggestion')");
        jdbc.update("insert into repository_pattern_profiles(id,project_id,status,progress) values(90001,?,'COMPLETED','Completed')", id);
        jdbc.update("insert into pattern_occurrences(profile_id,pattern_type,pattern_family,confidence) values(90001,'STRATEGY','GOF','HIGH')");
        jdbc.update("insert into pr_pattern_checks(pull_request_id,profile_id,checked_at,status,progress) values(90001,90001,CURRENT_TIMESTAMP,'COMPLETED','Completed')");
        jdbc.update("insert into app_notifications(user_id,github_project_id,pull_request_id,notification_type,title,message,read_flag,created_at,dedup_key) values(?,?,90001,'NEW_PR','new','new',false,CURRENT_TIMESTAMP,'delete-fixture')", uid,id);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            service.delete(id, "delete-children"); throw new IllegalStateException("forced rollback");
        }));
        assertTrue(projects.existsById(id));
        assertEquals(1, jdbc.queryForObject("select count(*) from pattern_occurrences where profile_id=90001", Integer.class));
        service.delete(id, "delete-children");
        for (String table : new String[]{"pull_request_files", "pull_request_comments", "satd_findings", "analysis_runs", "pattern_occurrences", "pr_pattern_checks", "app_notifications", "repository_pattern_profiles", "pull_requests"})
            assertEquals(0, jdbc.queryForObject("select count(*) from " + table, Integer.class), table);
    }

    @Test void runningProfileRejectsRemovalWithoutDeletingData() {
        Long id = fixture("delete-running");
        jdbc.update("insert into repository_pattern_profiles(project_id,status,progress) values(?,'RUNNING','Downloading')", id);
        var failure = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.delete(id, "delete-running"));
        assertEquals(409, failure.getStatusCode().value()); assertTrue(projects.existsById(id));
        jdbc.update("update repository_pattern_profiles set status='FAILED' where project_id=?", id);
        service.delete(id, "delete-running");
    }

    @Test void apiIsOwnerProtectedAndNeverDeletesOnGithub() throws Exception {
        Long id = fixture("delete-api");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/projects/" + id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/projects/" + id)
                .header("Authorization", "Bearer " + jwt.generateJwtToken("someone-else", "other")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/projects/" + id)
                .header("Authorization", "Bearer " + jwt.generateJwtToken("delete-api", "owner")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        org.mockito.Mockito.verifyNoInteractions(github);
    }
}
