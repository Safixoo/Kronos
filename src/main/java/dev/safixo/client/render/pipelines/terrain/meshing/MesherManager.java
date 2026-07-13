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
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class MesherManager {
	private static final long MAX_TIME = (long) (1E+9D / 240);

	private final SectionMesher mesher = new SectionMesher();
	private final VertexWriter[] writers = new VertexWriter[MeshDirection.COUNT + 1];

	private final ObjectPooler<SectionCache> caches = new ObjectPooler<>(new ObjectPooler.ObjectFactory<SectionCache>() {
		@Override
		public SectionCache create() {
			return new SectionCache();
		}
	}, WorldManager.MAX_TASK_CONCURRENTLY + 4);

	private final LinkedBlockingQueue<SectionTask[]> tasks = new LinkedBlockingQueue<>();
	private final LinkedBlockingQueue<SectionResult> results = new LinkedBlockingQueue<>();

	private final MesherRunnable mesherRunnable = new MesherRunnable(this.tasks, this.results);
	private final Thread meshThread;

	private long lastFrameNano;
	private long lastFrameBuildTime;
	private long lerpFrameBudget;

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
		int currentTasks = this.tasks.size();

		SectionSet sectionSet = manager.getSectionSet();

		if (rebuildSize == 0) {
			manager.setTerrainDirty(false);
		}
		PrimitivesFlags.processLeavesSolid();

		long current = System.nanoTime();
		long budget = calculateFrameBudgetNs(current);

		int updateIndex = 0, nonEmptyUpdates = 0;

		List<SectionTask> nonEmptyTasks = new ReferenceArrayList<>();

		while (updateIndex < maxSize && nonEmptyUpdates < WorldManager.MAX_FULL_UPDATES) {
			if (nonEmptyUpdates + currentTasks > WorldManager.MAX_TASK_CONCURRENTLY) {
				currentTasks = this.tasks.size();

				if (nonEmptyUpdates + currentTasks > WorldManager.MAX_TASK_CONCURRENTLY) {
					break;
				}
			}

			long position = BFSQueues.getSectionPos(manager.getCamera(), updateIndex++);

			int sectionX = MathExt.decodeX(position);
			int sectionY = MathExt.decodeY(position);
			int sectionZ = MathExt.decodeZ(position);

			SectionRender section = sectionSet.getSectionInstance(manager, sectionX, sectionY, sectionZ);

			if (section == null) {
				continue;
			}

			if (section.isDirty()) {
				boolean nonEmpty = this.queueTask(nonEmptyTasks, section, manager.getCamera(), manager.worldObj);

				if (nonEmpty) {
					nonEmptyUpdates++;
				}
			}

			// Batch some tasks before sending.
			if (nonEmptyTasks.size() >= 4 || currentTasks == 0) {
				this.tasks.add(nonEmptyTasks.toArray(new SectionTask[0]));
				nonEmptyTasks.clear();
			}

			if (this.isBudgetOver(current, budget)) {
				int distance = distanceToSection(section, manager.getCamera());
				int minUpdates = distance <= 20*20 ? 2 : 1;

				if (nonEmptyUpdates >= minUpdates) {
					break;
				}
			}
		}

		if (!nonEmptyTasks.isEmpty()) {
			this.tasks.add(nonEmptyTasks.toArray(new SectionTask[0]));
		}

		long diff = System.nanoTime() - current;
		float partialTick = RenderGlobalHook.PARTIAL_TICK;

		this.lastFrameBuildTime = diff > this.lastFrameBuildTime
			? diff
			: (long) Math.max(diff, this.lastFrameBuildTime - 5 * 1E+6D * partialTick);
		this.lastFrameNano = current;

		this.readAsyncResults(manager);
	}

	private void readAsyncResults(WorldManager manager) {
		List<SectionResult> results = new ArrayList<>();
		int size = this.results.drainTo(results);

		for (int i = 0; i < size; i++) {
			SectionResult result = results.get(i);
			SectionTask task = result.task;

			task.section.sendBuildResult(manager, result);
			this.caches.push(task.cache);

			manager.setTerrainDirty(true);
		}
	}

	private boolean queueTask(List<SectionTask> tasks, SectionRender section, CameraData camera, World world) {
		Chunk.isLit = false;

		if (this.nullSection(section, world)) {
			section.setFlags(SectionFlags.setSolidFaces(section.flags, 0b0));
			section.setFlags(SectionFlags.setDirty(section.flags, false));
			section.sendFlagsToSet();
			return false;
		} else {
			section.setFlags(SectionFlags.setDirty(section.flags, false));
			section.sendFlagsToSet();
		}

		SectionCache cache = this.caches.poll();
		cache.fillData(world, section.blockX, section.blockY, section.blockZ);

		WorldRenderer.chunksUpdated++;
		SectionTask task = new SectionTask(this.writers);

		task.section = section;
		task.camera = camera;
		task.cache = cache;
		task.mesherManager = this;

		tasks.add(task);

		return true;
	}

	private boolean nullSection(SectionRender section, World world) {
		Chunk centerChunk = world.getChunkFromChunkCoords(section.blockX >> 4, section.blockZ >> 4);
		ExtendedBlockStorage centerSection = centerChunk.getBlockStorageArray()[section.blockY >> 4];

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
		this.tasks.clear();
		this.results.clear();
	}
}
