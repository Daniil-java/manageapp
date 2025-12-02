package com.kuklin.manageapp.bots.pomidorotimer.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.pomidorotimer.configurations.TelegramPomidoroTimerBotKeyComponents;
import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.models.TaskDto;
import com.kuklin.manageapp.bots.pomidorotimer.models.mappers.TaskMapper;
import com.kuklin.manageapp.bots.pomidorotimer.models.mappers.UserMapper;
import com.kuklin.manageapp.bots.pomidorotimer.models.task.Status;
import com.kuklin.manageapp.bots.pomidorotimer.repositories.TaskRepository;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.PomidoroTelegramBot;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {
    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final TelegramPomidoroTimerBotKeyComponents components;
    private final ObjectMapper objectMapper;
    private static final String AI_REQUEST =
            "В конце данного запроса будут указаны: название задачи и комментарий к ней. У задачи есть 4 типа приоритета (MUST, SHOULD, COULD, WOULD).  \n" +
                    "Твоя задача декомпозировать задачу на меньшие фрагменты.\n" +
                    "Не пиши ничего лишнего, никаких объяснений. В качестве ответа, ты должен использовать ТОЛЬКО JSON сообщение следующего формата:\n" +
                    "[\n" +
                    "  {\n" +
                    "    \"name\": \"Sample Task 1\",\n" +
                    "    \"priority\": \"MUST\",\n" +
                    "    \"comment\": \"This is the first sample task.\"\n" +
                    "  },\n" +
                    "  ...\n" +
                    "]\n" +
                    "\n" +
                    "Имя задачи: \"%s\"\n" +
                    "Комментарий: \"%s\"";

    public List<TaskDto> getTasksDtoByUserId(Long id) {
        return taskMapper.entityListToDtoList(getTasksByUserIdOrNull(id));
    }

    public List<Task> getTasksByUserIdOrNull(Long id) {
        return taskRepository.findTasksByUserEntityIdAndParentIsNull(id)
                .orElse(null);
    }

    public TaskDto getTaskDtoById(Long id) {
        return taskMapper.entityToDto(getTaskByIdOrNull(id));
    }

    public Task getTaskByIdOrNull(Long id) {
        return taskRepository.findById(id).orElse(null);
    }

    public TaskDto createTaskByDto(TaskDto taskDto) {
        return taskMapper.entityToDto(
                createTaskOrNull(
                        taskMapper.dtoToEntity(taskDto)
                ));
    }

    public Task createTaskOrNull(Task task) {
        return taskRepository.save(task);
    }

    public TaskDto updateTask(TaskDto taskDto) {
        if (taskDto.getId() == null || !CollectionUtils.isEmpty(taskDto.getChildTasks())) {
            return null;
        }

        return taskMapper.entityToDto(
                taskRepository.save(
                        taskMapper.dtoToEntity(taskDto)
                ));
    }

    public void deleteTaskById(Long id) {
        taskRepository.deleteById(id);
    }

    public void deleteAllById(Iterable<? extends Long> ids) {
        taskRepository.deleteAllById(ids);
    }

    @Transactional
    public List<TaskDto> generateSubtasksOrNull(TaskDto taskDto) {
        try {
            List<Task> taskList = getTaskList(taskDto.getName(), taskDto.getComment());

            for (Task task : taskList) {
                task.setStatus(Status.PLANNED);
                task.setUserEntity(userMapper.dtoToEntity(taskDto.getUser()));
                task.setParent(new Task().setId(taskDto.getId()));
            }
            if (taskDto.getUser() != null) {
                return taskMapper.entityListToDtoList(taskRepository.saveAll(taskList));
            } else {
                return taskMapper.entityListToDtoList(taskList);
            }

        } catch (JsonProcessingException e) {
            log.error("TASK_SUBTASK_GENERATION_ERROR");
            return null;
        }
    }

    @Transactional
    public List<Task> generateSubtasksByIdOrNull(Long taskId) {
        try {
            Task task = getTaskByIdOrNull(taskId);
            List<Task> taskList = getTaskList(task.getName(), task.getComment());

            for (Task taskObj : taskList) {
                taskObj.setStatus(Status.PLANNED);
                taskObj.setUserEntity(task.getUserEntity());
                taskObj.setParent(task);
            }
            return taskRepository.saveAll(taskList);
        } catch (JsonProcessingException e) {
            log.error("TASK_SUBTASK_GENERATION_ERROR");
            return null;
        }
    }

    private List<Task> getTaskList(String name, String comment) throws JsonProcessingException {
        String response =
                openAiProviderProcessor.fetchResponse(
                        components.getAiKey(),
                        String.format(AI_REQUEST, name, comment),
                        PomidoroTelegramBot.BOT_IDENTIFIER,
                        this.getClass().getSimpleName()
                );

        List<Task> taskList = taskMapper.dtoListToEntityList(objectMapper.readValue(
                response,
                new TypeReference<List<TaskDto>>() {
                }
        ));
        return taskList;
    }

    public List<Task> getParentTasksByUserId(long userId, Pageable paging) {
        return taskRepository.findAllByUserEntityIdAndParentIsNull(userId, paging);
    }

    public List<Task> getParentDoneTasksByUserId(long userId, Pageable paging) {
        return taskRepository.findAllNotDoneByUserEntityIdAndParentIsNull(userId, paging);
    }

    public List<Task> getChildTasksByTaskId(long taskId, Pageable paging) {
        return taskRepository.findAllByParentId(taskId, paging);
    }

    public List<Task> getTasksByTimerId(long timerId) {
        return taskRepository.findAllByTimerId(timerId);
    }

    public List<TaskDto> saveAllTasks(List<TaskDto> dtoList) {
        return taskMapper.entityListToDtoList(
                taskRepository.saveAll(taskMapper.dtoListToEntityList(dtoList)));
    }
}
