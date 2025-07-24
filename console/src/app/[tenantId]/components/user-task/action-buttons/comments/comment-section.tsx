import {
  AuditEventDTO,
  SimpleUserTaskRunDTO,
  UserTaskEventType,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import Comment from "./comment";

export default function CommentList({
  eventList,
  userTask,
  fetchComments,
  admin,
}: {
  eventList: AuditEventDTO[];
  userTask: SimpleUserTaskRunDTO;
  fetchComments: () => Promise<void>;
  admin?: boolean;
}) {
  const filteredEvents = eventList.filter(
    (ev) =>
      ev.type === UserTaskEventType.COMMENT_ADDED ||
      ev.type === UserTaskEventType.COMMENT_EDITED,
  );

  if (filteredEvents.length === 0) {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground">
        <p>No comments yet.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {filteredEvents.map((ev) => (
        <Comment
          key={ev.time}
          commentEvent={ev}
          admin={admin}
          userTask={userTask}
          fetchComments={fetchComments}
        />
      ))}
    </div>
  );
}
