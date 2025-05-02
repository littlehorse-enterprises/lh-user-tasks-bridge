"use client";

import { adminClaimUserTask } from "@/app/[tenantId]/actions/admin";
import { claimUserTask } from "@/app/[tenantId]/actions/user";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button, buttonVariants } from "@/components/ui/button";
import { SimpleUserTaskRunDTO } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useParams } from "next/navigation";
import { toast } from "sonner";

export default function ClaimUserTaskButton({
  userTask,
  admin,
}: {
  userTask: SimpleUserTaskRunDTO;
  admin?: boolean;
}) {
  const tenantId = useParams().tenantId as string;

  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant="outline">Claim</Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            Are you sure you want to claim this UserTask?
          </AlertDialogTitle>
          <AlertDialogDescription>
            This UserTask will be assigned to you.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Close</AlertDialogCancel>

          <AlertDialogAction
            className={buttonVariants({ variant: "default" })}
            onClick={async () => {
              try {
                admin
                  ? await adminClaimUserTask(tenantId, {
                      wf_run_id: userTask.wfRunId,
                      user_task_guid: userTask.id,
                    })
                  : await claimUserTask(tenantId, {
                      wf_run_id: userTask.wfRunId,
                      user_task_guid: userTask.id,
                    });
                toast.success("UserTask claimed successfully");
              } catch (error) {
                console.error(error);
                toast.error("Failed to claim UserTask");
              }
            }}
          >
            Claim UserTask
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
