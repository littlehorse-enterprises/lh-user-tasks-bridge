"use server";
import { getClient } from "@/lib/client";
import {
  CancelUserTaskParams,
  ClaimUserTaskParams,
  CompleteUserTaskParams,
  CompleteUserTaskRequest,
  GetClaimableTasksParams,
  GetMyTasksParams,
  GetUserTaskDetailParams,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function getUserTasks(tenantId: string, params: GetMyTasksParams) {
  const client = await getClient(tenantId);
  return client.user.getMyTasks(params);
}

export async function getUserTaskDetail(
  tenantId: string,
  params: GetUserTaskDetailParams,
) {
  const client = await getClient(tenantId);
  return client.user.getUserTaskDetail(params);
}

export async function completeUserTask(
  tenantId: string,
  params: CompleteUserTaskParams,
  results: CompleteUserTaskRequest,
) {
  const client = await getClient(tenantId);
  return client.user.completeUserTask(params, results);
}

export async function cancelUserTask(
  tenantId: string,
  params: CancelUserTaskParams,
) {
  const client = await getClient(tenantId);
  return client.user.cancelUserTask(params);
}

export async function claimUserTask(
  tenantId: string,
  params: ClaimUserTaskParams,
) {
  const client = await getClient(tenantId);
  return client.user.claimUserTask(params);
}

export async function getUserGroupsFromIdentityProvider(tenantId: string) {
  const client = await getClient(tenantId);
  return client.user.getUserGroupsFromIdentityProvider();
}

export async function getMyUserInfo(tenantId: string) {
  const client = await getClient(tenantId);
  return client.user.getMyUserInfo();
}

export async function getClaimableTasks(
  tenantId: string,
  params: GetClaimableTasksParams,
) {
  const client = await getClient(tenantId);
  return client.user.getClaimableTasks(params);
}
