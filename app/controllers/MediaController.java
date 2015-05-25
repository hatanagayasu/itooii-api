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
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.bson.types.ObjectId;

import org.imgscalr.Scalr;

public class MediaController extends AppController {
    private static AmazonS3 s3client;
    private static String bucket;

    static {
        String access = conf.getString("media.s3.access");
        String secret = conf.getString("media.s3.secret");
        bucket = conf.getString("media.s3.bucket");

        AWSCredentials credentials = new BasicAWSCredentials(access, secret);
        s3client = new AmazonS3Client(credentials);
    }

    public static Result avatar(JsonNode params) {
        ObjectId id = getObjectId(params, "id");

        File file = new File("/tmp/" + id);
        if (!file.exists())
            s3client.getObject(new GetObjectRequest(bucket, id.toString()), file);

        try {
            int size = params.get("size").intValue();
            File thumb = new File("/tmp/" + id + "_avatar_" + size);

            BufferedImage img = ImageIO.read(file);
            BufferedImage resizedImg;
            int width = img.getWidth();
            int height = img.getHeight();

            if (width > height)
                resizedImg = Scalr.crop(img, (width - height) / 2, 0, height, height);
            else
                resizedImg = Scalr.crop(img, 0, (height - width) / 2, width, width);

            resizedImg = Scalr.resize(resizedImg, size);
            ImageIO.write(resizedImg, "jpg", thumb);

            return Ok(thumb);
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
                    size = img.getWidth() > size ? size : img.getWidth();
                    BufferedImage resizedImg = Scalr.resize(img, Scalr.Mode.FIT_TO_WIDTH, size);
                    ImageIO.write(resizedImg, "jpg", thumb);
                }

                return Ok(thumb);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return Ok(file);
        }
    }

    public static Result upload(JsonNode params) {
        User me = getMe(params);
        ObjectNode result = mapper.createObjectNode();
        ArrayNode files = result.putArray("files");

        MultipartFormData body = request().body().asMultipartFormData();
        if (body == null)
            return Error(Error.MALFORMED_MULTIPART_FORM);

        for (MultipartFormData.FilePart part : body.getFiles()) {
            //TODO part.getContentType();
            Media media = new Media(me.getId(), AttachmentType.photo);
            ObjectId id = media.getId();

            media.save();

            uploadPohto(id, part, files);
        }

        return Ok(result);
    }

    private static void uploadPohto(ObjectId id, MultipartFormData.FilePart part, ArrayNode files) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(part.getFile());
            int orientation = 0;
            if (metadata.containsDirectoryOfType(ExifIFD0Directory.class)) {
                orientation = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class)
                    .getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }

            BufferedImage img = ImageIO.read(part.getFile());
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
            ImageIO.write(resizedImg, "jpg", os);

            byte[] buffer = os.toByteArray();
            InputStream is = new ByteArrayInputStream(buffer);
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType("image/jpg");
            meta.setContentLength(buffer.length);

            s3client.putObject(new PutObjectRequest(bucket, id.toString(), is, meta));

            part.getFile().delete();

            String signing = Model.md5("#" + id + resizedImg.getWidth() + resizedImg.getHeight());

            files.addObject()
                .put("name", part.getKey())
                .put("content_type", "image/jpg")
                .put("id", id.toString())
                .put("width", resizedImg.getWidth())
                .put("height", resizedImg.getHeight())
                .put("signing", signing);
        } catch (IOException | ImageProcessingException | MetadataException e) {
            throw new RuntimeException(e);
        }
    }
}
