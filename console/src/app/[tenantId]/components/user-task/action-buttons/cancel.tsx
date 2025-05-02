"use client";
import { adminCancelUserTask } from "@/app/[tenantId]/actions/admin";
import { cancelUserTask } from "@/app/[tenantId]/actions/user";
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

export default function CancelUserTaskButton({
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
        <Button variant="destructive">Cancel</Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            Are you sure you want to permanently cancel this UserTask?
          </AlertDialogTitle>
          <AlertDialogDescription>
            This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Close</AlertDialogCancel>

          <AlertDialogAction
            className={buttonVariants({ variant: "destructive" })}
            onClick={async () => {
              try {
                admin
                  ? await adminCancelUserTask(tenantId, {
                      wf_run_id: userTask.wfRunId,
                      user_task_guid: userTask.id,
                    })
                  : await cancelUserTask(tenantId, {
                      wf_run_id: userTask.wfRunId,
                      user_task_guid: userTask.id,
                    });
                toast.success("UserTask cancelled successfully");
              } catch (error) {
                console.error(error);
                toast.error("Failed to cancel UserTask");
              }
            }}
          >
            Cancel UserTask
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
