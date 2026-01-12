"use server";
import { getClient } from "@/lib/client";
import { withErrorHandling } from "@/lib/error-handling";
import {
  AdminGetUserTaskDetailParams,
  AdminTaskActionParams,
  AssignmentRequest,
  GetAllTasksParams,
  GetAllUserTaskDefsParams,
  GetUserFromIdentityProviderParams,
  GetUsersFromIdentityProviderParams,
  UserTaskVariableValue,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function adminGetAllTasks(
  tenantId: string,
  params: GetAllTasksParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.getAllTasks(params);
  });
}

export async function adminGetAllUserTaskDefs(
  tenantId: string,
  params: GetAllUserTaskDefsParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.getAllUserTaskDefs(params);
  });
}

export async function adminGetUserTaskDetail(
  tenantId: string,
  params: AdminGetUserTaskDetailParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.getUserTaskDetail(params);
  });
}

export async function adminCompleteUserTask(
  tenantId: string,
  params: AdminTaskActionParams,
  results: Record<string, UserTaskVariableValue>,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.completeUserTask(params, results);
  });
}

export async function adminAssignUserTask(
  tenantId: string,
  params: AdminTaskActionParams,
  assignmentRequest: AssignmentRequest,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.assignUserTask(params, assignmentRequest);
  });
}

export async function adminCancelUserTask(
  tenantId: string,
  params: AdminTaskActionParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.cancelUserTask(params);
  });
}

export async function adminClaimUserTask(
  tenantId: string,
  params: AdminTaskActionParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.claimUserTask(params);
  });
}

export async function adminGetUserGroups(tenantId: string) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.getUserGroups();
  });
}

export async function adminGetUsers(
  tenantId: string,
  params: GetUsersFromIdentityProviderParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.getUsers(params);
  });
}

export async function adminGetUserInfo(
  tenantId: string,
  params: GetUserFromIdentityProviderParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.admin.getUserInfo(params);
  });
}
