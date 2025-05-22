"use client";
import { adminGetAllTasks } from "@/app/[tenantId]/actions/admin";
import { getUserTasks } from "@/app/[tenantId]/actions/user";
import { DateRangePicker } from "@/components/ui/data-range-picker";
import { ErrorResponse, ErrorType } from "@/lib/error-handling";
import {
  Alert,
  AlertDescription,
  AlertTitle,
} from "@littlehorse-enterprises/ui/alert";
import { Button } from "@littlehorse-enterprises/ui/button";
import { Input } from "@littlehorse-enterprises/ui/input";
import { Label } from "@littlehorse-enterprises/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@littlehorse-enterprises/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@littlehorse-enterprises/ui/select";
import {
  UserGroupDTO,
  UserTaskRunListDTO,
  UserTaskStatus,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { AlertCircle, FilterIcon, RefreshCw } from "lucide-react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useLayoutEffect, useState } from "react";
import useSWRInfinite from "swr/infinite";
import UserTask from "../../components/user-task";
import Loading from "../loading";
// Add a counter for generating unique keys
const getUniqueId = () => Date.now().toString();

type Query = {
  user_group_id?: UserGroupDTO["id"];
  status?: UserTaskStatus;
  earliest_start_date?: string;
  latest_start_date?: string;
};

export default function ListUserTasks({
  userGroups,
  userTaskDefName,
  initialData,
  claimable,
  initialError,
}: {
  userGroups: UserGroupDTO[];
  userTaskDefName?: string;
  initialData: UserTaskRunListDTO;
  claimable?: boolean;
  initialError?: ErrorResponse;
}) {
  const [query, setQuery] = useState<Query>({});
  const [search, setSearch] = useState("");
  const [limit] = useState(100);
  const [error, setError] = useState<ErrorResponse | undefined>(initialError);
  const tenantId = useParams().tenantId as string;
  const [mountId] = useState(getUniqueId());

  // Ensure userGroups is an array, even if undefined
  const safeUserGroups = Array.isArray(userGroups) ? userGroups : [];

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
  }, [query, router]);

  const getKey = (
    pageIndex: number,
    previousPageData: UserTaskRunListDTO | null,
  ) => {
    if (claimable) return null;

    if (previousPageData && !previousPageData.bookmark) return null; // reached the end
    return [mountId, "userTask", query, limit, previousPageData?.bookmark];
  };

  const { data, setSize, isValidating, mutate } =
    useSWRInfinite<UserTaskRunListDTO>(
      getKey,
      async (key): Promise<UserTaskRunListDTO> => {
        setError(undefined);

        const [, , query, limit, bookmark] = key;
        const response = await (userTaskDefName
          ? adminGetAllTasks(tenantId, {
              ...query,
              limit,
              type: userTaskDefName,
              bookmark,
            })
          : getUserTasks(tenantId, {
              ...query,
              limit,
              bookmark,
            }));

        // Handle error
        if (response.error) {
          setError(response.error);
          return { userTasks: [], bookmark: undefined };
        }

        return response.data || { userTasks: [], bookmark: undefined };
      },
      {
        refreshInterval: 1000,
        revalidateOnFocus: true,
        revalidateOnReconnect: true,
        revalidateOnMount: true,
        revalidateIfStale: true,
        fallbackData: [initialData],
        dedupingInterval: 500,
        shouldRetryOnError: false,
      },
    );

  const fetchNextPage = () => {
    setSize((size) => size + 1);
  };

  const handleRetry = () => {
    setError(undefined);
    mutate();
  };

  const isPending = !data;
  if (isPending) return <Loading />;
  const hasNextPage = !!(data && data[data.length - 1]?.bookmark);

  const isFetchingNextPage = isValidating && hasNextPage;

  // Handle error display
  if (error) {
    return (
      <div>
        <h1 className="text-2xl font-bold">
          {userTaskDefName ?? (!claimable && "My Assigned Tasks")}
        </h1>
        <Alert variant="destructive" className="mt-4">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>
            {error.type === ErrorType.UNAUTHORIZED
              ? "Authentication Error"
              : error.type === ErrorType.FORBIDDEN
                ? "Permission Denied"
                : error.type === ErrorType.NETWORK
                  ? "Network Error"
                  : "Error Loading Tasks"}
          </AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
          <Button
            onClick={handleRetry}
            variant="outline"
            size="sm"
            className="mt-2"
          >
            <RefreshCw className="mr-2 h-4 w-4" /> Retry
          </Button>
        </Alert>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold">
        {userTaskDefName ?? (!claimable && "My Assigned Tasks")}
      </h1>
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
            <div className="space-y-4 *:w-full">
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
                      status:
                        value === "ALL" ? undefined : (value as UserTaskStatus),
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
                      user_group_id: value === "ALL" ? undefined : value,
                    });
                  }}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">ALL</SelectItem>
                    {safeUserGroups.map((group) => (
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

      {data.flatMap((page) => page.userTasks).length ? (
        <div className="flex flex-col gap-8 items-center w-full">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 w-full">
            {data
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
                  claimable={claimable}
                />
              ))}
          </div>

          {hasNextPage && (
            <Button
              variant="outline"
              onClick={fetchNextPage}
              disabled={isFetchingNextPage}
            >
              {isFetchingNextPage ? "Loading more..." : "Load more"}
            </Button>
          )}
        </div>
      ) : (
        <div className="text-center py-8 text-muted-foreground">
          No tasks found
        </div>
      )}
    </div>
  );
}
