import { LHUTBApiClient } from "../client";
import { IDPGroupListDTO } from "../types";
import {
  CreateGroupRequest,
  DeleteGroupParams,
  GetGroupsParams,
  UpdateGroupParams,
  UpdateGroupRequest,
} from "../types/group-management";
import { objectToURLSearchParams } from "../utils";

export class GroupManagementController {
  private client: LHUTBApiClient;

  constructor(client: LHUTBApiClient) {
    this.client = client;
  }
  /**
   * Creates a Group within a specific tenant's IdP
   */
  async createGroup(request: CreateGroupRequest): Promise<void> {
    return this.client.fetch<void>("/management/groups", {
      method: "POST",
      body: JSON.stringify(request),
    });
  }

  /**
   * Gets a collection of Groups within a specific tenant's IdP
   */
  async getGroups(params: GetGroupsParams): Promise<IDPGroupListDTO> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<IDPGroupListDTO>(
      `/management/groups?${queryParams.toString()}`,
    );
  }

  /**
   * Updates a Group within a specific tenant's IdP
   */
  async updateGroup(
    params: UpdateGroupParams,
    request: UpdateGroupRequest,
  ): Promise<void> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<void>(
      `/management/groups/${params.group_id}?${queryParams.toString()}`,
      {
        method: "PUT",
        body: JSON.stringify(request),
      },
    );
  }

  /**
   * Deletes a Group within a specific tenant's IdP
   */
  async deleteGroup(params: DeleteGroupParams): Promise<void> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<void>(
      `/management/groups/${params.group_id}?${queryParams.toString()}`,
      {
        method: "DELETE",
      },
    );
  }
}
