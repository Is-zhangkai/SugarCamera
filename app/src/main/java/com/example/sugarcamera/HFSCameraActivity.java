package com.example.sugarcamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HFSCameraActivity extends AppCompatActivity {


    //旋转方向集合
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();//旋转方向集合
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private static final int REQUEST_CAMERA_CODE = 0x100;
    /**
     * 后置摄像头
     */
    private static final int CAMERA_FRONT = CameraCharacteristics.LENS_FACING_FRONT;

    /**
     * 预览
     */
    private TextureView mTextureView;
    /**
     * 拍照按钮
     */
    private Button mBtnTake;
    /**
     * 图片
     */
    // private ImageView mImageView;

    /**
     * 照相机ID，标识前置后置
     */
    private String mCameraId;
    /**
     * 相机尺寸
     */
    private Size mCaptureSize;
    /**
     * 图像读取者
     */
    private ImageReader mImageReader;
    /**
     * 图像主线程Handler
     */
    private Handler mCameraHandler;
    /**
     * 相机设备
     */
    private CameraDevice mCameraDevice;
    /**
     * 预览大小
     */
    private Size mPreviewSize;
    /**
     * 相机请求
     */
    private CaptureRequest.Builder mCameraCaptureBuilder;
    /**
     * 相机拍照捕获会话
     */
    private CameraCaptureSession mCameraCaptureSession;
    /**
     * 相机管理者
     */
    private CameraManager mCameraManager;

    /**
     * 相机设备状态回调
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            // 打开
            mCameraDevice = camera;
            if (null != mPreviewSize && mTextureView.isAvailable()) {
                // 开始预览

                takePreview();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            // 断开连接
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            // 异常
            camera.close();
            mCameraDevice = null;
        }
    };

    private TextView toptext;
    private TextView tv_iso,tv_time,tv_distance;

    private int[] iso= new int[]{100, 200, 300, 400, 500, 600, 700, 800};
    private long[]  time= new long[]{100000000, 100000000, 100000000, 100000000,
                                     100000000, 100000000, 100000000, 100000000};
    private float distance=5;
    private float qgd=100/distance;//转为屈光度
    private int i=0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_hfs_camera);

        initView();
        initListener();

        tv_iso.setText("ISO : "+iso[i]);
        tv_time.setText("曝光时间: "+(time[i]/1000000)+"毫秒");
        tv_distance.setText("焦距: "+distance);

        findViewById(R.id.camera_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

    }


    /**
     * 初始化View
     */
    private void initView() {
        // mImageView =findViewById(R.id.iv_show);
        tv_iso=findViewById(R.id.tv_iso);
        tv_time=findViewById(R.id.tv_time);
        tv_distance=findViewById(R.id.tv_jiaoju);
        toptext=findViewById(R.id.tv_topbar);
        mTextureView = findViewById(R.id.tv_camera);
        mBtnTake = findViewById(R.id.btn_take);

    }

    @Override
    public void onResume() {
        super.onResume();
        setTextureListener();
    }

    /**
     * 设置Texture监听
     */
    private void setTextureListener() {



        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {


            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // SurfaceTexture可用
                // 设置相机参数并打开相机
                setUpCamera(width, height);
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // SurfaceTexture大小改变

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                // SurfaceTexture 销毁
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // SurfaceTexture 更新
            }


        });


    }

    /**
     * 打开相机
     */
    private void openCamera() {
        // 此处ImageReader用于拍照所需
        setupImageReader();
        // 获取照相机管理者
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_CODE);

                return;
            }
            // 打开相机
            mCameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (REQUEST_CAMERA_CODE == requestCode) {
            // 权限允许
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    mCameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            } else {
                // 权限拒绝
                Toast.makeText(this, "无权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 设置相机参数
     *
     * @param width  宽度
     * @param height 高度
     */
    private void setUpCamera(int width, int height) {
        // 创建Handler
        mCameraHandler = new Handler(Looper.getMainLooper());
        // 获取摄像头的管理者
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // 遍历所有摄像头
            for (String cameraId : cameraManager.getCameraIdList()) {
                // 相机特性
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
//                float yourMinFocus = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
//                float yourMaxFocus = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);

                // 获取摄像头是前置还是后置
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                // 此处默认打开后置摄像头
                if (null != facing && CAMERA_FRONT == facing) continue;
                // 获取StreamConfigurationMap，管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                // 根据TextureView的尺寸设置预览尺寸
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                // 获取相机支持的最大拍照尺寸
                mCaptureSize = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
                            }
                        });
                // 为摄像头赋值
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    /**
     * 选择SizeMap中大于并且最接近width和height的size
     *
     * @param sizeMap 可选的尺寸
     * @param width   宽
     * @param height  高
     * @return 最接近width和height的size
     */
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        // 创建列表
        List<Size> sizeList = new ArrayList<>();
        // 遍历
        for (Size option : sizeMap) {
            // 判断宽度是否大于高度
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        // 判断存储Size的列表是否有数据
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }

    /**
     * 设置监听
     */
    private void initListener() {
        // 拍照
        mBtnTake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

    }



    /**
     * 重新打开摄像头
     */
    private void reOpenCamera() {
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            setTextureListener();
        }
    }

    /**
     * 关闭摄像头
     */
    private void closeCamera() {
        if (null != mCameraCaptureSession) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    /**
     * 预览
     */
    private void takePreview() {
        // 获取SurfaceTexture
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        // 设置默认的缓冲大小
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        // 创建Surface
        Surface previewSurface = new Surface(surfaceTexture);
        try {
            // 创建预览请求
            mCameraCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 将previewSurface添加到预览请求中
            mCameraCaptureBuilder.addTarget(previewSurface);
            // 创建会话
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        // 配置
                        // mCameraCaptureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
                        mCameraCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                        mCameraCaptureBuilder.set(CaptureRequest.CONTROL_MODE,0);
                        mCameraCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE,CameraMetadata.CONTROL_AF_MODE_OFF);
                        mCameraCaptureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,0);//ev曝光补偿 -其他锁定后此项无用
                        mCameraCaptureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso[i]);//传感器灵敏度iso
                        mCameraCaptureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) time[i]);//曝光时长  单位是纳秒


                        mCameraCaptureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,  (float)qgd );//焦距
                        //Log.v("asdd",mCameraCaptureBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE)+"");
                        mCameraCaptureBuilder.set(CaptureRequest.CONTROL_AWB_MODE,  CaptureRequest.CONTROL_AWB_MODE_OFF);//白平衡



                        //获取手机方向
                        int rotation = getWindowManager().getDefaultDisplay().getRotation();
                        //根据设备方向计算照片的方向
                        mCameraCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));

                        CaptureRequest captureRequest = mCameraCaptureBuilder.build();

                        // 設置session
                        mCameraCaptureSession = session;
                        // 设置重复预览请求
                        mCameraCaptureSession.setRepeatingRequest(captureRequest, null, mCameraHandler);
