import Link from "next/link";
import { adminListUserTaskDefNames } from "../actions/admin";

export default async function AdminPage({
  params,
}: {
  params: { tenantId: string };
}) {
  const adminListUserTaskDefNamesResponse = await adminListUserTaskDefNames(
    params.tenantId,
    {
      // TODO: add pagination so this needs to be on client using `useInfiniteQuery`
      limit: 100,
    },
  );
  if ("message" in adminListUserTaskDefNamesResponse)
    throw new Error(adminListUserTaskDefNamesResponse.message);

  if (!adminListUserTaskDefNamesResponse.userTaskDefNames?.length)
    return (
      <div>
        <h1 className="text-center text-2xl font-bold">
          No UserTask Definitions Found
        </h1>
        <p className="text-center text-muted-foreground">
          You currently have no UserTask Definitions registered.
        </p>
      </div>
    );

  return (
    <div className="flex flex-col gap-2">
      <h1 className="text-2xl font-bold">View UserTask Definitions</h1>
      {adminListUserTaskDefNamesResponse.userTaskDefNames.map(
        (userTaskDefName) => (
          <a
            key={userTaskDefName}
            href={`/${params.tenantId}/admin/${userTaskDefName}`}
            className="underline"
          >
            {userTaskDefName}
          </a>
        ),
      )}
    </div>
  );
}
