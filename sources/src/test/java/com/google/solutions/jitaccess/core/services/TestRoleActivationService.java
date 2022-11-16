//
// Copyright 2022 Google LLC
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
import com.google.solutions.jitaccess.core.adapters.ResourceManagerAdapter;
import com.google.solutions.jitaccess.core.adapters.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TestRoleActivationService {
  private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");
  private static final String SAMPLE_ROLE = "roles/resourcemanager.projectIamAdmin";
  private static final Pattern JUSTIFICATION_PATTERN = Pattern.compile(".*");
  private static final String ELIGIBILITY_CONDITION = "has({}.jitAccessConstraint)";

  // ---------------------------------------------------------------------
  // activateEligibleRoleBinding.
  // ---------------------------------------------------------------------

  @Test
  public void whenRoleIsNotEligible_ThenActivateEligibleRoleBindingAsyncThrowsException()
    throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1"))));

    var service = new RoleActivationService(
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true)),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(
      AccessDeniedException.class,
      () ->
        service.activateEligibleRoleBinding(
          SAMPLE_USER,
          new RoleBinding(
            "project-1",
            "//cloudresourcemanager.googleapis.com/projects/project-1",
            "roles/compute.admin",
            RoleBinding.RoleBindingStatus.ELIGIBLE),
          "justification"));
  }

  @Test
  public void whenRoleIsEligible_ThenActivateEligibleRoleBindingAddsBinding() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1")
            .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
              .setResources(List.of(new GoogleCloudAssetV1Resource()
                .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1")))
              .setConditionEvaluation(new ConditionEvaluation()
                .setEvaluationValue("CONDITIONAL"))))
            .setIamBinding(new Binding()
              .setMembers(List.of("user:" + SAMPLE_USER))
              .setRole(SAMPLE_ROLE)
              .setCondition(new Expr().setExpression(ELIGIBILITY_CONDITION))))));

    var service = new RoleActivationService(
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true)),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    var expiry =
      service.activateEligibleRoleBinding(
        SAMPLE_USER,
        new RoleBinding(
          "project-1",
          "//cloudresourcemanager.googleapis.com/projects/project-1",
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE),
        "justification");

    assertTrue(expiry.isAfter(OffsetDateTime.now()));
    assertTrue(expiry.isBefore(OffsetDateTime.now().plusMinutes(2)));

    verify(resourceAdapter)
      .addIamBinding(
        eq("project-1"),
        argThat(
          b ->
            b.getRole().equals(SAMPLE_ROLE)
              && b.getCondition().getExpression().contains("request.time < timestamp")
              && b.getCondition().getDescription().contains("justification")),
        eq(
          EnumSet.of(
            ResourceManagerAdapter.IamBindingOptions
              .REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE)),
        eq("justification"));
  }

  @Test
  public void whenRoleIsEligibleButJustificationDoesNotMatch_ThenActivateEligibleRoleBindingThrowsException()
    throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1")
            .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
              .setResources(List.of(new GoogleCloudAssetV1Resource()
                .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1")))
              .setConditionEvaluation(new ConditionEvaluation()
                .setEvaluationValue("CONDITIONAL"))))
            .setIamBinding(new Binding()
              .setMembers(List.of("user:" + SAMPLE_USER))
              .setRole(SAMPLE_ROLE)
              .setCondition(new Expr().setExpression(ELIGIBILITY_CONDITION))))));

    var service = new RoleActivationService(
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true)),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        Pattern.compile("^\\d+$"),
        Duration.ofMinutes(1)));

    assertThrows(
      AccessDeniedException.class,
      () ->
        service.activateEligibleRoleBinding(
          SAMPLE_USER,
          new RoleBinding(
            "project-1",
            "//cloudresourcemanager.googleapis.com/projects/project-1",
            SAMPLE_ROLE,
            RoleBinding.RoleBindingStatus.ELIGIBLE),
          "not-numeric"));
  }
}
