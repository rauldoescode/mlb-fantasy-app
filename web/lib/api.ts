const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

let authToken: string | null = null;

/** Called by AuthProvider whenever the session token changes. */
export function setApiToken(token: string | null) {
  authToken = token;
}

export class ApiError extends Error {
  status: number;
  fields?: Record<string, string>;

  constructor(status: number, message: string, fields?: Record<string, string>) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (authToken) {
    headers.set("Authorization", `Bearer ${authToken}`);
  }

  const res = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!res.ok) {
    const message = (data && (data.message as string)) || res.statusText || "Something went wrong";
    throw new ApiError(res.status, message, data?.fields);
  }

  return data as T;
}

export type UserResponse = {
  id: string;
  email: string;
  displayName: string;
  role: string;
  avatarUrl: string | null;
};

export type AuthResponse = {
  token: string;
  user: UserResponse;
};

export type LeagueVisibility = "PUBLIC" | "PRIVATE";

export type LeagueResponse = {
  id: string;
  name: string;
  seasonYear: number;
  commissionerId: string;
  salaryCap: number;
  rosterSize: number;
  visibility: LeagueVisibility;
  memberCount: number;
  maxMembers: number;
  joinCode?: string | null;
};

export type PublicLeagueResponse = {
  id: string;
  name: string;
  seasonYear: number;
  commissionerDisplayName: string;
  memberCount: number;
  maxMembers: number;
};

export type StandingRow = {
  userId: string;
  displayName: string;
  teamName: string;
  wins: number;
  losses: number;
  ties: number;
  pointsFor: number;
};

export type MatchupStatus = "SCHEDULED" | "IN_PROGRESS" | "FINAL";

export type MatchupResponse = {
  id: string;
  leagueId: string;
  weekNumber: number;
  userOneId: string;
  userOneScore: number | null;
  userTwoId: string;
  userTwoScore: number | null;
  winnerId: string | null;
  status: MatchupStatus;
};

export type ScoreBreakdown = {
  userId: string;
  totalPoints: number;
  categoryPoints: Record<string, number>;
};

export type MatchupDetailResponse = {
  matchup: MatchupResponse;
  weekStart: string;
  weekEnd: string;
  userOneBreakdown: ScoreBreakdown;
  userTwoBreakdown: ScoreBreakdown;
};

export type PlayerResponse = {
  mlbId: number;
  fullName: string;
  position: string | null;
  teamAbbrev: string | null;
  currentStatus: string | null;
  jerseyNumber: string | null;
  active: boolean;
  salary: number | null;
};

export type RosterSlotResponse = {
  slotId: string;
  playerId: number;
  playerName: string | null;
  position: string | null;
  teamAbbrev: string | null;
  salary: number | null;
  slotType: string;
  active: boolean;
  locked: boolean;
};

export type RosterResponse = {
  slots: RosterSlotResponse[];
  totalSalary: number;
  salaryCap: number;
  salaryRemaining: number;
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export const api = {
  register: (body: { email: string; displayName: string; password: string }) =>
    request<AuthResponse>("/api/auth/register", { method: "POST", body: JSON.stringify(body) }),

  login: (body: { email: string; password: string }) =>
    request<AuthResponse>("/api/auth/login", { method: "POST", body: JSON.stringify(body) }),

  me: () => request<UserResponse>("/api/auth/me"),

  updateAvatar: (avatarDataUrl: string) =>
    request<UserResponse>("/api/auth/me/avatar", {
      method: "PATCH",
      body: JSON.stringify({ avatarDataUrl }),
    }),

  myLeagues: () => request<LeagueResponse[]>("/api/leagues"),

  createLeague: (body: {
    name: string;
    seasonYear: number;
    teamName: string;
    visibility?: LeagueVisibility;
    maxMembers?: number;
    salaryCap?: number;
    rosterSize?: number;
  }) =>
    request<LeagueResponse>("/api/leagues", { method: "POST", body: JSON.stringify(body) }),

  publicLeagues: () => request<PublicLeagueResponse[]>("/api/leagues/public"),

  joinLeague: (leagueId: string, teamName: string) =>
    request<LeagueResponse>(`/api/leagues/${leagueId}/join`, {
      method: "POST",
      body: JSON.stringify({ teamName }),
    }),

  joinByCode: (joinCode: string, teamName: string) =>
    request<LeagueResponse>("/api/leagues/join-by-code", {
      method: "POST",
      body: JSON.stringify({ joinCode, teamName }),
    }),

  regenerateJoinCode: (leagueId: string) =>
    request<LeagueResponse>(`/api/leagues/${leagueId}/join-code/regenerate`, {
      method: "POST",
    }),

  updateLeagueVisibility: (leagueId: string, visibility: LeagueVisibility) =>
    request<LeagueResponse>(`/api/leagues/${leagueId}/visibility`, {
      method: "PUT",
      body: JSON.stringify({ visibility }),
    }),

  getLeague: (leagueId: string) => request<LeagueResponse>(`/api/leagues/${leagueId}`),

  standings: (leagueId: string) => request<StandingRow[]>(`/api/leagues/${leagueId}/standings`),

  matchups: (leagueId: string, week?: number) =>
    request<MatchupResponse[]>(
      `/api/leagues/${leagueId}/matchups${week ? `?week=${week}` : ""}`
    ),

  matchupDetail: (matchupId: string) =>
    request<MatchupDetailResponse>(`/api/matchups/${matchupId}`),

  roster: (leagueId: string) => request<RosterResponse>(`/api/leagues/${leagueId}/roster`),

  addRosterPlayer: (
    leagueId: string,
    body: { playerId: number; slotType: string; active?: boolean }
  ) =>
    request<RosterSlotResponse>(`/api/leagues/${leagueId}/roster`, {
      method: "POST",
      body: JSON.stringify(body),
    }),

  updateRosterSlot: (slotId: string, body: { active?: boolean; slotType?: string }) =>
    request<RosterSlotResponse>(`/api/roster/${slotId}`, {
      method: "PATCH",
      body: JSON.stringify(body),
    }),

  removeRosterSlot: (slotId: string) =>
    request<void>(`/api/roster/${slotId}`, { method: "DELETE" }),

  players: (params: {
    search?: string;
    position?: string;
    team?: string;
    page?: number;
    size?: number;
  }) => {
    const query = new URLSearchParams();
    if (params.search) query.set("search", params.search);
    if (params.position) query.set("position", params.position);
    if (params.team) query.set("team", params.team);
    query.set("page", String(params.page ?? 0));
    query.set("size", String(params.size ?? 25));
    return request<Page<PlayerResponse>>(`/api/players?${query.toString()}`);
  },
};
