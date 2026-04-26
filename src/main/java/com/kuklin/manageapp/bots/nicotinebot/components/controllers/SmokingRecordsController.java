package com.kuklin.manageapp.bots.nicotinebot.components.controllers;

import com.kuklin.manageapp.bots.nicotinebot.components.SmokingRecord;
import com.kuklin.manageapp.bots.nicotinebot.components.SmokingRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/nicotine")
@RequiredArgsConstructor
public class SmokingRecordsController {
    private final SmokingRecordService smokingRecordService;

    @PostMapping("/{id}")
    public SmokingRecord createSmokingRecord(@PathVariable Long id) {
        return smokingRecordService.create(id);
    }

    @GetMapping("/{id}")
    public List<SmokingRecord> getByPeriod(@PathVariable Long id,
                                           @RequestParam Instant start,
                                           @RequestParam Instant end) {
        return smokingRecordService.getByPeriod(id, start, end);
    }

    @GetMapping("/{id}/last")
    public SmokingRecord getLastSmokingRecordOrNull(@PathVariable Long id) {
        return smokingRecordService.getLastSmokingEvent(id).orElse(null);
    }

    @GetMapping("/{id}/all")
    public List<SmokingRecord> gelAll(@PathVariable Long id) {
        return smokingRecordService.getAllByUser(id);
    }

}
