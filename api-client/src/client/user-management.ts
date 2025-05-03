import { LHUTBApiClient } from "../client";
import { GetUsersFromIdentityProviderParams } from "../types/admin";
import {
  AdminRoleParams,
  CreateManagedUserRequest,
  DeleteUserParams,
  GetUserFromIdPParams,
  IDPUserDTO,
  IDPUserListDTO,
  JoinOrLeaveGroupParams,
  UpdateManagedUserRequest,
  UpdateUserParams,
  UpsertPasswordParams,
  UpsertPasswordRequest,
} from "../types/user-management";
import { objectToURLSearchParams } from "../utils";

export class UserManagementController {
  private client: LHUTBApiClient;

  constructor(client: LHUTBApiClient) {
    this.client = client;
  }

  /**
   * Gets all active Users from a specific identity provider of a specific tenant.
   */
  async getUsersFromIdP(
    params: GetUsersFromIdentityProviderParams,
  ): Promise<IDPUserListDTO> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<IDPUserListDTO>(
      `/management/users?${queryParams.toString()}`,
    );
  }

  /**
   * Creates a User within a specific tenant's IdP
   */
  async createUser(request: CreateManagedUserRequest): Promise<void> {
    return this.client.fetch<void>("/management/users", {
      method: "POST",
      body: JSON.stringify(request),
    });
  }

  /**
   * Sets or resets a user's password
   */
  async upsertPassword(
    params: UpsertPasswordParams,
    request: UpsertPasswordRequest,
  ): Promise<void> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<void>(
      `/management/users/${params.user_id}/password`,
      {
        method: "POST",
        body: JSON.stringify(request),
      },
    );
  }

  /**
   * Gets a User from a specific identity provider of a specific tenant.
   */
  async getUserFromIdP(params: GetUserFromIdPParams): Promise<IDPUserDTO> {
    return this.client.fetch<IDPUserDTO>(`/management/users/${params.user_id}`);
  }

  /**
   * Updates a user's properties
   */
  async updateUser(
    params: UpdateUserParams,
    request: UpdateManagedUserRequest,
  ): Promise<void> {
    return this.client.fetch<void>(`/management/users/${params.user_id}`, {
      method: "PUT",
      body: JSON.stringify(request),
    });
  }

  /**
   * Deletes a user from the respective Identity Provider
   */
  async deleteUser(params: DeleteUserParams): Promise<void> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<void>(
      `/management/users/${params.user_id}?${queryParams.toString()}`,
      {
        method: "DELETE",
      },
    );
  }

  /**
   * Assigns the Admin role to a specific user.
   */
  async assignAdminRole(params: AdminRoleParams): Promise<void> {
    return this.client.fetch<void>(
      `/management/users/${params.user_id}/roles/admin`,
      {
        method: "POST",
      },
    );
  }

  /**
   * Removes the Admin role from a specific user.
   */
  async removeAdminRole(params: AdminRoleParams): Promise<void> {
    return this.client.fetch<void>(
      `/management/users/${params.user_id}/roles/admin`,
      {
        method: "DELETE",
      },
    );
  }

  /**
   * Adds a user to a group.
   */
  async addUserToGroup(params: JoinOrLeaveGroupParams): Promise<void> {
    return this.client.fetch<void>(
      `/management/users/${params.user_id}/groups/${params.group_id}`,
      {
        method: "POST",
      },
    );
  }

  /**
   * Removes a user from a group.
   */
  async removeUserFromGroup(params: JoinOrLeaveGroupParams): Promise<void> {
    return this.client.fetch<void>(
      `/management/users/${params.user_id}/groups/${params.group_id}`,
      {
        method: "DELETE",
      },
    );
  }
}
