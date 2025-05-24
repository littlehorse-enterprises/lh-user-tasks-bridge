"use client";
import { Badge } from "@littlehorse-enterprises/ui-library/badge";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@littlehorse-enterprises/ui-library/card";
import { Label } from "@littlehorse-enterprises/ui-library/label";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@littlehorse-enterprises/ui-library/tooltip";
import type { SimpleUserTaskRunDTO } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import {
  AlertCircle,
  Calendar,
  Check,
  CheckCircle2,
  ClipboardCopy,
  ClipboardList,
  Clock,
  User,
  Users,
  Workflow,
  XCircle,
} from "lucide-react";
import { useSession } from "next-auth/react";
import { ReactNode, useEffect, useRef, useState } from "react";
import AssignUserTaskButton from "./action-buttons/assign";
import CancelUserTaskButton from "./action-buttons/cancel";
import ClaimUserTaskButton from "./action-buttons/claim";
import CompleteUserTaskButton from "./action-buttons/complete";
import NotesTextArea from "./notes";

const getStatusConfig = (status: string) => {
  switch (status) {
    case "ASSIGNED":
      return {
        color: "bg-primary text-primary-foreground",
        icon: Clock,
        label: "In Progress",
      };
    case "DONE":
      return {
        color: "bg-green-500 text-white",
        icon: CheckCircle2,
        label: "Completed",
      };
    case "CANCELLED":
      return {
        color: "bg-destructive text-destructive-foreground",
        icon: XCircle,
        label: "Cancelled",
      };
    case "UNASSIGNED":
      return {
        color: "bg-muted text-muted-foreground",
        icon: AlertCircle,
        label: "Available",
      };
    default:
      return {
        color: "bg-muted text-muted-foreground",
        icon: AlertCircle,
        label: status,
      };
  }
};

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

  const statusConfig = getStatusConfig(userTask.status);
  const StatusIcon = statusConfig.icon;

  return (
    <Card className="group relative overflow-hidden bg-card border border-border/50 hover:border-border hover:shadow-lg transition-all duration-300 ease-out">
      {/* Status indicator bar */}
      <div
        className={`absolute top-0 left-0 right-0 h-1 ${statusConfig.color.split(" ")[0]}`}
      />

      <CardHeader className="pb-4">
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 min-w-0">
            <CardTitle className="text-lg font-semibold text-foreground group-hover:text-primary transition-colors line-clamp-2">
              {userTask.userTaskDefName}
            </CardTitle>
            <div className="flex items-center gap-2 mt-2">
              <Badge
                variant="outline"
                className={`${statusConfig.color} border-0 font-medium`}
              >
                <StatusIcon className="h-3 w-3 mr-1" />
                {statusConfig.label}
              </Badge>
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <div className="flex items-center gap-1 text-xs text-muted-foreground cursor-help">
                      <Calendar className="h-3 w-3" />
                      {new Date(userTask.scheduledTime).toLocaleDateString()}
                    </div>
                  </TooltipTrigger>
                  <TooltipContent>
                    <p className="font-medium">Scheduled Time</p>
                    <p className="text-sm">
                      {new Date(userTask.scheduledTime).toLocaleString(
                        undefined,
                        {
                          weekday: "long",
                          year: "numeric",
                          month: "long",
                          day: "numeric",
                          hour: "2-digit",
                          minute: "2-digit",
                          second: "2-digit",
                          timeZoneName: "short",
                        },
                      )}
                    </p>
                  </TooltipContent>
                </Tooltip>
              </TooltipProvider>
            </div>
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-4 pb-4">
        {/* Key Information Grid */}
        <div className="grid grid-cols-1 gap-3">
          <InfoItem
            icon={ClipboardList}
            label="UserTask ID"
            value={userTask.id}
            copyable
          />
          <InfoItem
            icon={Workflow}
            label="Workflow Run"
            value={userTask.wfRunId}
            copyable
          />

          <InfoItem
            icon={User}
            label="Assigned User"
            value={
              userTask.user ? (
                userTask.user.valid ? (
                  <div className="flex flex-col">
                    <span className="font-medium">
                      {userTask.user.firstName} {userTask.user.lastName}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {userTask.user.email}
                    </span>
                  </div>
                ) : (
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm">
                      {userTask.user.id}
                    </span>
                    <Badge variant="destructive" className="text-xs">
                      Invalid
                    </Badge>
                  </div>
                )
              ) : (
                <span className="text-muted-foreground italic">Unassigned</span>
              )
            }
          />

          <InfoItem
            icon={Users}
            label="Group"
            value={
              userTask.userGroup ? (
                userTask.userGroup.valid ? (
                  <span className="font-medium">
                    {userTask.userGroup.name ?? userTask.userGroup.id}
                  </span>
                ) : (
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm">
                      {userTask.userGroup.id}
                    </span>
                    <Badge variant="destructive" className="text-xs">
                      Invalid
                    </Badge>
                  </div>
                )
              ) : (
                <span className="text-muted-foreground italic">No group</span>
              )
            }
          />
        </div>

        {/* Notes Section */}
        {userTask.notes && (
          <div className="space-y-2">
            <Label className="text-sm font-medium text-foreground">Notes</Label>
            <div className="bg-muted/50 rounded-md p-3">
              <NotesTextArea notes={userTask.notes} />
            </div>
          </div>
        )}
      </CardContent>

      <CardFooter className="pt-4 border-t border-border/50 bg-muted/20">
        {/* Action Buttons */}
        {claimable && userTask.status === "UNASSIGNED" && (
          <div className="w-full">
            <ClaimUserTaskButton userTask={userTask} />
          </div>
        )}

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

function InfoItem({
  icon: Icon,
  label,
  value,
  copyable = false,
  compact = false,
}: {
  icon?: React.ComponentType<{ className?: string }>;
  label: string;
  value: string | ReactNode | null | undefined;
  copyable?: boolean;
  compact?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
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
    <div className={`flex ${compact ? "items-center" : "items-start"} gap-3`}>
      {Icon && (
        <div className={`${compact ? "mt-0" : "mt-0.5"} text-muted-foreground`}>
          <Icon className="h-4 w-4" />
        </div>
      )}
      <div className="flex-1 min-w-0 space-y-1">
        <div className="flex items-center gap-2">
          <Label
            className={`${compact ? "text-xs" : "text-sm"} font-medium text-muted-foreground`}
          >
            {label}
          </Label>
          {copyable && typeof value === "string" && (
            <button
              onClick={handleCopy}
              className="text-muted-foreground hover:text-foreground transition-colors"
              aria-label={copied ? "Copied" : "Copy to clipboard"}
            >
              {copied ? (
                <Check className="h-3 w-3" />
              ) : (
                <ClipboardCopy className="h-3 w-3" />
              )}
            </button>
          )}
        </div>
        {typeof value === "string" ? (
          <TooltipProvider>
            <Tooltip delayDuration={0}>
              <TooltipTrigger asChild>
                <div
                  ref={ref}
                  className={`${compact ? "text-sm" : "text-sm"} text-foreground font-medium ${
                    isTruncated ? "truncate cursor-help" : ""
                  }`}
                >
                  {value}
                </div>
              </TooltipTrigger>
              {isTruncated && <TooltipContent>{value}</TooltipContent>}
            </Tooltip>
          </TooltipProvider>
        ) : (
          <div className={`${compact ? "text-sm" : "text-sm"}`}>{value}</div>
        )}
      </div>
    </div>
  );
}
