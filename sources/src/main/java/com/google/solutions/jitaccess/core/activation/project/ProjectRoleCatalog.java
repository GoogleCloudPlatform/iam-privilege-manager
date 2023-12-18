package com.google.solutions.jitaccess.core.activation.project;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AnnotatedResult;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.Entitlement;
import com.google.solutions.jitaccess.core.activation.EntitlementCatalog;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;

/**
 * Catalog for project-level role bindings.
 */
public abstract class ProjectRoleCatalog implements EntitlementCatalog<ProjectRoleId> {
  /**
   * List projects that the user has any entitlements for.
   */
  public abstract SortedSet<ProjectId> listProjects(
    UserId user
  ) throws AccessException, IOException;

  /**
   * List available entitlements.
   */
  public abstract AnnotatedResult<Entitlement<ProjectRoleId>> listEntitlements(
    UserId user,
    ProjectId projectId
  ) throws AccessException, IOException;

  /**
   * List available reviewers for (MPA-) activating an entitlement.
   */
  public abstract SortedSet<UserId> listReviewers(
    UserId requestingUser,
    ProjectRoleId entitlement
  ) throws AccessException, IOException;
}
