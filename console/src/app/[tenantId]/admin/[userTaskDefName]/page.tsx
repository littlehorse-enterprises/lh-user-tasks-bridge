import {
  adminGetAllTasks,
  adminGetUserGroups,
} from "@/app/[tenantId]/actions/admin";
import { Button } from "@littlehorse-enterprises/ui/button";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import ListUserTasks from "../../components/user-task/list";

export default async function TaskPage({
  params,
}: {
  params: { tenantId: string; userTaskDefName: string };
}) {
  const adminListUserGroupsResponse = await adminGetUserGroups(params.tenantId);

  const adminListUserTasksResponse = await adminGetAllTasks(params.tenantId, {
    limit: 10,
    type: params.userTaskDefName,
  });

  return (
    <div>
      <Button variant="link" asChild>
        <Link href={`/${params.tenantId}/admin`}>
          <ArrowLeft className="size-4" />
          Back to UserTask Definitions
        </Link>
      </Button>
      <ListUserTasks
        userGroups={adminListUserGroupsResponse.data?.groups || []}
        userTaskDefName={params.userTaskDefName}
        initialData={
          adminListUserTasksResponse.data || {
            userTasks: [],
            bookmark: undefined,
          }
        }
        initialError={adminListUserTasksResponse.error}
      />
    </div>
  );
}
