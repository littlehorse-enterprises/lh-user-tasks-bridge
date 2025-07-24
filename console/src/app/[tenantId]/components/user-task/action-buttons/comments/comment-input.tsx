"use client";
import {
  getUserTaskComments,
  postUserTaskComment,
} from "@/app/[tenantId]/actions/user";
import { ErrorResponse } from "@/lib/error-handling";
import { Badge } from "@littlehorse-enterprises/ui-library/badge";
import { Button } from "@littlehorse-enterprises/ui-library/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@littlehorse-enterprises/ui-library/dialog";
import { toast } from "@littlehorse-enterprises/ui-library/sonner";
import { Textarea } from "@littlehorse-enterprises/ui-library/textarea";
import {
  AuditEventDTO,
  SimpleUserTaskRunDTO,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useParams } from "next/navigation";
import { useState } from "react";
import { ErrorHandler } from "../../../error-handler";
import Loading from "../../../loading";
import CommentList from "./comment-section";

export default function UserTaskComments({
  userTask,
  admin,
}: {
  userTask: SimpleUserTaskRunDTO;
  admin?: boolean;
}) {
  const [inputText, setInputText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [commentEventList, setCommentEventList] = useState<AuditEventDTO[]>([]);
  const [error, setError] = useState<ErrorResponse>();
  const tenantId = useParams().tenantId as string;

  const postUserTaskCommentParams = {
    wf_run_id: userTask.wfRunId,
    user_task_guid: userTask.id,
    comment: inputText,
  };

  const getUserTaskParams = {
    wf_run_id: userTask.wfRunId,
    user_task_guid: userTask.id,
  };

  const fetchComments = async () => {
    setIsLoading(true);

    setError(undefined);
    const response = await getUserTaskComments(tenantId, getUserTaskParams);

    if (response.error) {
      setError(response.error);
      return;
    }
    setCommentEventList(response.data ?? []);
    setIsLoading(false);
  };

  const createComment = async () => {
    const result = await postUserTaskComment(
      tenantId,
      postUserTaskCommentParams,
    );

    if (result.error) {
      toast.error(
        `Failed to create comment: ${result.error.message || "Unknown error"}`,
      );
      return;
    }

    fetchComments();
    setInputText("");
  };

  const renderComments = () => {
    if (isLoading) {
      return <Loading />;
    }

    if (error) {
      return (
        <ErrorHandler
          error={error}
          onRetry={fetchComments}
          allowReturn={false}
          title="Error fetching comments"
        />
      );
    }

    return (
      <CommentList
        eventList={commentEventList}
        userTask={userTask}
        admin={admin}
        fetchComments={fetchComments}
      />
    );
  };

  return (
    <div>
      <Dialog>
        <DialogTrigger asChild>
          <Button onClick={fetchComments} variant="default" className="w-full">
            View Comments
          </Button>
        </DialogTrigger>

        <DialogContent className="w-[60vw] h-[75vh] max-w-none flex flex-col">
          <DialogHeader>
            <DialogTitle>
              View comments for{" "}
              <Badge variant="outline" className="ml-2 font-mono">
                {userTask.userTaskDefName}
              </Badge>
            </DialogTitle>
          </DialogHeader>
          <div className="flex-grow overflow-y-auto my-4 -mx-6 px-6">
            {renderComments()}
          </div>
          <DialogFooter className="flex items-center gap-2">
            <Textarea
              placeholder="Add a comment..."
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              rows={2}
              className="flex-grow resize-none rounded-md border border-input focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            />
            <Button
              onClick={createComment}
              disabled={!inputText.trim() || isLoading}
              className="bg-amber-500 hover:bg-amber-600 text-primary-foreground"
            >
              Comment
            </Button>
            <DialogClose asChild>
              <Button variant="outline">Close</Button>
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
