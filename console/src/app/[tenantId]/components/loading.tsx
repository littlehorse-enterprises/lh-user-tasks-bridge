export default function Loading() {
  return (
    <div className="flex flex-col items-center justify-center h-64 space-y-4">
      <div className="relative">
        <div className="w-12 h-12 border-4 border-muted rounded-full"></div>
        <div className="absolute top-0 left-0 w-12 h-12 border-4 border-primary border-t-transparent rounded-full animate-spin"></div>
      </div>
      <div className="text-center space-y-2">
        <p className="text-sm font-medium text-foreground">Loading tasks...</p>
        <p className="text-xs text-muted-foreground">
          Please wait while we fetch your data
        </p>
      </div>
    </div>
  );
}
