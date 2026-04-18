package dev.safixo.client.render.gfx.util;

import dev.safixo.client.render.gfx.buffer.GlBuffer;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

public class GlBufferHelper {
	public static void copyBufferToBuffer(GlBuffer from, GlBuffer to, int offsetFrom, int offsetTo, int size) {
		GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, from.getHandle());
		GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, to.getHandle());

		GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, offsetFrom, offsetTo, size);

		GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
		GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
	}
}
