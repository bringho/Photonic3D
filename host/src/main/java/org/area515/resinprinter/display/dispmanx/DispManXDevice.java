package org.area515.resinprinter.display.dispmanx;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.CustomNamedDisplayDevice;
import org.area515.resinprinter.display.DispManXTest.ALPHA;
import org.area515.resinprinter.display.DispManXTest.VC_DISPMANX_ALPHA_T;
import org.area515.resinprinter.display.DispManXTest.VC_IMAGE_TRANSFORM_T;
import org.area515.resinprinter.display.DispManXTest.VC_IMAGE_TYPE_T;
import org.area515.resinprinter.display.DispManXTest.VC_RECT_T;
import org.area515.resinprinter.display.GraphicsOutputInterface;
import org.area515.resinprinter.display.InappropriateDeviceException;

import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;

public class DispManXDevice extends CustomNamedDisplayDevice implements GraphicsOutputInterface {
    private static final Logger logger = LogManager.getLogger();
    private Rectangle bounds = new Rectangle();
    private SCREEN screen;
    private VC_DISPMANX_ALPHA_T.ByReference alpha;
    private int displayHandle;
    private int resourceHandle;
    private int updateHandle;
    private int elementHandle;
    private Memory destPixels;
    
    public DispManXDevice(String displayName, SCREEN screen) throws InappropriateDeviceException {
		super(displayName);
		this.screen = screen;
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
    	int returnCode = DispManX.INSTANCE.bcm_host_init();
    	if (returnCode != 0) {
    		disposeScreen();
    		throw new InappropriateDeviceException("bcm_host_init failed with:" + returnCode);
    	}
    	
    	displayHandle = DispManX.INSTANCE.graphics_get_display_size( screen.getId(), width, height );
    	if (displayHandle == 0) {
    		disposeScreen();
    		throw new InappropriateDeviceException("graphics_get_display_size failed with:" + returnCode);
    	}
    	
    	bounds.setBounds(0, 0, width.getValue(), height.getValue());
    	
        VC_DISPMANX_ALPHA_T.ByReference alpha = new VC_DISPMANX_ALPHA_T.ByReference();
        alpha.flags = ALPHA.DISPMANX_FLAGS_ALPHA_FROM_SOURCE.getFlag() | ALPHA.DISPMANX_FLAGS_ALPHA_FIXED_ALL_PIXELS.getFlag();
        alpha.opacity = 255;
	}

    private  void disposeScreen() {
    	logger.info("vc_dispmanx_display_close result:" + DispManX.INSTANCE.vc_dispmanx_display_close( screen.getId() ));
	}
    
	@Override
	public void dispose() {
        updateHandle = DispManX.INSTANCE.vc_dispmanx_update_start( 0 );
		logger.info("vc_dispmanx_element_remove result:" + DispManX.INSTANCE.vc_dispmanx_element_remove(updateHandle, elementHandle));
		logger.info("vc_dispmanx_update_submit_sync result:" + DispManX.INSTANCE.vc_dispmanx_update_submit_sync(updateHandle));
		logger.info("vc_dispmanx_resource_delete result:" + DispManX.INSTANCE.vc_dispmanx_resource_delete(resourceHandle));
	}
	
	
    public static int getPitch( int x, int y ) {
        return ((x + (y)-1) & ~((y)-1));
    }
    
