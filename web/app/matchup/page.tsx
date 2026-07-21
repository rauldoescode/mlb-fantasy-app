"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Swords, ShieldAlert } from "lucide-react";

import { useAuth } from "@/lib/auth-context";
import { useLeague } from "@/lib/league-context";
import {
  api,
  ApiError,
  type LineupPlayerCard,
  type MatchupDetailResponse,
  type MatchupLineupSide,
  type MatchupResponse,
  type SeasonResponse,
  type StandingRow,
} from "@/lib/api";
import { pickCurrentMatchup } from "@/lib/matchup-utils";
import { formatPoints, formatCategoryLabel } from "@/lib/format";
import { MatchupLineupCard, busyKeyFor } from "@/components/matchup-lineup-card";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { UserAvatar } from "@/components/user-avatar";
import { EmptyState } from "@/components/empty-state";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";

export default function MatchupPage() {
  const { user } = useAuth();
  const { currentLeague, leagues, isLoading: leaguesLoading } = useLeague();

  const [matchups, setMatchups] = useState<MatchupResponse[] | null>(null);
  const [standings, setStandings] = useState<StandingRow[] | null>(null);
  const [season, setSeason] = useState<SeasonResponse | null>(null);
  const [week, setWeek] = useState<number | null>(null);
  const [detail, setDetail] = useState<MatchupDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (!currentLeague || !user) return;
    setError(null);
    Promise.all([
      api.matchups(currentLeague.id),
      api.standings(currentLeague.id),
      api.season(),
    ])
      .then(([m, s, seasonInfo]) => {
        setMatchups(m);
        setStandings(s);
        setSeason(seasonInfo);
        const current = pickCurrentMatchup(m, user.id);
        setWeek(current?.weekNumber ?? seasonInfo.currentWeek);
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

  const reloadDetail = useCallback(async () => {
    if (!activeMatchup) {
      setDetail(null);
      return;
    }
    const next = await api.matchupDetail(activeMatchup.id);
    setDetail(next);
    setMatchups((prev) =>
      prev
        ? prev.map((m) => (m.id === next.matchup.id ? next.matchup : m))
        : prev
    );
  }, [activeMatchup]);

  useEffect(() => {
    if (!activeMatchup) {
      setDetail(null);
      return;
    }
    setIsDetailLoading(true);
    setActionError(null);
    api
      .matchupDetail(activeMatchup.id)
      .then(setDetail)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load matchup"))
      .finally(() => setIsDetailLoading(false));
  }, [activeMatchup]);

  async function handleToggleActive(player: LineupPlayerCard, active: boolean) {
    if (!detail || !player.slotId) return;
    setBusyKey(busyKeyFor(player));
    setActionError(null);
    try {
      await api.setMatchupLineup(detail.matchup.id, player.slotId, active);
      await reloadDetail();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't update lineup");
    } finally {
      setBusyKey(null);
    }
  }

  async function handleLockBest(player: LineupPlayerCard) {
    if (!detail || !currentLeague) return;
    setBusyKey(busyKeyFor(player));
    setActionError(null);
    try {
      await api.lockPerformance(
        currentLeague.id,
        detail.matchup.weekNumber,
        player.playerId
      );
      await reloadDetail();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't lock that performance");
    } finally {
      setBusyKey(null);
    }
  }

  async function handleUnlock(player: LineupPlayerCard) {
    if (!detail || !currentLeague) return;
    setBusyKey(busyKeyFor(player));
    setActionError(null);
    try {
      await api.unlockPerformance(
        currentLeague.id,
        detail.matchup.weekNumber,
        player.playerId
      );
      await reloadDetail();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't unlock that performance");
    } finally {
      setBusyKey(null);
    }
  }

  if (!leaguesLoading && leagues.length === 0) {
    return (
      <EmptyState icon={Swords} title="You're not in a league yet" description="Ask your commissioner for an invite." />
    );
  }

  if (leaguesLoading || !currentLeague || !user || !matchups || !standings || !season) {
    return <MatchupSkeleton />;
  }

  const totalWeeks = season.totalWeeks;

  if (error) {
    return <EmptyState icon={ShieldAlert} title="Couldn't load matchup" description={error} />;
  }

  const oriented = detail ? orientSides(detail, user.id) : null;

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
          <span className="w-24 text-center text-sm font-semibold">
            Week {week ?? "—"}
            <span className="text-muted-foreground"> / {totalWeeks}</span>
          </span>
          <Button
            variant="outline"
            size="icon"
            onClick={() => setWeek((w) => Math.min(totalWeeks, (w ?? 1) + 1))}
            disabled={week == null || week >= totalWeeks}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {!activeMatchup ? (
        <EmptyState icon={Swords} title="No matchup this week" description="Nothing scheduled for this week yet." />
      ) : isDetailLoading || !detail || !oriented ? (
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
                  name={oriented.leftLabel}
                  avatarUrl={oriented.leftUserId === user.id ? user.avatarUrl : null}
                  score={oriented.leftBreakdown.totalPoints}
                  winner={detail.matchup.winnerId === oriented.leftUserId}
                />
                <span className="text-sm font-bold text-muted-foreground">VS</span>
                <TeamScore
                  name={oriented.rightLabel}
                  avatarUrl={oriented.rightUserId === user.id ? user.avatarUrl : null}
                  score={oriented.rightBreakdown.totalPoints}
                  winner={detail.matchup.winnerId === oriented.rightUserId}
                />
              </div>
            </CardContent>
          </Card>

          {actionError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-2.5 text-sm font-medium text-destructive">
              {actionError}
            </div>
          ) : null}

          {!detail.lineupEditable && detail.matchup.status !== "FINAL" ? (
            <p className="text-center text-xs text-muted-foreground">
              Lineup edits are only available for the current open week.
            </p>
          ) : null}

          <Tabs defaultValue="lineups">
            <TabsList className="w-full sm:w-auto">
              <TabsTrigger value="lineups" className="flex-1 sm:flex-none">
                Lineups
              </TabsTrigger>
              <TabsTrigger value="categories" className="flex-1 sm:flex-none">
                Categories
              </TabsTrigger>
            </TabsList>
            <TabsContent value="lineups" className="mt-4">
              <MatchupLineupCard
                left={oriented.leftLineup}
                right={oriented.rightLineup}
                leftEditable={oriented.leftEditable}
                rightEditable={oriented.rightEditable}
                busyKey={busyKey}
                onToggleActive={handleToggleActive}
                onLockBest={handleLockBest}
                onUnlock={handleUnlock}
              />
            </TabsContent>
            <TabsContent value="categories" className="mt-4">
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Category breakdown</CardTitle>
                </CardHeader>
                <CardContent>
                  <CategoryTable
                    left={oriented.leftBreakdown.categoryPoints}
                    right={oriented.rightBreakdown.categoryPoints}
                    leftName={oriented.leftLabel}
                    rightName={oriented.rightLabel}
                  />
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </>
      )}
    </div>
  );
}

