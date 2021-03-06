package controllers;

import play.mvc.Http.MultipartFormData;

import controllers.constants.Error;

import models.AttachmentType;
import models.Media;
import models.Model;
import models.User;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;

import org.bson.types.ObjectId;

import org.imgscalr.Scalr;

public class MediaController extends AppController {
    private static AmazonS3 s3client;
    private static String bucket;

    static {
        String access = props.getProperty("media.s3.access");
        String secret = props.getProperty("media.s3.secret");
        bucket = props.getProperty("media.s3.bucket");

        AWSCredentials credentials = new BasicAWSCredentials(access, secret);
        s3client = new AmazonS3Client(credentials);
    }

    private static Result eTag(File file) {
        String eTag = Model.md5(String.valueOf(file.lastModified()));
        String ifNoneMatch = request().getHeader("If-None-Match");

        if (ifNoneMatch != null && ifNoneMatch.equals(eTag))
            return NotModified();

        response().setHeader("ETAG", eTag);

        return Ok(file);
    }

    public static Result avatar(JsonNode params) {
        ObjectId id = getObjectId(params, "id");

        File file = new File("/tmp/" + id);
        if (!file.exists())
            s3client.getObject(new GetObjectRequest(bucket, id.toString()), file);

        try {
            int size = params.get("size").intValue();
            File thumb = new File("/tmp/" + id + "_avatar_" + size);

            if (!thumb.exists()) {
                BufferedImage img = ImageIO.read(file);
                String type = img.getTransparency() == BufferedImage.TRANSLUCENT ?
                    "png" : "jpg";
                BufferedImage resizedImg;
                int width = img.getWidth();
                int height = img.getHeight();

                if (width > height)
                    resizedImg = Scalr.crop(img, (width - height) / 2, 0, height, height);
                else
                    resizedImg = Scalr.crop(img, 0, (height - width) / 2, width, width);

                resizedImg = Scalr.resize(resizedImg, size);
                ImageIO.write(resizedImg, type, thumb);
            }

            return eTag(thumb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Result download(JsonNode params) {
        ObjectId id = getObjectId(params, "id");

        File file = new File("/tmp/" + id);
        if (!file.exists())
            s3client.getObject(new GetObjectRequest(bucket, id.toString()), file);

        if (params.has("size")) {
            try {
                int size = params.get("size").intValue();
                File thumb = new File("/tmp/" + id + "_" + size);

                if (!thumb.exists()) {
                    BufferedImage img = ImageIO.read(file);
                    String type = img.getTransparency() == BufferedImage.TRANSLUCENT ?
                        "png" : "jpg";
                    size = img.getWidth() > size ? size : img.getWidth();
                    BufferedImage resizedImg = Scalr.resize(img, Scalr.Mode.FIT_TO_WIDTH, size);
                    ImageIO.write(resizedImg, type, thumb);
                }

                return eTag(thumb);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return eTag(file);
        }
    }

    public static Result upload(JsonNode params) {
        User me = getMe(params);
        ObjectNode result = mapper.createObjectNode();
        ArrayNode files = result.putArray("files");

        MultipartFormData<File> body = request().body().asMultipartFormData();
        if (body == null)
            return Error(Error.MALFORMED_MULTIPART_FORM);

        for (MultipartFormData.FilePart<File> part : body.getFiles()) {
            //TODO part.getContentType();
            if (part.getContentType().startsWith("audio")) {
                Media media = new Media(me.getId(), AttachmentType.audio);
                ObjectId id = media.getId();

                media.save();

                upload(id, part, files);
            } else {
                Media media = new Media(me.getId(), AttachmentType.photo);
                ObjectId id = media.getId();

                media.save();

                uploadPohto(id, part, files);
            }
        }

        return Ok(result);
    }

    private static void upload(ObjectId id, MultipartFormData.FilePart<File> part,
        ArrayNode files) {
        try {
            FFprobe ffprobe = new FFprobe("/usr/bin/ffprobe");
            FFmpegProbeResult result = ffprobe.probe(part.getFile().getAbsolutePath());
            FFmpegFormat format = result.getFormat();

            s3client.putObject(new PutObjectRequest(bucket, id.toString(), part.getFile()));

            part.getFile().delete();

            String signing = Model.md5("#" + id + format.bit_rate + format.duration);

            files.addObject()
                .put("name", part.getKey())
                .put("content_type", part.getContentType())
                .put("id", id.toString())
                .put("bit_rate", format.bit_rate)
                .put("duration", format.duration)
                .put("signing", signing);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void uploadPohto(ObjectId id, MultipartFormData.FilePart<File> part,
        ArrayNode files) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(part.getFile());
            int orientation = 0;
            if (metadata.containsDirectoryOfType(ExifIFD0Directory.class)) {
                Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
                if (directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION))
                    orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }

            BufferedImage img = ImageIO.read(part.getFile());
            String type = img.getTransparency() == BufferedImage.TRANSLUCENT ?
                "png" : "jpg";
            int size = img.getWidth() >= img.getHeight() ?
                (img.getWidth() > 1024 ? 1024 : img.getWidth()) :
                (img.getHeight() > 1024 ? 1024 : img.getHeight());
            Scalr.Mode mode = img.getWidth() >= img.getHeight() ?
                Scalr.Mode.FIT_TO_WIDTH : Scalr.Mode.FIT_TO_HEIGHT;
            BufferedImage resizedImg = Scalr.resize(img, mode, size);

            if (orientation == 3 || orientation == 4)
                resizedImg = Scalr.rotate(resizedImg, Scalr.Rotation.CW_180);
            else if (orientation == 5 || orientation == 6)
                resizedImg = Scalr.rotate(resizedImg, Scalr.Rotation.CW_90);
            else if (orientation == 7 || orientation == 8)
                resizedImg = Scalr.rotate(resizedImg, Scalr.Rotation.CW_270);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(resizedImg, type, os);

            byte[] buffer = os.toByteArray();
            InputStream is = new ByteArrayInputStream(buffer);
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType("image/" + type);
            meta.setContentLength(buffer.length);

            s3client.putObject(new PutObjectRequest(bucket, id.toString(), is, meta));

            part.getFile().delete();

            String signing = Model.md5("#" + id + resizedImg.getWidth() + resizedImg.getHeight());

            files.addObject()
                .put("name", part.getKey())
                .put("content_type", "image/" + type)
                .put("id", id.toString())
                .put("width", resizedImg.getWidth())
                .put("height", resizedImg.getHeight())
                .put("signing", signing);
        } catch (IOException | ImageProcessingException | MetadataException e) {
            throw new RuntimeException(e);
        }
    }
}
