"use server";
import { getClient } from "@/lib/client";
import { withErrorHandling } from "@/lib/error-handling";
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
  return withErrorHandling(async () => {
    try {
      console.log("Creating group with request:", request);
      const client = await getClient(tenantId);
      const response = await client.groupManagement.createGroup(request);
      console.log("Create group successful response:", response);
      return response;
    } catch (error) {
      console.error("Create group raw error:", error);
      // Re-throw to let withErrorHandling process it
      throw error;
    }
  });
}

export async function getGroups(tenantId: string, params: GetGroupsParams) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.groupManagement.getGroups(params);
  });
}

export async function updateGroup(
  tenantId: string,
  params: UpdateGroupParams,
  request: UpdateGroupRequest,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.groupManagement.updateGroup(params, request);
  });
}

export async function deleteGroup(tenantId: string, params: DeleteGroupParams) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.groupManagement.deleteGroup(params);
  });
}
