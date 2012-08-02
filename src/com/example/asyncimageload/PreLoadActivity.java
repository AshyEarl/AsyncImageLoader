package com.example.asyncimageload;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import ashy.earl.util.AsyncImageLoader;
import ashy.earl.util.AsyncImageLoader.ImageLoadListener;

public class PreLoadActivity extends Activity {
	private AsyncImageLoader mAsyncImageLoader;
	private boolean mStartLoading;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.preload);
		OnButtonClick buttonClick = new OnButtonClick();
		mAsyncImageLoader = AsyncImageLoader.getInstance(this);

		findViewById(R.id.button1).setOnClickListener(buttonClick);
		findViewById(R.id.button2).setOnClickListener(buttonClick);
		findViewById(R.id.button3).setOnClickListener(buttonClick);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		AsyncImageLoader.release();
	}

	private class OnButtonClick implements OnClickListener {
		private MyLinstener mLinstener;

		public OnButtonClick() {
			mLinstener = new MyLinstener();
		}

		@Override
		public void onClick(View v) {
			Intent intent = new Intent(PreLoadActivity.this, MainActivity.class);
			switch (v.getId()) {
			case R.id.button1:
				mAsyncImageLoader.clearCache();
				break;
			case R.id.button2:
				preLoad();
				break;
			case R.id.button3:
				intent.putExtra(MainActivity.URL, URL1);
				startActivity(intent);
				break;
			default:
				break;
			}
		}

		private void preLoad() {
			mStartLoading = true;
			Button button = (Button) findViewById(R.id.button1);
			for (String url : URL1) {
				// 这里也可以直接调用mAsyncImageLoader.preLoad(url)完成加载
				mAsyncImageLoader.loadImage(button, url, mLinstener);
			}
		}

		private class MyLinstener extends ImageLoadListener {

			@Override
			public void onSucceed(boolean isAsyncLoad, View view, String url,
					Bitmap bitmap) {
				Toast.makeText(PreLoadActivity.this, "--预加载完成--",
						Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onStart(View view, String url) {
				if (mStartLoading) {
					Toast.makeText(PreLoadActivity.this, "开始预加载",
							Toast.LENGTH_SHORT).show();
					mStartLoading = false;
				}
			}

			@Override
			public void onFailed(View view, String url) {
				Toast.makeText(PreLoadActivity.this, "预加载url失败:" + url,
						Toast.LENGTH_LONG).show();
			}
		}
	}

	private static final String[] URL1 = {
			"http://res2.windows.microsoft.com/resbox/en/Windows%207/main/0287b4c6-168b-4b0f-9338-4cb426a3cd74_5.jpg",
			"http://res2.windows.microsoft.com/resbox/en/Windows%207/main/bf72cef1-44d3-4e54-be78-84cacf1bea64_5.jpg",
			"http://res2.windows.microsoft.com/resbox/en/Windows%207/main/7349037d-e16b-4195-9ae4-f27b44111075_5.jpg",
			"http://res1.windows.microsoft.com/resbox/en/Windows%207/main/8d368b2b-8b3b-4f07-b281-70418d52adbb_5.jpg",
			"http://res2.windows.microsoft.com/resbox/en/Windows%207/main/869eb807-c143-43c2-8e14-12f78fbd34a5_5.jpg" };

}
