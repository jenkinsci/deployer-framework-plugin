package com.cloudbees.plugins.deployer.casc;

import com.cloudbees.plugins.deployer.DeployNowColumn;
import hudson.model.View;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.util.stream.StreamSupport;

public class ConfigurationAsCodeTest {
    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("view-configuration.yml")
    public void shouldBeAbleToConfigureAView() throws Exception {
        final View view = r.jenkins.getView("view-with-deployNowColumn");
        Assert.assertNotNull(view);

        Assert.assertTrue(
            StreamSupport.stream(view.getColumns().spliterator(), false)
                  .anyMatch(
                        item -> item instanceof DeployNowColumn
                  )
        );
    }
}
