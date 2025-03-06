import { listUserGroups, listUserTasks } from "./actions/user";
import ClaimableUserTasks from "./components/user-task/claimable";
import ListUserTasks from "./components/user-task/list";

export default async function Home({
  params,
}: {
  params: { tenantId: string };
}) {
  const listUserGroupsResponse = await listUserGroups(params.tenantId);

  if ("message" in listUserGroupsResponse)
    throw new Error(listUserGroupsResponse.message);

  const listUserTasksResponse = await listUserTasks(params.tenantId, {
    limit: 10,
  });

  if ("message" in listUserTasksResponse)
    throw new Error(listUserTasksResponse.message);

  return (
    <div className="flex gap-4">
      <ListUserTasks
        userGroups={listUserGroupsResponse.groups}
        initialData={listUserTasksResponse}
      />
      {listUserGroupsResponse.groups.length > 0 && (
        <ClaimableUserTasks
          userGroups={[
            listUserGroupsResponse.groups[0],
            ...listUserGroupsResponse.groups.slice(1),
          ]}
        />
      )}
    </div>
  );
}
