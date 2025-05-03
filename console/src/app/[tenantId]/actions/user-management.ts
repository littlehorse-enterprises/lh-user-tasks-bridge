"use server";
import { getClient } from "@/lib/client";
import { withErrorHandling } from "@/lib/error-handling";
import {
  AdminRoleParams,
  CreateManagedUserRequest,
  DeleteUserParams,
  GetUserFromIdPParams,
  GetUsersFromIdentityProviderParams,
  JoinOrLeaveGroupParams,
  UpdateManagedUserRequest,
  UpdateUserParams,
  UpsertPasswordParams,
  UpsertPasswordRequest,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function getUsersFromIdP(
  tenantId: string,
  params: GetUsersFromIdentityProviderParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.getUsersFromIdP(params);
  });
}

export async function createUser(
  tenantId: string,
  request: CreateManagedUserRequest,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.createUser(request);
  });
}

export async function upsertPassword(
  tenantId: string,
  params: UpsertPasswordParams,
  request: UpsertPasswordRequest,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.upsertPassword(params, request);
  });
}

export async function getUserFromIdP(
  tenantId: string,
  params: GetUserFromIdPParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.getUserFromIdP(params);
  });
}

export async function updateUser(
  tenantId: string,
  params: UpdateUserParams,
  request: UpdateManagedUserRequest,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.updateUser(params, request);
  });
}

export async function deleteUser(tenantId: string, params: DeleteUserParams) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.deleteUser(params);
  });
}

export async function assignAdminRole(
  tenantId: string,
  params: AdminRoleParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.assignAdminRole(params);
  });
}

export async function removeAdminRole(
  tenantId: string,
  params: AdminRoleParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.removeAdminRole(params);
  });
}

export async function addUserToGroup(
  tenantId: string,
  params: JoinOrLeaveGroupParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.addUserToGroup(params);
  });
}

export async function removeUserFromGroup(
  tenantId: string,
  params: JoinOrLeaveGroupParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.userManagement.removeUserFromGroup(params);
  });
}
