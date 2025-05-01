import {
  GetUsersFromIdentityProviderParams,
  UserListDTO,
} from "../types/admin";
import {
  CreateManagedUserRequest,
  GetUserFromIdPParams,
  IDPUserDTO,
  UpdateManagedUserRequest,
  UpdateUserParams,
  UpsertPasswordParams,
  UpsertPasswordRequest,
} from "../types/user-management";
import { objectToURLSearchParams } from "../utils";
import { LHUTBApiClient } from "./index";

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
  ): Promise<UserListDTO> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<UserListDTO>(
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
}
