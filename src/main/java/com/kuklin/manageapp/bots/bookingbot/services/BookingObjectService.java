package com.kuklin.manageapp.bots.bookingbot.services;

import com.kuklin.manageapp.bots.bookingbot.entities.BookingObject;
import com.kuklin.manageapp.bots.bookingbot.repositories.BookingObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingObjectService {
    private final BookingObjectRepository repository;

    public BookingObject getByIdOrNull(Long id) {
        return repository.findById(id).orElse(null);
    }

    public BookingObject save(BookingObject bookingObject) {
        return repository.save(bookingObject);
    }

    public List<BookingObject> getAllBookingObject(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return repository.findAll(pageable).getContent();
    }
}
