"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Lock, Trash2, ArrowLeftRight, Plus, ShieldAlert, ClipboardList } from "lucide-react";

import { useLeague } from "@/lib/league-context";
import { api, ApiError, type RosterResponse, type RosterSlotResponse } from "@/lib/api";
import { formatCurrency } from "@/lib/format";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/empty-state";

export default function RosterPage() {
  const { currentLeague, leagues, isLoading: leaguesLoading } = useLeague();
  const [roster, setRoster] = useState<RosterResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busySlotId, setBusySlotId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  function load() {
    if (!currentLeague) return;
    setError(null);
    api
      .roster(currentLeague.id)
      .then(setRoster)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load roster"));
  }

  useEffect(() => {
    setRoster(null);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentLeague]);

  async function toggleActive(slot: RosterSlotResponse) {
    setBusySlotId(slot.slotId);
    setActionError(null);
    try {
      await api.updateRosterSlot(slot.slotId, { active: !slot.active });
      load();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't update that slot");
    } finally {
      setBusySlotId(null);
    }
  }

  async function removePlayer(slot: RosterSlotResponse) {
    setBusySlotId(slot.slotId);
    setActionError(null);
    try {
      await api.removeRosterSlot(slot.slotId);
      load();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't drop that player");
    } finally {
      setBusySlotId(null);
    }
  }

  if (!leaguesLoading && leagues.length === 0) {
    return (
      <EmptyState icon={ClipboardList} title="You're not in a league yet" description="Ask your commissioner for an invite." />
    );
  }

  if (leaguesLoading || !currentLeague) {
    return <RosterSkeleton />;
  }

  if (error) {
    return <EmptyState icon={ShieldAlert} title="Couldn't load roster" description={error} />;
  }

  if (!roster) {
    return <RosterSkeleton />;
  }

  const activeSlots = roster.slots.filter((s) => s.active);
  const benchSlots = roster.slots.filter((s) => !s.active);
  const capPct = roster.salaryCap > 0 ? (roster.totalSalary / roster.salaryCap) * 100 : 0;
  const isFull = roster.slots.length >= currentLeague.rosterSize;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Roster</h1>
          <p className="text-sm text-muted-foreground">
            {roster.slots.length} / {currentLeague.rosterSize} players
          </p>
        </div>
        {!isFull ? (
          <Button asChild size="sm">
            <Link href="/players">
              <Plus className="h-4 w-4" /> Add player
            </Link>
          </Button>
        ) : null}
      </div>

      <Card>
        <CardContent className="flex flex-col gap-2 p-4">
          <div className="flex items-center justify-between text-sm">
            <span className="font-semibold">Salary cap</span>
            <span className="tabular-nums text-muted-foreground">
              {formatCurrency(roster.totalSalary)} / {formatCurrency(roster.salaryCap)}
            </span>
          </div>
          <Progress value={capPct} indicatorClassName={capPct > 95 ? "bg-destructive" : undefined} />
          <p className="text-xs text-muted-foreground">
            {formatCurrency(roster.salaryRemaining)} remaining
          </p>
        </CardContent>
      </Card>

      {actionError ? (
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-2.5 text-sm font-medium text-destructive">
          {actionError}
        </div>
      ) : null}

      <RosterSection
        title="Active lineup"
        slots={activeSlots}
        emptyText="No active players. Move someone up from the bench."
        busySlotId={busySlotId}
        onToggleActive={toggleActive}
        onRemove={removePlayer}
      />

      <RosterSection
        title="Bench"
        slots={benchSlots}
        emptyText="Your bench is empty."
        busySlotId={busySlotId}
        onToggleActive={toggleActive}
        onRemove={removePlayer}
      />
    </div>
  );
}

function RosterSection({
  title,
  slots,
  emptyText,
  busySlotId,
  onToggleActive,
  onRemove,
}: {
  title: string;
  slots: RosterSlotResponse[];
  emptyText: string;
  busySlotId: string | null;
  onToggleActive: (slot: RosterSlotResponse) => void;
  onRemove: (slot: RosterSlotResponse) => void;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">
          {title} <span className="font-normal text-muted-foreground">({slots.length})</span>
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        {slots.length === 0 ? (
          <p className="px-5 pb-5 text-sm text-muted-foreground">{emptyText}</p>
        ) : (
          <div className="divide-y divide-border/60">
            {slots.map((slot) => (
              <div key={slot.slotId} className="flex items-center justify-between gap-3 px-5 py-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold">{slot.playerName ?? "Unknown player"}</p>
                  <p className="text-xs text-muted-foreground">
                    {slot.position ?? "—"} · {slot.teamAbbrev ?? "FA"} ·{" "}
                    {formatCurrency(slot.salary)}
                  </p>
                </div>
                {slot.locked ? (
                  <Badge variant="warning">
                    <Lock className="h-3 w-3" /> Locked
                  </Badge>
                ) : null}
                <div className="flex shrink-0 items-center gap-1.5">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={slot.locked || busySlotId === slot.slotId}
                    onClick={() => onToggleActive(slot)}
                  >
                    <ArrowLeftRight className="h-3.5 w-3.5" />
                    {slot.active ? "Bench" : "Start"}
                  </Button>
                  <Button
                    size="icon"
                    variant="ghost"
                    disabled={slot.locked || busySlotId === slot.slotId}
                    onClick={() => onRemove(slot)}
                    className="text-destructive hover:bg-destructive/10"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function RosterSkeleton() {
  return (
    <div className="flex flex-col gap-6">
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-20 w-full rounded-xl" />
      <Skeleton className="h-56 w-full rounded-xl" />
      <Skeleton className="h-56 w-full rounded-xl" />
    </div>
  );
}
