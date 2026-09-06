package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.SectionFlags;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.SectionSet;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.cull.CullerQueue;
import dev.safixo.client.render.pipelines.terrain.meshing.data.FakeInlinedBiome;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionResult;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionTask;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.collection.ObjectPooler;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.core.hooks.AsyncBlockHook;
import dev.safixo.core.hooks.RenderGlobalHook;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.Vec3Pool;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.List;

public class MesherManager {
	private static final long MAX_TIME = (long) (1E+9D / 240);

	private final VertexWriter[] writers = new VertexWriter[MeshDirection.COUNT + 1];

	private final Vec3Pool vecPool = new Vec3Pool(300, 3000);
	private final ModelColorizer colorizer = new ModelColorizer();
	private final FakeInlinedBiome inlinedBiome = new FakeInlinedBiome(Integer.MAX_VALUE);

	private final ObjectPooler<SectionCache> caches = new ObjectPooler<>(new ObjectPooler.ObjectFactory<SectionCache>() {
		@Override
		public SectionCache create() {
			return new SectionCache(colorizer, inlinedBiome, vecPool);
		}
	}, WorldManager.MAX_TASK_CONCURRENTLY * 2);

	private final MesherRunnable mesherRunnable = new MesherRunnable(this.vecPool);
	private final Thread meshThread;

	private long lastFrameNano, lastFrameBuildTime, lerpFrameBudget;

	private final List<SectionRender> dirtySections = new ReferenceArrayList<>();

	private final List<SectionTask> regularTasks = new ReferenceArrayList<>();
	private final List<SectionTask> importantTasks = new ReferenceArrayList<>();

	private int importantTasksWaiting;
	private int currentTaskId;

	public MesherManager() {
		for (int i = 0; i < this.writers.length; i++) {
			this.writers[i] = new VertexWriter(512);
			this.writers[i].reset();
			this.writers[i].setQuadReceiver(DefaultVertexFormats.TERRAIN_FORMAT);
		}

		this.meshThread = new Thread(this.mesherRunnable);
		this.meshThread.setName("AsyncMesher");
		this.meshThread.start();

		AsyncBlockHook.setMeshingThread(this.meshThread);
	}

	public void queueRebuilds(CullerQueue cullerQueue, WorldManager manager) {
		int rebuildSize = cullerQueue.getRebuildIndex();
		int maxSize = Math.min(WorldManager.MAX_UPDATES_TRIES, rebuildSize);

		SectionSet sectionSet = manager.getSectionSet();
		PrimitivesFlags.processLeavesSolid();

		long current = System.nanoTime();
		long budget = this.calculateFrameBudgetNs(current);

		int updateIndex = 0, nonEmptyUpdates = 0;

		while (updateIndex < maxSize && nonEmptyUpdates < WorldManager.MAX_FULL_UPDATES) {
			SectionRender section = cullerQueue.getDirtySection(manager, sectionSet, updateIndex++);

			if (this.mesherRunnable.getTaskCount() >= WorldManager.MAX_TASK_CONCURRENTLY) {
				break;
			}

			if (section.isDirty()) {
				boolean nonEmpty = this.setupTask(manager, this.dirtySections, section);

				if (nonEmpty) {
					nonEmptyUpdates++;
				}
			}

			if (this.isBudgetOver(current, budget)) {
				int distance = distanceToSection(section, manager.getCamera());
				int minUpdates = distance <= 20*20 ? 2 : 1;

				if (nonEmptyUpdates >= minUpdates) {
					break;
				}
			}
		}

		long diff = System.nanoTime() - current;
		float partialTick = RenderGlobalHook.PARTIAL_TICK;

		this.lastFrameBuildTime = diff > this.lastFrameBuildTime
			? diff
			: (long) Math.max(diff, this.lastFrameBuildTime - 5 * 1E+6D * partialTick);
		this.lastFrameNano = current;

		List<SectionResult> results = this.mesherRunnable.getResults();
		if (nonEmptyUpdates > 0) {
			manager.markDirty();
		}

		this.queueTasks(manager);
		this.readAsyncResults(manager, results);
	}

	private void queueTasks(WorldManager manager) {
		for (SectionRender section : this.dirtySections) {
			SectionTask task = this.createTask(manager.getWorld(), section, manager.getCamera());

			switch (task.taskType) {
				case IMPORTANT:
					this.importantTasks.add(task);
					break;
				case REGULAR:
					this.regularTasks.add(task);
					break;
			}
		}
		this.dirtySections.clear();

		if (!this.regularTasks.isEmpty() || !this.importantTasks.isEmpty()) {
			this.mesherRunnable.addTasks(this.meshThread, this.regularTasks, this.importantTasks);
		}
		this.importantTasksWaiting += this.importantTasks.size();

		this.regularTasks.clear();
		this.importantTasks.clear();
	}

