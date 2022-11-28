//
// Copyright 2021 Google LLC
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

package com.google.solutions.jitaccess.core.services;

import com.google.api.services.cloudasset.v1.model.*;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.adapters.AssetInventoryAdapter;
import com.google.solutions.jitaccess.core.adapters.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

public class TestRoleDiscoveryService {
  private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2", "user-2@example.com");
  private static final String SAMPLE_PROJECT_RESOURCE = "//cloudresourcemanager.googleapis.com/projects/project-1";
  private static final String SAMPLE_ROLE = "roles/resourcemanager.projectIamAdmin";
  private static final String JIT_CONDITION = "has({}.jitAccessConstraint)";
  private static final String MPA_CONDITION = "has({}.multiPartyApprovalConstraint)";

  private static IamPolicyAnalysisResult createIamPolicyAnalysisResult(
    String resource,
    String role,
    UserId user
  )
  {
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
    String evaluationResult
  )
  {
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
          new GoogleCloudAssetV1Identity().setName("user:" + user.getEmail()),
          new GoogleCloudAssetV1Identity().setName("serviceAccount:ignoreme@x.iam.gserviceaccount.com"),
          new GoogleCloudAssetV1Identity().setName("group:ignoreme@example.com"))));
  }

  // ---------------------------------------------------------------------
  // listEligibleRoleBindings.
  // ---------------------------------------------------------------------

  @Test
  public void whenAnalysisResultEmpty_ThenListEligibleRoleBindingsReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(new IamPolicyAnalysis());

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(0, roles.getRoleBindings().size());
  }

  @Test
  public void whenAnalysisResultContainsEmptyAcl_ThenListEligibleRoleBindingsReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName(SAMPLE_PROJECT_RESOURCE))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(0, roles.getRoleBindings().size());
  }

  @Test
  public void whenAnalysisContainsNoEligibleRoles_ThenListEligibleRoleBindingsReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(
            createIamPolicyAnalysisResult(
              SAMPLE_PROJECT_RESOURCE,
              SAMPLE_ROLE,
              SAMPLE_USER))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(0, roles.getRoleBindings().size());
  }

  @Test
  public void whenAnalysisContainsEligibleBinding_ThenListEligibleRoleBindingsReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(
            createConditionalIamPolicyAnalysisResult(
              SAMPLE_PROJECT_RESOURCE,
              SAMPLE_ROLE,
              SAMPLE_USER,
              JIT_CONDITION,
              "eligible binding",
              "CONDITIONAL"))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(1, roles.getRoleBindings().size());

    var roleBinding = roles.getRoleBindings().stream().findFirst().get();
    assertEquals("project-1", roleBinding.getResourceName());
    assertEquals(SAMPLE_ROLE, roleBinding.getRole());
    assertEquals(RoleBinding.RoleBindingStatus.ELIGIBLE, roleBinding.getStatus());
  }

  @Test
  public void whenAnalysisContainsMpaEligibleBinding_ThenListEligibleRoleBindingsReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(
            createConditionalIamPolicyAnalysisResult(
              SAMPLE_PROJECT_RESOURCE,
              SAMPLE_ROLE,
              SAMPLE_USER,
              MPA_CONDITION,
              "eligible binding",
              "CONDITIONAL"))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(1, roles.getRoleBindings().size());

    var roleBinding = roles.getRoleBindings().stream().findFirst().get();
    assertEquals("project-1", roleBinding.getResourceName());
    assertEquals(SAMPLE_ROLE, roleBinding.getRole());
    assertEquals(RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA, roleBinding.getStatus());
  }

  @Test
  public void whenAnalysisContainsMpaEligibleBindingAndJitEligibleBinding_ThenListEligibleRoleBindingsReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE,
      SAMPLE_ROLE,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
      "//cloudresourcemanager.googleapis.com/projects/project-2",
      SAMPLE_ROLE,
      SAMPLE_USER,
      MPA_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(jitEligibleBinding, mpaEligibleBinding)));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(2, roles.getRoleBindings().size());

    var roleBinding = roles.getRoleBindings().stream().findFirst().get();
    assertEquals("project-1", roleBinding.getResourceName());
    assertEquals(SAMPLE_ROLE, roleBinding.getRole());
    assertEquals(RoleBinding.RoleBindingStatus.ELIGIBLE, roleBinding.getStatus());

    roleBinding = roles.getRoleBindings().stream().skip(1).findFirst().get();
    assertEquals("project-2", roleBinding.getResourceName());
    assertEquals(SAMPLE_ROLE, roleBinding.getRole());
    assertEquals(RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA, roleBinding.getStatus());
  }

  @Test
  public void whenAnalysisContainsActivatedBinding_ThenListEligibleRoleBindingsReturnsMergedList() throws Exception {

    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var eligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE,
      SAMPLE_ROLE,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    var activatedBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE,
      SAMPLE_ROLE,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ELEVATION_CONDITION_TITLE,
      "TRUE");

    var activatedExpiredBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE,
      SAMPLE_ROLE,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ELEVATION_CONDITION_TITLE,
      "FALSE");

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(
            eligibleBinding,
            activatedBinding,
            activatedExpiredBinding
          )));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(1, roles.getRoleBindings().size());

    var roleBinding = roles.getRoleBindings().stream().findFirst().get();
    assertEquals("project-1", roleBinding.getResourceName());
    assertEquals(SAMPLE_ROLE, roleBinding.getRole());
    assertEquals(RoleBinding.RoleBindingStatus.ACTIVATED, roleBinding.getStatus());
  }

  @Test
  public void whenAnalysisContainsEligibleBindingWithExtraCondition_ThenBindingIsIgnored()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(
            createConditionalIamPolicyAnalysisResult(
              SAMPLE_PROJECT_RESOURCE,
              SAMPLE_ROLE,
              SAMPLE_USER,
              JIT_CONDITION + " && resource.name=='Foo'",
              "eligible binding with extra junk",
              "CONDITIONAL"))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(0, roles.getRoleBindings().size());
  }

  @Test
  public void whenAnalysisContainsInheritedEligibleBinding_ThenListEligibleRoleBindingsAsyncReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

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
          .setFullResourceName(SAMPLE_PROJECT_RESOURCE),
        new GoogleCloudAssetV1Resource()
          .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-2")))
      .setConditionEvaluation(new ConditionEvaluation()
        .setEvaluationValue("CONDITIONAL"));

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/folders/folder-1")
            .setAccessControlLists(List.of(
              parentFolderAcl,
              childFolderAndProjectAcl))
            .setIamBinding(new Binding()
              .setMembers(List.of("user:" + SAMPLE_USER))
              .setRole(SAMPLE_ROLE)
              .setCondition(new Expr().setExpression(JIT_CONDITION))))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(2, roles.getRoleBindings().size());

    assertEquals(
      new RoleBinding(
        "project-1",
        SAMPLE_PROJECT_RESOURCE,
        SAMPLE_ROLE,
        RoleBinding.RoleBindingStatus.ELIGIBLE),
      roles.getRoleBindings().get(0));

    assertEquals(
      new RoleBinding(
        "project-2",
        "//cloudresourcemanager.googleapis.com/projects/project-2",
        SAMPLE_ROLE,
        RoleBinding.RoleBindingStatus.ELIGIBLE),
      roles.getRoleBindings().get(1));
  }

  // ---------------------------------------------------------------------
  // listApproversForEligibleRoleBinding.
  // ---------------------------------------------------------------------

  @Test
  public void whenRoleIsNotEligible_ThenListApproversForEligibleRoleBindingThrowsException() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName(SAMPLE_PROJECT_RESOURCE))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    assertThrows(
      AccessDeniedException.class,
      () -> service.listApproversForEligibleRoleBinding(
        SAMPLE_USER,
        new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA)));
  }

  @Test
  public void whenCallerIsOnlyMpaEligibleUser_ThenListApproversForEligibleRoleBindingReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var mpaBindingResult = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE,
      SAMPLE_ROLE,
      SAMPLE_USER,
      MPA_CONDITION,
      "eligible binding",
      "CONDITIONAL");
    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));
    when(assetAdapter.findPermissionedPrincipalsByResource(anyString(), anyString(), anyString()))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var approvers = service.listApproversForEligibleRoleBinding(
        SAMPLE_USER,
        new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA));

    assertTrue(approvers.isEmpty());
  }

  @Test
  public void whenMpaEligibleUsersIncludesOtherUser_ThenListApproversForEligibleRoleBindingReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var mpaBindingResult = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE,
      SAMPLE_ROLE,
      SAMPLE_USER,
      MPA_CONDITION,
      "eligible binding",
      "CONDITIONAL");
    var mpaBindingResultForOtherUser = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE,
      SAMPLE_ROLE,
      SAMPLE_USER_2,
      MPA_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    when(assetAdapter.findAccessibleResourcesByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));
    when(assetAdapter.findPermissionedPrincipalsByResource(anyString(), anyString(), anyString()))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult, mpaBindingResultForOtherUser)));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var approvers = service.listApproversForEligibleRoleBinding(
      SAMPLE_USER,
      new RoleBinding(
        "project-1",
        SAMPLE_PROJECT_RESOURCE,
        SAMPLE_ROLE,
        RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA));

    assertEquals(1, approvers.size());
    assertEquals(SAMPLE_USER_2, approvers.stream().findFirst().get());
  }
}
