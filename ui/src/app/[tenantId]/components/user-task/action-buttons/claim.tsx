"use client";

import { claimUserTask } from "@/app/[tenantId]/actions/user";
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

export default function ClaimUserTaskButton({
  userTask,
}: {
  userTask: UserTask;
}) {
  const tenantId = useTenantId();

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
                const response = await claimUserTask(tenantId, userTask);

                if (response && "message" in response)
                  return toast.error(response.message);

                toast.success("UserTask claimed successfully");
              } catch (error) {
                toast.error("Failed to claim UserTask");
                console.error(error);
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
