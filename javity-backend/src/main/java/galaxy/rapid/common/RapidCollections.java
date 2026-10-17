package galaxy.rapid.common;

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.RandomAccess;

public class RapidCollections {
	private final static int SHUFFLE_THRESHOLD = 5;

	private static Random r;

	public static void shuffle(List<?> list) {
		Random rnd = r;
		if (rnd == null)
			r = rnd = new Random(); // harmless race.
		shuffle(list, rnd);
	}

	public static void shuffle(List<?> list, Random rnd) {
		int size = list.size();
		if (size < SHUFFLE_THRESHOLD || list instanceof RandomAccess) {
			for (int i = size; i > 1; i--)
				Collections.swap(list, i - 1, rnd.nextInt(i));
		} else {
			Object arr[] = list.toArray();

			// Shuffle array
			for (int i = size; i > 1; i--)
				swap(arr, i - 1, rnd.nextInt(i));

			// Dump array back into list
			ListIterator it = list.listIterator();
			for (int i = 0; i < arr.length; i++) {
				it.next();
				it.set(arr[i]);
			}
		}
	}

	/**
	 * Swaps the two specified elements in the specified array.
	 */
	private static void swap(Object[] arr, int i, int j) {
		Object tmp = arr[i];
		arr[i] = arr[j];
		arr[j] = tmp;
	}
}
