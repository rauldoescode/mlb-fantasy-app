package com.mlbfantasy.service;

import com.mlbfantasy.dto.AddRosterPlayerRequest;
import com.mlbfantasy.dto.RosterResponse;
import com.mlbfantasy.dto.RosterSlotResponse;
import com.mlbfantasy.dto.UpdateRosterSlotRequest;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.Player;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.repository.PlayerRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RosterService {

    private final RosterSlotRepository rosterSlotRepository;
    private final PlayerRepository playerRepository;
    private final LeagueService leagueService;
    private final LineupLockService lineupLockService;

    public RosterService(RosterSlotRepository rosterSlotRepository,
                         PlayerRepository playerRepository,
                         LeagueService leagueService,
                         LineupLockService lineupLockService) {
        this.rosterSlotRepository = rosterSlotRepository;
        this.playerRepository = playerRepository;
        this.leagueService = leagueService;
        this.lineupLockService = lineupLockService;
    }

    @Transactional
    public RosterResponse getRoster(UUID leagueId, UUID userId) {
        leagueService.requireMember(leagueId, userId);
        League league = leagueService.requireLeague(leagueId);
        List<RosterSlot> slots = rosterSlotRepository.findByLeagueIdAndUserId(leagueId, userId);
        Map<Integer, Player> players = loadPlayers(slots);

        // Lazy-freeze today's eligibility for any player whose game has already started.
        for (RosterSlot slot : slots) {
            if (slot.getPlayerId() != null) {
                lineupLockService.materializeTodaysLockIfNeeded(
                        leagueId, userId, slot.getPlayerId(), Boolean.TRUE.equals(slot.getActive()));
            }
        }

        List<RosterSlotResponse> slotResponses = slots.stream()
                .map(slot -> RosterSlotResponse.from(
                        slot,
                        players.get(slot.getPlayerId()),
                        lineupLockService.isPlayerLocked(slot.getPlayerId())))
                .toList();

        BigDecimal totalSalary = slots.stream()
                .map(slot -> salaryOf(players.get(slot.getPlayerId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RosterResponse(
                slotResponses,
                totalSalary,
                league.getSalaryCap(),
                league.getSalaryCap().subtract(totalSalary));
    }

    @Transactional
    public RosterSlotResponse addPlayer(UUID leagueId, UUID userId, AddRosterPlayerRequest request) {
        leagueService.requireMember(leagueId, userId);
        lineupLockService.requireLineupEditable(leagueId, userId);
        League league = leagueService.requireLeague(leagueId);

        Player player = playerRepository.findById(request.playerId())
                .orElseThrow(() -> ApiException.notFound("Player not found"));

        if (lineupLockService.isPlayerLocked(player.getMlbId())) {
            throw ApiException.conflict("This player's game has started; the slot is locked");
        }

        rosterSlotRepository.findFirstByLeagueIdAndPlayerId(leagueId, request.playerId())
                .ifPresent(existing -> {
                    if (userId.equals(existing.getUserId())) {
                        throw ApiException.conflict("Player is already on your roster");
                    }
                    throw ApiException.conflict(
                            "Player is already rostered by another team in this league");
                });

        long currentCount = rosterSlotRepository.countByLeagueIdAndUserId(leagueId, userId);
        if (currentCount >= league.getRosterSize()) {
            throw ApiException.badRequest("Roster is full (" + league.getRosterSize() + " slots)");
        }

        BigDecimal projectedTotal = currentRosterSalary(leagueId, userId).add(salaryOf(player));
        if (projectedTotal.compareTo(league.getSalaryCap()) > 0) {
            throw ApiException.badRequest("Adding this player exceeds the salary cap");
        }

        boolean active = request.active() == null || request.active();
        RosterSlot slot = new RosterSlot(leagueId, userId, request.playerId(), request.slotType(), active);
        slot = rosterSlotRepository.save(slot);
        lineupLockService.syncOpenWeekEligibility(leagueId, userId, player.getMlbId(), active);
        return RosterSlotResponse.from(slot, player, lineupLockService.isPlayerLocked(player.getMlbId()));
    }

    @Transactional
    public RosterSlotResponse updateSlot(UUID slotId, UUID userId, UpdateRosterSlotRequest request) {
        RosterSlot slot = requireOwnedSlot(slotId, userId);
        lineupLockService.requireLineupEditable(slot.getLeagueId(), userId);
        if (lineupLockService.isPlayerLocked(slot.getPlayerId())) {
            throw ApiException.conflict("This player's game has started; the slot is locked");
        }
        if (request.active() != null) {
            slot.setActive(request.active());
        }
        if (request.slotType() != null) {
            slot.setSlotType(request.slotType());
        }
        slot = rosterSlotRepository.save(slot);
        if (slot.getPlayerId() != null && request.active() != null) {
            lineupLockService.syncOpenWeekEligibility(
                    slot.getLeagueId(), userId, slot.getPlayerId(), Boolean.TRUE.equals(slot.getActive()));
        }
        Player player = slot.getPlayerId() == null ? null
                : playerRepository.findById(slot.getPlayerId()).orElse(null);
        return RosterSlotResponse.from(slot, player, false);
    }

    @Transactional
    public void removeSlot(UUID slotId, UUID userId) {
        RosterSlot slot = requireOwnedSlot(slotId, userId);
        lineupLockService.requireLineupEditable(slot.getLeagueId(), userId);
        if (lineupLockService.isPlayerLocked(slot.getPlayerId())) {
            throw ApiException.conflict("This player's game has started; the slot is locked");
        }
        rosterSlotRepository.delete(slot);
    }

    private RosterSlot requireOwnedSlot(UUID slotId, UUID userId) {
        RosterSlot slot = rosterSlotRepository.findById(slotId)
                .orElseThrow(() -> ApiException.notFound("Roster slot not found"));
        if (!userId.equals(slot.getUserId())) {
            throw ApiException.forbidden("This roster slot does not belong to you");
        }
        return slot;
    }

    private BigDecimal currentRosterSalary(UUID leagueId, UUID userId) {
        List<RosterSlot> slots = rosterSlotRepository.findByLeagueIdAndUserId(leagueId, userId);
        Map<Integer, Player> players = loadPlayers(slots);
        return slots.stream()
                .map(slot -> salaryOf(players.get(slot.getPlayerId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Integer, Player> loadPlayers(List<RosterSlot> slots) {
        List<Integer> ids = slots.stream()
                .map(RosterSlot::getPlayerId)
                .filter(java.util.Objects::nonNull)
                .toList();
        return playerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Player::getMlbId, p -> p));
    }

    private BigDecimal salaryOf(Player player) {
        if (player == null || player.getSalary() == null) {
            return BigDecimal.ZERO;
        }
        return player.getSalary();
    }
}
