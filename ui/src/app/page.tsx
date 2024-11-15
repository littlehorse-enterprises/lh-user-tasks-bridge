import { listUserGroups, listUserTasks } from "./actions/user";
import ListUserTasks from "./components/user-task/list";

export default async function Home() {
  const [listUserGroupsResponse, listUserTasksResponse] = await Promise.all([
    listUserGroups(),
    listUserTasks({ limit: 10 }),
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
