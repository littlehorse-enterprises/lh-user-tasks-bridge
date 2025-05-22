"use client";

import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@littlehorse-enterprises/ui-library/tabs";
import { usePathname, useRouter } from "next/navigation";
import GroupsManagement from "./groups";
import UserTaskDefs from "./userTaskDefs";
import UsersManagement from "./users";

export default function AdminTabs({
  currentTab,
  tenantId,
}: {
  currentTab: string;
  tenantId: string;
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
        <UserTaskDefs tenantId={tenantId} />
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
