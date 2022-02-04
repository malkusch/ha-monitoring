package de.malkusch.ha.monitoring.infrastructure.persistence;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

final class FileStateStorage implements StateStorage, AutoCloseable {

    private final Path path;
    private final Properties properties;
    private final List<KeyValue> keyValues = new ArrayList<>();

    public FileStateStorage(String path) throws IOException {
        this(Paths.get(path));
    }

    public FileStateStorage(Path path) throws IOException {
        this.path = path;
        if (!Files.exists(path)) {
            Files.createFile(path);
        }

        this.properties = new Properties();

        try (var stream = new FileInputStream(path.toFile())) {
            properties.load(stream);
        }
    }

    private static record KeyValue(String key, DeferredValue<String> value) {
    };

    @Override
    public void persist(String key, Consumer<String> restore, DeferredValue<String> value) {
        var storedValue = (String) properties.get(key);
        if (storedValue != null) {
            restore.accept(storedValue);
        }

        keyValues.add(new KeyValue(key, value));
    }

    @Override
    public void close() throws Exception {
        keyValues.forEach(it -> properties.setProperty(it.key, it.value.value()));
        try (var stream = new FileOutputStream(path.toFile())) {
            properties.store(stream, null);
        }
    }
}
