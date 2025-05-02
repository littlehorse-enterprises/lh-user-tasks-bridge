"use server";
import { getClient } from "@/lib/client";
import {
  CreateManagedUserRequest,
  GetUserFromIdPParams,
  GetUsersFromIdentityProviderParams,
  UpdateManagedUserRequest,
  UpdateUserParams,
  UpsertPasswordParams,
  UpsertPasswordRequest,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function getUsersFromIdP(
  tenantId: string,
  params: GetUsersFromIdentityProviderParams,
) {
  const client = await getClient(tenantId);
  return client.userManagement.getUsersFromIdP(params);
}

export async function createUser(
  tenantId: string,
  request: CreateManagedUserRequest,
) {
  const client = await getClient(tenantId);
  return client.userManagement.createUser(request);
}

export async function upsertPassword(
  tenantId: string,
  params: UpsertPasswordParams,
  request: UpsertPasswordRequest,
) {
  const client = await getClient(tenantId);
  return client.userManagement.upsertPassword(params, request);
}

export async function getUserFromIdP(
  tenantId: string,
  params: GetUserFromIdPParams,
) {
  const client = await getClient(tenantId);
  return client.userManagement.getUserFromIdP(params);
}

export async function updateUser(
  tenantId: string,
  params: UpdateUserParams,
  request: UpdateManagedUserRequest,
) {
  const client = await getClient(tenantId);
  return client.userManagement.updateUser(params, request);
}
