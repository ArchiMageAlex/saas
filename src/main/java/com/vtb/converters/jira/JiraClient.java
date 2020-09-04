package com.vtb.converters.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicVotes;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.LinkIssuesInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Data
public class JiraClient {
    public static final Long TASK = 10003L;
    private String projectKey;
    private String username;
    private String password;
    private String jiraUrl;
    private JiraRestClient restClient;

    private JiraClient(String username, String password, String jiraUrl) {
        this.username = username;
        this.password = password;
        this.jiraUrl = jiraUrl;
        this.restClient = getJiraRestClient();
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException, TimeoutException {
/*        String parentProjectKey = null;

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
        }*/


        Configurations configs = new Configurations();

        try {
            XMLConfiguration config = configs.xml("config.xml");
            List<HierarchicalConfiguration<ImmutableNode>> fields =
                    config.configurationsAt("dispersion.epics.epic");
            JiraClient jiraClient = new JiraClient(
                    config.getString("identity.username"),
                    config.getString("identity.password"),
                    config.getString("identity.url"));

            for (HierarchicalConfiguration sub : fields) {
                String epicKey = sub.getString("[@key]");
                String targetProject = sub.getString("[@targetProject]");
                log.info("Create children of epic key= {}, target project = {}", epicKey, targetProject);
                jiraClient.createChildren(epicKey, targetProject);
            }

            jiraClient.restClient.close();
        } catch (ConfigurationException cex) {
            log.error("Something went wrong at configuration reading", cex);
        }

//        Path path = Files.createTempFile("jira", "out.txt");
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

/*        log.info("Issues of project {} are:", parentProjectKey);
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
        log.info("Written to file {}", path.toString());*/
    }

    private void createChildren(String epicKey, String targetProject) throws ExecutionException, InterruptedException {
        final Issue parentEpic = getIssue(epicKey);
        boolean issueAlreadyExists = false;

        for (IssueLink il : parentEpic.getIssueLinks()) {
            if (getIssue(il.getTargetIssueKey()).getSummary().equals(parentEpic.getSummary() + ". Аналитика")) {
                issueAlreadyExists = true;
                break;
            }
        }

        if (!issueAlreadyExists) {
            IssueInputBuilder issueInputBuilder = new IssueInputBuilder();
            issueInputBuilder.setFieldValue("customfield_10101", epicKey);
            issueInputBuilder.setFieldValue("customfield_11612", "542 Унификация ИТ-платформ, "); // Программа-заказчик
            issueInputBuilder.setFieldValue("customfield_11613", "581 Внедрение АБС ЦФТ 2.0 для физических лиц"); // Проект-заказчик
            issueInputBuilder.setFieldValue("customfield_11611", "Кредиты, "); // Стрим-владелец
            issueInputBuilder.setFieldValue("customfield_11610", "Кредиты, "); // Стрим-заказчик
            issueInputBuilder.setFieldValue("customfield_", ""); //
            issueInputBuilder.setSummary(parentEpic.getSummary() + ". Аналитика");
            issueInputBuilder.setProjectKey(targetProject);
            issueInputBuilder.setIssueTypeId(TASK);
            Issue issue = (Issue) restClient.getIssueClient().createIssue(issueInputBuilder.build()).get();
            log.info("Issue {} created, linked to epic {}", issue.getKey(), parentEpic.getKey());
        }
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

    private Issue getIssue(String issueKey) throws ExecutionException, InterruptedException {
        return restClient.getIssueClient().getIssue(issueKey).get();
    }

    private void voteForAnIssue(Issue issue) {
        restClient.getIssueClient().vote(issue.getVotesUri()).claim();
    }

    private int getTotalVotesCount(String issueKey) throws ExecutionException, InterruptedException {
        BasicVotes votes = getIssue(issueKey).getVotes();
        return votes == null ? 0 : votes.getVotes();
    }

    private void addComment(Issue issue, String commentBody) {
        restClient.getIssueClient().addComment(issue.getCommentsUri(), Comment.valueOf(commentBody));
    }

    private List<Comment> getAllComments(String issueKey) throws ExecutionException, InterruptedException {
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

    private void setEpicLink(Issue issue, Issue epic) {
        LinkIssuesInput lii = new LinkIssuesInput(issue.getKey(), epic.getKey(), "");
        restClient.getIssueClient().linkIssue(lii);
    }
}
