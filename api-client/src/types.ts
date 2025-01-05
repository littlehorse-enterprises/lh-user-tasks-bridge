/**
 * Response type for listing task type definitions
 */
export type ListTaskTypesResponse = {
  /** Array of user task definition names */
  userTaskDefNames: string[];
  /** Pagination bookmark token, null if no more results */
  bookmark: string | null;
};

/**
 * Response type for listing user tasks
 */
export type ListUserTasksResponse = {
  /** Array of user tasks */
  userTasks: UserTask[];
  /** Pagination bookmark token, null if no more results */
  bookmark: string | null;
};

/**
 * Response type for listing users
 */
export type ListUsersResponse = {
  /** Array of users */
  users: User[];
};

/**
 * Response type for listing user groups
 */
export type ListUserGroupsResponse = {
  /** Array of user groups */
  groups: UserGroup[];
};

/**
 * Represents a user task in the system
 */
export type UserTask = {
  /** Unique identifier for the task */
  id: string;
  /** Workflow run identifier associated with this task */
  wfRunId: string;
  /** Name of the user task definition */
  userTaskDefName: string;
  /** User group assigned to this task, if any */
  userGroup?: UserGroup;
  /** User assigned to this task, if any */
  user?: User;
  /** Current status of the task */
  status: Status;
  /** Additional notes or description for the task */
  notes: string;
  /** ISO 8601 timestamp when the task was scheduled */
  scheduledTime: string;
};

/** Possible states of a user task */
export type Status = "UNASSIGNED" | "ASSIGNED" | "DONE" | "CANCELLED";

/** Supported field types for task inputs/outputs
 * UNRECOGNIZED is used when the server returns an unknown field type
 * that wasn't available when this client was built
 */
export type FieldType =
  | "DOUBLE"
  | "BOOLEAN"
  | "STRING"
  | "INTEGER"
  | "UNRECOGNIZED";

/** Valid field values based on FieldType */
export type FieldValue = number | boolean | string;

/**
 * Represents a user group in the system
 */
export type UserGroup = {
  /** Unique identifier for the group */
  id: string;
  /** Display name of the group */
  name: string;
  /** Whether the group exists in the configured OIDC provider */
  valid: boolean;
};

/**
 * Represents a user in the system
 */
export type User = {
  /** Unique identifier for the user */
  id: string;
  /** User's email address */
  email: string | null;
  /** Username, if different from email */
  username: string | null;
  /** User's first name */
  firstName: string | null;
  /** User's last name */
  lastName: string | null;
  /** Whether the user exists in the configured OIDC provider */
  valid: boolean;
};

/**
 * Request parameters for listing user tasks
 */
export type ListUserTasksRequest = {
  /** Maximum number of results to return */
  limit: number;
  /** Task definition type to filter by */
  type: string;
  /** ISO 8601 timestamp for earliest start date filter */
  earliest_start_date?: string;
  /** ISO 8601 timestamp for latest start date filter */
  latest_start_date?: string;
  /** Filter by task status */
  status?: Status;
  /** Filter by assigned user ID */
  user_id?: string;
  /** Filter by assigned user group ID */
  user_group_id?: string;
  /** Pagination bookmark token */
  bookmark?: string;
};

/**
 * Request parameters for listing task definition names
 */
export type ListUserTaskDefNamesRequest = {
  /** Maximum number of results to return */
  limit: number;
  /** Pagination bookmark token */
  bookmark?: string;
};

/**
 * Response type for listing task definition names
 */
export type ListUserTaskDefNamesResponse = {
  /** Array of task definition names */
  userTaskDefNames?: string[];
};

/**
 * Response type for getting user task details
 */
export type GetUserTaskResponse = {
  /** Unique identifier for the task */
  id: string;
  /** Workflow run identifier */
  wfRunId: string;
  /** Task definition name */
  userTaskDefName: string;
  /** Assigned user group information */
  userGroup: {
    id: string;
    name: string;
  };
  /** Assigned user information */
  user: {
    id: string;
    email: string;
    username: string;
    firstName: string;
    lastName: string;
  };
  /** Current task status */
  status: Status;
  /** Task notes/description */
  notes: string;
  /** ISO 8601 timestamp when task was scheduled */
  scheduledTime: string;
  /** Array of field definitions for this task */
  fields: {
    /** Field identifier */
    name: string;
    /** Human-readable field name */
    displayName: string;
    /** Field description */
    description: string;
    /** Field data type */
    type: FieldType;
    /** Whether the field is required */
    required: boolean;
  }[];
  /** Task result values */
  results: UserTaskResult;
};

/**
 * Extended response type for administrative task views
 */
export type AdminGetUserTaskResponse = GetUserTaskResponse & {
  /** Array of events that occurred on this task */
  events: UserTaskEvent[];
};

/**
 * Event representing task execution
 */
export type UserTaskExecutedEvent = {
  /** Workflow run identifier */
  wfRunId: string;
  /** Task identifier */
  userTaskGuid: string;
};

/**
 * Event representing task assignment changes
 */
export type UserTaskAssignedEvent = {
  /** Previous assigned user ID */
  oldUserId?: string;
  /** Previous assigned group ID */
  oldUserGroup?: string;
  /** New assigned user ID */
  newUserId?: string;
  /** New assigned group ID */
  newUserGroup?: string;
};

/**
 * Event representing task cancellation
 */
export type UserTaskCancelledEvent = {
  /** Cancellation reason/message */
  message: string;
};

/**
 * Event representing changes to a task's lifecycle
 * Events are discriminated by their structure:
 * - UserTaskExecutedEvent: Contains wfRunId and userTaskGuid
 * - UserTaskAssignedEvent: Contains old/new user/group IDs
 * - UserTaskCancelledEvent: Contains a message
 */
export type UserTaskEvent =
  | UserTaskExecutedEvent
  | UserTaskAssignedEvent
  | UserTaskCancelledEvent;

/**
 * Structure for task results
 */
export type UserTaskResult = {
  /** Map of field names to their values and types */
  [key: string]: {
    /** Field data type */
    type: FieldType;
    /** Field value */
    value: FieldValue;
  };
};
