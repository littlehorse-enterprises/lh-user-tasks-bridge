"use client";
import logoWhite from "@/../public/images/logo-white.svg";
import logoBlack from "@/../public/images/logo.svg";
import { ThemeToggle } from "@/components/theme-toggle";
import {
  Avatar,
  AvatarFallback,
} from "@littlehorse-enterprises/ui-library/avatar";
import { Badge } from "@littlehorse-enterprises/ui-library/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@littlehorse-enterprises/ui-library/dropdown-menu";
import { Skeleton } from "@littlehorse-enterprises/ui-library/skeleton";
import { LogOut, Settings, User } from "lucide-react";
import { signOut, useSession } from "next-auth/react";
import { useTheme } from "next-themes";
import Image from "next/image";
import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { useEffect, useState } from "react";

export default function Header() {
  const { data: session, status } = useSession();
  const pathname = usePathname();
  const [mounted, setMounted] = useState(false);
  const { resolvedTheme } = useTheme();

  // Set mounted to true after component mounts
  useEffect(() => {
    setMounted(true);
  }, []);

  const isAdmin =
    status === "authenticated" &&
    session?.roles.includes("lh-user-tasks-admin");
  const { tenantId } = useParams();

  return (
    <>
      {isAdmin && pathname == `/${tenantId}` && (
        <div className="bg-destructive border-b border-destructive-foreground/20">
          <div className="px-4 md:px-8 lg:px-16 py-3">
            <div className="flex items-center justify-center gap-2">
              <div className="h-2 w-2 rounded-full bg-destructive-foreground/60"></div>
              <p className="text-sm font-medium text-destructive-foreground">
                Viewing as User
              </p>
              <div className="h-2 w-2 rounded-full bg-destructive-foreground/60"></div>
            </div>
          </div>
        </div>
      )}
      <header className="bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 border-b border-border/40 sticky top-0 z-50">
        <div className="px-4 md:px-8 lg:px-16 py-4">
          <div className="flex justify-between items-center">
            {/* Logo and Branding */}
            <Link
              href={`/${tenantId}`}
              className="flex items-center gap-3 group"
            >
              <div className="relative">
                <Image
                  src={
                    mounted && resolvedTheme === "dark" ? logoWhite : logoBlack
                  }
                  alt="LittleHorse"
                  height={40}
                  width={40}
                  className="transition-transform group-hover:scale-105"
                />
              </div>
              <div className="flex flex-col">
                <span className="font-bold text-lg text-foreground">
                  LittleHorse
                </span>
                <span className="text-xs text-muted-foreground font-medium">
                  UserTasks Bridge
                </span>
              </div>
            </Link>

            {/* Right side actions */}
            <div className="flex items-center gap-3">
              <ThemeToggle />

              {/* User Menu */}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button className="flex items-center gap-3 p-2 rounded-lg hover:bg-accent/50 transition-colors">
                    {status === "authenticated" && session.user ? (
                      <>
                        <div className="flex flex-col items-end text-right">
                          <span className="text-sm font-medium text-foreground">
                            {session.user.name || "User"}
                          </span>
                          <div className="flex items-center gap-2">
                            <span className="text-xs text-muted-foreground">
                              {tenantId}
                            </span>
                            {isAdmin && (
                              <Badge
                                variant="secondary"
                                className="text-xs px-2 py-0"
                              >
                                Admin
                              </Badge>
                            )}
                          </div>
                        </div>
                        <Avatar className="h-9 w-9 border-2 border-border">
                          <AvatarFallback className="bg-primary text-primary-foreground font-semibold">
                            {(session.user.name || "User")[0].toUpperCase()}
                          </AvatarFallback>
                        </Avatar>
                      </>
                    ) : (
                      <>
                        <div className="flex flex-col items-end text-right">
                          <Skeleton className="h-4 w-20" />
                          <Skeleton className="h-3 w-16 mt-1" />
                        </div>
                        <Skeleton className="h-9 w-9 rounded-full" />
                      </>
                    )}
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuLabel className="flex flex-col">
                    <span className="font-medium">
                      {status === "authenticated"
                        ? session.user.name || "User"
                        : "Loading..."}
                    </span>
                    <span className="text-xs text-muted-foreground font-normal">
                      Tenant: {tenantId}
                    </span>
                  </DropdownMenuLabel>

                  <DropdownMenuSeparator />

                  {isAdmin && (
                    <>
                      {pathname.startsWith(`/${tenantId}/admin`) ? (
                        <DropdownMenuItem asChild>
                          <Link
                            href={`/${tenantId}`}
                            className="flex items-center gap-2"
                          >
                            <User className="h-4 w-4" />
                            View As User
                          </Link>
                        </DropdownMenuItem>
                      ) : (
                        <DropdownMenuItem asChild>
                          <Link
                            href={`/${tenantId}/admin`}
                            className="flex items-center gap-2"
                          >
                            <Settings className="h-4 w-4" />
                            Admin Dashboard
                          </Link>
                        </DropdownMenuItem>
                      )}
                      <DropdownMenuSeparator />
                    </>
                  )}

                  <DropdownMenuItem
                    onClick={() => {
                      signOut();
                    }}
                    className="cursor-pointer text-destructive focus:text-destructive"
                  >
                    <LogOut className="mr-2 h-4 w-4" />
                    <span>Sign Out</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>
        </div>
      </header>
    </>
  );
}
