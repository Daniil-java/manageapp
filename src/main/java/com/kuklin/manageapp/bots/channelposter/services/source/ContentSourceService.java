package com.kuklin.manageapp.bots.channelposter.services.source;

import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.repositories.source.ContentSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContentSourceService {

    private final ContentSourceRepository repository;

    public List<ContentSource> getActive() {
        return repository.findAllByActiveTrueOrderByIdAsc();
    }

    public List<ContentSource> getAll() {
        return repository.findAllByOrderByIdAsc();
    }

    public Optional<ContentSource> findById(Long id) {
        return repository.findById(id);
    }

    public boolean exists(ContentSource.SourceType type, String address) {
        return repository.existsByTypeAndAddress(type, address);
    }

    public ContentSource create(ContentSource.SourceType type, String name, String address, int maxItems) {
        return repository.save(new ContentSource()
                .setType(type)
                .setName(name)
                .setAddress(address)
                .setActive(true)
                .setMaxItems(maxItems)
                .setFailCount(0));
    }

    public ContentSource setActive(ContentSource source, boolean active) {
        return repository.save(source.setActive(active));
    }

    public void delete(ContentSource source) {
        repository.delete(source);
    }

    public void markSuccess(ContentSource source) {
        Instant now = Instant.now();
        repository.save(source
                .setLastFetchedAt(now)
                .setLastSuccessAt(now)
                .setLastError(null)
                .setFailCount(0));
    }

    public void markFailure(ContentSource source, String error) {
        int fails = source.getFailCount() == null ? 0 : source.getFailCount();
        repository.save(source
                .setLastFetchedAt(Instant.now())
                .setLastError(error)
                .setFailCount(fails + 1));
    }
}
