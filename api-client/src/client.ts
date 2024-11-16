import {
  AdminGetUserTaskResponse,
  GetUserTaskResponse,
  ListUserGroupsResponse,
  ListUserTaskDefNamesRequest,
  ListUserTaskDefNamesResponse,
  ListUserTasksRequest,
  ListUserTasksResponse,
  ListUsersResponse,
  UserTask,
  UserTaskResult,
} from "./types";

import {
  AssignmentError,
  ForbiddenError,
  LHUserTasksError,
  NotFoundError,
  PreconditionFailedError,
  TaskStateError,
  UnauthorizedError,
  ValidationError,
} from "./errors";

/**
 * Client for interacting with the LittleHorse User Tasks API.
 * Provides methods for managing user tasks, including claiming, canceling, and completing tasks,
 * as well as administrative functions.
 */
export class LittleHorseUserTasksApiClient {
  private baseUrl: string;
  private tenantId: string;
  private accessToken: string;

  /**
   * Creates a new instance of the LittleHorse User Tasks API client
   * @param config Configuration object containing baseUrl, tenantId, and accessToken
   */
  constructor(config: {
    baseUrl: string;
    tenantId: string;
    accessToken: string;
  }) {
    this.baseUrl = config.baseUrl.replace(/\/$/, ""); // Remove trailing slash if present
    this.tenantId = config.tenantId;
    this.accessToken = config.accessToken;
    this.fetch("/init");
  }

