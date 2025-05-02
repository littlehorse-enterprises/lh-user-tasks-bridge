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
import type { SimpleUserTaskRunDTO } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
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
  userTask: SimpleUserTaskRunDTO;
  admin?: boolean;
}) {
  const session = useSession();
  const user = session.data?.user;
  if (!user) return null;

  return (
    <Card
      className={
        "px-4 py-6 shadow-[0px_1px_8px_0px_rgba(0,0,0,0.15)] border-none bg-card hover:bg-accent/50 transition-[all_150ms_ease-out] flex flex-col gap-6"
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
              </>
            )
          }
        />
        <Metadata
          label="Assigned To (Group)"
          value={
            userTask.userGroup && (
              <>{userTask.userGroup.name ?? userTask.userGroup.id}</>
            )
          }
        />
        <div>
          <Label>Notes:</Label>
          <NotesTextArea notes={userTask.notes} />
        </div>
      </CardContent>
      <CardFooter className="flex gap-2 p-0">
        {userTask.status === "UNASSIGNED" && (
          <>
            {admin && <AssignUserTaskButton userTask={userTask} />}
            {!admin && <ClaimUserTaskButton userTask={userTask} />}
          </>
        )}
        {userTask.status === "ASSIGNED" && admin && (
          <AssignUserTaskButton userTask={userTask} />
        )}
        {userTask.status === "ASSIGNED" &&
          (admin || (userTask.user && userTask.user.id === user.id)) && (
            <CompleteUserTaskButton userTask={userTask} admin={admin} />
          )}
        {userTask.status === "DONE" && (
          <CompleteUserTaskButton userTask={userTask} admin={admin} readOnly />
        )}
        {["UNASSIGNED", "ASSIGNED"].includes(userTask.status) &&
          (admin || (userTask.user && userTask.user.id === user.id)) && (
            <CancelUserTaskButton userTask={userTask} admin={admin} />
          )}
      </CardFooter>
    </Card>
  );
}

function Metadata({
  label,
  value,
  iconValue,
}: {
  label: string;
  value: string | ReactNode | null | undefined;
  iconValue?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);

  // Check if the text is truncated
  const [isTruncated, setIsTruncated] = useState(false);
  useEffect(() => {
    if (!ref.current) return;
    setIsTruncated(
      ref.current.scrollWidth > ref.current.clientWidth &&
        typeof value === "string",
    );
  }, [value]);

  if (!value) return null;

  return (
    <div className="flex flex-col space-y-1">
      <div className="text-muted-foreground text-sm">{label}</div>
      {typeof value === "string" ? (
        <div className="flex items-center gap-2">
          {iconValue && (
            <span className="text-muted-foreground">{iconValue}</span>
          )}
          <TooltipProvider>
            <Tooltip delayDuration={0}>
              <TooltipTrigger asChild>
                <div
                  ref={ref}
                  className={`max-w-xs ${
                    isTruncated ? "truncate cursor-help" : ""
                  }`}
                >
                  {value}
                </div>
              </TooltipTrigger>
              {isTruncated && (
                <TooltipContent>{value as string}</TooltipContent>
              )}
            </Tooltip>
          </TooltipProvider>
        </div>
      ) : (
        value
      )}
    </div>
  );
}
