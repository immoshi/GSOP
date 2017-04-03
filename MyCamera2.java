// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package mycompoent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
//======================================================================================================================================

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.ActivityResultListener;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.util.ErrorMessages;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnTouchListener;

//==================================================================================================================================
/**
 * Camera provides access to the phone's camera
 *
 *
 */
@DesignerComponent(version = YaVersion.CAMERA_COMPONENT_VERSION, description = "A component to take a picture using the device's camera. "
		+ "After the picture is taken, the name of the file on the phone "
		+ "containing the picture is available as an argument to the "
		+ "AfterPicture event. The file name can be used, for example, to set "
		+ "the Picture property of an Image component.", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "images/camera.png")
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.WRITE_EXTERNAL_STORAGE, android.permission.READ_EXTERNAL_STORAGE,android.permission.CAMERA")
public class MyCamera2 extends AndroidNonvisibleComponent implements ActivityResultListener, Component, OnTouchListener {

	// =========================================================================================================================
	// insert my code
	private final Activity activity;
	private static final String TAG = "AndroidCameraApi";
	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
	// private static final int REQUEST_CAMERA_PERMISSION = 200;

	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}

	protected CameraDevice cameraDevice;
	protected CameraCaptureSession cameraCaptureSessions;
	protected CaptureRequest captureRequest;
	protected CaptureRequest.Builder captureRequestBuilder;

	private SurfaceTexture textureView;

	TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
			// open your camera here
			openCamera();
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
			// Transform you image captured size according to the surface width
			// and height
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			return false;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture surface) {
		}
	};
	private String cameraId;
	private Size imageDimension;
	private ImageReader imageReader;

	// private File file;
	// private boolean mFlashSupported;
	private Handler mBackgroundHandler;
	private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
		@Override
		public void onOpened(CameraDevice camera) {
			// This is called when the camera is open
			Log.e(TAG, "onOpened");
			cameraDevice = camera;
			createCameraPreview();
		}

		@Override
		public void onDisconnected(CameraDevice camera) {
			cameraDevice.close();
		}

		@Override
		public void onError(CameraDevice camera, int error) {
			cameraDevice.close();
			cameraDevice = null;
		}
	};
	final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
		@Override
		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
				TotalCaptureResult result) {
			super.onCaptureCompleted(session, request, result);
			// Toast.makeText(MainActivity.this, "Saved:" + file,
			// Toast.LENGTH_SHORT).show();
			createCameraPreview();
		}
	};
	// private HandlerThread mBackgroundThread;

	// =========================================================================================================================
	// private static final String CAMERA_INTENT =
	// "android.media.action.IMAGE_CAPTURE";
	// private static final String CAMERA_OUTPUT = "output";
	private final ComponentContainer container;
	private Uri imageFile;

	/*
	 * Used to identify the call to startActivityForResult. Will be passed back
	 * into the resultReturned() callback method.
	 */
	private int requestCode;

	// whether to open into the front-facing camera
	private boolean useFront;

	/**
	 * Creates a Camera component.
	 *
	 * Camera has a boolean option to request the forward-facing camera via an
	 * intent extra.
	 *
	 * @param container
	 *            container, component will be placed in
	 */
	public MyCamera2(ComponentContainer container) {
		super(container.$form());
		this.container = container;
		// =========================================================================================================================

		activity = container.$context();
		textureView = new SurfaceTexture(10);
	 
		// =========================================================================================================================
		// Default property values
		UseFront(false);
	}

	/**
	 * Returns true if the front-facing camera is to be used (when available)
	 *
	 * @return {@code true} indicates front-facing is to be used, {@code false}
	 *         will open default
	 */
	// @Deprecated
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public boolean UseFront() {
		return useFront;
	}

	/**
	 * Specifies whether the front-facing camera should be used (when available)
	 *
	 * @param front
	 *            {@code true} for front-facing camera, {@code false} for
	 *            default
	 */
	// @Deprecated
	// Hide the deprecated property from the Designer
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
	@SimpleProperty(description = "Specifies whether the front-facing camera should be used (when available). "
			+ "If the device does not have a front-facing camera, this option will be ignored "
			+ "and the camera will open normally.")
	public void UseFront(boolean front) {
		useFront = front;
	}

	/**
	 * Takes a picture, then raises the AfterPicture event. If useFront is true,
	 * adds an extra to the intent that requests the front-facing camera.
	 */
	@SimpleFunction
	public void TakePicture() {
		openCamera();
	}
	public void TakePicture2() {
		Date date = new Date();
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
			Log.i("CameraComponent", "External storage is available and writable");

			// imageFile = Uri.fromFile(new
			// File(Environment.getExternalStorageDirectory(),
			// "/Pictures/app_inventor_" + date.getTime() + ".jpg"));

			// ContentValues values = new ContentValues();
			// values.put(MediaStore.Images.Media.DATA, imageFile.getPath());
			// values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
			// values.put(MediaStore.Images.Media.TITLE,
			// imageFile.getLastPathSegment());

			if (requestCode == 0) {
				requestCode = form.registerForActivityResult(this);
			}

			// Uri imageUri =
			// container.$context().getContentResolver().insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI,
			// values);
			// Intent intent = new Intent(CAMERA_INTENT);
			// intent.putExtra(CAMERA_OUTPUT, imageUri);

			// NOTE: This uses an undocumented, testing feature (CAMERA_FACING).
			// It may not work in the future.
			// if (useFront) {
			// intent.putExtra("android.intent.extras.CAMERA_FACING", 1);
			// }
			// =========================================================================================================================

//			openCamera();
			if (null == cameraDevice) {
				Log.e(TAG, "cameraDevice is null");
				return;
			}
			CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
			try {
				CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
				Size[] jpegSizes = null;
				if (characteristics != null) {
					jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
							.getOutputSizes(ImageFormat.JPEG);
				}
				int width = 640;
				int height = 480;
				if (jpegSizes != null && 0 < jpegSizes.length) {
					width = jpegSizes[0].getWidth();
					height = jpegSizes[0].getHeight();
				}
				ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
				List<Surface> outputSurfaces = new ArrayList<Surface>(2);
				outputSurfaces.add(reader.getSurface());
				outputSurfaces.add(new Surface(textureView));//.getSurfaceTexture()));
				final CaptureRequest.Builder captureBuilder = cameraDevice
						.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
				captureBuilder.addTarget(reader.getSurface());
				captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
				// Orientation
				int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
				captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
				final File file = new File(Environment.getExternalStorageDirectory(),
						"/Pictures/app_inventor_" + date.getTime() + ".jpg");
				imageFile = Uri.fromFile(file);
				ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
					@Override
					public void onImageAvailable(ImageReader reader) {
						Image image = null;
						try {
							image = reader.acquireLatestImage();
							ByteBuffer buffer = image.getPlanes()[0].getBuffer();
							byte[] bytes = new byte[buffer.capacity()];
							buffer.get(bytes);
							save(bytes);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							if (image != null) {
								image.close();
							}
						}
					}

					private void save(byte[] bytes) throws IOException {
						OutputStream output = null;
						try {
							output = new FileOutputStream(file);
							output.write(bytes);
						} finally {
							if (null != output) {
								output.close();
							}
						}
					}
				};
				reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
				final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
					@Override
					public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
							TotalCaptureResult result) {
						super.onCaptureCompleted(session, request, result);
						// Toast.makeText(activity.this, "Saved:" + file,
						// Toast.LENGTH_SHORT).show();
						createCameraPreview();
					}
				};
				cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
					@Override
					public void onConfigured(CameraCaptureSession session) {
						try {
							session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
						} catch (CameraAccessException e) {
							e.printStackTrace();
						}
					}

					@Override
					public void onConfigureFailed(CameraCaptureSession session) {
					}
				}, mBackgroundHandler);
			} catch (CameraAccessException e) {
				e.printStackTrace();
			}

			// =========================================================================================================================
			// container.$context().startActivityForResult(intent, requestCode);
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			form.dispatchErrorOccurredEvent(this, "TakePicture", ErrorMessages.ERROR_MEDIA_EXTERNAL_STORAGE_READONLY);
		} else {
			form.dispatchErrorOccurredEvent(this, "TakePicture",
					ErrorMessages.ERROR_MEDIA_EXTERNAL_STORAGE_NOT_AVAILABLE);
		}
	}

	@Override
	public void resultReturned(int requestCode, int resultCode, Intent data) {
		Log.i("CameraComponent", "Returning result. Request code = " + requestCode + ", result code = " + resultCode);
		if (requestCode == this.requestCode && resultCode == Activity.RESULT_OK) {
			File image = new File(imageFile.getPath());
			// File image=file;
			if (image.length() != 0) {
				scanFileToAdd(image);
				AfterPicture(imageFile.toString());
			} else {
				deleteFile(imageFile); // delete empty file
				// see if something useful got returned in the data
				if (data != null && data.getData() != null) {
					Uri tryImageUri = data.getData();
					Log.i("CameraComponent", "Calling Camera.AfterPicture with image path " + tryImageUri.toString());
					AfterPicture(tryImageUri.toString());
				} else {
					Log.i("CameraComponent", "Couldn't find an image file from the Camera result");
					form.dispatchErrorOccurredEvent(this, "TakePicture", ErrorMessages.ERROR_CAMERA_NO_IMAGE_RETURNED);
				}
			}
		} else {
			// delete empty file
			deleteFile(imageFile);
		}
	}

	/**
	 * Scan the newly added picture to be displayed in a default media content
	 * provider in a device (e.g. Gallery, Google Photo, etc..)
	 *
	 * @param image
	 *            the picture taken by Camera component
	 */
	private void scanFileToAdd(File image) {
		Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		Uri contentUri = Uri.fromFile(image);
		mediaScanIntent.setData(contentUri);
		container.$context().getApplicationContext().sendBroadcast(mediaScanIntent);
	}

	private void deleteFile(Uri fileUri) {
		File fileToDelete = new File(fileUri.getPath());
		try {
			if (fileToDelete.delete()) {
				Log.i("CameraComponent", "Deleted file " + fileUri.toString());
			} else {
				Log.i("CameraComponent", "Could not delete file " + fileUri.toString());
			}
		} catch (SecurityException e) {
			Log.i("CameraComponent", "Got security exception trying to delete file " + fileUri.toString());
		}
	}

	/**
	 * Indicates that a photo was taken with the camera and provides the path to
	 * the stored picture.
	 */
	@SimpleEvent
	public void AfterPicture(String image) {
		closeCamera();
		EventDispatcher.dispatchEvent(this, "AfterPicture", image);
	}
	// ================================================================================================

	protected void updatePreview() {
		if (null == cameraDevice) {
			Log.e(TAG, "updatePreview error, return");
		}
		captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
		try {
			cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void closeCamera() {
		if (null != cameraDevice) {
			cameraDevice.close();
			cameraDevice = null;
		}
		if (null != imageReader) {
			imageReader.close();
			imageReader = null;
		}
	}

	protected void createCameraPreview() {
		try {
			SurfaceTexture texture = textureView;//.getSurfaceTexture();
			assert texture != null;
			texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
			Surface surface = new Surface(texture);
			captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			captureRequestBuilder.addTarget(surface);
			cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
				@Override
				public void onConfigured(CameraCaptureSession cameraCaptureSession) {
					//// The camera is already closed
					if (null == cameraDevice) {
						return;
					}
					// When the session is ready, we start displaying the
					// preview.
					cameraCaptureSessions = cameraCaptureSession;
					updatePreview();
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
					// Toast.makeText(MainActivity.this, "Configuration change",
					// Toast.LENGTH_SHORT).show();
				}
			}, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void openCamera() {
		CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
		Log.e(TAG, "is camera open");
		try {

			if (useFront)
				cameraId = manager.getCameraIdList()[1];
			else
				cameraId = manager.getCameraIdList()[0];
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
			StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
			assert map != null;
			  imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
			 
			// Add permission for camera and let user grant the permission
			 
			manager.openCamera(cameraId, stateCallback, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
		Log.e(TAG, "openCamera X");
	}
	
	
	@Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
     
        case MotionEvent.ACTION_DOWN:
        	TakePicture2();
            break;
      
        case MotionEvent.ACTION_MOVE:
           
            break;
      
        case MotionEvent.ACTION_UP:
           
            break;
        default:
            break;
        }
     
        return false;
    }

	// =================================================================================================================================================
}
