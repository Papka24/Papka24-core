/*
 * Copyright (c) 2017. iDoc LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     (1) Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     (2) Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *     (3)The name of the author may not be used to
 *     endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package ua.papka24.server.api.helper;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.papka24.server.db.dao.ResourceDAO;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Queue;

/**
 * Class for render documents.
 */
public class PDFRenderQueueConsumer implements Runnable {
    public static final Logger log = LoggerFactory.getLogger("PDFRenderQueue");
    private Queue<File> queue;

    public PDFRenderQueueConsumer(Queue<File> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        PDDocument document = null;
        File file;
        while (true) {
            while ((file = queue.poll()) != null) {
                try {
                    document = PDDocument.load(file);
                    PDFRenderer render = new PDFRenderer(document);
                    BufferedImage image = render.renderImageWithDPI(0, 50);
                    ImageIO.write(image, "png", new File(file.getAbsolutePath() + Main.property.getProperty("pngPrefix")));
                } catch (Exception e) {
                    log.error("error create preview for pdf file:",e);
                    ResourceDAO.getInstance().setCryptedResource(file.getName());
                }finally {
                    if (document != null) {
                        try {
                            document.close();
                        } catch (Exception e) {
                            log.error("Can't close document {}", file, e);
                        }
                    }
                }
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("Error start queue", e);
            }
        }
    }
}
