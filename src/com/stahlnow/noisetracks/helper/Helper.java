package com.stahlnow.noisetracks.helper;

import java.io.File;

public class Helper {

	public static long dirSize(File dir) {

	    if (dir.exists()) {
	        long result = 0;
	        File[] fileList = dir.listFiles();
	        for(int i = 0; i < fileList.length; i++) {
	            // Recursive call if it's a directory
	            if(fileList[i].isDirectory()) {
	                result += dirSize(fileList [i]);
	            } else {
	                // Sum the file size in bytes
	                result += fileList[i].length();
	            }
	        }
	        return result; // return the file size
	    }
	    return 0;
	}
	
	/**
	 * Clamps a number to a range, by restricting it to a minimum and maximum values: if the passed value is lower than the minimum value, it's replaced by the minimum; if it's higher than the maximum value, it's replaced by the maximum; if not, it's unchanged.
	 * @param __value	The value to be clamped.
	 * @param __min		Minimum value allowed.
	 * @param __max		Maximum value allowed.
	 * @return			The newly clamped value.
	 */
	public static float clamp(float __value, float __min, float __max) {
		return __value < __min ? __min : __value > __max ? __max : __value;
	}

	public static int clamp(int __value, int __min, int __max) {
		return __value < __min ? __min : __value > __max ? __max : __value;
	}

	public static float clamp(float __value) {
		return clamp(__value, 0, 1);
	}

	/**
	 * Maps a value from a range, determined by old minimum and maximum values, to a new range, determined by new minimum and maximum values. These minimum and maximum values are referential; the new value is not clamped by them.
	 * @param __value	The value to be re-mapped.
	 * @param __oldMin	The previous minimum value.
	 * @param __oldMax	The previous maximum value.
	 * @param __newMin	The new minimum value.
	 * @param __newMax	The new maximum value.
	 * @return			The new value, mapped to the new range.
	 */
	public static float map(float __value, float __oldMin, float __oldMax, float __newMin, float __newMax, boolean __clamp) {
		if (__oldMin == __oldMax) return __newMin;
		float p = ((__value-__oldMin) / (__oldMax-__oldMin) * (__newMax-__newMin)) + __newMin;
		if (__clamp) p = __newMin < __newMax ? clamp(p, __newMin, __newMax) : clamp(p, __newMax, __newMin);
		return p;
	}

	public static float map(float __value, float __oldMin, float __oldMax, float __newMin, float __newMax) {
		return map(__value, __oldMin, __oldMax, __newMin, __newMax, false);
	}

	public static int mapRounded(float __value, float __oldMin, float __oldMax, float __newMin, float __newMax) {
		return Math.round(map(__value, __oldMin, __oldMax, __newMin, __newMax));
	}

	public static float map(float __value, float __oldMin, float __oldMax, boolean __clamp) {
		return map(__value, __oldMin, __oldMax, 0, 1, __clamp);
	}

	public static float map(float __value, float __oldMin, float __oldMax) {
		return map(__value, __oldMin, __oldMax, 0, 1);
	}

	/**
	 * Clamps a value to a range, by restricting it to a minimum and maximum values but folding the value to the range instead of simply resetting to the minimum and maximum. It works like a more powerful Modulo function.
	 * @param __value	The value to be clamped.
	 * @param __min		Minimum value allowed.
	 * @param __max		Maximum value allowed.
	 * @return			The newly clamped value.
	 * @example Some examples:
	 * <listing version="3.0">
	 * 	trace(MathUtils.roundClamp(14, 0, 10));
	 * 	// Result: 4
	 *
	 * 	trace(MathUtils.roundClamp(360, 0, 360));
	 * 	// Result: 0
	 *
	 * 	trace(MathUtils.roundClamp(360, -180, 180));
	 * 	// Result: 0
	 *
	 * 	trace(MathUtils.roundClamp(21, 0, 10));
	 * 	// Result: 1
	 *
	 * 	trace(MathUtils.roundClamp(-98, 0, 100));
	 * 	// Result: 2
	 * </listing>
	 */
	public static int rangeMod(int __value, int __min, int __pseudoMax) {
		int range = __pseudoMax - __min;
		__value = (__value - __min) % range;
		if (__value < 0) __value = range - (-__value % range);
		__value += __min;
		return __value;
	}
	
	
	public static float map2range(float x, float in_min, float in_max, float out_min, float out_max)
	{
		return clamp(
				out_min + (out_max - out_min) * (x - in_min) / (in_max - in_min),
				out_min,
				out_max);
	}

}