	private static SectionTask.TaskType getTypeByDistance(int squareDistance) {
		return squareDistance <= 20*20 ? SectionTask.TaskType.IMPORTANT : SectionTask.TaskType.REGULAR;
	}

	public void readAsyncResults(WorldManager manager) {
		this.readAsyncResults(manager, this.mesherRunnable.getResults());
	}

	public void readAsyncResults(WorldManager manager, List<SectionResult> results) {
		if (!results.isEmpty()) {
			manager.markDirty();
		}

		for (SectionResult result : results) {
			SectionTask task = result.task;

			if (task.taskId != this.currentTaskId) {
				result.delete();
				continue;
			}

			if (task.taskType == SectionTask.TaskType.IMPORTANT) {
				this.importantTasksWaiting--;
			}

			SectionRender section = task.section;
			SectionSet sectionSet = manager.getSectionSet();

			if (sectionSet.isInBounds(section.blockX >> 4, section.blockZ >> 4)) {
				task.section.sendBuildResult(manager, result);
			} else {
				result.delete();
			}
			this.caches.push(task.cache);
		}
	}

	public boolean areImportantResultsScheduled() {
		return this.importantTasksWaiting > 0;
	}

	private boolean setupTask(WorldManager manager, List<SectionRender> tasks, SectionRender section) {
		Chunk.isLit = false;

		World world = manager.getWorld();
		Chunk chunk = world.getChunkFromChunkCoords(section.blockX >> 4, section.blockZ >> 4);

		if (this.nullSection(chunk, section)) {
			section.setFlags(SectionFlags.setSolidFaces(section.flags, 0b0));
			section.setFlags(SectionFlags.setPassesNonEmpty(section.flags, 0b0));
			section.setFlags(SectionFlags.setDirty(section.flags, false));
			section.sendFlagsToSet();

			manager.markDirty();
			return false;
		} else {
			section.setFlags(SectionFlags.setDirty(section.flags, false));
			section.sendFlagsToSet();

			tasks.add(section);

			return true;
		}
	}

	public SectionTask createTask(World world, SectionRender section, CameraData camera) {
		SectionTask.TaskType taskType = getTypeByDistance(distanceToSection(section, camera));

		SectionCache cache = this.caches.poll();
		cache.setupCache(world, section.blockX, section.blockY, section.blockZ);

		WorldRenderer.chunksUpdated++;
		SectionTask task = new SectionTask(this.writers);

		task.section = section;
		task.camera = camera;
		task.cache = cache;
		task.taskId = this.currentTaskId;
		task.meshManager = this;
		task.taskType = taskType;

		return task;
	}

	private boolean nullSection(Chunk chunk, SectionRender section) {
		ExtendedBlockStorage centerSection = chunk.getBlockStorageArray()[section.blockY >> 4];
		return centerSection == null || centerSection.isEmpty();
	}

	private long calculateFrameBudgetNs(long current) {
		long currentFrameTimeNs = (current - this.lastFrameNano) - this.lastFrameBuildTime;
		long budget = Math.min(currentFrameTimeNs / 8, MAX_TIME);

		// If the budget is over the one in the current frame, start giving more budget slowly,
		// but if the budget is lower simply give lower budget.
		if (budget > this.lerpFrameBudget && this.lerpFrameBudget != 0L) {
			// adds 10ms of budget per sec.
			budget = (long) Math.min(budget, this.lerpFrameBudget + (RenderGlobalHook.PARTIAL_TICK * 1E+7));
		}

		this.lerpFrameBudget = budget;
		return budget;
	}

	private boolean isBudgetOver(long current, long budget) {
		long timeBuilding = System.nanoTime() - current;
		// if we are over budget, or we have passed 0.2ms more time building than last
		// frame, stop it.
		return timeBuilding >= budget || timeBuilding - (1E+6 / 5) >= this.lastFrameBuildTime;
	}

	private static int distanceToSection(SectionRender render, CameraData camera) {
		int dX = render.blockX - camera.intX + 8;
		int dY = render.blockY - camera.intY + 8;
		int dZ = render.blockZ - camera.intZ + 8;

		return MathExt.square(dX) + MathExt.square(dY) + MathExt.square(dZ);
	}

	public void clear() {
		this.mesherRunnable.clear();
		this.currentTaskId++;
		this.importantTasksWaiting = 0;
	}
}
