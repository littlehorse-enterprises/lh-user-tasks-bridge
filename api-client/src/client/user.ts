import { LHUTBApiClient } from "../client";
import {
  DetailedUserTaskRunDTO,
  UserDTO,
  UserGroupListDTO,
  UserTaskRunListDTO,
} from "../types/common";
import {
  CancelUserTaskParams,
  ClaimUserTaskParams,
  CompleteUserTaskParams,
  CompleteUserTaskRequest,
  GetClaimableTasksParams,
  GetMyTasksParams,
  GetUserTaskDetailParams,
} from "../types/user";

import { objectToURLSearchParams } from "../utils";

export class UserController {
  private client: LHUTBApiClient;

  constructor(client: LHUTBApiClient) {
    this.client = client;
  }

  /**
   * Gets all UserTasks assigned to a user and/or userGroup that the user belongs to.
   */
  async getMyTasks(params: GetMyTasksParams): Promise<UserTaskRunListDTO> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<UserTaskRunListDTO>(
      `/tasks?${queryParams.toString()}`,
    );
  }

  /**
   * Gets a UserTask's details, including its definition (UserTaskDef).
   */
  async getUserTaskDetail(
    params: GetUserTaskDetailParams,
  ): Promise<DetailedUserTaskRunDTO> {
    return this.client.fetch<DetailedUserTaskRunDTO>(
      `/tasks/${params.wf_run_id}/${params.user_task_guid}`,
    );
  }

  /**
   * Completes a UserTask by making it transition to DONE status if the request is successfully processed in the LittleHorse Kernel.
   */
  async completeUserTask(
    params: CompleteUserTaskParams,
    results: CompleteUserTaskRequest,
  ): Promise<void> {
    return this.client.fetch<void>(
      `/tasks/${params.wf_run_id}/${params.user_task_guid}/result`,
      {
        method: "POST",
        body: JSON.stringify(results),
      },
    );
  }

  /**
   * Cancels a UserTask by making it transition to CANCELLED status if the request is successfully processed in the LittleHorse Kernel.
   */
  async cancelUserTask(params: CancelUserTaskParams): Promise<void> {
    return this.client.fetch<void>(
      `/tasks/${params.wf_run_id}/${params.user_task_guid}/cancel`,
      {
        method: "POST",
      },
    );
  }

  /**
   * Claims a UserTaskRun by assigning it to the requester user.
   */
  async claimUserTask(params: ClaimUserTaskParams): Promise<void> {
    return this.client.fetch<void>(
      `/tasks/${params.wf_run_id}/${params.user_task_guid}/claim`,
      {
        method: "POST",
      },
    );
  }

  /**
   * Gets all Groups from a specific identity provider of a specific tenant for the requesting user.
   */
  async getUserGroupsFromIdentityProvider(): Promise<UserGroupListDTO> {
    return this.client.fetch<UserGroupListDTO>(`/groups`);
  }

  /**
   * Gets the requesting user's info.
   */
  async getMyUserInfo(): Promise<UserDTO> {
    return this.client.fetch<UserDTO>(`/userInfo`);
  }

  /**
   * Gets all UserTasks assigned to an specific userGroup that the user belongs to.
   */
  async getClaimableTasks(
    params: GetClaimableTasksParams,
  ): Promise<UserTaskRunListDTO> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<UserTaskRunListDTO>(
      `/tasks/claimable?${queryParams.toString()}`,
    );
  }
}
