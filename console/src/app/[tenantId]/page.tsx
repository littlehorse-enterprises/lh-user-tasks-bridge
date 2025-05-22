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
import { AlertCircle } from "lucide-react";
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
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Error loading user groups</AlertTitle>
        <AlertDescription>{userGroupsResult.error.message}</AlertDescription>
      </Alert>
    );
  }

  // Ensure data exists to avoid TypeScript errors
  if (!userGroupsResult.data) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Error loading user groups</AlertTitle>
        <AlertDescription>Could not retrieve user groups data</AlertDescription>
      </Alert>
    );
  }

  // If no groups are available, show user tasks only
  if (userGroupsResult.data.groups.length === 0) {
    const userTasksResult = await getUserTasks(params.tenantId, {
      limit: 10,
    });

    return (
      <div className="flex gap-4 sm:flex-row flex-col [&>*]:w-full">
        <Tabs defaultValue="my-tasks">
          <TabsList>
            <TabsTrigger value="my-tasks">My Tasks</TabsTrigger>
          </TabsList>
          <TabsContent value="my-tasks">
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

  return (
    <div className="flex gap-4 sm:flex-row flex-col [&>*]:w-full">
      <Tabs defaultValue="my-tasks">
        <TabsList>
          <TabsTrigger value="my-tasks">My Tasks</TabsTrigger>
          <TabsTrigger value="claimable-tasks">Claimable Tasks</TabsTrigger>
        </TabsList>
        <TabsContent value="my-tasks">
          <ListUserTasks
            userGroups={userGroupsResult.data.groups}
            initialData={
              userTasksResult.data || { userTasks: [], bookmark: undefined }
            }
            initialError={userTasksResult.error}
          />
        </TabsContent>
        <TabsContent value="claimable-tasks">
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
  );
}