	private Memory loadBitmapRGB565(BufferedImage image, Memory destPixels, IntByReference width, IntByReference height, IntByReference pitchByRef) {
		int bytesPerPixel = 2;
		int pitch = getPitch( bytesPerPixel * image.getWidth(), 32 );
		pitchByRef.setValue(pitch);
		if (destPixels == null) {
			destPixels = new Memory(pitch * image.getHeight());
		}
		
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
        		int rgb = image.getRGB(x, y);
        		destPixels.setShort((y*(pitch / bytesPerPixel) + x) * bytesPerPixel, (short)(((rgb & 0xf80000) >>> 8) | ((rgb & 0xfc00) >>> 5) | (rgb & 0xf8 >>> 3)));
            }
        }
        width.setValue(image.getWidth());
        height.setValue(image.getHeight());
        return destPixels;
	}
	
	//TODO: Why am I doing this? can't I do this with NIO buffers and no conversion at all?
	private Memory loadBitmapARGB8888(BufferedImage image, Memory destPixels, IntByReference width, IntByReference height, IntByReference pitchByRef) {
		int bytesPerPixel = 4;
		int pitch = getPitch( bytesPerPixel * image.getWidth(), 32 );
		pitchByRef.setValue(pitch);
		long start = System.currentTimeMillis();
		if (destPixels == null) {
			destPixels = new Memory(pitch * image.getHeight());
		}
		
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
        		destPixels.setInt((y*(pitch / bytesPerPixel) + x) * bytesPerPixel, image.getRGB(x, y));
            }
        }
        System.out.println(System.currentTimeMillis() - start);
        width.setValue(image.getWidth());
        height.setValue(image.getHeight());
        return destPixels;
	}
	
	@Override
	public void showBlankImage() {
		dispose();
	}

	@Override
	public void showCalibrationImage(int xPixels, int yPixels) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void showGridImage(int pixels) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void showImage(BufferedImage image) {
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        IntByReference pitch = new IntByReference();
        
        destPixels = loadBitmapARGB8888(image, destPixels, width, height, pitch);
        VC_RECT_T.ByReference sourceRect = new VC_RECT_T.ByReference();
        VC_RECT_T.ByReference destinationRect = new VC_RECT_T.ByReference();
        DispManX.INSTANCE.vc_dispmanx_rect_set( sourceRect, 0, 0, width.getValue()<<16, height.getValue()<<16 );//Shifting by 16 is a zoom factory of zero
        DispManX.INSTANCE.vc_dispmanx_rect_set( destinationRect, 0, 0, width.getValue(), height.getValue() );
        
        IntByReference nativeImageReference = new IntByReference();
        resourceHandle = DispManX.INSTANCE.vc_dispmanx_resource_create(
        		VC_IMAGE_TYPE_T.VC_IMAGE_ARGB8888.getcIndex(), 
        		width.getValue(), 
        		height.getValue(), 
        		nativeImageReference);
        if (resourceHandle == 0) {
        	throw new IllegalArgumentException("Couldn't create resourceHandle for dispmanx");
        }
        
        int returnCode = DispManX.INSTANCE.vc_dispmanx_resource_write_data( 
        		resourceHandle, 
        		VC_IMAGE_TYPE_T.VC_IMAGE_ARGB8888.getcIndex(), 
        		pitch.getValue() , 
        		destPixels, 
        		destinationRect);
        if (returnCode != 0) {
        	throw new IllegalArgumentException("Couldn't vc_dispmanx_resource_write_data for dispmanx:" + returnCode);
        }
        
        updateHandle = DispManX.INSTANCE.vc_dispmanx_update_start(0);  //This method should be called create update
        if (updateHandle == 0) {
        	throw new IllegalArgumentException("Couldn't vc_dispmanx_update_start for dispmanx");
        }

        elementHandle = DispManX.INSTANCE.vc_dispmanx_element_add(     //Creates and adds the element to the current screen update
        		updateHandle, 
        		displayHandle, 
        		1, 
        		destinationRect, 
        		resourceHandle, 
        		sourceRect, 
        		PROTECTION.DISPMANX_PROTECTION_NONE.getcConst(), 
        		alpha, 
        		0, 
        		VC_IMAGE_TRANSFORM_T.VC_IMAGE_ROT0.getcConst());
        if (updateHandle == 0) {
        	throw new IllegalArgumentException("Couldn't vc_dispmanx_element_add for dispmanx");
        }

        returnCode = DispManX.INSTANCE.vc_dispmanx_update_submit_sync(updateHandle);//Wait for the update to complete
        if (returnCode != 0) {
        	throw new IllegalArgumentException("Couldn't vc_dispmanx_update_submit_sync for dispmanx:" + returnCode);
        }
	}
	
	@Override
	public GraphicsConfiguration getDefaultConfiguration() {
		//TODO: this is horrible! we return this fake graphics configuration just so that we can give people our bounds!
		return new GraphicsConfiguration() {
			@Override
			public AffineTransform getNormalizingTransform() {
				return null;
			}
			
			@Override
			public GraphicsDevice getDevice() {
				return null;
			}
			
			@Override
			public AffineTransform getDefaultTransform() {
				return null;
			}
			
			@Override
			public ColorModel getColorModel(int transparency) {
				return null;
			}
			
			@Override
			public ColorModel getColorModel() {
				return null;
			}
			
			@Override
			public Rectangle getBounds() {
				return bounds;
			}
		};
	}

	@Override
	public void resetSliceCount() {
		//Since this isn't used for debugging we don't do anything
	}

	@Override
	public Rectangle getBoundry() {
		return bounds;
	}
}