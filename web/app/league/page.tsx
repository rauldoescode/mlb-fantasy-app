"use client";

import { useEffect, useState } from "react";
import {
  Trophy,
  ShieldAlert,
  Crown,
  Copy,
  Check,
  RefreshCw,
  Globe,
  Lock,
  Loader2,
} from "lucide-react";

import { useAuth } from "@/lib/auth-context";
import { useLeague } from "@/lib/league-context";
import { api, ApiError, type LeagueResponse, type StandingRow } from "@/lib/api";
import { formatPoints } from "@/lib/format";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { UserAvatar } from "@/components/user-avatar";
import { EmptyState } from "@/components/empty-state";
import { CreateLeagueDialog } from "@/components/create-league-dialog";
import { JoinLeagueDialog } from "@/components/join-league-dialog";
import { cn } from "@/lib/utils";

export default function LeaguePage() {
  const { user } = useAuth();
  const { currentLeague, leagues, isLoading: leaguesLoading } = useLeague();
  const [standings, setStandings] = useState<StandingRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!currentLeague) return;
    setStandings(null);
    setError(null);
    api
      .standings(currentLeague.id)
      .then(setStandings)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load standings"));
  }, [currentLeague]);

  if (!leaguesLoading && leagues.length === 0) {
    return <NoLeaguesEmptyState />;
  }

  if (leaguesLoading || !currentLeague) {
    return <Skeleton className="h-96 w-full rounded-xl" />;
  }

  if (error) {
    return <EmptyState icon={ShieldAlert} title="Couldn't load standings" description={error} />;
  }

  const isCommissioner = user?.id === currentLeague.commissionerId;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <p className="text-sm font-medium text-muted-foreground">
          {currentLeague.seasonYear} Season
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-bold tracking-tight">{currentLeague.name}</h1>
          <Badge variant={currentLeague.visibility === "PUBLIC" ? "success" : "muted"}>
            {currentLeague.visibility === "PUBLIC" ? (
              <Globe className="h-3 w-3" />
            ) : (
              <Lock className="h-3 w-3" />
            )}
            {currentLeague.visibility === "PUBLIC" ? "Public" : "Private"}
          </Badge>
        </div>
      </div>

      {isCommissioner ? <CommissionerPanel league={currentLeague} /> : null}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Standings</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {!standings ? (
            <div className="flex flex-col gap-2 p-5">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full rounded-lg" />
              ))}
            </div>
          ) : standings.length === 0 ? (
            <p className="py-12 text-center text-sm text-muted-foreground">No members yet.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="px-5 py-3 font-medium">#</th>
                  <th className="px-2 py-3 font-medium">Team</th>
                  <th className="px-2 py-3 text-center font-medium">W</th>
                  <th className="px-2 py-3 text-center font-medium">L</th>
                  <th className="px-2 py-3 text-center font-medium">T</th>
                  <th className="px-5 py-3 text-right font-medium">PF</th>
                </tr>
              </thead>
              <tbody>
                {standings.map((row, i) => {
                  const isMe = row.userId === user?.id;
                  const isCommissioner = row.userId === currentLeague.commissionerId;
                  return (
                    <tr
                      key={row.userId}
                      className={cn(
                        "border-b border-border/60 last:border-0",
                        isMe && "bg-primary/10"
                      )}
                    >
                      <td className="px-5 py-3 font-semibold tabular-nums text-muted-foreground">
                        {i + 1}
                      </td>
                      <td className="px-2 py-3">
                        <div className="flex items-center gap-3">
                          <UserAvatar name={row.displayName} size="sm" />
                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5">
                              <p className="truncate font-semibold">{row.teamName}</p>
                              {isCommissioner ? (
                                <Crown className="h-3.5 w-3.5 shrink-0 text-amber-400" />
                              ) : null}
                            </div>
                            <p className="truncate text-xs text-muted-foreground">
                              {row.displayName}
                            </p>
                          </div>
                        </div>
                      </td>
                      <td className="px-2 py-3 text-center tabular-nums">{row.wins}</td>
                      <td className="px-2 py-3 text-center tabular-nums">{row.losses}</td>
                      <td className="px-2 py-3 text-center tabular-nums">{row.ties}</td>
                      <td className="px-5 py-3 text-right font-semibold tabular-nums">
                        {formatPoints(row.pointsFor)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="p-4">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Salary cap</p>
            <p className="mt-1 text-2xl font-bold tabular-nums">
              ${(currentLeague.salaryCap / 1_000_000).toFixed(0)}M
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Roster size</p>
            <p className="mt-1 text-2xl font-bold tabular-nums">{currentLeague.rosterSize}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Members</p>
            <p className="mt-1 text-2xl font-bold tabular-nums">
              {currentLeague.memberCount}
              <span className="text-base font-semibold text-muted-foreground">
                /{currentLeague.maxMembers}
              </span>
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function NoLeaguesEmptyState() {
  const [createOpen, setCreateOpen] = useState(false);
  const [joinOpen, setJoinOpen] = useState(false);
  return (
    <>
      <EmptyState
        icon={Trophy}
        title="You're not in a league yet"
        description="Create your own league or join an existing one to get started."
        action={
          <div className="flex flex-wrap justify-center gap-2">
            <Button onClick={() => setCreateOpen(true)}>Create a league</Button>
            <Button variant="outline" onClick={() => setJoinOpen(true)}>
              Join a league
            </Button>
          </div>
        }
      />
      <CreateLeagueDialog open={createOpen} onOpenChange={setCreateOpen} />
      <JoinLeagueDialog open={joinOpen} onOpenChange={setJoinOpen} />
    </>
  );
}

function CommissionerPanel({ league }: { league: LeagueResponse }) {
  const { selectLeagueAfterAction } = useLeague();
  const [copied, setCopied] = useState(false);
  const [busy, setBusy] = useState<"code" | "visibility" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const joinCode = league.joinCode ?? null;
  const isPublic = league.visibility === "PUBLIC";

  async function copyCode() {
    if (!joinCode) return;
    try {
      await navigator.clipboard.writeText(joinCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setError("Couldn't copy to clipboard");
    }
  }

  async function regenerate() {
    if (
      !window.confirm(
        "Regenerate the join code? The current code will stop working immediately."
      )
    ) {
      return;
    }
    setBusy("code");
    setError(null);
    try {
      const updated = await api.regenerateJoinCode(league.id);
      selectLeagueAfterAction(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't regenerate the code");
    } finally {
      setBusy(null);
    }
  }

  async function toggleVisibility() {
    setBusy("visibility");
    setError(null);
    try {
      const updated = await api.updateLeagueVisibility(
        league.id,
        isPublic ? "PRIVATE" : "PUBLIC"
      );
      selectLeagueAfterAction(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't update visibility");
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Crown className="h-4 w-4 text-amber-400" />
          Commissioner tools
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <div className="flex flex-col gap-2">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Join code
          </p>
          <div className="flex items-center gap-2">
            <code className="flex h-10 flex-1 items-center rounded-lg border border-border bg-secondary/40 px-3 font-mono text-sm tracking-widest">
              {joinCode ?? "Not generated yet"}
            </code>
            <Button
              variant="outline"
              size="icon"
              onClick={copyCode}
              disabled={!joinCode}
              title="Copy join code"
            >
              {copied ? <Check className="h-4 w-4 text-primary" /> : <Copy className="h-4 w-4" />}
            </Button>
            <Button
              variant="outline"
              size="icon"
              onClick={regenerate}
              disabled={busy === "code"}
              title="Regenerate join code"
            >
              {busy === "code" ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <RefreshCw className="h-4 w-4" />
              )}
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            Share this code so others can join a private league.
          </p>
        </div>

        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold">Visibility</p>
            <p className="text-xs text-muted-foreground">
              {isPublic
                ? "Anyone can find and join this league."
                : "Only people with the join code can join."}
            </p>
          </div>
          <Button variant="outline" onClick={toggleVisibility} disabled={busy === "visibility"}>
            {busy === "visibility" ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : isPublic ? (
              <Lock className="h-4 w-4" />
            ) : (
              <Globe className="h-4 w-4" />
            )}
            Make {isPublic ? "private" : "public"}
          </Button>
        </div>

        {error ? <p className="text-sm text-destructive">{error}</p> : null}
      </CardContent>
    </Card>
  );
}
