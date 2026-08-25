package com.projectflow.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.platform.win32.Crypt32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinCrypt;
import com.sun.jna.platform.win32.WinCrypt.DATA_BLOB;

/**
 * Windows current-user DPAPI store.
 *
 * The encrypted blob is stored below configRoot/credentials. DPAPI is called
 * with flags=0, so it is bound to the current Windows user and machine; the
 * LOCAL_MACHINE flag is intentionally never used.
 */
public final class WindowsDpapiProviderCredentialStore implements ProviderCredentialStore {
    private static final String PREFIX = "win-dpapi:user:v1:";
    private static final int MAX_SECRET_BYTES = 64 * 1024;
    private static final int MAX_BLOB_BYTES = 256 * 1024;

    private final Path configRoot;

    public WindowsDpapiProviderCredentialStore(Path configRoot) {
        if (configRoot == null) throw new IllegalArgumentException("configRoot is required");
        this.configRoot = configRoot.toAbsolutePath().normalize();
        if (this.configRoot.getParent() == null) {
            throw new IllegalArgumentException("configRoot cannot be a filesystem root");
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    @Override
    public boolean available() {
        return isWindows();
    }

    @Override
    public String writeAndVerify(UUID providerId, String secret) {
        ensureWindows();
        UUID id = requireProviderId(providerId);
        byte[] plain = utf8Secret(secret);
        String ref = PREFIX + id;
        try {
            byte[] encrypted = protect(plain);
            writeBlob(ref, encrypted);
            String readBack = read(ref);
            byte[] readBackBytes = readBack.getBytes(StandardCharsets.UTF_8);
            try {
                if (!MessageDigest.isEqual(plain, readBackBytes)) {
                    delete(ref);
                    throw new ProviderCredentialStoreException("SECRET_STORE_VERIFY_FAILED", "凭据存储回读校验失败。");
                }
            } finally {
                clear(readBackBytes);
                clear(encrypted);
            }
            return ref;
        } catch (ProviderCredentialStoreException failure) {
            throw failure;
        } catch (RuntimeException | IOException failure) {
            throw new ProviderCredentialStoreException("SECRET_STORE_WRITE_FAILED", "凭据存储写入失败。", failure);
        } finally {
            clear(plain);
        }
    }

    @Override
    public String read(String secretRef) {
        ensureWindows();
        Path file = credentialFile(secretRef);
        byte[] encrypted;
        try {
            rejectReparsePath(file);
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProviderCredentialStoreException("SECRET_NOT_FOUND", "凭据不存在或已被删除。");
            }
            encrypted = readBoundedBlob(file);
        } catch (ProviderCredentialStoreException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new ProviderCredentialStoreException("SECRET_STORE_READ_FAILED", "凭据存储读取失败。", failure);
        }
        try {
            byte[] plain = unprotect(encrypted);
            try {
                return new String(plain, StandardCharsets.UTF_8);
            } finally {
                clear(plain);
            }
        } finally {
            clear(encrypted);
        }
    }

    @Override
    public void delete(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) return;
        ensureWindows();
        Path file = credentialFile(secretRef);
        try {
            rejectReparsePath(file);
            Files.deleteIfExists(file);
        } catch (ProviderCredentialStoreException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new ProviderCredentialStoreException("SECRET_STORE_DELETE_FAILED", "凭据清理失败。", failure);
        }
    }

    @Override
    public Status status(String secretRef) {
        if (!isWindows()) return Status.UNAVAILABLE;
        if (secretRef == null || secretRef.isBlank()) return Status.MISSING;
        final Path file;
        try {
            file = credentialFile(secretRef);
        } catch (ProviderCredentialStoreException failure) {
            return Status.INVALID;
        }
        try {
            rejectReparsePath(file);
            if (!Files.exists(file)) return Status.MISSING;
            if (Files.isSymbolicLink(file)) return Status.INVALID;
            long size = Files.size(file);
            return size > 0 && size <= MAX_BLOB_BYTES ? Status.CONFIGURED : Status.INVALID;
        } catch (IOException failure) {
            return Status.INVALID;
        }
    }

    private void ensureWindows() {
        if (!isWindows()) {
            throw new ProviderCredentialStoreException(
                "SECRET_STORE_UNAVAILABLE", "当前运行平台未提供 Windows DPAPI 凭据存储。"
            );
        }
    }

