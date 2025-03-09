import {
  listClaimableUserTasks,
  listUserGroups,
  listUserTasks,
} from "./actions/user";
import ListUserTasks from "./components/user-task/list";
import ListClaimableUserTasks from "./components/user-task/list-claimable";

export default async function Home({
  params,
}: {
  params: { tenantId: string };
}) {
  const listUserGroupsResponse = await listUserGroups(params.tenantId);

  if ("message" in listUserGroupsResponse)
    throw new Error(listUserGroupsResponse.message);

  const listClaimableUserTasksResponse = await Promise.all(
    listUserGroupsResponse.groups.map(async (group) => {
      const response = await listClaimableUserTasks(params.tenantId, {
        user_group_id: group.id,
        limit: 10,
      });
      if ("message" in response) throw new Error(response.message);
      return response;
    }),
  );

  const listUserTasksResponse = await listUserTasks(params.tenantId, {
    limit: 10,
  });

  if ("message" in listUserTasksResponse)
    throw new Error(listUserTasksResponse.message);

  return (
    <div className="flex gap-4 sm:flex-row flex-col [&>*]:w-full">
      <ListUserTasks
        userGroups={listUserGroupsResponse.groups}
        initialData={listUserTasksResponse}
      />
      {listUserGroupsResponse.groups.length > 0 && (
        <ListClaimableUserTasks
          userGroups={[
            listUserGroupsResponse.groups[0],
            ...listUserGroupsResponse.groups.slice(1),
          ]}
          initialData={listClaimableUserTasksResponse}
        />
      )}
    </div>
  );
}
