"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { api, type LeagueResponse } from "./api";
import { useAuth } from "./auth-context";

const LEAGUE_STORAGE_KEY = "mlbfantasy.leagueId";

type LeagueContextValue = {
  leagues: LeagueResponse[];
  currentLeague: LeagueResponse | null;
  setCurrentLeagueId: (id: string) => void;
  selectLeagueAfterAction: (league: LeagueResponse) => void;
  isLoading: boolean;
  error: string | null;
  refresh: () => void;
};

const LeagueContext = createContext<LeagueContextValue | undefined>(undefined);

export function LeagueProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [leagues, setLeagues] = useState<LeagueResponse[]>([]);
  const [currentLeagueId, setCurrentLeagueIdState] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshTick, setRefreshTick] = useState(0);
  const [pendingLeague, setPendingLeague] = useState<LeagueResponse | null>(null);

  useEffect(() => {
    if (!user) {
      setLeagues([]);
      setCurrentLeagueIdState(null);
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    api
      .myLeagues()
      .then((data) => {
        setLeagues(data);
        const stored = window.localStorage.getItem(LEAGUE_STORAGE_KEY);
        const preferredId = pendingLeague?.id ?? stored;
        const preferred = data.find((l) => l.id === preferredId) ?? pendingLeague ?? data[0];
        if (preferred) {
          window.localStorage.setItem(LEAGUE_STORAGE_KEY, preferred.id);
          setCurrentLeagueIdState(preferred.id);
        } else {
          setCurrentLeagueIdState(null);
        }
        setPendingLeague(null);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load leagues"))
      .finally(() => setIsLoading(false));
  }, [user, refreshTick, pendingLeague]);

  const setCurrentLeagueId = useCallback((id: string) => {
    window.localStorage.setItem(LEAGUE_STORAGE_KEY, id);
    setCurrentLeagueIdState(id);
  }, []);

  const selectLeagueAfterAction = useCallback((league: LeagueResponse) => {
    setLeagues((prev) => {
      const exists = prev.some((l) => l.id === league.id);
      return exists ? prev.map((l) => (l.id === league.id ? league : l)) : [...prev, league];
    });
    window.localStorage.setItem(LEAGUE_STORAGE_KEY, league.id);
    setCurrentLeagueIdState(league.id);
    setPendingLeague(league);
    setRefreshTick((t) => t + 1);
  }, []);

  const currentLeague = useMemo(
    () => leagues.find((l) => l.id === currentLeagueId) ?? null,
    [leagues, currentLeagueId]
  );

  const value = useMemo(
    () => ({
      leagues,
      currentLeague,
      setCurrentLeagueId,
      selectLeagueAfterAction,
      isLoading,
      error,
      refresh: () => setRefreshTick((t) => t + 1),
    }),
    [leagues, currentLeague, setCurrentLeagueId, selectLeagueAfterAction, isLoading, error]
  );

  return <LeagueContext.Provider value={value}>{children}</LeagueContext.Provider>;
}

export function useLeague() {
  const ctx = useContext(LeagueContext);
  if (!ctx) {
    throw new Error("useLeague must be used within a LeagueProvider");
  }
  return ctx;
}
