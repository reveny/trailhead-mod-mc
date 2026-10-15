package com.reveny.trailhead.worldgen.terrain;

public final class TerrainContext {
    private static volatile TerrainContext current;

    public final long seed;
    public final Climate.Mode mode;
    public final HeightField field;
    public final RiverNetwork rivers;
    public final Groundwater groundwater;
    final CaveNoise caves;

    private TerrainContext(long seed, Climate.Mode mode) {
        this.seed = seed;
        this.mode = mode;
        this.field = new HeightField(seed, mode);
        this.rivers = new RiverNetwork(seed, field);
        this.field.setRivers(rivers);
        this.groundwater = new Groundwater(field);
        this.caves = new CaveNoise(seed);
    }

    public static TerrainContext get(long seed, Climate.Mode mode) {
        TerrainContext context = current;
        if (context == null || !context.matches(seed, mode)) {
            synchronized (TerrainContext.class) {
                context = current;
                if (context == null || !context.matches(seed, mode)) {
                    context = new TerrainContext(seed, mode);
                    current = context;
                }
            }
        }
        return context;
    }

    private boolean matches(long seed, Climate.Mode mode) {
        return this.seed == seed && this.mode == mode;
    }
}
