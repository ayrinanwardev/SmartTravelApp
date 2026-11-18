package com.wanderly.api.repository;

import com.wanderly.api.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface JournalRepository extends JpaRepository<JournalEntry, String> {
    List<JournalEntry> findByUserIdOrderByDateDesc(Long userId);

    List<JournalEntry> findByUserIdAndAlbumOrderByDateDesc(Long userId, String album);

    Optional<JournalEntry> findByIdAndUserId(String id, Long userId);

    @Query("select distinct j.album from JournalEntry j where j.user.id = :userId order by j.album")
    List<String> findAlbums(Long userId);

    long countByUserId(Long userId);
}
