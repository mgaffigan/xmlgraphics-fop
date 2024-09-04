/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.render.java2d;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;

import org.apache.xmlgraphics.image.GraphicsConstants;
import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageFlavor;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.impl.ImageRendered;

import org.apache.fop.render.ImageHandler;
import org.apache.fop.render.ImageHandlerUtil;
import org.apache.fop.render.RenderingContext;

/**
 * Image handler implementation that paints {@link RenderedImage} instances on a {@link Graphics2D}
 * object.
 */
public class Java2DImageHandlerRenderedImage implements ImageHandler {

    /** {@inheritDoc} */
    public int getPriority() {
        return 300;
    }

    /** {@inheritDoc} */
    public Class getSupportedImageClass() {
        return ImageRendered.class;
    }

    /** {@inheritDoc} */
    public ImageFlavor[] getSupportedImageFlavors() {
        return new ImageFlavor[] {
            ImageFlavor.BUFFERED_IMAGE,
            ImageFlavor.RENDERED_IMAGE,
        };
    }

    /** {@inheritDoc} */
    public void handleImage(RenderingContext context, Image image, Rectangle pos)
            throws IOException {
        Java2DRenderingContext java2dContext = (Java2DRenderingContext)context;
        ImageInfo info = image.getInfo();
        ImageRendered imageRend = (ImageRendered)image;
        Graphics2D g2d = java2dContext.getGraphics2D();

        AffineTransform at = new AffineTransform();
        if (context.getHint(ImageHandlerUtil.INTEGER_PIXEL) != null) {
            // wrap to the nearest pixel
            double x = pos.x, y = pos.y;
            double pixelBoundary = 72000 / java2dContext.getUserAgent().getTargetResolution();
            x = x - (x % pixelBoundary);
            y = y - (y % pixelBoundary);
            at.translate(x, y);
        } else {
            at.translate(pos.x, pos.y);
        }
        
        double sx = pos.getWidth() / (double)info.getSize().getWidthPx();
        double sy = pos.getHeight() / (double)info.getSize().getHeightPx();
        
        // we need to translate from {source pixels}, {target dimension mpt} 
        // to {scale from source pixels to mpt}
        // ex: 72k mpt / 72 pixels => 1k scale

        //scaling because of image resolution
        //float sourceResolution = java2dContext.getUserAgent().getSourceResolution();
        //source resolution seems to be a bad idea, not sure why
        at.scale(sx, sy);

        // add rendering hints for scaling
        Object oldInterpolation = g2d.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        if (context.getHint(ImageHandlerUtil.INTEGER_PIXEL) != null) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }

        RenderedImage rend = imageRend.getRenderedImage();
        if (imageRend.getTransparentColor() != null && !rend.getColorModel().hasAlpha()) {
            int transCol = imageRend.getTransparentColor().getRGB();
            BufferedImage bufImage = makeTransparentImage(rend);
            WritableRaster alphaRaster = bufImage.getAlphaRaster();
            //TODO Masked images: Does anyone know a more efficient method to do this?
            final int[] transparent = new int[] {0x00};
            for (int y = 0, maxy = bufImage.getHeight(); y < maxy; y++) {
                for (int x = 0, maxx = bufImage.getWidth(); x < maxx; x++) {
                    int col = bufImage.getRGB(x, y);
                    if (col == transCol) {
                        //Mask out all pixels that match the transparent color
                        alphaRaster.setPixel(x, y, transparent);
                    }
                }
            }
            g2d.drawRenderedImage(bufImage, at);
        } else {
            g2d.drawRenderedImage(rend, at);
        }

        // revert rendering hints
        if (oldInterpolation != null) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        } else {
            g2d.getRenderingHints().remove(RenderingHints.KEY_INTERPOLATION);
        }
    }

    private BufferedImage makeTransparentImage(RenderedImage src) {
        BufferedImage bufImage = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufImage.createGraphics();
        g2d.drawRenderedImage(src, new AffineTransform());
        g2d.dispose();
        return bufImage;
    }

    /** {@inheritDoc} */
    public boolean isCompatible(RenderingContext targetContext, Image image) {
        return (image == null || image instanceof ImageRendered)
                && targetContext instanceof Java2DRenderingContext;
    }

}
