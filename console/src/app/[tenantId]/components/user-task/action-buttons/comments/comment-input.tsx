import {
  getUserTaskComments,
  postUserTaskComment,
} from "@/app/[tenantId]/actions/user";
import { ErrorResponse } from "@/lib/error-handling";
import {
  AuditEventDTO,
  SimpleUserTaskRunDTO,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useParams } from "next/navigation";
import { useState } from "react";
import Loading from "../../../loading";
import { ErrorHandler } from "../../../error-handler";
import CommentList from "./comment-section";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@littlehorse-enterprises/ui-library/dialog";
import {
  Button,
  buttonVariants,
} from "@littlehorse-enterprises/ui-library/button";

export default function UserTaskComments({
  userTask,
}: {
  userTask: SimpleUserTaskRunDTO;
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
      setError(result.error);
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
        tenantId={tenantId}
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

        <DialogContent className="w-[60vw] h-[75vh] max-w-none">
          <DialogHeader>
            <DialogTitle>
              <p>View comments</p>
              <span className="font-mono">{userTask.userTaskDefName}</span>
            </DialogTitle>
          </DialogHeader>
          <div className="flex flex-col-reverse overflow-y-auto overflow-x-hidden relative ">
            {renderComments()}
          </div>
          <DialogFooter>
            <div className="relative w-full border border-white-300 p-2 rounded-lg">
              <textarea
                className="w-full resize-none border-none outline-none"
                placeholder="Add a comment..."
                value={inputText}
                onChange={(e) => {
                  setInputText(e.target.value);
                }}
                rows={4}
              />
              <div className="flex justify-end m-2 mt-auto absolute bottom-2 right-2">
                <button
                  className="bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium py-2 px-4 rounded-lg transition "
                  onClick={createComment}
                >
                  Comment
                </button>
              </div>
            </div>
            <div className="mt-auto">
              <DialogClose className={buttonVariants({ variant: "outline" })}>
                Close
              </DialogClose>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
