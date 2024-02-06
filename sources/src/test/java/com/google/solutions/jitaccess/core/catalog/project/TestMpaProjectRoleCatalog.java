//
// Copyright 2023 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege.Status;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestMpaProjectRoleCatalog {

    private static final UserId SAMPLE_REQUESTING_USER = new UserId("user@example.com");
    private static final UserId SAMPLE_APPROVING_USER = new UserId("approver@example.com");
    private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");
    private static final String SAMPLE_ROLE = "roles/resourcemanager.role1";

    // ---------------------------------------------------------------------------
    // validateRequest.
    // ---------------------------------------------------------------------------

    @Test
    public void whenDurationExceedsMax_ThenValidateRequestThrowsException() throws Exception {
        var catalog = new MpaProjectRoleCatalog(
                Mockito.mock(PolicyAnalyzerRepository.class),
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(30),
                        1,
                        2));

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().maxActivationDuration().plusMinutes(1));

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.validateRequest(request));
    }

    @Test
    public void whenDurationBelowMin_ThenValidateRequestThrowsException() throws Exception {
        var catalog = new MpaProjectRoleCatalog(
                Mockito.mock(PolicyAnalyzerRepository.class),
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(30),
                        1,
                        2));

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration().minusMinutes(1));

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.validateRequest(request));
    }

    @Test
    public void whenReviewersMissingAndTypeSelfApproval_ThenValidateRequestReturns() throws Exception {
        var catalog = new MpaProjectRoleCatalog(
                Mockito.mock(PolicyAnalyzerRepository.class),
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(30),
                        1,
                        2));

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration());
        when(request.reviewers()).thenReturn(null);
        when(request.activationType()).thenReturn(ActivationType.SELF_APPROVAL);

        assertDoesNotThrow(() -> catalog.validateRequest(request));
    }

    @Test
    public void whenReviewersMissingAndTypeRequiresApproval_ThenValidateRequestThrowsException() throws Exception {
        var catalog = new MpaProjectRoleCatalog(
                Mockito.mock(PolicyAnalyzerRepository.class),
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(30),
                        1,
                        2));

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration());
        when(request.reviewers()).thenReturn(null);
        when(request.activationType()).thenReturn(ActivationType.EXTERNAL_APPROVAL);

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.validateRequest(request));
    }

    @Test
    public void whenNumberOfReviewersExceedsMax_ThenValidateRequestThrowsException() throws Exception {
        var catalog = new MpaProjectRoleCatalog(
                Mockito.mock(PolicyAnalyzerRepository.class),
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(30),
                        1,
                        2));

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration());
        when(request.reviewers()).thenReturn(Set.of(
                new UserId("user-1@example.com"),
                new UserId("user-2@example.com"),
                new UserId("user-3@example.com")));
        when(request.activationType()).thenReturn(ActivationType.EXTERNAL_APPROVAL);

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.validateRequest(request));
    }

    @Test
    public void whenNumberOfReviewersBelowMinAndRequiresApproval_ThenValidateRequestThrowsException() throws Exception {
        var catalog = new MpaProjectRoleCatalog(
                Mockito.mock(PolicyAnalyzerRepository.class),
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(30),
                        2,
                        2));

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration());
        when(request.reviewers()).thenReturn(Set.of(
                new UserId("user-1@example.com")));
        when(request.activationType()).thenReturn(ActivationType.PEER_APPROVAL);

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.validateRequest(request));
    }

    @Test
    public void whenNumberOfReviewersOk_ThenValidateRequestReturns() throws Exception {
        var catalog = new MpaProjectRoleCatalog(
                Mockito.mock(PolicyAnalyzerRepository.class),
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(30),
                        1,
                        2));

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration());
        when(request.reviewers()).thenReturn(Set.of(
                new UserId("user-1@example.com")));
        when(request.activationType()).thenReturn(ActivationType.EXTERNAL_APPROVAL);

        catalog.validateRequest(request);
    }

    // ---------------------------------------------------------------------------
    // verifyUserCanActivatePrivileges.
    // ---------------------------------------------------------------------------

    @Test
    public void whenPrivilegeNotFound_ThenVerifyUserCanActivateRequesterPrivilegesThrowsException() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

        when(policyAnalyzer
                .findRequesterPrivileges(
                        eq(SAMPLE_REQUESTING_USER),
                        eq(SAMPLE_PROJECT),
                        eq(EnumSet.of(ActivationType.SELF_APPROVAL)),
                        eq(EnumSet.of(RequesterPrivilege.Status.AVAILABLE))))
                .thenReturn(RequesterPrivilegeSet.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> catalog.verifyUserCanActivateRequesterPrivileges(
                        SAMPLE_REQUESTING_USER,
                        SAMPLE_PROJECT,
                        ActivationType.SELF_APPROVAL,
                        List.of(new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)))));
    }

    @Test
    public void whenActivationTypeMismatches_ThenVerifyUserCanActivateRequesterPrivilegesThrowsException()
            throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

        var peerApprovalPrivilege = new RequesterPrivilege<>(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
                "-",
                ActivationType.PEER_APPROVAL,
                RequesterPrivilege.Status.AVAILABLE);

        when(policyAnalyzer
                .findRequesterPrivileges(
                        eq(SAMPLE_REQUESTING_USER),
                        eq(SAMPLE_PROJECT),
                        eq(EnumSet.of(ActivationType.SELF_APPROVAL)),
                        eq(EnumSet.of(RequesterPrivilege.Status.AVAILABLE))))
                .thenReturn(RequesterPrivilegeSet.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> catalog.verifyUserCanActivateRequesterPrivileges(
                        SAMPLE_REQUESTING_USER,
                        SAMPLE_PROJECT,
                        ActivationType.SELF_APPROVAL,
                        List.of(peerApprovalPrivilege.id())));
    }

    // ---------------------------------------------------------------------------
    // verifyUserCanRequest.
    // ---------------------------------------------------------------------------

    @Test
    public void whenUserNotAllowedToActivate_ThenVerifyUserCanRequestThrowsException() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

        var selfApprovalPrivilege = new RequesterPrivilege<>(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
                "-",
                ActivationType.SELF_APPROVAL,
                RequesterPrivilege.Status.AVAILABLE);

        when(policyAnalyzer
                .findRequesterPrivileges(
                        eq(SAMPLE_REQUESTING_USER),
                        eq(SAMPLE_PROJECT),
                        eq(EnumSet.of(ActivationType.SELF_APPROVAL)),
                        eq(EnumSet.of(RequesterPrivilege.Status.AVAILABLE))))
                .thenReturn(RequesterPrivilegeSet.empty());

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration());
        when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
        when(request.requesterPrivilege()).thenReturn(selfApprovalPrivilege.id());
        when(request.activationType()).thenReturn(ActivationType.SELF_APPROVAL);

        assertThrows(
                AccessDeniedException.class,
                () -> catalog.verifyUserCanRequest(request));
    }

    @Test
    public void whenUserAllowedToActivate_ThenVerifyUserCanRequestReturns() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

        var selfApprovalPrivilege = new RequesterPrivilege<>(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
                "-",
                ActivationType.SELF_APPROVAL,
                RequesterPrivilege.Status.AVAILABLE);

        when(policyAnalyzer
                .findRequesterPrivileges(
                        eq(SAMPLE_REQUESTING_USER),
                        eq(SAMPLE_PROJECT),
                        eq(EnumSet.of(ActivationType.SELF_APPROVAL)),
                        eq(EnumSet.of(RequesterPrivilege.Status.AVAILABLE))))
                .thenReturn(new RequesterPrivilegeSet<>(
                        new TreeSet<>(Set.of(selfApprovalPrivilege)),
                        Set.of(),
                        Set.of()));

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration());
        when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
        when(request.requesterPrivilege()).thenReturn(selfApprovalPrivilege.id());
        when(request.activationType()).thenReturn(ActivationType.SELF_APPROVAL);

        catalog.verifyUserCanRequest(request);
    }

    // ---------------------------------------------------------------------------
    // verifyUserCanApprove.
    // ---------------------------------------------------------------------------

    @Test
    public void whenUserNotAllowedToActivate_ThenVerifyUserCanApproveThrowsException() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

        var peerApprovalPrivilege = new RequesterPrivilege<>(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
                "-",
                ActivationType.PEER_APPROVAL,
                RequesterPrivilege.Status.AVAILABLE);

        when(policyAnalyzer
                .findReviewerPrivelegeHolders(
                        peerApprovalPrivilege.id(),
                        peerApprovalPrivilege.activationType()))
                .thenReturn(Set.of());

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration());
        when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
        when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVING_USER));
        when(request.requesterPrivilege()).thenReturn(peerApprovalPrivilege.id());
        when(request.activationType()).thenReturn(ActivationType.PEER_APPROVAL);

        assertThrows(
                AccessDeniedException.class,
                () -> catalog.verifyUserCanApprove(SAMPLE_APPROVING_USER, request));
    }

    @Test
    public void whenUserAllowedToActivate_ThenVerifyUserCanApproveReturns() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

        var peerApprovalPrivilege = new RequesterPrivilege<>(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
                "-",
                ActivationType.PEER_APPROVAL,
                RequesterPrivilege.Status.AVAILABLE);

        when(policyAnalyzer
                .findReviewerPrivelegeHolders(
                        peerApprovalPrivilege.id(),
                        peerApprovalPrivilege.activationType()))
                .thenReturn(Set.of(SAMPLE_APPROVING_USER));

        var request = Mockito.mock(ActivationRequest.class);
        when(request.duration()).thenReturn(catalog.options().minActivationDuration());
        when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
        when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVING_USER));
        when(request.requesterPrivilege()).thenReturn(peerApprovalPrivilege.id());
        when(request.activationType()).thenReturn(ActivationType.PEER_APPROVAL);

        catalog.verifyUserCanApprove(SAMPLE_APPROVING_USER, request);
    }

    // ---------------------------------------------------------------------------
    // listProjects.
    // ---------------------------------------------------------------------------

    @Test
    public void whenProjectQueryProvided_thenListProjectsPerformsProjectSearch() throws Exception {
        var resourceManager = Mockito.mock(ResourceManagerClient.class);
        when(resourceManager.searchProjectIds(eq("query")))
                .thenReturn(new TreeSet<>(Set.of(
                        new ProjectId("project-2"),
                        new ProjectId("project-3"),
                        new ProjectId("project-1"))));

        var catalog = new MpaProjectRoleCatalog(
                Mockito.mock(PolicyAnalyzerRepository.class),
                resourceManager,
                new MpaProjectRoleCatalog.Options(
                        "query",
                        Duration.ofMinutes(5),
                        1,
                        1));

        var projects = catalog.listProjects(SAMPLE_REQUESTING_USER);
        assertIterableEquals(
                List.of( // Sorted
                        new ProjectId("project-1"),
                        new ProjectId("project-2"),
                        new ProjectId("project-3")),
                projects);
    }

    @Test
    public void whenProjectQueryNotProvided_thenListProjectsPerformsPolicySearch() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
        when(policyAnalyzer.findProjectsWithRequesterPrivileges(eq(SAMPLE_REQUESTING_USER)))
                .thenReturn(new TreeSet<>(Set.of(
                        new ProjectId("project-2"),
                        new ProjectId("project-3"),
                        new ProjectId("project-1"))));

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        "",
                        Duration.ofMinutes(5),
                        1,
                        1));

        var projects = catalog.listProjects(SAMPLE_REQUESTING_USER);
        assertIterableEquals(
                List.of( // Sorted
                        new ProjectId("project-1"),
                        new ProjectId("project-2"),
                        new ProjectId("project-3")),
                projects);
    }

    // ---------------------------------------------------------------------------
    // listRequesterPrivileges.
    // ---------------------------------------------------------------------------

    @Test
    public void listRequesterPrivilegesReturnsAvailableAndActiveRequesterPrivileges() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
        when(policyAnalyzer.findRequesterPrivileges(
                eq(SAMPLE_REQUESTING_USER),
                eq(SAMPLE_PROJECT),
                eq(EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL)),
                any()))
                .thenReturn(RequesterPrivilegeSet.empty());

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(5),
                        1,
                        1));

        var privileges = catalog.listRequesterPrivileges(SAMPLE_REQUESTING_USER, SAMPLE_PROJECT);
        assertNotNull(privileges);

        verify(policyAnalyzer, times(1)).findRequesterPrivileges(
                SAMPLE_REQUESTING_USER,
                SAMPLE_PROJECT,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));
    }

    // ---------------------------------------------------------------------------
    // listReviewers.
    // ---------------------------------------------------------------------------

    @Test
    public void whenUserNotAllowedToActivateRole_ThenListReviewersThrowsException() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
        when(policyAnalyzer.findRequesterPrivileges(
                eq(SAMPLE_REQUESTING_USER),
                eq(SAMPLE_PROJECT),
                eq(EnumSet.of(ActivationType.PEER_APPROVAL)),
                any()))
                .thenReturn(RequesterPrivilegeSet.empty());

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(5),
                        1,
                        1));

        var roleBinding = new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE);
        assertThrows(
                AccessDeniedException.class,
                () -> catalog.listReviewers(SAMPLE_REQUESTING_USER, new RequesterPrivilege<ProjectRoleBinding>(
                        new ProjectRoleBinding(roleBinding), roleBinding.role(), ActivationType.PEER_APPROVAL,
                        Status.AVAILABLE)));
    }

    @Test
    public void whenUserAllowedToActivateRoleWithoutMpa_ThenListReviewersReturnsList() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
        var roleBinding = new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE);
        var privilege = new RequesterPrivilege<ProjectRoleBinding>(new ProjectRoleBinding(roleBinding),
                roleBinding.role(), ActivationType.PEER_APPROVAL, Status.AVAILABLE);
        when(policyAnalyzer.findRequesterPrivileges(
                eq(SAMPLE_REQUESTING_USER),
                eq(SAMPLE_PROJECT),
                eq(EnumSet.of(ActivationType.PEER_APPROVAL)),
                any()))
                .thenReturn(new RequesterPrivilegeSet<>(
                        new TreeSet<>(Set.of(new RequesterPrivilege<>(
                                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, "roles/different-role")),
                                "-",
                                ActivationType.PEER_APPROVAL,
                                RequesterPrivilege.Status.AVAILABLE))),
                        Set.of(),
                        Set.of()));

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(
                        null,
                        Duration.ofMinutes(5),
                        1,
                        1));

        assertThrows(
                AccessDeniedException.class,
                () -> catalog.listReviewers(SAMPLE_REQUESTING_USER, privilege));
    }

    @Test
    public void whenUserAllowedToActivatePeerApprovalPrivilege_ThenListReviewersExcludesUser() throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

        var peerApprovalPrivilege = new RequesterPrivilege<>(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
                "-",
                ActivationType.PEER_APPROVAL,
                RequesterPrivilege.Status.AVAILABLE);

        when(policyAnalyzer
                .findRequesterPrivileges(
                        eq(SAMPLE_REQUESTING_USER),
                        eq(SAMPLE_PROJECT),
                        eq(EnumSet.of(ActivationType.PEER_APPROVAL)),
                        eq(EnumSet.of(RequesterPrivilege.Status.AVAILABLE))))
                .thenReturn(new RequesterPrivilegeSet<>(
                        new TreeSet<>(Set.of(peerApprovalPrivilege)),
                        Set.of(),
                        Set.of()));

        when(policyAnalyzer
                .findReviewerPrivelegeHolders(
                        eq(peerApprovalPrivilege.id()),
                        eq(ActivationType.PEER_APPROVAL)))
                .thenReturn(Set.of(SAMPLE_REQUESTING_USER, SAMPLE_APPROVING_USER));

        var reviewers = catalog.listReviewers(SAMPLE_REQUESTING_USER, peerApprovalPrivilege);
        assertIterableEquals(Set.of(SAMPLE_APPROVING_USER), reviewers);
    }

    @Test
    public void whenUserAllowedToActivateExternalApprovalPrivilege_ThenListReviewersIncludesReviewers()
            throws Exception {
        var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

        var catalog = new MpaProjectRoleCatalog(
                policyAnalyzer,
                Mockito.mock(ResourceManagerClient.class),
                new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

        var externalApprovalPrivilege = new RequesterPrivilege<>(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
                "-",
                ActivationType.EXTERNAL_APPROVAL,
                RequesterPrivilege.Status.AVAILABLE);

        var reviewerEntitlement = new ReviewerPrivilege<ProjectRoleBinding>(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
                "-",
                EnumSet.of(ActivationType.EXTERNAL_APPROVAL));

        when(policyAnalyzer
                .findRequesterPrivileges(
                        eq(SAMPLE_REQUESTING_USER),
                        eq(SAMPLE_PROJECT),
                        eq(EnumSet.of(ActivationType.EXTERNAL_APPROVAL)),
                        eq(EnumSet.of(RequesterPrivilege.Status.AVAILABLE))))
                .thenReturn(new RequesterPrivilegeSet<>(
                        new TreeSet<>(Set.of(externalApprovalPrivilege)),
                        Set.of(),
                        Set.of()));

        when(policyAnalyzer
                .findReviewerPrivelegeHolders(
                        eq(reviewerEntitlement.id()),
                        eq(ActivationType.EXTERNAL_APPROVAL)))
                .thenReturn(Set.of(SAMPLE_REQUESTING_USER, SAMPLE_APPROVING_USER));

        var reviewers = catalog.listReviewers(SAMPLE_REQUESTING_USER, externalApprovalPrivilege);
        assertIterableEquals(Set.of(SAMPLE_APPROVING_USER), reviewers);
    }
}
