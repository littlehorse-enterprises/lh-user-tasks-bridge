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
import { Check, ClipboardCopy } from "lucide-react";
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
  claimable,
}: {
  userTask: SimpleUserTaskRunDTO;
  admin?: boolean;
  claimable?: boolean;
}) {
  const session = useSession();
  const user = session.data?.user;
  if (!user) return null;

  return (
    <Card
      className={
        "px-4 py-6 shadow-[0px_1px_8px_0px_rgba(0,0,0,0.15)] bg-card hover:bg-accent/50 transition-[all_150ms_ease-out] flex flex-col h-full border border-neutral-200 dark:border-neutral-700"
      }
    >
      <CardHeader className="p-0">
        <CardTitle>{userTask.userTaskDefName}</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-2 p-0 flex-grow">
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
            userTask.user ? (
              userTask.user.valid ? (
                <>
                  {userTask.user.firstName} {userTask.user.lastName}{" "}
                  <span className="font-medium">{userTask.user.email}</span>
                </>
              ) : (
                <>
                  {userTask.user.id}{" "}
                  <span className="text-red-500">(NOT VALID USER)</span>
                </>
              )
            ) : (
              "N/A"
            )
          }
        />
        <Metadata
          label="Assigned To (Group)"
          value={
            userTask.userGroup ? (
              userTask.userGroup.valid ? (
                <>{userTask.userGroup.name ?? userTask.userGroup.id}</>
              ) : (
                <>
                  {userTask.userGroup.id}{" "}
                  <span className="text-red-500">(NOT VALID GROUP)</span>
                </>
              )
            ) : (
              "N/A"
            )
          }
        />
        <div className="mb-6">
          <Label>Notes:</Label>
          <NotesTextArea notes={userTask.notes} />
        </div>
      </CardContent>
      <CardFooter className="flex gap-2 p-0">
        {/* If the task is claimable, only show claim button */}
        {claimable && userTask.status === "UNASSIGNED" && (
          <div className="w-full">
            <ClaimUserTaskButton userTask={userTask} />
          </div>
        )}

        {/* Only show these buttons if not in claimable mode */}
        {!claimable && (
          <div className="flex gap-2 w-full">
            {userTask.status === "UNASSIGNED" && (
              <>
                {admin && (
                  <div className="flex-1">
                    <AssignUserTaskButton userTask={userTask} />
                  </div>
                )}
                {!admin && (
                  <div className="flex-1">
                    <ClaimUserTaskButton userTask={userTask} />
                  </div>
                )}
              </>
            )}
            {userTask.status === "ASSIGNED" && admin && (
              <div className="flex-1">
                <AssignUserTaskButton userTask={userTask} />
              </div>
            )}
            {userTask.status === "ASSIGNED" &&
              (admin || (userTask.user && userTask.user.id === user.id)) && (
                <div className="flex-1">
                  <CompleteUserTaskButton userTask={userTask} admin={admin} />
                </div>
              )}
            {userTask.status === "DONE" && (
              <div className="flex-1">
                <CompleteUserTaskButton
                  userTask={userTask}
                  admin={admin}
                  readOnly
                />
              </div>
            )}
            {["UNASSIGNED", "ASSIGNED"].includes(userTask.status) &&
              (admin || (userTask.user && userTask.user.id === user.id)) && (
                <div className="flex-1">
                  <CancelUserTaskButton userTask={userTask} admin={admin} />
                </div>
              )}
          </div>
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
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!ref.current) return;
    setIsTruncated(
      ref.current.scrollWidth > ref.current.clientWidth &&
        typeof value === "string",
    );
  }, [value]);

  useEffect(() => {
    if (copied) {
      const timer = setTimeout(() => setCopied(false), 2000);
      return () => clearTimeout(timer);
    }
  }, [copied]);

  const handleCopy = () => {
    if (typeof value === "string") {
      navigator.clipboard.writeText(value);
      setCopied(true);
    }
  };

  if (!value) return null;

  return (
    <div className="flex flex-col space-y-1">
      <div className="text-muted-foreground text-sm flex items-center gap-1">
        {typeof value === "string" && (
          <button
            onClick={handleCopy}
            className="h-4 w-4 text-muted-foreground hover:text-foreground"
            aria-label={copied ? "Copied" : "Copy to clipboard"}
          >
            {copied ? <Check size={14} /> : <ClipboardCopy size={14} />}
          </button>
        )}
        {label}
      </div>
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
              {isTruncated && <TooltipContent>{value}</TooltipContent>}
            </Tooltip>
          </TooltipProvider>
        </div>
      ) : (
        value
      )}
    </div>
  );
}