/** Put the viewer's team on the left when they are a participant. */
function emptySide(userId: string | null | undefined): MatchupLineupSide {
  return {
    userId: userId ?? "",
    teamName: "",
    starters: [],
    bench: [],
    totalPoints: 0,
  };
}

function orientSides(detail: MatchupDetailResponse, userId: string) {
  const youAreOne = detail.matchup.userOneId === userId;
  const youAreTwo = detail.matchup.userTwoId === userId;
  const swap = youAreTwo;
  const isParticipant = youAreOne || youAreTwo;

  const oneSide = detail.userOneLineup ?? emptySide(detail.matchup.userOneId);
  const twoSide = detail.userTwoLineup ?? emptySide(detail.matchup.userTwoId);

  const leftLineup = swap ? twoSide : oneSide;
  const rightLineup = swap ? oneSide : twoSide;
  const leftBreakdown = swap ? detail.userTwoBreakdown : detail.userOneBreakdown;
  const rightBreakdown = swap ? detail.userOneBreakdown : detail.userTwoBreakdown;
  const leftUserId = swap ? detail.matchup.userTwoId : detail.matchup.userOneId;
  const rightUserId = swap ? detail.matchup.userOneId : detail.matchup.userTwoId;

  const leftEditable = detail.lineupEditable && isParticipant && leftUserId === userId;
  const rightEditable = detail.lineupEditable && isParticipant && rightUserId === userId;

  const leftLabel =
    isParticipant && leftUserId === userId ? "You" : leftLineup.teamName || "Opponent";
  const rightLabel =
    isParticipant && rightUserId === userId ? "You" : rightLineup.teamName || "Opponent";

  return {
    leftLineup: { ...leftLineup, teamName: leftLabel },
    rightLineup: { ...rightLineup, teamName: rightLabel },
    leftBreakdown,
    rightBreakdown,
    leftUserId,
    rightUserId,
    leftLabel,
    rightLabel,
    leftEditable,
    rightEditable,
  };
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
  leftName,
  rightName,
}: {
  left: Record<string, number>;
  right: Record<string, number>;
  leftName: string;
  rightName: string;
}) {
  const categories = Array.from(new Set([...Object.keys(left), ...Object.keys(right)])).sort();

  if (categories.length === 0) {
    return <p className="py-6 text-center text-sm text-muted-foreground">No stats recorded yet this week.</p>;
  }

  return (
    <div className="flex flex-col">
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 pb-2 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
        <span className="truncate text-right">{leftName}</span>
        <span className="w-32 text-center">Stat</span>
        <span className="truncate text-left">{rightName}</span>
      </div>
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
      <Skeleton className="h-10 w-48 rounded-lg" />
      <div className="grid gap-4 lg:grid-cols-2">
        <Skeleton className="h-72 w-full rounded-xl" />
        <Skeleton className="h-72 w-full rounded-xl" />
      </div>
    </div>
  );
}
