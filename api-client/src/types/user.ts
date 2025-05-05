import { UserTaskStatus, UserTaskVariableValue } from "./common";

// Request params
export interface GetMyTasksParams {
  earliest_start_date?: string;
  latest_start_date?: string;
  status?: UserTaskStatus;
  type?: string;
  user_group_id?: string;
  limit: number;
  bookmark?: string;
}

export interface GetUserTaskDetailParams {
  wf_run_id: string;
  user_task_guid: string;
}

export interface CompleteUserTaskParams {
  wf_run_id: string;
  user_task_guid: string;
}

export interface ClaimUserTaskParams {
  wf_run_id: string;
  user_task_guid: string;
}

export interface CancelUserTaskParams {
  wf_run_id: string;
  user_task_guid: string;
}

export interface GetClaimableTasksParams {
  earliest_start_date?: string;
  latest_start_date?: string;
  user_group_id: string;
  limit: number;
  bookmark?: string;
}
