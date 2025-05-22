"use client";

import { adminClaimUserTask } from "@/app/[tenantId]/actions/admin";
import { claimUserTask } from "@/app/[tenantId]/actions/user";
import { ErrorType } from "@/lib/error-handling";
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
} from "@littlehorse-enterprises/ui-library/alert-dialog";
import {
  Button,
  buttonVariants,
} from "@littlehorse-enterprises/ui-library/button";
import { toast } from "@littlehorse-enterprises/ui-library/sonner";
import { SimpleUserTaskRunDTO } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useParams } from "next/navigation";
import { useState } from "react";

export default function ClaimUserTaskButton({
  userTask,
  admin,
}: {
  userTask: SimpleUserTaskRunDTO;
  admin?: boolean;
}) {
  const tenantId = useParams().tenantId as string;
  const [isLoading, setIsLoading] = useState(false);

  const handleClaim = async () => {
    setIsLoading(true);

    const response = await (admin
      ? adminClaimUserTask(tenantId, {
          wf_run_id: userTask.wfRunId,
          user_task_guid: userTask.id,
        })
      : claimUserTask(tenantId, {
          wf_run_id: userTask.wfRunId,
          user_task_guid: userTask.id,
        }));

    setIsLoading(false);

    if (response.error) {
      const errorMessage =
        response.error.type === ErrorType.FORBIDDEN
          ? "You don't have permission to claim this task"
          : response.error.type === ErrorType.NOT_FOUND
            ? "Task not found or already claimed"
            : `Failed to claim task: ${response.error.message}`;

      toast.error(errorMessage);
      return;
    }

    toast.success("Task claimed successfully");
  };

  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant="outline" className="w-full">
          Claim
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            Are you sure you want to claim this task?
          </AlertDialogTitle>
          <AlertDialogDescription>
            This task will be assigned to you.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Close</AlertDialogCancel>

          <AlertDialogAction
            className={buttonVariants({ variant: "default" })}
            onClick={handleClaim}
            disabled={isLoading}
          >
            {isLoading ? "Claiming..." : "Claim Task"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
