import {
  Alert,
  AlertDescription,
  AlertTitle,
} from "@littlehorse-enterprises/ui-library/alert";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@littlehorse-enterprises/ui-library/tabs";
import { AlertCircle, CheckCircle2, Clock, Users } from "lucide-react";
import {
  getClaimableTasks,
  getUserGroupsFromIdentityProvider,
  getUserTasks,
} from "./actions/user";
import ListUserTasks from "./components/user-task/list";
import ListClaimableUserTasks from "./components/user-task/list-claimable";

export default async function Home({
  params,
}: {
  params: { tenantId: string };
}) {
  const userGroupsResult = await getUserGroupsFromIdentityProvider(
    params.tenantId,
  );

  // Handle error getting user groups
  if (userGroupsResult.error) {
    return (
      <div className="max-w-4xl mx-auto">
        <Alert variant="destructive" className="shadow-lg">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error loading user groups</AlertTitle>
          <AlertDescription>{userGroupsResult.error.message}</AlertDescription>
        </Alert>
      </div>
    );
  }

  // Ensure data exists to avoid TypeScript errors
  if (!userGroupsResult.data) {
    return (
      <div className="max-w-4xl mx-auto">
        <Alert variant="destructive" className="shadow-lg">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error loading user groups</AlertTitle>
          <AlertDescription>
            Could not retrieve user groups data
          </AlertDescription>
        </Alert>
      </div>
    );
  }

  // If no groups are available, show UserTasks only
  if (userGroupsResult.data.groups.length === 0) {
    const userTasksResult = await getUserTasks(params.tenantId, {
      limit: 10,
    });

    return (
      <div className="max-w-7xl mx-auto space-y-8">
        {/* Header Section */}
        <div className="space-y-2">
          <h1 className="text-3xl font-bold tracking-tight text-foreground">
            My Tasks
          </h1>
          <p className="text-muted-foreground">
            Manage and track your assigned workflow tasks
          </p>
        </div>

        {/* Stats Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-card border border-border rounded-lg p-6 shadow-sm">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-primary/10 rounded-lg">
                <Clock className="h-5 w-5 text-primary" />
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">
                  Active Tasks
                </p>
                <p className="text-2xl font-bold text-foreground">
                  {userTasksResult.data?.userTasks.filter(
                    (t) => t.status === "ASSIGNED",
                  ).length || 0}
                </p>
              </div>
            </div>
          </div>
          <div className="bg-card border border-border rounded-lg p-6 shadow-sm">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-chart-2/10 rounded-lg">
                <CheckCircle2 className="h-5 w-5 text-chart-2" />
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">
                  Completed
                </p>
                <p className="text-2xl font-bold text-foreground">
                  {userTasksResult.data?.userTasks.filter(
                    (t) => t.status === "DONE",
                  ).length || 0}
                </p>
              </div>
            </div>
          </div>
          <div className="bg-card border border-border rounded-lg p-6 shadow-sm">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-chart-3/10 rounded-lg">
                <Users className="h-5 w-5 text-chart-3" />
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">
                  Total UserTasks
                </p>
                <p className="text-2xl font-bold text-foreground">
                  {userTasksResult.data?.userTasks.length || 0}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Tasks Section */}
        <div className="bg-card border border-border rounded-lg shadow-sm">
          <Tabs defaultValue="my-tasks" className="w-full">
            <div className="border-b border-border px-6 py-4">
              <TabsList className="bg-muted">
                <TabsTrigger
                  value="my-tasks"
                  className="data-[state=active]:bg-background"
                >
                  My Tasks
                </TabsTrigger>
              </TabsList>
            </div>
            <TabsContent value="my-tasks" className="p-6">
              <ListUserTasks
                userGroups={[]}
                initialData={
                  userTasksResult.data || { userTasks: [], bookmark: undefined }
                }
                initialError={userTasksResult.error}
              />
            </TabsContent>
          </Tabs>
        </div>
      </div>
    );
  }

  // Get claimable tasks for each user group
  const claimableTasksResults = await Promise.all(
    userGroupsResult.data.groups.map(async (group) => {
      return await getClaimableTasks(params.tenantId, {
        user_group_id: group.id,
        limit: 10,
      });
    }),
  );

  const userTasksResult = await getUserTasks(params.tenantId, {
    limit: 10,
  });

  const myTasks = userTasksResult.data?.userTasks || [];
  const claimableTasks = claimableTasksResults.flatMap(
    (result) => result.data?.userTasks || [],
  );

  return (
    <div className="max-w-7xl mx-auto space-y-8">
      {/* Header Section */}
      <div className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          UserTask Dashboard
        </h1>
        <p className="text-muted-foreground">
          Manage your assigned tasks and claim new ones from your groups
        </p>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <div className="bg-card border border-border rounded-lg p-6 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-primary/10 rounded-lg">
              <Clock className="h-5 w-5 text-primary" />
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">
                My Active
              </p>
              <p className="text-2xl font-bold text-foreground">
                {myTasks.filter((t) => t.status === "ASSIGNED").length}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-card border border-border rounded-lg p-6 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-chart-2/10 rounded-lg">
              <CheckCircle2 className="h-5 w-5 text-chart-2" />
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">
                Completed
              </p>
              <p className="text-2xl font-bold text-foreground">
                {myTasks.filter((t) => t.status === "DONE").length}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-card border border-border rounded-lg p-6 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-chart-3/10 rounded-lg">
              <Users className="h-5 w-5 text-chart-3" />
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">
                Available
              </p>
              <p className="text-2xl font-bold text-foreground">
                {claimableTasks.filter((t) => t.status === "UNASSIGNED").length}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-card border border-border rounded-lg p-6 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-chart-4/10 rounded-lg">
              <Users className="h-5 w-5 text-chart-4" />
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">
                Groups
              </p>
              <p className="text-2xl font-bold text-foreground">
                {userGroupsResult.data.groups.length}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Tasks Section */}
      <div className="bg-card border border-border rounded-lg shadow-sm">
        <Tabs defaultValue="my-tasks" className="w-full">
          <div className="border-b border-border px-6 py-4">
            <TabsList className="bg-muted">
              <TabsTrigger
                value="my-tasks"
                className="data-[state=active]:bg-background"
              >
                My Tasks
              </TabsTrigger>
              <TabsTrigger
                value="claimable-tasks"
                className="data-[state=active]:bg-background"
              >
                Available Tasks
              </TabsTrigger>
            </TabsList>
          </div>
          <TabsContent value="my-tasks" className="p-6">
            <ListUserTasks
              userGroups={userGroupsResult.data.groups}
              initialData={
                userTasksResult.data || { userTasks: [], bookmark: undefined }
              }
              initialError={userTasksResult.error}
            />
          </TabsContent>
          <TabsContent value="claimable-tasks" className="p-6">
            {userGroupsResult.data.groups.length > 0 && (
              <ListClaimableUserTasks
                userGroups={[
                  userGroupsResult.data.groups[0],
                  ...userGroupsResult.data.groups.slice(1),
                ]}
                initialData={claimableTasksResults.map(
                  (result) =>
                    result.data || { userTasks: [], bookmark: undefined },
                )}
                initialErrors={claimableTasksResults.map(
                  (result) => result.error,
                )}
              />
            )}
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
