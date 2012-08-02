package ashy.earl.util;

import java.util.LinkedHashMap;

import android.util.Log;

/**
 * 使用LRU方式实现的内存缓存<br>
 * 由于android垃圾回收的积极性,这里避免使用软引用或弱引用
 * 
 * @author AshyEarl
 * 
 * @param <K>
 * @param <V>
 */
public class MemCache<K, V> {
	private LinkedHashMap<K, V> mCache;
	private volatile int mCacheSize;
	private int mMaxSize;

	public MemCache(int size) {
		if (EarlDebug.DEBUG) {
			EarlDebug.logD("init MemCache,size:" + size);
		}
		if (size <= 0) {
			throw new IllegalArgumentException("size <= 0");
		}
		mCache = new LinkedHashMap<K, V>(0, 0.75f, true);
		mMaxSize = size;
	}

	/**
	 * 从内存缓存中获取数据,该步骤是线程安全的<br>
	 * key为空时抛出NullPointerException异常
	 * 
	 * @param key
	 * @return key对应的value,如果未找到,返回null
	 */
	public V get(K key) {
		if (key == null) {
			throw new NullPointerException("key is null");
		}
		synchronized (mCache) {
			return mCache.get(key);
		}
	}

	/**
	 * 向内存中缓存数据,该方法是线程安全的<br>
	 * key或value为空时抛出NullPointerException异常
	 * 
	 * @param key
	 * @param value
	 */
	public void put(K key, V value) {
		Log.w("put", key+":"+value);
		if (key == null || value == null) {
			throw new NullPointerException("key == null || value == null");
		}
		V oldValue;
		synchronized (mCache) {
			EarlDebug.logW(Thread.currentThread() + "---put, key:" + key);
			oldValue = mCache.put(key, value);
			mCacheSize += getSizeSafty(key, value);
			// value已存在,放入新的value
			if (oldValue != null) {
				mCacheSize -= getSizeSafty(key, oldValue);
				EarlDebug.logE("value dump,key:" + key);
			}
		}
		if (EarlDebug.DEBUG) {
			EarlDebug.logD("mem put size: " + (mCacheSize / 1000));
		}
		// 这里主要防止valueRemoved(key, oldValue)占用大量时间,阻塞其他线程运行
		if (oldValue != null) {
			valueRemoved(key, oldValue);
		}
		// 检查缓存空间,调整使内存使用不超限
		trimSize(mMaxSize);
	}

	/**
	 * 移除一个,该操作是线程安全的<br>
	 * key为空时抛出NullPointerException异常
	 * 
	 * @param key
	 */
	public void remove(K key) {
		if (key == null) {
			throw new NullPointerException("key == null");
		}
		V value;
		synchronized (this) {
			value = mCache.remove(key);
			if (value != null) {
				mCacheSize -= getSizeSafty(key, value);
			}
		}
		if (value != null) {
			valueRemoved(key, value);
		}
	}

	/**
	 * 清理所有元素,该操作是线程安全的
	 */
	public void clear() {
		synchronized (mCache) {
			trimSize(-1);
		}
	}

	protected void valueRemoved(K key, V value) {
	}

	private void trimSize(int maxSize) {
		while (true) {
			K key;
			V value;
			synchronized (this) {
				// 这里主要防止getSize方法动态改变
				if (mCacheSize < 0 || (mCacheSize > 0 && mCache.isEmpty())) {
					throw new IllegalStateException(
							"size error, please check your getSize method, you should not change size dynamic!!!");
				}
				if (mCacheSize < maxSize || mCache.isEmpty()) {
					break;
				}
				key = mCache.keySet().iterator().next();
				value = mCache.remove(key);
				mCacheSize -= getSizeSafty(key, value);
			}
			valueRemoved(key, value);
		}
	}

	/**
	 * 获取value的内存空间占用,这里防止出现大小为负的情况出现
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	private int getSizeSafty(K key, V value) {
		int size = getSize(key, value);
		if (size < 0) {
			throw new IllegalStateException("key: " + key + ",value:" + value
					+ " size < 0");
		}
		if (EarlDebug.DEBUG) {
			EarlDebug.logD("getSizeSafty:" + size / 1000 + "k");
		}
		return size;
	}

	/**
	 * 获取value的内存空间占用,子类应当重写该方法
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	protected int getSize(K key, V value) {
		return 1;
	}
}