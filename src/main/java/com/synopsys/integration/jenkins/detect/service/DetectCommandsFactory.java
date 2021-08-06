/*
 * blackduck-detect
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.detect.service;

import java.io.IOException;

import com.synopsys.integration.jenkins.detect.DetectFreestyleCommands;
import com.synopsys.integration.jenkins.detect.DetectPipelineCommands;
import com.synopsys.integration.jenkins.detect.DetectRunner;
import com.synopsys.integration.jenkins.detect.service.strategy.DetectStrategyService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsBuildService;
import com.synopsys.integration.jenkins.service.JenkinsConfigService;
import com.synopsys.integration.jenkins.service.JenkinsRemotingService;
import com.synopsys.integration.jenkins.service.JenkinsServicesFactory;
import com.synopsys.integration.jenkins.wrapper.JenkinsWrapper;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;

public class DetectCommandsFactory {
    public static final String NULL_WORKSPACE = "Detect cannot be executed when the workspace is null";
    private final JenkinsWrapper jenkinsWrapper;
    private final TaskListener listener;
    private final EnvVars envVars;
    private final FilePath workspace;

    private DetectCommandsFactory(JenkinsWrapper jenkinsWrapper, TaskListener listener, EnvVars envVars, FilePath workspace) throws AbortException {
        this.jenkinsWrapper = jenkinsWrapper;
        this.listener = listener;
        this.envVars = envVars;

        if (null == workspace) {
            throw new AbortException(NULL_WORKSPACE);
        }
        this.workspace = workspace;
    }

    public static DetectFreestyleCommands fromPostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        DetectCommandsFactory detectCommandsFactory = new DetectCommandsFactory(JenkinsWrapper.initializeFromJenkinsJVM(), listener, build.getEnvironment(listener), build.getWorkspace());

        JenkinsServicesFactory jenkinsServicesFactory = new JenkinsServicesFactory(detectCommandsFactory.getLogger(), build, build.getEnvironment(listener), launcher, listener, build.getBuiltOn(), build.getWorkspace());
        JenkinsBuildService jenkinsBuildService = jenkinsServicesFactory.createJenkinsBuildService();
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();
        JenkinsRemotingService jenkinsRemotingService = jenkinsServicesFactory.createJenkinsRemotingService();

        return new DetectFreestyleCommands(jenkinsBuildService, detectCommandsFactory.createDetectRunner(jenkinsConfigService, jenkinsRemotingService));
    }

    public static DetectPipelineCommands fromPipeline(TaskListener listener, EnvVars envVars, Launcher launcher, Node node, FilePath workspace) throws AbortException {
        DetectCommandsFactory detectCommandsFactory = new DetectCommandsFactory(JenkinsWrapper.initializeFromJenkinsJVM(), listener, envVars, workspace);

        JenkinsServicesFactory jenkinsServicesFactory = new JenkinsServicesFactory(detectCommandsFactory.getLogger(), null, envVars, launcher, listener, node, workspace);
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();
        JenkinsRemotingService jenkinsRemotingService = jenkinsServicesFactory.createJenkinsRemotingService();

        return new DetectPipelineCommands(detectCommandsFactory.createDetectRunner(jenkinsConfigService, jenkinsRemotingService), detectCommandsFactory.getLogger());
    }

    private DetectRunner createDetectRunner(JenkinsConfigService jenkinsConfigService, JenkinsRemotingService jenkinsRemotingService) throws AbortException {
        return new DetectRunner(createDetectEnvironmentService(jenkinsConfigService), jenkinsRemotingService, createDetectStrategyService(jenkinsConfigService), createDetectArgumentService());
    }

    private DetectArgumentService createDetectArgumentService() {
        return new DetectArgumentService(getLogger(), jenkinsWrapper.getVersionHelper());
    }

    private DetectEnvironmentService createDetectEnvironmentService(JenkinsConfigService jenkinsConfigService) {
        return new DetectEnvironmentService(getLogger(), jenkinsWrapper.getProxyHelper(), jenkinsWrapper.getVersionHelper(), jenkinsWrapper.getCredentialsHelper(), jenkinsConfigService, envVars);
    }

    private DetectStrategyService createDetectStrategyService(JenkinsConfigService jenkinsConfigService) {
        FilePath workspaceTempDir = WorkspaceList.tempDir(this.workspace);

        return new DetectStrategyService(getLogger(), jenkinsWrapper.getProxyHelper(), workspaceTempDir.getRemote(), jenkinsConfigService);
    }

    private JenkinsIntLogger getLogger() {
        return new JenkinsIntLogger(listener);
    }
}
