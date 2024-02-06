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

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.clients.IamTemporaryAccessConditions;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import jakarta.enterprise.context.Dependent;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Activator for project roles.
 */
@Dependent
public class ProjectRoleActivator extends RequesterPrivilegeActivator<ProjectRoleBinding> {
    private final ResourceManagerClient resourceManagerClient;

    public ProjectRoleActivator(
            RequesterPrivilegeCatalog<ProjectRoleBinding> catalog,
            ResourceManagerClient resourceManagerClient,
            JustificationPolicy policy) {
        super(catalog, policy);

        Preconditions.checkNotNull(resourceManagerClient, "resourceManagerClient");

        this.resourceManagerClient = resourceManagerClient;
    }

    private void provisionTemporaryBinding(
            String bindingDescription,
            ProjectId projectId,
            UserId user,
            String role,
            Instant startTime,
            Duration duration) throws AccessException, AlreadyExistsException, IOException {

        //
        // Add time-bound IAM binding.
        //
        // Replace existing bindings for same user and role to avoid
        // accumulating junk, and to prevent hitting the binding limit.
        //

        var binding = new Binding()
                .setMembers(List.of("user:" + user))
                .setRole(role)
                .setCondition(new com.google.api.services.cloudresourcemanager.v3.model.Expr()
                        .setTitle(PrivilegeFactory.ACTIVATION_CONDITION_TITLE)
                        .setDescription(bindingDescription)
                        .setExpression(IamTemporaryAccessConditions.createExpression(startTime, duration)));

        // TODO(later): Add bindings in a single request.

        this.resourceManagerClient.addProjectIamBinding(
                projectId,
                binding,
                EnumSet.of(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS),
                bindingDescription);
    }

    // -------------------------------------------------------------------------
    // Overrides.
    // -------------------------------------------------------------------------

    @Override
    protected void provisionAccess(
            UserId approvingUser,
            ActivationRequest<ProjectRoleBinding> request) throws AccessException, AlreadyExistsException, IOException {

        Preconditions.checkNotNull(request, "request");

        var bindingDescription = String.format(
                "Approved by %s, justification: %s",
                approvingUser.email,
                request.justification());

        //
        // NB. The start/end time for the binding is derived from the approval token. If
        // multiple
        // reviewers try to approve the same token, the resulting condition (and
        // binding) will
        // be the same. This is important so that we can use the FAIL_IF_BINDING_EXISTS
        // flag.
        //

        provisionTemporaryBinding(
                bindingDescription,
                ProjectActivationRequest.projectId(request),
                request.requestingUser(),
                request.requesterPrivilege().roleBinding().role(),
                request.startTime(),
                request.duration());
    }

    @Override
    public JsonWebTokenConverter<com.google.solutions.jitaccess.core.catalog.ActivationRequest<ProjectRoleBinding>> createTokenConverter() {
        return new JsonWebTokenConverter<>() {
            @Override
            public JsonWebToken.Payload convert(
                    com.google.solutions.jitaccess.core.catalog.ActivationRequest<ProjectRoleBinding> request) {
                var roleBinding = request.requesterPrivilege().roleBinding();

                return new JsonWebToken.Payload()
                        .setJwtId(request.id().toString())
                        .set("beneficiary", request.requestingUser().email)
                        .set("reviewers", request.reviewers().stream().map(id -> id.email).collect(Collectors.toList()))
                        .set("resource", roleBinding.fullResourceName())
                        .set("role", roleBinding.role())
                        .set("type", request.activationType().toString())
                        .set("justification", request.justification())
                        .set("start", request.startTime().getEpochSecond())
                        .set("end", request.endTime().getEpochSecond());
            }

            @Override
            public com.google.solutions.jitaccess.core.catalog.ActivationRequest<ProjectRoleBinding> convert(
                    JsonWebToken.Payload payload) {
                var roleBinding = new RoleBinding(
                        payload.get("resource").toString(),
                        payload.get("role").toString());

                var startTime = ((Number) payload.get("start")).longValue();
                var endTime = ((Number) payload.get("end")).longValue();

                return new ActivationRequest<ProjectRoleBinding>(
                        new ActivationId(payload.getJwtId()),
                        new UserId(payload.get("beneficiary").toString()),
                        ((List<String>) payload.get("reviewers"))
                                .stream()
                                .map(email -> new UserId(email))
                                .collect(Collectors.toSet()),
                        new ProjectRoleBinding(roleBinding),
                        ActivationType.valueOf(payload.get("type").toString()),
                        payload.get("justification").toString(),
                        Instant.ofEpochSecond(startTime),
                        Duration.ofSeconds(endTime - startTime));
            }
        };
    }
}
