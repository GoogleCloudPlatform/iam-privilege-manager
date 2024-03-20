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
import com.google.solutions.jitaccess.core.auth.UserId;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/**
 * Request for "MPA-activating" an entitlement.
 */
public abstract class MpaActivationRequest<TEntitlementId extends EntitlementId>
  extends ActivationRequest<TEntitlementId> {
  private final @NotNull Collection<UserId> reviewers;

  protected MpaActivationRequest(
    @NotNull ActivationId id,
    @NotNull UserId requestingUser,
    @NotNull Set<TEntitlementId> entitlements,
    @NotNull Set<UserId> reviewers,
    @NotNull String justification,
    @NotNull Instant startTime,
    @NotNull Duration duration) {
    super(
      id,
      requestingUser,
      entitlements,
      justification,
      startTime,
      duration);

    Preconditions.checkNotNull(reviewers, "reviewers");
    Preconditions.checkArgument(!reviewers.isEmpty());
    this.reviewers = reviewers;
  }

  public @NotNull Collection<UserId> reviewers() {
    return this.reviewers;
  }

  @Override
  public final @NotNull ActivationType type() {
    return ActivationType.MPA;
  }
}
