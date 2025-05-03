"use client";

import { useParams, useSearchParams } from "next/navigation";
import AdminTabs from "./components/admin-tabs";

export default function AdminPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const tenantId = params.tenantId as string;
  const currentTab = searchParams.get("tab") || "tasks";

  return (
    <>
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold">Admin Dashboard</h1>

        <AdminTabs currentTab={currentTab} tenantId={tenantId} />
      </div>
    </>
  );
}
