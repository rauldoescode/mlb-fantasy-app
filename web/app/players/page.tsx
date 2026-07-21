"use client";

import { useEffect, useState } from "react";
import { Search, Plus, Check, ShieldAlert, Users } from "lucide-react";

import { useLeague } from "@/lib/league-context";
import { api, ApiError, type PlayerResponse } from "@/lib/api";
import { formatCurrency } from "@/lib/format";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/empty-state";
import { cn } from "@/lib/utils";

const POSITIONS = ["ALL", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "P", "DH"];
const PAGE_SIZE = 20;

export default function PlayersPage() {
  const { currentLeague, leagues, isLoading: leaguesLoading } = useLeague();

  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [position, setPosition] = useState("ALL");
  const [page, setPage] = useState(0);
  const [players, setPlayers] = useState<PlayerResponse[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [addedIds, setAddedIds] = useState<Set<number>>(new Set());
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<{ type: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, position]);

  useEffect(() => {
    setPlayers(null);
    setError(null);
    api
      .players({
        search: debouncedSearch || undefined,
        position: position === "ALL" ? undefined : position,
        page,
        size: PAGE_SIZE,
      })
      .then((res) => {
        setPlayers(res.content);
        setTotalPages(res.totalPages);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load players"));
  }, [debouncedSearch, position, page]);

  async function handleAdd(player: PlayerResponse) {
    if (!currentLeague) return;
    setPendingId(player.mlbId);
    setFeedback(null);
    try {
      await api.addRosterPlayer(currentLeague.id, {
        playerId: player.mlbId,
        slotType: "BENCH",
        active: false,
      });
      setAddedIds((prev) => new Set(prev).add(player.mlbId));
      setFeedback({ type: "success", text: `Added ${player.fullName} to your bench.` });
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiError ? err.message : "Couldn't add that player",
      });
    } finally {
      setPendingId(null);
    }
  }

  if (!leaguesLoading && leagues.length === 0) {
    return (
      <EmptyState icon={Users} title="You're not in a league yet" description="Ask your commissioner for an invite." />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Players</h1>
        <p className="text-sm text-muted-foreground">Search and add players to your roster.</p>
      </div>

      <div className="flex flex-col gap-3">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search players…"
            className="pl-9"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          {POSITIONS.map((pos) => (
            <button
              key={pos}
              onClick={() => setPosition(pos)}
              className={cn(
                "rounded-full border border-border px-3 py-1 text-xs font-semibold transition-colors",
                position === pos
                  ? "border-primary bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:bg-secondary/60"
              )}
            >
              {pos}
            </button>
          ))}
        </div>
      </div>

      {feedback ? (
        <div
          className={cn(
            "rounded-lg border px-4 py-2.5 text-sm font-medium",
            feedback.type === "success"
              ? "border-primary/30 bg-primary/10 text-primary"
              : "border-destructive/30 bg-destructive/10 text-destructive"
          )}
        >
          {feedback.text}
        </div>
      ) : null}

      {error ? (
        <EmptyState icon={ShieldAlert} title="Couldn't load players" description={error} />
      ) : (
        <Card>
          <CardContent className="p-0">
            {!players ? (
              <div className="flex flex-col gap-2 p-5">
                {Array.from({ length: 8 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full rounded-lg" />
                ))}
              </div>
            ) : players.length === 0 ? (
              <p className="py-12 text-center text-sm text-muted-foreground">
                No players match your search.
              </p>
            ) : (
              <div className="divide-y divide-border/60">
                {players.map((player) => {
                  const isAdded = addedIds.has(player.mlbId);
                  return (
                    <div
                      key={player.mlbId}
                      className="flex items-center justify-between gap-3 px-5 py-3"
                    >
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <p className="truncate text-sm font-semibold">{player.fullName}</p>
                          {player.currentStatus && player.currentStatus !== "ACTIVE" ? (
                            <Badge variant="warning" className="text-[10px]">
                              {player.currentStatus}
                            </Badge>
                          ) : null}
                        </div>
                        <p className="text-xs text-muted-foreground">
                          {player.position ?? "—"} · {player.teamAbbrev ?? "FA"}
                        </p>
                      </div>
                      <p className="w-20 shrink-0 text-right text-sm font-semibold tabular-nums text-muted-foreground">
                        {formatCurrency(player.salary)}
                      </p>
                      <Button
                        size="sm"
                        variant={isAdded ? "secondary" : "outline"}
                        disabled={isAdded || pendingId === player.mlbId || !currentLeague}
                        onClick={() => handleAdd(player)}
                      >
                        {isAdded ? <Check className="h-3.5 w-3.5" /> : <Plus className="h-3.5 w-3.5" />}
                        {isAdded ? "Added" : "Add"}
                      </Button>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {players && players.length > 0 ? (
        <div className="flex items-center justify-center gap-3">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <span className="text-xs text-muted-foreground">
            Page {page + 1} of {Math.max(totalPages, 1)}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      ) : null}
    </div>
  );
}
