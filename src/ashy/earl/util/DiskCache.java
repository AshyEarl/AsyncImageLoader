package ashy.earl.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.util.Log;

/**
 * FIFOʵ�ֵ��ļ�����
 * 
 * @author AshyEarl
 * 
 * @param <V>
 */
public abstract class DiskCache<V> implements Closeable {
	private static final String CACHE_DIR = "AsyncCache";
	private static final String AUTHOR = "Author: AshyEarl";
	private static final String MD5 = "all.md5";
	private static final String MD5_TMP = "all.tmp";
	private static final String TAG = "DiskCache";
	private Map<String, EntryInfo> mFileNames;
	private File mMd5File;
	private String mDir;
	private int mCacheSize;
	private final int mMaxSize;
	private Writer mFileWriter;

	public DiskCache(Context context, int maxSize) {
		if (maxSize <= 0) {
			throw new IllegalArgumentException("maxSize <= 0");
		}
		mMaxSize = maxSize;
		mFileNames = new ConcurrentHashMap<String, DiskCache<V>.EntryInfo>();
		File cacheDir = context.getExternalCacheDir();
		// sd��δ���ػ���ͬ���ļ�����,�޷������ļ���
		if (cacheDir == null || !cacheDir.isDirectory()) {
			cacheDir = context.getCacheDir();
		}
		File asyncCacheDir = new File(cacheDir, CACHE_DIR);
		// �ļ��в�����,�����ļ���
		if (!asyncCacheDir.exists()) {
			asyncCacheDir.mkdirs();
		}
		mDir = asyncCacheDir.getAbsolutePath();
		mMd5File = new File(asyncCacheDir, MD5);
		// try {
		// readMd5s();
		// mFileWriter = new FileWriter(mMd5File, true);
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
	}

	private void readMd5s() throws IOException {
		// ���̷߳���
		synchronized (this) {
			if (mFileWriter != null) {
				return;
			}
			if (!mMd5File.exists()) {
				rebuildMd5File();
				return;
			}
			BufferedReader reader = null;
			try {
				String author;
				String cacheDir;
				String blankLine;
				FileReader fileReader = new FileReader(mMd5File);
				reader = new BufferedReader(fileReader);
				author = reader.readLine();
				cacheDir = reader.readLine();
				blankLine = reader.readLine();
				if (!AUTHOR.equals(author) || !CACHE_DIR.equals(cacheDir)
						|| !"".equals(blankLine)) {
					throw new IllegalStateException(
							String.format(
									"Unknow log header! author:%s, cacheDir:%s, blankLine:%s",
									author, cacheDir, blankLine));
				}
				String singleLine = null;
				EntryInfo singleEntry = null;
				int delCount = 0;
				while ((singleLine = reader.readLine()) != null) {
					String[] single = singleLine.split(" ");
					if (single.length != 5) {
						throw new IllegalStateException("Unknow line:"
								+ singleLine);
					}
					try {
						String Opt = single[0];
						if (Opt.equals("PUT")) {
							singleEntry = new EntryInfo(single[1],
									Long.valueOf(single[2]),
									Long.valueOf(single[4]));
							EntryInfo oldInfo = mFileNames.put(single[3],
									singleEntry);
							mCacheSize += Long.valueOf(single[4]);
							if (oldInfo != null) {
								mCacheSize -= oldInfo.size;
							}
						} else {
							delCount += 1;
							EntryInfo oldInfo = mFileNames.remove(single[3]);
							if (oldInfo != null) {
								mCacheSize -= oldInfo.size;
							}
						}
					} catch (Exception e) {
						throw new IllegalStateException("Unknow line:"
								+ singleLine);
					}
				}
				// ���DEL����,��Ҫ����log�ļ�
				if (delCount > 50) {
					rebuildMd5File();
				}
				Log.e("DiskCache", "mCacheSize:" + mCacheSize);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} finally {
				if (reader != null) {
					reader.close();
				}
			}
			if (mFileWriter == null) {
				mFileWriter = new FileWriter(mMd5File, true);
			}
		}
	}

