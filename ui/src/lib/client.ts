import {
  LHUserTasksError,
  LittleHorseUserTasksApiClient,
} from "@littlehorse-enterprises/user-tasks-api-client";
import { redirect } from "next/navigation";
import { auth } from "../app/api/auth/[...nextauth]/authOptions";

export async function getClient(tenantId: string) {
  const session = await auth();

  if (!session) redirect(`/api/auth/signin?callbackUrl=/`);

  return new LittleHorseUserTasksApiClient({
    baseUrl: process.env.LHUT_API_URL!,
    tenantId,
    accessToken: session.access_token,
  });
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