//
//                        if (i>0) {
//                            Toast.makeText(HFSCameraActivity.this, "相机重启成功，请继续拍照", Toast.LENGTH_SHORT).show();
//                        }
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    // 配置失败
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 拍照
     */
    private void takePicture() {
        try {
            // 设置触发
            mCameraCaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);


            mCameraCaptureBuilder.addTarget(mImageReader.getSurface());
           // mCameraCaptureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) jju[i]);//曝光时长  单位是纳秒
            //获取手机方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            //根据设备方向计算照片的方向
            mCameraCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));
            mCameraCaptureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            Log.v("asdd",mCameraCaptureBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE)+"");
            // 拍照
            mCameraCaptureSession.capture(mCameraCaptureBuilder.build(), null, mCameraHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "异常", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }



    /**
     * 设置ImageReader
     */
    private void setupImageReader() {
        // 2代表ImageReader中最多可以获取两帧图像流
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(), ImageFormat.JPEG, 1);
        // 设置图像可用监听
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                // 获取图片
                final Image image = reader.acquireNextImage();

                // 更新UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 获取字节缓冲区
                        i++;
                        if (i<8) {
                            reOpenCamera();
                            toptext.setText("第" + (i + 1) + "组数据");
                            tv_iso.setText("ISO : "+iso[i]);
                            tv_time.setText("曝光时间: "+(time[i]/1000000)+"毫秒");
                            tv_distance.setText("焦距: "+distance);
                        }
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        // 创建数组之前调用此方法，恢复默认设置
                        buffer.rewind();
                        // 创建与缓冲区内容大小相同的数组
                        byte[] bytes = new byte[buffer.remaining()];
                        // 从缓冲区存入字节数组,读取完成之后position在末尾
                        buffer.get(bytes);


                        //保存
                        String path = getExternalCacheDir().getPath();
                        Log.v("asd1",path);

                        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());  // 获取时间戳命名
                        String fileName = path + "/IMG_" + timeStamp + ".jpg"; // 文件名
                        String fName = "IMG_" + timeStamp+ ".jpg";
                        saveImage2Public(HFSCameraActivity.this,fName,bytes,"Pictures");

                        Toast.makeText(HFSCameraActivity.this,"拍照并保存成功",Toast.LENGTH_SHORT).show();

                        if (i>=8) {

                            startActivity(new Intent( HFSCameraActivity.this,ResultActivity.class));
                            finish();
                        }

                        // 获取Bitmap图像
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        // 显示
//                        if (null != bitmap) {
//                            //变成灰色
//                            //bitmap =convertGreyImg(bitmap);
//                            //显示
//                            //  mImageView.setImageBitmap(bitmap);
//                        }
                    }
                });



            }
        }, mCameraHandler);
    }




    public static void saveImage2Public(Context context, String fileName, byte[] image, String subDir) {
        String subDirection;
        if (!TextUtils.isEmpty(subDir)) {
            if (subDir.endsWith("/")) {
                subDirection = subDir.substring(0, subDir.length() - 1);
            } else {
                subDirection = subDir;
            }
        } else {
            subDirection = "DCIM";
        }

        Cursor cursor = searchImageFromPublic(context, subDir, fileName);
        if (cursor != null && cursor.moveToFirst()) {
            try {
                int id = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID));  // uri的id，用于获取图片
                Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
                Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                if (uri != null) {
                    OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
                    if (outputStream != null) {
                        outputStream.write(image);
                        outputStream.flush();
                        outputStream.close();
                    }
                }
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            //设置保存参数到ContentValues中
            ContentValues contentValues = new ContentValues();
            //设置文件名
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            //兼容Android Q和以下版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //android Q中不再使用DATA字段，而用RELATIVE_PATH代替
                //RELATIVE_PATH是相对路径不是绝对路径
                //关于系统文件夹可以到系统自带的文件管理器中查看，不可以写没存在的名字
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, subDirection);
                //contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Music/sample");
            } else {
                contentValues.put(MediaStore.Images.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath());
            }
            //设置文件类型
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/JPEG");
            //执行insert操作，向系统文件夹中添加文件
            //EXTERNAL_CONTENT_URI代表外部存储器，该值不变
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (uri != null) {
                //若生成了uri，则表示该文件添加成功
                //使用流将内容写入该uri中即可
                OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    outputStream.write(image);
                    outputStream.flush();
                    outputStream.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Cursor searchImageFromPublic(Context context, String filePath, String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            Log.e("1111", "searchImageFromPublic: fileName is null");
            return null;
        }
        if (TextUtils.isEmpty(filePath)) {
            filePath = "DCIM/";
        } else {
            if (!filePath.endsWith("/")) {
                filePath = filePath + "/";
            }
        }

        //兼容androidQ和以下版本
        String queryPathKey = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Images.Media.RELATIVE_PATH : MediaStore.Images.Media.DATA;
        String selection = queryPathKey + "=? and " + MediaStore.Images.Media.DISPLAY_NAME + "=?";
        Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID, queryPathKey, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DISPLAY_NAME},
                selection,
                new String[]{filePath, fileName},
                null);

        return cursor;
    }


    ///////////////////////////////////////////////
    // 彩图转换成灰色图片

    public static Bitmap convertGreyImg(Bitmap img) {
        int width = img.getWidth();         //获取位图的宽
        int height = img.getHeight();       //获取位图的高

        int[] pixels = new int[width * height]; //通过位图的大小创建像素点数组

        img.getPixels(pixels, 0, width, 0, 0, width, height);
        int alpha = 0xFF << 24;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int grey;
                if (pixels[width * i + j] == 0) {
                    continue;
                } else grey = pixels[width * i + j];

                int red = ((grey & 0x00FF0000) >> 16);
                int green = ((grey & 0x0000FF00) >> 8);
                int blue = (grey & 0x000000FF);

                grey = (int) ((float) red * 0.44 + (float) green * 0.45 + (float) blue * 0.11);
                grey = alpha | (grey << 16) | (grey << 8) | grey;
                pixels[width * i + j] = grey;
            }
        }
        //创建空的bitmap时，格式一定要选择ARGB_4444,或ARGB_8888,代表有Alpha通道，RGB_565格式的不显示灰度
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }


}