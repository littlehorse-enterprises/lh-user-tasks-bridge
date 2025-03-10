import { auth } from "@/app/api/auth/[...nextauth]/authOptions";
import {
  adminListUserTaskDefNames,
  adminListUserTasks,
} from "../actions/admin";
import AdminTabs from "./components/admin-tabs";

export default async function AdminPage({
  params,
  searchParams,
}: {
  params: { tenantId: string };
  searchParams: { tab?: string };
}) {
  const adminListUserTaskDefNamesResponse = await adminListUserTaskDefNames(
    params.tenantId,
    {
      // TODO: add pagination so this needs to be on client using `useInfiniteQuery`
      limit: 99,
    },
  );

  if ("message" in adminListUserTaskDefNamesResponse)
    throw new Error(adminListUserTaskDefNamesResponse.message);

  // Get current user information from session
  const session = await auth();
  if (!session?.user) throw new Error("Session found but user not found");
  const userId = session.user.id;

  // Get task counts for each definition
  const taskCountsPromises =
    adminListUserTaskDefNamesResponse.userTaskDefNames.map(
      async (userTaskDefName) => {
        // Get unassigned tasks count - limit to 99
        const unassignedResponse = await adminListUserTasks(params.tenantId, {
          type: userTaskDefName,
          status: "UNASSIGNED",
          limit: 99, // Fetch max 99 tasks
        });

        // Get tasks assigned to current user - limit to 99
        const assignedToMeResponse = userId
          ? await adminListUserTasks(params.tenantId, {
              type: userTaskDefName,
              status: "ASSIGNED",
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
