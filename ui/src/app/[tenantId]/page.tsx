import { listUserGroups, listUserTasks } from "./actions/user";
import ListUserTasks from "./components/user-task/list";

export default async function Home({
  params,
}: {
  params: { tenantId: string };
}) {
  const [listUserGroupsResponse, listUserTasksResponse] = await Promise.all([
    listUserGroups(params.tenantId),
    listUserTasks(params.tenantId, { limit: 10 }),
  ]);

  if ("message" in listUserGroupsResponse)
    throw new Error(listUserGroupsResponse.message);

  if ("message" in listUserTasksResponse)
    throw new Error(listUserTasksResponse.message);

  return (
    <ListUserTasks
      userGroups={listUserGroupsResponse.groups}
      initialData={listUserTasksResponse}
    />
  );
}
