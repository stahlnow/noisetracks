package com.noisetracks.android.helper;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

public class ImageHelper {
	/**
	 * Add rounded corners to bitmap and optionally invert colors.
	 * @param bitmap The Bitmap
	 * @param pixels The radius for corners
	 * @param invert Invert bitmap
	 * @return A rounded corner bitmap
	 */
	public static Bitmap transformBitmap(Bitmap bitmap, int pixels, boolean invert) {
		Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
				bitmap.getHeight(), Config.ARGB_8888);
		Canvas canvas = new Canvas(output);

		final int color = 0xffffffff;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, bitmap.getWidth(),
				bitmap.getHeight());
		final RectF rectF = new RectF(rect);
		final float roundPx = pixels;

		paint.setAntiAlias(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);
		canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

		paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
		
		if (invert) {
			float inv[] = { -1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 0.0f,
					1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f,
					1.0f, 0.0f };
			ColorMatrix cm = new ColorMatrix(inv);
			paint.setColorFilter(new ColorMatrixColorFilter(cm));
		}
		canvas.drawBitmap(bitmap, rect, rect, paint);

		return output;
	}
}

