"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { usePathname, useRouter } from "next/navigation";
import GroupsManagement from "./groups";
import UserTaskDefs from "./userTaskDefs";
import UsersManagement from "./users";

export default function AdminTabs({
  currentTab,
  tenantId,
  userTaskDefNames,
  userTaskDefCounts,
}: {
  currentTab: string;
  tenantId: string;
  userTaskDefNames: string[];
  userTaskDefCounts: {
    name: string;
    unassignedCount: number;
    assignedToMeCount: number;
  }[];
}) {
  const router = useRouter();
  const pathname = usePathname();

  const handleTabChange = (value: string) => {
    router.push(`${pathname}?tab=${value}`);
  };

  return (
    <Tabs defaultValue={currentTab} onValueChange={handleTabChange}>
      <TabsList>
        <TabsTrigger value="tasks">UserTask Definitions</TabsTrigger>
        <TabsTrigger value="users">Users</TabsTrigger>
        <TabsTrigger value="groups">Groups</TabsTrigger>
      </TabsList>

      <TabsContent value="tasks">
        {!userTaskDefNames.length ? (
          <div>
            <h1 className="text-center text-2xl font-bold">
              No UserTask Definitions Found
            </h1>
            <p className="text-center text-muted-foreground">
              You currently have no UserTask Definitions registered.
            </p>
          </div>
        ) : (
          <UserTaskDefs
            tenantId={tenantId}
            userTaskDefNames={userTaskDefNames}
            userTaskDefCounts={userTaskDefCounts}
          />
        )}
      </TabsContent>

      <TabsContent value="users">
        <UsersManagement />
      </TabsContent>

      <TabsContent value="groups">
        <GroupsManagement />
      </TabsContent>
    </Tabs>
  );
}
