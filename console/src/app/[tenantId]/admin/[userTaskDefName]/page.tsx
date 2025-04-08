import {
  adminListUserGroups,
  adminListUserTasks,
} from "@/app/[tenantId]/actions/admin";
import { Button } from "@/components/ui/button";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import ListUserTasks from "../../components/user-task/list";

export default async function TaskPage({
  params,
}: {
  params: { tenantId: string; userTaskDefName: string };
}) {
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
      <Button variant="link" asChild>
        <Link href={`/${params.tenantId}/admin`}>
          <ArrowLeft className="size-4" />
          Back to UserTask Definitions
        </Link>
      </Button>
      <ListUserTasks
        userGroups={adminListUserGroupsResponse.groups}
        userTaskDefName={params.userTaskDefName}
        initialData={adminListUserTasksResponse}
      />
    </div>
  );
}
