package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionResult;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionTask;

import java.util.concurrent.LinkedBlockingQueue;

public class MesherRunnable implements Runnable {
	private final LinkedBlockingQueue<SectionTask[]> tasks;
	private final LinkedBlockingQueue<SectionResult> results;

	private final SectionMesher mesher = new SectionMesher();

	public MesherRunnable(LinkedBlockingQueue<SectionTask[]> tasks, LinkedBlockingQueue<SectionResult> results) {
		this.tasks = tasks;
		this.results = results;
	}

	@Override
	public void run() {
		Thread thread = Thread.currentThread();

		while (!thread.isInterrupted()) {
			try {
				SectionTask[] tasks = this.tasks.take();

				for (SectionTask task : tasks) {
					SectionResult result = this.mesher.buildMesh(task);
					this.results.put(result);
				}
			} catch (InterruptedException e) {
				thread.interrupt();
				break;
			}
		}
	}
}