    private Path credentialFile(String secretRef) {
        if (secretRef == null || !secretRef.startsWith(PREFIX)) {
            throw new ProviderCredentialStoreException("SECRET_REF_INVALID", "凭据引用无效。");
        }
        UUID providerId;
        try {
            providerId = UUID.fromString(secretRef.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new ProviderCredentialStoreException("SECRET_REF_INVALID", "凭据引用无效。", exception);
        }
        Path credentials = configRoot.resolve("credentials").normalize();
        Path file = credentials.resolve(providerId + ".dpapi").normalize();
        if (!credentials.startsWith(configRoot) || !file.startsWith(credentials)) {
            throw new ProviderCredentialStoreException("SECRET_REF_INVALID", "凭据引用无效。");
        }
        return file;
    }

    private void writeBlob(String secretRef, byte[] encrypted) throws IOException {
        Path credentials = configRoot.resolve("credentials").normalize();
        if (!credentials.startsWith(configRoot)) {
            throw new ProviderCredentialStoreException("SECRET_STORE_PATH_INVALID", "凭据存储目录无效。");
        }
        rejectReparsePath(configRoot);
        Files.createDirectories(credentials);
        rejectReparsePath(credentials);
        Path target = credentialFile(secretRef);
        rejectReparsePath(target);
        Path temporary = Files.createTempFile(credentials, ".provider-credential-", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(ByteBuffer.wrap(encrypted));
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new ProviderCredentialStoreException("SECRET_STORE_ATOMIC_MOVE_UNSUPPORTED", "凭据存储不支持原子替换。", unsupported);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private byte[] protect(byte[] plain) {
        DATA_BLOB input = new DATA_BLOB(plain);
        DATA_BLOB output = new DATA_BLOB();
        input.write();
        try {
            boolean ok = Crypt32.INSTANCE.CryptProtectData(input, "ProjectFlow Provider", null, null, null, 0, output);
            if (!ok) throw new ProviderCredentialStoreException("SECRET_DPAPI_PROTECT_FAILED", "Windows 凭据保护失败。");
            output.read();
            byte[] encrypted = output.getData();
            if (encrypted == null || encrypted.length == 0 || encrypted.length > MAX_BLOB_BYTES) {
                throw new ProviderCredentialStoreException("SECRET_DPAPI_PROTECT_FAILED", "Windows 凭据保护结果无效。");
            }
            return encrypted;
        } catch (ProviderCredentialStoreException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ProviderCredentialStoreException("SECRET_DPAPI_PROTECT_FAILED", "Windows 凭据保护失败。", failure);
        } finally {
            clearBlob(input);
            freeBlob(output);
        }
    }

    private byte[] unprotect(byte[] encrypted) {
        DATA_BLOB input = new DATA_BLOB(encrypted);
        DATA_BLOB output = new DATA_BLOB();
        PointerByReference description = new PointerByReference();
        input.write();
        try {
            boolean ok = Crypt32.INSTANCE.CryptUnprotectData(input, description, null, null, null, 0, output);
            if (!ok) throw new ProviderCredentialStoreException("SECRET_DPAPI_UNPROTECT_FAILED", "Windows 凭据读取失败。");
            output.read();
            byte[] plain = output.getData();
            if (plain == null || plain.length == 0 || plain.length > MAX_SECRET_BYTES) {
                throw new ProviderCredentialStoreException("SECRET_DPAPI_UNPROTECT_FAILED", "Windows 凭据内容无效。");
            }
            return plain;
        } catch (ProviderCredentialStoreException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ProviderCredentialStoreException("SECRET_DPAPI_UNPROTECT_FAILED", "Windows 凭据读取失败。", failure);
        } finally {
            clearBlob(input);
            freeBlob(output);
            if (description.getValue() != null) Kernel32.INSTANCE.LocalFree(description.getValue());
        }
    }

    private byte[] readBoundedBlob(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size <= 0 || size > MAX_BLOB_BYTES) {
                throw new ProviderCredentialStoreException("SECRET_BLOB_INVALID", "凭据存储内容无效。");
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining()) {
                int count = channel.read(buffer);
                if (count < 0) {
                    throw new ProviderCredentialStoreException("SECRET_BLOB_INVALID", "凭据存储内容无效。");
                }
            }
            return buffer.array();
        }
    }

    /** Rejects symlinks and Windows junction/reparse points in every existing component. */
    private void rejectReparsePath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) current = absolute.getFileSystem().getPath("");
        for (Path name : absolute) {
            current = current.resolve(name);
            try {
                if (Files.isSymbolicLink(current)) {
                    throw new ProviderCredentialStoreException("SECRET_STORE_PATH_INVALID", "凭据存储路径无效。");
                }
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    BasicFileAttributes attributes = Files.readAttributes(
                        current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
                    );
                    if (attributes.isOther()) {
                        throw new ProviderCredentialStoreException("SECRET_STORE_PATH_INVALID", "凭据存储路径无效。");
                    }
                }
            } catch (NoSuchFileException ignored) {
                // A not-yet-created child is safe; existing ancestors were checked.
            } catch (IOException failure) {
                throw new ProviderCredentialStoreException("SECRET_STORE_PATH_INVALID", "凭据存储路径无效。", failure);
            }
        }
    }

    private static UUID requireProviderId(UUID providerId) {
        if (providerId == null) {
            throw new ProviderCredentialStoreException("SECRET_PROVIDER_ID_INVALID", "Provider 标识无效。");
        }
        return providerId;
    }

    private static byte[] utf8Secret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new ProviderCredentialStoreException("SECRET_EMPTY", "凭据不能为空。");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SECRET_BYTES) {
            clear(bytes);
            throw new ProviderCredentialStoreException("SECRET_TOO_LARGE", "凭据长度超出允许范围。");
        }
        return bytes;
    }

    private static void clearBlob(DATA_BLOB blob) {
        if (blob != null && blob.pbData != null && blob.cbData > 0) blob.pbData.clear(blob.cbData);
    }

    private static void freeBlob(DATA_BLOB blob) {
        if (blob != null && blob.pbData != null) {
            Kernel32.INSTANCE.LocalFree(blob.pbData);
            blob.pbData = null;
        }
    }

    private static void clear(byte[] value) {
        if (value != null) Arrays.fill(value, (byte) 0);
    }
}
