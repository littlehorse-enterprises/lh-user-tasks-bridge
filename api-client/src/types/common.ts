// Enums
export enum UserTaskFieldType {
  DOUBLE = "DOUBLE",
  BOOLEAN = "BOOLEAN",
  STRING = "STRING",
  INTEGER = "INTEGER",
  UNRECOGNIZED = "UNRECOGNIZED",
}

export enum UserTaskStatus {
  UNASSIGNED = "UNASSIGNED",
  ASSIGNED = "ASSIGNED",
  DONE = "DONE",
  CANCELLED = "CANCELLED",
}

export enum UserTaskEventType {
  TASK_EXECUTED = "TASK_EXECUTED",
  TASK_ASSIGNED = "TASK_ASSIGNED",
  TASK_CANCELLED = "TASK_CANCELLED",
}

export enum CustomUserIdClaim {
  // Matching the Java enum CustomUserIdClaim
  SUB = "SUB",
  PREFERRED_USERNAME = "PREFERRED_USERNAME",
  EMAIL = "EMAIL",
  ID = "ID",
}

// Common DTOs
export interface UserDTO {
  id: string;
  email?: string;
  username?: string;
  firstName?: string;
  lastName?: string;
  valid?: boolean;
}

export interface UserGroupDTO {
  id: string;
  name: string;
  valid?: boolean;
}

export interface UserTaskVariableValue {
  value: number | string | boolean;
  type: UserTaskFieldType;
}

export interface UserGroupListDTO {
  groups: UserGroupDTO[];
}

export interface SimpleUserTaskRunDTO {
  id: string;
  wfRunId: string;
  userTaskDefName: string;
  userGroup?: UserGroupDTO;
  user?: UserDTO;
  status: UserTaskStatus;
  notes?: string;
  scheduledTime: string; // ISO date string format
}

export interface UserTaskRunListDTO {
  userTasks: SimpleUserTaskRunDTO[];
  bookmark?: string;
}

export interface UserTaskFieldDTO {
  name: string;
  displayName?: string;
  description?: string;
  required: boolean;
  type: UserTaskFieldType;
  options?: string[];
}

export interface AuditEventDTO {
  time: string; // ISO date string format
  event: UserTaskExecutedEvent | UserTaskAssignedEvent | UserTaskCancelledEvent;
  type: UserTaskEventType;
}

export interface DetailedUserTaskRunDTO extends SimpleUserTaskRunDTO {
  fields: UserTaskFieldDTO[];
  results?: Record<string, UserTaskVariableValue>;
  events?: AuditEventDTO[];
}

export interface UserTaskExecutedEvent {
  wfRunId: string;
  userTaskGuid: string;
}

export interface UserTaskAssignedEvent {
  oldUserId?: string;
  oldUserGroup?: string;
  newUserId?: string;
  newUserGroup?: string;
}

export interface UserTaskCancelledEvent {
  message: string;
}
