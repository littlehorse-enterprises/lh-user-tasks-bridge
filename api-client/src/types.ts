export type ListTaskTypesResponse = {
  userTaskDefNames: string[];
  bookmark: string | null;
};

export type ListUserTasksResponse = {
  userTasks: UserTask[];
  bookmark: string | null;
};

export type ListUsersResponse = {
  users: User[];
};

export type ListUserGroupsResponse = {
  groups: UserGroup[];
};

export type UserTask = {
  id: string;
  wfRunId: string;
  userTaskDefName: string;
  userGroup?: UserGroup;
  user?: User;
  status: Status;
  notes: string;
  scheduledTime: string;
};

export type Status = "UNASSIGNED" | "ASSIGNED" | "DONE" | "CANCELLED";
export type FieldType =
  | "DOUBLE"
  | "BOOLEAN"
  | "STRING"
  | "INTEGER"
  | "UNRECOGNIZED";

export type FieldValue = number | boolean | string;

export type UserGroup = {
  id: string;
  name: string;
  valid: boolean;
};

export type User = {
  id: string;
  email: string | null;
  username: string | null;
  firstName: string | null;
  lastName: string | null;
  valid: boolean;
};

export type ListUserTasksRequest = {
  limit: number;
  type: string;
  earliest_start_date?: string;
  latest_start_date?: string;
  status?: Status;
  user_id?: string;
  user_group_id?: string;
  bookmark?: string;
};

export type ListUserTaskDefNamesRequest = {
  limit: number;
  bookmark?: string;
};

export type ListUserTaskDefNamesResponse = {
  userTaskDefNames?: string[];
};

export type GetUserTaskResponse = {
  id: string;
  wfRunId: string;
  userTaskDefName: string;
  userGroup: {
    id: string;
    name: string;
  };
  user: {
    id: string;
    email: string;
    username: string;
    firstName: string;
    lastName: string;
  };
  status: Status;
  notes: string;
  scheduledTime: string;
  fields: {
    name: string;
    displayName: string;
    description: string;
    type: FieldType;
    required: boolean;
  }[];
  results: UserTaskResult;
};

export type AdminGetUserTaskResponse = GetUserTaskResponse & {
  events: UserTaskEvent[];
};

export type UserTaskExecutedEvent = {
  wfRunId: string;
  userTaskGuid: string;
};

export type UserTaskAssignedEvent = {
  oldUserId?: string;
  oldUserGroup?: string;
  newUserId?: string;
  newUserGroup?: string;
};

export type UserTaskCancelledEvent = {
  message: string;
};

export type UserTaskEvent =
  | UserTaskExecutedEvent
  | UserTaskAssignedEvent
  | UserTaskCancelledEvent;

export type UserTaskResult = {
  [key: string]: {
    type: FieldType;
    value: FieldValue;
  };
};
