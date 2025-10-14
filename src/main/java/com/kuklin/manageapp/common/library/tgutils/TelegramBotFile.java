package com.kuklin.manageapp.common.library.tgutils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelegramBotFile {
    private boolean ok;
    private Result result;

    @Data
    public static class Result {
        @JsonProperty("file_id")
        private String fileId;
        @JsonProperty("file_unique_id")
        private String fileUniqueId;
        @JsonProperty("file_size")
        private int fileSize;
        @JsonProperty("file_path")
        private String filePath;

    }
}

