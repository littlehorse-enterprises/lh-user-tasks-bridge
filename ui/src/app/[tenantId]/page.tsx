import { listUserGroups, listUserTasks } from "./actions/user";
import ListUserTasks from "./components/user-task/list";

export default async function Home(props: {
  params: Promise<{ tenantId: string }>;
}) {
  const params = await props.params;
  const listUserGroupsResponse = await listUserGroups(params.tenantId);

  if ("message" in listUserGroupsResponse)
    throw new Error(listUserGroupsResponse.message);

  const listUserTasksResponse = await listUserTasks(params.tenantId, {
    limit: 10,
  });

  if ("message" in listUserTasksResponse)
    throw new Error(listUserTasksResponse.message);

  return (
    <ListUserTasks
      userGroups={listUserGroupsResponse.groups}
      initialData={listUserTasksResponse}
    />
  );
}
