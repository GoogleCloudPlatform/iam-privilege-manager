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

package com.google.solutions.jitaccess.core.catalog;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.auth.UserId;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Activates entitlements, for example by modifying IAM policies.
 */
public abstract class EntitlementActivator {

  private final @NotNull JustificationPolicy policy;
  private final @NotNull Catalog catalog;

  /**
   * @return maximum number of roles that can be requested at once.
   */
  public abstract int maximumNumberOfEntitlementsPerJitRequest();

  protected EntitlementActivator(
    @NotNull Catalog catalog,
    @NotNull JustificationPolicy policy
  ) {
    Preconditions.checkNotNull(catalog, "catalog");
    Preconditions.checkNotNull(policy, "policy");

    this.catalog = catalog;
    this.policy = policy;
  }

  /**
   * Create a new request to activate an entitlement that permits self-approval.
   */
  public final @NotNull JitActivationRequest createJitRequest(
    @NotNull CatalogUserContext requestingUserContext,
    @NotNull Set<EntitlementId> entitlements,
    @NotNull String justification,
    @NotNull Instant startTime,
    @NotNull Duration duration
  ) {
    Preconditions.checkArgument(
      entitlements.size() <= this.maximumNumberOfEntitlementsPerJitRequest(),
      String.format(
        "The number of roles exceeds the allowed maximum of %d",
        this.maximumNumberOfEntitlementsPerJitRequest()));
    Preconditions.checkArgument(
      startTime.isAfter(Instant.now().minus(Duration.ofMinutes(1))),
      "Start time must not be in the past");

    //
    // NB. There's no need to verify access at this stage yet.
    //
    return new JitRequest(
      ActivationId.newId(ActivationType.JIT),
      requestingUserContext.user(),
      entitlements,
      justification,
      startTime,
      duration);
  }

  /**
   * Create a new request to activate an entitlement that requires
   * multi-party approval.
   */
  public @NotNull MpaActivationRequest createMpaRequest(
    @NotNull CatalogUserContext requestingUserContext,
    @NotNull Set<EntitlementId> entitlements,
    @NotNull Set<UserId> reviewers,
    @NotNull String justification,
    @NotNull Instant startTime,
    @NotNull Duration duration
  ) throws AccessException, IOException {

    Preconditions.checkArgument(
      startTime.isAfter(Instant.now().minus(Duration.ofMinutes(1))),
      "Start time must not be in the past");

    //
    // Check that the justification is ok.
    //
    policy.checkJustification(requestingUserContext.user(), justification);

    var request = new MpaRequest(
      ActivationId.newId(ActivationType.MPA),
      requestingUserContext.user(),
      entitlements,
      reviewers,
      justification,
      startTime,
      duration);

    //
    // Pre-verify access to avoid sending an MPA requests for which
    // the access check will fail later.
    //
    this.catalog.verifyUserCanRequest(
      requestingUserContext,
      request);

    return request;
  }

  /**
   * Activate an entitlement that permits self-approval.
   */
  public final @NotNull Activation activate(
    @NotNull CatalogUserContext userContext,
    @NotNull JitActivationRequest request
  ) throws AccessException, AlreadyExistsException, IOException
  {
    //
    // Check that the justification is ok.
    //
    policy.checkJustification(request.requestingUser(), request.justification());

    //
    // Check that the user is (still) allowed to activate this entitlement.
    //
    this.catalog.verifyUserCanRequest(userContext, request);

    //
    // Request is legit, apply it.
    //
    return provisionAccess(request);
  }

  /**
   * Approve another user's request.
   */
  public final @NotNull Activation approve(
    @NotNull CatalogUserContext userContext,
    @NotNull MpaActivationRequest request
  ) throws AccessException, AlreadyExistsException, IOException
  {
    Preconditions.checkNotNull(policy, "policy");

    if (userContext.user().equals(request.requestingUser())) {
      throw new IllegalArgumentException(
        "MPA activation requires the caller and beneficiary to be the different");
    }

    if (!request.reviewers().contains(userContext.user())) {
      throw new AccessDeniedException(
        String.format("The request does not permit approval by %s", userContext.user()));
    }

    //
    // Check that the justification is ok.
    //
    policy.checkJustification(request.requestingUser(), request.justification());

    //
    // Check that the user is (still) allowed to request this entitlement.
    //
    this.catalog.verifyUserCanRequest(userContext, request);

    //
    // Check that the approving user is (still) allowed to approve this entitlement.
    //
    this.catalog.verifyUserCanApprove(userContext, request);

    //
    // Request is legit, apply it.
    //
    return provisionAccess(userContext.user(), request);
  }

  /**
   * Apply a request.
   */
  protected abstract Activation provisionAccess(
    @NotNull JitActivationRequest request
  ) throws AccessException, AlreadyExistsException, IOException;


  /**
   * Apply a request.
   */
  protected abstract Activation provisionAccess(
    @NotNull UserId approvingUser,
    @NotNull MpaActivationRequest request
  ) throws AccessException, AlreadyExistsException, IOException;

  /**
   * Create a converter for turning MPA requests into JWTs, and
   * vice versa.
   */
  public abstract @NotNull JsonWebTokenConverter<MpaActivationRequest> createTokenConverter();

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  protected static class JitRequest extends JitActivationRequest {
    public JitRequest(
      @NotNull ActivationId id,
      @NotNull UserId requestingUser,
      @NotNull Set<EntitlementId> entitlements,
      @NotNull String justification,
      @NotNull Instant startTime,
      @NotNull Duration duration
    ) {
      super(id, requestingUser, entitlements, justification, startTime, duration);
    }
  }

  protected static class MpaRequest extends MpaActivationRequest {
    public MpaRequest(
      @NotNull ActivationId id,
      @NotNull UserId requestingUser,
      @NotNull Set<EntitlementId> entitlements,
      @NotNull Set<UserId> reviewers,
      @NotNull String justification,
      @NotNull Instant startTime,
      @NotNull Duration duration
    ) {
      super(id, requestingUser, entitlements, reviewers, justification, startTime, duration);

      if (entitlements.size() != 1) {
        throw new IllegalArgumentException("Only one entitlement can be activated at a time");
      }
    }
  }
}
