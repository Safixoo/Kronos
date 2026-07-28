package dev.safixo.client.render.pipelines.terrain.meshing.model.light;


import dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.LightDataAccess;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.flat.FlatLightPipeline;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.smooth.SmoothLightPipeline;

import java.util.EnumMap;

public class LightPipelineProvider {
    private final EnumMap<LightMode, LightPipeline> lighters = new EnumMap<>(LightMode.class);

    public LightPipelineProvider(LightDataAccess cache) {
        this.lighters.put(LightMode.SMOOTH, new SmoothLightPipeline(cache));
        this.lighters.put(LightMode.FLAT, new FlatLightPipeline(cache));
    }

    public LightPipeline getLighter(LightMode type) {
        LightPipeline pipeline = this.lighters.get(type);

        if (pipeline == null) {
            throw new NullPointerException("No lighter exists for mode: " + type.name());
        }

        return pipeline;
    }
}
