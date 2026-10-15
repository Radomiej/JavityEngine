package galaxy.rapid.scene;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import galaxy.rapid.screen.ScreenNavigator;

//@RunWith(MockitoJUnitRunner.class)  
public class SceneNavigatorTest {

	ScreenNavigator sceneNavigator;
	TestOneScreen test1, test2;
	TestTwoScreen test3;
	
	@Before
	public void setUp() throws Exception {
		test1 = new TestOneScreen();
		test2 = new TestOneScreen();
		test3 = new TestTwoScreen();
		
	}

	@After
	public void tearDown() throws Exception {
	}

//	@Test
	public void testWithTheSameScreenClass() {		
		initialScreenIsCurrentScreen();		
		setScreenWithFalseDisposeGiveOldSceneForTheSameClassScreen();		
		setScreenWithTrueDisposeGiveNewScreenForTheSameClassScreen();		
	}
	
//	@Test
	public void testWithDifrentScreenClass() {		
		initialScreenIsCurrentScreen();
		setScreenWithDifrentClassGiveNewOneOrNonDisposableOld();		
	}

	private void initialScreenIsCurrentScreen() {
		sceneNavigator = new ScreenNavigator(test1);
		assertEquals(test1, sceneNavigator.getCurrentScreen());
	}

	private void setScreenWithFalseDisposeGiveOldSceneForTheSameClassScreen() {
		assertEquals(test1.getClass(), test2.getClass());
		sceneNavigator.changeScreen(test2, false);
		assertEquals(test1, sceneNavigator.getCurrentScreen());
	}

	private void setScreenWithTrueDisposeGiveNewScreenForTheSameClassScreen() {
		sceneNavigator.changeScreen(test2, true);
		assertEquals(test2, sceneNavigator.getCurrentScreen());
	}

	private void setScreenWithDifrentClassGiveNewOneOrNonDisposableOld() {
		sceneNavigator.changeScreen(test3, false);
		assertEquals(test3, sceneNavigator.getCurrentScreen());
		
		TestOneScreen tempScreen = new TestOneScreen();
		sceneNavigator.changeScreen(tempScreen, true);
		assertEquals(test1, sceneNavigator.getCurrentScreen());
	}

}
