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

import com.google.api.services.cloudasset.v1.model.*;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
import com.google.solutions.jitaccess.core.clients.PolicyAnalyzerClient;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TestPolicyAnalyzerRepository {
    private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");
    private static final UserId SAMPLE_APPROVING_USER_1 = new UserId("approver-1", "approver-1@example.com");
    private static final UserId SAMPLE_APPROVING_USER_2 = new UserId("approver-2", "approver-2@example.com");
    private static final ProjectId SAMPLE_PROJECT_ID_1 = new ProjectId("project-1");
    private static final ProjectId SAMPLE_PROJECT_ID_2 = new ProjectId("project-2");
    private static final ProjectId SAMPLE_PROJECT_ID_3 = new ProjectId("project-3");
    private static final String SAMPLE_ROLE_1 = "roles/resourcemanager.role1";
    private static final String SAMPLE_ROLE_2 = "roles/resourcemanager.role2";
    private static final String SAMPLE_ROLE_3 = "roles/resourcemanager.role3";
    private static final String SAMPLE_ROLE_4 = "roles/resourcemanager.role4";
    private static final String SELF_APPROVAL_CONDITION = "has({}.jitAccessConstraint)";
    private static final String PEER_CONDITION = "has({}.multiPartyApprovalconstraint)";
    private static final String REQUESTER_CONDITION = "has({}.externalApprovalConstraint)";
    private static final String REVIEWER_CONDITION = "has({}.reviewerPrivilege)";

    private static IamPolicyAnalysisResult createIamPolicyAnalysisResult(
            String resource,
            String role,
            UserId user) {
        return new IamPolicyAnalysisResult()
                .setAttachedResourceFullName(resource)
                .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
                        .setResources(List.of(new GoogleCloudAssetV1Resource()
                                .setFullResourceName(resource)))))
                .setIamBinding(new Binding()
                        .setMembers(List.of("user:" + user))
                        .setRole(role));
    }

    private static IamPolicyAnalysisResult createConditionalIamPolicyAnalysisResult(
            String resource,
            String role,
            UserId user,
            String condition,
            String conditionTitle,
            String evaluationResult) {
        return new IamPolicyAnalysisResult()
                .setAttachedResourceFullName(resource)
                .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
                        .setResources(List.of(new GoogleCloudAssetV1Resource()
                                .setFullResourceName(resource)))
                        .setConditionEvaluation(new ConditionEvaluation()
                                .setEvaluationValue(evaluationResult))))
                .setIamBinding(new Binding()
                        .setMembers(List.of("user:" + user))
                        .setRole(role)
                        .setCondition(new Expr()
                                .setTitle(conditionTitle)
                                .setExpression(condition)))
                .setIdentityList(new GoogleCloudAssetV1IdentityList()
                        .setIdentities(List.of(
                                new GoogleCloudAssetV1Identity().setName("user:" + user.email),
                                new GoogleCloudAssetV1Identity()
                                        .setName("serviceAccount:ignoreme@x.iam.gserviceaccount.com"),
                                new GoogleCloudAssetV1Identity().setName("group:ignoreme@example.com"))));
    }

    // ---------------------------------------------------------------------
    // findProjectsWithRequesterPrivileges.
    // ---------------------------------------------------------------------

    @Test
    public void whenAnalysisResultEmpty_ThenFindProjectsWithRequesterPrivilegesReturnsEmptyList() throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.of("resourcemanager.projects.get")),
                        eq(Optional.empty()),
                        eq(true)))
                .thenReturn(new IamPolicyAnalysis());

        var analyzer = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var projectIds = analyzer.findProjectsWithRequesterPrivileges(SAMPLE_USER);
        assertNotNull(projectIds);
        assertEquals(0, projectIds.size());
    }

    @Test
    public void whenAnalysisResultContainsAcsWithUnrecognizedConditions_ThenFindProjectsWithRequesterPrivilegesReturnsEmptyList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);
        var resourceManagerAdapter = Mockito.mock(ResourceManagerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.of("resourcemanager.projects.get")),
                        eq(Optional.empty()),
                        eq(true)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        "a==b",
                                        "unrecognized condition",
                                        "TRUE"))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var projectIds = service.findProjectsWithRequesterPrivileges(SAMPLE_USER);
        assertNotNull(projectIds);
        assertEquals(0, projectIds.size());
    }

    @Test
    public void whenAnalysisContainsPermanentBinding_ThenFindProjectsWithRequesterPrivilegesReturnsProjectId()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);
        var resourceManagerAdapter = Mockito.mock(ResourceManagerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.of("resourcemanager.projects.get")),
                        eq(Optional.empty()),
                        eq(true)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var projectIds = service.findProjectsWithRequesterPrivileges(SAMPLE_USER);
        assertNotNull(projectIds);
        assertEquals(1, projectIds.size());
        assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_1));
    }

    @Test
    public void whenAnalysisContainsEligibleBindings_ThenfindProjectsWithRequesterPrivilegesReturnsProjectIds()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);
        var resourceManagerAdapter = Mockito.mock(ResourceManagerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.of("resourcemanager.projects.get")),
                        eq(Optional.empty()),
                        eq(true)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        SELF_APPROVAL_CONDITION,
                                        "eligible binding",
                                        "CONDITIONAL"),
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_2.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        PEER_CONDITION,
                                        "eligible binding",
                                        "CONDITIONAL"),
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_3.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        REQUESTER_CONDITION,
                                        "eligible binding",
                                        "CONDITIONAL"),
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_3.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        REVIEWER_CONDITION,
                                        "eligible binding",
                                        "CONDITIONAL"))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var projectIds = service.findProjectsWithRequesterPrivileges(SAMPLE_USER);
        assertNotNull(projectIds);
        assertEquals(3, projectIds.size());
        assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_1));
        assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_2));
        assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_3));
    }

    // ---------------------------------------------------------------------
    // FindRequesterPrivileges.
    // ---------------------------------------------------------------------

    @Test
    public void whenAnalysisResultEmpty_ThenFindRequesterPrivilegesReturnsEmptyList() throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis());

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(0, privileges.allRequesterPrivileges().size());
    }

    @Test
    public void whenAnalysisResultContainsEmptyAcl_ThenFindRequesterPrivilegesReturnsEmptyList() throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                new IamPolicyAnalysisResult()
                                        .setAttachedResourceFullName(SAMPLE_PROJECT_ID_1.getFullResourceName())
                                        .setIamBinding(new Binding().setRole("role")))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(0, privileges.allRequesterPrivileges().size());
    }

    @Test
    public void whenAnalysisContainsNoEligibleRoles_ThenFindRequesterPrivilegesReturnsEmptyList() throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(0, privileges.allRequesterPrivileges().size());
    }

    @Test
    public void whenAnalysisContainsJitEligibleBinding_ThenFindRequesterPrivilegesReturnsList() throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        SELF_APPROVAL_CONDITION,
                                        "eligible binding",
                                        "CONDITIONAL"))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(1, privileges.allRequesterPrivileges().size());

        var privilege = privileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(SAMPLE_ROLE_1, privilege.name());
        assertEquals(ActivationType.SELF_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());
    }

    @Test
    public void whenAnalysisContainsDuplicateJitEligibleBinding_ThenFindRequesterPrivilegesReturnsList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        SELF_APPROVAL_CONDITION,
                                        "eligible binding #1",
                                        "CONDITIONAL"),
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        SELF_APPROVAL_CONDITION,
                                        "eligible binding #2",
                                        "CONDITIONAL"))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(1, privileges.allRequesterPrivileges().size());

        var privilege = privileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(SAMPLE_ROLE_1, privilege.name());
        assertEquals(ActivationType.SELF_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());
    }

    @Test
    public void whenAnalysisContainsPeerApprovalEligibleBinding_ThenFindRequesterPrivilegesReturnsList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        PEER_CONDITION,
                                        "eligible binding",
                                        "CONDITIONAL"))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(1, privileges.allRequesterPrivileges().size());

        var privilege = privileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(SAMPLE_ROLE_1, privilege.name());
        assertEquals(ActivationType.PEER_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());
    }

    @Test
    public void whenAnalysisContainsDuplicatePeerApprovalEligibleBinding_ThenFindRequesterPrivilegesReturnsList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        PEER_CONDITION,
                                        "eligible binding # 1",
                                        "CONDITIONAL"),
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        PEER_CONDITION,
                                        "eligible binding # 2",
                                        "CONDITIONAL"))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(1, privileges.allRequesterPrivileges().size());

        var privilege = privileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(SAMPLE_ROLE_1, privilege.name());
        assertEquals(ActivationType.PEER_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());
    }

    @Test
    public void whenAnalysisContainsExternalApprovalEligibleBinding_ThenFindRequesterPrivilegesReturnsList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        REQUESTER_CONDITION,
                                        "eligible binding",
                                        "CONDITIONAL"))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(1, privileges.allRequesterPrivileges().size());

        var privilege = privileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(SAMPLE_ROLE_1, privilege.name());
        assertEquals(ActivationType.EXTERNAL_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());
    }

    @Test
    public void whenAnalysisContainsDuplicateExternalApprovalEligibleBinding_ThenFindRequesterPrivilegesReturnsList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        REQUESTER_CONDITION,
                                        "eligible binding # 1",
                                        "CONDITIONAL"),
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        REQUESTER_CONDITION,
                                        "eligible binding # 2",
                                        "CONDITIONAL"))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(1, privileges.allRequesterPrivileges().size());

        var privilege = privileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(SAMPLE_ROLE_1, privilege.name());
        assertEquals(ActivationType.EXTERNAL_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());
    }

    @Test
    public void whenAnalysisContainsMultipleEligibleBindingForDifferentRoles_ThenFindRequesterPrivilegesReturnsList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                SELF_APPROVAL_CONDITION,
                "JIT-eligible binding",
                "CONDITIONAL");

        var peerApprovalEligibleBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_2,
                SAMPLE_USER,
                PEER_CONDITION,
                "Peer approval eligible binding",
                "CONDITIONAL");

        var externalApprovalEligibleBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_3,
                SAMPLE_USER,
                REQUESTER_CONDITION,
                "External approval eligible binding",
                "CONDITIONAL");

        when(assetAdapter.findAccessibleResourcesByUser(
                anyString(),
                eq(SAMPLE_USER),
                eq(Optional.empty()),
                eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(jitEligibleBinding, peerApprovalEligibleBinding,
                                externalApprovalEligibleBinding)));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(3, privileges.allRequesterPrivileges().size());

        var privilege = privileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_3, privilege.id().roleBinding().role());
        assertEquals(ActivationType.EXTERNAL_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());

        privilege = privileges.allRequesterPrivileges().stream().skip(1).findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_2, privilege.id().roleBinding().role());
        assertEquals(ActivationType.PEER_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());

        privilege = privileges.allRequesterPrivileges().stream().skip(2).findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(ActivationType.SELF_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());
    }

    @Test
    public void whenAnalysisContainsMultipleBindingsForSameRole_ThenFindRequesterPrivilegesReturnsList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                SELF_APPROVAL_CONDITION,
                "JIT-eligible binding",
                "CONDITIONAL");

        var peerApprovalEligibleBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                PEER_CONDITION,
                "Peer approval eligible binding",
                "CONDITIONAL");

        var externalApprovalEligibleBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                REQUESTER_CONDITION,
                "External approval eligible binding",
                "CONDITIONAL");

        when(assetAdapter.findAccessibleResourcesByUser(
                anyString(),
                eq(SAMPLE_USER),
                eq(Optional.empty()),
                eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(jitEligibleBinding, peerApprovalEligibleBinding,
                                externalApprovalEligibleBinding)));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        // All types -> all retained.
        var allPrivileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL,
                        ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(allPrivileges.warnings());
        assertEquals(0, allPrivileges.warnings().size());

        assertNotNull(allPrivileges.allRequesterPrivileges());
        assertEquals(3, allPrivileges.allRequesterPrivileges().size());

        var privilege = allPrivileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(ActivationType.EXTERNAL_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());

        privilege = allPrivileges.allRequesterPrivileges().stream().skip(1).findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(ActivationType.PEER_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());

        privilege = allPrivileges.allRequesterPrivileges().stream().skip(2).findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(ActivationType.SELF_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());

        // Self approval only -> Other bindings are ignored.
        var selfApprovalPrivilege = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));
        assertEquals(1, selfApprovalPrivilege.allRequesterPrivileges().size());
        privilege = selfApprovalPrivilege.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(ActivationType.SELF_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());

        // Peer only -> Other bindings are ignored.
        var peerPrivilege = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.PEER_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));
        assertEquals(1, peerPrivilege.allRequesterPrivileges().size());
        privilege = peerPrivilege.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(ActivationType.PEER_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());

        // External only -> Other bindings are ignored.
        var requesterPrivilege = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.EXTERNAL_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));
        assertEquals(1, peerPrivilege.allRequesterPrivileges().size());
        privilege = requesterPrivilege.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(ActivationType.EXTERNAL_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.AVAILABLE, privilege.status());
    }

    @Test
    public void whenAnalysisContainsActivatedBinding_ThenFindRequesterPrivilegesReturnsMergedList() throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        var eligibleBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                SELF_APPROVAL_CONDITION,
                "eligible binding",
                "CONDITIONAL");

        var activatedBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                "time ...",
                PrivilegeFactory.ACTIVATION_CONDITION_TITLE,
                "TRUE");

        var activatedExpiredBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                "time ...",
                PrivilegeFactory.ACTIVATION_CONDITION_TITLE,
                "FALSE");

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                eligibleBinding,
                                activatedBinding,
                                activatedExpiredBinding)));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(1, privileges.allRequesterPrivileges().size());

        var privilege = privileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(SAMPLE_ROLE_1, privilege.id().roleBinding().role());
        assertEquals(ActivationType.SELF_APPROVAL, privilege.activationType());
        assertEquals(RequesterPrivilege.Status.ACTIVE, privilege.status());
    }

    @Test
    public void whenAnalysisContainsEligibleBindingWithExtraCondition_ThenBindingIsIgnored()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                createConditionalIamPolicyAnalysisResult(
                                        SAMPLE_PROJECT_ID_1.getFullResourceName(),
                                        SAMPLE_ROLE_1,
                                        SAMPLE_USER,
                                        SELF_APPROVAL_CONDITION + " && resource.name=='Foo'",
                                        "eligible binding with extra junk",
                                        "CONDITIONAL"))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(0, privileges.allRequesterPrivileges().size());
    }

    @Test
    public void whenAnalysisContainsInheritedEligibleBinding_ThenFindRequesterPrivilegesReturnsList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        var parentFolderAcl = new GoogleCloudAssetV1AccessControlList()
                .setResources(List.of(new GoogleCloudAssetV1Resource()
                        .setFullResourceName("//cloudresourcemanager.googleapis.com/folders/folder-1")))
                .setConditionEvaluation(new ConditionEvaluation()
                        .setEvaluationValue("CONDITIONAL"));

        var childFolderAndProjectAcl = new GoogleCloudAssetV1AccessControlList()
                .setResources(List.of(
                        new GoogleCloudAssetV1Resource()
                                .setFullResourceName("//cloudresourcemanager.googleapis.com/folders/folder-1"),
                        new GoogleCloudAssetV1Resource()
                                .setFullResourceName(SAMPLE_PROJECT_ID_1.getFullResourceName()),
                        new GoogleCloudAssetV1Resource()
                                .setFullResourceName(SAMPLE_PROJECT_ID_2.getFullResourceName())))
                .setConditionEvaluation(new ConditionEvaluation()
                        .setEvaluationValue("CONDITIONAL"));

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
                                .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/folders/folder-1")
                                .setAccessControlLists(List.of(
                                        parentFolderAcl,
                                        childFolderAndProjectAcl))
                                .setIamBinding(new Binding()
                                        .setMembers(List.of("user:" + SAMPLE_USER))
                                        .setRole(SAMPLE_ROLE_1)
                                        .setCondition(new Expr().setExpression(SELF_APPROVAL_CONDITION))))));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.AVAILABLE, RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(1, privileges.allRequesterPrivileges().size());

        var first = privileges.allRequesterPrivileges().first();
        assertEquals(
                new RoleBinding(SAMPLE_PROJECT_ID_1, SAMPLE_ROLE_1),
                first.id().roleBinding());
        assertEquals(
                ActivationType.SELF_APPROVAL,
                first.activationType());
    }

    @Test
    public void whenStatusSetToActiveOnly_ThenFindRequesterPrivilegesOnlyReturnsActivatedBindings() throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                SELF_APPROVAL_CONDITION,
                "eligible binding",
                "CONDITIONAL");

        var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_2,
                SAMPLE_USER,
                PEER_CONDITION,
                "Peer approval eligible binding",
                "CONDITIONAL");

        var activatedBinding = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                "time ...",
                PrivilegeFactory.ACTIVATION_CONDITION_TITLE,
                "TRUE");

        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis()
                        .setAnalysisResults(List.of(
                                jitEligibleBinding,
                                mpaEligibleBinding,
                                activatedBinding)));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var privileges = service.findRequesterPrivileges(
                SAMPLE_USER,
                SAMPLE_PROJECT_ID_1,
                EnumSet.of(ActivationType.SELF_APPROVAL, ActivationType.PEER_APPROVAL),
                EnumSet.of(RequesterPrivilege.Status.ACTIVE));

        assertNotNull(privileges.warnings());
        assertEquals(0, privileges.warnings().size());

        assertNotNull(privileges.allRequesterPrivileges());
        assertEquals(1, privileges.allRequesterPrivileges().size());

        var privilege = privileges.allRequesterPrivileges().stream().findFirst().get();
        assertEquals(SAMPLE_PROJECT_ID_1, privilege.id().projectId());
        assertEquals(RequesterPrivilege.Status.ACTIVE, privilege.status());
    }

    // ---------------------------------------------------------------------
    // findReviewerPrivelegeHolders.
    // ---------------------------------------------------------------------

    @Test
    public void whenAllUsersSelfApprovalEligible_ThenFindReviewerPrivelegeHoldersReturnsEmptyList()
            throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);

        var mpaBindingResult = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                SELF_APPROVAL_CONDITION,
                "eligible binding",
                "CONDITIONAL");
        when(assetAdapter
                .findAccessibleResourcesByUser(
                        anyString(),
                        eq(SAMPLE_USER),
                        eq(Optional.empty()),
                        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
                        eq(false)))
                .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));
        when(assetAdapter.findPermissionedPrincipalsByResource(anyString(), anyString(), anyString()))
                .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var approvers = service.findReviewerPrivelegeHolders(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT_ID_1, SAMPLE_ROLE_1)),
                ActivationType.PEER_APPROVAL);

        assertTrue(approvers.isEmpty());
    }

    @Test
    public void whenUsersPeerEligible_ThenFindReviewerPrivelegeHoldersReturnsList() throws Exception {
        var assetAdapter = Mockito.mock(PolicyAnalyzerClient.class);
        var resourceManagerAdapter = Mockito.mock(ResourceManagerClient.class);

        var jitBindingResult = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_USER,
                SELF_APPROVAL_CONDITION,
                "eligible binding",
                "CONDITIONAL");
        var mpaBindingResult1 = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_APPROVING_USER_1,
                PEER_CONDITION,
                "eligible binding",
                "CONDITIONAL");
        var mpaBindingResult2 = createConditionalIamPolicyAnalysisResult(
                SAMPLE_PROJECT_ID_1.getFullResourceName(),
                SAMPLE_ROLE_1,
                SAMPLE_APPROVING_USER_2,
                PEER_CONDITION,
                "eligible binding",
                "CONDITIONAL");

        when(assetAdapter.findPermissionedPrincipalsByResource(anyString(), anyString(), anyString()))
                .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(
                        jitBindingResult,
                        mpaBindingResult1,
                        mpaBindingResult2)));

        var service = new PolicyAnalyzerRepository(
                assetAdapter,
                new PolicyAnalyzerRepository.Options("organizations/0"));

        var approvers = service.findReviewerPrivelegeHolders(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT_ID_1, SAMPLE_ROLE_1)),
                ActivationType.PEER_APPROVAL);

        assertEquals(2, approvers.size());
        assertIterableEquals(
                List.of(SAMPLE_APPROVING_USER_1, SAMPLE_APPROVING_USER_2),
                approvers);
    }
}
