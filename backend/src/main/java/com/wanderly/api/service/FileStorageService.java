package com.wanderly.api.service;

import com.wanderly.api.config.WanderlyProperties;
import com.wanderly.api.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Stores journal photos on the local disk and serves them from /uploads/**.
 * Swap for S3 / Cloudinary / Firebase Storage in production.
 */
@Service
public class FileStorageService {

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/heic", ".heic");

    private final Path root;

    public FileStorageService(WanderlyProperties props) throws IOException {
        this.root = Path.of(props.uploads().dir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Path root() {
        return root;
    }

    /** Saves the image under a random name and returns its public path, e.g. "/uploads/3f2a….jpg". */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("File is empty");
        String ext = ALLOWED.get(file.getContentType());
        if (ext == null) throw ApiException.badRequest("Only JPEG, PNG, WEBP or HEIC images are allowed");
        String name = UUID.randomUUID() + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store file", e);
        }
        return "/uploads/" + name;
    }
}
