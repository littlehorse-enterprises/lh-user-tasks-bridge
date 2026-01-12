import { IDPGroupDTO } from "./group-management";

// Response DTOs
export interface IDPUserDTO {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  groups: IDPGroupDTO[];
  realmRoles: string[];
  clientRoles: Record<string, string[]>;
  enabled: boolean;
}

export interface IDPUserListDTO {
  users: IDPUserDTO[];
}

// Request DTOs
export interface IDPUserSearchRequestFilter {
  email?: string;
  firstName?: string;
  lastName?: string;
  username?: string;
  userGroupId?: string;
}

export interface CreateManagedUserRequest {
  firstName: string;
  lastName: string;
  username: string;
  email: string;
}

export interface UpsertPasswordRequest {
  password: string;
  temporary?: boolean;
}

export interface UpdateManagedUserRequest {
  firstName?: string;
  lastName?: string;
  email: string;
  username?: string;
  enabled?: boolean;
}

// Request Params
export interface GetUsersFromIdPParams {
  email?: string;
  first_name?: string;
  last_name?: string;
  username?: string;
  user_group_id?: string;
  first_result?: number;
  max_results?: number;
}

export interface GetUserFromIdPParams {
  user_id: string;
}

export interface UpsertPasswordParams {
  user_id: string;
}

export interface UpdateUserParams {
  user_id: string;
}

export interface DeleteUserParams {
  user_id: string;
  ignore_orphan_tasks?: boolean;
}

export interface AdminRoleParams {
  user_id: string;
}

export interface JoinOrLeaveGroupParams {
  user_id: string;
  group_id: string;
}
