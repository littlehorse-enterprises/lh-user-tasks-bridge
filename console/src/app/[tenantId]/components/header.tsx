"use client";
import logoWhite from "@/../public/images/logo-white.svg";
import logoBlack from "@/../public/images/logo.svg";
import { ThemeToggle } from "@/components/theme-toggle";
import { Avatar, AvatarFallback } from "@littlehorse-enterprises/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@littlehorse-enterprises/ui/dropdown-menu";
import { Skeleton } from "@littlehorse-enterprises/ui/skeleton";
import { LogOut } from "lucide-react";
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
        <p className="text-sm text-destructive-foreground bg-destructive text-center py-2">
          Viewing as User
        </p>
      )}
      <header className="flex justify-between items-center px-4 md:px-8 lg:px-16 py-4 border-b border-border">
        <a href={`/${tenantId}`} className="flex items-center gap-2">
          <Image
            src={mounted && resolvedTheme === "dark" ? logoWhite : logoBlack}
            alt="LittleHorse"
            height={32}
            width={32}
          />
          <span className="font-semibold">LittleHorse</span>
        </a>

        <div className="flex items-center gap-4">
          <ThemeToggle />

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="flex items-center gap-2">
                {status === "authenticated" && session.user ? (
                  <Avatar>
                    <AvatarFallback>
                      {(session.user.name || "User")[0].toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                ) : (
                  <Skeleton className="h-10 w-10 rounded-full" />
                )}
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuLabel>
                {status === "authenticated"
                  ? session.user.name || "User"
                  : "Loading..."}
              </DropdownMenuLabel>
              <DropdownMenuLabel className="text-sm text-muted-foreground">
                Tenant: {tenantId}
              </DropdownMenuLabel>

              <DropdownMenuSeparator />
              {isAdmin && (
                <>
                  {pathname.startsWith(`/${tenantId}/admin`) ? (
                    <DropdownMenuItem asChild>
                      <Link href={`/${tenantId}`}>View As User</Link>
                    </DropdownMenuItem>
                  ) : (
                    <DropdownMenuItem asChild className="text-destructive">
                      <Link href={`/${tenantId}/admin`}>
                        Back to Admin View
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
                className="cursor-pointer"
              >
                <LogOut className="mr-2 h-4 w-4" />
                <span>Log Out</span>
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </header>
    </>
  );
}
