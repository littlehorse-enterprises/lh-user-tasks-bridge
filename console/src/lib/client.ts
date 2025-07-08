import { LHUTBApiClient } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { redirect } from "next/navigation";
import { auth } from "../auth";

export class LHUTBApiClientSingleton {
  private static instance: LHUTBApiClient | null = null;
  private static currentConfig: {
    baseUrl: string;
    tenantId: string;
    accessToken: string;
  } | null = null;

  private constructor() {}

  public static async getInstance(tenantId: string): Promise<LHUTBApiClient> {
    const session = await auth();

    if (!session) redirect(`/api/auth/signin?callbackUrl=/`);

    const config = {
      baseUrl: process.env.LHUT_API_URL!,
      tenantId,
      accessToken: session.accessToken,
    };

    // Create new instance if config changes or doesn't exist
    if (
      !this.instance ||
      !this.currentConfig ||
      this.currentConfig.baseUrl !== config.baseUrl ||
      this.currentConfig.tenantId !== config.tenantId ||
      this.currentConfig.accessToken !== config.accessToken
    ) {
      this.instance = new LHUTBApiClient(config);
      this.currentConfig = config;
    }

    return this.instance;
  }
}

export async function getClient(tenantId: string) {
  return LHUTBApiClientSingleton.getInstance(tenantId);
}
