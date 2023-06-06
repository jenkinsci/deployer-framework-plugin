package com.cloudbees.plugins.deployer.sources;

import hudson.model.Run;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import org.htmlunit.Page;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StaticSelectionDeploySourceTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder folder= new TemporaryFolder();

    @Issue("SECURITY-2208")
    @Test
    public void doCheckFilePathWhenPathTraversalThenShouldReturnError() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject("test1");
        r.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(1));


        new File(project.getLastSuccessfulBuild().getRootDir() + "/archive").mkdir();
        File file = new File(project.getLastSuccessfulBuild().getRootDir() + "/archive/test.war");
        file.createNewFile();

        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("admin");
        Page page = webClient.goTo("job/" + project.getName() + "/descriptorByName/com.cloudbees.plugins.deployer.sources.StaticSelectionDeploySource/checkFilePath?value=../log");

        assertThat(page.getWebResponse().getContentAsString(), containsString("Directory path &#039;../log&#039; is not contained within the artifacts directory for"));
    }

    @Issue("SECURITY-2208")
    @Test
    public void doCheckFilePathWhenParamIsNotPathTraversalThenShouldOk() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject("test1");
        r.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(1));

        new File(project.getLastSuccessfulBuild().getRootDir() + "/archive").mkdir();
        File file = new File(project.getLastSuccessfulBuild().getRootDir() + "/archive/test.war");
        file.createNewFile();

        JenkinsRule.WebClient webClient = r.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login("admin");
        Page page = webClient.goTo("job/" + project.getName() + "/descriptorByName/com.cloudbees.plugins.deployer.sources.StaticSelectionDeploySource/checkFilePath?value=test.war");

        assertThat(page.getWebResponse().getContentAsString(), is("<div/>"));
    }

    @Before
    public void setUpAuthorization() {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ).everywhere().to("user"));
    }

    @Issue("SECURITY-2764")
    @Test
    public void fileInDirectory() throws Exception {
        Run run = mock(Run.class);
        File tmp = folder.newFolder();
        Files.createFile(new File(tmp,"foo.txt").toPath());
        when(run.getArtifactsDir()).thenReturn(tmp);
        StaticSelectionDeploySource ssds = new StaticSelectionDeploySource("foo.txt");
        File appFile = ssds.getApplicationFile(run);
        Assert.assertTrue(Files.isRegularFile(appFile.toPath()));
    }

    @Issue("SECURITY-2764")
    @Test
    public void fileInSubDirectory() throws Exception {
        Run run = mock(Run.class);
        File tmp = folder.newFolder();
        File subTmp = new File(tmp, "subdir");
        Files.createDirectories(subTmp.toPath());
        Files.createFile(new File(subTmp,"foo.txt").toPath());
        when(run.getArtifactsDir()).thenReturn(tmp);
        StaticSelectionDeploySource ssds = new StaticSelectionDeploySource("subdir/foo.txt");
        // the file definitely exists but it's not part of the correct directory
        File appFile = ssds.getApplicationFile(run);
        Assert.assertTrue(Files.isRegularFile(appFile.toPath()));
    }


    @Issue("SECURITY-2764")
    @Test(expected = IllegalArgumentException.class)
    public void fileNotInDirectory() throws Exception {
        Run run = mock(Run.class);
        File tmp = folder.newFolder();
        File subTmp = new File(tmp, "subdir");
        Files.createDirectories(subTmp.toPath());
        Files.createFile(new File(tmp,"foo.txt").toPath());
        when(run.getArtifactsDir()).thenReturn(subTmp);
        StaticSelectionDeploySource ssds = new StaticSelectionDeploySource("../foo.txt");
        // the file definitely exists but it's not part of the correct directory
        ssds.getApplicationFile(run);
    }

    @Issue("SECURITY-2764")
    @Test
    public void fileNotExistingInSubdirectory() throws Exception {
        Run run = mock(Run.class);
        File tmp = folder.newFolder();
        when(run.getArtifactsDir()).thenReturn(tmp);
        StaticSelectionDeploySource ssds = new StaticSelectionDeploySource("/etc/passwd");
        Assert.assertNull(ssds.getApplicationFile(run));
    }

    @Issue("SECURITY-2764")
    @Test(expected = IllegalArgumentException.class)
    public void fileInParentDirectory() throws Exception {
        Run run = mock(Run.class);
        when(run.getArtifactsDir()).thenReturn(new File("target"));
        StaticSelectionDeploySource ssds = new StaticSelectionDeploySource("../pom.xml");
        ssds.getApplicationFile(run);
    }

    @Issue("SECURITY-2764")
    @Test(expected = IllegalArgumentException.class)
    public void traversalPath() throws Exception {
        Run run = mock(Run.class);
        when(run.getArtifactsDir()).thenReturn(new File("target"));
        StaticSelectionDeploySource ssds = new StaticSelectionDeploySource("../../../../../etc/passwd");
        ssds.getApplicationFile(run);
    }

}
