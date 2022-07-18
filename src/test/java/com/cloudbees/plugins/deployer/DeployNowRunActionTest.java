package com.cloudbees.plugins.deployer;

import com.gargoylesoftware.htmlunit.Page;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.io.File;
import java.net.HttpURLConnection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class DeployNowRunActionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("SECURITY-2205")
    @Test
    public void doDeployTextWhenUserWithoutPermissionThenShouldReturnStatusForbidden() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject("test");
        r.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(1));

        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        
        webClient.login("user");
        Page page = webClient.goTo("job/" + project.getName() + "/" + project.getLastSuccessfulBuild().getNumber() + "/deploy-now/deployText");
        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
    }

    @Issue("SECURITY-2205")
    @Test
    public void doDeployTextWhenUserWithDeployPermissionThenShouldReturnOk() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject("test1");
        r.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(1));
        File logFile = new File(project.getLastSuccessfulBuild().getRootDir() + "/cloudbees-deploy-now.log");
        logFile.createNewFile();

        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("admin");
        Page page = webClient.goTo("job/" + project.getName() + "/" + project.getLastSuccessfulBuild().getNumber() + "/deploy-now/deployText", "text/plain");
        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_OK));
    }

    @Before
    public void setUpAuthorization() {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER, DeployNowRunAction.DEPLOY).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ).everywhere().to("user"));
    }
}
