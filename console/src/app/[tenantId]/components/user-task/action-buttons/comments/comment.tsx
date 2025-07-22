import {
  deleteUserTaskComment,
  editUserTaskComment,
} from "@/app/[tenantId]/actions/user";
import { ErrorResponse } from "@/lib/error-handling";
import {
  AuditEventDTO,
  SimpleUserTaskRunDTO,
  UserTaskCommentEvent,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useSession } from "next-auth/react";
import { useState } from "react";
import Loading from "../../../loading";
import { toast } from "@littlehorse-enterprises/ui-library/sonner";

export default function Comment({
  commentEvent,
  tenantId,
  userTask,
  fetchComments,
}: {
  commentEvent: AuditEventDTO;
  tenantId: string;
  userTask: SimpleUserTaskRunDTO;
  fetchComments: () => Promise<void>;
}) {
  const ev = commentEvent.event as UserTaskCommentEvent;

  const { data: session } = useSession();

  const currentUserEmail = session?.user?.email;
  const currentUserName = session?.user?.preferredName;
  const [menuOpen, setMenuOpen] = useState(false);
  const isCurrentUser = currentUserEmail === ev.userId || currentUserName === ev.userId;
  const [inputText, setInputText] = useState("");
  const [editCommentId, setEditCommentId] = useState(0);
  const [isLoading, setIsLoading] = useState(false)

  const toggleMenu = () => {
    setMenuOpen(!menuOpen);
  };

  const editUserTaskCommentParams = {
    wf_run_id: userTask.wfRunId,
    user_task_guid: userTask.id,
    comment: inputText,
    comment_id: editCommentId,
  };

  const editComment = async () => {
    setIsLoading(true)

    const result = await editUserTaskComment(
      tenantId,
      editUserTaskCommentParams,
    );

    if (result.error) {
      toast.error(
        `Failed to complete task: ${result.error.message || "Unknown error"}`,
      );
      console.error("Error response:", result.error);
      return;
    }

    setInputText("");
    setEditCommentId(0);

    fetchComments();
    setIsLoading(false)
  };

  const deleteComment = async (commentId: number) => {
    setIsLoading(true)

    const result = await deleteUserTaskComment(tenantId, {
      wf_run_id: userTask.wfRunId,
      user_task_guid: userTask.id,
      comment_id: commentId,
    });

    if (result.error) {
      toast.error(
        `Failed to complete task: ${result.error.message || "Unknown error"}`,
      );
      console.error("Error response:", result.error);
      return;
    }


    fetchComments();
    setIsLoading(false)
  };

  return (
    <div
      className={` relative flex ${isCurrentUser ? "justify-end" : "justify-start"} mb-3 group`}
    >
      <div
        className={`relative w-fit px-4 py-2 rounded-xl shadow-sm text-sm break-words 
    ${isCurrentUser ? "bg-blue-100 text-gray-800" : "bg-gray-100 text-gray-800"} 
    max-w-[50%]`}
      >
        <div className="text-xs text-gray-500 mb-1">
          <span className="font-semibold text-gray-700">{ev.userId}</span>
          <span className="ml-2 text-[11px] text-gray-400">
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
        <div>
          {editCommentId === ev.commentId ? (
            isLoading ? (
                <Loading  />
            ):(
            <>
              <textarea
                className="bg-transparent outline-none border-none resize-none text-sm text-gray-800 break-words"
                placeholder="Edit comment"
                value={inputText}
                onChange={(e) => {
                  setInputText(e.target.value);
                }}
                rows={12}
              />
              <div className="flex justify-end mt-2">
                <button
                  className="p-2 bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium py-2 px-4 rounded-lg transition "
                  onClick={editComment}
                >
                  Edit Comment
                </button>
                <button
                  className="ml-2 p-2 bg-white border border-gray-300 hover:bg-gray-50 text-sm font-medium py-2 px-4 rounded-lg transition "
                  onClick={() => {
                    setEditCommentId(0);
                  }}
                >
                  Cancel
                </button>
              </div>
            </>
            )
          ) : (
            <span className="break-words text-sm text-gray-800">
              {ev.comment}
            </span>
          )}
        </div>
        {commentEvent.type == "COMMENT_EDITED" && (
          <span className="text-xs text-gray-400 justify start">(edited)</span>
        )}

        {isCurrentUser && (
          <>
            <button
              className="absolute top-1 right-2 hidden group-hover:block text-gray-500 text-lg focus:outline-none"
              onClick={toggleMenu}
            >
              ⋯
            </button>

            {menuOpen && (
              <div className="absolute bottom-11 right-6 w-32 bg-white border border-gray-200 rounded-lg shadow-xl z-50">
                <button
                  onClick={() => {
                    setMenuOpen(false);
                    setEditCommentId(ev.commentId);
                    setInputText(ev.comment);
                  }}
                  className="w-full text-left px-4 py-2 hover:bg-gray-100 text-gray-700 transition"
                >
                  Edit
                </button>
                <button
                  onClick={() => {
                    setMenuOpen(false);
                    deleteComment(ev.commentId);
                  }}
                  className="w-full text-left px-4 py-2 hover:bg-gray-100 text-red-600 transition"
                >
                  Delete
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
