package pl.silver.canvas.javity;

import java.util.concurrent.atomic.AtomicLong;

public abstract class DrawJobAbstractImpl implements DrawJob{
	private static AtomicLong lastId = new AtomicLong(0);
	
	private final long id;
	public DrawJobAbstractImpl() {
		id = lastId.incrementAndGet();
	}
	
	@Override
	public long getId() {
		return id;
	}
}
