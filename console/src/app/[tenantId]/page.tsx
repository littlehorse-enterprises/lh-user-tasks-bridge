import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
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
  const listUserGroupsResponse = await getUserGroupsFromIdentityProvider(
    params.tenantId,
  );

  const listClaimableUserTasksResponse = await Promise.all(
    listUserGroupsResponse.groups.map(async (group) => {
      const response = await getClaimableTasks(params.tenantId, {
        user_group_id: group.id,
        limit: 10,
      });
      return response;
    }),
  );

  const listUserTasksResponse = await getUserTasks(params.tenantId, {
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
            userGroups={listUserGroupsResponse.groups}
        initialData={listUserTasksResponse}
          />
        </TabsContent>
        <TabsContent value="claimable-tasks">
          {listUserGroupsResponse.groups.length > 0 && (
            <ListClaimableUserTasks
              userGroups={[
                listUserGroupsResponse.groups[0],
            ...listUserGroupsResponse.groups.slice(1),
          ]}
            initialData={listClaimableUserTasksResponse}
            />
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}
