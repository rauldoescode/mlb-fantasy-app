package com.mlbfantasy.service;

import com.mlbfantasy.dto.PlayerResponse;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.repository.PlayerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Transactional(readOnly = true)
    public Page<PlayerResponse> search(String search, String position, String team, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by("fullName").ascending());
        return playerRepository.search(
                        StringUtils.hasText(search) ? search : null,
                        StringUtils.hasText(position) ? position : null,
                        StringUtils.hasText(team) ? team : null,
                        pageable)
                .map(PlayerResponse::from);
    }

    @Transactional(readOnly = true)
    public PlayerResponse get(Integer mlbId) {
        return playerRepository.findById(mlbId)
                .map(PlayerResponse::from)
                .orElseThrow(() -> ApiException.notFound("Player not found"));
    }
}
