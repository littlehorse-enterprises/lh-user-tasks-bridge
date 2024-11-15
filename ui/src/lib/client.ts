import {
  LittleHorseUserTasksApiClient,
  LHUserTasksError,
} from "@littlehorse-enterprises/user-tasks-api-client";
import { redirect } from "next/navigation";
import { auth } from "../app/api/auth/[...nextauth]/authOptions";

export async function getClient() {
  const session = await auth();

  if (!session) redirect("/api/auth/signout");

  return new LittleHorseUserTasksApiClient({
    baseUrl: process.env.LHUT_API_URL!,
    tenantId: process.env.LHUT_TENANT_ID!,
    accessToken: session.access_token,
  });
}

export async function clientWithErrorHandling<T>(
  action: (client: LittleHorseUserTasksApiClient) => Promise<T>,
) {
  const client = await getClient();
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
