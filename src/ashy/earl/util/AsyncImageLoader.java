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
	/** �ڴ滺�� */
	private MemCache<String, Bitmap> mMemCache;
	/** �ļ����� */
	private DiskCache<Bitmap> mDiskCache;
	/** �̹߳��� */
	private ExecutorService mExecutorService;
	/** ����ģʽ����,����ϵͳ��Դ���� */
	private static AsyncImageLoader SELF;
	/** view���ύ������ */
	private Map<View, Order> mOrders;
	/** ��Ϣ���� */
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
		// ʹ��1/8�Ŀ����ڴ���Ϊ�ڴ滺��
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
	 * ��ȡ�첽���ع����ʵ��,�������Activity����ʱ����{@link #release()}���ͷ��ڴ�
	 * 
	 * @param context
	 * @return
	 */
	public static AsyncImageLoader getInstance(Context context) {
		return SELF == null ? (SELF = new AsyncImageLoader(context)) : SELF;
	}

	/**
	 * �ͷ��ڴ�,��Ӧ����{@link Activity}����ʱ���ø÷���
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
		// �ߴ��ڴ����
		System.gc();
	}

	/**
	 * ����ڴ滺���ͼƬ����,�ò����Ǻ�ʱ����,��Ҫ����UI�߳���ִ��
	 */
	public void clearCache() {
		mMemCache.clear();
		mDiskCache.clear();
	}

	/**
	 * ��ȡ������С
	 * 
	 * @param options
	 * @param minSideLength
	 * @param maxNumOfPixels
	 * @return ԭͼ���ű���,����ֵn,������ϵ��Ϊ1/(2^n)
	 */
	private int getSampleSize(BitmapFactory.Options options, int minSideLength,
			int maxNumOfPixels) {
		int h = options.outHeight;
		int w = options.outWidth;
		// ��С����ֵ,����cell(����n����С����)
		int minBound = (maxNumOfPixels <= 0 ? 1 : (int) Math.ceil(Math.sqrt(w
				* h / maxNumOfPixels)));
		// ������ֵ
		int maxBound = (minSideLength <= 0 ? 128 : (int) Math.min(
				Math.floor(h / minSideLength), Math.floor(w / minSideLength)));
		// ����������صļ���
		if (maxBound < minBound) {
			return minBound;
		}
		// �����쳣
		if (minSideLength <= 0 && maxNumOfPixels <= 0) {
			return 1;
		}
		// ����ʹ�����ص���������ֵ
		else if (minSideLength <= 0) {
			return minBound;
		} else {
			return maxBound;
		}
	}

	/**
	 * ��ȡ������С
	 * 
	 * @param options
	 *            BitmapFactory ѡ��
	 * @param minSideLength
	 *            ��С�ߵĳ���
	 * @param maxNumOfPixels
	 *            ����������ֵ
	 * @return
	 */
	private int computeSampleSize(BitmapFactory.Options options,
			int minSideLength, int maxNumOfPixels) {
		int initSize = getSampleSize(options, minSideLength, maxNumOfPixels);
		int finalSize;
		// ����Options.inSampleSizeֻ����2^n����,���������ת��
		if (initSize <= 8) {
			finalSize = 1;
			while (finalSize < initSize) {
				// ����һλ,��*2��ͬ,Ч�ʽϸ�
				finalSize <<= 1;
			}
		} else {
			finalSize = (initSize + 7) / 8 * 8;
		}
		return finalSize;
	}

	/**
	 * �첽����ͼƬ
	 * 
	 * @param notCacheToMem
	 *            ͼƬ�Ƿ񻺴浽�ڴ���,true������,false����<br>
	 *            <li>��ImageView�Ȳ����ظ��������ͼƬ�Ŀؼ���,��Ӧ���Ѹ�ֵ��Ϊtrue<br>
	 *            �Է��ڴ滺����ƶ�bitmap�Ļ��մ���
	 * @param view
	 *            �������ͼƬ��view,����Ϊ��
	 * @param url
	 *            ͼƬ���ص�url�򱾵��ļ�·��(�����Ǿ���·��,����/sdcard/test.jpg),����Ϊ��
	 * @param imageLoad
	 *            ͼƬ����״̬�Ļص��ӿ�,��Ӧ��ʵ�����Լ��Ľӿ�������ui,����Ϊ��
	 */
	public void loadImage(boolean notCacheToMem, View view, String url,
			ImageLoadListener imageLoad) {
		// ��������
		if (view == null || url == null) {
			if (imageLoad != null) {
				imageLoad.onFailed(view, url);
			}
			return;
		}

		// ���ȴ��ڴ滺���л�ȡͼƬ
		Bitmap bitmap = mMemCache.get(url);
		if (bitmap != null) {
			// ��viewԭ���ύ������,����ֱ�Ӹ���ԭ��������
			mOrders.put(view, ORDER);
			if (imageLoad != null) {
				imageLoad.onSucceed(false, view, url, bitmap);
			}
			return;
		}
		// �ڴ���û�ж�Ӧ��ͼƬ,�ύ�����������ļ��л�ȡ
		// ֪ͨui�Ѿ���ʼ�첽����
		if (imageLoad != null) {
			imageLoad.onStart(view, url);
		}
		Order order = new Order();
		order.imageLoad = imageLoad;
		order.url = url;
		order.view = view;
		// ��viewԭ���ύ������,����ֱ�Ӹ���ԭ��������
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
	 * �첽����ͼƬ
	 * 
	 * @param view
	 *            �������ͼƬ��view,����Ϊ��
	 * @param url
	 *            ͼƬ���ص�url�򱾵��ļ�·��(�����Ǿ���·��,����/sdcard/test.jpg),����Ϊ��
	 * @param imageLoad
	 *            ͼƬ����״̬�Ļص��ӿ�,��Ӧ��ʵ�����Լ��Ľӿ�������ui,����Ϊ��
	 */
	public void loadImage(View view, String url, ImageLoadListener imageLoad) {
		loadImage(false, view, url, imageLoad);
	}

	/**
	 * ������򱾵��ļ�Ԥ����ͼƬ,ͼƬ������ص��ڴ滺����<br>
	 * ��Ӧ��������Ҫ��ʾ��ͼƬʱ����<br>
	 * <li>{@link #loadImage(View, String, ImageLoadListener)} <br><li>
	 * {@link #loadImage(boolean, View, String, ImageLoadListener)}
	 * 
	 * @param url
	 *            ͼƬ���ص�url�򱾵��ļ�·��(�����Ǿ���·��,����/sdcard/test.jpg)
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
		 * ͼƬ���سɹ�ʱ���õķ���
		 * 
		 * @param isAsyncLoad
		 *            �Ƿ��첽����,����ڴ�����������
		 * @param view
		 *            ������첽���ص�view
		 * @param url
		 *            �����url
		 * @param bitmap
		 *            ������ɵ�ͼƬ
		 */
		public abstract void onSucceed(boolean isAsyncLoad, View view,
				String url, Bitmap bitmap);

		/**
		 * ͼƬ����ʧ��ʱ����
		 * 
		 * @param view
		 *            ������첽���ص�view
		 * @param url
		 *            �����url
		 */
		public void onFailed(View view, String url) {
		}

		/**
		 * ͼƬ��ʼ����ʱ����
		 * 
		 * @param view
		 *            ������첽���ص�view
		 * @param url
		 *            �����url
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
				// ��ʼ�첽���ݻ�ȡ
				if (mMemCache.get(url) != null) {
					continue;
				}
				Bitmap bitmap = null;
				try {
					// �ӱ��ػ����ȡ
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
					// �ӱ��ػ����ȡʧ��,��������߱����ļ���ȡ
					mDownloadTimes += 1;
					Log.d("", Thread.currentThread() + " start load:" + url);
					if (url.startsWith("http://") || url.startsWith("https://")) {
						bitmap = downloadPic(url);
					} else {
						bitmap = loadPic(url);
					}
					Log.d("", Thread.currentThread() + " end load:" + url
							+ ",  " + bitmap);
					// �����ļ���������10��,�ߴ�ϵͳ�����ڴ�
					// ��Ϊ���ع��̻������Ŵ���
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
					// ��ȡͼƬʧ��
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
				// ͼƬ���Ŵ���
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
			// ͼƬ���Ŵ���
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
