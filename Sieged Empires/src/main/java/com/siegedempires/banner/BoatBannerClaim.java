package com.siegedempires.banner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Persistent town/empire banner ownership stamped onto a boat or Shippy Ships vessel
 * the first time a town member boards. Never overwritten after claim.
 */
public record BoatBannerClaim(
		String townId,
		String empireId,
		String baseColor,
		List<String> patterns
) {
	public static final Codec<BoatBannerClaim> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					Codec.STRING.fieldOf("townId").forGetter(BoatBannerClaim::townId),
					Codec.STRING.optionalFieldOf("empireId", "").forGetter(BoatBannerClaim::empireId),
					Codec.STRING.fieldOf("baseColor").forGetter(BoatBannerClaim::baseColor),
					Codec.STRING.listOf().fieldOf("patterns").forGetter(BoatBannerClaim::patterns)
			).apply(instance, BoatBannerClaim::new));

	public boolean hasEmpire() {
		return empireId != null && !empireId.isEmpty();
	}
}
