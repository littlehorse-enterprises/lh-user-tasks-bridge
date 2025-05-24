import {
  adminGetAllTasks,
  adminGetUserGroups,
} from "@/app/[tenantId]/actions/admin";
import { Button } from "@littlehorse-enterprises/ui-library/button";
import { ArrowLeft, ChevronRight } from "lucide-react";
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
    <div className="space-y-4">
      {/* Professional Navigation Header */}
      <div className="border-b border-border/40 pb-4">
        <div className="flex items-center gap-2 mb-3">
          <Button
            variant="ghost"
            size="sm"
            asChild
            className="text-muted-foreground hover:text-foreground h-8 px-2 gap-1"
          >
            <Link href={`/${params.tenantId}/admin`}>
              <ArrowLeft className="h-4 w-4" />
              Back
            </Link>
          </Button>
        </div>

        {/* Breadcrumb Navigation */}
        <nav className="flex items-center text-sm text-muted-foreground">
          <Link
            href={`/${params.tenantId}/admin`}
            className="hover:text-foreground transition-colors"
          >
            UserTaskDefs
          </Link>
          <ChevronRight className="h-4 w-4 mx-2" />
          <span className="text-foreground font-medium">
            {params.userTaskDefName}
          </span>
        </nav>
      </div>

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
