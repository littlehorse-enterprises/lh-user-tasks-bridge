"use client";
import logo from "@/../public/images/logo.svg";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { LogOut } from "lucide-react";
import { signOut, useSession } from "next-auth/react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTenantId } from "../layout";

export default function Header() {
  const session = useSession();
  const pathname = usePathname();
  const tenantId = useTenantId();

  return (
    <>
      {session.data?.roles.includes("lh-user-tasks-admin") &&
        pathname == `/${tenantId}` && (
          <p className="text-sm text-destructive-foreground bg-destructive text-center py-2">
            Viewing as User
          </p>
        )}
      <header className="flex px-4 md:px-8 lg:px-16 py-4 bg-foreground/10">
        <div className="flex-1 flex items-center">
          <Image src={logo} alt="Logo" height={50} priority className="mr-2" />

          <h1 className="text-xl font-bold">LittleHorse UserTasks</h1>
        </div>
        <div className="flex-1 flex justify-end gap-2 items-center">
          {session.status == "loading" && (
            <Skeleton className="size-10 rounded-full" />
          )}
          {session.status == "authenticated" && session.data.user?.name && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Avatar className="cursor-pointer">
                  <AvatarFallback>
                    {session.data.user.name.charAt(0).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuLabel>{session.data.user.name}</DropdownMenuLabel>
                <DropdownMenuLabel className="text-sm text-muted-foreground">
                  Tenant: {tenantId}
                </DropdownMenuLabel>

                <DropdownMenuSeparator />
                {session.data?.roles.includes("lh-user-tasks-admin") && (
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
          )}
        </div>
      </header>
    </>
  );
}
