import {
  AuditEventDTO,
  SimpleUserTaskRunDTO,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import Comment from "./comment";

export default function CommentList({
  eventList,
  userTask,
  tenantId,
  fetchComments,
}: {
  eventList: AuditEventDTO[];
  userTask: SimpleUserTaskRunDTO;
  tenantId: string;
  fetchComments: () => Promise<void>;
}) {
  return (
    <>
      <ul>
        {eventList.map((ev, key) => {
          switch (ev.type) {
            // Does not show COMMENT_DELETED Events
            case "COMMENTED":
              return (
                <li key={key}>
                  <Comment
                    commentEvent={ev}
                    userTask={userTask}
                    tenantId={tenantId}
                    fetchComments={fetchComments}
                  />
                </li>
              );

            case "COMMENT_EDITED":
              return (
                <li key={key}>
                  <Comment
                    commentEvent={ev}
                    userTask={userTask}
                    tenantId={tenantId}
                    fetchComments={fetchComments}
                  />
                </li>
              );
          }
        })}
      </ul>
    </>
  );
}
