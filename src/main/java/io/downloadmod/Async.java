package io.downloadmod;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Async {
	private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "downloadmod-worker");
		t.setDaemon(true);
		return t;
	});

	private Async() {
	}

	public static void run(Runnable r) {
		POOL.execute(r);
	}
}
