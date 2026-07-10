"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Home,
  Swords,
  Trophy,
  Users,
  ClipboardList,
  LogOut,
  ChevronsUpDown,
  Loader2,
  Plus,
} from "lucide-react";

import { useAuth } from "@/lib/auth-context";
import { useLeague } from "@/lib/league-context";
import { UserAvatar } from "@/components/user-avatar";
import { CreateLeagueDialog } from "@/components/create-league-dialog";
import { JoinLeagueDialog } from "@/components/join-league-dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { href: "/", label: "Dashboard", icon: Home },
  { href: "/matchup", label: "Matchup", icon: Swords },
  { href: "/league", label: "League", icon: Trophy },
  { href: "/players", label: "Players", icon: Users },
  { href: "/roster", label: "Roster", icon: ClipboardList },
];

const PUBLIC_ROUTES = new Set(["/login"]);

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, isLoading, logout } = useAuth();
  const router = useRouter();
  const isPublicRoute = PUBLIC_ROUTES.has(pathname);

  useEffect(() => {
    if (isLoading) return;
    if (!user && !isPublicRoute) {
      router.replace("/login");
    }
    if (user && isPublicRoute) {
      router.replace("/");
    }
  }, [isLoading, user, isPublicRoute, router]);

  if (isPublicRoute) {
    return <>{children}</>;
  }

  if (isLoading || !user) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="min-h-dvh md:flex">
      <aside className="hidden w-64 shrink-0 flex-col border-r border-border bg-card/40 md:flex">
        <div className="flex items-center gap-2 px-6 py-6">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Swords className="h-5 w-5" />
          </div>
          <span className="text-lg font-bold tracking-tight">Diamond League</span>
        </div>

        <LeagueSwitcher />

        <nav className="flex flex-1 flex-col gap-1 px-3 py-4">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.href} {...item} pathname={pathname} />
          ))}
        </nav>

        <div className="border-t border-border p-3">
          <ProfileMenu align="start" />
        </div>
      </aside>

      <div className="flex min-h-dvh flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-border bg-background/80 px-4 py-3 backdrop-blur md:hidden">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <Swords className="h-4 w-4" />
            </div>
            <span className="font-bold tracking-tight">Diamond League</span>
          </div>
          <ProfileMenu align="end" />
        </header>

        <main className="flex-1 px-4 pb-24 pt-4 md:px-8 md:pb-8 md:pt-8">
          <div className="mx-auto w-full max-w-5xl">{children}</div>
        </main>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-40 flex items-center justify-around border-t border-border bg-card/95 px-2 py-2 backdrop-blur md:hidden">
        {NAV_ITEMS.map((item) => (
          <MobileNavLink key={item.href} {...item} pathname={pathname} />
        ))}
      </nav>
    </div>
  );
}

function NavLink({
  href,
  label,
  icon: Icon,
  pathname,
}: {
  href: string;
  label: string;
  icon: typeof Home;
  pathname: string;
}) {
  const active = pathname === href;
  return (
    <Link
      href={href}
      className={cn(
        "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-semibold text-muted-foreground transition-colors hover:bg-secondary/60 hover:text-foreground",
        active && "bg-primary/15 text-primary hover:bg-primary/15 hover:text-primary"
      )}
    >
      <Icon className="h-4 w-4" />
      {label}
    </Link>
  );
}

function MobileNavLink({
  href,
  label,
  icon: Icon,
  pathname,
}: {
  href: string;
  label: string;
  icon: typeof Home;
  pathname: string;
}) {
  const active = pathname === href;
  return (
    <Link
      href={href}
      className={cn(
        "flex flex-1 flex-col items-center gap-0.5 rounded-lg py-1.5 text-[11px] font-semibold text-muted-foreground",
        active && "text-primary"
      )}
    >
      <Icon className="h-5 w-5" />
      {label}
    </Link>
  );
}

function LeagueSwitcher() {
  const { leagues, currentLeague, setCurrentLeagueId, isLoading } = useLeague();
  const [createOpen, setCreateOpen] = useState(false);
  const [joinOpen, setJoinOpen] = useState(false);

  if (isLoading) {
    return <div className="mx-3 mb-2 h-10 animate-pulse rounded-lg bg-secondary/50" />;
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button className="mx-3 mb-2 flex items-center justify-between rounded-lg border border-border bg-secondary/30 px-3 py-2.5 text-left text-sm font-semibold transition-colors hover:bg-secondary/60">
            <span className="truncate">
              {currentLeague ? currentLeague.name : "No league selected"}
            </span>
            <ChevronsUpDown className="h-4 w-4 shrink-0 text-muted-foreground" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-56">
          {leagues.length > 0 ? (
            <>
              <DropdownMenuLabel>Your leagues</DropdownMenuLabel>
              <DropdownMenuSeparator />
              {leagues.map((league) => (
                <DropdownMenuItem key={league.id} onClick={() => setCurrentLeagueId(league.id)}>
                  {league.name}
                  <span className="ml-auto text-xs text-muted-foreground">{league.seasonYear}</span>
                </DropdownMenuItem>
              ))}
              <DropdownMenuSeparator />
            </>
          ) : null}
          <DropdownMenuItem onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" />
            Create a league
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => setJoinOpen(true)}>
            <Users className="h-4 w-4" />
            Join a league
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <CreateLeagueDialog open={createOpen} onOpenChange={setCreateOpen} />
      <JoinLeagueDialog open={joinOpen} onOpenChange={setJoinOpen} />
    </>
  );
}

function ProfileMenu({ align }: { align: "start" | "end" }) {
  const { user, logout } = useAuth();
  if (!user) return null;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button className="flex w-full items-center gap-2.5 rounded-lg p-1.5 text-left transition-colors hover:bg-secondary/60">
          <UserAvatar name={user.displayName} avatarUrl={user.avatarUrl} size="sm" />
          <div className="hidden min-w-0 flex-1 md:block">
            <p className="truncate text-sm font-semibold">{user.displayName}</p>
            <p className="truncate text-xs text-muted-foreground">{user.email}</p>
          </div>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align={align} className="w-52">
        <DropdownMenuLabel>{user.displayName}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={logout} className="text-destructive focus:bg-destructive/10">
          <LogOut className="h-4 w-4" />
          Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
