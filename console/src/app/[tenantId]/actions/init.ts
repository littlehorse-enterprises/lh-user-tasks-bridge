"use server";
import { getClient } from "@/lib/client";
import { withErrorHandling } from "@/lib/error-handling";

export async function checkConnection(tenantId: string) {
  return withErrorHandling(async () => {
    const client = await getClient(tenantId);
    return client.init.checkConnection();
  });
}
