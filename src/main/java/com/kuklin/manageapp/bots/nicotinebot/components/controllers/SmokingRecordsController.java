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
    public SmokingRecord createSmokingRecord(@PathVariable("id") Long id) {
        return smokingRecordService.create(id);
    }

    @GetMapping("/{id}")
    public List<SmokingRecord> getByPeriod(@PathVariable("id") Long id,
                                           @RequestParam Instant start,
                                           @RequestParam Instant end) {
        return smokingRecordService.getByPeriod(id, start, end);
    }

    @GetMapping("/{id}/last")
    public SmokingRecord getLastSmokingRecordOrNull(@PathVariable("id") Long id) {
        return smokingRecordService.getLastSmokingEvent(id).orElse(null);
    }

    @GetMapping("/{id}/all")
    public List<SmokingRecord> gelAll(@PathVariable("id") Long id) {
        return smokingRecordService.getAllByUser(id);
    }

    @DeleteMapping("/{id}/{logId}")
    public void deleteSmokingRecordById(@PathVariable("id") Long id, @PathVariable("logId") Long logId) {
        smokingRecordService.deleteById(logId);
    }

}
