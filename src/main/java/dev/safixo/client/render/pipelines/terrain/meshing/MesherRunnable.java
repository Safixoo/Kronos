package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionResult;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionTask;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MesherRunnable implements Runnable {
	private final ConcurrentLinkedQueue<SectionTask> tasks = new ConcurrentLinkedQueue<>();
	private final ArrayBlockingQueue<SectionResult> results = new ArrayBlockingQueue<>(WorldManager.MAX_TASK_CONCURRENTLY);

	private final Semaphore semaphore = new Semaphore(0);
	private final SectionMesher mesher = new SectionMesher();

	public MesherRunnable() {
	}

	public void addTasks(List<SectionTask> tasks) {
		this.tasks.addAll(tasks);
		this.semaphore.release(tasks.size());
	}

	public int getTaskCount() {
		return this.semaphore.availablePermits();
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
				this.semaphore.acquire();
				SectionTask task = this.tasks.poll();

				if (task == null) {
					continue;
				}

				SectionResult result = this.mesher.buildMesh(task);

				this.results.put(result);
			} catch (InterruptedException e) {
				thread.interrupt();
				break;
			}
		}
	}
}
