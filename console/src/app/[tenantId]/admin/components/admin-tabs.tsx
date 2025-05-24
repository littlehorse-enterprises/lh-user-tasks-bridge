"use client";

import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@littlehorse-enterprises/ui-library/tabs";
import {
  IDPGroupDTO,
  IDPUserDTO,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { usePathname, useRouter } from "next/navigation";
import GroupsManagement from "./groups";
import UserTaskDefs from "./userTaskDefs";
import UsersManagement from "./users";

interface AdminTabsProps {
  currentTab: string;
  tenantId: string;
  userTaskDefs: string[];
  users: IDPUserDTO[];
  groups: IDPGroupDTO[];
  hasDataErrors: {
    userTaskDefs: boolean;
    users: boolean;
    groups: boolean;
  };
}

export default function AdminTabs({
  currentTab,
  tenantId,
  userTaskDefs,
  users,
  groups,
  hasDataErrors,
}: AdminTabsProps) {
  const router = useRouter();
  const pathname = usePathname();

  const handleTabChange = (value: string) => {
    router.push(`${pathname}?tab=${value}`);
  };

  return (
    <Tabs
      defaultValue={currentTab}
      onValueChange={handleTabChange}
      className="w-full"
    >
      <div className="border-b border-border px-6 py-4">
        <TabsList className="bg-muted">
          <TabsTrigger
            value="tasks"
            className="data-[state=active]:bg-background"
          >
            UserTaskDefs
          </TabsTrigger>
          <TabsTrigger
            value="users"
            className="data-[state=active]:bg-background"
          >
            Users
          </TabsTrigger>
          <TabsTrigger
            value="groups"
            className="data-[state=active]:bg-background"
          >
            Groups
          </TabsTrigger>
        </TabsList>
      </div>

      <TabsContent value="tasks" className="p-6">
        <UserTaskDefs
          tenantId={tenantId}
          userTaskDefs={userTaskDefs}
          hasError={hasDataErrors.userTaskDefs}
        />
      </TabsContent>

      <TabsContent value="users" className="p-6">
        <UsersManagement
          tenantId={tenantId}
          initialUsers={users}
          hasError={hasDataErrors.users}
        />
      </TabsContent>

      <TabsContent value="groups" className="p-6">
        <GroupsManagement
          tenantId={tenantId}
          initialGroups={groups}
          hasError={hasDataErrors.groups}
        />
      </TabsContent>
    </Tabs>
  );
}
