package io.downloadmod.install;

import io.downloadmod.api.ContentType;
import io.downloadmod.api.Models;

import java.util.ArrayList;
import java.util.List;

public final class DownloadQueue {
	public record Item(Models.Hit hit, ContentType type) {
	}

	private static final List<Item> ITEMS = new ArrayList<>();

	private DownloadQueue() {
	}

	public static List<Item> items() {
		return ITEMS;
	}

	public static int size() {
		return ITEMS.size();
	}

	public static boolean contains(String projectId) {
		return ITEMS.stream().anyMatch(i -> i.hit().projectId().equals(projectId));
	}

	public static boolean toggle(Models.Hit hit, ContentType type) {
		if (ITEMS.removeIf(i -> i.hit().projectId().equals(hit.projectId()))) {
			return false;
		}
		ITEMS.add(new Item(hit, type));
		return true;
	}

	public static void removeAll(List<Item> done) {
		for (Item d : done) {
			ITEMS.removeIf(i -> i.hit().projectId().equals(d.hit().projectId()));
		}
	}
}
