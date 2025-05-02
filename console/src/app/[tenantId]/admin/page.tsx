import { auth } from "@/app/api/auth/[...nextauth]/authOptions";
import { UserTaskStatus } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { adminGetAllTasks, adminGetAllUserTasksDef } from "../actions/admin";
import AdminTabs from "./components/admin-tabs";

export default async function AdminPage({
  params,
  searchParams,
}: {
  params: { tenantId: string };
  searchParams: { tab?: string };
}) {
  const adminListUserTaskDefNamesResponse = await adminGetAllUserTasksDef(
    params.tenantId,
    {
      // TODO: add pagination so this needs to be on client using `useInfiniteQuery`
      limit: 999,
    },
  );

  // Get current user information from session
  const session = await auth();
  const userId = session?.user?.id;

  // Get task counts for each definition
  const taskCountsPromises = adminListUserTaskDefNamesResponse.userTaskDefNames.map(
    async (userTaskDefName: string) => {
      // Get unassigned tasks count - limit to 99
      const unassignedResponse = await adminGetAllTasks(params.tenantId, {
        type: userTaskDefName,
        status: UserTaskStatus.UNASSIGNED,
        limit: 99, // Fetch max 99 tasks
      });

      // Get tasks assigned to current user - limit to 99
      const assignedToMeResponse = userId
        ? await adminGetAllTasks(params.tenantId, {
            type: userTaskDefName,
            status: UserTaskStatus.ASSIGNED,
            user_id: userId,
            limit: 99, // Fetch max 99 tasks
          })
        : { userTasks: [] };

      // Handle error responses
      const unassignedCount =
        "userTasks" in unassignedResponse
          ? unassignedResponse.userTasks.length
          : 0;
      const assignedToMeCount =
        "userTasks" in assignedToMeResponse
          ? assignedToMeResponse.userTasks.length
          : 0;

      return {
        name: userTaskDefName,
        unassignedCount,
        assignedToMeCount,
      };
    },
  );

  const taskCounts = await Promise.all(taskCountsPromises);

  // Get the tab from URL or default to "tasks"
  const currentTab = searchParams.tab || "tasks";

  return (
    <>
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold">Admin Dashboard</h1>

        <AdminTabs
          currentTab={currentTab}
          tenantId={params.tenantId}
          userTaskDefNames={adminListUserTaskDefNamesResponse.userTaskDefNames}
          userTaskDefCounts={taskCounts}
        />
      </div>
    </>
  );
}
