// Response DTOs
export interface IDPGroupDTO {
  id: string;
  name: string;
}

export interface IDPGroupListDTO {
  groups: IDPGroupDTO[];
}

// Request DTOs
export interface CreateGroupRequest {
  name: string;
}

export interface UpdateGroupRequest {
  name: string;
}

// Request Params
export interface GetGroupsParams {
  name?: string;
  first_result?: number;
  max_results?: number;
}

export interface UpdateGroupParams {
  group_id: string;
  ignore_orphan_tasks?: boolean;
}

export interface DeleteGroupParams {
  group_id: string;
  ignore_orphan_tasks?: boolean;
}
