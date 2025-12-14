package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.model.Modifier;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipResourcePackLoader extends ResourcePackLoader {

    private static final Pattern ZIPFILE_EDIT_SUFFIX_PATTERN = Pattern.compile("-edit(\\d*)");

    private Path zipPath;

    @Override
    public void load(Path path) throws IOException {

        this.zipPath = path;

        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {

            validateResourcePackStructure(zipFs.getPath(""));
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null)
                readResourcePackEntry(entry.getName(), entry, zis);

        } catch (IOException e) {

            throw new RuntimeException(e);
        }

        validateLoadedModifiers();
    }

    @Override
    public void save(Map<String, List<ColorData>> colorChanges, int biomeKey, BiConsumer<String, Double> progressCallback) throws MissingResourceException, IOException {

        if (zipPath == null) throw new RuntimeException("No resource pack loaded.");

        zipPath = determineTargetPath();

        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {

            persistColorChanges(colorChanges, biomeKey, zipFs, "", progressCallback);
        }
    }

    /**
     * Processes a single entry in the resource pack.
     * Determines the type of entry and delegates processing accordingly.
     *
     * @param entryName The name of the entry.
     * @param entry The ZipEntry object.
     * @param zis The ZipInputStream for reading the entry data.
     * @throws IOException If an I/O error occurs.
     */
    private void readResourcePackEntry(String entryName, ZipEntry entry, ZipInputStream zis) throws IOException {

        // Process base biome mappings file
        if (polytoneMappings.toString().equals(entryName))
            readBiomeMappingsEntry(zis.readAllBytes());

        readEntry(entryName, entry, zis, polytoneBlockModifiersRoot, blockModifiers);
        readEntry(entryName, entry, zis, polytoneDimensionsModifiersRoot, dimensionsModifiers);
        readEntry(entryName, entry, zis, polytonFluidModifiersRoot, fluidModifiers);
        readEntry(entryName, entry, zis, polytonParticleModifiersRoot, particleModifiers);
    }

    private void readEntry(String entryName, ZipEntry entry, ZipInputStream zis, Path root, Map<String, Modifier> modifiers) throws IOException {

        if (entryName.startsWith(root.toString())){

            var assetName = entry.getName().replaceAll(root.toString() + "/","");
            readEntry(assetName, zis.readAllBytes(), modifiers);
        }
    }

    /**
     * Determines the target path for saving the resource pack.
     * Creates a new version of the resource pack if necessary.
     *
     * @return The target path for the resource pack.
     * @throws IOException If an I/O error occurs.
     */
    private Path determineTargetPath() throws IOException {

        Path targetPath = zipPath;
        String fileName = zipPath.getFileName().toString();
        Matcher matcher = ZIPFILE_EDIT_SUFFIX_PATTERN.matcher(fileName);

        if (!matcher.find()) {

            // No "-edit" suffix exists, create first edit copy
            String baseName = getBaseFileName(fileName);
            String extension = getFileExtension(fileName);
            targetPath = zipPath.getParent().resolve(baseName + "-edit" + extension);
            Files.copy(zipPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } else {

            // Has "-edit" or "-editN", check if locked
            if (isFileLocked(zipPath)) {

                // File is locked, create next edit version
                targetPath = getNextEditPath(zipPath);
                Files.copy(zipPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return targetPath;
    }

    /**
     * Checks if the specified file is locked.
     *
     * @param original The path to the file to check.
     * @return True if the file is locked, false otherwise.
     */
    private boolean isFileLocked(Path original) {

        Path lockTestPath = original.getParent().resolve(original.getFileName().toString() + ".locktest");

        try {
            Files.move(original, lockTestPath, StandardCopyOption.ATOMIC_MOVE);
            Files.move(lockTestPath, original, StandardCopyOption.ATOMIC_MOVE);
            return false; // Successfully moved and back, file is not locked
        } catch (IOException e) {
            // Failed to move, file is locked
            try {
                // Clean up if lockTestPath was created
                Files.deleteIfExists(lockTestPath);
            } catch (IOException ignored) {}
            return true;
        }
    }

    /**
     * Generates the next available edit path for the resource pack.
     *
     * @param originalPath The original path of the resource pack.
     * @return The next available edit path.
     */
    private Path getNextEditPath(Path originalPath) {

        String fileName = originalPath.getFileName().toString();
        String baseName = getBaseFileName(fileName);
        String extension = getFileExtension(fileName);
        Path parent = originalPath.getParent();

        int editNumber = 1;
        Path newPath;

        do {
            newPath = parent.resolve(baseName + "-edit" + editNumber + extension);
            editNumber++;
        } while (Files.exists(newPath));

        return newPath;
    }

    /**
     * Extracts the base file name from the given file name.
     *
     * @param fileName The file name to process.
     * @return The base file name.
     */
    private String getBaseFileName(String fileName) {
        Matcher matcher = ZIPFILE_EDIT_SUFFIX_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return fileName.substring(0, matcher.start());
        }

        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    /**
     * Extracts the file extension from the given file name.
     *
     * @param fileName The file name to process.
     * @return The file extension.
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }

    @Override
    public Path getResourcePackPath() {
        return zipPath;
    }
}