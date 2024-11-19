"use client";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import type { UserTask } from "@littlehorse-enterprises/user-tasks-api-client";
import { useSession } from "next-auth/react";
import { ReactNode, useEffect, useRef, useState } from "react";
import AssignUserTaskButton from "./action-buttons/assign";
import CancelUserTaskButton from "./action-buttons/cancel";
import ClaimUserTaskButton from "./action-buttons/claim";
import CompleteUserTaskButton from "./action-buttons/complete";
import NotesTextArea from "./notes";

export default function UserTask({
  userTask,
  admin,
}: {
  userTask: UserTask;
  admin?: boolean;
}) {
  const session = useSession();
  const user = session.data?.user;
  if (!user) return null;

  return (
    <Card
      className={
        "px-4 py-6 shadow-[0px_1px_8px_0px_rgba(0,0,0,0.15)] border-none bg-white hover:bg-gray-50 transition-[all_150ms_ease-out] flex flex-col gap-6"
      }
    >
      <CardHeader className="p-0">
        <CardTitle>{userTask.userTaskDefName}</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-2 p-0">
        <Metadata label="Workflow Run ID" value={userTask.wfRunId} />
        <Metadata label="UserTask Run ID" value={userTask.id} />
        <Metadata label="Status" value={userTask.status} />
        <Metadata
          label="Scheduled Time"
          value={new Date(userTask.scheduledTime).toLocaleString()}
        />
        <Metadata
          label="Assigned To (User)"
          value={
            userTask.user && (
              <>
                {userTask.user.firstName} {userTask.user.lastName}{" "}
                <span className="font-medium">{userTask.user.email}</span>
                {userTask.user.valid === false && (
                  <>
                    {userTask.user.id}{" "}
                    <span className="text-destructive">INVALID USER</span>
                  </>
                )}
              </>
            )
          }
        />
        <Metadata
          label="Assigned To (Group)"
          value={
            userTask.userGroup && (
              <>
                {userTask.userGroup.name ?? userTask.userGroup.id}{" "}
                <span className="text-destructive">
                  {userTask.userGroup.valid === false && "INVALID USER GROUP"}
                </span>
              </>
            )
          }
        />
        <div>
          <Label>Notes:</Label>
          <NotesTextArea notes={userTask.notes} />
        </div>
      </CardContent>
      <CardFooter className="w-full flex items-center justify-end gap-2 flex-wrap p-0">
        {userTask.status !== "CANCELLED" && userTask.status !== "DONE" && (
          <>
            {admin && (
              <>
                <AssignUserTaskButton userTask={userTask} />
              </>
            )}
            <CancelUserTaskButton userTask={userTask} admin={admin} />
            {user.id !== userTask.user?.id && (
              <ClaimUserTaskButton userTask={userTask} />
            )}
            <CompleteUserTaskButton userTask={userTask} admin={admin} />
          </>
        )}
        {userTask.status === "DONE" && (
          <CompleteUserTaskButton userTask={userTask} admin={admin} readOnly />
        )}
      </CardFooter>
    </Card>
  );
}

function Metadata({ label, value }: { label: string; value?: ReactNode }) {
  const textRef = useRef<HTMLParagraphElement>(null);
  const [isTruncated, setIsTruncated] = useState(false);

  useEffect(() => {
    const checkTruncation = () => {
      if (textRef.current) {
        const { offsetWidth, scrollWidth } = textRef.current;
        setIsTruncated(scrollWidth > offsetWidth);
      }
    };

    checkTruncation();
    window.addEventListener("resize", checkTruncation);
    return () => window.removeEventListener("resize", checkTruncation);
  }, [value]);

  const content = (
    <div className="overflow-hidden">
      <p
        ref={textRef}
        className="text-sm text-muted-foreground text-right truncate"
      >
        {value ?? "N/A"}
      </p>
    </div>
  );

  return (
    <div className="grid grid-cols-[auto_minmax(0,1fr)] gap-4 pb-2 border-b items-center">
      <p className="text-sm font-medium leading-none capitalize whitespace-nowrap">
        {label}:
      </p>
      {isTruncated ? (
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>{content}</TooltipTrigger>
            <TooltipContent>
              <p className="max-w-xs break-all">{value ?? "N/A"}</p>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
      ) : (
        content
      )}
    </div>
  );
}
