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
 * Configuration options for the LittleHorse User Tasks Bridge API client
 * @interface
 */
export interface ClientConfig {
  /** Base URL of the API endpoint (e.g., "http://localhost:8089") */
  baseUrl: string;
  /** Tenant identifier for multi-tenant environments */
  tenantId: string;
  /** OAuth access token for authentication (Bearer token) */
  accessToken: string;
}

/**
 * Client for interacting with the LittleHorse User Tasks Bridge API.
 *
 * This client provides methods for managing user tasks in LittleHorse, including:
 * - Task operations (claim, complete, cancel)
 * - Task listing and filtering
 * - Administrative functions (assign, force complete)
 * - User and group management
 *
 * @example
 * ```typescript
 * const client = new LHUTBApiClient({
 *   baseUrl: 'http://localhost:8089',  // UserTasks API endpoint
 *   tenantId: 'default',              // Your LittleHorse tenant
 *   accessToken: 'your-oidc-token'    // Valid OIDC access token
 * });
 * ```
 */
export class LHUTBApiClient {
  private baseUrl: string;
  private tenantId: string;
  private accessToken: string;

  /**
   * Creates a new instance of the LittleHorse User Tasks Bridge API client
   * @param config - Configuration object containing connection details
   * @throws {ValidationError} If required configuration parameters are missing or invalid
   * @throws {UnauthorizedError} If initial connection test fails
   */
  constructor(config: ClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, ""); // Remove trailing slash if present
    this.tenantId = config.tenantId;
    this.accessToken = config.accessToken;
    this.fetch("/init");
  }

  /**
   * Internal method to make authenticated HTTP requests to the API
   * @param path - API endpoint path (without base URL and tenant)
   * @param init - Optional fetch configuration including method, headers, and body
   * @returns Promise resolving to the JSON response or void
   * @throws {UnauthorizedError} If authentication fails
   * @throws {ForbiddenError} If the user lacks necessary permissions
   * @throws {TaskStateError} If attempting to modify a task that is DONE or CANCELLED
   * @throws {ValidationError} If the request is malformed
   * @throws {AssignmentError} If task assignment/claim conditions aren't met
   * @throws {PreconditionFailedError} If other preconditions aren't met
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

  /**
   * Internal method to get the error message from a response
   * @param response The response to get the error message from
   * @returns Promise resolving to the error message
   * @private
   */
  private async getErrorMessage(response: Response): Promise<string> {
    try {
      const contentType = response.headers.get("content-type");
      if (contentType && contentType.includes("application/json")) {
        const errorData = await response.json();
        return errorData.message || "Unknown error";
      }
      return await response.text();
    } catch {
      return "Unknown error";
    }
  }

  // User Methods
  /**
   * Claims a UserTask for the authenticated user.
   * Once claimed, the task is assigned exclusively to the claiming user.
   *
   * @param userTask - The UserTask to claim, must contain wfRunId and id
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have permission to claim the task
   * @throws {TaskStateError} If the task is already completed or cancelled
   * @throws {AssignmentError} If the task cannot be claimed (e.g., already claimed by another user)
   *
   * @example
   * ```typescript
   * await client.claimUserTask({
   *   id: 'task-123',
   *   wfRunId: 'workflow-456'
   * });
   * ```
   */
  async claimUserTask(userTask: UserTask): Promise<void> {
    await this.fetch(`/tasks/${userTask.wfRunId}/${userTask.id}/claim`, {
      method: "POST",
    });
  }

  /**
   * Cancels a UserTask, preventing it from being completed.
   * Once cancelled, a task cannot be claimed or completed.
   *
   * @param userTask - The UserTask to cancel, must contain wfRunId and id
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have permission to cancel the task
   * @throws {TaskStateError} If the task is already completed or cancelled
   * @throws {NotFoundError} If the task doesn't exist
   *
   * @example
   * ```typescript
   * await client.cancelUserTask({
   *   id: 'task-123',
   *   wfRunId: 'workflow-456'
   * });
   * ```
   */
  async cancelUserTask(userTask: UserTask): Promise<void> {
    await this.fetch(`/tasks/${userTask.wfRunId}/${userTask.id}/cancel`, {
      method: "POST",
    });
  }

  /**
   * Lists UserTasks based on search criteria.
   * Results are paginated and can be filtered by various parameters.
   *
   * @param search - Search parameters for filtering tasks
   * @param search.limit - Maximum number of results to return
   * @param search.earliest_start_date - Optional ISO 8601 timestamp for earliest task start
   * @param search.latest_start_date - Optional ISO 8601 timestamp for latest task start
   * @param search.status - Optional task status filter
   * @param search.user_id - Optional filter by assigned user
   * @param search.user_group_id - Optional filter by assigned group
   * @param search.bookmark - Optional pagination token from previous response
   *
   * @returns Promise resolving to paginated list of UserTasks
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ValidationError} If search parameters are invalid
   *
   * @example
   * ```typescript
   * const result = await client.listUserTasks({
   *   limit: 10,
   *   status: 'ASSIGNED',
   *   user_id: 'user-123'
   * });
   * ```
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
   * Retrieves all user groups available in the system.
   *
   * @returns Promise resolving to the list of user groups
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have permission to list groups
   *
   * @example
   * ```typescript
   * const groups = await client.listUserGroups();
   * console.log(groups.groups); // Array of UserGroup objects
   * ```
   */
  async listUserGroups(): Promise<ListUserGroupsResponse> {
    return await this.fetch("/groups");
  }

  /**
   * Retrieves detailed information about a specific UserTask.
   *
   * @param userTask - The UserTask to retrieve, must contain wfRunId and id
   * @returns Promise resolving to the detailed UserTask information
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have permission to view the task
   * @throws {NotFoundError} If the task doesn't exist
   *
   * @example
   * ```typescript
   * const taskDetails = await client.getUserTask({
   *   id: 'task-123',
   *   wfRunId: 'workflow-456'
   * });
   * console.log(taskDetails.status); // Current task status
   * console.log(taskDetails.fields); // Array of field definitions
   * ```
   */
  async getUserTask(userTask: UserTask): Promise<GetUserTaskResponse> {
    return await this.fetch(`/tasks/${userTask.wfRunId}/${userTask.id}`);
  }

  /**
   * Completes a user task by submitting the task result.
   *
   * @param userTask - Object containing task identifiers
   * @param values - Task result values matching the task's variable definitions
   * @throws {UnauthorizedError} If the user doesn't have permission to complete the task
   * @throws {NotFoundError} If the task doesn't exist
   * @throws {PreconditionFailedError} If the task cannot be completed in its current state
   *
   * @example
   * ```typescript
   * await client.completeUserTask(
   *   { id: 'task-123', wfRunId: 'wf-456' },
   *   {
   *     approved: { type: 'BOOLEAN', value: true },
   *     comment: { type: 'STR', value: 'Looks good!' }
   *   }
   * );
   * ```
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
   * Administrative method to cancel any UserTask, regardless of its current state or assignment.
   *
   * @param userTask - The UserTask to cancel
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the task doesn't exist
   *
   * @example
   * ```typescript
   * await client.adminCancelUserTask({
   *   id: 'task-123',
   *   wfRunId: 'workflow-456'
   * });
   * ```
   */
  async adminCancelUserTask(userTask: UserTask): Promise<void> {
    await this.fetch(`/admin/tasks/${userTask.wfRunId}/${userTask.id}/cancel`, {
      method: "POST",
    });
  }

  /**
   * Assigns a user task to a specific user or group (admin only).
   *
   * @param userTask - Object containing task identifiers
   * @param assignTo - Object specifying user ID and/or group ID to assign
   * @throws {UnauthorizedError} If the caller lacks admin privileges
   * @throws {NotFoundError} If the task doesn't exist
   * @throws {AssignmentError} If the assignment cannot be completed
   *
   * @example
   * ```typescript
   * await client.adminAssignUserTask(
   *   { id: 'task-123', wfRunId: 'wf-456' },
   *   { userId: 'user-789', userGroupId: 'group-123' }
   * );
   * ```
   */
  async adminAssignUserTask(
    userTask: UserTask,
    assignTo: { userId?: string; userGroupId?: string },
  ): Promise<void> {
    await this.fetch(`/admin/tasks/${userTask.wfRunId}/${userTask.id}/assign`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        userId: assignTo.userId ?? "",
        userGroup: assignTo.userGroupId ?? "",
      }),
    });
  }

  /**
   * Administrative method to list all users in the system.
   *
   * @returns Promise resolving to the list of all users
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   *
   * @example
   * ```typescript
   * const users = await client.adminListUsers();
   * console.log(users.users); // Array of User objects
   * ```
   */
  async adminListUsers(): Promise<ListUsersResponse> {
    return await this.fetch("/admin/users");
  }

  /**
   * Administrative method to list all user groups in the system.
   *
   * @returns Promise resolving to the list of all user groups
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   *
   * @example
   * ```typescript
   * const groups = await client.adminListUserGroups();
   * console.log(groups.groups); // Array of UserGroup objects
   * ```
   */
  async adminListUserGroups(): Promise<ListUserGroupsResponse> {
    return await this.fetch("/admin/groups");
  }

  /**
   * Administrative method to list all available UserTask definition names.
   *
   * @param search - Search parameters for filtering task definitions
   * @param search.limit - Maximum number of results to return
   * @param search.bookmark - Optional pagination token from previous response
   * @returns Promise resolving to the list of task definition names
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   *
   * @example
   * ```typescript
   * const taskDefs = await client.adminListUserTaskDefNames({
   *   limit: 20,
   *   bookmark: 'next-page-token'
   * });
   * console.log(taskDefs.userTaskDefNames); // Array of task definition names
   * ```
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
   * Administrative method to get detailed information about a UserTask.
   * This method provides additional information not available in the regular getUserTask method,
   * including task events history.
   *
   * @param userTask - The UserTask to retrieve details for
   * @returns Promise resolving to the detailed UserTask information including events history
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the task doesn't exist
   *
   * @example
   * ```typescript
   * const taskDetails = await client.adminGetUserTask({
   *   id: 'task-123',
   *   wfRunId: 'workflow-456'
   * });
   * console.log(taskDetails.events); // Array of task events
   * console.log(taskDetails.status); // Current task status
   * ```
   */
  async adminGetUserTask(
    userTask: UserTask,
  ): Promise<AdminGetUserTaskResponse> {
    return await this.fetch(`/admin/tasks/${userTask.wfRunId}/${userTask.id}`);
  }

  /**
   * Administrative method to list all UserTasks in the system.
   * Provides comprehensive access to all tasks regardless of assignment or state.
   *
   * @param search - Search parameters for filtering tasks
   * @param search.limit - Maximum number of results to return
   * @param search.type - Task definition type to filter by
   * @param search.earliest_start_date - Optional ISO 8601 timestamp for earliest task start
   * @param search.latest_start_date - Optional ISO 8601 timestamp for latest task start
   * @param search.status - Optional task status filter
   * @param search.user_id - Optional filter by assigned user
   * @param search.user_group_id - Optional filter by assigned group
   * @param search.bookmark - Optional pagination token
   * @returns Promise resolving to the paginated list of UserTasks
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {ValidationError} If search parameters are invalid
   *
   * @example
   * ```typescript
   * const tasks = await client.adminListUserTasks({
   *   limit: 50,
   *   type: 'approval',
   *   status: 'DONE',
   *   earliest_start_date: '2024-01-01T00:00:00Z'
   * });
   * console.log(tasks.userTasks); // Array of UserTask objects
   * console.log(tasks.bookmark); // Pagination token for next page
   * ```
   */
  async adminListUserTasks(
    search: ListUserTasksRequest,
  ): Promise<ListUserTasksResponse> {
    const filteredSearch = Object.fromEntries(
      Object.entries(search)
        .filter(([_, value]) => value !== undefined && value !== null)
        .map(([key, value]) => [
          key,
          encodeURIComponent(value.toString().trim()),
        ]),
    );
    const searchParams = new URLSearchParams(filteredSearch);

    return await this.fetch(`/admin/tasks?${searchParams.toString()}`);
  }

  /**
   * Administrative method to complete any UserTask.
   * Allows administrators to complete tasks regardless of their current state or assignment.
   *
   * @param userTask - The UserTask to complete
   * @param values - The result values for the task fields
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {ValidationError} If the provided values don't match the task's field definitions
   * @throws {NotFoundError} If the task doesn't exist
   *
   * @example
   * ```typescript
   * await client.adminCompleteUserTask(
   *   { id: 'task-123', wfRunId: 'workflow-456' },
   *   {
   *     approved: { type: 'BOOLEAN', value: true },
   *     reason: { type: 'STRING', value: 'Administrative override' }
   *   }
   * );
   * ```
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
