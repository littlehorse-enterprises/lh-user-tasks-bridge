import { adminListUserGroups, adminListUserTasks } from "@/app/actions/admin";
import ListUserTasks from "@/app/components/user-task/list";
import { ArrowLeftIcon } from "@radix-ui/react-icons";
import Link from "next/link";

export default async function TaskPage({
  params,
}: {
  params: { userTaskDefName: string };
}) {
  const adminListUserGroupsResponse = await adminListUserGroups();
  if ("message" in adminListUserGroupsResponse)
    throw new Error(adminListUserGroupsResponse.message);

  const adminListUserTasksResponse = await adminListUserTasks({
    limit: 10,
    type: params.userTaskDefName,
  });
  if ("message" in adminListUserTasksResponse)
    throw new Error(adminListUserTasksResponse.message);

  return (
    <div>
      <Link
        href="/admin"
        className="mb-2 text-sm text-blue-500 flex items-center gap-2"
      >
        <ArrowLeftIcon className="size-4" />
        Back to UserTask Definitions
      </Link>
      <ListUserTasks
        userGroups={adminListUserGroupsResponse.groups}
        userTaskDefName={params.userTaskDefName}
        initialData={adminListUserTasksResponse}
      />
    </div>
  );
}
