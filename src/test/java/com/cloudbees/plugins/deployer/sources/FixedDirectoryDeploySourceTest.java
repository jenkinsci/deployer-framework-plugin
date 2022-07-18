package com.cloudbees.plugins.deployer.sources;

import com.gargoylesoftware.htmlunit.Page;
import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TaskListener;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class FixedDirectoryDeploySourceTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private FreeStyleProject project;

    @Issue("SECURITY-2206")
    @Test
    public void doCheckDirectoryPathWhenUserWithoutPermissionThenStatusForbidden() throws Exception {
        project = r.createFreeStyleProject();

        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("user");
        Page page = webClient.goTo("job/" + project.getName() +"/descriptorByName/com.cloudbees.plugins.deployer.sources.FixedDirectoryDeploySource/checkDirectoryPath?fromWorkspace=true&value=value");

        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
    }

    @Issue("SECURITY-2206")
    @Test
    public void doCheckDirectoryPathWhenPathTraversalThenReturnError() throws Exception {
        project = r.createFreeStyleProject();
        r.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(1));

        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("admin");
        Page page = webClient.goTo("job/" + project.getName() +"/descriptorByName/com.cloudbees.plugins.deployer.sources.FixedDirectoryDeploySource/checkDirectoryPath?fromWorkspace=true&value=../../secret");

        assertThat(page.getWebResponse().getContentAsString(), containsString("Directory path &#039;../../secret&#039; is not contained within the workspace for"));
    }

    @Issue("SECURITY-2206")
    @Test
    public void doCheckDirectoryPathWhenValueIsSymlinkThenReturnError() throws Exception {
        project = r.createFreeStyleProject();
        r.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(1));
        Util.createSymlink(new File(project.getSomeWorkspace().getRemote()), r.jenkins.getRootDir().getAbsolutePath(), "temp_link", TaskListener.NULL);

        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("admin");
        Page page = webClient.goTo("job/" + project.getName() +"/descriptorByName/com.cloudbees.plugins.deployer.sources.FixedDirectoryDeploySource/checkDirectoryPath?fromWorkspace=true&value=temp_link");

        assertThat(page.getWebResponse().getContentAsString(), containsString("Directory path &#039;temp_link&#039; is not contained within the workspace for"));
    }

    @Issue("SECURITY-2206")
    @Test
    public void doCheckDirectoryPathWhenParamsValidThenReturnOk() throws Exception {
        project = r.createFreeStyleProject();
        r.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(1));

        project.getSomeWorkspace().child("test").mkdirs();

        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("admin");
        Page page = webClient.goTo("job/" + project.getName() +"/descriptorByName/com.cloudbees.plugins.deployer.sources.FixedDirectoryDeploySource/checkDirectoryPath?fromWorkspace=true&value=test");

        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(page.getWebResponse().getContentAsString(), is("<div/>"));
    }

    @Before
    public void setUpAuthorization() {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ).everywhere().to("user"));
    }
}
