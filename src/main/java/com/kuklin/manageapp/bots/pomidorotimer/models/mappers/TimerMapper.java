package com.kuklin.manageapp.bots.pomidorotimer.models.mappers;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.entities.Timer;
import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.TaskDto;
import com.kuklin.manageapp.bots.pomidorotimer.models.TimerDto;
import com.kuklin.manageapp.bots.pomidorotimer.models.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {UserMapper.class, TaskMapper.class})
public interface TimerMapper {
    @Mapping(source = "userEntity", target = "user")
    @Mapping(source = "tasks", target = "tasks")
    TimerDto entityToDto(Timer timer);

    @Mapping(source = "user", target = "userEntity")
    @Mapping(source = "tasks", target = "tasks")
    Timer dtoToEntity(TimerDto timerDto);

    List<TimerDto> entityListToDtoList(List<Timer> timerList);
    List<Timer> dtoListToEntityList(List<TimerDto> timerDtoList);

    default UserDto mapUser(UserEntity user, UserMapper userMapper) {
        if (user == null) {
            return null;
        }
        return userMapper.entityToDto(user);
    }

    default UserEntity mapUserDto(UserDto userDto, UserMapper userMapper) {
        if (userDto == null) {
            return null;
        }
        return userMapper.dtoToEntity(userDto);
    }

    default List<TaskDto> mapTasks(List<Task> taskList, TaskMapper taskMapper) {
        if (taskList == null) {
            return null;
        }
        return taskMapper.entityListToDtoList(taskList);
    }

    default List<Task> mapTasksDto(List<TaskDto> taskDtoList, TaskMapper taskMapper) {
        if (taskDtoList == null) {
            return null;
        }
        return taskMapper.dtoListToEntityList(taskDtoList);
    }
}
