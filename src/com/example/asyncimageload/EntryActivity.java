package com.example.asyncimageload;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import ashy.earl.util.AsyncImageLoader;

public class EntryActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.entry);
		OnButtonClick buttonClick = new OnButtonClick();

		findViewById(R.id.button1).setOnClickListener(buttonClick);
		findViewById(R.id.button2).setOnClickListener(buttonClick);
		findViewById(R.id.button3).setOnClickListener(buttonClick);
		findViewById(R.id.button4).setOnClickListener(buttonClick);
		findViewById(R.id.button5).setOnClickListener(buttonClick);
		findViewById(R.id.button6).setOnClickListener(buttonClick);
		findViewById(R.id.button7).setOnClickListener(buttonClick);
		findViewById(R.id.button8).setOnClickListener(buttonClick);
		// testDiskCache();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != RESULT_OK) {
			Log.e("onActivityResult", "ActivityResult resultCode error");
			return;
		}
		if (requestCode == 0) {
			Uri originalUri = data.getData(); // 获得图片的uri
			String[] proj = { MediaStore.Images.Media.DATA };
			// 好像是android多媒体数据库的封装接口，具体的看Android文档
			Cursor cursor = managedQuery(originalUri, proj, null, null, null);
			// 按我个人理解 这个是获得用户选择的图片的索引值
			int column_index = cursor
					.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			// 将光标移至开头 ，这个很重要，不小心很容易引起越界
			cursor.moveToFirst();
			// 最后根据索引值获取图片路径
			String path = cursor.getString(column_index);
			if (!TextUtils.isEmpty(path)) {
				System.out.println(path);
				Intent intent = new Intent(EntryActivity.this,
						MainActivity.class);
				intent.putExtra(MainActivity.URL, new String[]{path});
				startActivity(intent);
			}
		}
	}

	// private void testDiskCache() {
	// DiskCache<Bitmap> diskCache = new DiskCache<Bitmap>(this, 200 * 1024) {
	// @Override
	// protected Bitmap readFile(File file) {
	// return BitmapFactory.decodeFile(file.getAbsolutePath());
	// }
	//
	// @Override
	// protected void writeToFile(File file, Bitmap value)
	// throws FileNotFoundException {
	// value.compress(Bitmap.CompressFormat.JPEG, 100,
	// new FileOutputStream(file));
	// }
	// };
	// diskCache.put("ic_action_search", BitmapFactory.decodeResource(
	// getResources(), R.drawable.ic_action_search));
	// diskCache.put("ic_launcher", BitmapFactory.decodeResource(
	// getResources(), R.drawable.ic_launcher));
	// diskCache.put("test", BitmapFactory.decodeFile("/sdcard/xunlei.png"));
	// }

	private class OnButtonClick implements OnClickListener {

		@Override
		public void onClick(View v) {
			Intent intent = new Intent(EntryActivity.this, MainActivity.class);
			switch (v.getId()) {
			case R.id.button1:
				intent.putExtra(MainActivity.URL, URL1);
				startActivity(intent);
				break;
			case R.id.button2:
				intent.putExtra(MainActivity.URL, URL2);
				startActivity(intent);
				break;
			case R.id.button3:
				intent.putExtra(MainActivity.URL, URL3);
				startActivity(intent);
				break;
			case R.id.button4:
				AsyncImageLoader.getInstance(EntryActivity.this).clearCache();
				break;
			case R.id.button5:
				intent = new Intent(EntryActivity.this, TestActivity.class);
				intent.putExtra(MainActivity.URL, URL3);
				startActivity(intent);
				break;
			case R.id.button6:
				intent = new Intent(EntryActivity.this, PreLoadActivity.class);
				intent.putExtra(MainActivity.URL, URL3);
				startActivity(intent);
				break;
			case R.id.button7:
				intent = new Intent(EntryActivity.this,
						GoogleSearchActivity.class);
				intent.putExtra(MainActivity.URL, URL3);
				startActivity(intent);
				break;
			case R.id.button8:
				Intent getAlbum = new Intent(Intent.ACTION_GET_CONTENT);
				getAlbum.setType("image/*");
				startActivityForResult(getAlbum, 0);
				break;
			default:
				break;
			}
		}

	}

	private static final String[] URL1 = {
			"http://img.yingyonghui.com/business/images/QQpeifu.jpg",
			"http://fmn.rrimg.com/fmn060/xiaozhan/20120510/1225/x_large_nDFG_5204000016531261.jpg",
			"http://img.yingyonghui.com/business/images/QQpeifu.jpg",
			"http://fmn.rrimg.com/fmn060/xiaozhan/20120510/1225/x_large_nDFG_5204000016531261.jpg",
			"http://img.yingyonghui.com/business/images/QQpeifu.jpg",
			"http://fmn.rrimg.com/fmn060/xiaozhan/20120510/1225/x_large_nDFG_5204000016531261.jpg",
			"http://img.yingyonghui.com/business/images/QQpeifu.jpg",
			"http://img.yingyonghui.com/business/images/QQpeifu.jpg",
			"http://img.yingyonghui.com/business/images/QQpeifu.jpg",
			"http://fmn.rrimg.com/fmn060/xiaozhan/20120510/1225/x_large_nDFG_5204000016531261.jpg",
			"http://fmn.rrimg.com/fmn060/xiaozhan/20120510/1225/x_large_nDFG_5204000016531261.jpg",
			"http://fmn.rrimg.com/fmn060/xiaozhan/20120510/1225/x_large_nDFG_5204000016531261.jpg",
			"http://fmn.rrimg.com/fmn060/xiaozhan/20120510/1225/x_large_nDFG_5204000016531261.jpg" };

	private static final String[] URL2 = {
			"http://res2.windows.microsoft.com/resbox/en/Windows%207/main/0287b4c6-168b-4b0f-9338-4cb426a3cd74_5.jpg",
			"http://res2.windows.microsoft.com/resbox/en/Windows%207/main/bf72cef1-44d3-4e54-be78-84cacf1bea64_5.jpg",
			"http://res2.windows.microsoft.com/resbox/en/Windows%207/main/7349037d-e16b-4195-9ae4-f27b44111075_5.jpg",
			"http://res1.windows.microsoft.com/resbox/en/Windows%207/main/8d368b2b-8b3b-4f07-b281-70418d52adbb_5.jpg",
			"http://res2.windows.microsoft.com/resbox/en/Windows%207/main/869eb807-c143-43c2-8e14-12f78fbd34a5_5.jpg" };

	private static final String[] URL3 = {
			"http://chn.lotour.com/image/20071126/img262868_min.jpg",
			"http://i2.sinaimg.cn/ent/v/m/2012-07-17/U7393P28T3D3686620F326DT20120717021043.jpg",
			"http://chn.lotour.com/image/20071126/img262877_min.jpg",
			"http://www.gotoningbo.com/jqjd/gnlyjd/jx/200912/W020091210350634359495.jpg",
			"http://ts3.mm.bing.net/th?id=I4546056042184794&pid=1.5",
			"http://mm.allcoolmen.com/images/2011/02/gulinazha-15.jpg",
			"http://a3.att.hudong.com/20/65/14300001137328129794650289257_950.jpg",
			"http://photocdn.sohu.com/20110818/Img316724116.jpg",
			"http://photo.staticsdo.com/a1/498/232/142/69493-1274365201-8_765.jpg",
			"http://static.betazeta.com/www.fayerwayer.com/up/2011/03/win8.jpg",
			"http://ts2.mm.bing.net/th?id=I4886011260765353&pid=1.5" };
}
