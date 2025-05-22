"use client";

import { ErrorResponse, ErrorType } from "@/lib/error-handling";
import {
  Alert,
  AlertDescription,
  AlertTitle,
} from "@littlehorse-enterprises/ui/alert";
import { Button } from "@littlehorse-enterprises/ui/button";
import { AlertCircle, RefreshCw } from "lucide-react";
import { useRouter } from "next/navigation";

type ErrorHandlerProps = {
  error: ErrorResponse;
  onRetry?: () => void;
  title?: string;
  allowReturn?: boolean;
  returnPath?: string;
};

export function ErrorHandler({
  error,
  onRetry,
  title,
  allowReturn = true,
  returnPath,
}: ErrorHandlerProps) {
  const router = useRouter();

  const getErrorTitle = () => {
    if (title) return title;

    switch (error.type) {
      case ErrorType.UNAUTHORIZED:
        return "Authentication Error";
      case ErrorType.FORBIDDEN:
        return "Permission Denied";
      case ErrorType.NOT_FOUND:
        return "Not Found";
      case ErrorType.NETWORK:
        return "Network Error";
      case ErrorType.SERVER:
        return "Server Error";
      case ErrorType.VALIDATION:
        return "Validation Error";
      default:
        return "An Error Occurred";
    }
  };

  const handleGoBack = () => {
    if (returnPath) {
      router.push(returnPath);
    } else {
      router.back();
    }
  };

  return (
    <Alert variant="destructive" className="mt-4">
      <AlertCircle className="h-4 w-4" />
      <AlertTitle>{getErrorTitle()}</AlertTitle>
      <AlertDescription>{error.message}</AlertDescription>

      <div className="flex gap-2 mt-4">
        {onRetry && (
          <Button onClick={onRetry} variant="outline" size="sm">
            <RefreshCw className="mr-2 h-4 w-4" /> Retry
          </Button>
        )}

        {allowReturn && (
          <Button onClick={handleGoBack} variant="outline" size="sm">
            Return to Previous Page
          </Button>
        )}
      </div>
    </Alert>
  );
}