	private void rebuildMd5File() throws IOException {
		Log.e("", "rebuildMd5File");
		// ���̷߳���
		synchronized (this) {
			if (mFileWriter != null) {
				mFileWriter.close();
			}
			// ��д����ʱ�ļ�,��ֹIO�쳣
			File md5Tmp = new File(mDir, MD5_TMP);
			BufferedWriter writer = new BufferedWriter(new FileWriter(md5Tmp));
			writer.write(AUTHOR);
			writer.write('\n');
			writer.write(CACHE_DIR);
			writer.write('\n');
			writer.write('\n');
			for (Map.Entry<String, EntryInfo> entry : mFileNames.entrySet()) {
				EntryInfo entryInfo = entry.getValue();
				writer.write(String.format("PUT %s %d %s %d\n", entryInfo.md5,
						entryInfo.createTime, entry.getKey(), entryInfo.size));
			}
			writer.close();
			// һ����д��log�ļ�
			md5Tmp.renameTo(mMd5File.getAbsoluteFile());
			mFileWriter = new BufferedWriter(new FileWriter(mMd5File, true));
		}

	}

	@Override
	public void close() throws IOException {
		if (mFileWriter != null) {
			mFileWriter.close();
		}
	}

	/**
	 * ��ȡ�����ļ�����,�ò�����һ����ʱ����,��Ӧ����<font color=red>��UI�߳�</font>��ִ�и÷���
	 * 
	 * @param url
	 *            �ļ���key
	 * @return �ļ�����
	 */
	public V get(String url) {
		if (url == null) {
			throw new NullPointerException("key is null");
		}
		// ��MD5�ļ���ȡ�ӳٵ������첽�����ķ�����,��֤UI�̵߳�����
		if (mFileWriter == null) {
			try {
				readMd5s();
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalStateException("can't read md5 file!!!");
			}
		}
		EntryInfo entryInfo;
		entryInfo = mFileNames.get(url);
		if (entryInfo == null) {
			return null;
		}
		V value = null;
		try {
			value = readFile(new File(mDir, entryInfo.md5));
		} catch (Exception e) {
		}
		if (value == null) {
			mFileNames.remove(url);
		}
		return value;
	}

	/**
	 * �򱾵��ļ����滺��,��Ӧ����<font color=red>��UI�߳�</font>��ִ�и÷���
	 * 
	 * @param key
	 *            �ļ���Ӧ��key
	 * @param value
	 *            ��Ҫ�����value
	 */
	public void put(String key, V value) {
		if (key == null || value == null) {
			throw new NullPointerException("key == null || value == null");
		}
		// ��MD5�ļ���ȡ�ӳٵ������첽�����ķ�����,��֤UI�̵߳�����
		if (mFileWriter == null) {
			try {
				readMd5s();
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalStateException("can't read md5 file!!!");
			}
		}
		String md5 = null;
		EntryInfo entryInfo = mFileNames.get(key);
		if (entryInfo == null) {
			try {
				md5 = getMD5(key);
			} catch (Exception e) {
				throw new IllegalStateException("can't compute md5 for " + key);
			}
		} else {
			md5 = entryInfo.md5;
		}
		File file = new File(mDir, md5);
		// ֱ��д��,���»���ӱ����ļ�
		try {
			writeToFile(file, value);
		} catch (Exception e1) {
			e1.printStackTrace();
			// ����ʧ��,�����ļ�����д...
			if (entryInfo != null) {
				mFileNames.remove(key);
				// ��¼�ļ���ɾ��
				writeInfo(false, key, md5, file.lastModified(), file.length());
			}
			return;
		}
		// �ļ��Ѵ���,��������Ϣ��û��,��Ӧ�ļ�header��Ҳû��
		if (entryInfo == null) {
			mFileNames.put(key,
					new EntryInfo(md5, file.lastModified(), file.length()));
			mCacheSize += file.length();
		}

		// �ļ��Ѵ���,��������Ϣ��û��
		if (entryInfo == null && file.exists()) {
			mFileNames.put(key,
					new EntryInfo(md5, file.lastModified(), file.length()));
			mCacheSize += file.length();
			// �����ļ�����,ʱ��,��С��Ϣ
			writeInfo(true, key, md5, file.lastModified(), file.length());
		}
		trimToSize(mMaxSize);
	}

