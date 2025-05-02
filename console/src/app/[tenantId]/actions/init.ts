"use server";
import { getClient } from "@/lib/client";

export async function checkConnection(tenantId: string) {
  const client = await getClient(tenantId);
  return client.init.checkConnection();
}
