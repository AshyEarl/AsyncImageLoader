package ashy.earl.util;

import java.util.LinkedHashMap;

import android.util.Log;

/**
 * ʹ��LRU��ʽʵ�ֵ��ڴ滺��<br>
 * ����android�������յĻ�����,�������ʹ�������û�������
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
	 * ���ڴ滺���л�ȡ����,�ò������̰߳�ȫ��<br>
	 * keyΪ��ʱ�׳�NullPointerException�쳣
	 * 
	 * @param key
	 * @return key��Ӧ��value,���δ�ҵ�,����null
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
	 * ���ڴ��л�������,�÷������̰߳�ȫ��<br>
	 * key��valueΪ��ʱ�׳�NullPointerException�쳣
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
			// value�Ѵ���,�����µ�value
			if (oldValue != null) {
				mCacheSize -= getSizeSafty(key, oldValue);
				EarlDebug.logE("value dump,key:" + key);
			}
		}
		if (EarlDebug.DEBUG) {
			EarlDebug.logD("mem put size: " + (mCacheSize / 1000));
		}
		// ������Ҫ��ֹvalueRemoved(key, oldValue)ռ�ô���ʱ��,���������߳�����
		if (oldValue != null) {
			valueRemoved(key, oldValue);
		}
		// ��黺��ռ�,����ʹ�ڴ�ʹ�ò�����
		trimSize(mMaxSize);
	}

	/**
	 * �Ƴ�һ��,�ò������̰߳�ȫ��<br>
	 * keyΪ��ʱ�׳�NullPointerException�쳣
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
	 * ��������Ԫ��,�ò������̰߳�ȫ��
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
				// ������Ҫ��ֹgetSize������̬�ı�
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
	 * ��ȡvalue���ڴ�ռ�ռ��,�����ֹ���ִ�СΪ�����������
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
	 * ��ȡvalue���ڴ�ռ�ռ��,����Ӧ����д�÷���
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	protected int getSize(K key, V value) {
		return 1;
	}
}