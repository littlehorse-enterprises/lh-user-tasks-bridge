"use client";
import { adminCancelUserTask } from "@/app/[tenantId]/actions/admin";
import { cancelUserTask } from "@/app/[tenantId]/actions/user";
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
} from "@littlehorse-enterprises/ui/alert-dialog";
import { Button, buttonVariants } from "@littlehorse-enterprises/ui/button";
import { toast } from "@littlehorse-enterprises/ui/sonner";
import { SimpleUserTaskRunDTO } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useParams } from "next/navigation";
import { useState } from "react";

export default function CancelUserTaskButton({
  userTask,
  admin,
}: {
  userTask: SimpleUserTaskRunDTO;
  admin?: boolean;
}) {
  const tenantId = useParams().tenantId as string;
  const [isLoading, setIsLoading] = useState(false);

  const handleCancel = async () => {
    setIsLoading(true);

    const response = await (admin
      ? adminCancelUserTask(tenantId, {
          wf_run_id: userTask.wfRunId,
          user_task_guid: userTask.id,
        })
      : cancelUserTask(tenantId, {
          wf_run_id: userTask.wfRunId,
          user_task_guid: userTask.id,
        }));

    setIsLoading(false);

    if (response.error) {
      const errorMessage =
        response.error.type === ErrorType.FORBIDDEN
          ? "You don't have permission to cancel this task"
          : response.error.type === ErrorType.NOT_FOUND
            ? "Task not found or already cancelled"
            : `Failed to cancel task: ${response.error.message}`;

      toast.error(errorMessage);
      return;
    }

    toast.success("Task cancelled successfully");
  };

  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant="destructive" className="w-full">
          Cancel
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            Are you sure you want to permanently cancel this task?
          </AlertDialogTitle>
          <AlertDialogDescription>
            This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Close</AlertDialogCancel>

          <AlertDialogAction
            className={buttonVariants({ variant: "destructive" })}
            onClick={handleCancel}
            disabled={isLoading}
          >
            {isLoading ? "Cancelling..." : "Cancel Task"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
