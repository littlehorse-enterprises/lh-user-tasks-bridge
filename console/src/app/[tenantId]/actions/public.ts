"use server";
import { getClient } from "@/lib/client";
import { withErrorHandling } from "@/lib/error-handling";
import { GetIdentityProviderConfigParams } from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function getIdentityProviderConfig(
  tenantId: string,
  params: GetIdentityProviderConfigParams,
) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.public.getIdentityProviderConfig(params);
  });
}
