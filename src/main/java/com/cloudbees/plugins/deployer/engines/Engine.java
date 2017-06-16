/*
 * The MIT License
 *
 * Copyright (c) 2011-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.plugins.deployer.engines;

import com.cloudbees.plugins.deployer.DeployEvent;
import com.cloudbees.plugins.deployer.DeployListener;
import com.cloudbees.plugins.deployer.exceptions.DeployException;
import com.cloudbees.plugins.deployer.exceptions.DeploySourceNotFoundException;
import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.records.DeployedApplicationAction;
import com.cloudbees.plugins.deployer.records.DeployedApplicationFingerprintFacet;
import com.cloudbees.plugins.deployer.records.DeployedApplicationLocation;
import com.cloudbees.plugins.deployer.sources.DeploySource;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Fingerprint;
import hudson.model.Item;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A deployment engine knows how to deploy artifacts to a remote service.
 */
public abstract class Engine<S extends DeployHost<S, T>, T extends DeployTarget<T>> {

    private final static String NO_MD5 = "NOMD5";

    protected final Item deployScope;
    protected final List<Authentication> deployAuthentications;
    protected final AbstractBuild<?, ?> build;
    protected final S set;
    protected final Launcher launcher;
    protected final BuildListener listener;
    protected final Set<DeploySourceOrigin> sources;

    protected Engine(EngineConfiguration<S, T> config) {
        final List<Authentication> deployAuthentications = config.getDeployAuthentications();
        this.deployAuthentications = deployAuthentications == null
                ? Arrays.asList(ACL.SYSTEM)
                : new ArrayList<Authentication>(deployAuthentications);
        this.deployScope = config.getDeployScope();
        this.build = config.getBuild();
        this.launcher = config.getLauncher();
        this.listener = config.getListener();
        this.set = config.getConfiguration();
        final Set<DeploySourceOrigin> sources = config.getSources();
        this.sources = sources == null ? new HashSet<DeploySourceOrigin>() : new HashSet<DeploySourceOrigin>(
                sources);
    }

    public boolean perform() throws Throwable {
        final List<DeploySourceOrigin> validOrigins = new ArrayList<DeploySourceOrigin>(
                DeploySourceOrigin.allInPreferenceOrder());
        validOrigins.retainAll(sources);

        logDetails();
        for (T target : set.getTargets()) {
            log("Deploying " + target.getDisplayName());
            boolean found = false;
            DeployEvent event = createEvent(target);
            try {
                DeploySource source = target.getArtifact();
                if (source == null) {
                    throw new DeploySourceNotFoundException(null,
                            "Undefined source for " + target.getDisplayName());
                }
                DeployedApplicationLocation location = null;
                findSource:
                for (DeploySourceOrigin origin : validOrigins) {
                    if (source.getDescriptor().isSupported(origin)) {
                        switch (origin) {
                            case WORKSPACE: {
                                FilePath workspace = build.getWorkspace();
                                if (workspace != null) {
                                    FilePath applicationFile = source.getApplicationFile(workspace);
                                    if (applicationFile != null) {
                                        found = true;
                                        validate(applicationFile);
                                        log("  Resolved from workspace as " + applicationFile);
                                        location = process(applicationFile, target);
                                        DeployListener.notifySuccess(set, target, event);
                                        break findSource;
                                    }
                                }
                            }
                            break;
                            case RUN: {
                                File applicationFile = source.getApplicationFile(build);
                                if (applicationFile != null) {
                                    found = true;
                                    validate(applicationFile);
                                    log("  Resolved from archived artifacts as " + applicationFile);
                                    location = process(applicationFile, target);
                                    DeployListener.notifySuccess(set, target, event);
                                    break findSource;
                                }
                            }
                            break;
                            default:
                                DeployListener.notifyFailure(set, target, event);
                                throw new UnsupportedOperationException(
                                        "Unknown DeploySourceOrigin instance: " + origin);
                        }
                    }
                }
                if (!found) {
                    throw new DeploySourceNotFoundException(source,
                            "Cannot find source for " + target.getDisplayName());
                }
                if (location != null) {
                    boolean haveAction = false;
                    for (DeployedApplicationAction action : build.getActions(DeployedApplicationAction.class)) {
                        if (action.getLocation().equals(location)) {
                            haveAction = true;
                            break;
                        }
                    }
                    if (!haveAction) {
                        build.addAction(new DeployedApplicationAction<DeployedApplicationLocation>(location));
                    }
                }
            } catch (RuntimeException e) {
                DeployListener.notifyFailure(set, target, event);
                throw e;
            } catch (DeployException e) {
                DeployListener.notifyFailure(set, target, event);
                throw e;
            }
        }
        return true;
    }

