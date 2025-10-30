import { LHUTBApiClient } from "../client";
import {
  AuditEventDTO,
  DetailedUserTaskRunDTO,
  UserDTO,
  UserGroupListDTO,
  UserTaskRunListDTO,
  UserTaskVariableValue,
} from "../types/common";
import {
  CancelUserTaskParams,
  ClaimUserTaskParams,
  CompleteUserTaskParams,
  GetClaimableTasksParams,
  GetMyTasksParams,
  GetUserTaskDetailParams,
  PostUserTaskCommentParams,
  EditUserTaskCommentParams,
  DeleteUserTaskCommentParams,
  GetUserTaskCommentsParams,
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
    results: Record<string, UserTaskVariableValue>,
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
  /**
   * Gets all comments for specific userTask
   */
  async getUserTaskComments(
    params: GetUserTaskCommentsParams,
  ): Promise<AuditEventDTO[]> {
    return this.client.fetch<AuditEventDTO[]>(
      `/tasks/${params.wf_run_id}/${params.user_task_guid}/comments`,
    );
  }
  /**
   * Adds a comment to a userTask
   */
  async postUserTaskComment(
    params: PostUserTaskCommentParams,
  ): Promise<AuditEventDTO> {
    return this.client.fetch<AuditEventDTO>(
      `/tasks/${params.wf_run_id}/${params.user_task_guid}/comment`,
      {
        method: "POST",
        body: JSON.stringify({ comment: params.comment }),
      },
    );
  }

  /**
   * Edits an existing comment on a userTask
   */
  async editUserTaskComment(params: EditUserTaskCommentParams) {
    return this.client.fetch<AuditEventDTO>(
      `/tasks/${params.wf_run_id}/${params.user_task_guid}/comment/${params.comment_id}`,
      {
        method: "PUT",
        body: JSON.stringify({ comment: params.comment }),
      },
    );
  }

  /**
   * Deletes a comment on a userTask
   */
  async deleteUserTaskComment(params: DeleteUserTaskCommentParams) {
    return this.client.fetch<AuditEventDTO>(
      `/tasks/${params.wf_run_id}/${params.user_task_guid}/comment/${params.comment_id}`,
      {
        method: "DELETE",
      },
    );
  }
}
