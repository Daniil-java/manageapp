package com.kuklin.manageapp.common.library.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URL;

@Slf4j
public class FilesUtils {

    public static byte[] downloadImage(String url) throws IOException {
        try (InputStream in = new URL(url).openStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] data = new byte[8192];
            int n;

            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }

            return buffer.toByteArray();
        }
    }

    public static String saveImage(byte[] bytes, String fileName, String pack) throws IOException {
        String dir = "uploads/images/" + pack + "/";
        new File(dir).mkdirs();

        String path = dir + fileName;

        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(bytes);
        }

        return path;
    }

    public static void deleteOldImages(String pack, int daysOld) {
        String dirPath = "uploads/images/" + pack + "/";
        File directory = new File(dirPath);

        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        long thresholdTime = System.currentTimeMillis() - (long) daysOld * 24 * 60 * 60 * 1000;
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.lastModified() < thresholdTime) {
                    if (file.delete()) {
                        log.info("Deleted old file: " + file.getName());
                    }
                }
            }
        }
    }

    public static boolean deleteImage(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        File file = new File(path);

        if (!file.exists()) {
            return false;
        }

        return file.delete();
    }
}