    public abstract void validate(FilePath applicationFile) throws DeployException;

    public abstract void validate(File applicationFile) throws DeployException;

    @CheckForNull
    public DeployedApplicationLocation process(FilePath applicationFile, T target) throws DeployException {
        try {
            FingerprintingWrapper actor = new FingerprintingWrapper(newDeployActor(target), listener);
            return this.addToFacets(applicationFile.act(actor));
        } catch (DeployException e) {
            throw e;
        } catch (InterruptedException e) {
            throw new DeployException("Deployment interrupted", e);
        } catch (Throwable t) {
            throw new DeployException(t.getMessage(), t);
        }
    }

    @CheckForNull
    public DeployedApplicationLocation process(File applicationFile, T target) throws DeployException {
        try {
            FingerprintingWrapper actor = new FingerprintingWrapper(newDeployActor(target), listener);
            return this.addToFacets(actor.invoke(applicationFile, launcher.getChannel()));
        } catch (DeployException e) {
            throw e;
        } catch (InterruptedException e) {
            throw new DeployException("Deployment interrupted", e);
        } catch (Throwable t) {
            throw new DeployException(t.getMessage(), t);
        }
    }

    private DeployedApplicationLocation addToFacets(Map.Entry<String, DeployedApplicationLocation> pair) throws IOException {
        DeployedApplicationLocation location = pair.getValue();
        String md5sum = pair.getKey();

        if (!NO_MD5.equals(md5sum)) {
            Jenkins j = Jenkins.getInstance();
            if (j == null) {
                throw new IllegalStateException("Jenkins has not been started, or was already shut down");
            }

            Fingerprint fingerprint = j._getFingerprint(md5sum);
            if (fingerprint != null) {
                fingerprint.getFacets()
                        .add(new DeployedApplicationFingerprintFacet<DeployedApplicationLocation>(fingerprint,
                                System.currentTimeMillis(),
                                location));
                fingerprint.save();
                listener.getLogger().println("[cloudbees-deployer] Recorded deployment in fingerprint record");
            } else {
                listener.getLogger()
                        .println("[cloudbees-deployer] Deployed artifact does not have a fingerprint record");
            }
        }

        return location;
    }

    protected abstract FilePath.FileCallable<DeployedApplicationLocation> newDeployActor(T target)
            throws DeployException;

    public abstract DeployEvent createEvent(T target) throws DeployException;

    public abstract void logDetails();

    public void log(String message) {
        listener.getLogger().println("[cloudbees-deployer] " + message);
    }

    @SuppressWarnings("unchecked")
    public static EngineFactory<?, ?> create(DeployHost<?, ?> configuration) throws DeployException {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IllegalStateException("Jenkins has shut down or not started.");
        }
        for (Object d : jenkins.getDescriptorList(EngineFactory.class)) {
            if (d instanceof EngineFactoryDescriptor) {
                final EngineFactoryDescriptor<?, ?> descriptor = EngineFactoryDescriptor.class.cast(d);
                if (descriptor.isApplicable(configuration.getClass())) {
                    return ((EngineFactoryDescriptor) descriptor).newFactory(configuration);
                }
            }

        }
        throw new DeployException("Deployment hosts of type " + configuration.getClass() + " are unsupported");
    }

    public static class FingerprintingWrapper extends MasterToSlaveFileCallable<Map.Entry<String, DeployedApplicationLocation>> {
        private final FilePath.FileCallable<DeployedApplicationLocation> delegate;
        private final BuildListener listener;

        public FingerprintingWrapper(FilePath.FileCallable<DeployedApplicationLocation> delegate,
                                     BuildListener listener) {
            this.delegate = delegate;
            this.listener = listener;
        }

        public Map.Entry<String, DeployedApplicationLocation> invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            DeployedApplicationLocation location = delegate.invoke(f, channel);
            if (f.isFile() && location != null) {
                FileInputStream fis = new FileInputStream(f);
                String md5sum = Util.getDigestOf(fis);
                return new AbstractMap.SimpleEntry<String, DeployedApplicationLocation>(md5sum, location);
            }

            return new AbstractMap.SimpleEntry<String, DeployedApplicationLocation>(NO_MD5, location);
        }
    }
}
