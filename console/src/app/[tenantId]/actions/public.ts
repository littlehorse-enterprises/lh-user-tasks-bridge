"use server";
import { getClient } from "@/lib/client";
import { GetIdentityProviderConfigParams } from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function getIdentityProviderConfig(
  tenantId: string,
  params: GetIdentityProviderConfigParams,
) {
  const client = await getClient(tenantId);
  return client.public.getIdentityProviderConfig(params);
}
