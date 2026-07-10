"use client";

import { useEffect, useState } from "react";
import { ArrowLeft, Loader2, Users } from "lucide-react";

import { api, ApiError, type PublicLeagueResponse } from "@/lib/api";
import { useLeague } from "@/lib/league-context";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";

export function JoinLeagueDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { selectLeagueAfterAction } = useLeague();
  const [tab, setTab] = useState<"browse" | "code">("browse");
  const [naming, setNaming] = useState<PublicLeagueResponse | null>(null);

  function handleOpenChange(next: boolean) {
    if (!next) {
      setTab("browse");
      setNaming(null);
    }
    onOpenChange(next);
  }

  async function onJoined() {
    handleOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Join a league</DialogTitle>
          <DialogDescription>
            Browse public leagues or enter a code from a commissioner.
          </DialogDescription>
        </DialogHeader>

        {naming ? (
          <TeamNamePrompt
            league={naming}
            onBack={() => setNaming(null)}
            onJoined={(league) => {
              selectLeagueAfterAction(league);
              onJoined();
            }}
          />
        ) : (
          <Tabs value={tab} onValueChange={(v) => setTab(v as "browse" | "code")}>
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="browse">Public leagues</TabsTrigger>
              <TabsTrigger value="code">Have a code?</TabsTrigger>
            </TabsList>
            <TabsContent value="browse">
              <BrowseTab open={open} onSelect={setNaming} />
            </TabsContent>
            <TabsContent value="code">
              <CodeTab
                onJoined={(league) => {
                  selectLeagueAfterAction(league);
                  onJoined();
                }}
              />
            </TabsContent>
          </Tabs>
        )}
      </DialogContent>
    </Dialog>
  );
}

function BrowseTab({
  open,
  onSelect,
}: {
  open: boolean;
  onSelect: (league: PublicLeagueResponse) => void;
}) {
  const [leagues, setLeagues] = useState<PublicLeagueResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setLeagues(null);
    setError(null);
    api
      .publicLeagues()
      .then(setLeagues)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Couldn't load public leagues")
      );
  }, [open]);

  if (error) {
    return <p className="py-8 text-center text-sm text-destructive">{error}</p>;
  }

  if (!leagues) {
    return (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 w-full rounded-lg" />
        ))}
      </div>
    );
  }

  if (leagues.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground">
        No public leagues yet. Ask a commissioner for a join code.
      </p>
    );
  }

  return (
    <div className="flex max-h-80 flex-col gap-2 overflow-y-auto">
      {leagues.map((league) => {
        const full = league.memberCount >= league.maxMembers;
        return (
          <div
            key={league.id}
            className="flex items-center justify-between gap-3 rounded-lg border border-border bg-secondary/20 p-3"
          >
            <div className="min-w-0">
              <p className="truncate font-semibold">{league.name}</p>
              <p className="truncate text-xs text-muted-foreground">
                {league.commissionerDisplayName} · {league.seasonYear}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-3">
              <span className="flex items-center gap-1 text-xs tabular-nums text-muted-foreground">
                <Users className="h-3.5 w-3.5" />
                {league.memberCount}/{league.maxMembers}
              </span>
              <Button size="sm" disabled={full} onClick={() => onSelect(league)}>
                {full ? "Full" : "Join"}
              </Button>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function TeamNamePrompt({
  league,
  onBack,
  onJoined,
}: {
  league: PublicLeagueResponse;
  onBack: () => void;
  onJoined: (league: Awaited<ReturnType<typeof api.joinLeague>>) => void;
}) {
  const [teamName, setTeamName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      const joined = await api.joinLeague(league.id, teamName.trim());
      onJoined(joined);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't join the league");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <button
        type="button"
        onClick={onBack}
        className="flex items-center gap-1 self-start text-xs font-semibold text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back
      </button>
      <div>
        <p className="text-sm text-muted-foreground">Joining</p>
        <p className="font-semibold">{league.name}</p>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="join-team-name">Your team name</Label>
        <Input
          id="join-team-name"
          required
          autoFocus
          maxLength={70}
          value={teamName}
          onChange={(e) => setTeamName(e.target.value)}
          placeholder="The Dingers"
        />
      </div>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <Button type="submit" size="lg" disabled={isSubmitting}>
        {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        Join league
      </Button>
    </form>
  );
}

function CodeTab({
  onJoined,
}: {
  onJoined: (league: Awaited<ReturnType<typeof api.joinByCode>>) => void;
}) {
  const [joinCode, setJoinCode] = useState("");
  const [teamName, setTeamName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      const joined = await api.joinByCode(joinCode.trim().toUpperCase(), teamName.trim());
      onJoined(joined);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't join with that code");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div className="space-y-1.5">
        <Label htmlFor="code-input">Join code</Label>
        <Input
          id="code-input"
          required
          value={joinCode}
          onChange={(e) => setJoinCode(e.target.value)}
          placeholder="ABCD2345"
          className="font-mono uppercase tracking-widest"
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="code-team-name">Your team name</Label>
        <Input
          id="code-team-name"
          required
          maxLength={70}
          value={teamName}
          onChange={(e) => setTeamName(e.target.value)}
          placeholder="The Dingers"
        />
      </div>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <Button type="submit" size="lg" disabled={isSubmitting}>
        {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        Join league
      </Button>
    </form>
  );
}
