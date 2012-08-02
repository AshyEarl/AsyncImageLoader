package ashy.earl.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

public class AsyncImageLoader {
	private static final int MSG_LOAD_SUCCEED = 0;
	private static final int MSG_LOAD_FAILED = 1;
	private static final int MSG_LOAD_START = 2;
	private static final int THREAD_COUNT = 5;
	private static final Order ORDER = new Order();
	private static final String NOT_CACHE_MEM = "NOT_CACHE_MEM";
	private final View DEFAULT_VIEW;
	/** 内存缓存 */
	private MemCache<String, Bitmap> mMemCache;
	/** 文件缓存 */
	private DiskCache<Bitmap> mDiskCache;
	/** 线程管理 */
	private ExecutorService mExecutorService;
	/** 单例模式运行,减少系统资源消耗 */
	private static AsyncImageLoader SELF;
	/** view所提交的请求 */
	private Map<View, Order> mOrders;
	/** 消息处理 */
	private EarlHandle mHandle;
	private Queue<String> mUrls;
	private Set<String> mRunUrls;
	private int width;
	private int height;

	private AsyncImageLoader(Context context) {
		mOrders = new ConcurrentHashMap<View, AsyncImageLoader.Order>();
		mHandle = new EarlHandle();
		mExecutorService = Executors.newFixedThreadPool(THREAD_COUNT);
		mUrls = new ConcurrentLinkedQueue<String>();
		//In android 2.1,here can replace with ConcurrentLinkedQueue,but not the best way
		mRunUrls = new ConcurrentSkipListSet<String>();
		DEFAULT_VIEW = new View(context);
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		height = metrics.heightPixels;
		width = metrics.widthPixels;

		long start = System.currentTimeMillis();
		for (int i = 0; i < THREAD_COUNT; i++) {
			mExecutorService.submit(new LoadImage());
		}
		System.out.println("submit use time:"
				+ (System.currentTimeMillis() - start));

		final int memClass = ((ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
		// 使用1/8的可用内存作为内存缓存
		final int cacheSize = 1024 * 1024 * memClass / 8;
		mMemCache = new MemCache<String, Bitmap>(cacheSize) {
			@Override
			protected int getSize(String key, Bitmap value) {
				return value.getRowBytes() * value.getHeight();
			}

			@Override
			protected void valueRemoved(String key, Bitmap value) {
				if (EarlDebug.DEBUG) {
					EarlDebug.logW("release mem:" + key + ",size:"
							+ (value.getRowBytes() * value.getHeight()));
				}
				value.recycle();
				value = null;
			}
		};
		mDiskCache = new DiskCache<Bitmap>(context, 10 * 1024 * 1024) {
			@Override
			protected Bitmap readFile(File file) {
				return BitmapFactory.decodeFile(file.getAbsolutePath());
			}

			@Override
			protected void writeToFile(File file, Bitmap value)
					throws FileNotFoundException {
				value.compress(Bitmap.CompressFormat.JPEG, 100,
						new FileOutputStream(file));
			}
		};
	}

	/**
	 * 获取异步加载管理的实例,你必须在Activity销毁时调用{@link #release()}来释放内存
	 * 
	 * @param context
	 * @return
	 */
	public static AsyncImageLoader getInstance(Context context) {
		return SELF == null ? (SELF = new AsyncImageLoader(context)) : SELF;
	}

	/**
	 * 释放内存,你应当在{@link Activity}销毁时调用该方法
	 */
	public static void release() {
		if (SELF == null) {
			return;
		}
		SELF.mExecutorService.shutdownNow();
		try {
			SELF.mExecutorService.awaitTermination(0, TimeUnit.MICROSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		SELF.mMemCache.clear();
		try {
			SELF.mDiskCache.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		SELF = null;
		// 催促内存回收
		System.gc();
	}

	/**
	 * 清空内存缓存和图片缓存,该操作是耗时操作,不要放在UI线程中执行
	 */
	public void clearCache() {
		mMemCache.clear();
		mDiskCache.clear();
	}

	/**
	 * 获取样本大小
	 * 
	 * @param options
	 * @param minSideLength
	 * @param maxNumOfPixels
	 * @return 原图缩放比例,返回值n,其缩放系数为1/(2^n)
	 */
	private int getSampleSize(BitmapFactory.Options options, int minSideLength,
			int maxNumOfPixels) {
		int h = options.outHeight;
		int w = options.outWidth;
		// 最小调整值,其中cell(大于n的最小整数)
		int minBound = (maxNumOfPixels <= 0 ? 1 : (int) Math.ceil(Math.sqrt(w
				* h / maxNumOfPixels)));
		// 最大调整值
		int maxBound = (minSideLength <= 0 ? 128 : (int) Math.min(
				Math.floor(h / minSideLength), Math.floor(w / minSideLength)));
		// 优先最大像素的计算
		if (maxBound < minBound) {
			return minBound;
		}
		// 参数异常
		if (minSideLength <= 0 && maxNumOfPixels <= 0) {
			return 1;
		}
		// 返回使用像素点计算的缩放值
		else if (minSideLength <= 0) {
			return minBound;
		} else {
			return maxBound;
		}
	}

	/**
	 * 获取样本大小
	 * 
	 * @param options
	 *            BitmapFactory 选项
	 * @param minSideLength
	 *            最小边的长度
	 * @param maxNumOfPixels
	 *            最大的像素总值
	 * @return
	 */
	private int computeSampleSize(BitmapFactory.Options options,
			int minSideLength, int maxNumOfPixels) {
		int initSize = getSampleSize(options, minSideLength, maxNumOfPixels);
		int finalSize;
		// 由于Options.inSampleSize只接受2^n缩放,故这里进行转换
		if (initSize <= 8) {
			finalSize = 1;
			while (finalSize < initSize) {
				// 左移一位,和*2相同,效率较高
				finalSize <<= 1;
			}
		} else {
			finalSize = (initSize + 7) / 8 * 8;
		}
		return finalSize;
	}

	/**
	 * 异步加载图片
	 * 
	 * @param notCacheToMem
	 *            图片是否缓存到内存中,true不缓存,false缓存<br>
	 *            <li>在ImageView等不会重复请求加载图片的控件里,你应当把该值设为true<br>
	 *            以防内存缓存机制对bitmap的回收处理
	 * @param view
	 *            请求加载图片的view,不能为空
	 * @param url
	 *            图片下载的url或本地文件路径(必须是绝对路径,例如/sdcard/test.jpg),不能为空
	 * @param imageLoad
	 *            图片加载状态的回调接口,你应当实现你自己的接口来更新ui,可以为空
	 */
	public void loadImage(boolean notCacheToMem, View view, String url,
			ImageLoadListener imageLoad) {
		// 参数有误
		if (view == null || url == null) {
			if (imageLoad != null) {
				imageLoad.onFailed(view, url);
			}
			return;
		}

		// 首先从内存缓存中获取图片
		Bitmap bitmap = mMemCache.get(url);
		if (bitmap != null) {
			// 该view原来提交过请求,这里直接覆盖原来的请求
			mOrders.put(view, ORDER);
			if (imageLoad != null) {
				imageLoad.onSucceed(false, view, url, bitmap);
			}
			return;
		}
		// 内存中没有对应的图片,提交请求从网络或文件中获取
		// 通知ui已经开始异步加载
		if (imageLoad != null) {
			imageLoad.onStart(view, url);
		}
		Order order = new Order();
		order.imageLoad = imageLoad;
		order.url = url;
		order.view = view;
		// 该view原来提交过请求,这里直接覆盖原来的请求
		mOrders.put(view, order);
		Log.w("", view + ":" + order);
		if (notCacheToMem) {
			url += NOT_CACHE_MEM;
		}
		if (mUrls.contains(url) || mRunUrls.contains(url)) {
			return;
		}
		mUrls.add(url);
		synchronized (mSync) {
			mSync.notify();
		}
	}

	/**
	 * 异步加载图片
	 * 
	 * @param view
	 *            请求加载图片的view,不能为空
	 * @param url
	 *            图片下载的url或本地文件路径(必须是绝对路径,例如/sdcard/test.jpg),不能为空
	 * @param imageLoad
	 *            图片加载状态的回调接口,你应当实现你自己的接口来更新ui,可以为空
	 */
	public void loadImage(View view, String url, ImageLoadListener imageLoad) {
		loadImage(false, view, url, imageLoad);
	}

	/**
	 * 从网络或本地文件预加载图片,图片不会加载到内存缓存中<br>
	 * 你应当在你需要显示该图片时调用<br>
	 * <li>{@link #loadImage(View, String, ImageLoadListener)} <br><li>
	 * {@link #loadImage(boolean, View, String, ImageLoadListener)}
	 * 
	 * @param url
	 *            图片下载的url或本地文件路径(必须是绝对路径,例如/sdcard/test.jpg)
	 */
	public void preLoad(String url) {
		if (TextUtils.isEmpty(url)) {
			return;
		}
		loadImage(true, DEFAULT_VIEW, url, null);
	}

	private class EarlHandle extends Handler {
		@Override
		public void handleMessage(Message msg) {
			Order order = (Order) msg.obj;
			String url = order.url;
			Bitmap bitmap = order.bitmap;
			switch (msg.what) {
			case MSG_LOAD_FAILED:
				Iterator<View> iterator = mOrders.keySet().iterator();
				// TODO to be continue...
				// TODO how to optimize??? here is handle in UI Thread...
				while (iterator.hasNext()) {
					View view = iterator.next();
					Order item = mOrders.get(view);
					if (item != ORDER && url.equals(item.url)) {
						mOrders.remove(view);
						Log.i("" + url, "" + item);
						if (item.imageLoad != null) {
							item.imageLoad.onFailed(item.view, url);
						}
					}
				}
				break;
			case MSG_LOAD_SUCCEED:
				iterator = mOrders.keySet().iterator();
				// TODO to be continue...
				while (iterator.hasNext()) {
					View view = iterator.next();
					Order item = mOrders.get(view);
					if (url.equals(item.url)) {
						mOrders.remove(view);
						Log.i("" + url, "" + item);
						if (item.imageLoad != null) {
							item.imageLoad.onSucceed(true, item.view, url,
									bitmap);
						}
					}
				}
				break;
			case MSG_LOAD_START:
				if (order.imageLoad != null) {
					order.imageLoad.onStart(order.view, order.url);
				}
				break;
			default:
				break;
			}
		}
	}

	public static abstract class ImageLoadListener {
		/**
		 * 图片加载成功时调用的方法
		 * 
		 * @param isAsyncLoad
		 *            是否异步加载,如果内存中已有则不是
		 * @param view
		 *            请求该异步加载的view
		 * @param url
		 *            请求的url
		 * @param bitmap
		 *            加载完成的图片
		 */
		public abstract void onSucceed(boolean isAsyncLoad, View view,
				String url, Bitmap bitmap);

		/**
		 * 图片加载失败时调用
		 * 
		 * @param view
		 *            请求该异步加载的view
		 * @param url
		 *            请求的url
		 */
		public void onFailed(View view, String url) {
		}

		/**
		 * 图片开始加载时调用
		 * 
		 * @param view
		 *            请求该异步加载的view
		 * @param url
		 *            请求的url
		 */
		public void onStart(View view, String url) {
		}
	}

	private static class Order {
		View view;
		String url;
		ImageLoadListener imageLoad;
		Bitmap bitmap;

		@Override
		public String toString() {
			return "url:" + url.substring(url.length() - 10) + "\nview:" + view
					+ "\nbitmap:" + bitmap;
		}
	}

	private byte[] mSync = new byte[0];

	private class LoadImage implements Runnable {
		private int mDownloadTimes;

		@Override
		public void run() {
			while (true) {
				String url = mUrls.poll();
				if (url == null) {
					synchronized (mSync) {
						try {
							System.out
									.println(Thread.currentThread() + " wait");
							mSync.wait();
							System.out.println(Thread.currentThread()
									+ " notify");
						} catch (InterruptedException e) {
							return;
						}
					}
					continue;
				}
				boolean notCacheMem = false;
				if (url.endsWith(NOT_CACHE_MEM)) {
					notCacheMem = true;
					url = url.substring(0,
							url.length() - NOT_CACHE_MEM.length());
				}
				System.out.println("start working:" + url);
				mRunUrls.add(url);
				// 开始异步数据获取
				if (mMemCache.get(url) != null) {
					continue;
				}
				Bitmap bitmap = null;
				try {
					// 从本地缓存获取
					bitmap = mDiskCache.get(url);
					if (bitmap != null) {
						if (!notCacheMem) {
							mMemCache.put(url, bitmap);
						}
						finishOrder(url, bitmap);
						continue;
					}
				} catch (Exception e) {
					e.printStackTrace();
					finishOrder(url, bitmap);
					continue;
				}

				try {
					// 从本地缓存获取失败,从网络或者本地文件获取
					mDownloadTimes += 1;
					Log.d("", Thread.currentThread() + " start load:" + url);
					if (url.startsWith("http://") || url.startsWith("https://")) {
						bitmap = downloadPic(url);
					} else {
						bitmap = loadPic(url);
					}
					Log.d("", Thread.currentThread() + " end load:" + url
							+ ",  " + bitmap);
					// 下载文件次数多于10次,催促系统回收内存
					// 因为下载过程会有缩放处理
					if (mDownloadTimes > 10) {
						System.gc();
						mDownloadTimes = 0;
					}
					if (bitmap != null) {
						if (!notCacheMem) {
							mMemCache.put(url, bitmap);
						}
						mDiskCache.put(url, bitmap);
						finishOrder(url, bitmap);
						continue;
					}
					// 获取图片失败
					finishOrder(url, bitmap);
				} catch (Exception e) {
					e.printStackTrace();
					finishOrder(url, bitmap);
				}
			}
		}

		private void finishOrder(String url, Bitmap bitmap) {
			Order order = new Order();
			order.url = url;
			order.bitmap = bitmap;
			System.out.println("finishOrder:" + bitmap);
			mHandle.obtainMessage(
					bitmap == null ? MSG_LOAD_FAILED : MSG_LOAD_SUCCEED, order)
					.sendToTarget();
			mRunUrls.remove(url);
		}

		private Bitmap downloadPic(String url) throws Exception {
			Log.w("earl", Thread.currentThread() + " start downloadPic");
			URLConnection urlConnection = new URL(url).openConnection();
			urlConnection.setConnectTimeout(5 * 1000);
			int length = urlConnection.getContentLength();
			Bitmap bitmap;
			if (length != -1) {
				byte[] picData = new byte[length];
				byte[] buffer = new byte[1024];
				int len = 0;
				int currentPos = 0;
				InputStream is = urlConnection.getInputStream();
				while ((len = is.read(buffer)) != -1) {
					System.arraycopy(buffer, 0, picData, currentPos, len);
					currentPos += len;
				}
				// 图片缩放处理
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				bitmap = BitmapFactory.decodeByteArray(picData, 0,
						picData.length, options);
				options.inSampleSize = computeSampleSize(options,
						width < height ? width : height, width * height);
				options.inJustDecodeBounds = false;
				if (EarlDebug.DEBUG && options.inSampleSize > 1) {
					EarlDebug.logE("sampleSize:" + options.inSampleSize);
				}
				bitmap = BitmapFactory.decodeByteArray(picData, 0,
						picData.length, options);
				picData = null;
				Log.w("earl", Thread.currentThread() + " end downloadPic");
				return bitmap;
			}
			Log.w("earl", Thread.currentThread() + " end downloadPic");
			return null;
		}

		private Bitmap loadPic(String filePath) throws Exception {
			File file = new File(filePath);
			if (!file.exists()) {
				return null;
			}
			Bitmap bitmap;
			Log.w("earl", Thread.currentThread() + " start loadPic");
			// 图片缩放处理
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			bitmap = BitmapFactory.decodeFile(filePath, options);
			options.inSampleSize = computeSampleSize(options,
					width < height ? width : height, width * height);
			options.inJustDecodeBounds = false;
			if (EarlDebug.DEBUG && options.inSampleSize > 1) {
				EarlDebug.logE("sampleSize:" + options.inSampleSize);
			}
			bitmap = BitmapFactory.decodeFile(filePath, options);
			Log.w("earl", Thread.currentThread() + " end downloadPic");
			return bitmap;
		}
	}
}