  /**
   * Internal method to make authenticated HTTP requests to the API
   * @param path API endpoint path
   * @param init Optional fetch configuration
   * @returns Promise resolving to the JSON response or void
   * @private
   */
  private async fetch<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${this.baseUrl}/${this.tenantId}${path}`, {
      ...init,
      headers: {
        ...init?.headers,
        Authorization: `Bearer ${this.accessToken}`,
      },
    });

    if (!response.ok) {
      const errorMessage = await this.getErrorMessage(response);
      switch (response.status) {
        case 401:
          throw new UnauthorizedError(errorMessage);
        case 403:
          // Check if error is related to task state
          if (errorMessage.includes("DONE")) {
            throw new TaskStateError(
              "Cannot modify a task that is already completed",
            );
          } else if (errorMessage.includes("CANCELLED")) {
            throw new TaskStateError(
              "Cannot modify a task that has been cancelled",
            );
          }
          throw new ForbiddenError(errorMessage);
        case 404:
          throw new NotFoundError(errorMessage);
        case 412:
          // Check if error is related to assignment
          if (
            errorMessage.includes("assign") ||
            errorMessage.includes("claim")
          ) {
            throw new AssignmentError(errorMessage);
          }
          throw new PreconditionFailedError(errorMessage);
        case 400:
          throw new ValidationError(errorMessage);
        default:
          throw new LHUserTasksError(
            `HTTP error ${response.status}: ${response}`,
          );
      }
    }

    const contentType = response.headers.get("content-type");
    if (contentType && contentType.includes("application/json")) {
      return response.json();
    } else if (!contentType && response.status === 204) {
      // Handle no-content responses explicitly
      return undefined as T;
    }
    return response as T;
  }

  private async getErrorMessage(response: Response): Promise<string> {
    try {
      const contentType = response.headers.get("content-type");
      if (contentType && contentType.includes("application/json")) {
        const errorData = await response.json();
        return errorData.detail || errorData.message || "Unknown error";
      }
      return await response.text();
    } catch {
      return "Unknown error";
    }
  }

  // User Methods
  /**
   * Claims a user task for the authenticated user
   * @param userTask The user task to claim
   */
  async claimUserTask(userTask: UserTask): Promise<void> {
    await this.fetch(`/tasks/${userTask.wfRunId}/${userTask.id}/claim`, {
      method: "POST",
    });
  }

  /**
   * Cancels a user task
   * @param userTask The user task to cancel
   */
  async cancelUserTask(userTask: UserTask): Promise<void> {
    await this.fetch(`/tasks/${userTask.wfRunId}/${userTask.id}/cancel`, {
      method: "POST",
    });
  }

  /**
   * Lists user tasks based on search criteria
   * @param search Search parameters (excluding user_task_def_name)
   * @returns Promise resolving to the list of user tasks
   */
  async listUserTasks(
    search: Omit<ListUserTasksRequest, "type">,
  ): Promise<ListUserTasksResponse> {
    const filteredSearch = Object.fromEntries(
      Object.entries(search)
        .filter(([_, value]) => value !== undefined && value !== null)
        .map(([key, value]) => [
          key,
          // Ensure value is properly encoded for URLs
          encodeURIComponent(value.toString().trim()),
        ]),
    );
    const searchParams = new URLSearchParams(filteredSearch);

    return await this.fetch(`/tasks?${searchParams.toString()}`);
  }

  /**
   * Retrieves all user groups
   * @returns Promise resolving to the list of user groups
   */
  async listUserGroups(): Promise<ListUserGroupsResponse> {
    return await this.fetch("/groups");
  }

  /**
   * Retrieves a specific user task by ID
   * @param userTask The user task to retrieve
   * @returns Promise resolving to the user task details
   */
  async getUserTask(userTask: UserTask): Promise<GetUserTaskResponse> {
    return await this.fetch(`/tasks/${userTask.wfRunId}/${userTask.id}`);
  }

  /**
   * Completes a user task with the provided values
   * @param userTask The user task to complete
   * @param values The result values for the task
   */
  async completeUserTask(
    userTask: UserTask,
    values: UserTaskResult,
  ): Promise<void> {
    await this.fetch(`/tasks/${userTask.wfRunId}/${userTask.id}/result`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(values),
    });
  }

  // Admin Methods
  /**
   * Administrative method to cancel a user task
   * @param userTask The user task to cancel
   */
  async adminCancelUserTask(userTask: UserTask): Promise<void> {
    await this.fetch(`/admin/tasks/${userTask.wfRunId}/${userTask.id}/cancel`, {
      method: "POST",
    });
  }

  /**
   * Administrative method to assign a user task to a specific user or group
   * @param userTask The user task to assign
   * @param param1 Object containing userId and/or userGroupId
   */
  async adminAssignUserTask(
    userTask: UserTask,
    { userId, userGroupId }: { userId?: string; userGroupId?: string },
  ): Promise<void> {
    await this.fetch(`/admin/tasks/${userTask.wfRunId}/${userTask.id}/assign`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        userId: userId ?? "",
        userGroup: userGroupId ?? "",
      }),
    });
  }

  /**
   * Administrative method to list all users
   * @returns Promise resolving to the list of users
   */
  async adminListUsers(): Promise<ListUsersResponse> {
    return await this.fetch("/admin/users");
  }

  /**
   * Administrative method to list all user groups
   * @returns Promise resolving to the list of user groups
   */
  async adminListUserGroups(): Promise<ListUserGroupsResponse> {
    return await this.fetch("/admin/groups");
  }

  /**
   * Administrative method to list user task definition names
   * @param search Search parameters for task definitions
   * @returns Promise resolving to the list of task definition names
   */
  async adminListUserTaskDefNames(
    search: ListUserTaskDefNamesRequest,
  ): Promise<ListUserTaskDefNamesResponse> {
    const filteredSearch = Object.fromEntries(
      Object.entries(search)
        .filter(([_, value]) => value !== undefined && value !== null)
        .map(([key, value]) => [
          key,
          // Ensure value is properly encoded for URLs
          encodeURIComponent(value.toString().trim()),
        ]),
    );
    const searchParams = new URLSearchParams(filteredSearch);
    return await this.fetch(`/admin/taskTypes?${searchParams.toString()}`);
  }

  /**
   * Administrative method to get a user task
   * @param userTask The user task to get the details of
   * @returns Promise resolving to the user task details
   */
  async adminGetUserTask(
    userTask: UserTask,
  ): Promise<AdminGetUserTaskResponse> {
    return await this.fetch(`/admin/tasks/${userTask.wfRunId}/${userTask.id}`);
  }

  /**
   * Administrative method to list all user tasks
   * @param search Search parameters for tasks
   * @returns Promise resolving to the list of user tasks
   */
  async adminListUserTasks(
    search: ListUserTasksRequest,
  ): Promise<ListUserTasksResponse> {
    const filteredSearch = Object.fromEntries(
      Object.entries(search)
        .filter(([_, value]) => value !== undefined && value !== null)
        .map(([key, value]) => [
          key,
          // Ensure value is properly encoded for URLs
          encodeURIComponent(value.toString().trim()),
        ]),
    );
    const searchParams = new URLSearchParams(filteredSearch);

    return await this.fetch(`/admin/tasks?${searchParams.toString()}`);
  }

  /**
   * Administrative method to complete a user task
   * @param userTask The user task to complete
   * @param values The result values for the task
   */
  async adminCompleteUserTask(
    userTask: UserTask,
    values: UserTaskResult,
  ): Promise<void> {
    await this.fetch(`/admin/tasks/${userTask.wfRunId}/${userTask.id}/result`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(values),
    });
  }
}
