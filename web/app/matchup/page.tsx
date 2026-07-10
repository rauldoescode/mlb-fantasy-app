"use client";

import { useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Swords, ShieldAlert } from "lucide-react";

import { useAuth } from "@/lib/auth-context";
import { useLeague } from "@/lib/league-context";
import { api, type MatchupDetailResponse, type MatchupResponse, type StandingRow } from "@/lib/api";
import { pickCurrentMatchup } from "@/lib/matchup-utils";
import { formatPoints, formatCategoryLabel } from "@/lib/format";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { UserAvatar } from "@/components/user-avatar";
import { EmptyState } from "@/components/empty-state";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";

export default function MatchupPage() {
  const { user } = useAuth();
  const { currentLeague, leagues, isLoading: leaguesLoading } = useLeague();

  const [matchups, setMatchups] = useState<MatchupResponse[] | null>(null);
  const [standings, setStandings] = useState<StandingRow[] | null>(null);
  const [week, setWeek] = useState<number | null>(null);
  const [detail, setDetail] = useState<MatchupDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);

  useEffect(() => {
    if (!currentLeague || !user) return;
    setError(null);
    Promise.all([api.matchups(currentLeague.id), api.standings(currentLeague.id)])
      .then(([m, s]) => {
        setMatchups(m);
        setStandings(s);
        const current = pickCurrentMatchup(m, user.id);
        setWeek(current?.weekNumber ?? null);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load matchups"));
  }, [currentLeague, user]);

  const activeMatchup = useMemo(() => {
    if (!matchups || !user || week == null) return null;
    return (
      matchups.find(
        (m) => m.weekNumber === week && (m.userOneId === user.id || m.userTwoId === user.id)
      ) ?? null
    );
  }, [matchups, user, week]);

  useEffect(() => {
    if (!activeMatchup) {
      setDetail(null);
      return;
    }
    setIsDetailLoading(true);
    api
      .matchupDetail(activeMatchup.id)
      .then(setDetail)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load matchup"))
      .finally(() => setIsDetailLoading(false));
  }, [activeMatchup]);

  if (!leaguesLoading && leagues.length === 0) {
    return (
      <EmptyState icon={Swords} title="You're not in a league yet" description="Ask your commissioner for an invite." />
    );
  }

  if (leaguesLoading || !currentLeague || !user || !matchups || !standings) {
    return <MatchupSkeleton />;
  }

  if (error) {
    return <EmptyState icon={ShieldAlert} title="Couldn't load matchup" description={error} />;
  }

  const nameFor = (userId: string) =>
    standings.find((s) => s.userId === userId)?.teamName ??
    standings.find((s) => s.userId === userId)?.displayName ??
    "TBD";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Matchup</h1>
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="icon"
            onClick={() => setWeek((w) => Math.max(1, (w ?? 1) - 1))}
            disabled={week == null || week <= 1}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="w-20 text-center text-sm font-semibold">Week {week ?? "—"}</span>
          <Button variant="outline" size="icon" onClick={() => setWeek((w) => (w ?? 1) + 1)}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {!activeMatchup ? (
        <EmptyState icon={Swords} title="No matchup this week" description="Nothing scheduled for this week yet." />
      ) : isDetailLoading || !detail ? (
        <MatchupSkeleton />
      ) : (
        <>
          <Card className="overflow-hidden border-primary/20 bg-gradient-to-br from-accent/40 via-card to-card">
            <CardContent className="flex flex-col gap-5 p-6">
              <div className="flex items-center justify-between">
                <Badge variant="secondary">
                  {new Date(detail.weekStart).toLocaleDateString(undefined, { month: "short", day: "numeric" })} –{" "}
                  {new Date(detail.weekEnd).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
                </Badge>
                <Badge
                  variant={
                    detail.matchup.status === "IN_PROGRESS"
                      ? "success"
                      : detail.matchup.status === "FINAL"
                        ? "outline"
                        : "muted"
                  }
                >
                  {detail.matchup.status === "IN_PROGRESS"
                    ? "Live"
                    : detail.matchup.status === "FINAL"
                      ? "Final"
                      : "Upcoming"}
                </Badge>
              </div>

              <div className="flex items-center justify-between gap-4">
                <TeamScore
                  name={nameFor(detail.matchup.userOneId)}
                  avatarUrl={detail.matchup.userOneId === user.id ? user.avatarUrl : null}
                  score={detail.userOneBreakdown.totalPoints}
                  winner={detail.matchup.winnerId === detail.matchup.userOneId}
                />
                <span className="text-sm font-bold text-muted-foreground">VS</span>
                <TeamScore
                  name={nameFor(detail.matchup.userTwoId)}
                  avatarUrl={detail.matchup.userTwoId === user.id ? user.avatarUrl : null}
                  score={detail.userTwoBreakdown.totalPoints}
                  winner={detail.matchup.winnerId === detail.matchup.userTwoId}
                />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Category breakdown</CardTitle>
            </CardHeader>
            <CardContent>
              <CategoryTable
                left={detail.userOneBreakdown.categoryPoints}
                right={detail.userTwoBreakdown.categoryPoints}
              />
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

function TeamScore({
  name,
  avatarUrl,
  score,
  winner,
}: {
  name: string;
  avatarUrl?: string | null;
  score: number;
  winner: boolean;
}) {
  return (
    <div className="flex flex-1 flex-col items-center gap-2 text-center">
      <UserAvatar name={name} avatarUrl={avatarUrl} size="lg" />
      <p className="max-w-[9rem] truncate text-sm font-semibold">{name}</p>
      <p className={cn("text-3xl font-bold tabular-nums", winner && "text-primary")}>
        {formatPoints(score)}
      </p>
    </div>
  );
}

function CategoryTable({
  left,
  right,
}: {
  left: Record<string, number>;
  right: Record<string, number>;
}) {
  const categories = Array.from(new Set([...Object.keys(left), ...Object.keys(right)])).sort();

  if (categories.length === 0) {
    return <p className="py-6 text-center text-sm text-muted-foreground">No stats recorded yet this week.</p>;
  }

  return (
    <div className="flex flex-col">
      {categories.map((key, i) => {
        const l = left[key] ?? 0;
        const r = right[key] ?? 0;
        return (
          <div key={key}>
            {i > 0 ? <Separator /> : null}
            <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 py-2.5">
              <span
                className={cn(
                  "text-right text-sm font-semibold tabular-nums",
                  l > r && "text-primary"
                )}
              >
                {l.toFixed(1)}
              </span>
              <span className="w-32 text-center text-xs font-medium text-muted-foreground">
                {formatCategoryLabel(key)}
              </span>
              <span
                className={cn(
                  "text-left text-sm font-semibold tabular-nums",
                  r > l && "text-primary"
                )}
              >
                {r.toFixed(1)}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function MatchupSkeleton() {
  return (
    <div className="flex flex-col gap-6">
      <Skeleton className="h-56 w-full rounded-xl" />
      <Skeleton className="h-72 w-full rounded-xl" />
    </div>
  );
}
