"use strict"

/** Manage browser-local storage */
class LocalSettings {
    get lastProjectId() {
        if (typeof (Storage) !== "undefined") {
            return localStorage.getItem("projectId");
        }
        else {
            return null;
        }
    }

    set lastProjectId(projectId) {
        if (typeof (Storage) !== "undefined") {
            localStorage.setItem("projectId", projectId);
        }
    }
}

class Model {
    _getHeaders() {
        return { "X-JITACCESS": "1" };
    }

    _formatError(error) {
        let message = (error.responseJSON && error.responseJSON.message)
            ? error.responseJSON.message
            : "";
        return `${message} (HTTP ${error.status}: ${error.statusText})`;
    }

    get policy() {
        console.assert(this._policy);
        return this._policy;
    }

    async fetchPolicy() {
        try {
            await new Promise(r => setTimeout(r, 200));
            this._policy = await $.ajax({
                url: "/api/policy",
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    async listProjects() {
        try {
            return await $.ajax({
                url: "/api/projects",
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    async searchProjects(projectIdPrefix) {
        console.assert(projectIdPrefix);

        // Avoid kicking off multiple requests in parallel.
        if (!this._listProjectsPromise) {
            this._listProjectsPromise = this.listProjects();
        }

        let projectsResult = await this._listProjectsPromise;
        if (!projectsResult.projects) {
            return [];
        }

        return projectsResult.projects.filter(
            projectId => projectId.toLowerCase().includes(projectIdPrefix.trim().toLowerCase()));
    }

    /** List eligible roles */
    async listRoles(projectId) {
        console.assert(projectId);

        try {
            return await $.ajax({
                url: `/api/projects/${projectId}/roles`,
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** List peers that can approve a request */
    async listPeers(projectId, role) {
        console.assert(projectId);
        console.assert(role);

        try {
            return await $.ajax({
                url: `/api/projects/${projectId}/peers?role=${encodeURIComponent(role)}`,
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** Activate roles without peer approval */
    async selfActivateRoles(projectId, roles, justification) {
        console.assert(projectId);
        console.assert(roles.length > 0);
        console.assert(justification)

        try {
            return await $.ajax({
                type: "POST",
                url: `/api/projects/${projectId}/roles/self-activate`,
                dataType: "json",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify({
                    roles: roles,
                    justification: justification
                }),
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** Activate a role with peer approval */
    async requestRole(projectId, role, peers, justification) {
        console.assert(projectId);
        console.assert(role);
        console.assert(peers.length > 0);
        console.assert(justification)

        try {
            return await $.ajax({
                type: "POST",
                url: `/api/projects/${projectId}/roles/request`,
                dataType: "json",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify({
                    role: role,
                    justification: justification,
                    peers: peers
                }),
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** Get details for an activation request */
    async getActivationRequest(activationToken) {
        console.assert(activationToken);

        throw "NIY"; // TODO: Lookup
        try {
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** Approve an activation request */
    async approveRole(activationToken) {
        console.assert(activationToken);

        throw "NIY"; // TODO: Lookup
        try {
        }
        catch (error) {
            throw this._formatError(error);
        }
    }
}

class DebugModel extends Model {
    constructor() {
        super();
        $("body").append(`
            <div id="debug-pane">
                <div>
                Principal: <input type="text" id="debug-principal"/>
                </div>
                <hr/>
                <div>
                    listProjects:
                    <select id="debug-listProjects">
                        <option value="">(default)</option>
                        <option value="error">Simulate error</option>
                        <option value="0">Simulate 0 results</option>
                        <option value="10">Simulate 10 result</option>
                        <option value="100">Simulate 100 results</option>
                    </select>
                </div>
                <div>
                    listRoles:
                    <select id="debug-listRoles">
                        <option value="">(default)</option>
                        <option value="error">Simulate error</option>
                        <option value="0">Simulate 0 results</option>
                        <option value="1">Simulate 1 result</option>
                        <option value="10">Simulate 10 results</option>
                    </select>
                </div>
                <div>
                    listPeers:
                    <select id="debug-listPeers">
                        <option value="">(default)</option>
                        <option value="error">Simulate error</option>
                        <option value="0">Simulate 0 results</option>
                        <option value="1">Simulate 1 result</option>
                        <option value="10">Simulate 10 results</option>
                    </select>
                </div>
                <div>
                    selfActivateRoles:
                    <select id="debug-selfActivateRoles">
                        <option value="">(default)</option>
                        <option value="success">Simulate success</option>
                        <option value="error">Simulate error</option>
                    </select>
                </div>
                <div>
                    requestRole:
                    <select id="debug-requestRole">
                        <option value="">(default)</option>
                        <option value="success">Simulate success</option>
                        <option value="error">Simulate error</option>
                    </select>
                </div>
                <div>
                    getActivationRequest:
                    <select id="debug-getActivationRequest">
                        <option value="">(default)</option>
                        <option value="success">Simulate success</option>
                        <option value="error">Simulate error</option>
                    </select>
                </div>
                <div>
                    approveRole:
                    <select id="debug-approveRole">
                        <option value="">(default)</option>
                        <option value="success">Simulate success</option>
                        <option value="error">Simulate error</option>
                    </select>
                </div>
            </div>
        `);

        //
        // Persist settings.
        //
        [
            "debug-principal",
            "debug-listProjects",
            "debug-listRoles",
            "debug-listPeers",
            "debug-selfActivateRoles",
            "debug-requestRole",
            "debug-getActivationRequest",
            "debug-approveRole"
        ].forEach(setting => {

            $("#" + setting).val(localStorage.getItem(setting))
            $("#" + setting).change(() => {
                localStorage.setItem(setting, $("#" + setting).val());
            });
        });
    }

    _getHeaders() {
        const headers = super._getHeaders();
        const principal = $("#debug-principal").val();
        if (principal) {
            headers["X-Debug-Principal"] = principal;
        }
        return headers;
    }

    async _simulateError() {
        await new Promise(r => setTimeout(r, 1000));
        return Promise.reject("Simulated error");
    }

    async _simulateActivationResponse(projectId, justification, roles, status, forSelf) {
        await new Promise(r => setTimeout(r, 1000));
        return Promise.resolve({
            isBeneficiary: forSelf,
            isReviewer: (!forSelf),
            justification: justification,
            requestTime: Math.floor(Date.now() / 1000),
            beneficiary: "user",
            projectId: projectId,
            items: roles.map(r => ({
                activationId: "sim-1",
                roleBinding: {
                    fullResourceName: "//simulated",
                    role: r
                },
                status: status,
                expiry: Math.floor(Date.now() / 1000) + 300
            }))
        });
    }

    get policy() {
        return {
            justificationHint: "simulated hint"
        };
    }

    async fetchPolicy() { }

    async listRoles(projectId) {
        var setting = $("#debug-listRoles").val();
        if (!setting) {
            return super.listRoles(projectId);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            await new Promise(r => setTimeout(r, 2000));
            const statuses = ["ACTIVATED", "ELIGIBLE_FOR_JIT", "ELIGIBLE_FOR_MPA"]
            return Promise.resolve({
                warnings: ["This is a simulated result"],
                roles: Array.from({ length: setting }, (e, i) => ({
                    roleBinding: {
                        id: "//project-1:roles/simulated-role-" + i,
                        role: "roles/simulated-role-" + i
                    },
                    status: statuses[i % statuses.length]
                }))
            });
        }
    }

    async listProjects() {
        var setting = $("#debug-listProjects").val();
        if (!setting) {
            return super.listProjects();
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            await new Promise(r => setTimeout(r, 2000));
            return Promise.resolve({
                projects: Array.from({ length: setting }, (e, i) => "project-" + i)
            });
        }
    }

    async listPeers(projectId, role) {
        var setting = $("#debug-listPeers").val();
        if (!setting) {
            return super.listPeers(projectId, role);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            await new Promise(r => setTimeout(r, 1000));
            return Promise.resolve({
                peers: Array.from({ length: setting }, (e, i) => ({
                    email: `user-${i}@example.com`
                }))
            });
        }
    }

    async selfActivateRoles(projectId, roles, justification) {
        var setting = $("#debug-selfActivateRoles").val();
        if (!setting) {
            return super.selfActivateRoles(projectId, roles, justification);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            return await this._simulateActivationResponse(
                projectId,
                justification,
                roles,
                "ACTIVATED",
                true);
        }
    }

    async requestRole(projectId, role, peers, justification) {
        var setting = $("#debug-requestRole").val();
        if (!setting) {
            return super.requestRole(projectId, role, peers, justification);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            return await this._simulateActivationResponse(
                projectId,
                justification,
                [role],
                "ACTIVATION_PENDING",
                true);
        }
    }

    async getActivationRequest(activationToken) {
        var setting = $("#debug-getActivationRequest").val();
        if (!setting) {
            return super.getActivationRequest(activationToken);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            return await this._simulateActivationResponse(
                "project-1",
                "a justification",
                ["roles/role-1"],
                "ACTIVATION_PENDING",
                false);
        }
    }

    async approveRole(activationToken) {
        var setting = $("#debug-approveRole").val();
        if (!setting) {
            return super.approveRole(activationToken);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            return await this._simulateActivationResponse(
                "project-1",
                "a justification",
                ["roles/role-1"],
                "ACTIVATED",
                false);
        }
    }
}