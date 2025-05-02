"use server";
import { getClient } from "@/lib/client";
import {
  AdminGetUserTaskDetailParams,
  AdminTaskActionParams,
  AssignmentRequest,
  GetAllTasksParams,
  GetAllUserTasksDefParams,
  GetUserFromIdentityProviderParams,
  GetUsersFromIdentityProviderParams,
  UserTaskVariableValue,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function adminGetAllTasks(
  tenantId: string,
  params: GetAllTasksParams,
) {
  const client = await getClient(tenantId);
  return client.admin.getAllTasks(params);
}

export async function adminGetAllUserTasksDef(
  tenantId: string,
  params: GetAllUserTasksDefParams,
) {
  const client = await getClient(tenantId);
  return client.admin.getAllUserTasksDef(params);
}

export async function adminGetUserTaskDetail(
  tenantId: string,
  params: AdminGetUserTaskDetailParams,
) {
  const client = await getClient(tenantId);
  return client.admin.getUserTaskDetail(params);
}

export async function adminCompleteUserTask(
  tenantId: string,
  params: AdminTaskActionParams,
  results: Record<string, UserTaskVariableValue>,
) {
  const client = await getClient(tenantId);
  await client.admin.completeUserTask(params, results);
}

export async function adminAssignUserTask(
  tenantId: string,
  params: AdminTaskActionParams,
  assignmentRequest: AssignmentRequest,
) {
  const client = await getClient(tenantId);
  await client.admin.assignUserTask(params, assignmentRequest);
}

export async function adminCancelUserTask(
  tenantId: string,
  params: AdminTaskActionParams,
) {
  const client = await getClient(tenantId);
  await client.admin.cancelUserTask(params);
}

export async function adminClaimUserTask(
  tenantId: string,
  params: AdminTaskActionParams,
) {
  const client = await getClient(tenantId);
  await client.admin.claimUserTask(params);
}

export async function adminGetUserGroups(tenantId: string) {
  const client = await getClient(tenantId);
  return client.admin.getUserGroups();
}

export async function adminGetUsers(
  tenantId: string,
  params: GetUsersFromIdentityProviderParams,
) {
  const client = await getClient(tenantId);
  return client.admin.getUsers(params);
}

export async function adminGetUserInfo(
  tenantId: string,
  params: GetUserFromIdentityProviderParams,
) {
  const client = await getClient(tenantId);
  return client.admin.getUserInfo(params);
}
