package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.LightMode;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.LightPipeline;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.LightPipelineProvider;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.data.ArrayLightDataCache;
import dev.safixo.client.render.pipelines.terrain.meshing.model.light.smooth.SmoothLightPipeline;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionResult;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionTask;
import dev.safixo.client.render.vertex.TerrainQuadInterceptor;
import dev.safixo.client.util.memory.MemoryPool;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3Pool;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

public class MesherRunnable implements Runnable {
	private final ConcurrentLinkedDeque<SectionTask> tasks = new ConcurrentLinkedDeque<>();
	private final ArrayBlockingQueue<SectionResult> results = new ArrayBlockingQueue<>(WorldManager.MAX_TASK_CONCURRENTLY * 2);

	private final MemoryPool memoryPool = new MemoryPool(4 << 20, WorldManager.MAX_TASK_CONCURRENTLY * 2);

	private final ArrayLightDataCache lightDataCache = new ArrayLightDataCache();
	private final LightPipelineProvider provider = new LightPipelineProvider(this.lightDataCache);
	private final TerrainQuadInterceptor quadInterceptor = new TerrainQuadInterceptor();

	private final SectionMesher mesher = new SectionMesher(this.quadInterceptor);
	private final Vec3Pool vecPool;

	public MesherRunnable(Vec3Pool pool) {
		this.vecPool = pool;
	}

	public void addTasks(Thread thread, List<SectionTask> regularTasks, List<SectionTask> importantTasks) {
		for (SectionTask task : importantTasks) {
			this.tasks.addFirst(task);
		}

		this.tasks.addAll(regularTasks);
		LockSupport.unpark(thread);
	}

	public int getTaskCount() {
		return this.tasks.size();
	}

	public List<SectionResult> getResults() {
		List<SectionResult> results = new ReferenceArrayList<>();
		this.results.drainTo(results);

		return results;
	}

	public void clear() {
		this.tasks.clear();

		List<SectionResult> results = this.getResults();

		for (SectionResult result : results) {
			result.delete();
		}
	}

	@Override
	public void run() {
		Thread thread = Thread.currentThread();

		while (!thread.isInterrupted()) {
			try {
				SectionTask task;

				while ((task = this.tasks.poll()) != null) {
					SectionResult result = this.mesher.buildMesh(this.lightDataCache, this.provider.getLighter(LightMode.getCurrent()), this.memoryPool, task);

					this.vecPool.clear();
					this.memoryPool.tick();

					this.results.put(result);
				}

				LockSupport.park();
			} catch (InterruptedException e) {
				thread.interrupt();
				break;
			}
		}
	}
}
