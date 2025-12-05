package com.kuklin.manageapp.bots.pomidorotimer.models.mappers;


import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.TaskDto;
import com.kuklin.manageapp.bots.pomidorotimer.models.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface TaskMapper {
    @Mapping(source = "userEntity", target = "user", qualifiedByName = "mapUser")
    TaskDto entityToDto(Task task);
    @Mapping(source = "user", target = "userEntity", qualifiedByName = "mapUserDto")
    Task dtoToEntity(TaskDto taskDto);
    List<TaskDto> entityListToDtoList(List<Task> taskList);
    List<Task> dtoListToEntityList(List<TaskDto> taskDtoList);

    @Named("mapUser")
    default UserDto mapUser(UserEntity user) {
        if (user == null) {
            return null;
        }
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        return userMapper.entityToDto(user);
    }

    @Named("mapUserDto")
    default UserEntity mapUserDto(UserDto userDto) {
        if (userDto == null) {
            return null;
        }
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        return userMapper.dtoToEntity(userDto);
    }
}
