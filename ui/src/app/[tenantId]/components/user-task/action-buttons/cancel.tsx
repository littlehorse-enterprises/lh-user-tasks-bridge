"use client";
import { adminCancelUserTask } from "@/app/[tenantId]/actions/admin";
import { cancelUserTask } from "@/app/[tenantId]/actions/user";
import { useTenantId } from "@/app/[tenantId]/layout";
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
import { UserTask } from "@littlehorse-enterprises/sso-workflow-bridge-api-client";
import { toast } from "sonner";

export default function CancelUserTaskButton({
  userTask,
  admin,
}: {
  userTask: UserTask;
  admin?: boolean;
}) {
  const tenantId = useTenantId();

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
                const response = admin
                  ? await adminCancelUserTask(tenantId, userTask)
                  : await cancelUserTask(tenantId, userTask);

                if (response && "message" in response)
                  return toast.error(response.message);

                toast.success("UserTask cancelled successfully");
              } catch (error) {
                toast.error("Failed to cancel userTask");
                console.error(error);
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
