"use client";
import { adminListUserTasks } from "@/app/[tenantId]/actions/admin";
import { listUserTasks } from "@/app/[tenantId]/actions/user";
import { Button } from "@/components/ui/button";
import { DateRangePicker } from "@/components/ui/data-range-picker";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  ListUserTasksResponse,
  Status,
  UserGroup,
} from "@littlehorse-enterprises/sso-workflow-bridge-api-client";
import { useInfiniteQuery } from "@tanstack/react-query";
import { FilterIcon } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useLayoutEffect, useState } from "react";
import { toast } from "sonner";
import UserTask from "../../components/user-task";
import { useTenantId } from "../../layout";
import Loading from "../loading";

type Query = {
  user_group_id?: UserGroup["id"];
  status?: Status;
  earliest_start_date?: string;
  latest_start_date?: string;
};

export default function ListUserTasks({
  userGroups,
  userTaskDefName,
  initialData,
}: {
  userGroups: UserGroup[];
  userTaskDefName?: string;
  initialData: ListUserTasksResponse;
}) {
  const [query, setQuery] = useState<Query>({});
  const [search, setSearch] = useState("");
  const [limit] = useState(10);
  const tenantId = useTenantId();

  const router = useRouter();

  const searchParams = useSearchParams();
  useEffect(() => {
    const query = Object.fromEntries(
      new URLSearchParams(searchParams).entries(),
    ) as Query;
    setQuery(query);
  }, [searchParams]);

  useLayoutEffect(() => {
    const searchParams = new URLSearchParams();
    Object.entries(query)
      .filter(([, value]) => value !== undefined)
      .forEach(([key, value]) => searchParams.set(key, value.toString()));
    router.replace(
      `${window.location.pathname.split("?")[0]}?${searchParams.toString()}`,
    );
  }, [query]);

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useInfiniteQuery({
      queryKey: ["userTasks", userTaskDefName, query, limit],
      initialData: {
        pages: [initialData],
        pageParams: [initialData.bookmark ?? undefined],
      },
      initialPageParam: undefined,
      getNextPageParam: (lastPage: ListUserTasksResponse) => lastPage.bookmark,
      queryFn: async ({
        pageParam: bookmark,
      }): Promise<ListUserTasksResponse> => {
        const response = await (userTaskDefName
          ? adminListUserTasks(tenantId, {
              ...query,
              limit,
              type: userTaskDefName,
              bookmark,
            })
          : listUserTasks(tenantId, {
              ...query,
              limit,
              bookmark,
            }));

        if ("message" in response) {
          toast.error(response.message);
          return {
            userTasks: [],
            bookmark: null,
          };
        }

        return response;
      },
    });

  if (data === undefined) return <Loading />;

  return (
    <div>
      <h1 className="text-2xl font-bold">{userTaskDefName ?? "My Tasks"}</h1>
      <div className="flex items-center gap-2 py-4">
        <Input
          placeholder="Search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <Popover>
          <PopoverTrigger asChild>
            <Button variant="outline" size="icon">
              <FilterIcon />
            </Button>
          </PopoverTrigger>
          <PopoverContent>
            <div className="space-y-4 [&>*]:w-full">
              <div>
                <Label>Date Range</Label>
                <DateRangePicker
                  initialDateFrom={
                    query.earliest_start_date
                      ? new Date(query.earliest_start_date)
                      : undefined
                  }
                  initialDateTo={
                    query.latest_start_date
                      ? new Date(query.latest_start_date)
                      : undefined
                  }
                  onUpdate={(values) => {
                    setQuery({
                      ...query,
                      earliest_start_date: values.range.from.toISOString(),
                      latest_start_date: values.range.to?.toISOString(),
                    });
                  }}
                />
              </div>
              <div>
                <Label>Status</Label>
                <Select
                  value={query.status ?? "ALL"}
                  onValueChange={(value) => {
                    setQuery({
                      ...query,
                      status: value === "ALL" ? undefined : (value as Status),
                    });
                  }}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">ALL</SelectItem>
                    {userTaskDefName && (
                      <SelectItem value="UNASSIGNED">UNASSIGNED</SelectItem>
                    )}
                    <SelectItem value="ASSIGNED">ASSIGNED</SelectItem>
                    <SelectItem value="DONE">DONE</SelectItem>
                    <SelectItem value="CANCELLED">CANCELLED</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label>User Group</Label>
                <Select
                  value={query.user_group_id ?? "ALL"}
                  onValueChange={(value) => {
                    setQuery({
                      ...query,
                      user_group_id:
                        value === "ALL"
                          ? undefined
                          : (value as UserGroup["id"]),
                    });
                  }}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">ALL</SelectItem>
                    {userGroups.map((group) => (
                      <SelectItem key={group.id} value={group.id}>
                        {group.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <Button
                variant="outline"
                onClick={() => {
                  setQuery({});
                }}
              >
                Clear
              </Button>
            </div>
          </PopoverContent>
        </Popover>
      </div>

      {data.pages.flatMap((page) => page.userTasks).length ? (
        <div className="flex flex-col gap-8 items-center">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {data.pages
              .flatMap((page) => page.userTasks)
              .sort(
                (a, b) =>
                  new Date(b.scheduledTime).getTime() -
                  new Date(a.scheduledTime).getTime(),
              )
              .filter((userTask) => {
                return Object.values(userTask).some((value) => {
                  return JSON.stringify(value)
                    .toLowerCase()
                    .includes(search.toLowerCase());
                });
              })
              .map((userTask) => (
                <UserTask
                  key={userTask.id}
                  userTask={userTask}
                  admin={!!userTaskDefName}
                />
              ))}
          </div>
          {hasNextPage && (
            <Button
              onClick={() => fetchNextPage()}
              loading={isFetchingNextPage}
              className="w-fit"
            >
              Load More
            </Button>
          )}
        </div>
      ) : (
        <div>
          <h1 className="text-center text-2xl font-bold">No UserTasks Found</h1>
          <p className="text-center text-muted-foreground max-w-[60ch] mx-auto">
            You currently have no UserTasks assigned to you or your group with
            the current filter. Please try a different filter. Or contact your
            administrator if you believe this is an error.
          </p>
        </div>
      )}
    </div>
  );
}
