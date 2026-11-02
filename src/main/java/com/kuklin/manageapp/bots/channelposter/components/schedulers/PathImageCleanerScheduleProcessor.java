package com.kuklin.manageapp.bots.channelposter.components.schedulers;

import com.kuklin.manageapp.bots.channelposter.services.PostImageService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.utils.FilesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PathImageCleanerScheduleProcessor implements ScheduleProcessor {
    private static final int OLD_DAYS = 7;
    @Override
    public void process() {
        FilesUtils.deleteOldImages(PostImageService.IMG_DIR, OLD_DAYS);
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
