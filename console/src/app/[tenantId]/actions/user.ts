"use server";
import { getClient } from "@/lib/client";
import { withErrorHandling } from "@/lib/error-handling";
import {
  CancelUserTaskParams,
  ClaimUserTaskParams,
  CompleteUserTaskParams,
  DeleteUserTaskCommentParams,
  GetClaimableTasksParams,
  GetMyTasksParams,
  GetUserTaskCommentsParams,
  GetUserTaskDetailParams,
  PostUserTaskCommentParams,
  EditUserTaskCommentParams,
  UserTaskVariableValue,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function getUserTasks(tenantId: string, params: GetMyTasksParams) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.getMyTasks(params);
  });
}

export async function getUserTaskDetail(
  tenantId: string,
  params: GetUserTaskDetailParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.getUserTaskDetail(params);
  });
}

export async function completeUserTask(
  tenantId: string,
  params: CompleteUserTaskParams,
  results: Record<string, UserTaskVariableValue>,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.completeUserTask(params, results);
  });
}

export async function cancelUserTask(
  tenantId: string,
  params: CancelUserTaskParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.cancelUserTask(params);
  });
}

export async function claimUserTask(
  tenantId: string,
  params: ClaimUserTaskParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.claimUserTask(params);
  });
}

export async function getUserGroupsFromIdentityProvider(tenantId: string) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.getUserGroupsFromIdentityProvider();
  });
}

export async function getMyUserInfo(tenantId: string) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.getMyUserInfo();
  });
}

export async function getClaimableTasks(
  tenantId: string,
  params: GetClaimableTasksParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.getClaimableTasks(params);
  });
}

export async function getUserTaskComments(
  tenantId: string,
  params: GetUserTaskCommentsParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.getUserTaskComments(params);
  });
}

export async function postUserTaskComment(
  tenantId: string,
  params: PostUserTaskCommentParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.postUserTaskComment(params);
  });
}

export async function editUserTaskComment(
  tenantId: string,
  params: EditUserTaskCommentParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.editUserTaskComment(params);
  });
}

export async function deleteUserTaskComment(
  tenantId: string,
  params: DeleteUserTaskCommentParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.user.deleteUserTaskComment(params);
  });
}
