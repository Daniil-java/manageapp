package com.kuklin.manageapp.bots.pomidorotimer.models;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserDto {
    @NotNull(message = "User id can't be empty!")
    private Long id;
    private String password;
    private Long telegramId;
    private Long chatId;
    private String username;
}
