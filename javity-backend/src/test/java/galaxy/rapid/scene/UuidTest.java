package galaxy.rapid.scene;

import static org.junit.Assert.*;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UuidTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		UUID id1 = new UUID(100l, 100l);
		UUID id2 = new UUID(100l, 100l);
		
		
		assertTrue(id1.equals(id2));
		assertTrue(id1.hashCode() == id2.hashCode());
		assertFalse(id1 == id2);
	}

}
