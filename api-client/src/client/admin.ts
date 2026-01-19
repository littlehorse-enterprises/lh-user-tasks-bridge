import { LHUTBApiClient } from "../client";

import {
  AdminGetUserTaskDetailParams,
  AdminTaskActionParams,
  AssignmentRequest,
  GetAllTasksParams,
  GetAllUserTaskDefsParams,
  GetUserFromIdentityProviderParams,
  GetUsersFromIdentityProviderParams,
  UserListDTO,
  UserTaskDefListDTO,
} from "../types/admin";
import {
  DetailedUserTaskRunDTO,
  UserDTO,
  UserGroupListDTO,
  UserTaskRunListDTO,
  UserTaskVariableValue,
} from "../types/common";
import { objectToURLSearchParams } from "../utils";

export class AdminController {
  private client: LHUTBApiClient;

  constructor(client: LHUTBApiClient) {
    this.client = client;
  }

  /**
   * Gets all UserTasks from a specific tenant.
   */
  async getAllTasks(params: GetAllTasksParams): Promise<UserTaskRunListDTO> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<UserTaskRunListDTO>(
      `/admin/tasks?${queryParams.toString()}`,
    );
  }

  /**
   * Gets all UserTaskDef from a specific tenant.
   */
  async getAllUserTaskDefs(
    params: GetAllUserTaskDefsParams,
  ): Promise<UserTaskDefListDTO> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<UserTaskDefListDTO>(
      `/admin/taskTypes?${queryParams.toString()}`,
    );
  }

  /**
   * Gets a UserTask's details, including its definition (UserTaskDef) and events.
   */
  async getUserTaskDetail(
    params: AdminGetUserTaskDetailParams,
  ): Promise<DetailedUserTaskRunDTO> {
    return this.client.fetch<DetailedUserTaskRunDTO>(
      `/admin/tasks/${params.wf_run_id}/${params.user_task_guid}`,
    );
  }

  /**
   * Completes a UserTask by making it transition to DONE status if the request is successfully processed in the LittleHorse Kernel.
   */
  async completeUserTask(
    params: AdminTaskActionParams,
    results: Record<string, UserTaskVariableValue>,
  ): Promise<void> {
    return this.client.fetch<void>(
      `/admin/tasks/${params.wf_run_id}/${params.user_task_guid}/result`,
      {
        method: "POST",
        body: JSON.stringify(results),
      },
    );
  }

  /**
   * Assigns a UserTaskRun to a User and/or UserGroup.
   */
  async assignUserTask(
    params: AdminTaskActionParams,
    assignmentRequest: AssignmentRequest,
  ): Promise<void> {
    return this.client.fetch<void>(
      `/admin/tasks/${params.wf_run_id}/${params.user_task_guid}/assign`,
      {
        method: "POST",
        body: JSON.stringify(assignmentRequest),
      },
    );
  }

  /**
   * Cancels a UserTaskRun by making it transition to CANCELLED status without verifying to whom the UserTaskRun is assigned to.
   */
  async cancelUserTask(params: AdminTaskActionParams): Promise<void> {
    return this.client.fetch<void>(
      `/admin/tasks/${params.wf_run_id}/${params.user_task_guid}/cancel`,
      {
        method: "POST",
      },
    );
  }

  /**
   * Claims a UserTaskRun by assigning it to the requester Admin user.
   */
  async claimUserTask(params: AdminTaskActionParams): Promise<void> {
    return this.client.fetch<void>(
      `/admin/tasks/${params.wf_run_id}/${params.user_task_guid}/claim`,
      {
        method: "POST",
      },
    );
  }

  /**
   * Gets all Groups from a specific identity provider of a specific tenant.
   */
  async getUserGroups(): Promise<UserGroupListDTO> {
    return this.client.fetch<UserGroupListDTO>(`/admin/groups`);
  }

  /**
   * Gets all active Users from a specific identity provider of a specific tenant.
   */
  async getUsers(
    params: GetUsersFromIdentityProviderParams,
  ): Promise<UserListDTO> {
    const queryParams = objectToURLSearchParams(params);
    return this.client.fetch<UserListDTO>(
      `/admin/users?${queryParams.toString()}`,
    );
  }

  /**
   * Gets a User's basic info.
   */
  async getUserInfo(
    params: GetUserFromIdentityProviderParams,
  ): Promise<UserDTO> {
    return this.client.fetch<UserDTO>(`/admin/users/${params.user_id}`);
  }
}
