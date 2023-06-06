package com.cloudbees.plugins.deployer.sources;

import org.htmlunit.Page;
import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.text.StringEscapeUtils;
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

public class WildcardPathDeploySourceTest {
    
    @Rule
    public JenkinsRule r = new JenkinsRule();
    
    
    @Issue("SECURITY-2205")
    @Test
    public void doCheckFilePatternWhenUserWithoutPermissionThenStatusForbidden() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject("test1");

        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("user");
        Page page = webClient.goTo("job/" + project.getName() +"/descriptorByName/com.cloudbees.plugins.deployer.sources.FixedDirectoryDeploySource/checkDirectoryPath?fromWorkspace=true&value=value");

        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
    }
    
    @Issue("SECURITY-2205")
    @Test
    public void doCheckFilePatternWhenValueIsSymlinkThenReturnError() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject("test2");
        r.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(1));

        Util.createSymlink(new File(project.getSomeWorkspace().getRemote()), r.jenkins.getRootPath().createTempFile("prefix", "suffix").getRemote(), "master.key", TaskListener.NULL);
        String value = "master.key";
        
        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("admin");
        Page page = webClient.goTo("job/" + project.getName() + "/descriptorByName/com.cloudbees.plugins.deployer.sources.WildcardPathDeploySource/checkFilePattern?fromWorkspace=true&value=" + value);
        assertThat(StringEscapeUtils.unescapeHtml4(page.getWebResponse().getContentAsString()), containsString("Directory path '" + value + "' is not contained within the workspace for"));
    }

    @Issue("SECURITY-2205")
    @Test
    public void doCheckFilePatternWhenParamsValidThenReturnOk() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject("test3");
        r.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(1));
        
        project.getSomeWorkspace().createTempFile("prefix", "suffix");
        String value = "*suffix";
        
        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("admin");
        Page page = webClient.goTo("job/" + project.getName() + "/descriptorByName/com.cloudbees.plugins.deployer.sources.WildcardPathDeploySource/checkFilePattern?fromWorkspace=true&value=" + value);

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
