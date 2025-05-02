import { auth } from "@/app/api/auth/[...nextauth]/authOptions";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { UserTaskStatus } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { AlertCircle } from "lucide-react";
import { adminGetAllTasks, adminGetAllUserTasksDef } from "../actions/admin";
import AdminTabs from "./components/admin-tabs";

export default async function AdminPage({
  params,
  searchParams,
}: {
  params: { tenantId: string };
  searchParams: { tab?: string };
}) {
  const userTaskDefResult = await adminGetAllUserTasksDef(
    params.tenantId,
    {
      // TODO: add pagination so this needs to be on client using `useInfiniteQuery`
      limit: 999,
    },
  );

  // Handle error getting user task def names
  if (userTaskDefResult.error) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Error loading task definitions</AlertTitle>
        <AlertDescription>
          {userTaskDefResult.error.message}
        </AlertDescription>
      </Alert>
    );
  }

  // Ensure the data exists to avoid map errors
  if (!userTaskDefResult.data || !userTaskDefResult.data.userTaskDefNames) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Error loading task definitions</AlertTitle>
        <AlertDescription>
          Failed to load task definition data
        </AlertDescription>
      </Alert>
    );
  }

  // Get current user information from session
  const session = await auth();
  const userId = session?.user?.id;

  // Get task counts for each definition
  const taskCountsPromises = userTaskDefResult.data.userTaskDefNames.map(
    async (userTaskDefName: string) => {
      // Get unassigned tasks count - limit to 99
      const unassignedResult = await adminGetAllTasks(params.tenantId, {
        type: userTaskDefName,
        status: UserTaskStatus.UNASSIGNED,
        limit: 99, // Fetch max 99 tasks
      });

      // Get tasks assigned to current user - limit to 99
      const assignedToMeResult = userId
        ? await adminGetAllTasks(params.tenantId, {
            type: userTaskDefName,
            status: UserTaskStatus.ASSIGNED,
            user_id: userId,
            limit: 99, // Fetch max 99 tasks
          })
        : { data: { userTasks: [] } };

      // Handle error responses
      const unassignedCount = unassignedResult.data?.userTasks?.length || 0;
      const assignedToMeCount = assignedToMeResult.data?.userTasks?.length || 0;

      return {
        name: userTaskDefName,
        unassignedCount,
        assignedToMeCount,
        hasError: unassignedResult.error || assignedToMeResult.error,
      };
    },
  );

  const taskCounts = await Promise.all(taskCountsPromises);
  
  // Check if all task counts have errors
  const allTaskCountsHaveErrors = taskCounts.length > 0 && 
    taskCounts.every(count => count.hasError);
    
  if (allTaskCountsHaveErrors) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Error loading task counts</AlertTitle>
        <AlertDescription>
          Unable to retrieve task count information
        </AlertDescription>
      </Alert>
    );
  }

  // Get the tab from URL or default to "tasks"
  const currentTab = searchParams.tab || "tasks";

  return (
    <>
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold">Admin Dashboard</h1>

        <AdminTabs
          currentTab={currentTab}
          tenantId={params.tenantId}
          userTaskDefNames={userTaskDefResult.data.userTaskDefNames}
          userTaskDefCounts={taskCounts}
        />
      </div>
    </>
  );
}
