import { describe, expect, it } from "vitest";

import type { MatchupDetailResponse, MatchupLineupSide } from "./api";
import { emptySide, orientSides } from "./matchup-orient";

function side(userId: string, teamName: string): MatchupLineupSide {
  return {
    userId,
    teamName,
    starters: [],
    bench: [],
    totalPoints: 12,
  };
}

function detail(overrides: Partial<MatchupDetailResponse> = {}): MatchupDetailResponse {
  const userOneId = "user-1";
  const userTwoId = "user-2";
  return {
    matchup: {
      id: "m1",
      leagueId: "l1",
      weekNumber: 3,
      userOneId,
      userTwoId,
      userOneScore: 10,
      userTwoScore: 8,
      winnerId: null,
      status: "IN_PROGRESS",
    },
    weekStart: "2026-04-06",
    weekEnd: "2026-04-12",
    userOneBreakdown: { userId: userOneId, totalPoints: 10, categoryPoints: {} },
    userTwoBreakdown: { userId: userTwoId, totalPoints: 8, categoryPoints: {} },
    userOneLineup: side(userOneId, "Aces"),
    userTwoLineup: side(userTwoId, "Bombers"),
    lineupEditable: true,
    ...overrides,
  };
}

describe("emptySide", () => {
  it("returns a safe default lineup", () => {
    expect(emptySide("abc")).toEqual({
      userId: "abc",
      teamName: "",
      starters: [],
      bench: [],
      totalPoints: 0,
    });
  });
});

describe("orientSides", () => {
  it("keeps userOne on the left for userOne viewer", () => {
    const oriented = orientSides(detail(), "user-1");
    expect(oriented.leftLabel).toBe("You");
    expect(oriented.rightLabel).toBe("Bombers");
    expect(oriented.leftEditable).toBe(true);
    expect(oriented.rightEditable).toBe(false);
    expect(oriented.leftLineup.teamName).toBe("You");
  });

  it("swaps so userTwo viewer is on the left", () => {
    const oriented = orientSides(detail(), "user-2");
    expect(oriented.leftLabel).toBe("You");
    expect(oriented.rightLabel).toBe("Aces");
    expect(oriented.leftUserId).toBe("user-2");
    expect(oriented.rightUserId).toBe("user-1");
    expect(oriented.leftEditable).toBe(true);
    expect(oriented.rightEditable).toBe(false);
  });

  it("does not crash when lineup sides are missing from the payload", () => {
    const oriented = orientSides(
      detail({
        userOneLineup: undefined as unknown as MatchupLineupSide,
        userTwoLineup: undefined as unknown as MatchupLineupSide,
      }),
      "user-2"
    );
    expect(oriented.leftLabel).toBe("You");
    expect(oriented.rightLabel).toBe("Opponent");
    expect(oriented.leftLineup.starters).toEqual([]);
    expect(oriented.rightLineup.starters).toEqual([]);
  });

  it("disables edits when lineupEditable is false", () => {
    const oriented = orientSides(detail({ lineupEditable: false }), "user-1");
    expect(oriented.leftEditable).toBe(false);
    expect(oriented.rightEditable).toBe(false);
  });

  it("treats non-participants as read-only spectators", () => {
    const oriented = orientSides(detail(), "spectator");
    expect(oriented.leftEditable).toBe(false);
    expect(oriented.rightEditable).toBe(false);
    expect(oriented.leftLabel).toBe("Aces");
    expect(oriented.rightLabel).toBe("Bombers");
  });
});
