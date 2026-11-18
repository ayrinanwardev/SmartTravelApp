package com.wanderly.api.service;

import com.wanderly.api.domain.JournalEntry;
import com.wanderly.api.domain.User;
import com.wanderly.api.dto.CommonDtos.JournalDto;
import com.wanderly.api.dto.CommonDtos.JournalRequest;
import com.wanderly.api.exception.ApiException;
import com.wanderly.api.repository.JournalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JournalService {

    private final JournalRepository repo;

    public JournalService(JournalRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<JournalDto> list(User user, String album) {
        var list = album == null || album.isBlank()
                ? repo.findByUserIdOrderByDateDesc(user.getId())
                : repo.findByUserIdAndAlbumOrderByDateDesc(user.getId(), album);
        return list.stream().map(JournalDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> albums(User user) {
        return repo.findAlbums(user.getId());
    }

    @Transactional(readOnly = true)
    public JournalDto get(User user, String id) {
        return JournalDto.from(find(user, id));
    }

    @Transactional
    public JournalDto create(User user, JournalRequest req) {
        var entry = new JournalEntry(user);
        apply(entry, req);
        return JournalDto.from(repo.save(entry));
    }

    @Transactional
    public JournalDto update(User user, String id, JournalRequest req) {
        var entry = find(user, id);
        apply(entry, req);
        return JournalDto.from(repo.saveAndFlush(entry));
    }

    @Transactional
    public void delete(User user, String id) {
        repo.delete(find(user, id));
    }

    private static void apply(JournalEntry e, JournalRequest r) {
        e.update(r.title().trim(),
                r.body() == null ? "" : r.body().trim(),
                r.location() == null || r.location().isBlank() ? "Somewhere wonderful" : r.location().trim(),
                r.date(),
                r.album() == null || r.album().isBlank() ? "My Travels" : r.album().trim(),
                r.photoUrls());
    }

    private JournalEntry find(User user, String id) {
        return repo.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiException.notFound("Journal entry", id));
    }
}
