package com.valor.auth;

import com.valor.response.ApiResponse;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Private, per-account photos. Images are decoded and resized before storage. */
@RestController
@RequestMapping("/api/v1/profile/photo")
class ProfilePhotoController {
    private final AssetIdentityAccess identities;
    private final WorkflowIdentityAccess profiles;
    private final Path root;
    ProfilePhotoController(AssetIdentityAccess identities, WorkflowIdentityAccess profiles,
            @Value("${app.profile-photos.local-root:./data/profile-photos}") String root) {
        this.identities = identities;
        this.profiles = profiles;
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }
    record Photo(String dataUri) {}
    @ExceptionHandler(IllegalArgumentException.class)
    org.springframework.http.ResponseEntity<ApiResponse<Object>> invalid(IllegalArgumentException error) {
        return org.springframework.http.ResponseEntity.badRequest().body(ApiResponse.error(error.getMessage(), 400));
    }
    @ExceptionHandler(javax.imageio.IIOException.class)
    org.springframework.http.ResponseEntity<ApiResponse<Object>> corruptImage() {
        return org.springframework.http.ResponseEntity.badRequest().body(ApiResponse.error("This photo cannot be read. Choose another JPEG or PNG.", 400));
    }
    private Path target() {
        User actor = identities.actor();
        if (actor.getRole() == Role.CUSTOMER) identities.requireActiveCustomerUser(actor);
        else if (actor.getRole() == Role.TECHNICIAN) profiles.technician(actor);
        else throw new org.springframework.security.access.AccessDeniedException("Access denied");
        return root.resolve(actor.getId() + ".jpg");
    }
    private Photo photo(Path target) throws IOException {
        return new Photo(Files.exists(target) ? "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(target)) : null);
    }
    @GetMapping
    ApiResponse<Photo> get() throws IOException { return ApiResponse.success("Profile photo", photo(target()), 200); }
    @PostMapping(consumes = "multipart/form-data")
    ApiResponse<Photo> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Path target = target();
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("Choose a photo smaller than 5 MB.");
        BufferedImage source;
        try (var stream = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Choose a JPEG or PNG photo.");
            var reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName();
                if (!(format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("PNG"))) throw new IllegalArgumentException("Choose a JPEG or PNG photo.");
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 25_000_000) throw new IllegalArgumentException("Choose a photo below 25 megapixels.");
                source = reader.read(0);
            } finally { reader.dispose(); }
        }
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage resized = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        var graphics = resized.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 256, 256);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int x = (source.getWidth() - side) / 2, y = (source.getHeight() - side) / 2;
            graphics.drawImage(source, 0, 0, 256, 256, x, y, x + side, y + side, null);
        } finally { graphics.dispose(); }
        Files.createDirectories(root);
        Path temporary = Files.createTempFile(root, "photo-", ".jpg");
        try {
            ImageIO.write(resized, "jpg", temporary.toFile());
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return ApiResponse.success("Profile photo saved", photo(target), 200);
    }
    @DeleteMapping
    ApiResponse<Photo> remove() throws IOException {
        Files.deleteIfExists(target());
        return ApiResponse.success("Profile photo removed", new Photo(null), 200);
    }
}
