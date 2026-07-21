"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Trophy, Swords, Lock, ArrowRight, ShieldAlert } from "lucide-react";

import { useAuth } from "@/lib/auth-context";
import { useLeague } from "@/lib/league-context";
import { api, type MatchupResponse, type RosterResponse, type StandingRow } from "@/lib/api";
import { pickCurrentMatchup, opponentIdFor, scoresFor } from "@/lib/matchup-utils";
import { formatPoints } from "@/lib/format";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { UserAvatar } from "@/components/user-avatar";
import { EmptyState } from "@/components/empty-state";
import { Progress } from "@/components/ui/progress";

export default function DashboardPage() {
  const { user } = useAuth();
  const { currentLeague, leagues, isLoading: leaguesLoading } = useLeague();

  const [standings, setStandings] = useState<StandingRow[] | null>(null);
  const [matchups, setMatchups] = useState<MatchupResponse[] | null>(null);
  const [roster, setRoster] = useState<RosterResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!currentLeague) return;
    setStandings(null);
    setMatchups(null);
    setRoster(null);
    setError(null);
    Promise.all([
      api.standings(currentLeague.id),
      api.matchups(currentLeague.id),
      api.roster(currentLeague.id),
    ])
      .then(([s, m, r]) => {
        setStandings(s);
        setMatchups(m);
        setRoster(r);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load dashboard"));
  }, [currentLeague]);

  if (!leaguesLoading && leagues.length === 0) {
    return (
      <EmptyState
        icon={Trophy}
        title="You're not in a league yet"
        description="Ask your commissioner to add your email to their league to get started."
      />
    );
  }

  if (leaguesLoading || !currentLeague || !user) {
    return <DashboardSkeleton />;
  }

  if (error) {
    return <EmptyState icon={ShieldAlert} title="Couldn't load your dashboard" description={error} />;
  }

  if (!standings || !matchups || !roster) {
    return <DashboardSkeleton />;
  }

  const myStanding = standings.find((s) => s.userId === user.id);
  const matchup = pickCurrentMatchup(matchups, user.id);
  const opponentId = matchup ? opponentIdFor(matchup, user.id) : null;
  const opponentStanding = standings.find((s) => s.userId === opponentId);
  const { myScore, opponentScore } = matchup
    ? scoresFor(matchup, user.id)
    : { myScore: null, opponentScore: null };

  const activeSlots = roster.slots.filter((s) => s.active);
  const lockedActive = activeSlots.filter((s) => s.locked);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <p className="text-sm font-medium text-muted-foreground">
          {currentLeague.name} · {currentLeague.seasonYear}
        </p>
        <h1 className="text-2xl font-bold tracking-tight">
          Hey {user.displayName.split(" ")[0]} ⚾️
        </h1>
      </div>

      {matchup ? (
        <MatchupHero
          weekNumber={matchup.weekNumber}
          status={matchup.status}
          myName={myStanding?.teamName ?? user.displayName}
          myAvatarUrl={user.avatarUrl}
          myScore={myScore}
          opponentName={opponentStanding?.teamName ?? opponentStanding?.displayName ?? "TBD"}
          opponentScore={opponentScore}
        />
      ) : (
        <EmptyState
          icon={Swords}
          title="No matchup this week"
          description="You may have a bye this week, or your league needs at least two teams to schedule matchups."
        />
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label="Season record"
          value={
            myStanding ? `${myStanding.wins}-${myStanding.losses}-${myStanding.ties}` : "0-0-0"
          }
        />
        <StatCard label="Points for" value={formatPoints(myStanding?.pointsFor)} />
        <StatCard
          label="Salary remaining"
          value={`$${(roster.salaryRemaining / 1_000_000).toFixed(1)}M`}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle className="text-base">Today&apos;s lineup</CardTitle>
            <Link
              href="/roster"
              className="flex items-center gap-1 text-xs font-semibold text-primary hover:underline"
            >
              Manage <ArrowRight className="h-3 w-3" />
            </Link>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {activeSlots.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">
                No active players set yet.
              </p>
            ) : (
              activeSlots.slice(0, 6).map((slot) => (
                <div
                  key={slot.slotId}
                  className="flex items-center justify-between rounded-lg bg-secondary/30 px-3 py-2"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold">{slot.playerName}</p>
                    <p className="text-xs text-muted-foreground">
                      {slot.position} · {slot.teamAbbrev}
                    </p>
                  </div>
                  {slot.locked ? (
                    <Badge variant="warning">
                      <Lock className="h-3 w-3" /> Locked
                    </Badge>
                  ) : (
                    <Badge variant="success">Active</Badge>
                  )}
                </div>
              ))
            )}
            {lockedActive.length > 0 ? (
              <p className="pt-1 text-xs text-muted-foreground">
                {lockedActive.length} of your active players have locked for the day.
              </p>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle className="text-base">Standings</CardTitle>
            <Link
              href="/league"
              className="flex items-center gap-1 text-xs font-semibold text-primary hover:underline"
            >
              Full table <ArrowRight className="h-3 w-3" />
            </Link>
          </CardHeader>
          <CardContent className="flex flex-col gap-1">
            {standings.slice(0, 5).map((row, i) => (
              <div
                key={row.userId}
                className="flex items-center gap-3 rounded-lg px-2 py-2 text-sm"
              >
                <span className="w-4 text-xs font-semibold text-muted-foreground">{i + 1}</span>
                <UserAvatar name={row.displayName} size="sm" />
                <span className="flex-1 truncate font-medium">{row.teamName}</span>
                <span className="tabular-nums text-xs font-semibold text-muted-foreground">
                  {row.wins}-{row.losses}-{row.ties}
                </span>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function MatchupHero({
  weekNumber,
  status,
  myName,
  myAvatarUrl,
  myScore,
  opponentName,
  opponentScore,
}: {
  weekNumber: number;
  status: string;
  myName: string;
  myAvatarUrl?: string | null;
  myScore: number | null;
  opponentName: string;
  opponentScore: number | null;
}) {
  const total = (myScore ?? 0) + (opponentScore ?? 0);
  const myShare = total > 0 ? ((myScore ?? 0) / total) * 100 : 50;
  const isWinning = (myScore ?? 0) >= (opponentScore ?? 0);

  return (
    <Card className="overflow-hidden border-primary/20 bg-gradient-to-br from-accent/40 via-card to-card">
      <CardContent className="flex flex-col gap-5 p-6">
        <div className="flex items-center justify-between">
          <Badge variant="secondary">Week {weekNumber}</Badge>
          <Badge variant={status === "IN_PROGRESS" ? "success" : status === "FINAL" ? "outline" : "muted"}>
            {status === "IN_PROGRESS" ? "Live" : status === "FINAL" ? "Final" : "Upcoming"}
          </Badge>
        </div>

        <div className="flex items-center justify-between gap-4">
          <div className="flex flex-1 flex-col items-center gap-2 text-center">
            <UserAvatar name={myName} avatarUrl={myAvatarUrl} size="lg" />
            <p className="max-w-[8rem] truncate text-sm font-semibold">{myName}</p>
            <p
              className={`text-3xl font-bold tabular-nums ${isWinning ? "text-primary" : "text-foreground"}`}
            >
              {formatPoints(myScore)}
            </p>
          </div>

          <span className="text-sm font-bold text-muted-foreground">VS</span>

          <div className="flex flex-1 flex-col items-center gap-2 text-center">
            <UserAvatar name={opponentName} size="lg" />
            <p className="max-w-[8rem] truncate text-sm font-semibold">{opponentName}</p>
            <p
              className={`text-3xl font-bold tabular-nums ${!isWinning ? "text-primary" : "text-foreground"}`}
            >
              {formatPoints(opponentScore)}
            </p>
          </div>
        </div>

        <Progress value={myShare} />

        <Link
          href="/matchup"
          className="flex items-center justify-center gap-1 text-sm font-semibold text-primary hover:underline"
        >
          View full breakdown <ArrowRight className="h-3.5 w-3.5" />
        </Link>
      </CardContent>
    </Card>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardContent className="p-4">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {label}
        </p>
        <p className="mt-1 text-2xl font-bold tabular-nums">{value}</p>
      </CardContent>
    </Card>
  );
}

function DashboardSkeleton() {
  return (
    <div className="flex flex-col gap-6">
      <Skeleton className="h-8 w-56" />
      <Skeleton className="h-56 w-full rounded-xl" />
      <div className="grid gap-4 sm:grid-cols-3">
        <Skeleton className="h-20 rounded-xl" />
        <Skeleton className="h-20 rounded-xl" />
        <Skeleton className="h-20 rounded-xl" />
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <Skeleton className="h-64 rounded-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    </div>
  );
}
