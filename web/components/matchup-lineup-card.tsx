"use client";

import { Lock, ArrowLeftRight, Pin } from "lucide-react";

import type { LineupPlayerCard, MatchupLineupSide } from "@/lib/api";
import { formatPoints } from "@/lib/format";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

type BusyKey = string | null;

export function MatchupLineupCard({
  left,
  right,
  leftEditable,
  rightEditable,
  busyKey,
  onToggleActive,
  onLockBest,
  onUnlock,
}: {
  left?: MatchupLineupSide | null;
  right?: MatchupLineupSide | null;
  leftEditable: boolean;
  rightEditable: boolean;
  busyKey: BusyKey;
  onToggleActive: (player: LineupPlayerCard, active: boolean) => void;
  onLockBest: (player: LineupPlayerCard) => void;
  onUnlock: (player: LineupPlayerCard) => void;
}) {
  const leftSide = left ?? {
    userId: "",
    teamName: "TBD",
    starters: [],
    bench: [],
    totalPoints: 0,
  };
  const rightSide = right ?? {
    userId: "",
    teamName: "TBD",
    starters: [],
    bench: [],
    totalPoints: 0,
  };

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <TeamLineupColumn
        side={leftSide}
        editable={leftEditable}
        busyKey={busyKey}
        onToggleActive={onToggleActive}
        onLockBest={onLockBest}
        onUnlock={onUnlock}
      />
      <TeamLineupColumn
        side={rightSide}
        editable={rightEditable}
        busyKey={busyKey}
        onToggleActive={onToggleActive}
        onLockBest={onLockBest}
        onUnlock={onUnlock}
      />
    </div>
  );
}

function TeamLineupColumn({
  side,
  editable,
  busyKey,
  onToggleActive,
  onLockBest,
  onUnlock,
}: {
  side: MatchupLineupSide;
  editable: boolean;
  busyKey: BusyKey;
  onToggleActive: (player: LineupPlayerCard, active: boolean) => void;
  onLockBest: (player: LineupPlayerCard) => void;
  onUnlock: (player: LineupPlayerCard) => void;
}) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="truncate text-base">{side.teamName}</CardTitle>
          <span className="shrink-0 text-sm font-semibold tabular-nums text-muted-foreground">
            {formatPoints(side.totalPoints)} pts
          </span>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-5 p-0 pb-4">
        <LineupSection
          title="Starters"
          players={side.starters}
          emptyText="No starters set."
          editable={editable}
          busyKey={busyKey}
          onToggleActive={onToggleActive}
          onLockBest={onLockBest}
          onUnlock={onUnlock}
        />
        <LineupSection
          title="Bench"
          players={side.bench}
          emptyText="Bench is empty."
          editable={editable}
          busyKey={busyKey}
          onToggleActive={onToggleActive}
          onLockBest={onLockBest}
          onUnlock={onUnlock}
        />
      </CardContent>
    </Card>
  );
}

function LineupSection({
  title,
  players,
  emptyText,
  editable,
  busyKey,
  onToggleActive,
  onLockBest,
  onUnlock,
}: {
  title: string;
  players: LineupPlayerCard[];
  emptyText: string;
  editable: boolean;
  busyKey: BusyKey;
  onToggleActive: (player: LineupPlayerCard, active: boolean) => void;
  onLockBest: (player: LineupPlayerCard) => void;
  onUnlock: (player: LineupPlayerCard) => void;
}) {
  return (
    <div>
      <p className="px-5 pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {title}{" "}
        <span className="font-normal normal-case tracking-normal">({players.length})</span>
      </p>
      {players.length === 0 ? (
        <p className="px-5 text-sm text-muted-foreground">{emptyText}</p>
      ) : (
        <div className="divide-y divide-border/60 border-t border-border/60">
          {players.map((player) => (
            <PlayerRow
              key={`${player.playerId}-${player.slotId ?? "snap"}`}
              player={player}
              editable={editable}
              busy={busyKey === busyKeyFor(player)}
              onToggleActive={onToggleActive}
              onLockBest={onLockBest}
              onUnlock={onUnlock}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function PlayerRow({
  player,
  editable,
  busy,
  onToggleActive,
  onLockBest,
  onUnlock,
}: {
  player: LineupPlayerCard;
  editable: boolean;
  busy: boolean;
  onToggleActive: (player: LineupPlayerCard, active: boolean) => void;
  onLockBest: (player: LineupPlayerCard) => void;
  onUnlock: (player: LineupPlayerCard) => void;
}) {
  const canEditSlot = editable && !!player.slotId && !player.locked;
  const hasEligibleGame = player.games.some((g) => g.eligible);
  const canLock = editable && player.active && hasEligibleGame && !player.locked;

  return (
    <div className="flex flex-col gap-2 px-5 py-3 sm:flex-row sm:items-center sm:justify-between sm:gap-3">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate text-sm font-semibold">{player.fullName ?? "Unknown"}</p>
          {player.locked ? (
            <Badge variant="warning" className="text-[10px]">
              <Lock className="h-3 w-3" /> Game locked
            </Badge>
          ) : null}
          {player.performanceLocked ? (
            <Badge variant="secondary" className="text-[10px]">
              <Pin className="h-3 w-3" /> Best locked
            </Badge>
          ) : null}
        </div>
        <p className="text-xs text-muted-foreground">
          {player.position ?? "—"} · {player.teamAbbrev ?? "FA"}
        </p>
      </div>

      <div className="flex shrink-0 items-center justify-between gap-3 sm:justify-end">
        <div className="text-right">
          <p className="text-sm font-semibold tabular-nums">{formatPoints(player.weekPoints)}</p>
          {!player.performanceLocked && player.bestGamePoints > 0 ? (
            <p className="text-[10px] text-muted-foreground tabular-nums">
              best {formatPoints(player.bestGamePoints)}
            </p>
          ) : null}
        </div>

        {editable ? (
          <div className="flex items-center gap-1.5">
            {player.active && (canLock || player.performanceLocked) ? (
              <Button
                size="sm"
                variant="ghost"
                disabled={busy || (!canLock && !player.performanceLocked)}
                onClick={() =>
                  player.performanceLocked ? onUnlock(player) : onLockBest(player)
                }
                className={cn("text-xs", !hasEligibleGame && !player.performanceLocked && "invisible")}
              >
                <Pin className="h-3.5 w-3.5" />
                {player.performanceLocked ? "Unlock" : "Lock best"}
              </Button>
            ) : null}
            <Button
              size="sm"
              variant="outline"
              disabled={!canEditSlot || busy}
              onClick={() => onToggleActive(player, !player.active)}
            >
              <ArrowLeftRight className="h-3.5 w-3.5" />
              {player.active ? "Bench" : "Start"}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  );
}

export function busyKeyFor(player: LineupPlayerCard) {
  return player.slotId ?? `player-${player.playerId}`;
}
