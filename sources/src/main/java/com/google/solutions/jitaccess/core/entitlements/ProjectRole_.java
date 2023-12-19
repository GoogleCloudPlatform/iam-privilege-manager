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

package com.google.solutions.jitaccess.core.entitlements;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.ProjectId;

/**
 * Represents an eligible role on a project.
 */
public record ProjectRole_( // TODO: delete
  RoleBinding roleBinding,
  ProjectRole_.Status status
) implements Comparable<ProjectRole_> {

  public ProjectRole_ {
    Preconditions.checkNotNull(roleBinding);
    Preconditions.checkNotNull(status);
    Preconditions.checkArgument(ProjectId.isProjectFullResourceName(roleBinding.fullResourceName()));
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", this.roleBinding, this.status);
  }

  /**
   * Return the unqualified project ID.
   */
  public ProjectId getProjectId() {
    return ProjectId.fromFullResourceName(this.roleBinding.fullResourceName());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    var that = (ProjectRole_) o;
    return this.roleBinding.equals(that.roleBinding) && this.status.equals(that.status);
  }

  @Override
  public int compareTo(ProjectRole_ o) {
    return this.roleBinding.compareTo(o.roleBinding);
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public enum Status {
    /**
     * Role binding can be activated using self-approval ("JIT approval")
     */
    ELIGIBLE_FOR_JIT,

    /**
     * Role binding can be activated using multi party-approval ("MPA approval")
     */
    ELIGIBLE_FOR_MPA,

    /**
     * Eligible role binding has been activated
     */
    ACTIVATED,

    /**
     * Approval pending
     */
    ACTIVATION_PENDING
  }
}
