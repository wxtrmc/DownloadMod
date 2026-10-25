package io.downloadmod.install;

import io.downloadmod.api.ContentType;

import java.nio.file.Path;

public record PlanFile(String displayName, String filename, String url, String sha512, long size, Path targetDir,
					   boolean dependency, ContentType type, Path replaces) {
}
