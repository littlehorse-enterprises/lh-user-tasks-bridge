import { UserDTO, UserTaskStatus } from "./common";

// Response DTOs specific to AdminController
export interface UserListDTO {
  users: UserDTO[];
}

export interface UserTaskDefListDTO {
  userTaskDefNames: string[];
  bookmark: string | null;
}

// Request DTOs specific to AdminController
export interface GetAllTasksParams {
  earliest_start_date?: string;
  latest_start_date?: string;
  status?: UserTaskStatus;
  type: string;
  limit: number;
  user_id?: string;
  user_group_id?: string;
  bookmark?: string;
}

export interface GetAllUserTaskDefsParams {
  limit: number;
  bookmark?: string;
}

export interface AdminGetUserTaskDetailParams {
  wf_run_id: string;
  user_task_guid: string;
}

export interface AssignmentRequest {
  userId?: string;
  userGroup?: string;
}

export interface GetUsersFromIdentityProviderParams {
  email?: string;
  first_name?: string;
  last_name?: string;
  username?: string;
  user_group_id?: string;
  first_result?: number;
  max_results?: number;
}

export interface GetUserFromIdentityProviderParams {
  user_id: string;
}

export interface AdminTaskActionParams {
  wf_run_id: string;
  user_task_guid: string;
}
