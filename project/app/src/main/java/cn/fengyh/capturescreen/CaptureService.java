package cn.fengyh.capturescreen;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


public class CaptureService extends Service
{

    private static final String TAG = "CaptureService";

    private MediaProjectionManager mMediaProjectionMgr;
    private MediaProjection mMediaProjection;
    private ImageView mCaptureImageView;
    private LinearLayout mCaptureLayout;
    private ImageReader mImageReader;
    private String mImageName;
    private String mImagePath;
    private int screenDensity;
    private int windowWidth;
    private int windowHeight;
    private VirtualDisplay mVirtualDisplay;
    private WindowManager windowMgr;


    @Nullable
    @Override
    public IBinder onBind(Intent intent)
	{
        return null;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        createEnvironment();
        createFloatView();
    }

    private void createEnvironment()
    {
        mImagePath = Environment.getExternalStorageDirectory().getPath() + "/CaptureScreen/ScreenShot/";
        mMediaProjectionMgr = ((MyApplication) getApplication()).getMpmngr();
        windowMgr = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        windowWidth = windowMgr.getDefaultDisplay().getWidth();
        windowHeight = windowMgr.getDefaultDisplay().getHeight();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowMgr.getDefaultDisplay().getMetrics(displayMetrics);
        screenDensity = displayMetrics.densityDpi;
        mImageReader = ImageReader.newInstance(windowWidth, windowHeight, 0x1, 2);

    }

    private void createFloatView() 
	{

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams
                (WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.RGBA_8888);
        params.x = 0;
        params.y = windowHeight / 2;
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;

        LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
        mCaptureLayout = (LinearLayout) inflater.inflate(R.layout.float_capture, null);
        mCaptureImageView = (ImageView) mCaptureLayout.findViewById(R.id.iv_capture);
        windowMgr.addView(mCaptureLayout, params);

        mCaptureImageView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) 
			{
                mCaptureImageView.setVisibility(View.INVISIBLE);
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() 
					{
                        Log.e(TAG, "start startVirtual");
                        startVirtual();
                    }
                }, 500);
                // Handler handler1 = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.e(TAG, "start startCapture");
                        startCapture();
                    }
                }, 1000);
                // Handler handler2 = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.e(TAG, "start stopVirtual");
                        mCaptureImageView.setVisibility(View.VISIBLE);
                        stopVirtual();
                    }
                }, 1500);
            }
        });

        mCaptureImageView.setOnTouchListener(new View.OnTouchListener() 
		{
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) 
			{
                params.x = (int) (motionEvent.getRawX() - mCaptureImageView.getMeasuredWidth() / 2);
                params.y = (int) (motionEvent.getRawY() - mCaptureImageView.getMeasuredHeight() / 2 - 20);
                windowMgr.updateViewLayout(mCaptureLayout, params);
                return false;
            }
        });
    }

    private void stopVirtual()
    {
        if (mVirtualDisplay != null)
        {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    private void startCapture()
    {
        mImageName = System.currentTimeMillis() + ".png";
        Log.e(TAG, "image name is : " + mImageName);
        Image image = mImageReader.acquireLatestImage();
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        image.close();

        if (bitmap != null)
        {
            Log.e(TAG, "bitmap  create success ");
            try
            {
                File fileFolder = new File(mImagePath);
                if (!fileFolder.exists())
                    fileFolder.mkdirs();
                File file = new File(mImagePath, mImageName);
                if (!file.exists())
                {
                    Log.e(TAG, "file create success ");
                    file.createNewFile();
                }
                FileOutputStream out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();
                Log.e(TAG, "file save success ");
                Toast.makeText(this.getApplicationContext(), "截图成功", Toast.LENGTH_SHORT).show();
            }
            catch (IOException e)
            {
                Log.e(TAG, e.toString());
                e.printStackTrace();
            }
        }
    }

    private void startVirtual()
    {
        if (mMediaProjection != null)
        {
            virtualDisplay();
        }
        else
        {
            setUpMediaProjection();
            virtualDisplay();
        }
    }

    private void setUpMediaProjection()
    {
        int resultCode = ((MyApplication) getApplication()).getResultCode();
        Intent data = ((MyApplication) getApplication()).getResultIntent();
        mMediaProjection = mMediaProjectionMgr.getMediaProjection(resultCode, data);
    }

    private void virtualDisplay()
    {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("capture_screen", windowWidth, windowHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mImageReader.getSurface(), null, null);
    }


    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (mCaptureLayout != null)
        {
            windowMgr.removeView(mCaptureLayout);
        }
        if (mMediaProjection != null)
        {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }
}
