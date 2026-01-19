"use client";
import {
  deleteUserTaskComment,
  editUserTaskComment,
} from "@/app/[tenantId]/actions/user";
import { cn } from "@/lib/utils";
import { Button } from "@littlehorse-enterprises/ui-library/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@littlehorse-enterprises/ui-library/dropdown-menu";
import { toast } from "@littlehorse-enterprises/ui-library/sonner";
import { Textarea } from "@littlehorse-enterprises/ui-library/textarea";
import {
  AuditEventDTO,
  SimpleUserTaskRunDTO,
  UserTaskCommentEvent,
  UserTaskEventType,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { MoreHorizontal } from "lucide-react";
import { useSession } from "next-auth/react";
import { useParams } from "next/navigation";
import { useState } from "react";

const DELIM = "::";
function parseCommentUserId(commentUserId: string) {
  const i = commentUserId.indexOf(DELIM);
  if (i === -1)
    return { sub: commentUserId, displayName: undefined as string | undefined };
  return {
    sub: commentUserId.slice(0, i),
    displayName: commentUserId.slice(i + DELIM.length) || undefined,
  };
}

export default function Comment({
  commentEvent,
  userTask,
  fetchComments,
  admin,
}: {
  commentEvent: AuditEventDTO;
  userTask: SimpleUserTaskRunDTO;
  fetchComments: () => Promise<void>;
  admin?: boolean;
}) {
  const tenantId = useParams().tenantId as string;
  const ev = commentEvent.event as UserTaskCommentEvent;
  const { data: session } = useSession();
  const userId = ev.userId || "";
  const { sub, displayName: packedName } = parseCommentUserId(userId);
  const isCurrentUser = (session?.user?.id || "") === sub;

  const displayName = isCurrentUser
    ? session?.user?.name || session?.user?.email || session?.user?.id || ""
    : packedName || sub;

  const [isEditing, setIsEditing] = useState(false);
  const [editedComment, setEditedComment] = useState(ev.comment);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleEdit = async () => {
    if (editedComment.trim() === "") return;

    setIsSubmitting(true);
    const result = await editUserTaskComment(tenantId, {
      wf_run_id: userTask.wfRunId,
      user_task_guid: userTask.id,
      comment: editedComment,
      comment_id: ev.commentId,
    });

    if (result.error) {
      toast.error(
        `Failed to edit comment: ${result.error.message || "Unknown error"}`,
      );
    } else {
      await fetchComments();
      setIsEditing(false);
    }
    setIsSubmitting(false);
  };

  const handleDelete = async () => {
    setIsSubmitting(true);
    const result = await deleteUserTaskComment(tenantId, {
      wf_run_id: userTask.wfRunId,
      user_task_guid: userTask.id,
      comment_id: ev.commentId,
    });

    if (result.error) {
      toast.error(
        `Failed to delete comment: ${result.error.message || "Unknown error"}`,
      );
    } else {
      await fetchComments();
    }
    setIsSubmitting(false);
  };

  return (
    <div
      className={cn(
        "group relative flex items-start gap-2.5",
        isCurrentUser && "justify-end",
      )}
    >
      <div
        className={cn(
          "flex w-fit max-w-[calc(100%-5rem)] flex-col rounded-lg px-3 py-2 text-sm",
          isCurrentUser
            ? "bg-blue-500 text-white"
            : "bg-gray-200 text-gray-800",
        )}
      >
        <div className="flex items-center justify-between">
          <p className="font-semibold">{displayName}</p>
          <span className="text-xs opacity-75 ml-2">
            {new Date(commentEvent.time).toLocaleString(undefined, {
              year: "numeric",
              month: "numeric",
              day: "numeric",
              hour: "numeric",
              minute: "2-digit",
              hour12: true,
            })}
          </span>
        </div>
        {isEditing ? (
          <div className="mt-2 flex flex-col items-end gap-2">
            <Textarea
              value={editedComment}
              onChange={(e) => setEditedComment(e.target.value)}
              className="min-h-[60px] w-full resize-y bg-white text-black"
              disabled={isSubmitting}
            />
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setIsEditing(false);
                  setEditedComment(ev.comment);
                }}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button
                size="sm"
                onClick={handleEdit}
                disabled={isSubmitting}
                className="bg-blue-500 hover:bg-blue-600 text-white"
              >
                {isSubmitting ? "Saving..." : "Edit Comment"}
              </Button>
            </div>
          </div>
        ) : (
          <>
            <p className="break-words mt-1">{ev.comment}</p>
            {commentEvent.type === UserTaskEventType.COMMENT_EDITED && (
              <span className="text-xs opacity-75 mt-1 self-end">(edited)</span>
            )}
          </>
        )}
      </div>

      {(isCurrentUser || admin) && !isEditing && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 opacity-0 transition-opacity group-hover:opacity-100"
            >
              <MoreHorizontal className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align={isCurrentUser ? "end" : "start"}>
            {isCurrentUser && (
              <DropdownMenuItem onClick={() => setIsEditing(true)}>
                Edit
              </DropdownMenuItem>
            )}
            <DropdownMenuItem onClick={handleDelete} className="text-red-500">
              Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </div>
  );
}
