export default function Footer() {
  return (
    <footer className="bg-muted/30 border-t border-border/40 mt-auto">
      <div className="px-4 md:px-8 lg:px-16 py-6">
        <div className="flex flex-col md:flex-row justify-between items-center gap-4">
          <div className="flex flex-col md:flex-row items-center gap-4 text-sm text-muted-foreground">
            <p className="font-medium">
              &copy; {new Date().getFullYear()} LittleHorse Enterprises LLC
            </p>
            <div className="hidden md:block h-4 w-px bg-border"></div>
            <p>UserTasks Bridge Console</p>
          </div>
          <div className="flex items-center gap-4 text-xs text-muted-foreground">
            <span className="flex items-center gap-2">
              <div className="h-2 w-2 rounded-full bg-primary" />
              Version: {process.env.NEXT_PUBLIC_API_CLIENT_VERSION}
            </span>
          </div>
        </div>
      </div>
    </footer>
  );
}
