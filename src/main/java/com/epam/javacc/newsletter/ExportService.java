package com.epam.javacc.newsletter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class ExportService {

    public static void main(String[] args) {
        new ExportService().export(args[0], args[1]);
    }

    public void export(String pageURL, String fileName) {
        try {
            System.out.println("Exporting " + pageURL + " to " + fileName);
            Document document = Jsoup.connect(pageURL).get();

            document.getElementsByAttributeValue("class", "webversion").forEach(Node::remove);
            document.getElementsByAttributeValue("class", "layout one-col email-footer").forEach(Node::remove);

            Elements images = document.getElementsByTag("img");
            images.forEach(
                image -> {
                    boolean allowConversion = !image.parents().eachAttr("class").contains("emb-web-links");
                    String src = image.attr("src");
                    try {
                        image.attr(
                            "src",
                            "data:image/png;base64," + getBase64EncodedImage(src, allowConversion)
                        );
                    } catch (IOException e) {
                        System.out.println("Skipped image " + src);
                    }
                });
            Elements anchors = document.getElementsByTag("a");
            anchors.forEach(
                element -> {
                    String initialURL = element.attr("href");
                    String redirectedURL = getFinalURL(initialURL);
                    System.out.println("Replacing URL " + initialURL + " -> " + redirectedURL);
                    element.attr("href", redirectedURL);
                });

            final File f = new File(fileName + ".html");
            FileUtils.writeStringToFile(f, document.outerHtml(), "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String getFinalURL(String url) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.connect();
            con.getInputStream();
            if (con.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM
                || con.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                String redirectUrl = con.getHeaderField("Location");
                if (redirectUrl.toLowerCase().startsWith("http")) {
                    return getFinalURL(redirectUrl);
                }
            }
            return url;
        } catch (Exception e) {
            return url;
        }
    }

    private String getBase64EncodedImage(String imageURL, boolean allowConversion) throws IOException {
        java.net.URL url = new java.net.URL(imageURL);
        InputStream is = url.openStream();
        byte[] bytes = allowConversion && imageURL.toLowerCase().endsWith(".png")
            ? pngToJpg(is)
            : IOUtils.toByteArray(is);
        System.out.println("Encoding image " + imageURL);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] pngToJpg(InputStream in) throws IOException {

        BufferedImage image = ImageIO.read(in);
        BufferedImage result = new BufferedImage(
            image.getWidth(),
            image.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        result.createGraphics().drawImage(image, 0, 0, Color.WHITE, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(result, "jpg", out);

        return out.toByteArray();
    }
}
