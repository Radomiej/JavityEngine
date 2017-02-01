package org.javity.engine;

import java.io.IOException;
import java.util.Random;

import org.javity.engine.utilities.NameGenerator;

import com.badlogic.gdx.Gdx;

public enum JRandom {
	INSTANCE;
	private Random random = new Random(123);
	private NameGenerator nameGenerator;

	private JRandom() {
		try {
			nameGenerator = new NameGenerator("randomes/romans.txt");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String randomName(int maxSylabs){
		return nameGenerator.compose(1 + random.nextInt(maxSylabs - 1));
	}
	/**
	 * Set seed on random and unique. After invoke this method repeatability is lost in all next*** methods.
	 */
	public void randomSeed() {
		random.setSeed(System.currentTimeMillis());
	}
	
	/**
	 * The same as {@link java.util.Random#setSeed(long) setSeed} method in
	 * {@link java.util.Random}
	 * 
	 */
	public void setSeed(long seed) {
		random.setSeed(seed);
	}

	/**
	 * Random value from given range
	 * 
	 * @param min
	 *            the minimum value inclusive
	 * @param max
	 *            the maximum value inclusive
	 * @return value in range or 0 if error occured.
	 */
	public float nextFloat(float min, float max) {
		if (min > max) {
			Gdx.app.error(JRandom.class.getSimpleName(),
					"The min value: " + min + " is greater than the max value: " + max);
			return 0;
		}
		float random = nextFloat() * (max - min) + min;
		;
		return random;
	}

	/**
	 * The same as {@link java.util.Random#nextFloat() nextFloat} method in
	 * {@link java.util.Random}
	 * 
	 * @return Returns the next pseudorandom, uniformly distributed float value
	 *         between 0.0 and 1.0 from this random number generator's sequence.
	 */
	public float nextFloat() {
		return random.nextFloat();
	}

	/**
	 * The same as {@link java.util.Random#nextInt() nextInt} method in
	 * {@link java.util.Random}
	 * 
	 * @return Returns the next pseudorandom, uniformly distributed int value
	 *         from this random number generator's sequence. The general
	 *         contract of nextInt is that one int value is pseudorandomly
	 *         generated and returned. All 232 possible int values are produced
	 *         with (approximately) equal probability.
	 */
	public int nextInt() {
		return random.nextInt();
	}

	/**
	 * The same as {@link java.util.Random#nextInt(int) nextInt} method in
	 * {@link java.util.Random}
	 * 
	 * @return Returns the next pseudorandom, uniformly distributed int value
	 *         from this random number generator's sequence. The general
	 *         contract of nextInt is that one int value is pseudorandomly
	 *         generated and returned. All 232 possible int values are produced
	 *         with (approximately) equal probability.
	 */
	public int nextInt(int bound) {
		return random.nextInt(bound);
	}

	/**
	 * The same as {@link java.util.Random#nextBoolean(int) nextBoolean} method
	 * in {@link java.util.Random}
	 * 
	 * @return Returns the next pseudorandom, uniformly distributed boolean
	 *         value from this random number generator's sequence. The general
	 *         contract of nextBoolean is that one boolean value is
	 *         pseudorandomly generated and returned. The values true and false
	 *         are produced with (approximately) equal probability.
	 */
	public boolean nextBoolean() {
		return random.nextBoolean();
	}

	/**
	 * The same as {@link java.util.Random#nextGaussian(int) nextGaussian}
	 * method in {@link java.util.Random}
	 * 
	 * @return RReturns the next pseudorandom, Gaussian ("normally") distributed
	 *         double value with mean 0.0 and standard deviation 1.0 from this
	 *         random number generator's sequence.
	 */
	public double nextGaussian() {
		return random.nextGaussian();
	}

	/**
	 * The same as {@link java.util.Random#nextGaussian(int) nextGaussian}
	 * method in {@link java.util.Random}
	 * 
	 * @return RReturns the next pseudorandom, Gaussian ("normally") distributed
	 *         double value with mean 0.0 and standard deviation 1.0 from this
	 *         random number generator's sequence.
	 */
	public double nextDouble() {
		return random.nextDouble();
	}

	/**
	 * The same as {@link java.util.Random#nextLong(int) nextLong} method in
	 * {@link java.util.Random}
	 * 
	 * @return Returns the next pseudorandom, uniformly distributed long value
	 *         from this random number generator's sequence. The general
	 *         contract of nextLong is that one long value is pseudorandomly
	 *         generated and returned.
	 */
	public long nextLong() {
		return random.nextLong();
	}
}
