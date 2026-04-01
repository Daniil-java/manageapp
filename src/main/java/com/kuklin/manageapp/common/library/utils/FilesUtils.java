package com.kuklin.manageapp.common.library.utils;

import java.io.*;
import java.net.URL;

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
