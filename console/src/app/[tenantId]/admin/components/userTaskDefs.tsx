import { WrenchIcon } from "lucide-react";
import Link from "next/link";

interface UserTaskDefCount {
  name: string;
  unassignedCount: number;
  assignedToMeCount: number;
}

interface UserTaskDefsProps {
  tenantId: string;
  userTaskDefNames: string[];
  userTaskDefCounts: UserTaskDefCount[];
}

export default function UserTaskDefs({
  tenantId,
  userTaskDefNames,
  userTaskDefCounts,
}: UserTaskDefsProps) {
  if (!userTaskDefNames.length) {
    return (
      <div>
        <h1 className="text-center text-2xl font-bold text-foreground">
          No UserTask Definitions Found
        </h1>
        <p className="text-center text-muted-foreground">
          You currently have no UserTask Definitions registered.
        </p>
      </div>
    );
  }

  return (
    <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
      {userTaskDefNames.map((name) => {
        const counts = userTaskDefCounts.find(
          (count) => count.name === name,
        ) || {
          unassignedCount: 0,
          assignedToMeCount: 0,
        };

        return (
          <Link key={name} href={`/${tenantId}/admin/${name}`}>
            <div className="rounded-lg border bg-card p-5 hover:bg-accent/50 transition-colors cursor-pointer h-full">
              <div className="flex items-center gap-4">
                <WrenchIcon className="size-10 text-primary bg-primary/25 p-2 rounded-full" />
                <h3 className="text-lg font-medium text-card-foreground">
                  {name}
                </h3>
              </div>

              <div className="mt-4 flex gap-3 text-sm">
                {counts.unassignedCount > 0 ? (
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-accent text-accent-foreground">
                    {counts.unassignedCount >= 99
                      ? "99+"
                      : counts.unassignedCount}{" "}
                    unassigned
                  </span>
                ) : null}

                {counts.assignedToMeCount > 0 ? (
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-primary text-primary-foreground">
                    {counts.assignedToMeCount >= 99
                      ? "99+"
                      : counts.assignedToMeCount}{" "}
                    assigned to me
                  </span>
                ) : null}
              </div>
            </div>
          </Link>
        );
      })}
    </div>
  );
}
