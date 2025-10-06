package com.kuklin.manageapp.common.library.tgutils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadUtil {
    public static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            log.error("Thread sleep error!", e);
        }
    }
}
