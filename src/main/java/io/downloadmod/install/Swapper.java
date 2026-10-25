package io.downloadmod.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class Swapper {
	private Swapper() {
	}

	public static boolean apply(List<PlanFile> plan) {
		boolean waiting = false;
		for (PlanFile f : plan) {
			if (f.replaces() == null) {
				continue;
			}
			Path tmp = f.targetDir().resolve(f.filename() + ".dmnew");
			Path fin = f.targetDir().resolve(f.filename());
			try {
				Files.deleteIfExists(f.replaces());
				Files.move(tmp, fin, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				PendingOps.addReplace(f.replaces(), tmp, fin);
				waiting = true;
			}
		}
		return waiting;
	}
}
