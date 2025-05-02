"use server";
import { getClient } from "@/lib/client";
import {
  CreateGroupRequest,
  DeleteGroupParams,
  GetGroupsParams,
  UpdateGroupParams,
  UpdateGroupRequest,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function createGroup(
  tenantId: string,
  request: CreateGroupRequest,
) {
  const client = await getClient(tenantId);
  return client.groupManagement.createGroup(request);
}

export async function getGroups(tenantId: string, params: GetGroupsParams) {
  const client = await getClient(tenantId);
  return client.groupManagement.getGroups(params);
}

export async function updateGroup(
  tenantId: string,
  params: UpdateGroupParams,
  request: UpdateGroupRequest,
) {
  const client = await getClient(tenantId);
  return client.groupManagement.updateGroup(params, request);
}

export async function deleteGroup(tenantId: string, params: DeleteGroupParams) {
  const client = await getClient(tenantId);
  return client.groupManagement.deleteGroup(params);
}
