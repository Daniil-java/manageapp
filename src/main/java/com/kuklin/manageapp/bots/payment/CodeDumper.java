package com.kuklin.manageapp.bots.payment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CodeDumper {
    public static void main(String[] args) {
        Path sourceDir = Paths.get(
                "C:\\Users\\ASUS\\IdeaProjects\\manageapp\\src\\main\\java\\com\\kuklin\\manageapp\\bots\\caloriebot"
        );
        Path outputFile = Paths.get(
                "C:\\Users\\ASUS\\Desktop\\calorie-code-dump.txt"
        );

        try {
            dumpCode(sourceDir, outputFile);
        } catch (IOException e) {
            System.out.println("[EXCEPTION] Во время выполнения dumpCode случилась ошибка:");
            e.printStackTrace();
        }

        System.out.println("Проверь файл: " + outputFile.toAbsolutePath());
    }

    /**
     * Собирает весь код из указанной папки (рекурсивно) и
     * записывает в один текстовый файл.
     *
     * @param sourceDir  папка с исходниками
     * @param outputFile путь к выходному .txt файлу
     */
    public static void dumpCode(Path sourceDir, Path outputFile) throws IOException {
        System.out.println("=== dumpCode START ===");
        System.out.println("sourceDir  = " + sourceDir.toAbsolutePath());
        System.out.println("outputFile = " + outputFile.toAbsolutePath());

        // 0. Проверяем, что исходная папка существует
        if (!Files.exists(sourceDir)) {
            System.out.println("[ERROR] Папка с исходниками не существует: " + sourceDir.toAbsolutePath());
            return;
        }
        if (!Files.isDirectory(sourceDir)) {
            System.out.println("[ERROR] Это не папка: " + sourceDir.toAbsolutePath());
            return;
        }

        // 1. Гарантируем, что директория под файл существует
        Path parent = outputFile.getParent();
        if (parent != null) {
            System.out.println("Проверяю директорию для output файла: " + parent.toAbsolutePath());
            Files.createDirectories(parent);
            System.out.println("Директория существует/создана: " + parent.toAbsolutePath());
        } else {
            System.out.println("У output файла нет родительской директории (parent == null)");
        }

        // 2. Собираем список .java файлов
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            javaFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        System.out.println("Найдено .java файлов: " + javaFiles.size());
        for (Path file : javaFiles) {
            System.out.println("  - " + file.toAbsolutePath());
        }

        if (javaFiles.isEmpty()) {
            System.out.println("[WARN] В папке нет .java файлов. Всё равно создаю пустой файл вывода.");
        }

        // 3. Пишем в выходной файл (создаём или перетираем)
        System.out.println("Открываю output файл на запись...");
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            System.out.println("Output файл открыт: " + outputFile.toAbsolutePath());

            for (Path file : javaFiles) {
                Path relative = sourceDir.relativize(file);
                System.out.println("Записываю файл: " + relative);

                writer.write("========== " + relative + " ==========");
                writer.newLine();
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
                writer.newLine();
                writer.newLine();
            }

            writer.flush();
            System.out.println("Запись завершена, writer.flush() вызван.");
        }

        System.out.println("=== dumpCode END ===");
    }


}
