import {
  AdminGetUserTaskResponse,
  CreateGroupRequest,
  GetUserTaskResponse,
  GroupManagementSearchRequest,
  ListClaimableUserTasksRequest,
  ListUserGroupsResponse,
  ListUserTaskDefNamesRequest,
  ListUserTaskDefNamesResponse,
  ListUserTasksRequest,
  ListUserTasksResponse,
  ListUsersResponse,
  UpdateGroupRequest,
  UserManagementListGroupsResponse,
  UserManagementListUsersResponse,
  UserManagementSearchRequest,
  UserManagementUser,
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
   * Lists UserTasks that are claimable by the authenticated user in any of their respective user groups.
   *
   * @param search - Search parameters for filtering tasks
   * @param search.limit - Maximum number of results to return
   * @param search.user_group_id - Which user group to get the claimable tasks for
   * @param search.earliest_start_date - Optional ISO 8601 timestamp for earliest task start
   * @param search.latest_start_date - Optional ISO 8601 timestamp for latest task start
   * @param search.bookmark - Optional pagination token from previous response
   *
   * @returns Promise resolving to paginated list of UserTasks
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ValidationError} If search parameters are invalid
   *
   * @example
   * ```typescript
   * const result = await client.listClaimableUserTasks({
   *   limit: 10,
   *   user_group_id: 'group-123'
   * });
   * ```
   */
  async listClaimableUserTasks(
    search: ListClaimableUserTasksRequest,
  ): Promise<ListUserTasksResponse> {
    const filteredSearch = Object.fromEntries(
      Object.entries(search)
        .filter(([_, value]) => value !== undefined && value !== null)
        .map(([key, value]) => [
          key,
          encodeURIComponent(value.toString().trim()),
        ]),
    );
    const searchParams = new URLSearchParams({
      ...filteredSearch,
    });
    return await this.fetch(`/tasks/claimable?${searchParams.toString()}`);
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
   * Administrative method to claim a UserTask for a specific user. This will bypass any normal validation checks and overide anyone that already has it claimed.
   *
   * @param userTask - The UserTask to claim, must contain wfRunId and id
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have permission to claim the task
   * @throws {TaskStateError} If the task is already completed or cancelled
   * @throws {AssignmentError} If the task cannot be claimed
   *
   * @example
   * ```typescript
   * await client.adminClaimUserTask({
   *   id: 'task-123',
   *   wfRunId: 'workflow-456'
   * });
   * ```
   */
  async adminClaimUserTask(userTask: UserTask): Promise<void> {
    await this.fetch(`/admin/tasks/${userTask.wfRunId}/${userTask.id}/claim`, {
      method: "POST",
    });
  }

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

  // User and Group Management

  /**
   * List all users with optional search filters.
   *
   * @param search - Optional search parameters
   * @param search.email - Filter by email
   * @param search.firstName - Filter by first name
   * @param search.lastName - Filter by last name
   * @param search.username - Filter by username
   * @param search.userGroupId - Filter by user group ID
   * @param search.firstResult - Starting index for pagination (default: 0)
   * @param search.maxResults - Maximum number of results to return (default: 10)
   * @returns Promise resolving to the list of users
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   *
   * @example
   * ```typescript
   * const result = await client.listUsers({
   *   email: 'test@example.com',
   *   maxResults: 20
   * });
   * ```
   */
  async listUsers(search: UserManagementSearchRequest = {}): Promise<UserManagementListUsersResponse> {
    const params = new URLSearchParams();
    if (search.email) params.append("email", search.email);
    if (search.firstName) params.append("first_name", search.firstName);
    if (search.lastName) params.append("last_name", search.lastName);
    if (search.username) params.append("username", search.username);
    if (search.userGroupId) params.append("user_group_id", search.userGroupId);
    if (search.firstResult !== undefined) params.append("first_result", search.firstResult.toString());
    if (search.maxResults !== undefined) params.append("max_results", search.maxResults.toString());

    return await this.fetch(`/management/users${params.toString() ? `?${params.toString()}` : ""}`);
  }

  /**
   * Create a new user.
   *
   * @param user - User details to create
   * @param user.username - Username for the new user
   * @param user.firstName - First name of the user
   * @param user.lastName - Last name of the user
   * @param user.email - Email address of the user
   * @returns Promise resolving to the created user's ID
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {ValidationError} If the user details are invalid
   *
   * @example
   * ```typescript
   * const userId = await client.createUser({
   *   username: 'new-user',
   *   firstName: 'John',
   *   lastName: 'Doe',
   *   email: 'john.doe@example.com'
   * });
   * ```
   */
  async createUser(user: {
    username: string;
    firstName: string;
    lastName: string;
    email: string;
  }): Promise<string> {
    const response = await this.fetch<{ id: string }>("/management/users", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(user),
    });
    return response.id;
  }

  /**
   * Sets a new password for a user.
   *
   * @param userId - ID of the user to update
   * @param password - New password to set
   * @param temporary - Whether the password is temporary and must be changed on first login
   * @returns Promise resolving to void
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the user doesn't exist
   * @throws {ValidationError} If the password is invalid
   *
   * @example
   * ```typescript
   * await client.setUserPassword('user-123', 'new-password', true);
   * ```
   */
  async setUserPassword(userId: string, password: string, temporary: boolean = false): Promise<void> {
    await this.fetch(`/management/users/${userId}/password`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        password,
        temporary,
      }),
    });
  }

  /**
   * Gets detailed information about a specific user.
   *
   * @param userId - ID of the user to retrieve
   * @returns Promise resolving to the user details
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the user doesn't exist
   *
   * @example
   * ```typescript
   * const userDetails = await client.getUser('user-123');
   * console.log(userDetails.username); // User's username
   * ```
   */
  async getUser(userId: string): Promise<UserManagementUser> {
    return await this.fetch(`/management/users/${userId}`);
  }

  /**
   * Updates a user's information.
   *
   * @param userId - ID of the user to update
   * @param updates - Fields to update
   * @param updates.email - New email address
   * @param updates.enabled - Whether the user account is enabled
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the user doesn't exist
   * @throws {ValidationError} If the update data is invalid
   *
   * @example
   * ```typescript
   * await client.updateUser('user-123', {
   *   email: 'new.email@example.com',
   *   enabled: true
   * });
   * ```
   */
  async updateUser(
    userId: string,
    updates: {
      email?: string;
      enabled?: boolean;
    },
  ): Promise<void> {
    await this.fetch(`/management/users/${userId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(updates),
    });
  }

  /**
   * Deletes a user from the system.
   *
   * @param userId - ID of the user to delete
   * @param ignoreOrphanTasks - Whether to ignore tasks assigned to this user
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the user doesn't exist
   *
   * @example
   * ```typescript
   * await client.deleteUser('user-123', true);
   * ```
   */
  async deleteUser(userId: string, ignoreOrphanTasks: boolean = false): Promise<void> {
    const params = new URLSearchParams();
    if (ignoreOrphanTasks) {
      params.append("ignore_orphan_tasks", "true");
    }
    await this.fetch(`/management/users/${userId}?${params.toString()}`, {
      method: "DELETE",
    });
  }

  /**
   * Assigns the admin role to a user.
   *
   * @param userId - ID of the user to make admin
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the user doesn't exist
   *
   * @example
   * ```typescript
   * await client.assignAdminRole('user-123');
   * ```
   */
  async assignAdminRole(userId: string): Promise<void> {
    await this.fetch(`/management/users/${userId}/roles/admin`, {
      method: "POST",
    });
  }

  /**
   * Removes the admin role from a user.
   *
   * @param userId - ID of the user to remove admin role from
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the user doesn't exist
   *
   * @example
   * ```typescript
   * await client.removeAdminRole('user-123');
   * ```
   */
  async removeAdminRole(userId: string): Promise<void> {
    await this.fetch(`/management/users/${userId}/roles/admin`, {
      method: "DELETE",
    });
  }

  /**
   * Adds a user to a group.
   *
   * @param userId - ID of the user to add to the group
   * @param groupId - ID of the group to add the user to
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the user or group doesn't exist
   *
   * @example
   * ```typescript
   * await client.joinGroup('user-123', 'group-456');
   * ```
   */
  async joinGroup(userId: string, groupId: string): Promise<void> {
    await this.fetch(`/management/users/${userId}/groups/${groupId}`, {
      method: "POST",
    });
  }

  /**
   * Removes a user from a group.
   *
   * @param userId - ID of the user to remove from the group
   * @param groupId - ID of the group to remove the user from
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the user or group doesn't exist
   *
   * @example
   * ```typescript
   * await client.removeFromGroup('user-123', 'group-456');
   * ```
   */
  async removeFromGroup(userId: string, groupId: string): Promise<void> {
    await this.fetch(`/management/users/${userId}/groups/${groupId}`, {
      method: "DELETE",
    });
  }

  /**
   * Creates a new user group.
   *
   * @param request - Group creation request
   * @returns Promise resolving to the created group's ID
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {ValidationError} If the group name is invalid
   * @throws {PreconditionFailedError} If a group with the same name already exists
   *
   * @example
   * ```typescript
   * const groupId = await client.createGroup({ name: 'new-group' });
   * ```
   */
  async createGroup(request: CreateGroupRequest): Promise<string> {
    const response = await this.fetch<{ id: string }>("/management/groups", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    });
    return response.id;
  }

  /**
   * Gets a list of user groups with optional filtering.
   *
   * @param search - Search parameters for filtering groups
   * @param search.name - Optional name filter
   * @param search.firstResult - Starting index for pagination (default: 0)
   * @param search.maxResults - Maximum number of results to return (default: 10)
   * @returns Promise resolving to the list of groups
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   *
   * @example
   * ```typescript
   * const groups = await client.getGroups({
   *   name: 'admin',
   *   firstResult: 0,
   *   maxResults: 10
   * });
   * ```
   */
  async getGroups(search: GroupManagementSearchRequest = {}): Promise<UserManagementListGroupsResponse> {
    const params = new URLSearchParams();
    if (search.name) params.append("name", search.name);
    if (search.firstResult !== undefined) params.append("first_result", search.firstResult.toString());
    if (search.maxResults !== undefined) params.append("max_results", search.maxResults.toString());

    return await this.fetch(`/management/groups${params.toString() ? `?${params.toString()}` : ""}`);
  }

  /**
   * Updates a group's information.
   *
   * @param groupId - ID of the group to update
   * @param request - Group update request
   * @param ignoreOrphanTasks - Whether to ignore tasks assigned to this group
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the group doesn't exist
   * @throws {ValidationError} If the update data is invalid
   * @throws {PreconditionFailedError} If there are pending tasks assigned to the group
   *
   * @example
   * ```typescript
   * await client.updateGroup('group-123', { name: 'new-name' }, true);
   * ```
   */
  async updateGroup(groupId: string, request: UpdateGroupRequest, ignoreOrphanTasks: boolean = false): Promise<void> {
    const params = new URLSearchParams();
    if (ignoreOrphanTasks) {
      params.append("ignore_orphan_tasks", "true");
    }

    await this.fetch(`/management/groups/${groupId}${params.toString() ? `?${params.toString()}` : ""}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    });
  }

  /**
   * Deletes a user group.
   *
   * @param groupId - ID of the group to delete
   * @param ignoreOrphanTasks - Whether to ignore tasks assigned to this group
   * @throws {UnauthorizedError} If the user is not authenticated
   * @throws {ForbiddenError} If the user doesn't have administrative permissions
   * @throws {NotFoundError} If the group doesn't exist
   * @throws {PreconditionFailedError} If there are pending tasks assigned to the group
   *
   * @example
   * ```typescript
   * await client.deleteGroup('group-123', true);
   * ```
   */
  async deleteGroup(groupId: string, ignoreOrphanTasks: boolean = false): Promise<void> {
    const params = new URLSearchParams();
    if (ignoreOrphanTasks) {
      params.append("ignore_orphan_tasks", "true");
    }
    await this.fetch(`/management/groups/${groupId}${params.toString() ? `?${params.toString()}` : ""}`, {
      method: "DELETE",
    });
  }
}
