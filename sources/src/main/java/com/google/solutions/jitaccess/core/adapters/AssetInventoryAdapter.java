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

package com.google.solutions.jitaccess.core.adapters;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.cloudasset.v1.CloudAsset;
import com.google.api.services.cloudasset.v1.model.IamPolicyAnalysis;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.ApplicationVersion;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;

import javax.enterprise.context.RequestScoped;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Adapter for the Asset Inventory API.
 */
@RequestScoped
public class AssetInventoryAdapter {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final int ANALYZE_IAM_POLICY_TIMEOUT_SECS = 30;

  private final GoogleCredentials credentials;

  public AssetInventoryAdapter(GoogleCredentials credentials) throws IOException {
    Preconditions.checkNotNull(credentials, "credentials");

    this.credentials = credentials;
  }

  private CloudAsset createClient() throws IOException {
    try {
      return new CloudAsset
        .Builder(
        HttpTransport.newTransport(),
        new GsonFactory(),
        new HttpCredentialsAdapter(this.credentials))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a CloudAsset client failed", e);
    }
  }

  /**
   * Find resources accessible by a user, incl.:
   * - resources the user has been directly granted access to
   * - resources which the user can access because of a group membership
   *
   * NB. For group membership resolution to work, the service account must have the right
   * privileges in Cloud Identity/Workspace.
   */
  public IamPolicyAnalysis findAccessibleResourcesByUser(
      String scope,
      UserId user,
      boolean expandResources) throws AccessException, IOException {
    Preconditions.checkNotNull(scope, "scope");
    Preconditions.checkNotNull(user, "user");

    assert (scope.startsWith("organizations/")
      || scope.startsWith("folders/")
      || scope.startsWith("projects/"));

    try {
      return createClient().v1()
        .analyzeIamPolicy(scope)
        .setAnalysisQueryIdentitySelectorIdentity("user:" + user.getEmail())
        .setAnalysisQueryOptionsExpandResources(expandResources)
        .setAnalysisQueryConditionContextAccessTime(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        .setExecutionTimeout(String.format("%ds", ANALYZE_IAM_POLICY_TIMEOUT_SECS))
        .execute()
        .getMainAnalysis();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(String.format("Denied access to scope '%s': %s", scope, e.getMessage()), e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }

  /**
   * Find users or groups that have been (conditionally) granted a given role on a given resource.
   */
  public IamPolicyAnalysis findPermissionedPrincipalsByResource(
      String scope,
      String fullResourceName,
      String role) throws AccessException, IOException {
    Preconditions.checkNotNull(scope, "scope");
    Preconditions.checkNotNull(fullResourceName, "fullResourceName");
    Preconditions.checkNotNull(role, "role");

    assert (scope.startsWith("organizations/")
      || scope.startsWith("folders/")
      || scope.startsWith("projects/"));

    try {
      return createClient().v1()
        .analyzeIamPolicy(scope)
        .setAnalysisQueryResourceSelectorFullResourceName(fullResourceName)
        .setAnalysisQueryAccessSelectorRoles(List.of(role))
        .setAnalysisQueryConditionContextAccessTime(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        .setExecutionTimeout(String.format("%ds", ANALYZE_IAM_POLICY_TIMEOUT_SECS))
        .execute()
        .getMainAnalysis();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(String.format("Denied access to scope '%s': %s", scope, e.getMessage()), e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }
}
