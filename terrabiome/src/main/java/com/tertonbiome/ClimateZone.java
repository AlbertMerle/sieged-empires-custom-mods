package com.tertonbiome;

/**
 * Five latitude climate zones from wobbled Z (same cutoffs as temperature bands).
 */
public enum ClimateZone {
	FREEZING,
	COLD,
	TEMPERATE,
	WARM,
	HOT;

	/** Zone at (X, Z) using {@link LatitudeTemperature#effectiveZ}. */
	public static ClimateZone at(int blockX, int blockZ) {
		return atEffectiveZ(LatitudeTemperature.effectiveZ(blockX, blockZ));
	}

	/** Zone for an effective Z that was already computed by the caller. */
	public static ClimateZone atEffectiveZ(double z) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		int[] cuts = c.zoneCutoffs();
		if (z <= cuts[0]) {
			return FREEZING;
		}
		if (z < cuts[1]) {
			return COLD;
		}
		if (z < cuts[2]) {
			return TEMPERATE;
		}
		if (z < cuts[3]) {
			return WARM;
		}
		return HOT;
	}
}
