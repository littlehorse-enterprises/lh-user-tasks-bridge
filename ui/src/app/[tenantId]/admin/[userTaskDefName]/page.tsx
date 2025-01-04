import {
  adminListUserGroups,
  adminListUserTasks,
} from "@/app/[tenantId]/actions/admin";
import { ArrowLeftIcon } from "@radix-ui/react-icons";
import Link from "next/link";
import ListUserTasks from "../../components/user-task/list";

export default async function TaskPage(props: {
  params: Promise<{ tenantId: string; userTaskDefName: string }>;
}) {
  const params = await props.params;
  const adminListUserGroupsResponse = await adminListUserGroups(
    params.tenantId,
  );
  if ("message" in adminListUserGroupsResponse)
    throw new Error(adminListUserGroupsResponse.message);

  const adminListUserTasksResponse = await adminListUserTasks(params.tenantId, {
    limit: 10,
    type: params.userTaskDefName,
  });
  if ("message" in adminListUserTasksResponse)
    throw new Error(adminListUserTasksResponse.message);

  return (
    <div>
      <Link
        href={`/${params.tenantId}/admin`}
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
