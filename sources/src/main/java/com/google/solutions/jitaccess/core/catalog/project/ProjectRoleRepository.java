package com.google.solutions.jitaccess.core.catalog.project;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserEmail;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.EntitlementSet;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.SortedSet;

/**
 * Repository for ProjectRoleBinding-based entitlements.
 */
public abstract class ProjectRoleRepository {

  /**
   * Find projects that a user has standing, JIT-, or MPA-eligible access to.
   */
  abstract SortedSet<ProjectId> findProjectsWithEntitlements(
    UserEmail user
  ) throws AccessException, IOException;

  /**
   * List entitlements for the given user.
   */
  abstract EntitlementSet<ProjectRoleBinding> findEntitlements(
    UserEmail user,
    ProjectId projectId,
    EnumSet<ActivationType> typesToInclude,
    EnumSet<Entitlement.Status> statusesToInclude
  ) throws AccessException, IOException;

  /**
   * List users that hold an eligible role binding.
   */
  abstract Set<UserEmail> findEntitlementHolders(
    ProjectRoleBinding roleBinding,
    ActivationType activationType
  ) throws AccessException, IOException;
}