	private void writeInfo(boolean isPut, String key, String md5,
			long createTime, long size) {
		try {
			mFileWriter.write(String.format(isPut ? "PUT %s %d %s %d\n"
					: "DEL %s %d %s %d\n", md5, createTime, key, size));
			mFileWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
			// TODO �ļ�ͷ��Ϣд��ʧ��,����???
		}
	}

	/**
	 * ��ձ��ػ���,�ò�����һ����ʱ����,��Ӧ����<font color=red>��UI�߳�</font>��ִ�и÷���
	 */
	public void clear() {
		// ��MD5�ļ���ȡ�ӳٵ������첽�����ķ�����,��֤UI�̵߳�����
		if (mFileWriter == null) {
			try {
				readMd5s();
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalStateException("can't read md5 file!!!");
			}
		}
		// ���̷߳���
		synchronized (this) {
			mFileNames.clear();
			mCacheSize = 0;
			File cache = new File(mDir);
			File[] files = cache.listFiles();
			for (File f : files) {
				if (f.isFile()) {
					f.delete();
				}
			}
			try {
				rebuildMd5File();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * �ݲ�֧��
	 * 
	 * @param key
	 */
	// TODO �ݲ�֧��???
	public void remove(String key) {
	}

	/**
	 * �������̿ռ�ռ��
	 * 
	 * @param size
	 */
	private void trimToSize(long size) {
		if (mCacheSize < size) {
			return;
		}
		// ���̷߳���
		synchronized (this) {
			ArrayList<Entry<String, EntryInfo>> l = new ArrayList<Entry<String, EntryInfo>>(
					mFileNames.entrySet());
			Collections.sort(l, new Comparator<Map.Entry<String, EntryInfo>>() {
				public int compare(Map.Entry<String, EntryInfo> o1,
						Map.Entry<String, EntryInfo> o2) {
					return (int) (o1.getValue().createTime - o2.getValue().createTime);
				}
			});
			for (Entry<String, EntryInfo> single : l) {
				EntryInfo entryInfo = single.getValue();
				File file = new File(mDir, entryInfo.md5);
				if (file.exists()) {
					Log.w(TAG, "trimToSize del file:" + file.getAbsolutePath());
					file.delete();
					mCacheSize -= entryInfo.size;
					mFileNames.remove(single.getKey());
					writeInfo(false, single.getKey(), entryInfo.md5,
							entryInfo.createTime, entryInfo.size);
				}
				if (mCacheSize < size || mFileNames.isEmpty()) {
					break;
				}
			}
		}
	}

	/**
	 * ���ļ���ȡ����,������Լ�ʵ�ָ÷���,�ò������첽����
	 * 
	 * @param file
	 *            ��ȡ���ļ�
	 * @return ����
	 * @throws Exception
	 *             �κζ�ȡ�������쳣
	 */
	protected abstract V readFile(File file) throws Exception;

	/**
	 * ���ļ�д�뻺��,������Լ�ʵ�ָ÷���,�ò������첽����
	 * 
	 * @param file
	 *            ��ȡ���ļ�
	 * @param value
	 *            ����
	 * @throws Exception
	 *             �κζ�ȡ�������쳣
	 */
	protected abstract void writeToFile(File file, V value) throws Exception;

	private String getMD5(String str) throws Exception {
		MessageDigest md5 = MessageDigest.getInstance("MD5");
		md5.update(str.getBytes());
		return toHex(md5.digest());
	}

	private String toHex(byte[] b) {
		char[] hexChar = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
				'a', 'b', 'c', 'd', 'e', 'f' };
		StringBuilder sb = new StringBuilder(b.length * 2);
		for (int i = 0; i < b.length; i++) {
			sb.append(hexChar[(b[i] & 0xf0) >>> 4]);
			sb.append(hexChar[b[i] & 0x0f]);
		}
		return sb.toString();
	}

	private class EntryInfo {
		String md5;
		long createTime;
		long size;

		EntryInfo(String md5, long time, long size) {
			this.md5 = md5;
			this.createTime = time;
			this.size = size;
		}
	}
}
