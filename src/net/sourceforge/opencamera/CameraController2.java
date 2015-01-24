package net.sourceforge.opencamera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.SurfaceHolder;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraController2 extends CameraController {
	private static final String TAG = "CameraController2";

	private CameraDevice camera = null;
	private String cameraIdS = null;
	private CameraCharacteristics characteristics = null;
	private CameraCaptureSession captureSession = null;
	private CaptureRequest.Builder previewBuilder = null;
	private AutoFocusCallback autofocus_cb = null;
	private ImageReader imageReader = null;
	//private ImageReader previewImageReader = null;
	private SurfaceHolder holder = null;
	private SurfaceTexture texture = null;
	private HandlerThread thread = null; 
	Handler handler = null;

	//private MeteringRectangle [] focus_areas = null;
	//private MeteringRectangle [] metering_areas = null;

	public CameraController2(Context context, int cameraId) {
		if( MyDebug.LOG )
			Log.d(TAG, "create new CameraController2: " + cameraId);

		thread = new HandlerThread("CameraBackground"); 
		thread.start(); 
		handler = new Handler(thread.getLooper());

		class MyStateCallback extends CameraDevice.StateCallback {
			boolean callback_done = false;
			@Override
			public void onOpened(CameraDevice camera) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera opened");
				CameraController2.this.camera = camera;
				callback_done = true;

				// note, this won't start the preview yet, but we create the previewBuilder in order to start setting camera parameters
				createPreviewRequest();
			}

			@Override
			public void onClosed(CameraDevice camera) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera closed");
			}

			@Override
			public void onDisconnected(CameraDevice camera) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera disconnected");
				camera.close();
				CameraController2.this.camera = null;
				callback_done = true;
			}

			@Override
			public void onError(CameraDevice camera, int error) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera error: " + error);
				callback_done = true;
			}
		};
		MyStateCallback myStateCallback = new MyStateCallback();

		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
			this.cameraIdS = manager.getCameraIdList()[cameraId];
			manager.openCamera(cameraIdS, myStateCallback, handler);
		    characteristics = manager.getCameraCharacteristics(cameraIdS);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			// throw as a RuntimeException instead, as this is what callers will catch
			throw new RuntimeException();
		}

		if( MyDebug.LOG )
			Log.d(TAG, "wait until camera opened...");
		// need to wait until camera is opened
		while( !myStateCallback.callback_done ) {
		}
		if( camera == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera failed to open");
			throw new RuntimeException();
		}
		if( MyDebug.LOG )
			Log.d(TAG, "camera now opened");

		/*CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
	    StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
	    android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
		imageReader = ImageReader.newInstance(camera_picture_sizes[0].getWidth(), , ImageFormat.JPEG, 2);*/
	}

	@Override
	void release() {
		if( MyDebug.LOG )
			Log.d(TAG, "release");
		if( thread != null ) {
			thread.quitSafely();
			try {
				thread.join();
				thread = null;
				handler = null;
			}
			catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		if( captureSession != null ) {
			captureSession.close();
			captureSession = null;
		}
		if( camera != null ) {
			camera.close();
			camera = null;
		}
		if( imageReader != null ) {
			imageReader.close();
			imageReader = null;
		}
		/*if( previewImageReader != null ) {
			previewImageReader.close();
			previewImageReader = null;
		}*/
	}

	private List<String> convertFocusModesToValues(int [] supported_focus_modes_arr) {
		if( MyDebug.LOG )
			Log.d(TAG, "convertFocusModesToValues()");
	    List<Integer> supported_focus_modes = new ArrayList<Integer>();
	    for(int i=0;i<supported_focus_modes_arr.length;i++)
	    	supported_focus_modes.add(supported_focus_modes_arr[i]);
	    List<String> output_modes = new Vector<String>();
		if( supported_focus_modes != null ) {
			// also resort as well as converting
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
				output_modes.add("focus_mode_auto");
				if( MyDebug.LOG ) {
					Log.d(TAG, " supports focus_mode_auto");
				}
			}
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_MACRO) ) {
				output_modes.add("focus_mode_macro");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_macro");
			}
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
				output_modes.add("focus_mode_manual");
				if( MyDebug.LOG ) {
					Log.d(TAG, " supports focus_mode_manual");
				}
			}
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_EDOF) ) {
				output_modes.add("focus_mode_edof");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_edof");
			}
			if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) ) {
				output_modes.add("focus_mode_continuous_video");
				if( MyDebug.LOG )
					Log.d(TAG, " supports focus_mode_continuous_video");
			}
		}
		return output_modes;
	}

	@Override
	CameraFeatures getCameraFeatures() {
		if( MyDebug.LOG )
			Log.d(TAG, "getCameraFeatures()");
		CameraFeatures camera_features = new CameraFeatures();
		if( MyDebug.LOG ) {
			int hardware_level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
			if( hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY )
				Log.d(TAG, "Hardware Level: LEGACY");
			else if( hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED )
				Log.d(TAG, "Hardware Level: LIMITED");
			else if( hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL )
				Log.d(TAG, "Hardware Level: FULL");
			else
				Log.e(TAG, "Unknown Hardware Level!");
		}

	    // TODO: zoom
	    
		int [] face_modes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
		camera_features.supports_face_detection = false;
		for(int i=0;i<face_modes.length && !camera_features.supports_face_detection;i++) {
			if( face_modes[i] == CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE ) {
				camera_features.supports_face_detection = true;
			}
		}
		if( camera_features.supports_face_detection ) {
			int face_count = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
			if( face_count <= 0 ) {
				camera_features.supports_face_detection = false;
			}
		}

		StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

	    android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
		camera_features.picture_sizes = new ArrayList<CameraController.Size>();
		for(android.util.Size camera_size : camera_picture_sizes) {
			camera_features.picture_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
		}

	    android.util.Size [] camera_video_sizes = configs.getOutputSizes(MediaRecorder.class);
		camera_features.video_sizes = new ArrayList<CameraController.Size>();
		for(android.util.Size camera_size : camera_video_sizes) {
			camera_features.video_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
		}

		//android.util.Size [] camera_preview_sizes = configs.getOutputSizes(SurfaceHolder.class);
		android.util.Size [] camera_preview_sizes = configs.getOutputSizes(SurfaceTexture.class);
		camera_features.preview_sizes = new ArrayList<CameraController.Size>();
		for(android.util.Size camera_size : camera_preview_sizes) {
			camera_features.preview_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
		}
		
		// TODO: current_fps_range
		
		if( characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ) {
			// TODO: flash
		}

		int [] supported_focus_modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES); // Android format
		camera_features.supported_focus_values = convertFocusModesToValues(supported_focus_modes); // convert to our format (also resorts)
		camera_features.max_num_focus_areas = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);

		camera_features.is_exposure_lock_supported = true;

		Range<Integer> exposure_range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
		camera_features.min_exposure = exposure_range.getLower();
		camera_features.max_exposure = exposure_range.getUpper();

		return camera_features;
	}

	@Override
	SupportedValues setSceneMode(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSceneMode() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	SupportedValues setColorEffect(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getColorEffect() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	SupportedValues setWhiteBalance(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getWhiteBalance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	SupportedValues setISO(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	String getISOKey() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Size getPictureSize() {
		Size size = new Size(imageReader.getWidth(), imageReader.getHeight());
		return size;
	}

	@Override
	void setPictureSize(int width, int height) {
		if( imageReader != null ) {
			imageReader.close();
		}
		imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2); 
	}

	private int preview_width = 0;
	private int preview_height = 0;
	
	@Override
	public Size getPreviewSize() {
		return new Size(preview_width, preview_height);
	}

	@Override
	void setPreviewSize(int width, int height) {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewSize: " + width + " , " + height);
		if( holder != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set size of surface holder");
			holder.setFixedSize(width, height);
		}
		if( texture != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set size of preview texture");
			texture.setDefaultBufferSize(width, height);
		}
		preview_width = width;
		preview_height = height;
		/*if( previewImageReader != null ) {
			previewImageReader.close();
		}
		previewImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2); 
		*/
	}

	@Override
	void setVideoStabilization(boolean enabled) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean getVideoStabilization() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getJpegQuality() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	void setJpegQuality(int quality) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getZoom() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	void setZoom(int value) {
		// TODO Auto-generated method stub

	}

	@Override
	int getExposureCompensation() {
		return previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
	}

	@Override
	// Returns whether exposure was modified
	boolean setExposureCompensation(int new_exposure) {
		int current_exposure = previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
		if( new_exposure != current_exposure ) {
			if( MyDebug.LOG )
				Log.d(TAG, "change exposure from " + current_exposure + " to " + new_exposure);
	    	previewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, new_exposure);
	    	setRepeatingRequest();
        	return true;
		}
		return false;
	}

	@Override
	void setPreviewFpsRange(int min, int max) {
		// TODO Auto-generated method stub

	}

	@Override
	void getPreviewFpsRange(int[] fps_range) {
		// TODO Auto-generated method stub

	}

	@Override
	List<int[]> getSupportedPreviewFpsRange() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultSceneMode() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultColorEffect() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultWhiteBalance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultISO() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	void setFocusValue(String focus_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "setFocusValue: " + focus_value);
		/*if( previewBuilder == null || captureSession == null )
			return;*/
		int focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
    	if( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_manual") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
    	}
    	else if( focus_value.equals("focus_mode_macro") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_MACRO;
    	}
    	else if( focus_value.equals("focus_mode_edof") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_EDOF;
    	}
    	else if( focus_value.equals("focus_mode_continuous_video") ) {
    		focus_mode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
    	}
    	else {
    		if( MyDebug.LOG )
    			Log.d(TAG, "setFocusValue() received unknown focus value " + focus_value);
    		return;
    	}
    	previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, focus_mode);
    	setRepeatingRequest();
	}

	private String convertFocusModeToValue(int focus_mode) {
		if( MyDebug.LOG )
			Log.d(TAG, "convertFocusModeToValue: " + focus_mode);
		String focus_value = "";
		if( focus_mode == CaptureRequest.CONTROL_AF_MODE_AUTO ) {
    		focus_value = "focus_mode_auto";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_MACRO ) {
    		focus_value = "focus_mode_macro";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_EDOF ) {
    		focus_value = "focus_mode_edof";
    	}
		else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO ) {
    		focus_value = "focus_mode_continuous_video";
    	}
    	return focus_value;
	}
	
	@Override
	public String getFocusValue() {
		/*if( previewBuilder == null || captureSession == null )
			return "";*/
		int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
		return convertFocusModeToValue(focus_mode);
	}

	@Override
	void setFlashValue(String flash_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "setFlashValue: " + flash_value);
		// TODO Auto-generated method stub

	}

	@Override
	public String getFlashValue() {
		// TODO Auto-generated method stub
		// returns "" if flash isn't supported
		return "";
	}

	@Override
	void setRecordingHint(boolean hint) {
		// TODO Auto-generated method stub

	}

	@Override
	void setAutoExposureLock(boolean enabled) {
    	previewBuilder.set(CaptureRequest.CONTROL_AE_LOCK, enabled);
    	setRepeatingRequest();
	}

	@Override
	public boolean getAutoExposureLock() {
    	return previewBuilder.get(CaptureRequest.CONTROL_AE_LOCK);
	}

	@Override
	void setRotation(int rotation) {
		// TODO Auto-generated method stub

	}

	@Override
	void setLocationInfo(Location location) {
		// TODO Auto-generated method stub

	}

	@Override
	void removeLocationInfo() {
		// TODO Auto-generated method stub

	}

	@Override
	void enableShutterSound(boolean enabled) {
		// TODO Auto-generated method stub

	}
	
	private MeteringRectangle convertAreaToMeteringRectangle(Rect sensor_rect, Area area) {
		// CameraController.Area is always [-1000, -1000] to [1000, 1000]
		// but for CameraController2, we must convert to [0, 0] to [sensor width-1, sensor height-1] for use as a MeteringRectangle
		double left_f = (area.rect.left+1000)/2000.0;
		double top_f = (area.rect.top+1000)/2000.0;
		double right_f = (area.rect.right+1000)/2000.0;
		double bottom_f = (area.rect.bottom+1000)/2000.0;
		int sensor_left = (int)(left_f * (sensor_rect.width()-1));
		int sensor_right = (int)(right_f * (sensor_rect.width()-1));
		int sensor_top = (int)(top_f * (sensor_rect.height()-1));
		int sensor_bottom = (int)(bottom_f * (sensor_rect.height()-1));
		sensor_left = Math.max(sensor_left, 0);
		sensor_right = Math.max(sensor_right, 0);
		sensor_top = Math.max(sensor_top, 0);
		sensor_bottom = Math.max(sensor_bottom, 0);
		sensor_left = Math.min(sensor_left, sensor_rect.width()-1);
		sensor_right = Math.min(sensor_right, sensor_rect.width()-1);
		sensor_top = Math.min(sensor_top, sensor_rect.height()-1);
		sensor_bottom = Math.min(sensor_bottom, sensor_rect.height()-1);
		MeteringRectangle metering_rectangle = new MeteringRectangle(sensor_left, sensor_top, sensor_right, sensor_bottom, area.weight);
		return metering_rectangle;
	}

	@Override
	boolean setFocusAndMeteringArea(List<Area> areas) {
		/*if( previewBuilder == null || captureSession == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "capture session not available");
			return false;
		}*/

		Rect sensor_rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
		boolean has_focus = false;
		boolean has_metering = false;
		if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
			has_focus = true;
			MeteringRectangle [] focus_areas = new MeteringRectangle[areas.size()];
			int i = 0;
			for(CameraController.Area area : areas) {
				focus_areas[i++] = convertAreaToMeteringRectangle(sensor_rect, area);
			}
        	previewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, focus_areas);
		}
		if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
			has_metering = true;
			MeteringRectangle [] metering_areas = new MeteringRectangle[areas.size()];
			int i = 0;
			for(CameraController.Area area : areas) {
				metering_areas[i++] = convertAreaToMeteringRectangle(sensor_rect, area);
			}
        	previewBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, metering_areas);
		}
		if( has_focus || has_metering ) {
			setRepeatingRequest();
		}
		return has_focus;
	}

	@Override
	void clearFocusAndMetering() {
		// TODO Auto-generated method stub

	}

	@Override
	public List<Area> getFocusAreas() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Area> getMeteringAreas() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	boolean supportsAutoFocus() {
		/*if( previewBuilder == null || captureSession == null )
			return false;*/
		int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
		if( focus_mode == CaptureRequest.CONTROL_AF_MODE_AUTO || focus_mode == CaptureRequest.CONTROL_AF_MODE_MACRO )
			return true;
		return false;
	}

	@Override
	boolean focusIsVideo() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	void reconnect() throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	void setPreviewDisplay(SurfaceHolder holder) throws IOException {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewDisplay");
		this.holder = holder;
		this.texture = null;
	}

	@Override
	void setPreviewTexture(SurfaceTexture texture) throws IOException {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewTexture");
		this.texture = texture;
		this.holder = null;
	}
	
	private void setRepeatingRequest() {
		if( MyDebug.LOG )
			Log.d(TAG, "setRepeatingRequest");
		if( /*previewBuilder == null ||*/ captureSession == null )
			return;
		try {
			captureSession.setRepeatingRequest(previewBuilder.build(), mCaptureCallback, null);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void capture() {
		if( MyDebug.LOG )
			Log.d(TAG, "capture");
		if( /*previewBuilder == null ||*/ captureSession == null )
			return;
		try {
			captureSession.capture(previewBuilder.build(), mCaptureCallback, null);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
	}
	
	private void createPreviewRequest() {
		if( MyDebug.LOG )
			Log.d(TAG, "createPreviewRequest");
		if( camera == null /*|| captureSession == null*/ ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not available!");
			return;
		}
		try {
			previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			/*if( MyDebug.LOG && holder != null ) {
				Log.d(TAG, "holder surface: " + holder.getSurface());
				if( holder.getSurface() == null )
					Log.d(TAG, "holder surface is null!");
				else if( !holder.getSurface().isValid() )
					Log.d(TAG, "holder surface is not valid!");
			}
        	Surface surface = null;
            if( holder != null ) {
            	surface = holder.getSurface();
            }
            else if( texture != null ) {
            	surface = new Surface(texture);
            }
			previewBuilder.addTarget(surface);*/

			previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
			//previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
			//previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

			setRepeatingRequest();
		}
		catch(CameraAccessException e) {
			//captureSession = null;
			e.printStackTrace();
		} 
	}

	@Override
	void startPreview() {
		if( MyDebug.LOG )
			Log.d(TAG, "startPreview");

		try {
			captureSession = null;
			//previewBuilder = null;

			if( MyDebug.LOG )
				Log.d(TAG, "picture size: " + imageReader.getWidth() + " x " + imageReader.getHeight());
			/*if( MyDebug.LOG )
				Log.d(TAG, "preview size: " + previewImageReader.getWidth() + " x " + previewImageReader.getHeight());*/

			class MyStateCallback extends CameraCaptureSession.StateCallback {
				@Override
				public void onConfigured(CameraCaptureSession session) {
					if( MyDebug.LOG )
						Log.d(TAG, "onConfigured");
					if( camera == null ) {
						return;
					}
					captureSession = session;
					//createPreviewRequest();
					if( MyDebug.LOG && holder != null ) {
						Log.d(TAG, "holder surface: " + holder.getSurface());
						if( holder.getSurface() == null )
							Log.d(TAG, "holder surface is null!");
						else if( !holder.getSurface().isValid() )
							Log.d(TAG, "holder surface is not valid!");
					}
		        	Surface surface = null;
		            if( holder != null ) {
		            	surface = holder.getSurface();
		            }
		            else if( texture != null ) {
		            	surface = new Surface(texture);
		            }
					previewBuilder.addTarget(surface);
					setRepeatingRequest();
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession session) {
					if( MyDebug.LOG )
						Log.d(TAG, "onConfigureFailed");
				}
			}
			MyStateCallback myStateCallback = new MyStateCallback();

        	Surface surface = null;
            if( holder != null ) {
            	surface = holder.getSurface();
            }
            else if( texture != null ) {
            	surface = new Surface(texture);
            }
			camera.createCaptureSession(Arrays.asList(surface/*, previewImageReader.getSurface()*/, imageReader.getSurface()),
				myStateCallback,
		 		null);
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
			//throw new IOException();
		}
	}

	@Override
	void stopPreview() {
		if( MyDebug.LOG )
			Log.d(TAG, "stopPreview");
		if( captureSession == null )
			return;
		try {
			captureSession.stopRepeating();
		}
		catch(CameraAccessException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean startFaceDetection() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	void setFaceDetectionListener(FaceDetectionListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	void autoFocus(AutoFocusCallback cb) {
		if( MyDebug.LOG )
			Log.d(TAG, "autoFocus");
		if( /*previewBuilder == null ||*/ captureSession == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "capture session not available");
			return;
		}
    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
		if( MyDebug.LOG ) {
			{
				MeteringRectangle [] areas = previewBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
				for(int i=0;i<areas.length;i++) {
					Log.d(TAG, i + " focus area: " + areas[i].getX() + " , " + areas[i].getY() + " : " + areas[i].getWidth() + " x " + areas[i].getHeight() + " weight " + areas[i].getMeteringWeight());
				}
			}
			{
				MeteringRectangle [] areas = previewBuilder.get(CaptureRequest.CONTROL_AE_REGIONS);
				for(int i=0;i<areas.length;i++) {
					Log.d(TAG, i + " metering area: " + areas[i].getX() + " , " + areas[i].getY() + " : " + areas[i].getWidth() + " x " + areas[i].getHeight() + " weight " + areas[i].getMeteringWeight());
				}
			}
		}
    	/*if( focus_areas != null ) {
        	previewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, focus_areas);
    	}
    	if( metering_areas != null ) {
        	previewBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, metering_areas);
    	}*/
    	//previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
    	/*previewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
		if( MyDebug.LOG ) {
			Float focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
			Log.d(TAG, "focus_distance: " + focus_distance);
		}*/
    	//setRepeatingRequest();
    	capture();
		this.autofocus_cb = cb;
    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
	}

	@Override
	void cancelAutoFocus() {
		if( MyDebug.LOG )
			Log.d(TAG, "cancelAutoFocus");
		if( /*previewBuilder == null ||*/ captureSession == null )
			return;
    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
    	//setRepeatingRequest();
    	capture();
    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
		this.autofocus_cb = null;
	}

	@Override
	void takePicture(PictureCallback raw, PictureCallback jpeg) {
		// TODO Auto-generated method stub

	}

	@Override
	void setDisplayOrientation(int degrees) {
		// do nothing - for CameraController2, the preview display orientation is handled via the TextureView's transform
	}

	@Override
	int getDisplayOrientation() {
		return 0;
	}

	@Override
	int getCameraOrientation() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	boolean isFrontFacing() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	void unlock() {
		// TODO Auto-generated method stub

	}

	@Override
	void initVideoRecorder(MediaRecorder video_recorder) {
		// TODO Auto-generated method stub

	}

	@Override
	String getParametersString() {
		// TODO Auto-generated method stub
		return null;
	}

	private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() { 
		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
			/*if( MyDebug.LOG )
				Log.d(TAG, "onCaptureCompleted");*/
			/*int af_state = result.get(CaptureResult.CONTROL_AF_STATE);
			if( af_state != CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN ) {
				if( MyDebug.LOG )
					Log.d(TAG, "CONTROL_AF_STATE = " + af_state);
			}*/
			if( MyDebug.LOG && autofocus_cb == null ) {
				int af_state = result.get(CaptureResult.CONTROL_AF_STATE);
				if( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED )
					Log.d(TAG, "onCaptureCompleted: autofocus success but no callback set");
				else if( af_state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED )
					Log.d(TAG, "onCaptureCompleted: autofocus failed but no callback set");
			}
			if( autofocus_cb != null ) {
				// check for autofocus completing
				int af_state = result.get(CaptureResult.CONTROL_AF_STATE);
				//Log.d(TAG, "onCaptureCompleted: af_state: " + af_state);
				if( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ) {
					if( MyDebug.LOG ) {
						if( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED )
							Log.d(TAG, "onCaptureCompleted: autofocus success");
						else
							Log.d(TAG, "onCaptureCompleted: autofocus failed");
					}
					// we need to cancel af trigger, otherwise sometimes things seem to get confused, with the autofocus thinking it's completed too early
			    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			    	capture();
			    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);

					autofocus_cb.onAutoFocus(af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED);
					autofocus_cb = null;
				}
			}
		}
	};
}
