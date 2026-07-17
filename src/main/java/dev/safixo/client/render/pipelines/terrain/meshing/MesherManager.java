package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.SectionFlags;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.SectionSet;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.cull.BFSQueues;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.List;

public class MesherManager {
	private static final long MAX_TIME = (long) (1E+9D / 240);

	private final VertexWriter[] writers = new VertexWriter[MeshDirection.COUNT + 1];

	private final ObjectPooler<SectionCache> caches = new ObjectPooler<>(new ObjectPooler.ObjectFactory<SectionCache>() {
		@Override
		public SectionCache create() {
			return new SectionCache();
		}
	}, WorldManager.MAX_TASK_CONCURRENTLY * 2);

	private final MesherRunnable mesherRunnable = new MesherRunnable();
	private final Thread meshThread;

	private long lastFrameNano;
	private long lastFrameBuildTime;
	private long lerpFrameBudget;

	private int currentTaskId;

	public MesherManager() {
		for (int i = 0; i < this.writers.length; i++) {
			this.writers[i] = new VertexWriter(512);
			this.writers[i].startDrawing();
			this.writers[i].setVertexFormat(DefaultVertexFormats.TERRAIN_FORMAT);
		}

		this.meshThread = new Thread(this.mesherRunnable);
		this.meshThread.setName("AsyncMesher");
		this.meshThread.start();

		AsyncBlockHook.setMeshingThread(this.meshThread);
	}

	public void queueRebuilds(WorldManager manager) {
		int rebuildSize = BFSQueues.getRebuildIndex();
		int maxSize = Math.min(WorldManager.MAX_UPDATES_TRIES, rebuildSize);

		SectionSet sectionSet = manager.getSectionSet();

		if (rebuildSize == 0) {
			manager.setTerrainDirty(false);
		}
		PrimitivesFlags.processLeavesSolid();

		long current = System.nanoTime();
		long budget = calculateFrameBudgetNs(current);

		int updateIndex = 0, nonEmptyUpdates = 0;
		List<SectionRender> sectionForTasks = new ReferenceArrayList<>();

		while (updateIndex < maxSize && nonEmptyUpdates < WorldManager.MAX_FULL_UPDATES) {
			long position = BFSQueues.getSectionPos(manager.getCamera(), updateIndex++);

			int sectionX = MathExt.decodeX(position);
			int sectionY = MathExt.decodeY(position);
			int sectionZ = MathExt.decodeZ(position);

			SectionRender section = sectionSet.getSectionInstance(manager, sectionX, sectionY, sectionZ);

			if (section == null) {
				continue;
			}

			if (this.mesherRunnable.getTaskCount() >= WorldManager.MAX_TASK_CONCURRENTLY) {
				break;
			}

			if (section.isDirty()) {
				boolean nonEmpty = this.setupTask(manager, sectionForTasks, section);

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

		this.queueTasks(manager, sectionForTasks);
		this.readAsyncResults(manager);
	}

	private void queueTasks(WorldManager manager, List<SectionRender> sectionForTasks) {
		List<SectionTask> tasks = new ReferenceArrayList<>(sectionForTasks.size());
		boolean firstTask = true;

		for (SectionRender section : sectionForTasks) {
			SectionTask task = this.createTask(manager.getWorld(), section, manager.getCamera());
			tasks.add(task);

			if (firstTask) {
				this.mesherRunnable.addTasks(tasks);
				tasks.clear();
				firstTask = false;
			}
		}

		if (!tasks.isEmpty()) {
			this.mesherRunnable.addTasks(tasks);
		}
	}


	private static SectionTask.TaskType getTypeByDistance(int squareDistance) {
		return squareDistance <= 20*20 ? SectionTask.TaskType.IMPORTANT : SectionTask.TaskType.REGULAR;
	}

	private void readAsyncResults(WorldManager manager) {
		List<SectionResult> results = this.mesherRunnable.getResults();

		for (SectionResult result : results) {
			SectionTask task = result.task;

			if (task.taskId != this.currentTaskId) {
				result.delete();
				continue;
			}

			task.section.sendBuildResult(manager, result);
			this.caches.push(task.cache);

			manager.setTerrainDirty(true);
		}
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

			manager.setTerrainDirty(true);
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
		Chunk chunk = world.getChunkFromChunkCoords(section.blockX >> 4, section.blockZ >> 4);

		cache.setupCache(chunk, world, section.blockX, section.blockY, section.blockZ);

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
	}
}
