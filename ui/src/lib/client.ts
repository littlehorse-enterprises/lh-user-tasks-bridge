import {
  LHUserTasksError,
  LittleHorseUserTasksApiClient,
} from "@littlehorse-enterprises/user-tasks-api-client";
import { redirect } from "next/navigation";
import { auth } from "../app/api/auth/[...nextauth]/authOptions";

export class LHUserTasksClientSingleton {
  private static instance: LittleHorseUserTasksApiClient | null = null;
  private static currentConfig: {
    baseUrl: string;
    tenantId: string;
    accessToken: string;
  } | null = null;

  private constructor() {}

  public static async getInstance(
    tenantId: string,
  ): Promise<LittleHorseUserTasksApiClient> {
    const session = await auth();

    if (!session) redirect(`/api/auth/signin?callbackUrl=/`);

    const config = {
      baseUrl: process.env.LHUT_API_URL!,
      tenantId,
      accessToken: session.access_token,
    };

    // Create new instance if config changes or doesn't exist
    if (
      !this.instance ||
      !this.currentConfig ||
      this.currentConfig.baseUrl !== config.baseUrl ||
      this.currentConfig.tenantId !== config.tenantId ||
      this.currentConfig.accessToken !== config.accessToken
    ) {
      this.instance = new LittleHorseUserTasksApiClient(config);
      this.currentConfig = config;
    }

    return this.instance;
  }
}

export async function getClient(tenantId: string) {
  return LHUserTasksClientSingleton.getInstance(tenantId);
}

export async function clientWithErrorHandling<T>(
  tenantId: string,
  action: (client: LittleHorseUserTasksApiClient) => Promise<T>,
) {
  const client = await getClient(tenantId);
  try {
    return await action(client);
  } catch (error) {
    console.error(error);
    if (error instanceof LHUserTasksError) {
      return { message: error.message };
    }
    throw error;
  }
}
