package com.weaponsmodaddon.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Include / exclude lists of entity ids ({@code minecraft:cow}) or tags ({@code #minecraft:animals}).
 */
public final class EntityTagMatcher {
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();

	private transient @Nullable List<Predicate<EntityType<?>>> includePredicates;
	private transient @Nullable List<Predicate<EntityType<?>>> excludePredicates;

	public boolean matches(LivingEntity entity, boolean matchAllWhenIncludeEmpty) {
		resolve();
		EntityType<?> type = entity.getType();
		for (Predicate<EntityType<?>> excludePredicate : excludePredicates) {
			if (excludePredicate.test(type)) {
				return false;
			}
		}
		if (includePredicates.isEmpty()) {
			return matchAllWhenIncludeEmpty;
		}
		for (Predicate<EntityType<?>> includePredicate : includePredicates) {
			if (includePredicate.test(type)) {
				return true;
			}
		}
		return false;
	}

	void sanitize() {
		if (include == null) {
			include = new ArrayList<>();
		}
		if (exclude == null) {
			exclude = new ArrayList<>();
		}
		includePredicates = null;
		excludePredicates = null;
	}

	private void resolve() {
		if (includePredicates != null && excludePredicates != null) {
			return;
		}
		includePredicates = resolveList(include);
		excludePredicates = resolveList(exclude);
	}

	private static List<Predicate<EntityType<?>>> resolveList(List<String> entries) {
		List<Predicate<EntityType<?>>> predicates = new ArrayList<>();
		for (String entry : entries) {
			Predicate<EntityType<?>> predicate = parseEntry(entry);
			if (predicate != null) {
				predicates.add(predicate);
			}
		}
		return predicates;
	}

	private static @Nullable Predicate<EntityType<?>> parseEntry(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String entry = raw.trim();
		if (entry.startsWith("#")) {
			Identifier id = Identifier.tryParse(entry.substring(1));
			if (id == null) {
				return null;
			}
			TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, id);
			return type -> type.builtInRegistryHolder().is(tag);
		}
		Identifier id = Identifier.tryParse(entry);
		if (id == null) {
			return null;
		}
		return type -> id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}
}
