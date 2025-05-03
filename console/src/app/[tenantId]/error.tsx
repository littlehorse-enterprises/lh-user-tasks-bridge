"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { AlertCircle, RefreshCw } from "lucide-react";
import { useParams } from "next/navigation";
import { useEffect } from "react";

export default function TenantErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const params = useParams();
  const tenantId = params.tenantId as string;

  useEffect(() => {
    // Log the error to an error reporting service
    console.error(`Tenant error (${tenantId}):`, error);
  }, [error, tenantId]);

  return (
    <div className="container flex items-center justify-center min-h-[50vh]">
      <Alert variant="destructive" className="max-w-xl">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Something went wrong!</AlertTitle>
        <AlertDescription className="space-y-4">
          <p>
            An error occurred in the tenant "{tenantId}". Our team has been
            notified.
          </p>

          {error.message && (
            <p className="text-sm font-mono bg-slate-900 p-2 rounded">
              {error.message}
            </p>
          )}

          <div className="flex space-x-2">
            <Button variant="outline" onClick={() => reset()} size="sm">
              <RefreshCw className="mr-2 h-4 w-4" /> Try again
            </Button>

            <Button
              variant="outline"
              onClick={() => (window.location.href = `/${tenantId}`)}
              size="sm"
            >
              Go to Tenant Home
            </Button>
          </div>
        </AlertDescription>
      </Alert>
    </div>
  );
}
