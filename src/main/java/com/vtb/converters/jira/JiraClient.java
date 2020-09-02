package com.vtb.converters.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicVotes;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Data
public class JiraClient {
    private String projectKey;
    private String username;
    private String password;
    private String jiraUrl;
    private JiraRestClient restClient;
    public static final Long TASK = 10003L;

    private JiraClient(String username, String password, String jiraUrl) {
        this.username = username;
        this.password = password;
        this.jiraUrl = jiraUrl;
        this.restClient = getJiraRestClient();
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        String parentProjectKey = null;

        if (args.length > 0) {
            parentProjectKey = Arrays.stream(args)
                    .filter(p -> p.toLowerCase().contains("parentproject")).findFirst().get().split("=")[1];

            if (parentProjectKey != null && !parentProjectKey.isEmpty()) {
                log.info("Parent project key={}", parentProjectKey);
            } else {
                log.error("Must have nonempty parentProject arg");
            }
        } else {
            log.error("Must have at least nonempty parentProject arg");
            System.exit(1);
        }

        JiraClient jiraClient = new JiraClient("archimagealex", "au_$Y5!VvSX6QW8", "http://localhost:8088");

        Path path = Files.createTempFile("jira", "out.txt");
/*        final String issueKey = jiraClient.createIssue(PROJECT_KEY, TASK, "Issue created from JRJC");
        log.info("Issue {} created", issueKey);
        jiraClient.updateIssueDescription(issueKey, "This is description from my Jira Client");
        Issue issue = jiraClient.getIssue(issueKey);
        log.info("Issue {} description: {}", issueKey, issue.getDescription());

        log.info("Votes count: {}", jiraClient.getTotalVotesCount(issueKey));

        jiraClient.addComment(issue, "This is comment from my Jira Client");

        log.info("Comments of issue {} are:", issueKey);
        List<Comment> comments = jiraClient.getAllComments(issueKey);
        comments.forEach(c -> log.info("Comment id: {}, comment body: {}", c.getId(), c.getBody()));

        jiraClient.deleteIssue(issueKey, true);
        log.info("Issue {} deleted", issueKey);

        log.info("Project {} components are:", PROJECT_KEY);
        jiraClient.getJiraRestClient().getProjectClient()
                .getProject(PROJECT_KEY).get(1L, TimeUnit.MINUTES)
                .getComponents().forEach(c -> log.info(c.getName()));*/

        log.info("Issues of project {} are:", parentProjectKey);
        jiraClient.getProjectIssues(parentProjectKey)
                .forEach(i -> log.info("Issue id: {}, issue summary: {}", i.getId(), i.getSummary()));

        String projectName = jiraClient.getJiraRestClient().getProjectClient()
                .getProject(parentProjectKey).get().getName();
        String searchCriteria = "project = '" + projectName + "' AND labels in (Тестовая_метка)";
        log.info("Issues of search criteria {} are:", searchCriteria);
        jiraClient.getFilteredIssues(searchCriteria)
                .forEach(i -> {
                    log.info("Issue id: {}, issue summary: {}", i.getId(), i.getSummary());
                    try {
                        Files.write(path, Collections.singleton(i.getSummary()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        log.info("Written to file {}", path.toString());
        jiraClient.restClient.close();
    }

    private Iterable<Issue> getFilteredIssues(String jql) throws InterruptedException, ExecutionException {
        return restClient.getSearchClient().searchJql(jql).get().getIssues();
    }

    private Iterable<Issue> getProjectIssues(String projectKey) throws InterruptedException, ExecutionException {
        String projectName = restClient.getProjectClient().getProject(projectKey).get().getName();
        return restClient.getSearchClient().searchJql("project = '" + projectName + "'").get().getIssues();
    }

    private String createIssue(String projectKey, Long issueType, String issueSummary) {

        IssueRestClient issueClient = restClient.getIssueClient();

        IssueInput newIssue = new IssueInputBuilder(projectKey, issueType, issueSummary).build();

        return issueClient.createIssue(newIssue).claim().getKey();
    }

    private Issue getIssue(String issueKey) {
        return restClient.getIssueClient().getIssue(issueKey).claim();
    }

    private void voteForAnIssue(Issue issue) {
        restClient.getIssueClient().vote(issue.getVotesUri()).claim();
    }

    private int getTotalVotesCount(String issueKey) {
        BasicVotes votes = getIssue(issueKey).getVotes();
        return votes == null ? 0 : votes.getVotes();
    }

    private void addComment(Issue issue, String commentBody) {
        restClient.getIssueClient().addComment(issue.getCommentsUri(), Comment.valueOf(commentBody));
    }

    private List<Comment> getAllComments(String issueKey) {
        return StreamSupport.stream(getIssue(issueKey).getComments().spliterator(), false)
                .collect(Collectors.toList());
    }

    private void updateIssueDescription(String issueKey, String newDescription) {
        IssueInput input = new IssueInputBuilder().setDescription(newDescription).build();
        restClient.getIssueClient().updateIssue(issueKey, input).claim();
    }

    private void deleteIssue(String issueKey, boolean deleteSubtasks) {
        restClient.getIssueClient().deleteIssue(issueKey, deleteSubtasks).claim();
    }

    private JiraRestClient getJiraRestClient() {
        return new AsynchronousJiraRestClientFactory()
                .createWithBasicHttpAuthentication(getJiraUri(), this.username, this.password);
    }

    private URI getJiraUri() {
        return URI.create(this.jiraUrl);
    }
}
