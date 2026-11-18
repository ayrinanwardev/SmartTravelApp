package com.wanderly.api.service;

import com.wanderly.api.domain.User;
import com.wanderly.api.domain.WishlistItem;
import com.wanderly.api.dto.DestinationDtos.DestinationSummary;
import com.wanderly.api.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistService {

    private final WishlistRepository repo;
    private final DestinationService destinations;

    public WishlistService(WishlistRepository repo, DestinationService destinations) {
        this.repo = repo;
        this.destinations = destinations;
    }

    @Transactional(readOnly = true)
    public List<DestinationSummary> list(User user) {
        return repo.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(w -> DestinationSummary.from(w.getDestination()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> ids(User user) {
        return repo.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(w -> w.getDestination().getId())
                .toList();
    }

    /** Idempotent: saving an already-saved destination is a no-op. */
    @Transactional
    public void add(User user, String destinationId) {
        if (repo.findByUserIdAndDestinationId(user.getId(), destinationId).isEmpty()) {
            repo.save(new WishlistItem(user, destinations.find(destinationId)));
        }
    }

    @Transactional
    public void remove(User user, String destinationId) {
        repo.findByUserIdAndDestinationId(user.getId(), destinationId).ifPresent(repo::delete);
    }
}
